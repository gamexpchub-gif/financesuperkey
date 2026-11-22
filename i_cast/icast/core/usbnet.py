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
