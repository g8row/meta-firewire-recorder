# Allow openssh-native so the image can pre-generate SSH host keys with the
# native ssh-keygen (the host's is not in HOSTTOOLS).
BBCLASSEXTEND:append = " native"
