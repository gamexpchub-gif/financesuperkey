"""Wireless casting: AirPlay Screen Mirroring receiver powered by UxPlay.

On the iPhone: Control Center → Screen Mirroring → pick the iCast receiver.
The iPhone and this computer must be on the same Wi‑Fi / LAN.
"""

from __future__ import annotations

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

    def __init__(self, settings: WirelessSettings, binary: str = "uxplay") -> None:
        self.s = settings
        self.binary = binary

    def build_command(self) -> list[str]:
        s = self.s
        argv = [self.binary, "-n", s.receiver_name.strip() or "iCast"]
        if not s.append_hostname:
            argv.append("-nh")
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
