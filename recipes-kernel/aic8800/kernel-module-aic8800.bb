SUMMARY = "AIC8800D80 USB WiFi and Bluetooth out-of-tree kernel modules"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://src/LICENSE;md5=570a9b3749dd0463a1778803b12a6dce"

inherit module

SRC_URI = "git://github.com/radxa-pkg/aic8800.git;protocol=https;branch=main;nobranch=1"
SRCREV = "7af8ef10bd0222a4e1ee54c25904d5e0e3ca37f7"

PV = "4.0+git${SRCPV}"
S = "${WORKDIR}/git"

# USB WiFi common flags
AIC_USB_FLAGS = " \
    CONFIG_USB_SUPPORT=y \
    CONFIG_USB_BT=y \
    CONFIG_USB_MSG_EP=y \
    CONFIG_LINK_DET_5G=y \
    CONFIG_USE_FW_REQUEST=n \
    CONFIG_PREALLOC_RX_SKB=n \
    CONFIG_PREALLOC_TXQ=y \
"

do_compile() {
    LOADFW_DIR="${S}/src/USB/driver_fw/drivers/aic8800/aic_load_fw"
    FDRV_DIR="${S}/src/USB/driver_fw/drivers/aic8800/aic8800_fdrv"
    BTUSB_DIR="${S}/src/USB/driver_fw/drivers/aic_btusb"

    # Step 1: aic_load_fw — firmware loader (must go first, exports symbols)
    make -C ${STAGING_KERNEL_DIR} M="${LOADFW_DIR}" \
        ARCH=${ARCH} CROSS_COMPILE=${CROSS_COMPILE} \
        CONFIG_AIC_LOADFW_SUPPORT=m \
        ${AIC_USB_FLAGS} \
        EXTRA_CFLAGS="-Wno-error" \
        modules

    # Step 2: aic8800_fdrv — main WiFi MAC/PHY driver
    #         Needs aic_load_fw's Module.symvers for exported symbols
    #         EXTRA_CFLAGS=-Wno-error: vendor code triggers multiple -Werror warnings
    #         (implicit-fallthrough, address, etc.) that we don't own
    make -C ${STAGING_KERNEL_DIR} M="${FDRV_DIR}" \
        ARCH=${ARCH} CROSS_COMPILE=${CROSS_COMPILE} \
        KBUILD_EXTRA_SYMBOLS="${LOADFW_DIR}/Module.symvers" \
        CONFIG_AIC8800_WLAN_SUPPORT=m \
        ${AIC_USB_FLAGS} \
        EXTRA_CFLAGS="-Wno-error" \
        modules

    # Step 3: aic_btusb — vendor BT driver (replaces btusb for a69c:8d80)
    #         Makefile defaults CONFIG_PLATFORM_UBUNTU=y which redefines compat_ptr
    #         on arm64; override to Rockchip platform instead
    make -C ${STAGING_KERNEL_DIR} M="${BTUSB_DIR}" \
        ARCH=${ARCH} CROSS_COMPILE=${CROSS_COMPILE} \
        CONFIG_AIC8800_BTUSB_SUPPORT=m \
        CONFIG_USE_FW_REQUEST=n \
        CONFIG_PLATFORM_UBUNTU=n \
        CONFIG_PLATFORM_ROCKCHIP=y \
        EXTRA_CFLAGS="-Wno-error" \
        modules
}

do_install() {
    LOADFW_DIR="${S}/src/USB/driver_fw/drivers/aic8800/aic_load_fw"
    FDRV_DIR="${S}/src/USB/driver_fw/drivers/aic8800/aic8800_fdrv"
    BTUSB_DIR="${S}/src/USB/driver_fw/drivers/aic_btusb"
    MODDIR="${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/kernel/drivers/net/wireless/aic8800"

    install -d "${MODDIR}"
    install -m 0644 "${LOADFW_DIR}/aic_load_fw.ko"   "${MODDIR}/"
    install -m 0644 "${FDRV_DIR}/aic8800_fdrv.ko"    "${MODDIR}/"
    install -m 0644 "${BTUSB_DIR}/aic_btusb.ko"      "${MODDIR}/"

    # Blacklist the mainline btusb so aic_btusb takes the a69c:8d80 interface
    install -d "${D}${sysconfdir}/modprobe.d"
    echo "blacklist btusb" > "${D}${sysconfdir}/modprobe.d/aic_btusb.conf"

    # Load order: aic_load_fw must come before aic8800_fdrv
    install -d "${D}${sysconfdir}/modules-load.d"
    printf "aic_load_fw\naic8800_fdrv\naic_btusb\n" \
        > "${D}${sysconfdir}/modules-load.d/aic8800.conf"
}

FILES:${PN} += " \
    ${sysconfdir}/modprobe.d/aic_btusb.conf \
    ${sysconfdir}/modules-load.d/aic8800.conf \
"

# Do not stage modules — kernel-module-split handles packaging
RPROVIDES:${PN} += "kernel-module-aic-load-fw kernel-module-aic8800-fdrv kernel-module-aic-btusb"
