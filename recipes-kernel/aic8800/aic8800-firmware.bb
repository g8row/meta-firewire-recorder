SUMMARY = "Firmware for AIC8800D80 USB WiFi/BT combo (Radxa ROCK 2F)"
LICENSE = "CLOSED"
LIC_FILES_CHKSUM = ""

SRC_URI = "git://github.com/radxa-pkg/aic8800.git;protocol=https;branch=main;nobranch=1"
SRCREV = "7af8ef10bd0222a4e1ee54c25904d5e0e3ca37f7"

PV = "4.0+git${SRCPV}"
S = "${WORKDIR}/git"

do_compile[noexec] = "1"

do_install() {
    # Path must match aic_default_fw_path/aic8800D80 as constructed in aicbluetooth.c
    FWDIR="${D}${nonarch_base_libdir}/firmware/aic8800D80"
    install -d "${FWDIR}"
    for f in ${S}/src/USB/driver_fw/fw/aic8800D80/*; do
        install -m 0644 "$f" "${FWDIR}/"
    done
}

FILES:${PN} = "${nonarch_base_libdir}/firmware/aic8800D80"

inherit allarch
