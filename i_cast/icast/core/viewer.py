"""iCast's fullscreen viewer (ffplay) — shared by wired (qvh) and AirPlay (UxPlay -vrtp).

Why our own viewer instead of the engine's window: the iPhone changes the picture's shape when it
rotates (AirPlay keeps the height and changes the width). Engine windows keep the size of the first
(portrait) frame, so a landscape game ends up small in the middle. ffplay re-fits every frame to
the screen, keeping its shape: portrait fills the height, landscape fills the width — automatically.

Latency: no input buffering, no probing delay, low-delay decoding, and in "ultra" mode every frame
is shown the moment it is decoded (stale frames are dropped), so delay can't build up.
"""

from __future__ import annotations

import os
import socket
import tempfile
from pathlib import Path

TITLE = "iCast - iPhone"  # ASCII: some locales drop non-ASCII window titles

_FLIP = {"H": ["hflip"], "V": ["vflip"], "I": ["hflip", "vflip"]}
_ROTATE = {"R": ["transpose=1"], "L": ["transpose=2"]}


def free_udp_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def write_rtp_sdp(port: int, codec: str = "H264") -> Path:
    """SDP describing the RTP video UxPlay sends (rtph264pay -> payload 96, 90 kHz)."""
    base = Path(os.environ.get("XDG_RUNTIME_DIR") or tempfile.gettempdir())
    fd, name = tempfile.mkstemp(prefix="icast-", suffix=".sdp", dir=base)
    with os.fdopen(fd, "w", encoding="ascii") as fh:
        fh.write("v=0\no=- 0 0 IN IP4 127.0.0.1\ns=iCast\nc=IN IP4 127.0.0.1\nt=0 0\n"
                 f"m=video {port} RTP/AVP 96\na=rtpmap:96 {codec}/90000\n")
    return Path(name)


def ffplay_command(source: list[str], *, latency: str = "ultra", fullscreen: bool = True,
                   screen: tuple[int, int, int, int] | None = None, rotation: str = "none",
                   flip: str = "none", player: str = "ffplay") -> list[str]:
    filters = _FLIP.get(flip, []) + _ROTATE.get(rotation, [])
    if latency != "smooth":
        filters.append("setpts=0")           # show each frame now; never wait on timestamps
    argv = [player, "-hide_banner", "-nostats", "-loglevel", "info",
            "-fflags", "nobuffer", "-flags", "low_delay", "-probesize", "32", "-analyzeduration", "0",
            "-framedrop", "-sync", "ext"]
    if filters:
        argv += ["-vf", ",".join(filters)]
    argv += ["-an", "-window_title", TITLE]
    if fullscreen:
        argv += ["-fs", "-noborder"]
        if screen:
            x, y, w, h = (int(v) for v in screen)
            argv += ["-left", str(x), "-top", str(y), "-x", str(w), "-y", str(h)]
    return argv + source


def rtp_source(sdp: Path) -> list[str]:
    return ["-protocol_whitelist", "file,udp,rtp", "-reorder_queue_size", "0", "-i", str(sdp)]


def port_bound(port: int, proto: str = "tcp") -> bool:
    """True when a local socket is LISTENing (tcp) / bound (udp) on ``port`` — read from /proc, no traffic."""
    want = f":{port:04X}"
    state = "0A" if proto == "tcp" else "07"
    for table in (f"/proc/net/{proto}", f"/proc/net/{proto}6"):
        try:
            with open(table, encoding="ascii") as fh:
                next(fh, None)
                for row in fh:
                    cols = row.split()
                    if len(cols) > 3 and cols[1].endswith(want) and cols[3] == state:
                        return True
        except OSError:
            continue
    return False


# --------------------------------------------------------------------------------- mpv (preferred)
# mpv lets iCast define the keys: Esc leaves fullscreen into a small window (casting continues),
# F / Enter / double-click go fullscreen again, Q or closing the window stops casting.
INPUT_CONF = """# iCast viewer keys
ESC          set fullscreen no
f            cycle fullscreen
F            cycle fullscreen
ENTER        cycle fullscreen
MBTN_LEFT_DBL cycle fullscreen
q            quit
Q            quit
CLOSE_WIN    quit
"""


def available_player() -> str | None:
    import shutil

    for name in ("mpv", "ffplay"):
        if shutil.which(name):
            return name
    return None


def _mpv_options(binary: str = "mpv") -> set[str]:
    import subprocess

    try:
        out = subprocess.run([binary, "--list-options"], capture_output=True, text=True, timeout=10).stdout
    except (OSError, subprocess.TimeoutExpired):
        return set()
    return {line.split()[0] for line in out.splitlines() if line.strip().startswith("--")}


_MPV_OPTS_CACHE: dict[str, set[str]] = {}


def mpv_supports(option: str, binary: str = "mpv") -> bool:
    if binary not in _MPV_OPTS_CACHE:
        _MPV_OPTS_CACHE[binary] = _mpv_options(binary)
    opts = _MPV_OPTS_CACHE[binary]
    return not opts or option in opts      # unknown list -> assume a current mpv


def input_conf_path() -> Path:
    base = Path(os.environ.get("XDG_RUNTIME_DIR") or tempfile.gettempdir())
    path = base / "icast-viewer-input.conf"
    try:
        if not path.exists() or path.read_text() != INPUT_CONF:
            path.write_text(INPUT_CONF)
    except OSError:
        pass
    return path


def mpv_command(url: str, *, demux_opts: list[str] | None = None, demux_format: str | None = None,
                latency: str = "ultra", fullscreen: bool = True, screen: tuple[int, int, int, int] | None = None,
                rotation: str = "none", flip: str = "none", player: str = "mpv") -> list[str]:
    argv = [player, "--profile=low-latency", "--no-cache", "--no-audio", "--quiet",
            "--no-input-default-bindings", f"--input-conf={input_conf_path()}",
            f"--title={TITLE}", "--force-window=immediate", "--keepaspect=yes"]
    if latency != "smooth":
        argv.append("--untimed")            # show each frame the moment it is decoded
    if mpv_supports("--auto-window-resize", player):
        argv.append("--auto-window-resize=no")   # the small window keeps its size when the iPhone rotates
    if screen:
        x, y, w, h = (int(v) for v in screen)
        argv += [f"--geometry={max(w // 2, 320)}x{max(h // 2, 240)}+{x + w // 4}+{y + h // 4}"]
    else:
        argv.append("--autofit=50%")
    if fullscreen:
        argv.append("--fs")
    if rotation == "R":
        argv.append("--video-rotate=90")
    elif rotation == "L":
        argv.append("--video-rotate=270")
    flips = _FLIP.get(flip, [])
    if flips:
        argv.append(f"--vf=lavfi=[{','.join(flips)}]")
    if demux_format:
        argv.append(f"--demuxer-lavf-format={demux_format}")
    if demux_opts:
        argv.append("--demuxer-lavf-o=" + ",".join(demux_opts))
    return argv + [url]


def viewer_command(*, rtp_sdp: Path | None = None, tcp_listen_port: int | None = None, latency: str = "ultra",
                   fullscreen: bool = True, screen: tuple[int, int, int, int] | None = None,
                   rotation: str = "none", flip: str = "none", player: str | None = None) -> list[str]:
    """Viewer for an RTP/SDP stream (AirPlay via UxPlay -vrtp) or a raw H.264 TCP stream (qvh)."""
    player = player or available_player() or "ffplay"
    if os.path.basename(player).startswith("mpv"):
        if rtp_sdp is not None:
            return mpv_command(str(rtp_sdp), demux_opts=["protocol_whitelist=[file,udp,rtp]", "reorder_queue_size=0",
                                                         "fflags=+nobuffer", "buffer_size=4194304"],
                               latency=latency, fullscreen=fullscreen, screen=screen, rotation=rotation, flip=flip,
                               player=player)
        return mpv_command(f"lavf://tcp://127.0.0.1:{tcp_listen_port}?listen=1", demux_format="h264",
                           demux_opts=["fflags=+nobuffer"], latency=latency, fullscreen=fullscreen, screen=screen,
                           rotation=rotation, flip=flip, player=player)
    if rtp_sdp is not None:
        source = rtp_source(rtp_sdp)
    else:
        source = ["-f", "h264", f"tcp://127.0.0.1:{tcp_listen_port}?listen=1"]
    return ffplay_command(source, latency=latency, fullscreen=fullscreen, screen=screen, rotation=rotation,
                          flip=flip, player=player)


# log lines that mean "the picture is arriving" (ffplay / mpv)
STREAM_STARTED = r"\[viewer\].*(Input #0|Stream #0|\(\+\) Video|VO: \[)"
