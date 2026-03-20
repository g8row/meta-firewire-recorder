SUMMARY = "A lean image for a portable FireWire recorder appliance."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

EXTRA_IMAGE_FEATURES += "package-management ssh-server-openssh"

IMAGE_INSTALL:append = " \
    openssh \
    connman \
    python3 \
    python3-pip \
    i2c-tools \
    e2fsprogs-resize2fs \
    wireless-regdb \
    kernel-module-aic8800 \
    rkwifibt-firmware-aic8800d80-usb \
    rock2f-tweaks \
    iw \
    rfkill \
    wpa-supplicant \
    bluez5 \
    usbutils \
    pciutils \
    parted \
    util-linux \
    ethtool \
    htop \
    iperf3 \
    linux-firmware-rtl-nic \
    less \
    nano \
    vim \
    connman-tools \
    ffmpeg \
    dvgrab \
"

IMAGE_LINGUAS = " "

inherit core-image

# ---------------------------------------------------------------------------
# Boot-time optimisations
# ---------------------------------------------------------------------------

# 1. Pre-generate SSH host keys at image build time.
#    sshd normally generates these on first boot (RSA 3072-bit on a Cortex-A53
#    takes ~3 seconds). Baking them in makes every boot equally fast.
#    Keys are host-specific only in the sense that they identify this image
#    instance; for a dedicated appliance that is fine.
pregenearte_ssh_host_keys() {
    SSH_DIR="${IMAGE_ROOTFS}/etc/ssh"
    for type in rsa ecdsa ed25519; do
        keyfile="${SSH_DIR}/ssh_host_${type}_key"
        if [ ! -f "${keyfile}" ]; then
            ssh-keygen -t ${type} -q -N "" -f "${keyfile}"
            chmod 600 "${keyfile}"
            chmod 644 "${keyfile}.pub"
        fi
    done
}

# 2. Disable services that are not needed on a headless appliance:
#    rpcbind  — NFS portmapper
#    mountnfs — NFS client mounter
#    avahi    — mDNS/DNS-SD discovery
#    ofono    — telephony daemon (pulled in by bluez5 recommends)
disable_unused_services() {
    for svc in rpcbind mountnfs avahi-daemon ofono; do
        find ${IMAGE_ROOTFS}/etc -name "*${svc}*" -path "*/rc*.d/*" -delete
    done
}

ROOTFS_POSTPROCESS_COMMAND:append = " pregenearte_ssh_host_keys; disable_unused_services; "
