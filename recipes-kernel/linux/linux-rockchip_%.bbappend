# Point to the directory containing your custom config fragment
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# Add your firewire.cfg to the list of source files for the kernel
SRC_URI += "file://firewire.cfg"

# Force PCIe Gen1 to prevent DWC auto-speed-change from collapsing the link
#SRC_URI += "file://pcie-gen1-only.patch"

# btusb: add AICSEMI AIC8800D80 with broken extended LE quirks
SRC_URI += "file://0001-btusb-Add-AIC8800D80-with-broken-extended-LE-quirks.patch"
