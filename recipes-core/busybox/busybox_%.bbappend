# Override busybox hwclock.sh with a no-op for ROCK 2F (no on-board RTC)
FILESEXTRAPATHS:prepend := "${THISDIR}/${BPN}:"
