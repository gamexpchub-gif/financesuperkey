"""Wireless casting: AirPlay Screen Mirroring receiver powered by UxPlay.

On the iPhone: Control Center → Screen Mirroring → pick the iCast receiver.
The iPhone and this computer must be on the same Wi‑Fi / LAN.
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess
from functools import lru_cache

from icast.backends.base import Backend, CastState, split_extra
from icast.core import viewer
from icast.core.config import WirelessSettings


@lru_cache(maxsize=4)
def uxplay_supports_vrtp(binary: str = "uxplay") -> bool:
    """UxPlay >= 1.73 can forward the decrypted video (-vrtp) instead of rendering it itself."""
    if not shutil.which(binary):
        return False
    try:
        res = subprocess.run([binary, "-h"], capture_output=True, text=True, timeout=5)
    except (OSError, subprocess.TimeoutExpired):
        return False
    return "-vrtp" in (res.stdout + res.stderr)


class WirelessBackend(Backend):
    name = "Wireless (AirPlay)"
    binary = "uxplay"

    STATE_PATTERNS = (
        (CastState.ERROR, (r"\berror\b.*(bind|port|dns.?sd|avahi)", r"failed to (start|create|register)",
                           r"DNSServiceRegister", r"address already in use")),
        (CastState.STREAMING, (viewer.STREAM_STARTED, r"connection request from", r"begin streaming", r"raop_rtp_mirror",
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

    companion_timeout_s = 10.0

    def __init__(self, settings: WirelessSettings, binary: str = "uxplay",
                 screen: tuple[int, int, int, int] | None = None, use_viewer: bool | None = None,
                 player: str | None = None) -> None:
        self.s = settings
        self.binary = binary
        self.screen = screen
        self.player = player or viewer.available_player() or "ffplay"
        self.wants_retry = False
        self._fresh_id = False   # retry: random device ID (-m) + computer name appended
        # iCast's own viewer: re-fits every frame, so landscape games fill the screen automatically
        self.use_viewer = (use_viewer if use_viewer is not None
                           else bool(shutil.which(self.player)) and uxplay_supports_vrtp(binary))
        self.port: int | None = None
        self.sdp = None

    def display_size(self) -> tuple[int, int]:
        """Size requested from the iPhone. Height is what the iPhone uses; width follows the orientation."""
        if self.s.resolution == "auto":
            if self.screen:
                return int(self.screen[2]), int(self.screen[3])
            return 1920, 1080
        w, h = self.s.resolution.split("x")
        return int(w), int(h)

    def companions(self) -> list[list[str]]:
        if not self.use_viewer or self.sdp is None:
            return []
        # mpv (preferred: Esc = small window, F = fullscreen, Q = stop) or ffplay
        return [viewer.viewer_command(rtp_sdp=self.sdp, player=self.player,
                                      latency="ultra" if self.s.low_latency else "smooth",
                                      fullscreen=self.s.fullscreen, screen=self.screen,
                                      rotation=self.s.rotation, flip=self.s.flip)]

    def companions_ready(self) -> bool:
        if self.port is None or not os.path.exists("/proc/net/udp"):
            return True
        return viewer.port_bound(self.port, "udp")

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
        w, h = self.display_size()
        # @fps tells the iPhone the display's refresh rate; -fps is the maximum it may stream
        argv += ["-s", f"{w}x{h}@{s.fps}", "-fps", str(s.fps)]
        if self.use_viewer:
            self.port = viewer.free_udp_port()
            if self.sdp is not None:
                try:
                    self.sdp.unlink()
                except OSError:
                    pass
            self.sdp = viewer.write_rtp_sdp(self.port)
            argv += ["-vrtp", f"config-interval=-1 ! udpsink host=127.0.0.1 port={self.port} sync=false"]
        else:
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
        if s.video_sink != "auto" and not self.use_viewer:
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
