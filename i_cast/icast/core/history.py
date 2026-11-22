"""Connection history: one record per casting session, kept as JSON."""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from pathlib import Path

from icast.core.config import config_dir

LIMIT = 200


@dataclass
class SessionRecord:
    started: str          # ISO local time
    mode: str             # Wireless / Wired
    device: str           # iPhone name or "AirPlay client"
    duration_s: int       # seconds actually streaming
    result: str           # Completed / No connection / Failed


def history_path() -> Path:
    return config_dir() / "history.json"


def load(path: Path | None = None) -> list[SessionRecord]:
    path = path or history_path()
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
        return [SessionRecord(**r) for r in raw if isinstance(r, dict)]
    except (OSError, ValueError, TypeError):
        return []


def append(record: SessionRecord, path: Path | None = None) -> None:
    path = path or history_path()
    items = [record] + load(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps([asdict(r) for r in items[:LIMIT]], indent=2), encoding="utf-8")


def clear(path: Path | None = None) -> None:
    path = path or history_path()
    try:
        path.unlink()
    except FileNotFoundError:
        pass


def fmt_duration(seconds: int) -> str:
    h, rem = divmod(max(0, int(seconds)), 3600)
    m, s = divmod(rem, 60)
    return f"{h:d}:{m:02d}:{s:02d}" if h else f"{m:d}:{s:02d}"
