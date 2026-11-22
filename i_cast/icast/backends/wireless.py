"""Wireless casting: AirPlay Screen Mirroring receiver powered by UxPlay.

On the iPhone: Control Center → Screen Mirroring → pick the iCast receiver.
The iPhone and this computer must be on the same Wi‑Fi / LAN.
"""

from __future__ import annotations

import os
import re

from icast.backends.base import Backend, CastState, split_extra
from icast.core.config import WirelessSettings


class WirelessBackend(Backend):
    name = "Wireless (AirPlay)"
    binary = "uxplay"

    STATE_PATTERNS = (
        (CastState.ERROR, (r"\berror\b.*(bind|port|dns.?sd|avahi)", r"failed to (start|create|register)",
                           r"DNSServiceRegister", r"address already in use")),
        (CastState.STREAMING, (r"connection request from", r"begin streaming", r"raop_rtp_mirror",
                               r"open connections:\s*[1-9]", r"client connected")),
        (CastState.WAITING, (r"Initialized server socket", r"Accepting connections", r"server started",
                             r"connection closed", r"open connections:\s*0", r"teardown")),
    )

    prepare_label = "Starting AirPlay receiver…"
    NAME_CONFLICT = r"kDNSServiceErr_NameConflict|NameConflict"
    EXPLANATIONS = (
        (NAME_CONFLICT,
         "Another AirPlay receiver on this network already uses the name/ID of iCast — iCast restarts it "
         "with a fresh device ID automatically. If it keeps happening, change Receiver name."),
        (r"kDNSServiceErr_(Unknown|ServiceNotRunning|NoSuchName)|avahi|dns.?sd.*(fail|not running)",
         "Bonjour (Avahi) is not running, so iPhones can't see iCast. Run: sudo systemctl enable --now avahi-daemon"),
        (r"address already in use|bind",
         "The AirPlay ports are busy. Turn off “Use fixed ports” in Settings, or choose another base port."),
    )

    def __init__(self, settings: WirelessSettings, binary: str = "uxplay") -> None:
        self.s = settings
        self.binary = binary
        self.wants_retry = False
        self._fresh_id = False   # retry: random device ID (-m) + computer name appended

    def classify(self, line: str) -> CastState | None:
        if re.search(self.NAME_CONFLICT, line) and not self._fresh_id:
            self.wants_retry = True
        return super().classify(line)

    def explain(self, line: str) -> str:
        for pattern, text in self.EXPLANATIONS:
            if re.search(pattern, line, re.IGNORECASE):
                return text
        return line.strip("* ").removeprefix("ERROR: ")

    def prepare_steps(self) -> list[list[str]]:
        # An AirPlay receiver left over from an earlier session (iCast killed, crash, terminal run)
        # still owns the name on the network -> "DNSServiceRegister ... NameConflict". Stop only
        # this user's uxplay processes, then give Avahi a moment to withdraw the old announcement.
        return [["pkill", "-u", str(os.getuid()), "-x", self.binary], ["sleep", "0.8"]]

    def prepare_retry(self) -> str:
        self.wants_retry = False
        self._fresh_id = True
        return "Name conflict on the network — restarting the receiver with a fresh device ID"

    def build_command(self) -> list[str]:
        s = self.s
        argv = [self.binary, "-n", s.receiver_name.strip() or "iCast"]
        if not s.append_hostname and not self._fresh_id:
            argv.append("-nh")
        if self._fresh_id:
            argv.append("-m")  # random MAC / device ID
        w, h = s.resolution.split("x")
        argv += ["-s", f"{w}x{h}@{s.fps}", "-fps", str(s.fps)]
        if s.fullscreen:
            argv.append("-fs")
        if s.rotation in ("L", "R"):
            argv += ["-r", s.rotation]
        if s.flip in ("H", "V", "I"):
            argv += ["-f", s.flip]
        if s.audio_sink == "none":
            argv += ["-as", "0"]
        elif s.audio_sink != "auto":
            argv += ["-as", s.audio_sink]
        if s.video_sink != "auto":
            argv += ["-vs", s.video_sink]
        if s.low_latency:
            argv += ["-vsync", "no"]
        if s.fixed_ports:
            argv += ["-p", str(s.base_port)]
        if s.pin_enabled:
            argv += ["-pin", s.pin] if s.pin else ["-pin"]
        if s.restrict_to_trusted and s.trusted_devices:
            argv.append("-restrict")
            for dev in s.trusted_devices:
                argv += ["-allow", dev]
        argv += split_extra(s.extra_args)
        return argv

    def firewall_ports(self) -> list[str]:
        if not self.s.fixed_ports:
            return []
        p = self.s.base_port
        return [f"{p}/tcp", f"{p + 1}/tcp", f"{p + 2}/tcp", f"{p + 1}/udp", f"{p + 2}/udp", "5353/udp"]
