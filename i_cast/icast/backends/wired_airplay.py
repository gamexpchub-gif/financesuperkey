"""Wired casting with AirPlay over the USB cable (iPhone Personal Hotspot via USB).

Apple's own Screen Mirroring protocol, carried over the cable's network link — no Wi‑Fi and no
reverse-engineered USB capture protocol, so it keeps working on new iOS versions. The picture opens
fullscreen in a single window with UxPlay's low-latency settings.
"""

from __future__ import annotations

from dataclasses import replace

from icast.backends.wireless import WirelessBackend
from icast.core.config import WirelessSettings
from icast.core.usbnet import find_iphone_link

HOTSPOT_STEPS = ("On the iPhone open Settings → Personal Hotspot and turn on “Allow Others to Join”, "
                 "keep the cable connected, then press Start Wired again.")


class WiredAirPlayBackend(WirelessBackend):
    name = "Wired (AirPlay over USB)"
    prepare_label = "Starting AirPlay over the USB cable…"

    def __init__(self, settings: WirelessSettings, binary: str = "uxplay", link_finder=find_iphone_link) -> None:
        # always one big fullscreen window, lowest latency
        super().__init__(replace(settings, fullscreen=True, low_latency=True), binary=binary)
        self.link_finder = link_finder
        self.link = None

    def preflight(self) -> str | None:
        self.link = self.link_finder()
        if self.link is None:
            return "The iPhone's USB network link is not up. " + HOTSPOT_STEPS
        if not self.link.ready:
            return (f"The iPhone's USB link ({self.link.ifname}) has no address yet. Wait a few seconds; "
                    "if it stays like this: " + HOTSPOT_STEPS)
        return None

    @property
    def instructions(self) -> str:
        return f"On the iPhone: Control Center → Screen Mirroring → “{self.s.receiver_name or 'iCast'}”"
