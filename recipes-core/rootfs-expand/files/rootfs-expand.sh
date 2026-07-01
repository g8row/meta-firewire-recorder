#!/bin/sh
# Idempotent rootfs partition + filesystem grow-to-fill-disk. Run once at
# first boot by rootfs-expand.service (guarded by a sentinel file so it
# doesn't redo the work — and the disk I/O — on every subsequent boot).
#
# Two separate things need growing:
#   1. The GPT partition table entry for rootfs, which wic sizes to fit the
#      built image at build time, not the physical SD card/eMMC it ends up
#      flashed onto.
#   2. The ext4 filesystem within that partition, to fill the (now larger)
#      partition. x-systemd.growfs in the wks fsoptions also tries to do
#      this automatically very early in boot, but only once the partition
#      itself is already large enough — step 1 here is the prerequisite
#      that nothing else in this image was doing.
set -e

ROOT_SRC=$(findmnt -no SOURCE /)

# Derive the parent disk and partition number, handling both /dev/sdaN and
# /dev/mmcblkNpM / /dev/nvmeNn1pM naming schemes.
case "$ROOT_SRC" in
    *mmcblk*p[0-9]*|*nvme*n[0-9]p[0-9]*)
        PART_NUM=$(echo "$ROOT_SRC" | grep -o 'p[0-9]*$' | tr -d 'p')
        DISK=$(echo "$ROOT_SRC" | sed -E 's/p[0-9]+$//')
        ;;
    *[0-9])
        PART_NUM=$(echo "$ROOT_SRC" | grep -o '[0-9]*$')
        DISK=$(echo "$ROOT_SRC" | sed -E 's/[0-9]+$//')
        ;;
    *)
        PART_NUM=""
        DISK=""
        ;;
esac

if [ -z "$DISK" ] || [ -z "$PART_NUM" ]; then
    echo "rootfs-expand: could not determine root disk/partition from '$ROOT_SRC', skipping"
    exit 0
fi

echo "rootfs-expand: root=$ROOT_SRC disk=$DISK partition=$PART_NUM"

# When the image is smaller than the physical card, GPT's backup header
# sits at the position matching the image's (smaller) size, not the real
# disk. parted then refuses to resize with "Unable to satisfy all
# constraints on the partition" — it has an interactive "Fix the GPT to
# use the whole disk?" prompt for exactly this, but --script can't answer
# it (confirmed live: this is what silently failed on-device — resizepart
# errored, the script swallowed it via `|| true` below, and the sentinel
# file still got touched, so it never actually grew). sgdisk -e relocates
# the backup GPT to the true end of the disk first, non-interactively.
sgdisk -e "$DISK" || true

# Grow the partition table entry to use all remaining disk space. parted
# exits non-zero when there's nothing to grow into (already maximal) —
# that's an expected steady-state outcome on every boot after the first,
# not a failure.
parted --script "$DISK" resizepart "$PART_NUM" 100% || true

# Make the kernel re-read the (now larger) partition table. Extending the
# size of the currently-mounted root partition is safe to do online — the
# same approach cloud-init's growpart uses on every major Linux distro.
partprobe "$DISK" 2>/dev/null || true

# Grow the ext4 filesystem to fill the (now larger) partition. Online
# resize of a mounted root filesystem is fully supported by resize2fs.
resize2fs "$ROOT_SRC" || true

echo "rootfs-expand: done"
