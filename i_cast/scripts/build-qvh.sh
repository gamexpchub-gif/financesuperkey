#!/usr/bin/env bash
# Build the qvh USB screen-capture engine for iCast, pinned and patched.
#
# Why a patch: every qvh command first calls checkDeviceIsPaired(), which needs usbmuxd and
# exits if it is unreachable. But Linux only lets qvh switch the iPhone into screen-capture
# mode while usbmuxd is NOT holding the iPhone ("interface claimed by usbfs while 'qvh' sets
# config"). iCast checks pairing itself (via `qvh activate`, with usbmuxd running), pauses
# usbmuxd, then runs `qvh gstreamer` with QVH_SKIP_PAIR_CHECK=1 so the check is skipped.
#
# usage: build-qvh.sh <output-binary>
set -euo pipefail

QVH_REPO="https://github.com/danielpaulus/quicktime_video_hack"
QVH_COMMIT="d81396e2e7758d98c2a594853b64f98b54a8a871"   # v0.x master, 2023-05-04
OUT="${1:?usage: build-qvh.sh <output-binary>}"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
git -C "$WORK" init -q qvh
git -C "$WORK/qvh" fetch -q --depth 1 "$QVH_REPO" "$QVH_COMMIT"
git -C "$WORK/qvh" checkout -q FETCH_HEAD

MAIN="$WORK/qvh/main.go"
grep -q '^	checkDeviceIsPaired(device)$' "$MAIN" || { echo "build-qvh: unexpected qvh source, patch not applied" >&2; exit 1; }
sed -i 's/^	checkDeviceIsPaired(device)$/	if os.Getenv("QVH_SKIP_PAIR_CHECK") == "" { \/\/ iCast patch\n		checkDeviceIsPaired(device)\n	}/' "$MAIN"
grep -q 'QVH_SKIP_PAIR_CHECK' "$MAIN" || { echo "build-qvh: patch failed" >&2; exit 1; }

(cd "$WORK/qvh" && go build -o "$WORK/qvh-bin" .)
install -m 0755 "$WORK/qvh-bin" "$OUT"
echo "build-qvh: built patched qvh ($QVH_COMMIT) -> $OUT"
