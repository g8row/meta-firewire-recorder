# Root cause for AP/tethering "Invalid arguments" from ConnMan (2026-06-30):
# connmand shells out to /usr/sbin/dnsmasq to serve DHCP/DNS to tethered
# clients on the AP interface. The base image never installed dnsmasq, so
# every `connmanctl tether wifi on ...` failed with
# "Error enabling wifi tethering: Not supported", which the companion API
# surfaces as a generic "Invalid arguments" 502. AIC8800D80 + ConnMan both
# support AP mode fine (`iw phy0 info` lists AP in supported interface
# modes) — dnsmasq was simply missing.
#
# Keep this minimal: this appliance only needs basic DHCP/DNS for AP
# clients, not conntrack/nftables integration. Disabling those avoids
# pulling in libnetfilter-conntrack/libnftables and their transitive deps,
# which a Debian-built dnsmasq binary needed and this distro's package set
# doesn't otherwise carry.
PACKAGECONFIG:remove = "conntrack nftset"
