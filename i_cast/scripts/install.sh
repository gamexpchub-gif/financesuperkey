#!/usr/bin/env bash
# iCast installer for Debian / Ubuntu / Kali / Linux Mint / Pop!_OS.
# Installs: UxPlay (AirPlay), mpv (viewer), libimobiledevice + usbmuxd + qvh (USB), GStreamer, iCast itself.
#
# Robust by design: a package that does not exist on this distro is reported and skipped instead of
# aborting everything, old iCast processes/versions are removed first, and the end result is verified.
set -uo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
ME="${USER:-$(id -un)}"          # USER is not set in every terminal/session
HOME="${HOME:-$(getent passwd "$ME" | cut -d: -f6)}"
VERSION="$(sed -n 's/^version = "\(.*\)"/\1/p' "$HERE/pyproject.toml" | head -1)"
say()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m ✔\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m !\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m ✘ %s\033[0m\n' "$*"; }

if ! command -v apt-get >/dev/null; then
  fail "This installer supports apt-based distros (Debian, Ubuntu, Kali, Mint, Pop!_OS)."
  exit 1
fi
if [ "$(id -u)" -eq 0 ]; then
  fail "Run this as your normal user (not with sudo): ./scripts/install.sh"
  exit 1
fi

say "Installing iCast $VERSION from $HERE"

say "Stopping any running iCast…"
pkill -f "bin/icast" 2>/dev/null || true
pkill -x uxplay 2>/dev/null || true
pkill -x mpv 2>/dev/null || true
pkill -x ffplay 2>/dev/null || true

# ------------------------------------------------------------------ system packages
REQUIRED=(python3 python3-venv pipx uxplay avahi-daemon mpv ffmpeg
          gstreamer1.0-tools gstreamer1.0-plugins-base gstreamer1.0-plugins-good gstreamer1.0-plugins-bad
          gstreamer1.0-libav gstreamer1.0-gl usbmuxd libimobiledevice-utils
          libxcb-cursor0 libxcb-icccm4 libxcb-keysyms1 libxcb-image0 libxcb-render-util0 libxkbcommon-x11-0)
OPTIONAL=(fonts-inter gstreamer1.0-pulseaudio libavahi-compat-libdnssd1 python3-pip
          golang-go git pkg-config libgstreamer1.0-dev libgstreamer-plugins-base1.0-dev libusb-1.0-0-dev
          libusb-1.0-0)

say "Installing system packages (asks for your password)…"
sudo apt-get update || warn "apt-get update reported problems — continuing"
MISSING=()
if ! sudo apt-get install -y "${REQUIRED[@]}" "${OPTIONAL[@]}"; then
  warn "Some packages are not available on this system — installing them one by one…"
  for pkg in "${REQUIRED[@]}" "${OPTIONAL[@]}"; do
    sudo apt-get install -y "$pkg" >/dev/null 2>&1 || MISSING+=("$pkg")
  done
fi
for pkg in "${MISSING[@]}"; do
  if printf '%s\n' "${REQUIRED[@]}" | grep -qx "$pkg"; then fail "required package not installed: $pkg"
  else warn "optional package not available (skipped): $pkg"; fi
done

say "Enabling Avahi (AirPlay discovery) and usbmuxd…"
sudo systemctl enable --now avahi-daemon >/dev/null 2>&1 || warn "could not start avahi-daemon"
sudo systemctl restart usbmuxd >/dev/null 2>&1 || true

# ------------------------------------------------------------------ USB capture engine (optional)
QVH_MARKER=/usr/local/share/icast/qvh-patched-d81396e
if [ ! -f "$QVH_MARKER" ] || ! command -v qvh >/dev/null; then
  if command -v go >/dev/null && command -v git >/dev/null; then
    say "Building qvh (optional QuickTime USB engine)…"
    TMP_QVH="$(mktemp)"
    if bash "$HERE/scripts/build-qvh.sh" "$TMP_QVH"; then
      sudo install -m 0755 "$TMP_QVH" /usr/local/bin/qvh
      sudo mkdir -p /usr/local/share/icast && sudo touch "$QVH_MARKER"
      ok "qvh installed"
    else
      warn "qvh could not be built — only the experimental QuickTime engine is affected"
    fi
    rm -f "$TMP_QVH"
  else
    warn "go/git missing — skipping the optional QuickTime engine (qvh)"
  fi
fi

# ------------------------------------------------------------------ system tuning & permissions
say "Tuning network buffers for smooth high-frame-rate mirroring…"
echo "net.core.rmem_max=8388608" | sudo tee /etc/sysctl.d/90-icast.conf >/dev/null
sudo sysctl -q -p /etc/sysctl.d/90-icast.conf >/dev/null 2>&1 || true

say "Allowing USB access to iPhones without root…"
sudo tee /etc/udev/rules.d/39-icast-iphone.rules >/dev/null <<'RULE'
SUBSYSTEM=="usb", ATTR{idVendor}=="05ac", MODE="0666"
RULE
sudo udevadm control --reload-rules >/dev/null 2>&1 && sudo udevadm trigger >/dev/null 2>&1 || true

say "Installing the USB helper…"
sudo install -d /usr/local/libexec
sudo install -o root -g root -m 0755 "$HERE/scripts/icast-usb" /usr/local/libexec/icast-usb
TMP_SUDOERS="$(mktemp)"
echo "$ME ALL=(root) NOPASSWD: /usr/local/libexec/icast-usb" > "$TMP_SUDOERS"
if sudo visudo -cf "$TMP_SUDOERS" >/dev/null 2>&1; then
  sudo install -o root -g root -m 0440 "$TMP_SUDOERS" /etc/sudoers.d/icast-usb
else
  warn "could not validate the sudoers rule; USB casting may ask for your password"
fi
rm -f "$TMP_SUDOERS"

if command -v ufw >/dev/null && sudo ufw status 2>/dev/null | grep -q active; then
  say "Opening firewall for AirPlay…"
  sudo ufw allow 5353/udp >/dev/null; sudo ufw allow 7100:7102/tcp >/dev/null; sudo ufw allow 7100:7102/udp >/dev/null
fi

# ------------------------------------------------------------------ iCast itself (clean reinstall)
say "Installing iCast $VERSION…"
export PATH="$HOME/.local/bin:$PATH"
if command -v pipx >/dev/null; then
  pipx uninstall icast >/dev/null 2>&1 || true
  pipx install --force "$HERE" || { fail "pipx could not install iCast"; exit 1; }
  pipx ensurepath >/dev/null 2>&1 || true
else
  warn "pipx not available — installing with pip --user"
  python3 -m pip install --user --break-system-packages --force-reinstall "$HERE" \
    || python3 -m pip install --user --force-reinstall "$HERE" \
    || { fail "pip could not install iCast"; exit 1; }
fi

say "Adding desktop launcher…"
mkdir -p "$HOME/.local/share/applications" "$HOME/.local/share/icons/hicolor/scalable/apps"
install -m 0644 "$HERE/packaging/icast.desktop" "$HOME/.local/share/applications/icast.desktop"
install -m 0644 "$HERE/packaging/icast.svg" "$HOME/.local/share/icons/hicolor/scalable/apps/icast.svg"

# ------------------------------------------------------------------ verify
echo
INSTALLED="$("$HOME/.local/bin/icast" --version 2>/dev/null || true)"
if [ "$INSTALLED" = "iCast $VERSION" ]; then
  ok "iCast $VERSION is installed"
else
  fail "verification failed: expected 'iCast $VERSION', found '${INSTALLED:-nothing}'"
  exit 1
fi
for bin in uxplay mpv avahi-daemon; do
  if command -v "$bin" >/dev/null || [ -x "/usr/sbin/$bin" ]; then ok "$bin"; else fail "$bin missing"; fi
done
echo
ok "Done! Re-plug the iPhone, then start:  icast"
[ "${#MISSING[@]}" -gt 0 ] && warn "skipped packages: ${MISSING[*]}"
exit 0
