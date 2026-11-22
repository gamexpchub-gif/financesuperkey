"""Wired casting over USB using the QuickTime screen-capture protocol (qvh).

Plug the iPhone in with a Lightning/USB‑C cable, unlock it and tap "Trust".

Measured end-to-end on a 60 fps stream (fake phone source, see tests): ultra mode keeps the
picture on the newest frame with no growing delay; smooth mode trades ~0.1 s for steadier pacing.

Output modes
------------
fullscreen (default)
    qvh hands the iPhone's H.264 stream, untouched, to a local TCP socket.
    ffplay listens there and shows it in one borderless fullscreen window. The
    decoder runs with no input buffer, low-delay flags and late-frame dropping,
    so the screen shows the newest frame. Audio plays through GStreamer with a
    small buffer (40 ms by default) so it stays in step with the picture.
    Esc / q closes the viewer and ends the session; f / double-click toggles fullscreen.
window
    qvh's built-in GStreamer window.
record
    Raw .h264 + .wav, muxed to MP4 with ffmpeg afterwards.
"""

from __future__ import annotations

import os
import shutil
import socket
import subprocess
import time
from functools import lru_cache
from pathlib import Path

from icast.backends.base import Backend, CastState, split_extra
from icast.core.config import WiredSettings

VIEWER_TITLE = "iCast — iPhone"


def free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


@lru_cache(maxsize=32)
def gst_has(element: str) -> bool:
    if not shutil.which("gst-inspect-1.0"):
        return False
    try:
        return subprocess.run(["gst-inspect-1.0", "--exists", element], capture_output=True, timeout=5).returncode == 0
    except (OSError, subprocess.TimeoutExpired):
        return False


def pulse_server_running() -> bool:
    """True when a PulseAudio / PipeWire-Pulse server socket exists for this user."""
    if os.environ.get("PULSE_SERVER"):
        return True
    runtime = os.environ.get("XDG_RUNTIME_DIR") or f"/run/user/{os.getuid()}"
    return Path(runtime, "pulse", "native").exists()


class WiredBackend(Backend):
    name = "Wired (USB)"
    binary = "qvh"
    companion_delay_s = 0.6  # give ffplay time to open its listening socket

    STATE_PATTERNS = (
        (CastState.ERROR, (r"no device", r"device not found", r"could not", r"permission denied",
                           r"LIBUSB_ERROR", r"\bpanic\b", r"Invalid Pipeline", r"Connection refused")),
        (CastState.STREAMING, (r"start streaming", r"receiving", r"Start Session", r"ASYN", r"FEED",
                               r"stream started", r"Input #0", r"Stream #0:0")),
        (CastState.WAITING, (r"activat", r"enabling", r"waiting for", r"Opening device")),
    )

    def __init__(self, settings: WiredSettings, udid: str = "", binary: str = "qvh",
                 player: str = "ffplay", screen: tuple[int, int, int, int] | None = None) -> None:
        self.s = settings
        self.udid = udid or settings.preferred_udid
        self.binary = binary
        self.player = player
        self.port: int | None = None
        # (x, y, width, height) of the monitor to fill; makes fullscreen work even without a WM.
        self.screen = screen
        self.last_recording: tuple[Path, Path] | None = None

    # ---------------------------------------------------------------- pipelines
    def video_pipeline(self, port: int) -> str:
        # No decode, no re-encode: the phone's H.264 goes straight to the viewer.
        return (
            "queue name=video_target max-size-buffers=0 max-size-bytes=0 max-size-time=0 "
            "! h264parse config-interval=-1 "
            "! video/x-h264,stream-format=byte-stream,alignment=au "
            f"! tcpclientsink host=127.0.0.1 port={port} sync=false"
        )

    def audio_pipeline(self) -> str:
        if not self.s.audio:
            return "queue name=audio_target ! fakesink sync=false"
        buf_us = int(self.s.audio_buffer_ms) * 1000
        if gst_has("pulsesink") and pulse_server_running():
            sink = f"pulsesink sync=false buffer-time={buf_us} latency-time={max(5000, buf_us // 4)}"
        else:
            sink = "autoaudiosink sync=false"
        return (
            "queue name=audio_target leaky=downstream max-size-time=200000000 "
            "! wavparse ignore-length=true ! audioconvert ! audioresample "
            f"! {sink}"
        )

    def viewer_command(self, port: int) -> list[str]:
        return [
            self.player, "-hide_banner", "-loglevel", "warning",
            # input: no buffering, no probing delay
            "-fflags", "nobuffer", "-flags", "low_delay", "-probesize", "32", "-analyzeduration", "0",
            # ultra: every frame is due "now" and stale ones are dropped -> newest frame on screen
            *self._pacing_args(),
            # one borderless fullscreen window, picture only (audio is handled by GStreamer)
            "-an", "-fs", "-noborder", "-window_title", VIEWER_TITLE,
            *self._geometry_args(),
            "-f", "h264", f"tcp://127.0.0.1:{port}?listen=1",
        ]

    def _pacing_args(self) -> list[str]:
        if self.s.latency_mode == "smooth":
            return ["-framedrop", "-sync", "ext"]
        return ["-framedrop", "-sync", "ext", "-vf", "setpts=0"]

    def _geometry_args(self) -> list[str]:
        if not self.screen:
            return []
        x, y, w, h = (int(v) for v in self.screen)
        return ["-left", str(x), "-top", str(y), "-x", str(w), "-y", str(h)]

    # ------------------------------------------------------------------ commands
    def build_command(self) -> list[str]:
        out = self.s.output
        if out == "record":
            out_dir = Path(self.s.record_dir).expanduser()
            out_dir.mkdir(parents=True, exist_ok=True)
            stamp = time.strftime("%Y%m%d-%H%M%S")
            video, audio = out_dir / f"iCast-{stamp}.h264", out_dir / f"iCast-{stamp}.wav"
            self.last_recording = (video, audio)
            argv = [self.binary, "record", str(video), str(audio)]
        elif out == "window" or not shutil.which(self.player):
            argv = [self.binary, "gstreamer"]
        else:
            self.port = free_port()
            pipeline = f"{self.video_pipeline(self.port)} {self.audio_pipeline()}"
            argv = [self.binary, "gstreamer", f"--pipeline={pipeline}"]
        if self.udid:
            argv.append(f"--udid={self.udid}")
        argv += split_extra(self.s.extra_args)
        return argv

    def companions(self) -> list[list[str]]:
        if self.port is None:
            return []
        return [self.viewer_command(self.port)]

    def mux_command(self) -> list[str] | None:
        """ffmpeg command to turn the raw recording into a shareable MP4."""
        if not self.last_recording:
            return None
        video, audio = self.last_recording
        return ["ffmpeg", "-y", "-i", str(video), "-i", str(audio), "-c:v", "copy", "-c:a", "aac",
                str(video.with_suffix(".mp4"))]
