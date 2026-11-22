"""Qt-aware session controller: owns the running engine and broadcasts state."""

from __future__ import annotations

import shutil
import subprocess
import threading
import time

from PySide6.QtCore import QObject, QRunnable, QThreadPool, QTimer, Signal

from icast.backends.base import Backend, CastState, run_steps
from icast.core import devices, history
from icast.core.process import ManagedProcess


class _StepRunner(QRunnable):
    """Runs short prepare/cleanup commands off the UI thread."""

    def __init__(self, steps: list[list[str]], backend: Backend, log, done, best_effort: bool = False,
                 cancel: threading.Event | None = None) -> None:
        super().__init__()
        self.steps, self.backend, self.log, self.done = steps, backend, log, done
        self.best_effort, self.cancel = best_effort, cancel

    def run(self) -> None:
        self.done.emit(run_steps(self.steps, self.backend, self.log.emit, best_effort=self.best_effort,
                                 cancel=self.cancel))


class CastController(QObject):
    """One casting session at a time:  prepare -> viewer (optional) -> engine -> cleanup."""

    state_changed = Signal(str, str)  # state, detail
    log_line = Signal(str)
    history_changed = Signal()
    _line_from_thread = Signal(str)
    _exit_from_thread = Signal(int)
    _companion_exit = Signal(int)
    _prepare_done = Signal(str)
    _cleanup_done = Signal(str)

    NO_PICTURE_HINT_S = 20.0
    NO_PICTURE_HINT = ("No picture from the iPhone yet. Keep the iPhone unlocked, tap “Trust”, "
                       "then try another cable/port. If it keeps waiting, press “Reset USB” on the Wired page.")

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
        self._pending_argv: list[str] | None = None   # set from start() until the engine runs
        self._needs_cleanup: Backend | None = None
        self.cleaning_up = False
        self._last_error = ""
        self._retried = False
        self._cancel = threading.Event()
        self._line_from_thread.connect(self._on_line)
        self._exit_from_thread.connect(self._on_exit)
        self._companion_exit.connect(self._on_companion_exit)
        self._prepare_done.connect(self._after_prepare)
        self._cleanup_done.connect(self._after_cleanup)

    @property
    def active(self) -> bool:
        return self._pending_argv is not None or (self.proc is not None and self.proc.running)

    @property
    def busy(self) -> bool:
        """Active, or still handing the device back after a session."""
        return self.active or self.cleaning_up

    def _set(self, state: CastState, detail: str = "") -> None:
        if state == CastState.STREAMING and self.state != CastState.STREAMING:
            self.started_at = time.monotonic()
        elif state != CastState.STREAMING:
            if self.started_at is not None:
                self._streamed_s += time.monotonic() - self.started_at
            self.started_at = None
        self.state = state
        self.state_changed.emit(state.value, detail)

    # ------------------------------------------------------------------ start
    def start(self, backend: Backend, mode: str = "", device: str = "") -> None:
        if self.busy:
            return
        self.mode, self.device = mode or backend.name, device
        self._last_error = ""
        self._session_start = time.strftime("%Y-%m-%d %H:%M")
        self._streamed_s = 0.0
        if not shutil.which(backend.binary):
            self._set(CastState.ERROR, f"'{backend.binary}' is not installed — run scripts/install.sh")
            return
        self.backend = backend
        self._retried = False
        blocker = backend.preflight()
        if blocker:
            self._last_error = blocker
            self.log_line.emit(f"⚠ {blocker}")
            self._finish(blocker, failed=True)
            return
        try:
            argv = backend.build_command()
        except OSError as exc:
            self._set(CastState.ERROR, str(exc))
            return
        self._pending_argv = argv
        steps = backend.prepare_steps()
        if steps:
            self._needs_cleanup = backend
            self._set(CastState.STARTING, backend.prepare_label)
            self._cancel = threading.Event()
            QThreadPool.globalInstance().start(_StepRunner(steps, backend, self.log_line, self._prepare_done,
                                                           cancel=self._cancel))
        else:
            self._set(CastState.STARTING, backend.name)
            self._after_prepare("")

    def _after_prepare(self, error: str) -> None:
        if self._pending_argv is None or error == "Cancelled":   # user pressed Stop while preparing
            self._pending_argv = None
            self._finish("Cancelled", failed=False)
            return
        if error:
            self._pending_argv = None
            self._last_error = error
            self.log_line.emit(f"⚠ {error}")
            self._finish(error, failed=True)
            return
        backend = self.backend
        assert backend is not None
        self.companions = []
        for cargv in backend.companions():
            self.log_line.emit(f"$ {' '.join(cargv)}")
            comp = ManagedProcess(cargv, on_line=lambda l: self._line_from_thread.emit(f"[viewer] {l}"),
                                  on_exit=self._companion_exit.emit)
            try:
                comp.start()
                self.companions.append(comp)
            except OSError as exc:
                self.log_line.emit(f"Viewer failed to start: {exc}")
        self._set(CastState.STARTING, "Opening viewer…" if self.companions else backend.name)
        self._ready_since = time.monotonic()
        self._ready_deadline = self._ready_since + backend.companion_timeout_s
        self._wait_for_companions()

    def _wait_for_companions(self) -> None:
        """Start the engine only once the viewer is really listening (never on a fixed guess)."""
        if self._pending_argv is None:
            self._stop_companions()
            self._finish("Cancelled", failed=False)
            return
        backend = self.backend
        if not self.companions or backend is None:
            self._launch_engine()
            return
        if any(not c.running for c in self.companions):
            self._pending_argv = None
            self._stop_companions()
            self._finish("The fullscreen viewer closed before the iPhone stream started. See log.", failed=True)
            return
        waited = time.monotonic() - self._ready_since
        if backend.companions_ready() and waited >= 0.05:
            self.log_line.emit(f"Viewer ready after {waited:.2f}s")
            self._launch_engine()
        elif time.monotonic() > self._ready_deadline:
            self._pending_argv = None
            self._stop_companions()
            self._finish("The fullscreen viewer (ffplay) did not start. Check that ffmpeg is installed.", failed=True)
        else:
            QTimer.singleShot(50, self._wait_for_companions)

    def _launch_engine(self) -> None:
        argv, self._pending_argv = self._pending_argv, None
        if argv is None:
            return
        self.log_line.emit(f"$ {' '.join(argv)}")
        env = self.backend.engine_env() if self.backend else {}
        self.proc = ManagedProcess(argv, on_line=self._line_from_thread.emit, on_exit=self._exit_from_thread.emit,
                                   env=env or None)
        try:
            self.proc.start()
        except OSError as exc:
            self.proc = None
            self._stop_companions()
            self._finish(str(exc), failed=True)
            return
        self._set(CastState.WAITING, "Waiting for the iPhone screen…")
        proc = self.proc
        QTimer.singleShot(int(self.NO_PICTURE_HINT_S * 1000), lambda: self._no_picture_check(proc))

    def _no_picture_check(self, proc: ManagedProcess) -> None:
        if proc is self.proc and proc.running and self.state in (CastState.STARTING, CastState.WAITING):
            self.log_line.emit(self.NO_PICTURE_HINT)
            self._set(CastState.WAITING, self.NO_PICTURE_HINT)

    # ------------------------------------------------------------------- stop
    def stop(self) -> None:
        if self._pending_argv is not None:
            self._pending_argv = None   # prepare / viewer wait will notice and wind down
            self._cancel.set()          # kill a running prepare step right away
            self.log_line.emit("Stopping…")
            return
        if self.proc:
            self.log_line.emit("Stopping…")
            self.proc.stop()
        self._stop_companions()

    def shutdown(self) -> None:
        """App is quitting: stop everything and hand the device back synchronously."""
        self._pending_argv = None
        if self.proc:
            self.proc.stop()
            self.proc.wait(5)
        self._stop_companions()
        backend, self._needs_cleanup = self._needs_cleanup, None
        if backend:
            run_steps(backend.cleanup_steps(), backend, lambda _l: None, best_effort=True)

    def reset_usb(self, argv: list[str]) -> tuple[bool, str]:
        """Run a short one-off engine command (e.g. ``qvh deactivate``) and report its output."""
        if self.busy:
            return False, "Stop casting first."
        if not shutil.which(argv[0]):
            return False, f"'{argv[0]}' is not installed."
        try:
            res = subprocess.run(argv, capture_output=True, text=True, timeout=20)
        except (OSError, subprocess.TimeoutExpired) as exc:
            return False, str(exc)
        out = "\n".join(self.backend.humanize(l) if self.backend else l
                         for l in (res.stdout + res.stderr).strip().splitlines()[-6:])
        self.log_line.emit(f"$ {' '.join(argv)}\n{out}")
        return res.returncode == 0, out or "Done."

    def _stop_companions(self) -> None:
        comps, self.companions = self.companions, []
        for c in comps:
            c.on_exit = lambda _c: None
            c.stop(timeout=2)

    def _on_companion_exit(self, code: int) -> None:
        # The viewer window was closed (Esc / q / window close) -> end the whole session.
        if self.proc and self.proc.running:
            self.log_line.emit("Viewer closed — ending session")
            self.proc.stop()

    # ---------------------------------------------------------------- events
    def _on_line(self, line: str) -> None:
        if self.backend:
            line = self.backend.humanize(line)
        self.log_line.emit(line)
        if self.backend:
            new = self.backend.classify(line)
            if new == CastState.ERROR:
                self._last_error = self.backend.explain(line)
                self._set(new, self._last_error)
            elif new and new != self.state:
                self._set(new, line[:120])

    def _on_exit(self, code: int) -> None:
        self.log_line.emit(f"Engine exited (code {code})")
        backend, self.proc = self.backend, None
        self._stop_companions()
        if backend is not None and backend.wants_retry and not self._retried:
            self._retried = True
            self.log_line.emit(backend.prepare_retry())
            self._last_error = ""
            try:
                self._pending_argv = backend.build_command()
            except OSError as exc:
                self._finish(str(exc), failed=True)
                return
            self._set(CastState.STARTING, "Restarting receiver with a fresh device ID…")
            self._after_prepare("")
            return
        mux = getattr(backend, "mux_command", lambda: None)()
        if mux and shutil.which("ffmpeg"):
            subprocess.run(mux, capture_output=True)
            self.log_line.emit(f"Recording saved: {mux[-1]}")
        failed = self.state == CastState.ERROR or code not in (0, -2, -15, 130, 143)
        msg = self._last_error or f"Casting engine stopped (code {code}). Open Connection History → Copy log."
        self._finish(msg if failed else "", failed=failed)

    def _finish(self, detail: str, failed: bool) -> None:
        """Common end of every session: state, history, and handing the device back."""
        if failed:
            self._set(CastState.ERROR, detail)
        else:
            self._set(CastState.IDLE, "")
        result = "Failed" if failed else ("Completed" if self._streamed_s >= 1 else "No connection")
        try:
            history.append(history.SessionRecord(self._session_start, self.mode, self.device or "—",
                                                 int(self._streamed_s), result))
        except OSError:
            pass
        self.history_changed.emit()
        backend, self._needs_cleanup = self._needs_cleanup, None
        if backend and backend.cleanup_steps():
            self.cleaning_up = True
            QThreadPool.globalInstance().start(
                _StepRunner(backend.cleanup_steps(), backend, self.log_line, self._cleanup_done, best_effort=True))

    def _after_cleanup(self, error: str) -> None:
        self.cleaning_up = False
        self.log_line.emit("iPhone handed back to the system" if not error
                           else "iPhone handed back (some cleanup steps had nothing to undo)")


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
