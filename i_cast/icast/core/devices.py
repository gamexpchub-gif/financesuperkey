"""USB iPhone discovery and pairing via libimobiledevice."""

from __future__ import annotations

import shutil
import subprocess
from dataclasses import dataclass

# Friendly names for common ProductType identifiers.
_MODEL_NAMES = {
    "iPhone12,1": "iPhone 11", "iPhone12,3": "iPhone 11 Pro", "iPhone12,5": "iPhone 11 Pro Max",
    "iPhone12,8": "iPhone SE (2nd gen)", "iPhone13,1": "iPhone 12 mini", "iPhone13,2": "iPhone 12",
    "iPhone13,3": "iPhone 12 Pro", "iPhone13,4": "iPhone 12 Pro Max", "iPhone14,4": "iPhone 13 mini",
    "iPhone14,5": "iPhone 13", "iPhone14,2": "iPhone 13 Pro", "iPhone14,3": "iPhone 13 Pro Max",
    "iPhone14,6": "iPhone SE (3rd gen)", "iPhone14,7": "iPhone 14", "iPhone14,8": "iPhone 14 Plus",
    "iPhone15,2": "iPhone 14 Pro", "iPhone15,3": "iPhone 14 Pro Max", "iPhone15,4": "iPhone 15",
    "iPhone15,5": "iPhone 15 Plus", "iPhone16,1": "iPhone 15 Pro", "iPhone16,2": "iPhone 15 Pro Max",
    "iPhone17,1": "iPhone 16 Pro", "iPhone17,2": "iPhone 16 Pro Max", "iPhone17,3": "iPhone 16",
    "iPhone17,4": "iPhone 16 Plus", "iPhone17,5": "iPhone 16e",
}


@dataclass
class IPhone:
    udid: str
    name: str = "iPhone"
    product_type: str = ""
    ios_version: str = ""
    paired: bool | None = None

    @property
    def model(self) -> str:
        return _MODEL_NAMES.get(self.product_type, self.product_type or "iPhone")

    @property
    def label(self) -> str:
        ios = f" · iOS {self.ios_version}" if self.ios_version else ""
        return f"{self.name} ({self.model}{ios})"


def _run(argv: list[str], timeout: float = 5.0) -> tuple[int, str]:
    if not shutil.which(argv[0]):
        return 127, ""
    try:
        res = subprocess.run(argv, capture_output=True, text=True, timeout=timeout)
    except (OSError, subprocess.TimeoutExpired):
        return 1, ""
    return res.returncode, (res.stdout + res.stderr).strip()


def list_udids() -> list[str]:
    code, out = _run(["idevice_id", "-l"])
    if code != 0:
        return []
    seen: list[str] = []
    for line in out.splitlines():
        udid = line.split()[0].strip() if line.strip() else ""
        if udid and udid not in seen:
            seen.append(udid)
    return seen


def _info(udid: str, key: str) -> str:
    code, out = _run(["ideviceinfo", "-u", udid, "-k", key])
    return out.splitlines()[0].strip() if code == 0 and out else ""


def is_paired(udid: str) -> bool | None:
    code, out = _run(["idevicepair", "-u", udid, "validate"])
    if code == 127:
        return None
    return code == 0 and "SUCCESS" in out.upper()


def pair(udid: str) -> tuple[bool, str]:
    """Trigger the 'Trust This Computer?' prompt on the iPhone."""
    code, out = _run(["idevicepair", "-u", udid, "pair"], timeout=30)
    return code == 0, out or "idevicepair not available"


def discover() -> list[IPhone]:
    phones = []
    for udid in list_udids():
        phones.append(
            IPhone(
                udid=udid,
                name=_info(udid, "DeviceName") or "iPhone",
                product_type=_info(udid, "ProductType"),
                ios_version=_info(udid, "ProductVersion"),
                paired=is_paired(udid),
            )
        )
    return phones
