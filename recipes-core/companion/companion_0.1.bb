SUMMARY = "equip-1 companion: HTTP API, web UI, and BLE provisioning daemon"
DESCRIPTION = "Two Go binaries built from source by this recipe – companion-api \
(HTTP server + embedded React UI + mediamtx lifecycle manager) and \
companion-net (BLE GATT server + ConnMan WiFi provisioning) – plus the \
mediamtx relay (fetched as a prebuilt arm64 release, it's a large third-party \
project) and systemd units to run them all."
HOMEPAGE = "https://github.com/g8row/equip-1"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# Pin to a commit on g8row/equip-1 main. Bump after pushing companion changes:
#   git ls-remote https://github.com/g8row/equip-1.git main
# go.mod's directive is now "go 1.26" (was "go 1.26.4", a hard patch pin
# GOTOOLCHAIN=local rejected against OE's 1.26.2 go-native) — the
# `go mod edit -go=1.26` workaround below is no longer needed against
# this SRCREV or later.
SRCREV = "a16b3d3f9d78265e8a819a3c49a14535e556c86c"

MEDIAMTX_VERSION = "1.19.2"

# Asset is named "..._linux_arm64.tar.gz", NOT "..._linux_arm64v8" (that
# suffix is Docker's platform-tag convention, not this project's release
# naming — the wrong filename here would have 404'd on the very first
# fetch). Verified against the correct URL and cross-checked the sha256sum
# below against mediamtx's own published checksums.sha256 for this release.
SRC_URI = " \
    git://github.com/g8row/equip-1.git;protocol=https;branch=main \
    https://github.com/bluenviron/mediamtx/releases/download/v${MEDIAMTX_VERSION}/mediamtx_v${MEDIAMTX_VERSION}_linux_arm64.tar.gz;name=mediamtx;subdir=mediamtx-release \
    file://companion-api.service \
    file://companion-net.service \
    file://rfkill-unblock.service \
    file://99-rfkill-unblock.rules \
    file://mediamtx.yml \
    file://mediamtx-launch \
"

# Downloaded the asset directly and hashed it (shasum -a 256), then
# cross-checked against mediamtx's own published checksums.sha256 for
# v1.19.2 — both match.
SRC_URI[mediamtx.sha256sum] = "562f419912a8668c18216a9e8c95359ec82fbb754e4a44e2953ef62b98eec688"

# The repo root is the monorepo `equip-1`; the Go module lives under companion/server.
# Git checkouts now unpack to ${UNPACKDIR}/${BP} (BB_GIT_DEFAULT_DESTSUFFIX = "${BP}"),
# not the old ${WORKDIR}/git.
S = "${UNPACKDIR}/${BP}/companion/server"

inherit go systemd

GO_IMPORT = "equip1/companion/server"

# go.mod has no vendor/ dir checked in, so module resolution needs network
# access on first fetch. If your distro config sets BB_NO_NETWORK globally,
# this task-level override still requires PREMIRROR/proxy reachability for
# proxy.golang.org, or you must `go mod vendor` and check vendor/ into the
# repo so do_compile is fully offline.
do_compile[network] = "1"

# go.bbclass's default do_compile builds GO_IMPORT as a single package; we
# need two distinct binaries from cmd/, so override it.
do_compile() {
    export GOARCH GOOS CGO_ENABLED=0
    export GOFLAGS="-mod=mod -trimpath"
    cd ${S}
    ${GO} build -o ${B}/companion-api ./cmd/companion-api
    ${GO} build -o ${B}/companion-net ./cmd/companion-net
}

do_install() {
    # --- binaries built above ---
    install -d ${D}${bindir}
    install -m 0755 ${B}/companion-api ${D}${bindir}/companion-api
    install -m 0755 ${B}/companion-net ${D}${bindir}/companion-net

    # --- mediamtx, fetched as a prebuilt release (third-party, not ours) ---
    # No standalone mediamtx.service: companion-api is the mediamtx lifecycle
    # manager (cmd/companion-api spawns and supervises it), so a separate unit
    # would be a second instance colliding with companion-api's on UDP :8000
    # (default RTP port), crash-looping.
    #
    # But companion-api spawns mediamtx with NO config arg, and mediamtx's
    # config-less default REJECTS all publishing (RTSP publish -> 400), so the
    # stream never comes online. We must feed it /etc/mediamtx.yml (which has
    # `paths: all_others:`). mediamtx only auto-searches its CWD, never /etc, so
    # the mediamtx-launch wrapper passes the path explicitly; companion-api is
    # pointed at it via EQUIP_MEDIAMTX_BINARY in companion-api.service.
    install -m 0755 ${UNPACKDIR}/mediamtx-release/mediamtx ${D}${bindir}/mediamtx
    install -m 0755 ${UNPACKDIR}/mediamtx-launch ${D}${bindir}/mediamtx-launch

    # --- mediamtx config (paths: all_others: — required for publishing) ---
    install -d ${D}${sysconfdir}
    install -m 0644 ${UNPACKDIR}/mediamtx.yml ${D}${sysconfdir}/mediamtx.yml

    # --- systemd unit files ---
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/companion-api.service   ${D}${systemd_system_unitdir}/
    install -m 0644 ${UNPACKDIR}/companion-net.service   ${D}${systemd_system_unitdir}/
    install -m 0644 ${UNPACKDIR}/rfkill-unblock.service  ${D}${systemd_system_unitdir}/

    # --- udev rule: catches rfkill devices that register after
    #     rfkill-unblock.service's one-shot has already run and exited
    #     (confirmed live: AIC8800's Bluetooth rfkill entry appears later
    #     than WiFi's, lands soft-blocked, and nothing re-unblocks it) ---
    install -d ${D}${nonarch_base_libdir}/udev/rules.d
    install -m 0644 ${UNPACKDIR}/99-rfkill-unblock.rules \
        ${D}${nonarch_base_libdir}/udev/rules.d/

    # --- enable bluetooth.service so BLE is ready before companion-net starts ---
    install -d ${D}${systemd_system_unitdir}/multi-user.target.wants
    ln -sf ${systemd_system_unitdir}/bluetooth.service \
        ${D}${systemd_system_unitdir}/multi-user.target.wants/bluetooth.service
}

SYSTEMD_SERVICE:${PN} = " \
    rfkill-unblock.service \
    companion-api.service \
    companion-net.service \
"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} = "bluez5 connman systemd"

FILES:${PN} += " \
    ${bindir}/companion-api \
    ${bindir}/companion-net \
    ${bindir}/mediamtx \
    ${bindir}/mediamtx-launch \
    ${sysconfdir}/mediamtx.yml \
    ${systemd_system_unitdir}/companion-api.service \
    ${systemd_system_unitdir}/companion-net.service \
    ${systemd_system_unitdir}/rfkill-unblock.service \
    ${nonarch_base_libdir}/udev/rules.d/99-rfkill-unblock.rules \
    ${systemd_system_unitdir}/multi-user.target.wants/bluetooth.service \
"
