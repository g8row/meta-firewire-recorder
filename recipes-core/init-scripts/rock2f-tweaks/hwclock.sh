#!/bin/sh
### BEGIN INIT INFO
# Provides:          hwclock
# Required-Start:
# Required-Stop:
# Default-Start:     S
# Default-Stop:
# Short-Description: Hardware clock sync (disabled — ROCK 2F has no RTC)
### END INIT INFO
#
# The Radxa ROCK 2F does not have an on-board RTC chip.
# /dev/misc/rtc is absent and hwclock would fail with:
#   hwclock: can't open '/dev/misc/rtc': No such file or directory
# This is a no-op replacement to suppress that error.
exit 0
