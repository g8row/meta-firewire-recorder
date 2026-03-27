# Enable software H.264 encoding fallback via libx264.
# x264 requires the gpl flag in ffmpeg.
# Enable libv4l2 integration so FFmpeg can use V4L2/plugin-based paths
# provided by the Rockchip userspace stack (v4l-rkmpp).

# FireWire/IEEE-1394 support:
# - libiec61883 for DV/HDV-over-1394 ingest (depends on raw1394/avc1394)
# - libdc1394 for IIDC camera capture over 1394
PACKAGECONFIG[iec61883] = "--enable-libiec61883,--disable-libiec61883,libiec61883 libraw1394 libavc1394"
PACKAGECONFIG[libdc1394] = "--enable-libdc1394,--disable-libdc1394,libdc1394"

PACKAGECONFIG:append = " gpl x264 v4l2 iec61883 libdc1394"
    