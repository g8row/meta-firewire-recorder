# Radxa ROCK 2F — Firewire Recorder Bring-up Log

## Hardware

| Component | Detail |
|---|---|
| Board | Radxa ROCK 2F |
| SoC | Rockchip RK3528A (quad Cortex-A53, 1.8 GHz) |
| RAM | 1 GB LPDDR4 |
| Storage | 116 GB SD card (mmc1) |
| Ethernet | RTL8153A USB Gigabit (USB 1-1.2.1) |
| WiFi/BT | AIC8800D80 USB combo (USB 1-1.4), a69c:8d80 → 8d81 post-firmware |
| Kernel | 6.1.115-rockchip-standard (vendor, Nov 25 2025 build) |
| Build system | Yocto Poky scarthgap 5.0.12 |
| Custom layer | `meta-firewire-recorder` |

---

## Work Completed

### Fix 1 — PCIe boot stall (30-second hang)

**Symptom:** Every boot hung for 30 seconds inside `rk_pcie_establish_link()` waiting for LTSSM.  
**Root cause:** `rockchip,skip-reset-in-config` DTS property missing from the ROCK 2F PCIe node.  
**Fix:** Added `0007-arm64-dts-rockchip-rk3528-rock-2f-add-pcie-default-link-up.patch` in
`meta-rockchip/recipes-kernel/linux/linux-rockchip_6.1/`.  
**Verification:** Boot log shows `rk-pcie fe4f0000.pcie: skip reset controller` — stall gone.

```diff
+&pcie2x1 {
+    rockchip,skip-reset-in-config;
+};
```

---

### Fix 2 — AIC8800D80 WiFi/BT drivers

**Symptom:** No WiFi or Bluetooth — no kernel modules for AIC8800D80 out-of-tree driver.  
**Approach:** Created two new Yocto recipes.

#### `recipes-kernel/aic8800/aic8800-firmware.bb`

Clones `radxa-pkg/aic8800` from GitHub and installs firmware blobs to
`/lib/firmware/aic8800D80/` — the path hardcoded in the AIC8800 driver.

Key fixes during development:
- `inherit allarch` required (not `PACKAGE_ARCH = "all"`) — avoids sstate manifest naming mismatch.
- Path must be `aic8800D80/` directly under `/lib/firmware/`, **not** `aic8800/USB/aic8800D80/` (first attempt).

#### `recipes-kernel/aic8800/kernel-module-aic8800.bb`

Builds three out-of-tree modules (`aic_load_fw.ko`, `aic8800_fdrv.ko`, `aic_btusb.ko`).

Bugs fixed:
| Bug | Fix |
|---|---|
| `-Werror` failures (implicit-fallthrough, address) | `EXTRA_CFLAGS="-Wno-error"` on all 3 `make` calls |
| `compat_ptr` redefinition on arm64 | `CONFIG_PLATFORM_UBUNTU=n CONFIG_PLATFORM_ROCKCHIP=y` on aic_btusb build |
| stale sstate manifest naming | `inherit allarch` in firmware recipe |

Module load order (written to `/etc/modules-load.d/aic8800.conf`):
```
aic_load_fw     # firmware loader — must go first (exports symbols)
aic8800_fdrv    # WiFi MAC/PHY driver (depends on aic_load_fw exports)
aic_btusb       # vendor BT driver (replaces mainline btusb for a69c:8d80)
```

Mainline `btusb` blacklisted via `/etc/modprobe.d/aic_btusb.conf`.

---

### Fix 3 — AIC8800D80 firmware path

**Symptom:** `aic_load_firmware: fw_patch_table_8800d80_u02.bin file failed to open`  
**Root cause:** Firmware recipe installed to `aic8800/USB/aic8800D80/` but driver looks for `aic8800D80/`.  
**Fix:** Changed `FWDIR` in `aic8800-firmware.bb` from:
```bitbake
FWDIR = "${D}${nonarch_base_libdir}/firmware/aic8800/USB/aic8800D80"
```
to:
```bitbake
FWDIR = "${D}${nonarch_base_libdir}/firmware/aic8800D80"
```
**Verification:** Boot log shows all 5 firmware files loading successfully (md5 checksums match).

---

## Current Boot State (as of latest image)

### ✅ Working

| Component | Evidence |
|---|---|
| PCIe (clean fail, no hang) | `rk-pcie fe4f0000.pcie: skip reset controller` |
| AIC8800 firmware loading | All 5 files downloaded, chip re-enumerates as `a69c:8d81` |
| WiFi `wlan0` | `New interface create wlan0` at [8.19s]; up after `rfkill unblock wifi` |
| WiFi driver | `usbcore: registered new interface driver aic8800_fdrv`, claims AIC8800D81 |
| BT driver | `usbcore: registered new interface driver aic_btusb`; `bluetoothd` running, MGMT ver 1.22 |
| Ethernet `eth0` | RTL8153A, gets DHCP `192.168.1.220`, IPv4+IPv6 |
| SSH access | `sshd` running, authorized key login works |
| connman | Starts, manages eth0 |
| wireless-regdb | Installed; early boot -2 error is pre-rootfs (expected, retried on interface up) |

### ⚠️ Known Issues / Next Steps

| Issue | Priority | Details |
|---|---|---|
| WiFi RF-killed on boot | HIGH | `ip link set wlan0 up` blocked until `rfkill unblock wifi`; connman needs wifi plugin config |
| `iw` not in image | HIGH | `iw: command not found` — needed for scan/connect |
| GPT partition table | MEDIUM | Secondary header not at card end; harmless but `parted` needed for resize-on-first-boot |
| `hwclock` errors | LOW | `can't open '/dev/misc/rtc'` — ROCK 2F has no RTC; hwclock init script should be masked |
| `app_cmp` trace spam | LOW | Vendor kernel has `trace_printk()` active in aic_load_fw (DEBUG kernel banner); ~20 lines/boot |
| `rtl8153a-4.fw` missing | LOW | ETH works without it; add `linux-firmware-rtl-nic` to suppress warning |
| PCIe endpoint absent | INFO | LTSSM 0x1 — no PCIe device connected, expected on ROCK 2F without add-on card |
| BT `hci0` status | INFO | `aic_btusb` has `0x8d81` in USB ID table; bluetoothd running, verify with `bluetoothctl` |

---

## AIC8800 USB Re-enumeration Details

Normal AIC8800D80 USB lifecycle:
```
Power on  →  a69c:8d80 appears  →  aic_load_fw binds, uploads 5 firmware files
          →  chip resets         →  a69c:8d81 appears (post-firmware PID)
          →  aic8800_fdrv binds  →  wlan0 created
          →  aic_btusb binds     →  hci0 created (if BT interface matches)
```

`aic_load_fw: probe of 1-1.4:1.2 failed with error -1` on the 8d81 device is **expected** —
`aic_load_fw` attempts to probe 8d81 but returns -1 because firmware is already loaded.

`USB_PRODUCT_ID_AIC8800D80` is defined as `0x8d81` in `aic_btusb.c` (line 63).

---

## File Inventory

```
meta-firewire-recorder/
├── conf/
│   └── layer.conf
├── recipes-images/images/
│   └── firewire-recorder-image.bb        # Main image recipe
├── recipes-kernel/aic8800/
│   ├── aic8800-firmware.bb               # Firmware blobs → /lib/firmware/aic8800D80/
│   └── kernel-module-aic8800.bb          # 3 out-of-tree kernel modules
├── recipes-connectivity/connman/
│   ├── connman_%.bbappend                # Installs connman main.conf
│   └── connman/main.conf                 # Enables WiFi plugin, unblocks via connman
└── recipes-core/init-scripts/
    └── rock2f-tweaks.bb                  # Init scripts: rfkill unblock + hwclock disable

meta-rockchip/recipes-kernel/linux/linux-rockchip_6.1/
└── 0007-arm64-dts-rockchip-rk3528-rock-2f-add-pcie-default-link-up.patch
```

---

## Reproducing the Build

```bash
cd /build/alex/radxa/poky
. oe-init-build-env build
bitbake firewire-recorder-image
```

Output image: `build/tmp/deploy/images/rockchip-rk3528-rock-2f/firewire-recorder-image-rockchip-rk3528-rock-2f.rootfs.update.img`

Flash with `rkdeveloptool` (board in maskrom mode):
```bash
rkdeveloptool db path/to/MiniLoaderAll.bin
rkdeveloptool wl 0 firewire-recorder-image-*.update.img
rkdeveloptool rd
```

---

## Hardware Test Utilities (in image)

| Tool | Package | Purpose |
|---|---|---|
| `iw` | `iw` | WiFi interface management, scan, connect |
| `rfkill` | `rfkill` | Unblock/block WiFi and BT radios |
| `wpa_supplicant` | `wpa-supplicant` | WPA2/WPA3 authentication |
| `connmanctl` | `connman` | connman CLI: enable/power technologies |
| `bluetoothctl` | `bluez5` | BT device scanning, pairing, connect |
| `lsusb` | `usbutils` | List USB devices (verify AIC8800 PID) |
| `lspci` | `pciutils` | List PCIe devices |
| `parted` | `parted` | GPT partition resize on first boot |
| `lsblk`, `blkid`, `fdisk` | `util-linux` | Block device inspection |
| `ethtool` | `ethtool` | Ethernet diagnostics |
| `htop` | `htop` | Process monitor |
| `iperf3` | `iperf3` | Network throughput testing |
| `i2c-detect` | `i2c-tools` | I2C bus scan |
| `nano` | `nano` | On-device text editing |
| `less` | `less` | Log pager |

---

## Quick WiFi Test Procedure

```bash
# After boot — wlan0 is RF-killed by default (vendor driver behaviour)
rfkill unblock wifi
ip link set wlan0 up

# Scan for networks
iw dev wlan0 scan | grep SSID

# Connect via wpa_supplicant
wpa_passphrase "MySSID" "MyPassword" > /etc/wpa_supplicant.conf
wpa_supplicant -B -i wlan0 -c /etc/wpa_supplicant.conf
udhcpc -i wlan0

# Or use connman
connmanctl enable wifi
connmanctl scan wifi
connmanctl services
connmanctl connect wifi_XXXX
```

## Quick Bluetooth Test Procedure

```bash
bluetoothctl
[bluetooth]# power on
[bluetooth]# scan on
[bluetooth]# devices
[bluetooth]# pair XX:XX:XX:XX:XX:XX
```

---

## Next Steps

1. **Auto-unblock WiFi on boot** — connman `main.conf` or rfkill init script (see `rock2f-tweaks`)
2. **First-boot GPT resize** — `parted /dev/mmcblk1 resizepart 3 100%` + `resize2fs`
3. **Suppress `app_cmp` trace spam** — patch `trace_printk("app_cmp\n")` → `pr_debug()` in aic_load_fw source, or disable tracing via kernel cmdline (`trace_options=nosym-userobj`)
4. **Validate hci0** — confirm BT with `bluetoothctl power on; scan on` after full image deploy
5. **ConnMan WiFi provisioning** — add static `/var/lib/connman/<network>.config` for target WiFi network
6. **Application layer** — IEEE 1394 FireWire stack (novatek NVT72172 capture, ffmpeg pipeline)
