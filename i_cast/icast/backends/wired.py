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
import sys
import shutil
import socket
import subprocess
import time
from functools import lru_cache
from pathlib import Path

from icast.backends.base import Backend, CastState, split_extra
from icast.core import viewer
from icast.core.config import WiredSettings

VIEWER_TITLE = "iCast - iPhone"
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
    from icast.core.usbcap import _sysfs
    try:
        for dev in (_sysfs() / "bus" / "usb" / "devices").iterdir():
            vid = dev / "idVendor"
            if vid.exists() and vid.read_text().strip() == "05ac":
                return True
    except OSError:
        pass
    return False


def iphone_stuck() -> bool:
    """An iPhone is on the USB bus but usbmuxd can't see it (e.g. left in capture mode)."""
    if not apple_device_connected():
        return False
    if not usbmuxd_running():
        return True
    from icast.core.devices import list_udids
    return not list_udids()


def recover_usb(force: bool = False) -> str:
    """Self-heal: restart usbmuxd and software re-plug an iPhone that usbmuxd lost. Returns a log line or ''."""
    if not os.path.exists(USB_HELPER):
        return ""
    if not (force or usbmuxd_masked() or iphone_stuck()):
        return ""
    try:
        res = subprocess.run(["sudo", "-n", USB_HELPER, "recover"], capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.TimeoutExpired):
        return "Could not reset the iPhone's USB automatically — unplug and re-plug the cable."
    if res.returncode != 0:
        return "Could not reset the iPhone's USB automatically — run scripts/install.sh, then re-plug the cable."
    return "iPhone was stuck in an earlier USB mode — reset it (software re-plug) and restarted usbmuxd"


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
        (CastState.STREAMING, (viewer.STREAM_STARTED, r"PING received",
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
        # 1) trusted? (checked now, while usbmuxd still runs — qvh's own check is skipped later)
        # 2) switch the iPhone into screen-capture mode with iCast's own, time-limited activation
        #    (qvh's `activate` can wait forever for the iPhone to re-connect)
        steps = [["idevicepair", "-u", self.udid, "validate"],
                 [sys.executable, "-m", "icast.core.usbcap", "activate", self.udid]]
        # 2) Linux won't switch to that configuration while usbmuxd / ipheth hold the iPhone
        #    ("Could not retrieve config"), so release it for the duration of the cast.
        if os.path.exists(USB_HELPER):
            steps.append(["sudo", "-n", USB_HELPER, "prepare", self.udid])
        return steps

    def preflight(self) -> str | None:
        """Problems that make a wired cast impossible, found before touching the iPhone."""
        if self.s.output != "record" and not shutil.which(self.player) and self.s.output == "fullscreen":
            return "The fullscreen viewer (mpv or ffplay) is missing — run scripts/install.sh."
        if not os.path.exists(USB_HELPER) or not os.path.exists(QVH_PATCH_MARKER):
            return ("iCast's USB components are out of date (USB helper / patched qvh). "
                    "Run scripts/install.sh once, re-plug the iPhone, then press Start Wired.")
        return None

    def engine_env(self) -> dict[str, str]:
        # usbmuxd is paused during the cast; pairing was already verified by `qvh activate`.
        return {"QVH_SKIP_PAIR_CHECK": "1"} if self.udid and os.path.exists(USB_HELPER) else {}

    def engine_ready_steps(self, line: str) -> list[list[str]]:
        # qvh has switched the configuration and holds the capture interface: bring usbmuxd back so
        # the iPhone sees a live usbmux host next to the capture session (as with QuickTime on macOS).
        if "waiting for ping" in line and not self._resumed and self.udid and os.path.exists(USB_HELPER):
            self._resumed = True
            return [["sudo", "-n", USB_HELPER, "resume", self.udid]]
        return []

    def cleanup_steps(self) -> list[list[str]]:
        if not self.udid:
            return []
        # 1) switch the iPhone back to normal USB mode — straight over USB (patched qvh), so this also
        #    rescues an iPhone that usbmuxd can no longer see because it is stuck half-way in capture mode
        # 2) hand it back to usbmuxd / the tethering driver
        steps = [["env", "QVH_SKIP_PAIR_CHECK=1", self.binary, "deactivate", f"--udid={self.udid}"]]
        if os.path.exists(USB_HELPER):
            steps.append(["sudo", "-n", USB_HELPER, "restore", self.udid])
        return steps

    def step_timeout(self, argv: list[str]) -> float:
        return 25.0 if "activate" in argv else 45.0

    def step_timed_out(self, argv: list[str], timeout: float, output: str) -> str:
        if "activate" in argv:
            return ("The iPhone did not come back in screen-capture mode within "
                    f"{timeout:.0f} s. Unplug the iPhone, plug it back in, unlock it and press Start Wired again. "
                    "If it happens again: Connection History → Copy log, and send the log.")
        return super().step_timed_out(argv, timeout, output)

    def step_failed(self, argv: list[str], code: int, output: str) -> str | None:
        if argv[0] == "idevicepair":
            if code == 0:
                return None
            return ("This computer is not trusted by the iPhone yet. Unlock the iPhone, tap “Trust”, "
                    "enter the passcode, then press Start Wired again.")
        if argv[:2] == ["sudo", "-n"] and code != 0:
            if "password" in output.lower() or "not allowed" in output.lower():
                return ("iCast needs its USB helper permission. Run scripts/install.sh once more, "
                        "re-plug the iPhone, then press Start Wired.")
            return self.explain(output.strip().splitlines()[-1] if output.strip() else "USB helper failed")
        for line in output.splitlines():
            human = self.humanize(line.strip())
            if human.startswith("FATAL:"):
                return self.explain(human)
        if code != 0 and "activate" in argv:
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
                 player: str | None = None, screen: tuple[int, int, int, int] | None = None) -> None:
        self.s = settings
        self.udid = udid or settings.preferred_udid
        self.binary = binary
        self.player = player or viewer.available_player() or "ffplay"
        self.port: int | None = None
        # (x, y, width, height) of the monitor to fill; makes fullscreen work even without a WM.
        self.screen = screen
        self.last_recording: tuple[Path, Path] | None = None
        self._resumed = False

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
        # mpv (preferred: Esc = small window, F = fullscreen, Q = stop) or ffplay, both low-latency
        return viewer.viewer_command(tcp_listen_port=port, latency=self.s.latency_mode, fullscreen=True,
                                     screen=self.screen, player=self.player)

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
