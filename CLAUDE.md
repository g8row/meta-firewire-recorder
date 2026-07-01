# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`meta-firewire-recorder` is the Yocto/OpenEmbedded **appliance layer** for a headless
**FireWire recorder** built on the **Radxa ROCK 2F** (Rockchip **RK3528A**, aarch64
Cortex-A53). It captures DV/HDV over IEEE-1394, streams it (mediamtx / WebRTC-WHEP), and is
provisioned over BLE + WiFi. Target image: `firewire-recorder-image`.

## Where this layer lives

This layer is one clone inside a larger **wrynose** OE workspace (separate `openembedded-core`,
`bitbake`, `meta-yocto`, `meta-arm`, `meta-openembedded`, and `meta-rockchip` clones). It is a
**standalone git repo** (`github.com/g8row/meta-firewire-recorder`, branch **wrynose**) —
commits here do not touch the other layers. The BSP (machine config, kernel, U-Boot,
AIC8800 driver/firmware) lives in the sibling **`meta-rockchip`** repo; kernel-level work
belongs there or in the workspace kernel recipe, not here.

**Design constraint:** use packages that exist in **oe-core + meta-openembedded only**. Do
not depend on vendor/BSP-only or non-existent packages — a package with no provider breaks
`do_rootfs` late and hard (see the nftables note below).

## Build & develop (from the workspace, not this dir)

```sh
# From the workspace root. This ALREADY cds into build/ — do NOT `cd build` after
# (a second cd fails; the script takes the build dir as its argument).
source openembedded-core/oe-init-build-env build

bitbake firewire-recorder-image                 # build the image
bitbake -c clean <recipe>                       # wipe a recipe's WORKDIR + stamps
bitbake -e <recipe> | grep '^VAR='              # expanded value of any variable
```

Output: `build/tmp/deploy/images/radxa-rock-2f/` (`.wic` + `.manifest`). Inspect the staged
rootfs under `build/tmp/work/radxa_rock_2f-oe-linux/firewire-recorder-image/1.0/rootfs/`.
There is **no automated test suite**; verify by building, inspecting the rootfs, and booting.
Several behaviours (BT power-on, tethering NAT, mediamtx port binding) are only observable on
hardware — don't claim a runtime fix is verified from a build alone.

Gotchas:
- **`companion` is a Go recipe.** After a SRC_URI/source change re-runs `do_compile`, it can
  fail with `rm: … pkg/mod/…: Permission denied` (Go marks its module cache read-only). Fix:
  `chmod -R u+w build/tmp/work/cortexa53-oe-linux/companion/0.1/build`, then
  `bitbake -c clean companion`, then rebuild.
- **`radxa-rock-2` is a valid override token** — `radxa-rock-2f.conf` (in meta-rockchip) sets
  `MACHINEOVERRIDES =. "radxa-rock-2:"`. That's why `SRC_URI:append:radxa-rock-2` in
  `recipes-kernel/linux/linux-yocto_%.bbappend` correctly wires in `firewire.cfg`.
- **Init is systemd-only.** No `sysvinit`, so `systemd-sysv-generator` is absent and SysV
  `/etc/init.d` scripts DO NOT run. `recipes-core/init-scripts/rock2f-tweaks` is a SysV
  recipe and is therefore inert on this image (its rfkill unblock is handled by the systemd
  `rfkill-unblock.service` instead). Anything that must run at boot needs a real `.service`.

## Layer architecture (the parts that span files)

**`recipes-images/images/firewire-recorder-image.bb`** — `core-image` + a big
`IMAGE_INSTALL:append` + several `ROOTFS_POSTPROCESS_COMMAND` functions (SSH host-key
pre-gen, disabling unused services, masking systemd-rfkill, enabling timesyncd). The custom
`wic/firewire-recorder-gptdisk.wks.in` is **not** enabled (`WKS_FILE` is commented out) — the
build uses meta-rockchip's default wic layout; `rootfs-expand` grows the rootfs partition via
`parted` at first boot regardless.

**`recipes-core/companion/`** — builds two Go binaries from the `g8row/equip-1` monorepo,
plus a prebuilt `mediamtx`:
- `companion-api` — HTTP API + embedded UI, and the **mediamtx lifecycle manager**: it
  spawns/supervises `mediamtx` itself. **Never add a standalone `mediamtx.service`** — it
  becomes a second instance and collides on RTP UDP :8000. But companion-api spawns mediamtx
  with **no config arg**, and mediamtx's config-less default **rejects all publishing** (RTSP
  publish → 400), so it must be fed `/etc/mediamtx.yml` (which has `paths: all_others:`).
  mediamtx only searches its CWD for config, never `/etc`, so `EQUIP_MEDIAMTX_BINARY` points
  at the `mediamtx-launch` wrapper that `exec`s `mediamtx /etc/mediamtx.yml`.
- `companion-net` — BLE GATT provisioning + ConnMan WiFi control. Powers the BT adapter over
  BlueZ D-Bus; if power-on fails it `exit(1)`s and systemd restart-loops it. So anything that
  leaves hci0 rfkill-blocked crash-loops this daemon.

**FireWire + codec stack** — `recipes-multimedia/dvgrab` (capture), plus `ffmpeg` and `x264`
bbappends enabling GPL/x264/v4l2/IEEE-1394 codec paths; `recipes-kernel/linux` adds
`firewire.cfg` (FireWire + V4L2 m2m). ffmpeg/x264 are `commercial`-flagged, accepted in
`conf/layer.conf`.

**Networking / firewall** — the 6.18 kernel ships an **nftables-only** netfilter set (no
legacy `ip_tables.ko`/`iptable_nat.ko`). `recipes-connectivity/connman/connman_%.bbappend`
therefore rebuilds ConnMan with its **nftables** backend
(`PACKAGECONFIG:remove = "iptables"` / `:append = " nftables"`), and the image installs
`kernel-module-nft-{nat,masq,chain-nat}`. ConnMan does its own in-process WiFi-tethering
DHCP+NAT (no dnsmasq).

**Board quirks** — RTC-less board: `busybox`/init hwclock scripts are no-op'd. All
networking is USB (RTL8153 Ethernet, AIC8800D80 WiFi/BT), driven by the out-of-tree AIC8800
driver in meta-rockchip.

## Known hardware issue: AIC8800D80 Bluetooth extended advertising is broken

Read before touching anything BLE. The chip reports HCI/LMP **5.4** with `HCI_LE_EXT_ADV`
but its **firmware rejects the extended LE opcodes** (`0x2036`, `0x2042`, and ext-adv
equivalents fail); only legacy LE commands work. The kernel — not bluetoothd — picks ext vs
legacy purely from the feature bit (`ext_adv_capable(dev) == le_features[1] & HCI_LE_EXT_ADV`),
and 6.18 has **no `HCI_QUIRK_BROKEN_EXT_ADV`**, so config can't force legacy.

Today the only fallback is the userspace `hciadv.go` raw-`hcitool` path in `companion-net`,
which **races** BlueZ's `RegisterAdvertisement` (→ `0x0C Command Disallowed`) and puts the
service UUID in the scan-response rather than the primary ADV. A prior kernel patch was
removed in commit `9c89d9d` (malformed diff; only covered scan). **Recommended real fix:** a
btusb quirk (target the **linux-yocto** recipe, via `:radxa-rock-2`) that adds
`HCI_QUIRK_BROKEN_EXT_ADV`, clears `le_features[1] & HCI_LE_EXT_ADV` in
`hci_cc_le_read_local_features`, and sets the quirk for `USB_DEVICE(0xa69c, 0x8d81)`. That
lets BlueZ advertise legacy natively and makes `hciadv.go` removable.

Already fixed: `systemd-rfkill` is masked in the image (it restored a stale `blocked` rfkill
state on every hci0 appearance, defeating `rfkill-unblock.service`).
