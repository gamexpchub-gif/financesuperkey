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

    def step_timeout(self, argv: list[str]) -> float:
        return 45.0

    def step_timed_out(self, argv: list[str], timeout: float, output: str) -> str:
        return f"'{' '.join(argv[:2])}' did not finish within {timeout:.0f} s."

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


class StepTimeout(Exception):
    pass


def _run_one(argv: list[str], log, humanize, timeout: float, cancel) -> tuple[int, str]:
    """Run one step, streaming its output live; kill it on timeout or cancel."""
    import os
    import signal
    import threading
    import time

    proc = subprocess.Popen(argv, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                            text=True, bufsize=1, start_new_session=True)
    lines: list[str] = []

    def pump() -> None:
        assert proc.stdout is not None
        for raw in proc.stdout:
            raw = raw.rstrip()
            if raw:
                lines.append(raw)
                log(humanize(raw))

    reader = threading.Thread(target=pump, daemon=True)
    reader.start()
    deadline = time.monotonic() + timeout
    reason = ""
    while proc.poll() is None:
        if cancel is not None and cancel.is_set():
            reason = "cancelled"
        elif time.monotonic() > deadline:
            reason = "timeout"
        if reason:
            try:
                os.killpg(proc.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            proc.wait()
            break
        time.sleep(0.05)
    reader.join(timeout=1)
    if reason == "timeout":
        raise StepTimeout("\n".join(lines))
    if reason == "cancelled":
        return -1, "cancelled"
    return proc.returncode, "\n".join(lines)


def run_steps(steps: list[list[str]], backend: Backend, log, best_effort: bool = False, cancel=None) -> str:
    """Run steps in order; return "" on success or a plain-language error.

    Output is streamed to ``log`` as it happens. best_effort=True (cleanup) runs every step even if an
    earlier one failed and reports problems quietly. ``cancel`` (threading.Event) stops at once.
    """
    first_error = ""
    for argv in steps:
        if cancel is not None and cancel.is_set():
            return "Cancelled"
        log(f"$ {' '.join(argv)}")
        if not shutil.which(argv[0]):
            err = f"'{argv[0]}' is not installed. Run scripts/install.sh."
        else:
            timeout = backend.step_timeout(argv)
            try:
                code, out = _run_one(argv, log, backend.humanize, timeout, cancel)
            except StepTimeout as exc:
                err = backend.step_timed_out(argv, timeout, str(exc))
            except OSError as exc:
                err = f"{argv[0]} could not start: {exc}"
            else:
                if out == "cancelled" and code == -1:
                    return "Cancelled"
                err = backend.step_failed(argv, code, out) or ""
        if err:
            if not best_effort:
                return err
            log(f"(cleanup) {err}")
            first_error = first_error or err
    return first_error
