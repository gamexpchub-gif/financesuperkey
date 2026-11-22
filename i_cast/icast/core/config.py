"""Persistent user settings stored as JSON in the XDG config directory."""

from __future__ import annotations

import json
import os
from dataclasses import asdict, dataclass, field, fields
from pathlib import Path

RESOLUTIONS = ("auto", "1280x720", "1920x1080", "2560x1440", "3840x2160")
FRAME_RATES = (30, 60, 120)
ROTATIONS = ("none", "L", "R")  # UxPlay -r values
FLIPS = ("none", "H", "V", "I")  # UxPlay -f values
AUDIO_SINKS = ("auto", "pulsesink", "pipewiresink", "alsasink", "none")
VIDEO_SINKS = ("auto", "autovideosink", "glimagesink", "xvimagesink", "waylandsink", "kmssink")
WIRED_OUTPUTS = ("fullscreen", "window", "record")
LATENCY_MODES = ("ultra", "smooth")
WIRED_ENGINES = ("airplay", "qvh")
SETTINGS_VERSION = 3


def config_dir() -> Path:
    base = os.environ.get("XDG_CONFIG_HOME") or str(Path.home() / ".config")
    return Path(base) / "icast"


@dataclass
class WirelessSettings:
    receiver_name: str = "iCast"
    append_hostname: bool = False
    resolution: str = "auto"   # auto = this monitor's size (the iPhone sizes the picture by height)
    fps: int = 120
    pin_enabled: bool = False
    pin: str = ""
    fullscreen: bool = True
    rotation: str = "none"
    flip: str = "none"
    audio_sink: str = "auto"
    video_sink: str = "auto"
    low_latency: bool = True
    fixed_ports: bool = False
    base_port: int = 7100
    restrict_to_trusted: bool = False
    trusted_devices: list[str] = field(default_factory=list)
    extra_args: str = ""


@dataclass
class WiredSettings:
    engine: str = "airplay"  # airplay: AirPlay over the USB cable | qvh: QuickTime capture (experimental)
    preferred_udid: str = ""
    auto_start_on_connect: bool = False
    output: str = "fullscreen"  # fullscreen | window | record
    latency_mode: str = "ultra"  # ultra: always newest frame | smooth: steadier on slow PCs
    audio: bool = True
    audio_buffer_ms: int = 40
    record_dir: str = str(Path.home() / "Videos" / "iCast")
    extra_args: str = ""


@dataclass
class AppSettings:
    mode: str = "wireless"  # wireless | wired
    wireless: WirelessSettings = field(default_factory=WirelessSettings)
    wired: WiredSettings = field(default_factory=WiredSettings)
    log_level: str = "info"
    settings_version: int = SETTINGS_VERSION

    # ---------- persistence ----------
    @classmethod
    def path(cls) -> Path:
        return config_dir() / "settings.json"

    @classmethod
    def load(cls, path: Path | None = None) -> "AppSettings":
        path = path or cls.path()
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            return cls()
        return cls.from_dict(raw)

    @classmethod
    def from_dict(cls, raw: dict) -> "AppSettings":
        def pick(dc, data):
            names = {f.name for f in fields(dc)}
            return dc(**{k: v for k, v in (data or {}).items() if k in names})

        settings = cls()
        if isinstance(raw, dict):
            settings.mode = raw.get("mode", settings.mode)
            settings.log_level = raw.get("log_level", settings.log_level)
            old_version = raw.get("settings_version", 1)
            try:
                settings.wireless = pick(WirelessSettings, raw.get("wireless"))
                settings.wired = pick(WiredSettings, raw.get("wired"))
            except TypeError:
                pass
            # v1 defaulted wired output to a small window; v2 casts fullscreen by default.
            if old_version < 2 and settings.wired.output == "window":
                settings.wired.output = "fullscreen"
            # v3: AirPlay fills the screen in any orientation and asks for up to 120 fps
            if old_version < 3:
                w = settings.wireless
                if w.resolution == "1920x1080":
                    w.resolution = "auto"
                if w.fps == 60:
                    w.fps = 120
                w.fullscreen = True
        settings.settings_version = SETTINGS_VERSION
        settings.validate()
        return settings

    def save(self, path: Path | None = None) -> Path:
        path = path or self.path()
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(".tmp")
        tmp.write_text(json.dumps(asdict(self), indent=2), encoding="utf-8")
        tmp.replace(path)
        return path

    def validate(self) -> None:
        """Clamp invalid values back to safe defaults instead of failing."""
        w = self.wireless
        defaults = WirelessSettings()
        if self.mode not in ("wireless", "wired"):
            self.mode = "wireless"
        if w.resolution not in RESOLUTIONS:
            w.resolution = defaults.resolution
        if w.fps not in FRAME_RATES:
            w.fps = defaults.fps
        if w.rotation not in ROTATIONS:
            w.rotation = "none"
        if w.flip not in FLIPS:
            w.flip = "none"
        if w.audio_sink not in AUDIO_SINKS:
            w.audio_sink = "auto"
        if w.video_sink not in VIDEO_SINKS:
            w.video_sink = "auto"
        if not (1024 <= int(w.base_port) <= 65000):
            w.base_port = defaults.base_port
        if w.pin and not (len(w.pin) == 4 and w.pin.isdigit()):
            w.pin = ""
        if not w.receiver_name.strip():
            w.receiver_name = defaults.receiver_name
        if self.wired.output not in WIRED_OUTPUTS:
            self.wired.output = "fullscreen"
        if self.wired.engine not in WIRED_ENGINES:
            self.wired.engine = "airplay"
        if self.wired.latency_mode not in LATENCY_MODES:
            self.wired.latency_mode = "ultra"
        if not (10 <= int(self.wired.audio_buffer_ms) <= 500):
            self.wired.audio_buffer_ms = 40
