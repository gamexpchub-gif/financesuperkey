"""Switch an iPhone into (and check for) its hidden screen-capture USB configuration — no qvh needed.

macOS QuickTime and qvh both enable screen capture by sending one vendor control request to the
iPhone (bmRequestType 0x40, bRequest 0x52, wValue 0, wIndex 2). The iPhone then drops off the bus
and re-connects with an extra USB configuration that contains a vendor-class interface with
subclass 0x2A ("QuickTime"). qvh's own `activate` waits for that re-connection with no time limit;
this module does the same job with a deadline and readable progress, using only Linux usbfs and
sysfs (works for the user thanks to the udev rule installed by scripts/install.sh).

    python -m icast.core.usbcap activate <udid>
    python -m icast.core.usbcap status   <udid>
"""

from __future__ import annotations

import ctypes
import errno
import fcntl
import os
import sys
import time
from dataclasses import dataclass
from pathlib import Path

APPLE_VID = "05ac"
QT_SUBCLASS = 0x2A
VENDOR_CLASS = 0xFF


def _sysfs() -> Path:
    return Path(os.environ.get("ICAST_SYSFS", "/sys"))


def _devroot() -> Path:
    return Path(os.environ.get("ICAST_DEVROOT", "/dev"))


def normalize(udid: str) -> str:
    return udid.replace("-", "").strip().upper()


@dataclass
class UsbIPhone:
    sysdir: Path
    busnum: int
    devnum: int

    @property
    def node(self) -> Path:
        return _devroot() / "bus" / "usb" / f"{self.busnum:03d}" / f"{self.devnum:03d}"


def find(udid: str) -> UsbIPhone | None:
    want = normalize(udid)
    base = _sysfs() / "bus" / "usb" / "devices"
    try:
        entries = list(base.iterdir())
    except OSError:
        return None
    for d in entries:
        try:
            if (d / "idVendor").read_text().strip() != APPLE_VID:
                continue
            if normalize((d / "serial").read_text().strip("\x00\n ")) != want:
                continue
            return UsbIPhone(d, int((d / "busnum").read_text()), int((d / "devnum").read_text()))
        except (OSError, ValueError):
            continue
    return None


def has_capture_interface(descriptors: bytes) -> bool:
    """True if any configuration exposes the vendor-class / subclass 0x2A screen-capture interface."""
    i = 0
    while i + 2 <= len(descriptors):
        length, dtype = descriptors[i], descriptors[i + 1]
        if length < 2:
            break
        if dtype == 4 and length >= 9 and i + 9 <= len(descriptors):  # interface descriptor
            if descriptors[i + 5] == VENDOR_CLASS and descriptors[i + 6] == QT_SUBCLASS:
                return True
        i += length
    return False


def capture_mode(dev: UsbIPhone) -> bool:
    try:
        return has_capture_interface((dev.sysdir / "descriptors").read_bytes())
    except OSError:
        return False


class _CtrlTransfer(ctypes.Structure):
    _fields_ = [("bRequestType", ctypes.c_uint8), ("bRequest", ctypes.c_uint8),
                ("wValue", ctypes.c_uint16), ("wIndex", ctypes.c_uint16), ("wLength", ctypes.c_uint16),
                ("timeout", ctypes.c_uint32), ("data", ctypes.c_void_p)]


def _iowr(type_char: str, nr: int, size: int) -> int:
    return (3 << 30) | (size << 16) | (ord(type_char) << 8) | nr


USBDEVFS_CONTROL = _iowr("U", 0, ctypes.sizeof(_CtrlTransfer))


def send_enable_request(dev: UsbIPhone, enable: bool = True) -> None:
    """Send the 'enable' (wIndex 2) or 'disable' (wIndex 0) screen-capture vendor request.

    The iPhone may vanish mid-request (it re-connects): that's expected.
    """
    fd = os.open(dev.node, os.O_RDWR)
    try:
        req = _CtrlTransfer(0x40, 0x52, 0x00, 0x02 if enable else 0x00, 0, 1000, None)
        try:
            fcntl.ioctl(fd, USBDEVFS_CONTROL, req)
        except OSError as exc:
            if exc.errno not in (errno.ENODEV, errno.EPROTO, errno.EPIPE, errno.ETIMEDOUT, errno.ESHUTDOWN):
                raise
    finally:
        os.close(fd)


def _wait(udid: str, want_capture: bool, timeout: float, log) -> UsbIPhone | None:
    deadline = time.monotonic() + timeout
    seen_gone = False
    while time.monotonic() < deadline:
        time.sleep(0.2)
        now = find(udid)
        if now is None:
            if not seen_gone:
                log("iPhone is re-connecting…")
            seen_gone = True
            continue
        if capture_mode(now) == want_capture:
            return now
    return None


def activate(udid: str, timeout: float = 15.0, log=print, sender=send_enable_request) -> int:
    dev = find(udid)
    if dev is None:
        log("FATAL: iPhone not found on USB — unlock it and re-plug the cable")
        return 3
    if capture_mode(dev):
        # Left over from an earlier session: the iPhone keeps its capture state across a software
        # re-plug. Start a fresh capture session (like a first activation) — switch off, then on.
        log("iPhone is still in screen-capture mode from an earlier session — starting a fresh one")
        try:
            sender(dev, False)
        except OSError as exc:
            log(f"Could not switch capture mode off ({exc}) — using the existing capture mode")
            return 0
        else:
            off = _wait(udid, want_capture=False, timeout=8.0, log=log)
            dev = off or find(udid)
            if dev is None:
                log("FATAL: iPhone did not come back after switching capture mode off — re-plug the cable")
                return 3
            if off is None:
                log("iPhone kept capture mode on — using it as is")
                return 0
            log("Capture mode is off — switching it on again")
    log(f"Switching iPhone (USB {dev.busnum}-{dev.devnum}) into screen-capture mode…")
    try:
        sender(dev, True)
    except PermissionError:
        log(f"FATAL: permission denied on {dev.node} — run scripts/install.sh and re-plug the iPhone")
        return 4
    except OSError as exc:
        log(f"FATAL: could not talk to the iPhone over USB: {exc}")
        return 5
    gone = {"seen": False}

    def track(msg: str) -> None:
        gone["seen"] = True
        log(msg)

    now = _wait(udid, want_capture=True, timeout=timeout, log=track)
    if now is not None:
        log(f"Screen-capture mode is on (USB {now.busnum}-{now.devnum})")
        return 0
    if not gone["seen"]:
        log("FATAL: the iPhone ignored the screen-capture request — unlock it, tap Trust, re-plug and try again")
    else:
        log("FATAL: the iPhone re-connected without screen-capture mode — unlock it, re-plug and try again")
    return 6


def main(argv: list[str] | None = None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    if len(argv) != 2 or argv[0] not in ("activate", "status"):
        print("usage: python -m icast.core.usbcap activate|status <udid>")
        return 2
    cmd, udid = argv
    if cmd == "status":
        dev = find(udid)
        print("not connected" if dev is None else ("capture mode" if capture_mode(dev) else "normal mode"))
        return 0 if dev else 3
    return activate(udid, log=lambda m: print(m, flush=True))


if __name__ == "__main__":
    raise SystemExit(main())
