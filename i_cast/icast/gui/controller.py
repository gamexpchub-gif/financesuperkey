"""Qt-aware session controller: owns the running engine and broadcasts state."""

from __future__ import annotations

import shutil
import subprocess
import time

from PySide6.QtCore import QObject, QRunnable, QThreadPool, QTimer, Signal

from icast.backends.base import Backend, CastState
from icast.core import devices, history
from icast.core.process import ManagedProcess


class CastController(QObject):
    state_changed = Signal(str, str)  # state, detail
    log_line = Signal(str)
    history_changed = Signal()
    _line_from_thread = Signal(str)
    _exit_from_thread = Signal(int)
    _companion_exit = Signal(int)

    def __init__(self) -> None:
        super().__init__()
        self.backend: Backend | None = None
        self.proc: ManagedProcess | None = None
        self.state = CastState.IDLE
        self.started_at: float | None = None
        self.mode = ""
        self.device = ""
        self._session_start = ""
        self._streamed_s = 0.0
        self.companions: list[ManagedProcess] = []
        self._pending_argv: list[str] | None = None
        self._line_from_thread.connect(self._on_line)
        self._exit_from_thread.connect(self._on_exit)
        self._companion_exit.connect(self._on_companion_exit)

    @property
    def active(self) -> bool:
        return self._pending_argv is not None or (self.proc is not None and self.proc.running)

    def _set(self, state: CastState, detail: str = "") -> None:
        if state == CastState.STREAMING and self.state != CastState.STREAMING:
            self.started_at = time.monotonic()
        elif state != CastState.STREAMING:
            if self.started_at is not None:
                self._streamed_s += time.monotonic() - self.started_at
            self.started_at = None
        self.state = state
        self.state_changed.emit(state.value, detail)

    def start(self, backend: Backend, mode: str = "", device: str = "") -> None:
        if self.active:
            return
        self.mode, self.device = mode or backend.name, device
        self._session_start = time.strftime("%Y-%m-%d %H:%M")
        self._streamed_s = 0.0
        if not shutil.which(backend.binary):
            self._set(CastState.ERROR, f"'{backend.binary}' is not installed — open Diagnostics")
            return
        self.backend = backend
        try:
            argv = backend.build_command()
        except OSError as exc:
            self._set(CastState.ERROR, str(exc))
            return
        self.companions = []
        for cargv in backend.companions():
            if not shutil.which(cargv[0]):
                self.log_line.emit(f"'{cargv[0]}' not found — falling back to the standard window")
                continue
            self.log_line.emit(f"$ {' '.join(cargv)}")
            comp = ManagedProcess(cargv, on_line=lambda l: self._line_from_thread.emit(f"[viewer] {l}"),
                                  on_exit=self._companion_exit.emit)
            try:
                comp.start()
                self.companions.append(comp)
            except OSError as exc:
                self.log_line.emit(f"Viewer failed to start: {exc}")
        self._set(CastState.STARTING, backend.name)
        self._pending_argv = argv
        delay = int(backend.companion_delay_s * 1000) if self.companions else 0
        QTimer.singleShot(delay, self._launch_engine)

    def _launch_engine(self) -> None:
        argv, self._pending_argv = self._pending_argv, None
        if argv is None:  # stopped while the viewer was opening
            self._stop_companions()
            self._set(CastState.IDLE, "")
            return
        self.log_line.emit(f"$ {' '.join(argv)}")
        self.proc = ManagedProcess(argv, on_line=self._line_from_thread.emit, on_exit=self._exit_from_thread.emit)
        try:
            self.proc.start()
        except OSError as exc:
            self.proc = None
            self._stop_companions()
            self._set(CastState.ERROR, str(exc))

    def stop(self) -> None:
        if self._pending_argv is not None:
            self._pending_argv = None
            self.log_line.emit("Stopping…")
            return
        if self.proc:
            self.log_line.emit("Stopping…")
            self.proc.stop()
        self._stop_companions()

    def _stop_companions(self) -> None:
        comps, self.companions = self.companions, []
        for c in comps:
            c.stop(timeout=2)

    def _on_companion_exit(self, code: int) -> None:
        # The viewer window was closed (Esc / q / window close) -> end the whole session.
        if self.proc and self.proc.running:
            self.log_line.emit("Viewer closed — ending session")
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
        self._stop_companions()
        mux = getattr(backend, "mux_command", lambda: None)()
        if mux and shutil.which("ffmpeg"):
            subprocess.run(mux, capture_output=True)
            self.log_line.emit(f"Recording saved: {mux[-1]}")
        failed = self.state == CastState.ERROR or code not in (0, -2, -15, 130, 143)
        if failed:
            self._set(CastState.ERROR, f"Engine stopped unexpectedly (code {code}). See log.")
        else:
            self._set(CastState.IDLE, "")
        result = "Failed" if failed else ("Completed" if self._streamed_s >= 1 else "No connection")
        try:
            history.append(history.SessionRecord(self._session_start, self.mode, self.device or "—",
                                                 int(self._streamed_s), result))
        except OSError:
            pass
        self.history_changed.emit()


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
