"""Wired casting over USB using the QuickTime screen-capture protocol (qvh).

Plug the iPhone in with a Lightning/USB‑C cable, unlock it and tap "Trust".
No Wi‑Fi needed; lowest latency and most stable picture.
"""

from __future__ import annotations

import time
from pathlib import Path

from icast.backends.base import Backend, CastState, split_extra
from icast.core.config import WiredSettings


class WiredBackend(Backend):
    name = "Wired (USB)"
    binary = "qvh"

    STATE_PATTERNS = (
        (CastState.ERROR, (r"no device", r"device not found", r"could not", r"permission denied",
                           r"LIBUSB_ERROR", r"\bpanic\b")),
        (CastState.STREAMING, (r"start streaming", r"receiving", r"Start Session", r"ASYN", r"FEED",
                               r"stream started")),
        (CastState.WAITING, (r"activat", r"enabling", r"waiting for", r"Opening device")),
    )

    def __init__(self, settings: WiredSettings, udid: str = "", binary: str = "qvh") -> None:
        self.s = settings
        self.udid = udid or settings.preferred_udid
        self.binary = binary
        self.last_recording: tuple[Path, Path] | None = None

    def build_command(self) -> list[str]:
        if self.s.output == "record":
            out_dir = Path(self.s.record_dir).expanduser()
            out_dir.mkdir(parents=True, exist_ok=True)
            stamp = time.strftime("%Y%m%d-%H%M%S")
            video, audio = out_dir / f"iCast-{stamp}.h264", out_dir / f"iCast-{stamp}.wav"
            self.last_recording = (video, audio)
            argv = [self.binary, "record", str(video), str(audio)]
        else:
            argv = [self.binary, "gstreamer"]
        if self.udid:
            argv.append(f"--udid={self.udid}")
        argv += split_extra(self.s.extra_args)
        return argv

    def mux_command(self) -> list[str] | None:
        """ffmpeg command to turn the raw recording into a shareable MP4."""
        if not self.last_recording:
            return None
        video, audio = self.last_recording
        return ["ffmpeg", "-y", "-i", str(video), "-i", str(audio), "-c:v", "copy", "-c:a", "aac",
                str(video.with_suffix(".mp4"))]
