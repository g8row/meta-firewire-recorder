# Enable software H.264 encoding fallback via libx264.
# x264 requires the gpl flag in ffmpeg.
# Enable libv4l2 integration so FFmpeg can use V4L2/plugin-based paths
# provided by the Rockchip userspace stack (v4l-rkmpp).
PACKAGECONFIG:append = " gpl x264 v4l2"
