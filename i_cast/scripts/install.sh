#!/usr/bin/env bash
# iCast installer for Debian / Ubuntu / Linux Mint / Pop!_OS.
# Installs: UxPlay (wireless), libimobiledevice + usbmuxd + qvh (wired), GStreamer, iCast itself.
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
say() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }

if ! command -v apt-get >/dev/null; then
  echo "This script supports apt-based distros. See README.md for Fedora/Arch package names." >&2
  exit 1
fi

say "Installing system packages (sudo)…"
sudo apt-get update
sudo apt-get install -y \
  python3 python3-venv python3-pip pipx fonts-inter \
  libxcb-cursor0 libxcb-icccm4 libxcb-keysyms1 libxcb-image0 libxcb-render-util0 libxkbcommon-x11-0 \
  uxplay avahi-daemon libavahi-compat-libdnssd1 \
  usbmuxd libimobiledevice-utils libusb-1.0-0 \
  gstreamer1.0-tools gstreamer1.0-plugins-base gstreamer1.0-plugins-good \
  gstreamer1.0-plugins-bad gstreamer1.0-libav gstreamer1.0-gl gstreamer1.0-pulseaudio \
  golang-go git ffmpeg pkg-config libgstreamer1.0-dev libgstreamer-plugins-base1.0-dev libusb-1.0-0-dev

say "Enabling Avahi (AirPlay discovery) and usbmuxd…"
sudo systemctl enable --now avahi-daemon || true
sudo systemctl restart usbmuxd 2>/dev/null || true

QVH_MARKER=/usr/local/share/icast/qvh-patched-d81396e
if [ ! -f "$QVH_MARKER" ] || ! command -v qvh >/dev/null; then
  say "Building qvh (USB screen-capture engine, patched for iCast)…"
  TMP_QVH="$(mktemp)"
  bash "$HERE/scripts/build-qvh.sh" "$TMP_QVH"
  sudo install -m 0755 "$TMP_QVH" /usr/local/bin/qvh
  rm -f "$TMP_QVH"
  sudo mkdir -p /usr/local/share/icast && sudo touch "$QVH_MARKER"
fi

say "Allowing USB access to iPhones without root…"
sudo tee /etc/udev/rules.d/39-icast-iphone.rules >/dev/null <<'RULE'
SUBSYSTEM=="usb", ATTR{idVendor}=="05ac", MODE="0666"
RULE
sudo udevadm control --reload-rules && sudo udevadm trigger || true

say "Installing the USB helper (lets iCast take exclusive control of the iPhone while casting)…"
sudo install -o root -g root -m 0755 "$HERE/scripts/icast-usb" /usr/local/libexec/icast-usb
RULE_USER="${SUDO_USER:-$USER}"
TMP_SUDOERS="$(mktemp)"
echo "$RULE_USER ALL=(root) NOPASSWD: /usr/local/libexec/icast-usb" > "$TMP_SUDOERS"
if sudo visudo -cf "$TMP_SUDOERS" >/dev/null; then
  sudo install -o root -g root -m 0440 "$TMP_SUDOERS" /etc/sudoers.d/icast-usb
else
  echo "warning: could not validate sudoers rule; wired casting will ask for your password" >&2
fi
rm -f "$TMP_SUDOERS"

if command -v ufw >/dev/null && sudo ufw status | grep -q active; then
  say "Opening firewall for AirPlay (mDNS + ports 7100-7102)…"
  sudo ufw allow 5353/udp
  sudo ufw allow 7100:7102/tcp
  sudo ufw allow 7100:7102/udp
fi

say "Installing iCast…"
pipx install --force "$HERE"
pipx ensurepath >/dev/null || true

say "Adding desktop launcher…"
mkdir -p "$HOME/.local/share/applications" "$HOME/.local/share/icons/hicolor/scalable/apps"
install -m 0644 "$HERE/packaging/icast.desktop" "$HOME/.local/share/applications/icast.desktop"
install -m 0644 "$HERE/packaging/icast.svg" "$HOME/.local/share/icons/hicolor/scalable/apps/icast.svg"

say "Done! Unplug and re-plug your iPhone, then run:  icast doctor   and   icast"
echo "Tip: with 'Fixed ports' enabled in iCast, use base port 7100 to match the firewall rules."
