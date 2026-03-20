SUMMARY = "Capture DV or MPEG-2 video from a FireWire (IEEE 1394) camcorder"
HOMEPAGE = "https://github.com/ddennedy/dvgrab"
LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://COPYING;md5=94d55d512a9ba36caa9b7df079bae19f"

SRC_URI = "git://github.com/ddennedy/dvgrab.git;protocol=https;branch=master;nobranch=1"
# v3.5 tag — pin to a specific commit for reproducible builds
SRCREV = "6a57bdaa7fbdb774f75fc12481af5df32b137ca3"

PV = "3.5+git"
S = "${WORKDIR}/git"

inherit autotools pkgconfig

DEPENDS = "libraw1394 libavc1394 libiec61883"
