# meta-firewire-recorder

yocto meta-layer that builds the device image for **equip-1** — a portable
firewire dv recorder appliance on the radxa rock 2f (rockchip rk3528a, aarch64).
the image captures dv/hdv over ieee-1394, streams a live preview
(mediamtx / webrtc-whep), and is provisioned over bluetooth + wifi. it is
controlled from the [equip-1 android app](https://github.com/g8row/equip-1).

target image: `firewire-recorder-image`. active branch: **wrynose**.

> **v0.1** — first tagged release. builds cleanly and boots on the rock 2f; the
> equip-1 app records, previews and provisions against it. see
> [status](#status).

## prebuilt image

a ready-to-flash image is published on the
[latest release](https://github.com/g8row/meta-firewire-recorder/releases/latest).
write it to a microsd card and boot the rock 2f from it:

```sh
# with dd (replace /dev/sdX with your card, this erases it)
zcat firewire-recorder-image-radxa-rock-2f.wic.gz | sudo dd of=/dev/sdX bs=4M status=progress conv=fsync
```

or use [balenaetcher](https://etcher.balena.io/). the rootfs auto-expands to fill
the card on first boot. after boot the recorder software (`companion-api` on
`:8000`, `companion-net` for ble/wifi) starts automatically.

## what the layer builds

- **firewire capture** — `dvgrab`, plus `ffmpeg` and `x264` bbappends that enable
  the gpl / x264 / v4l2 / ieee-1394 codec paths, and a kernel config fragment
  (`firewire.cfg`) turning on firewire + v4l2 m2m.
- **recorder software** — the `companion` recipe builds two go binaries from the
  [g8row/equip-1](https://github.com/g8row/equip-1) monorepo (`companion-api`,
  `companion-net`) and installs a prebuilt `mediamtx`. `companion-api` owns the
  mediamtx lifecycle, so there is deliberately no standalone `mediamtx.service`.
- **networking** — connman rebuilt with its **nftables** backend (the 6.18 kernel
  ships an nftables-only netfilter set), plus the `nft-nat` / `nft-masq` /
  `nft-chain-nat` kernel modules. connman handles wifi tethering, dhcp and nat
  in-process — no dnsmasq.
- **system** — systemd, openssh, first-boot rootfs expand, ssh host-key pre-gen,
  unused services disabled, rfkill unblocked for the wifi/bt radios.

the bsp — machine config, kernel, u-boot, and the out-of-tree aic8800d80 wifi/bt
driver + firmware — lives in the sibling **meta-rockchip** layer, not here.

## building from source

this layer is one clone inside a larger openembedded workspace alongside
`openembedded-core`, `bitbake`, `meta-openembedded`, `meta-arm`, `meta-yocto`,
and **meta-rockchip** (the rk3528 bsp).

```sh
# from the workspace root
source openembedded-core/oe-init-build-env build

# add this layer (and meta-rockchip + meta-openembedded) to bblayers.conf
bitbake-layers add-layer ../meta-firewire-recorder

# build the image
MACHINE=radxa-rock-2f bitbake firewire-recorder-image
```

output lands in `build/tmp/deploy/images/radxa-rock-2f/` (`.wic` + `.manifest`).

**design constraint:** recipes here depend only on packages that exist in oe-core
+ meta-openembedded. a package with no provider fails `do_rootfs` late and hard.

## layer layout

```
meta-firewire-recorder/
  recipes-images/       firewire-recorder-image.bb
  recipes-core/         companion (go binaries + mediamtx), init tweaks
  recipes-connectivity/ connman nftables backend
  recipes-multimedia/   dvgrab, ffmpeg + x264 bbappends
  recipes-kernel/       linux bbappend (firewire.cfg)
  recipes-devtools/     tooling
  wic/                  custom image layout (optional)
  conf/                 layer.conf
```

## status

works: the image builds cleanly with the nftables connman backend, boots on the
rock 2f, auto-expands its rootfs, and brings up the core services
(`companion-api`, `companion-net`, `connman`, `bluetooth`). dv capture, the
webrtc and mjpeg previews, ble pairing, the device wifi hotspot, and 2.4ghz wifi
provisioning all work end-to-end against the equip-1 app.

known board-level limitations (these live in the bsp / kernel, not this layer):

- **5ghz wifi as a client** — the aic8800 rejects 5ghz association; only 2.4ghz
  networks provision reliably. `regulatory.db` packaging is also still open.
- **bluetooth extended advertising** — the combo chip's extended le doesn't
  transmit, so `companion-net` falls back to raw legacy hci advertising. the
  clean fix is a kernel `hci_quirk_broken_ext_adv` patch in the bsp.
- **hardware h.264 encoding** — no mainline encoder driver for the rk3528, so the
  preview is encoded in software (libx264). dv capture is lossless and unaffected.

## license

mit (see `COPYING.MIT`), matching yocto layer convention. the recorder software
built by the `companion` recipe is gpl; see the
[equip-1](https://github.com/g8row/equip-1) repo.
