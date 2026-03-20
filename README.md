# meta-firewire-recorder

Yocto layer for a portable FireWire recorder appliance on Rockchip RK3528.

## Dependencies

- poky (scarthgap)
- meta-rockchip

## Adding the layer

```bash
bitbake-layers add-layer meta-firewire-recorder
```

## Building

```bash
MACHINE=rockchip-rk3528-rock-2f bitbake firewire-recorder-image
```

## Features

- FireWire (IEEE 1394) support via kernel config fragment
- OpenSSH for remote access
- ConnMan for network management
- Python 3 runtime
