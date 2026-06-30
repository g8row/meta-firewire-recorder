FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# btusb: add AICSEMI AIC8800D80 (a69c:8d81) with HCI_QUIRK_BROKEN_EXT_SCAN.
# The AIC8800D80 reports BT 5.4 but extended LE commands fail with -EBUSY;
# legacy LE scan/adv commands work correctly.
SRC_URI += "file://0001-btusb-Add-AIC8800D80-with-broken-extended-LE-quirks.patch"

# FireWire/IEEE1394 config is shared with the rockchip bbappend
SRC_URI += "file://firewire.cfg"
