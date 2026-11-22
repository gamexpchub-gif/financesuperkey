"""Find the network link an iPhone creates over its USB cable (Personal Hotspot → "iPhone USB").

With Personal Hotspot on and the cable connected, the kernel's ipheth (or cdc_ncm) driver exposes the
iPhone as an Ethernet interface and the iPhone hands this computer an address (172.20.10.x). AirPlay
Screen Mirroring then runs over the cable — Apple's own protocol, so it works on every iOS version.
"""

from __future__ import annotations

import ipaddress
import os
import socket
import struct
from dataclasses import dataclass
from pathlib import Path

USB_NET_DRIVERS = ("ipheth", "cdc_ncm")


def _sysfs() -> Path:
    return Path(os.environ.get("ICAST_SYSFS", "/sys"))


@dataclass
class UsbLink:
    ifname: str
    ipv4: str | None

    @property
    def ready(self) -> bool:
        return self.ipv4 is not None


def _driver(ifdir: Path) -> str:
    try:
        return Path(os.readlink(ifdir / "device" / "driver")).name
    except OSError:
        return ""


def _is_apple(ifdir: Path) -> bool:
    # .../usbX/1-1/1-1:4.2/net/enx... -> the USB device dir (1-1) holds idVendor
    try:
        dev = (ifdir / "device").resolve().parent
        return (dev / "idVendor").read_text().strip() == "05ac"
    except OSError:
        return False


def ipv4_of(ifname: str) -> str | None:
    """IPv4 address of an interface (SIOCGIFADDR), or None."""
    try:
        import fcntl

        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            raw = fcntl.ioctl(s.fileno(), 0x8915, struct.pack("256s", ifname[:15].encode()))
        return socket.inet_ntoa(raw[20:24])
    except OSError:
        return None


def find_iphone_link(addr_lookup=ipv4_of) -> UsbLink | None:
    base = _sysfs() / "class" / "net"
    try:
        names = sorted(p.name for p in base.iterdir())
    except OSError:
        return None
    found: UsbLink | None = None
    for name in names:
        ifdir = base / name
        drv = _driver(ifdir)
        if drv == "ipheth" or (drv in USB_NET_DRIVERS and _is_apple(ifdir)):
            link = UsbLink(name, addr_lookup(name))
            if link.ready:
                return link
            found = found or link
    return found


def is_hotspot_address(ip: str | None) -> bool:
    try:
        return ip is not None and ipaddress.ip_address(ip) in ipaddress.ip_network("172.20.10.0/28")
    except ValueError:
        return False


def carrier(ifname: str) -> bool | None:
    """True when the iPhone side of the cable network is active (Personal Hotspot serving)."""
    try:
        return (_sysfs() / "class" / "net" / ifname / "carrier").read_text().strip() == "1"
    except OSError:
        return None   # interface down: the kernel can't tell


def nm_connect(ifname: str) -> str:
    """Ask NetworkManager to bring the link up and run DHCP (no root needed for the desktop user)."""
    import shutil
    import subprocess

    if not shutil.which("nmcli"):
        return "NetworkManager (nmcli) not found"
    try:
        res = subprocess.run(["nmcli", "device", "connect", ifname], capture_output=True, text=True, timeout=25)
    except (OSError, subprocess.TimeoutExpired) as exc:
        return f"nmcli did not finish: {exc}"
    return (res.stdout + res.stderr).strip() or f"nmcli exit code {res.returncode}"


def wait_for_address(timeout: float = 20.0, log=print, finder=None, connect=nm_connect, sleep=None) -> int:
    """Wait until the iPhone's USB link has an IPv4 address; nudge NetworkManager if it doesn't come."""
    import time

    finder = finder or find_iphone_link
    sleep = sleep or time.sleep
    start = time.monotonic()
    nudged = False
    announced = False
    while True:
        link = finder()
        if link is None:
            log("FATAL: the iPhone's USB network link disappeared — check the cable and Personal Hotspot")
            return 3
        if link.ready:
            log(f"iPhone USB network ready: {link.ifname} {link.ipv4}")
            return 0
        waited = time.monotonic() - start
        if not announced:
            log(f"Waiting for the iPhone's USB network ({link.ifname}) to get an address…")
            announced = True
        if waited >= 3 and not nudged:
            nudged = True
            log(f"Asking NetworkManager to connect {link.ifname}: {connect(link.ifname)}")
        if waited >= timeout:
            if carrier(link.ifname) is False:
                log("FATAL: the iPhone is not serving its USB network — on the iPhone open Settings → Personal "
                    "Hotspot, turn on “Allow Others to Join” and keep that screen open for a few seconds")
            else:
                log(f"FATAL: {link.ifname} got no address from the iPhone — run: nmcli device connect "
                    f"{link.ifname}   (or turn Personal Hotspot off and on again)")
            return 6
        sleep(0.5)


def main(argv: list[str] | None = None) -> int:
    import sys

    argv = list(sys.argv[1:] if argv is None else argv)
    if argv[:1] != ["wait"]:
        print("usage: python -m icast.core.usbnet wait [seconds]")
        return 2
    timeout = float(argv[1]) if len(argv) > 1 else 20.0
    return wait_for_address(timeout, log=lambda m: print(m, flush=True))


if __name__ == "__main__":
    raise SystemExit(main())
