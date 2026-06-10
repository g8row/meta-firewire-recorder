# FireWire (IEEE-1394) + V4L2 m2m kernel support for the recorder appliance.
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"
SRC_URI:append:radxa-rock-2 = " file://firewire.cfg"
