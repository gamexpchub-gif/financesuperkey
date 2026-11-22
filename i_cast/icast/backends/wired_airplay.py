"""Wired casting with AirPlay over the USB cable (iPhone Personal Hotspot via USB).

Apple's own Screen Mirroring protocol, carried over the cable's network link — no Wi‑Fi and no
reverse-engineered USB capture protocol, so it keeps working on new iOS versions. The picture opens
fullscreen in a single window with UxPlay's low-latency settings.
"""

from __future__ import annotations

import sys
from dataclasses import replace

from icast.backends.wireless import WirelessBackend
from icast.core.config import WirelessSettings
from icast.core.usbnet import find_iphone_link

HOTSPOT_STEPS = ("On the iPhone open Settings → Personal Hotspot and turn on “Allow Others to Join”, "
                 "keep the cable connected, then press Start Wired again.")


class WiredAirPlayBackend(WirelessBackend):
    name = "Wired (AirPlay over USB)"
    prepare_label = "Starting AirPlay over the USB cable…"

    def __init__(self, settings: WirelessSettings, binary: str = "uxplay", link_finder=find_iphone_link,
                 screen: tuple[int, int, int, int] | None = None, use_viewer: bool | None = None) -> None:
        # always one big fullscreen window, lowest latency
        super().__init__(replace(settings, fullscreen=True, low_latency=True), binary=binary, screen=screen,
                         use_viewer=use_viewer)
        self.link_finder = link_finder
        self.link = None

    def preflight(self) -> str | None:
        self.link = self.link_finder()
        if self.link is None:
            return "The iPhone's USB network link is not up. " + HOTSPOT_STEPS
        return None   # a missing address is waited for (and fixed) in prepare_steps

    def prepare_steps(self) -> list[list[str]]:
        steps = super().prepare_steps()          # stop leftover receivers
        if self.link is not None and not self.link.ready:
            # the link is there but has no address yet: wait for DHCP, nudge NetworkManager if needed
            steps = [[sys.executable, "-m", "icast.core.usbnet", "wait", "30"]] + steps
        return steps

    def step_timeout(self, argv: list[str]) -> float:
        return 40.0 if "icast.core.usbnet" in argv else super().step_timeout(argv)

    def step_failed(self, argv: list[str], code: int, output: str) -> str | None:
        if "icast.core.usbnet" in argv and code != 0:
            fatal = [l for l in output.splitlines() if l.startswith("FATAL:")]
            return fatal[-1].removeprefix("FATAL: ") if fatal else "The iPhone's USB network did not come up."
        return super().step_failed(argv, code, output)

    @property
    def instructions(self) -> str:
        return f"On the iPhone: Control Center → Screen Mirroring → “{self.s.receiver_name or 'iCast'}”"
