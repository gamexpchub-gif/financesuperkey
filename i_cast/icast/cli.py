"""iCast command-line interface.

    icast                 launch the desktop app
    icast wireless        start AirPlay receiver (headless)
    icast wired           start USB mirroring (headless)
    icast devices         list USB-connected iPhones
    icast pair <udid>     trust this computer on the iPhone
    icast doctor          check system dependencies
"""

from __future__ import annotations

import argparse
import shutil
import signal
import sys

from icast import __app_name__, __version__
from icast.backends.base import CastState
from icast.backends.wired import WiredBackend
from icast.backends.wireless import WirelessBackend
from icast.core import deps, devices
from icast.core.config import FRAME_RATES, RESOLUTIONS, AppSettings
from icast.core.process import ManagedProcess

_C = {"ok": "\033[32m", "bad": "\033[31m", "dim": "\033[2m", "b": "\033[1m", "x": "\033[0m"}


def _c(key: str, text: str) -> str:
    return f"{_C[key]}{text}{_C['x']}" if sys.stdout.isatty() else text


def cmd_doctor(_args) -> int:
    print(_c("b", f"{__app_name__} {__version__} — system check\n"))
    all_ok = True
    for st in deps.check_all():
        mark = _c("ok", "✔") if st.ok else _c("bad", "✘")
        where = st.path or _c("dim", f"missing → install: {st.dep.apt_package}")
        print(f"  {mark} {st.dep.binary:<16} {st.dep.purpose:<40} {where}")
        all_ok &= st.ok
    for svc in ("avahi-daemon", "usbmuxd"):
        state = deps.service_active(svc)
        if state is not None:
            print(f"  {'•'} service {svc:<10} {_c('ok', 'active') if state else _c('bad', 'inactive')}")
    print()
    print("All good. Ready to cast." if all_ok else "Run scripts/install.sh to install missing components.")
    return 0 if all_ok else 1


def cmd_devices(_args) -> int:
    if not shutil.which("idevice_id"):
        print("libimobiledevice is not installed (sudo apt install libimobiledevice-utils)")
        return 1
    phones = devices.discover()
    if not phones:
        print("No iPhone detected over USB. Connect the cable, unlock the phone and tap 'Trust'.")
        return 1
    for p in phones:
        trust = {True: "trusted", False: "NOT trusted", None: "unknown"}[p.paired]
        print(f"{p.udid}  {p.label}  [{trust}]")
    return 0


def cmd_pair(args) -> int:
    ok, msg = devices.pair(args.udid)
    print(msg)
    return 0 if ok else 1


def _run_backend(backend, label: str) -> int:
    missing = shutil.which(backend.binary) is None
    if missing:
        print(f"'{backend.binary}' not found. Run: icast doctor")
        return 127
    argv = backend.build_command()
    print(_c("b", f"▶ {label}"), _c("dim", " ".join(argv)))
    state = {"v": CastState.STARTING}

    def on_line(line: str) -> None:
        new = backend.classify(line)
        if new and new != state["v"]:
            state["v"] = new
            print(_c("b", f"[{new.value.upper()}]"))
        print(_c("dim", f"  {line}"))

    proc = ManagedProcess(argv, on_line=on_line)
    proc.start()
    signal.signal(signal.SIGTERM, lambda *_: proc.stop())
    try:
        code = proc.wait()
    except KeyboardInterrupt:
        print("\nStopping…")
        proc.stop()
        code = proc.wait(timeout=5) or 0
    return code or 0


def cmd_wireless(args) -> int:
    settings = AppSettings.load()
    w = settings.wireless
    if args.name:
        w.receiver_name = args.name
    if args.resolution:
        w.resolution = args.resolution
    if args.fps:
        w.fps = args.fps
    if args.fullscreen:
        w.fullscreen = True
    if args.pin is not None:
        w.pin_enabled, w.pin = True, args.pin
    settings.validate()
    print(f"On your iPhone: Control Center → Screen Mirroring → “{w.receiver_name}”")
    return _run_backend(WirelessBackend(w), "Wireless AirPlay receiver")


def cmd_wired(args) -> int:
    settings = AppSettings.load()
    if args.record:
        settings.wired.output = "record"
    udid = args.udid
    if not udid:
        found = devices.list_udids()
        if not found:
            print("No iPhone detected over USB. Connect, unlock and tap 'Trust'.")
            return 1
        udid = found[0]
    backend = WiredBackend(settings.wired, udid=udid)
    code = _run_backend(backend, f"Wired USB mirroring ({udid})")
    mux = backend.mux_command()
    if mux and shutil.which("ffmpeg"):
        print("Saving MP4…")
        import subprocess

        subprocess.run(mux, capture_output=True)
        print(f"Saved: {mux[-1]}")
    return code


def cmd_gui(_args) -> int:
    try:
        from icast.gui.app import run
    except ImportError as exc:
        print(f"GUI unavailable ({exc}). Install with: pip install PySide6")
        return 1
    return run()


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="icast", description="iCast — iPhone screen casting (USB + AirPlay)")
    p.add_argument("--version", action="version", version=f"{__app_name__} {__version__}")
    sub = p.add_subparsers(dest="cmd")
    sub.add_parser("gui", help="launch the desktop app (default)").set_defaults(fn=cmd_gui)
    sub.add_parser("doctor", help="check system dependencies").set_defaults(fn=cmd_doctor)
    sub.add_parser("devices", help="list USB iPhones").set_defaults(fn=cmd_devices)
    pp = sub.add_parser("pair", help="pair/trust an iPhone")
    pp.add_argument("udid")
    pp.set_defaults(fn=cmd_pair)

    pw = sub.add_parser("wireless", help="AirPlay receiver")
    pw.add_argument("--name")
    pw.add_argument("--resolution", choices=RESOLUTIONS)
    pw.add_argument("--fps", type=int, choices=FRAME_RATES)
    pw.add_argument("--fullscreen", action="store_true")
    pw.add_argument("--pin", help="4-digit PIN required to connect")
    pw.set_defaults(fn=cmd_wireless)

    pu = sub.add_parser("wired", help="USB mirroring")
    pu.add_argument("--udid")
    pu.add_argument("--record", action="store_true", help="record to file instead of showing a window")
    pu.set_defaults(fn=cmd_wired)
    return p


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    fn = getattr(args, "fn", cmd_gui)
    return fn(args)
