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
import time

from icast import __app_name__, __version__
from icast.backends.base import CastState, run_steps
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
    blocker = backend.preflight()
    if blocker:
        print(_c("bad", f"✘ {blocker}"))
        return 1
    argv = backend.build_command()
    print(_c("b", f"▶ {label}"), _c("dim", " ".join(argv)))
    err = run_steps(backend.prepare_steps(), backend, lambda l: print(_c("dim", f"  {l}")))
    if err:
        print(_c("bad", f"✘ {err}"))
        run_steps(backend.cleanup_steps(), backend, lambda l: print(_c("dim", f"  {l}")))
        return 1
    try:
        code = _run_engine(backend, argv)
        if backend.wants_retry:
            print(_c("b", "↻ " + backend.prepare_retry()))
            code = _run_engine(backend, backend.build_command())
        return code
    finally:
        run_steps(backend.cleanup_steps(), backend, lambda l: print(_c("dim", f"  {l}")))


def _run_engine(backend, argv: list[str]) -> int:
    state = {"v": CastState.STARTING}

    def on_line(line: str) -> None:
        line = backend.humanize(line)
        new = backend.classify(line)
        if new and new != state["v"]:
            state["v"] = new
            print(_c("b", f"[{new.value.upper()}]"))
            if new == CastState.ERROR:
                print(_c("bad", "  → " + backend.explain(line)))
        print(_c("dim", f"  {line}"))

    companions = []
    for cargv in backend.companions():
        print(_c("dim", "  viewer: " + " ".join(cargv)))
        comp = ManagedProcess(cargv, on_line=lambda l: on_line(f"[viewer] {l}"))
        comp.start()
        companions.append(comp)
    if companions:
        deadline = time.monotonic() + backend.companion_timeout_s
        while not backend.companions_ready() and time.monotonic() < deadline:
            if any(not c.running for c in companions):
                print("Viewer (ffplay) closed before the stream started.")
                return 1
            time.sleep(0.05)

    proc = ManagedProcess(argv, on_line=on_line)
    for comp in companions:  # closing the viewer ends the session
        comp.on_exit = lambda _c: proc.stop()
    proc.start()
    signal.signal(signal.SIGTERM, lambda *_: proc.stop())
    try:
        code = proc.wait()
    except KeyboardInterrupt:
        print("\nStopping…")
        proc.stop()
        code = proc.wait(timeout=5) or 0
    finally:
        for comp in companions:
            comp.on_exit = lambda _c: None
            comp.stop(timeout=2)
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
    elif args.window:
        settings.wired.output = "window"
    else:
        settings.wired.output = "fullscreen"
    if args.smooth:
        settings.wired.latency_mode = "smooth"
    if args.no_audio:
        settings.wired.audio = False
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


def cmd_reset_usb(args) -> int:
    backend = WiredBackend(AppSettings.load().wired, udid=args.udid or "")
    err = run_steps(backend.cleanup_steps() or [backend.reset_command()], backend, print)
    print(f"Reset failed: {err}" if err else "Done. Unplug and re-plug the iPhone, unlock it, then run: icast wired")
    return 1 if err else 0


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
    mode = pu.add_mutually_exclusive_group()
    mode.add_argument("--record", action="store_true", help="record to file instead of showing the screen")
    mode.add_argument("--window", action="store_true", help="classic small window instead of fullscreen")
    pu.add_argument("--smooth", action="store_true", help="steadier pacing for slower PCs (~0.1 s more delay)")
    pu.add_argument("--no-audio", action="store_true", help="picture only")
    pu.set_defaults(fn=cmd_wired)

    pr = sub.add_parser("reset-usb", help="take the iPhone out of a stuck USB capture mode")
    pr.add_argument("--udid")
    pr.set_defaults(fn=cmd_reset_usb)
    return p


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    fn = getattr(args, "fn", cmd_gui)
    return fn(args)
