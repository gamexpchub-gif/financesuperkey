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

import json
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
USB_HELPER = "/usr/local/libexec/icast-usb"  # installed by scripts/install.sh
QVH_PATCH_MARKER = "/usr/local/share/icast/qvh-patched-d81396e"  # qvh built by scripts/build-qvh.sh


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


def port_listening(port: int) -> bool:
    """True when something on this machine is LISTENing on TCP ``port`` (reads /proc, no connect).

    Connecting to probe would be accepted by ffplay as *the* stream and break it, so we look instead.
    """
    want = f":{port:04X}"
    for table in ("/proc/net/tcp", "/proc/net/tcp6"):
        try:
            with open(table, encoding="ascii") as fh:
                next(fh, None)
                for row in fh:
                    cols = row.split()
                    if len(cols) > 3 and cols[1].endswith(want) and cols[3] == "0A":
                        return True
        except OSError:
            continue
    return False


def usbmuxd_running() -> bool:
    try:
        return subprocess.run(["pgrep", "-x", "usbmuxd"], capture_output=True, timeout=3).returncode == 0
    except (OSError, subprocess.TimeoutExpired):
        return False


def usbmuxd_masked() -> bool:
    """True when a previous session left usbmuxd paused (runtime-masked)."""
    if not shutil.which("systemctl"):
        return False
    try:
        out = subprocess.run(["systemctl", "is-enabled", "usbmuxd.service"], capture_output=True, text=True, timeout=3)
    except (OSError, subprocess.TimeoutExpired):
        return False
    return out.stdout.strip().startswith("masked")


def apple_device_connected() -> bool:
    try:
        for dev in Path("/sys/bus/usb/devices").iterdir():
            vid = dev / "idVendor"
            if vid.exists() and vid.read_text().strip() == "05ac":
                return True
    except OSError:
        pass
    return False


def recover_usb() -> str:
    """Self-heal at startup: restart usbmuxd if iCast left it paused or stopped. Returns a log line or ''."""
    if os.path.exists(USB_HELPER) and (usbmuxd_masked() or (apple_device_connected() and not usbmuxd_running())):
        try:
            subprocess.run(["sudo", "-n", USB_HELPER, "recover"], capture_output=True, timeout=20)
        except (OSError, subprocess.TimeoutExpired):
            return "Could not restart usbmuxd automatically. Run: sudo systemctl unmask --runtime usbmuxd"
        return "Restored usbmuxd left paused by an earlier session"
    return ""


def pulse_server_running() -> bool:
    """True when a PulseAudio / PipeWire-Pulse server socket exists for this user."""
    if os.environ.get("PULSE_SERVER"):
        return True
    runtime = os.environ.get("XDG_RUNTIME_DIR") or f"/run/user/{os.getuid()}"
    return Path(runtime, "pulse", "native").exists()


class WiredBackend(Backend):
    name = "Wired (USB)"
    binary = "qvh"
    companion_delay_s = 0.6      # fallback wait when /proc/net/tcp is unavailable
    companion_timeout_s = 10.0   # max wait for the viewer to start listening
    prepare_label = "Preparing iPhone for capture…"

    # Matched against humanize()d lines. qvh logs logrus JSON ({"level","msg"}) and prints fatal
    # problems as {"error_message", "original_error"}; ffplay (viewer) announces the stream with "Input #0".
    STATE_PATTERNS = (
        (CastState.ERROR, (r"^FATAL:", r"no iOS devices are connected", r"usbSerial:.*not found",
                           r"Unable to find device", r"LIBUSB_ERROR", r"access denied", r"\bpanic\b",
                           r"Invalid Pipeline", r"Failed creating custom pipeline", r"failed connecting to usb",
                           r"Error enabling QT config")),
        (CastState.STREAMING, (r"\[viewer\].*Input #0", r"\[viewer\].*Stream #0:0", r"PING received",
                               r"Start Session")),
        (CastState.WAITING, (r"Starting Gstreamer", r"Gstreamer is running", r"USB connection ready",
                             r"waiting for ping", r"no usbSerial specified")),
    )

    def humanize(self, line: str) -> str:
        raw = line.removeprefix("[viewer] ")
        if not raw.startswith("{"):
            return line
        try:
            data = json.loads(raw)
        except ValueError:
            return line
        if not isinstance(data, dict):
            return line
        if "error_message" in data:
            # printErrJSON(): qvh gives up after this
            return f"FATAL: {data['error_message']} — {data.get('original_error', '')}".rstrip(" —")
        msg = data.get("msg")
        if msg is None:
            return line
        level = str(data.get("level", "info")).lower()
        return msg if level in ("info", "debug") else f"{level.upper()}: {msg}"

    EXPLANATIONS = (
        (r"LIBUSB_ERROR_ACCESS|bad access|code -3\]|access denied|permission denied|operation not permitted",
         "No permission to use the iPhone over USB. Unplug the iPhone and plug it back in "
         "(the USB rule from install.sh applies on re-plug). If it persists, run scripts/install.sh again."),
        (r"LIBUSB_ERROR_BUSY|resource busy|code -6\]",
         "The iPhone's USB connection is busy — another app is using it. Close other mirroring tools, "
         "press Reset USB, re-plug the cable and try again."),
        (r"no iOS devices are connected|Unable to find device|usbSerial:.*not found|LIBUSB_ERROR_NO_DEVICE|no device \[code -4\]",
         "iPhone not found on USB. Unlock it, re-plug the cable (try another port) and press Start again."),
        (r"Error enabling QT config|failed connecting to usb|LIBUSB_ERROR_(PIPE|IO|TIMEOUT)",
         "The iPhone didn't switch into screen-capture mode. Unlock the iPhone, press Reset USB, "
         "re-plug the cable and press Start Wired again."),
        (r"Invalid Pipeline|no element|Failed creating custom pipeline",
         "A GStreamer plugin is missing. Run scripts/install.sh again (needs gstreamer1.0-plugins-bad, -libav)."),
    )

    def explain(self, line: str) -> str:
        import re
        for pattern, text in self.EXPLANATIONS:
            if re.search(pattern, line, re.IGNORECASE):
                return text
        return line.removeprefix("FATAL: ")

    def companions_ready(self) -> bool:
        if self.port is None:
            return True
        if not os.path.exists("/proc/net/tcp"):
            return True  # can't check; caller falls back to the fixed delay
        return port_listening(self.port)

    # ------------------------------------------------- exclusive USB access
    def prepare_steps(self) -> list[list[str]]:
        if not self.udid:
            return []
        # 1) let the iPhone expose its screen-capture USB configuration (it re-connects once)
        steps = [[self.binary, "activate", f"--udid={self.udid}"]]
        # 2) Linux won't switch to that configuration while usbmuxd / ipheth hold the iPhone
        #    ("Could not retrieve config"), so release it for the duration of the cast.
        if os.path.exists(USB_HELPER):
            steps.append(["sudo", "-n", USB_HELPER, "prepare", self.udid])
        return steps

    def preflight(self) -> str | None:
        """Problems that make a wired cast impossible, found before touching the iPhone."""
        if self.s.output != "record" and not shutil.which(self.player) and self.s.output == "fullscreen":
            return "ffplay (from ffmpeg) is missing — run scripts/install.sh."
        if not os.path.exists(USB_HELPER) or not os.path.exists(QVH_PATCH_MARKER):
            return ("iCast's USB components are out of date (USB helper / patched qvh). "
                    "Run scripts/install.sh once, re-plug the iPhone, then press Start Wired.")
        return None

    def engine_env(self) -> dict[str, str]:
        # usbmuxd is paused during the cast; pairing was already verified by `qvh activate`.
        return {"QVH_SKIP_PAIR_CHECK": "1"} if self.udid and os.path.exists(USB_HELPER) else {}

    def cleanup_steps(self) -> list[list[str]]:
        if not self.udid:
            return []
        steps = []
        if os.path.exists(USB_HELPER):
            # usbmuxd first: every unpatched qvh command (deactivate included) needs it
            steps.append(["sudo", "-n", USB_HELPER, "restore", self.udid])
            steps.append(["sleep", "1"])
        steps.append([self.binary, "deactivate", f"--udid={self.udid}"])
        return steps

    def step_failed(self, argv: list[str], code: int, output: str) -> str | None:
        if argv[:2] == ["sudo", "-n"] and code != 0:
            if "password" in output.lower() or "not allowed" in output.lower():
                return ("iCast needs its USB helper permission. Run scripts/install.sh once more, "
                        "re-plug the iPhone, then press Start Wired.")
            return self.explain(output.strip().splitlines()[-1] if output.strip() else "USB helper failed")
        for line in output.splitlines():
            human = self.humanize(line.strip())
            if human.startswith("FATAL:"):
                return self.explain(human)
        if code != 0 and argv[1:2] == ["activate"]:
            last = output.strip().splitlines()[-1] if output.strip() else f"exit code {code}"
            return f"Could not switch the iPhone into capture mode: {self.explain(self.humanize(last))}"
        return None

    def reset_command(self) -> list[str]:
        """Take the iPhone out of a stuck screen-capture USB mode."""
        argv = [self.binary, "deactivate"]
        if self.udid:
            argv.append(f"--udid={self.udid}")
        return argv

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
            # info level so iCast sees "Input #0" the moment the picture arrives; no stats spam
            self.player, "-hide_banner", "-nostats", "-loglevel", "info",
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
