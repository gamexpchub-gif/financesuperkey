"""Qt-aware session controller: owns the running engine and broadcasts state."""

from __future__ import annotations

import shutil
import subprocess
import time

from PySide6.QtCore import QObject, QRunnable, QThreadPool, Signal

from icast.backends.base import Backend, CastState
from icast.core import devices
from icast.core.process import ManagedProcess


class CastController(QObject):
    state_changed = Signal(str, str)  # state, detail
    log_line = Signal(str)
    _line_from_thread = Signal(str)
    _exit_from_thread = Signal(int)

    def __init__(self) -> None:
        super().__init__()
        self.backend: Backend | None = None
        self.proc: ManagedProcess | None = None
        self.state = CastState.IDLE
        self.started_at: float | None = None
        self._line_from_thread.connect(self._on_line)
        self._exit_from_thread.connect(self._on_exit)

    @property
    def active(self) -> bool:
        return self.proc is not None and self.proc.running

    def _set(self, state: CastState, detail: str = "") -> None:
        if state == CastState.STREAMING and self.state != CastState.STREAMING:
            self.started_at = time.monotonic()
        elif state != CastState.STREAMING:
            self.started_at = None
        self.state = state
        self.state_changed.emit(state.value, detail)

    def start(self, backend: Backend) -> None:
        if self.active:
            return
        if not shutil.which(backend.binary):
            self._set(CastState.ERROR, f"'{backend.binary}' is not installed — open Diagnostics")
            return
        self.backend = backend
        try:
            argv = backend.build_command()
        except OSError as exc:
            self._set(CastState.ERROR, str(exc))
            return
        self.log_line.emit(f"$ {' '.join(argv)}")
        self.proc = ManagedProcess(argv, on_line=self._line_from_thread.emit, on_exit=self._exit_from_thread.emit)
        try:
            self.proc.start()
        except OSError as exc:
            self._set(CastState.ERROR, str(exc))
            return
        self._set(CastState.STARTING, backend.name)

    def stop(self) -> None:
        if self.proc:
            self.log_line.emit("Stopping…")
            self.proc.stop()

    def _on_line(self, line: str) -> None:
        self.log_line.emit(line)
        if self.backend:
            new = self.backend.classify(line)
            if new and new != self.state:
                self._set(new, line[:120])

    def _on_exit(self, code: int) -> None:
        self.log_line.emit(f"Engine exited (code {code})")
        backend, self.proc = self.backend, None
        mux = getattr(backend, "mux_command", lambda: None)()
        if mux and shutil.which("ffmpeg"):
            subprocess.run(mux, capture_output=True)
            self.log_line.emit(f"Recording saved: {mux[-1]}")
        if self.state == CastState.ERROR or code not in (0, -2, -15, 130, 143):
            self._set(CastState.ERROR, f"Engine stopped unexpectedly (code {code}). See log.")
        else:
            self._set(CastState.IDLE, "")


class _DeviceScan(QRunnable):
    def __init__(self, sink: "DeviceWatcher") -> None:
        super().__init__()
        self.sink = sink

    def run(self) -> None:
        try:
            phones = devices.discover()
        except Exception:  # never let a scan crash the app
            phones = []
        self.sink.found.emit(phones)


class DeviceWatcher(QObject):
    found = Signal(list)

    def __init__(self) -> None:
        super().__init__()
        self._busy = False
        self.found.connect(lambda _p: setattr(self, "_busy", False))

    def scan(self) -> None:
        if self._busy:
            return
        self._busy = True
        QThreadPool.globalInstance().start(_DeviceScan(self))
