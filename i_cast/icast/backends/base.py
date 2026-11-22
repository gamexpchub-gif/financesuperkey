"""Common interface for casting engines."""

from __future__ import annotations

import re
import shlex
import shutil
import subprocess
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

    def companions(self) -> list[list[str]]:
        """Helper processes (e.g. the fullscreen viewer) started *before* the engine.

        If a companion exits, the whole session ends; when the engine exits, companions are stopped.
        Must be called after build_command().
        """
        return []

    companion_delay_s: float = 0.0
    companion_timeout_s: float = 10.0

    def companions_ready(self) -> bool:
        """True once the companions can accept the engine (e.g. the viewer is listening)."""
        return True

    def engine_env(self) -> dict[str, str]:
        """Extra environment variables for the engine process."""
        return {}

    def preflight(self) -> str | None:
        """Plain-language blocker found before starting anything, else None."""
        return None

    # When the engine exits because of a recoverable problem, the backend may ask for exactly one
    # automatic restart (e.g. AirPlay name conflict -> restart with a fresh device ID).
    wants_retry: bool = False
    prepare_label: str = "Preparing…"

    def prepare_retry(self) -> str:
        """Adjust for the automatic retry; returns a log line describing what changed."""
        return ""

    def prepare_steps(self) -> list[list[str]]:
        """Short commands run (in order, off the UI thread) before the viewer and engine start."""
        return []

    def cleanup_steps(self) -> list[list[str]]:
        """Commands run after the session ends — also when iCast quits — to hand the device back."""
        return []

    def step_failed(self, argv: list[str], code: int, output: str) -> str | None:
        """Plain-language error if a prepare step failed fatally, else None."""
        return None

    def explain(self, line: str) -> str:
        """Plain-language reason + fix for an error line (falls back to the line itself)."""
        return line

    def humanize(self, line: str) -> str:
        """Turn an engine log line into something readable for the activity log."""
        return line

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


def run_steps(steps: list[list[str]], backend: Backend, log, best_effort: bool = False) -> str:
    """Run steps in order; return "" on success or a plain-language error.

    best_effort=True (cleanup) runs every step even if an earlier one failed.
    """
    first_error = ""
    for argv in steps:
        log(f"$ {' '.join(argv)}")
        if not shutil.which(argv[0]):
            err = f"'{argv[0]}' is not installed. Run scripts/install.sh."
        else:
            try:
                res = subprocess.run(argv, capture_output=True, text=True, timeout=45, stdin=subprocess.DEVNULL)
            except (OSError, subprocess.TimeoutExpired) as exc:
                err = f"{argv[0]} did not finish: {exc}"
            else:
                out = (res.stdout or "") + (res.stderr or "")
                for line in out.strip().splitlines()[-12:]:
                    log(backend.humanize(line.strip()))
                err = backend.step_failed(argv, res.returncode, out) or ""
        if err:
            if not best_effort:
                return err
            log(f"⚠ {err}")
            first_error = first_error or err
    return first_error
