"""System dependency detection ("icast doctor")."""

from __future__ import annotations

import shutil
import subprocess
from dataclasses import dataclass


@dataclass(frozen=True)
class Dependency:
    key: str
    binary: str
    purpose: str
    required_for: str  # wireless | wired | both
    apt_package: str


DEPENDENCIES: tuple[Dependency, ...] = (
    Dependency("uxplay", "uxplay", "AirPlay mirroring receiver", "wireless", "uxplay"),
    Dependency("avahi", "avahi-daemon", "Bonjour/mDNS discovery", "wireless", "avahi-daemon"),
    Dependency("usbmuxd", "usbmuxd", "USB multiplexing daemon for iOS", "wired", "usbmuxd"),
    Dependency("idevice_id", "idevice_id", "List USB-connected iPhones", "wired", "libimobiledevice-utils"),
    Dependency("ideviceinfo", "ideviceinfo", "Read iPhone name/model", "wired", "libimobiledevice-utils"),
    Dependency("idevicepair", "idevicepair", "Pair / trust iPhone", "wired", "libimobiledevice-utils"),
    Dependency("qvh", "qvh", "USB screen capture (QuickTime protocol)", "wired", "(build from source)"),
    Dependency("gst", "gst-launch-1.0", "GStreamer media pipeline", "both", "gstreamer1.0-tools"),
)


@dataclass
class DependencyStatus:
    dep: Dependency
    path: str | None

    @property
    def ok(self) -> bool:
        return self.path is not None


def _which(binary: str) -> str | None:
    found = shutil.which(binary)
    if found:
        return found
    # daemons often live in sbin which may not be on a normal user's PATH
    for prefix in ("/usr/sbin", "/sbin", "/usr/local/sbin"):
        candidate = shutil.which(binary, path=prefix)
        if candidate:
            return candidate
    return None


def check_all() -> list[DependencyStatus]:
    return [DependencyStatus(d, _which(d.binary)) for d in DEPENDENCIES]


def missing_for(mode: str) -> list[Dependency]:
    return [s.dep for s in check_all() if not s.ok and s.dep.required_for in (mode, "both")]


def service_active(name: str) -> bool | None:
    """Return True/False if systemd reports the service state, None if unknown."""
    if not shutil.which("systemctl"):
        return None
    try:
        out = subprocess.run(
            ["systemctl", "is-active", name], capture_output=True, text=True, timeout=3
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    return out.stdout.strip() == "active"
