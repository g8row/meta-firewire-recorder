# oe-core master (2026-06): perl-ptest embeds the build host HOME in
# configure_path.sh, tripping the buildpaths QA error. Not our bug; demote
# to a warning until oe-core fixes it, then drop this append.
ERROR_QA:remove = "buildpaths"
WARN_QA:append = " buildpaths"
