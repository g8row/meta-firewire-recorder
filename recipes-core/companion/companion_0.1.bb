SUMMARY = "equip-1 companion: HTTP API, web UI, and BLE provisioning daemon"
DESCRIPTION = "Two statically-linked Go binaries – companion-api (HTTP server + \
embedded React UI + mediamtx lifecycle manager) and companion-net (BLE GATT \
server + ConnMan WiFi provisioning) – plus the mediamtx arm64 binary and their \
systemd units. \
The Go binaries are cross-compiled outside Yocto (CGO_ENABLED=0); copy the \
pre-built arm64 executables into files/ before baking the image. See the \
deploy/ directory in the companion repository and the cross-compile section of \
its README."
HOMEPAGE = "https://github.com/g8row/equip-1"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# ---------------------------------------------------------------------------
# Pre-built binary preparation (run before `bitbake firewire-recorder-image`)
# ---------------------------------------------------------------------------
# 1. Build the companion binaries:
#      cd companion/server
#      GOOS=linux GOARCH=arm64 CGO_ENABLED=0 \
#        go build -o ../deploy/bin/companion-api ./cmd/companion-api
#      GOOS=linux GOARCH=arm64 CGO_ENABLED=0 \
#        go build -o ../deploy/bin/companion-net ./cmd/companion-net
#
# 2. Download mediamtx arm64 release (v1.19.2 or later):
#      curl -L https://github.com/bluenviron/mediamtx/releases/download/v1.19.2/\
#        mediamtx_v1.19.2_linux_arm64v8.tar.gz | tar -C companion/deploy/bin -xz mediamtx
#
# 3. Copy all into this recipe's files/ directory:
#      cp companion/deploy/bin/companion-api  <this-layer>/recipes-core/companion/files/
#      cp companion/deploy/bin/companion-net  <this-layer>/recipes-core/companion/files/
#      cp companion/deploy/bin/mediamtx      <this-layer>/recipes-core/companion/files/
#      cp companion/deploy/mediamtx.yml      <this-layer>/recipes-core/companion/files/
# ---------------------------------------------------------------------------

SRC_URI = " \
    file://companion-api \
    file://companion-net \
    file://mediamtx \
    file://companion-api.service \
    file://companion-net.service \
    file://mediamtx.service \
    file://rfkill-unblock.service \
    file://mediamtx.yml \
"

inherit systemd

# Enable all companion + infrastructure services at image boot.
# rfkill-unblock must run before bluetooth.service (AIC8800 quirk).
SYSTEMD_SERVICE:${PN} = " \
    rfkill-unblock.service \
    companion-api.service \
    companion-net.service \
    mediamtx.service \
"
SYSTEMD_AUTO_ENABLE = "enable"

# bluez5 provides bluetooth.service; connman provides WiFi management.
RDEPENDS:${PN} = "bluez5 connman systemd"

do_install() {
    # --- binaries ---
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/companion-api  ${D}${bindir}/companion-api
    install -m 0755 ${WORKDIR}/companion-net  ${D}${bindir}/companion-net
    install -m 0755 ${WORKDIR}/mediamtx       ${D}${bindir}/mediamtx

    # --- systemd unit files ---
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/companion-api.service   ${D}${systemd_system_unitdir}/
    install -m 0644 ${WORKDIR}/companion-net.service   ${D}${systemd_system_unitdir}/
    install -m 0644 ${WORKDIR}/mediamtx.service        ${D}${systemd_system_unitdir}/
    install -m 0644 ${WORKDIR}/rfkill-unblock.service  ${D}${systemd_system_unitdir}/

    # --- mediamtx config ---
    install -d ${D}${sysconfdir}
    install -m 0644 ${WORKDIR}/mediamtx.yml ${D}${sysconfdir}/mediamtx.yml

    # --- enable bluetooth.service so BLE is ready before companion-net starts ---
    # bluetooth.service is owned by bluez5; we enable it from here so our
    # single recipe handles the full companion bring-up dependency.
    install -d ${D}${systemd_system_unitdir}/multi-user.target.wants
    ln -sf ${systemd_system_unitdir}/bluetooth.service \
        ${D}${systemd_system_unitdir}/multi-user.target.wants/bluetooth.service
}

FILES:${PN} += " \
    ${bindir}/companion-api \
    ${bindir}/companion-net \
    ${bindir}/mediamtx \
    ${systemd_system_unitdir}/companion-api.service \
    ${systemd_system_unitdir}/companion-net.service \
    ${systemd_system_unitdir}/mediamtx.service \
    ${systemd_system_unitdir}/rfkill-unblock.service \
    ${systemd_system_unitdir}/multi-user.target.wants/bluetooth.service \
    ${sysconfdir}/mediamtx.yml \
"
