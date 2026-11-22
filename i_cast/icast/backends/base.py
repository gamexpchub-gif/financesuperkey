"""Common interface for casting engines."""

from __future__ import annotations

import re
import shlex
from abc import ABC, abstractmethod
from enum import Enum


class CastState(str, Enum):
    IDLE = "idle"
    STARTING = "starting"
    WAITING = "waiting"      # receiver up, waiting for the iPhone
    STREAMING = "streaming"  # iPhone screen is live
    ERROR = "error"


class Backend(ABC):
    name: str = "backend"
    binary: str = ""

    @abstractmethod
    def build_command(self) -> list[str]:
        """Return argv to launch the engine."""

    def classify(self, line: str) -> CastState | None:
        """Map an engine log line to a state transition (None = no change)."""
        for state, patterns in self.STATE_PATTERNS:
            if any(re.search(p, line, re.IGNORECASE) for p in patterns):
                return state
        return None

    STATE_PATTERNS: tuple[tuple[CastState, tuple[str, ...]], ...] = ()


def split_extra(extra: str) -> list[str]:
    try:
        return shlex.split(extra or "")
    except ValueError:
        return []
