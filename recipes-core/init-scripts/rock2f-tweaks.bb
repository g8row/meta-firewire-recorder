SUMMARY = "ROCK 2F boot quirk fixes: rfkill unblock at runlevel 5"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit allarch update-rc.d

INITSCRIPT_PACKAGES = "${PN}"
INITSCRIPT_NAME:${PN} = "rock2f-tweaks"
# Start at S99 in runlevel 5 (after network/bluetooth services)
INITSCRIPT_PARAMS:${PN} = "start 99 5 . stop 01 0 6 ."

SRC_URI = "file://rock2f-tweaks.init"

do_install() {
    install -d "${D}${sysconfdir}/init.d"
    install -m 0755 "${WORKDIR}/rock2f-tweaks.init" "${D}${sysconfdir}/init.d/rock2f-tweaks"
}

FILES:${PN} = " \
    ${sysconfdir}/init.d/rock2f-tweaks \
    ${sysconfdir}/rc5.d \
    ${sysconfdir}/rc0.d \
    ${sysconfdir}/rc6.d \
"
