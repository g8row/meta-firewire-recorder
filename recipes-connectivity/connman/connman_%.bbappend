FILESEXTRAPATHS:prepend := "${THISDIR}/${BPN}:"

# Firewall backend: nftables, not the oe-core default of iptables.
#
# ConnMan's WiFi tethering programs the masquerade/NAT rule itself through
# its in-process firewall backend (src/firewall-{iptables,nftables}.c). The
# oe-core default (--with-firewall=iptables) talks to the kernel's legacy
# ip_tables/iptable_nat interface — which the mainline 6.18 kernel used here
# does NOT provide (it ships an nftables-only netfilter set; there are no
# ip_tables.ko/iptable_nat.ko modules to install). That mismatch is what
# produced the live "iptables support missing (Protocol not available)" /
# "Cannot enable NAT" tethering failure.
#
# Switching to --with-firewall=nftables makes ConnMan program rules via
# libnftnl/netlink against nf_tables (built into this kernel), backed by the
# nft_nat/nft_masq/nft_chain_nat modules the kernel actually builds. The
# nftables PACKAGECONFIG declares a conflict with iptables, so the latter
# must be removed. libnftnl (meta-networking) and libmnl (oe-core) are both
# already in the layer set.
PACKAGECONFIG:remove = "iptables"
PACKAGECONFIG:append = " nftables"

SRC_URI:append = " file://main.conf"

do_install:append() {
    install -d "${D}${sysconfdir}/connman"
    install -m 0644 "${UNPACKDIR}/main.conf" "${D}${sysconfdir}/connman/main.conf"
}

FILES:${PN}:append = " ${sysconfdir}/connman/main.conf"
