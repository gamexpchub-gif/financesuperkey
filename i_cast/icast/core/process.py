"""Supervised child process with live log streaming and clean shutdown."""

from __future__ import annotations

import os
import signal
import subprocess
import threading
from typing import Callable, Sequence

LineCallback = Callable[[str], None]
ExitCallback = Callable[[int], None]


class ManagedProcess:
    """Runs one external engine (uxplay / qvh) in its own process group.

    Output lines are delivered to ``on_line`` from a background thread; the GUI
    layer is responsible for marshalling them onto its own thread.
    """

    def __init__(
        self,
        argv: Sequence[str],
        on_line: LineCallback | None = None,
        on_exit: ExitCallback | None = None,
        env: dict[str, str] | None = None,
    ) -> None:
        self.argv = list(argv)
        self.on_line = on_line or (lambda _l: None)
        self.on_exit = on_exit or (lambda _c: None)
        self.env = env
        self._proc: subprocess.Popen[str] | None = None
        self._reader: threading.Thread | None = None
        self._lock = threading.Lock()
        self._stopping = False

    @property
    def running(self) -> bool:
        return self._proc is not None and self._proc.poll() is None

    @property
    def pid(self) -> int | None:
        return self._proc.pid if self._proc else None

    def start(self) -> None:
        with self._lock:
            if self.running:
                raise RuntimeError("process already running")
            self._stopping = False
            env = dict(os.environ)
            if self.env:
                env.update(self.env)
            self._proc = subprocess.Popen(
                self.argv,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                stdin=subprocess.DEVNULL,
                text=True,
                bufsize=1,
                env=env,
                start_new_session=True,  # own process group -> clean kill of children
            )
            self._reader = threading.Thread(target=self._pump, daemon=True)
            self._reader.start()

    def _pump(self) -> None:
        proc = self._proc
        assert proc is not None and proc.stdout is not None
        for line in proc.stdout:
            line = line.rstrip()
            if line:
                self.on_line(line)
        code = proc.wait()
        self.on_exit(code)

    def stop(self, timeout: float = 4.0) -> None:
        with self._lock:
            proc = self._proc
            if proc is None or proc.poll() is not None:
                return
            self._stopping = True
            for sig, wait in ((signal.SIGINT, timeout / 2), (signal.SIGTERM, timeout / 2), (signal.SIGKILL, 2)):
                try:
                    os.killpg(proc.pid, sig)
                except ProcessLookupError:
                    break
                try:
                    proc.wait(timeout=wait)
                    break
                except subprocess.TimeoutExpired:
                    continue

    def wait(self, timeout: float | None = None) -> int | None:
        if self._proc is None:
            return None
        try:
            code = self._proc.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            return None
        if self._reader:
            self._reader.join(timeout=1)
        return code
