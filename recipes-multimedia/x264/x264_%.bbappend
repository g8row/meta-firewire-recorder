# wrynose's x264 enables its ffmpeg (lavf/swscale) integration by default,
# which creates an ffmpeg <-> x264 dependency cycle with our ffmpeg x264
# PACKAGECONFIG. We only need libx264 as an encoder library for ffmpeg.
PACKAGECONFIG:remove = "ffmpeg"
