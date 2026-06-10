FILESEXTRAPATHS:prepend := "${THISDIR}/${BPN}:"

SRC_URI:append = " file://main.conf"

do_install:append() {
    install -d "${D}${sysconfdir}/connman"
    install -m 0644 "${UNPACKDIR}/main.conf" "${D}${sysconfdir}/connman/main.conf"
}

FILES:${PN}:append = " ${sysconfdir}/connman/main.conf"
