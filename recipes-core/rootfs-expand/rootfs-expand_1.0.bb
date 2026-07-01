SUMMARY = "Grow the rootfs partition and filesystem to fill the disk on first boot"
DESCRIPTION = "wic sizes the rootfs partition to fit the built image, not the \
physical SD card/eMMC it's flashed onto. This grows the GPT partition entry \
to use the rest of the disk (parted resizepart), then grows the ext4 \
filesystem to fill it (resize2fs). Guarded by a sentinel file so it only \
does real work once, at first boot. Complements x-systemd.growfs in the wks \
fsoptions, which only grows the filesystem within whatever partition size \
already exists — this recipe is what makes the partition itself large \
enough for that to matter."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://rootfs-expand.sh \
    file://rootfs-expand.service \
"

inherit systemd

SYSTEMD_SERVICE:${PN} = "rootfs-expand.service"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} = "parted e2fsprogs-resize2fs util-linux-partprobe util-linux-findmnt"

do_install() {
    install -d ${D}${sbindir}
    install -m 0755 ${WORKDIR}/rootfs-expand.sh ${D}${sbindir}/rootfs-expand.sh

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/rootfs-expand.service ${D}${systemd_system_unitdir}/
}

FILES:${PN} += " \
    ${sbindir}/rootfs-expand.sh \
    ${systemd_system_unitdir}/rootfs-expand.service \
"
