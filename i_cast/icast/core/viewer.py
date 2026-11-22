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

TITLE = "iCast — iPhone"

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
