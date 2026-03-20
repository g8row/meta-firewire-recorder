#!/bin/sh
### BEGIN INIT INFO
# Provides:          hwclock
# Required-Start:
# Required-Stop:     $local_fs
# Default-Start:     S
# Default-Stop:      0 6
# Short-Description: Hardware clock sync (disabled — ROCK 2F has no RTC)
### END INIT INFO
#
# The Radxa ROCK 2F (RK3528A) does not have an on-board RTC chip.
# /dev/misc/rtc is absent; running hwclock would print:
#   hwclock: can't open '/dev/misc/rtc': No such file or directory
# This is an intentional no-op replacement.
exit 0
