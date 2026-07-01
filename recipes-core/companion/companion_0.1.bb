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
# Still ahead of origin/main as of this pin (2026-07-01) — nothing pushed yet,
# so this recipe can't actually fetch until that happens. Update to whatever
# the pushed HEAD is at push time; don't assume this hash is current.
SRCREV = "8f96281837890cfb4b2ca96580d0acafe0dbcfa6"

MEDIAMTX_VERSION = "1.19.2"

SRC_URI = " \
    git://github.com/g8row/equip-1.git;protocol=https;branch=main \
    https://github.com/bluenviron/mediamtx/releases/download/v${MEDIAMTX_VERSION}/mediamtx_v${MEDIAMTX_VERSION}_linux_arm64v8.tar.gz;name=mediamtx;subdir=mediamtx-release \
    file://companion-api.service \
    file://companion-net.service \
    file://mediamtx.service \
    file://rfkill-unblock.service \
    file://mediamtx.yml \
"

# TODO: verify and fill in — run `bitbake companion -c fetch` once, bitbake
# will print the actual sha256 mismatch error with the correct value to paste
# here. Left blank deliberately rather than guessing.
SRC_URI[mediamtx.sha256sum] = ""

# The repo root is the monorepo `equip-1`; the Go module lives under companion/server.
S = "${WORKDIR}/git/companion/server"

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
    install -m 0755 ${WORKDIR}/mediamtx-release/mediamtx ${D}${bindir}/mediamtx

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
    install -d ${D}${systemd_system_unitdir}/multi-user.target.wants
    ln -sf ${systemd_system_unitdir}/bluetooth.service \
        ${D}${systemd_system_unitdir}/multi-user.target.wants/bluetooth.service
}

SYSTEMD_SERVICE:${PN} = " \
    rfkill-unblock.service \
    companion-api.service \
    companion-net.service \
    mediamtx.service \
"
SYSTEMD_AUTO_ENABLE = "enable"

RDEPENDS:${PN} = "bluez5 connman systemd"

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
