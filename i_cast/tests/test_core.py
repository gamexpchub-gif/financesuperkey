import sys
import time

from icast.backends.base import CastState
from icast.backends.wired import WiredBackend
from icast.backends.wireless import WirelessBackend
from icast.core.config import AppSettings, WiredSettings, WirelessSettings
from icast.core.process import ManagedProcess
from icast.cli import build_parser


def test_wireless_defaults_command():
    argv = WirelessBackend(WirelessSettings()).build_command()
    assert argv[:3] == ["uxplay", "-n", "iCast"]
    assert "-nh" in argv
    assert argv[argv.index("-s") + 1] == "1920x1080@60"
    assert argv[argv.index("-fps") + 1] == "60"
    assert ["-vsync", "no"] == argv[argv.index("-vsync"):argv.index("-vsync") + 2]
    assert "-pin" not in argv and "-fs" not in argv


def test_wireless_full_options():
    s = WirelessSettings(receiver_name="Living Room", fullscreen=True, pin_enabled=True, pin="1234",
                         rotation="R", flip="H", audio_sink="none", video_sink="glimagesink",
                         fixed_ports=True, base_port=7100, restrict_to_trusted=True,
                         trusted_devices=["AA:BB"], extra_args="-d")
    b = WirelessBackend(s)
    argv = b.build_command()
    for flag in (["-fs"], ["-pin", "1234"], ["-r", "R"], ["-f", "H"], ["-as", "0"],
                 ["-vs", "glimagesink"], ["-p", "7100"], ["-restrict"], ["-allow", "AA:BB"], ["-d"]):
        i = argv.index(flag[0])
        assert argv[i:i + len(flag)] == flag
    assert argv[2] == "Living Room"
    assert "7100/tcp" in b.firewall_ports()


def test_wired_window_and_record(tmp_path):
    b = WiredBackend(WiredSettings(output="window"), udid="abc123")
    assert b.build_command() == ["qvh", "gstreamer", "--udid=abc123"]
    assert b.companions() == []
    rec = WiredBackend(WiredSettings(output="record", record_dir=str(tmp_path)), udid="abc123")
    argv = rec.build_command()
    assert argv[:2] == ["qvh", "record"] and argv[2].endswith(".h264") and argv[3].endswith(".wav")
    assert rec.mux_command()[-1].endswith(".mp4")


def test_wired_fullscreen_low_latency(monkeypatch):
    import icast.backends.wired as wired
    monkeypatch.setattr(wired.shutil, "which", lambda name: f"/usr/bin/{name}")
    b = WiredBackend(WiredSettings(), udid="abc123", screen=(0, 0, 2560, 1440))
    argv = b.build_command()
    assert argv[:2] == ["qvh", "gstreamer"] and argv[-1] == "--udid=abc123"
    pipeline = argv[2].split("=", 1)[1]
    # phone H.264 goes untouched to the viewer: no decode / re-encode inside qvh
    assert "name=video_target" in pipeline and "name=audio_target" in pipeline
    assert f"tcpclientsink host=127.0.0.1 port={b.port} sync=false" in pipeline
    assert "avdec_h264" not in pipeline and "x264enc" not in pipeline
    assert "sync=false" in pipeline.split("audio_target", 1)[1]
    (viewer,) = b.companions()
    assert viewer[0] == "ffplay" and viewer[-1] == f"tcp://127.0.0.1:{b.port}?listen=1"
    for flag in (["-fflags", "nobuffer"], ["-flags", "low_delay"], ["-vf", "setpts=0"], ["-fs"], ["-an"],
                 ["-x", "2560"], ["-y", "1440"], ["-framedrop"]):
        i = viewer.index(flag[0])
        assert viewer[i:i + len(flag)] == flag
    smooth = WiredBackend(WiredSettings(latency_mode="smooth"), udid="x")
    smooth.build_command()
    assert "setpts=0" not in smooth.companions()[0]


def test_wired_audio_fallback(monkeypatch):
    import icast.backends.wired as wired
    monkeypatch.setattr(wired, "gst_has", lambda e: True)
    monkeypatch.setattr(wired, "pulse_server_running", lambda: True)
    assert "pulsesink sync=false buffer-time=40000" in WiredBackend(WiredSettings()).audio_pipeline()
    monkeypatch.setattr(wired, "pulse_server_running", lambda: False)
    assert "autoaudiosink sync=false" in WiredBackend(WiredSettings()).audio_pipeline()
    assert "fakesink" in WiredBackend(WiredSettings(audio=False)).audio_pipeline()


def test_settings_migrate_wired_to_fullscreen():
    old = AppSettings.from_dict({"wired": {"output": "window"}})  # v1 file
    assert old.wired.output == "fullscreen" and old.settings_version == 2
    kept = AppSettings.from_dict({"settings_version": 2, "wired": {"output": "window"}})
    assert kept.wired.output == "window"


def test_state_classification():
    w = WirelessBackend(WirelessSettings())
    assert w.classify("Initialized server socket(s)") == CastState.WAITING
    assert w.classify("connection request from iPhone (192.168.1.5)") == CastState.STREAMING
    assert w.classify("random noise") is None


def test_settings_roundtrip_and_validation(tmp_path):
    p = tmp_path / "s.json"
    s = AppSettings()
    s.mode = "wired"
    s.wireless.receiver_name = "Said's Mac"
    s.save(p)
    loaded = AppSettings.load(p)
    assert loaded.mode == "wired" and loaded.wireless.receiver_name == "Said's Mac"
    bad = AppSettings.from_dict({"mode": "x", "wireless": {"fps": 7, "pin": "12ab", "resolution": "1x1", "bogus": 1}})
    assert bad.mode == "wireless" and bad.wireless.fps == 60 and bad.wireless.pin == ""
    assert AppSettings.load(tmp_path / "missing.json").mode == "wireless"


def test_managed_process_stream_and_stop():
    lines, codes = [], []
    p = ManagedProcess([sys.executable, "-u", "-c", "import time;print('hello');time.sleep(30)"],
                       on_line=lines.append, on_exit=codes.append)
    p.start()
    deadline = time.time() + 5
    while not lines and time.time() < deadline:
        time.sleep(0.05)
    assert lines == ["hello"] and p.running
    p.stop()
    p.wait(5)
    assert not p.running and codes


def test_cli_parser():
    a = build_parser().parse_args(["wireless", "--name", "TV", "--fps", "30"])
    assert a.name == "TV" and a.fps == 30


def test_history_roundtrip(tmp_path):
    from icast.core import history
    p = tmp_path / "h.json"
    history.append(history.SessionRecord("2026-11-22 10:00", "Wireless", "AirPlay client", 75, "Completed"), p)
    history.append(history.SessionRecord("2026-11-22 11:00", "Wired", "Said's iPhone", 0, "No connection"), p)
    recs = history.load(p)
    assert [r.mode for r in recs] == ["Wired", "Wireless"]
    assert history.fmt_duration(75) == "1:15" and history.fmt_duration(3725) == "1:02:05"
    history.clear(p)
    assert history.load(p) == []


# Real qvh output (logrus JSON + printErrJSON), copied from qvh's source strings.
QVH_WAIT = '{"level":"info","msg":"Device \'00008120001A2B3C\' USB connection ready, waiting for ping..","time":"x"}'
QVH_PING = '{"level":"info","msg":"PING received","time":"x"}'
QVH_WARN = ('{"level":"warning","msg":"Failed sending control transfer for enabling hidden QT config. Seems like '
            'this happens sometimes but it still works usually: x","time":"x"}')
QVH_SOFT_ERR = '{"level":"error","msg":"Error parsing FEED packet: 00 x","time":"x"}'
QVH_FATAL = '{"error_message":"Error enabling QT config","original_error":"libusb: bad access [code -3]"}'
QVH_NODEV = '{"error_message":"no device found to use","original_error":"no iOS devices are connected to this host"}'


def test_wired_understands_real_qvh_logs():
    b = WiredBackend(WiredSettings(), udid="x")
    h = b.humanize
    assert h(QVH_WAIT).startswith("Device") and b.classify(h(QVH_WAIT)) == CastState.WAITING
    assert b.classify(h(QVH_PING)) == CastState.STREAMING
    assert b.classify(h("[viewer] Input #0, h264, from 'tcp://127.0.0.1:5000?listen=1':")) == CastState.STREAMING
    # warnings and per-packet errors are not fatal
    assert b.classify(h(QVH_WARN)) is None
    assert b.classify(h(QVH_SOFT_ERR)) is None
    # printErrJSON is fatal and gets a plain-language explanation
    assert b.classify(h(QVH_FATAL)) == CastState.ERROR
    assert "permission" in b.explain(h(QVH_FATAL)).lower()
    assert b.classify(h(QVH_NODEV)) == CastState.ERROR and "not found" in b.explain(h(QVH_NODEV))
    assert b.reset_command() == ["qvh", "deactivate", "--udid=x"]


def test_port_listening_detects_listener():
    import socket
    from icast.backends.wired import free_port, port_listening
    port = free_port()
    assert not port_listening(port)
    srv = socket.socket()
    srv.bind(("127.0.0.1", port))
    srv.listen(1)
    try:
        assert port_listening(port)
    finally:
        srv.close()


def test_wired_prepare_and_cleanup_steps(monkeypatch):
    import icast.backends.wired as wired
    b = WiredBackend(WiredSettings(), udid="00008120-001A2B3C")
    monkeypatch.setattr(wired.os.path, "exists", lambda p: p == wired.USB_HELPER)
    assert b.prepare_steps() == [["qvh", "activate", "--udid=00008120-001A2B3C", "-v"],
                                 ["sudo", "-n", wired.USB_HELPER, "prepare", "00008120-001A2B3C"]]
    # deactivate goes straight over USB (works even when usbmuxd lost the stuck iPhone), then restore
    assert b.cleanup_steps()[0] == ["env", "QVH_SKIP_PAIR_CHECK=1", "qvh", "deactivate", "--udid=00008120-001A2B3C"]
    assert b.cleanup_steps()[1][3] == "restore"
    assert b.engine_env() == {"QVH_SKIP_PAIR_CHECK": "1"}
    # fatal output from qvh activate, crash of activate, and missing sudo permission are all reported
    assert "permission" in b.step_failed(["qvh", "activate"], 0, QVH_FATAL).lower()
    assert "capture mode" in b.step_failed(["qvh", "activate"], 2, "boom")
    assert "install.sh" in b.step_failed(["sudo", "-n", wired.USB_HELPER, "prepare", "x"], 1,
                                         "sudo: a password is required")
    assert b.step_failed(["qvh", "activate"], 0, '{"level":"info","msg":"ok"}') is None
    monkeypatch.setattr(wired.os.path, "exists", lambda p: False)
    assert len(b.prepare_steps()) == 1  # helper not installed: activate only


def test_usb_helper_releases_only_that_iphone(tmp_path):
    import subprocess
    from pathlib import Path
    helper = Path(__file__).resolve().parents[1] / "scripts" / "icast-usb"
    d = tmp_path / "bus/usb/devices"
    for name in ("1-2", "1-2:1.1", "1-2:1.2", "3-1", "3-1:1.0"):
        (d / name).mkdir(parents=True)
    (d / "1-2/idVendor").write_text("05ac\n"); (d / "1-2/serial").write_text("00008120001A2B3C\n")
    (d / "3-1/idVendor").write_text("046d\n"); (d / "3-1/serial").write_text("OTHER\n")
    for drv in ("usbfs", "ipheth", "usbhid"):
        (tmp_path / "drivers" / drv).mkdir(parents=True)
        (tmp_path / "drivers" / drv / "unbind").write_text("")
    (d / "1-2:1.1/driver").symlink_to(tmp_path / "drivers/usbfs")
    (d / "1-2:1.2/driver").symlink_to(tmp_path / "drivers/ipheth")
    (d / "3-1:1.0/driver").symlink_to(tmp_path / "drivers/usbhid")
    env = {"ICAST_SYSFS": str(tmp_path), "PATH": "/usr/bin:/bin"}
    r = subprocess.run(["bash", str(helper), "prepare", "00008120-001A2B3C"], env=env, capture_output=True, text=True)
    assert r.returncode == 0, r.stderr
    assert (tmp_path / "drivers/ipheth/unbind").read_text().strip() == "1-2:1.2"
    assert (tmp_path / "drivers/usbfs/unbind").read_text().strip() == "1-2:1.1"
    assert (tmp_path / "drivers/usbhid/unbind").read_text() == ""          # other devices untouched
    bad = subprocess.run(["bash", str(helper), "prepare", "x; rm -rf /"], env=env, capture_output=True, text=True)
    assert bad.returncode == 2


UXPLAY_CONFLICT = "*** ERROR: DNSServiceRegister call returned kDNSServiceErr_NameConflict"


def test_wireless_cleans_stale_receiver_and_retries_on_name_conflict():
    import os
    b = WirelessBackend(WirelessSettings())
    assert b.prepare_steps()[0] == ["pkill", "-u", str(os.getuid()), "-x", "uxplay"]
    assert b.classify(UXPLAY_CONFLICT) == CastState.ERROR and b.wants_retry
    assert "fresh device ID" in b.explain(UXPLAY_CONFLICT)
    b.prepare_retry()
    argv = b.build_command()
    assert "-m" in argv and "-nh" not in argv and not b.wants_retry
    b.classify(UXPLAY_CONFLICT)
    assert not b.wants_retry          # only one automatic retry
    assert "avahi" in b.explain("*** ERROR: DNSServiceRegister call returned kDNSServiceErr_Unknown").lower()


def test_wired_preflight_requires_helper_and_patched_qvh(monkeypatch):
    import icast.backends.wired as wired
    monkeypatch.setattr(wired.shutil, "which", lambda n: f"/usr/bin/{n}")
    monkeypatch.setattr(wired, "usbmuxd_running", lambda: True)
    monkeypatch.setattr(wired.os.path, "exists", lambda p: False)
    assert "install.sh" in WiredBackend(WiredSettings(), udid="x").preflight()
    monkeypatch.setattr(wired.os.path, "exists", lambda p: p == wired.USB_HELPER)      # helper but old qvh
    assert "install.sh" in WiredBackend(WiredSettings(), udid="x").preflight()
    monkeypatch.setattr(wired.os.path, "exists", lambda p: p in (wired.USB_HELPER, wired.QVH_PATCH_MARKER))
    assert WiredBackend(WiredSettings(), udid="x").preflight() is None


def test_cleanup_runs_every_step_even_after_a_failure():
    # Regression: a failing `qvh deactivate` used to skip the usbmuxd restore -> no iPhones listed.
    from icast.backends.base import run_steps
    b = WiredBackend(WiredSettings(), udid="x")
    fatal = 'echo \'{"error_message":"device not found, is it still connected?","original_error":"usbmuxd"}\''
    ran, logs = [], []
    steps = [["sh", "-c", fatal], ["sh", "-c", "echo second"]]
    err = run_steps(steps, b, logs.append, best_effort=True)
    assert "second" in logs and err                       # kept going, still reports the problem
    logs.clear()
    assert run_steps(steps, b, logs.append) and "second" not in logs   # prepare stops at the first error


def test_hung_activate_is_killed_with_clear_advice(monkeypatch):
    import time
    from icast.backends.base import run_steps
    b = WiredBackend(WiredSettings(), udid="x")
    monkeypatch.setattr(b, "step_timeout", lambda argv: 0.5)
    logs = []
    t0 = time.monotonic()
    err = run_steps([["sh", "-c", "echo waiting for device; sleep 30", "activate"]], b, logs.append)
    assert time.monotonic() - t0 < 3
    assert "Unplug the iPhone" in err and "waiting for device" in logs   # output streamed live


def test_stop_cancels_running_step_immediately():
    import threading, time
    from icast.backends.base import run_steps
    b = WiredBackend(WiredSettings(), udid="x")
    cancel = threading.Event()
    threading.Timer(0.3, cancel.set).start()
    t0 = time.monotonic()
    assert run_steps([["sleep", "30"], ["echo", "never"]], b, lambda _l: None, cancel=cancel) == "Cancelled"
    assert time.monotonic() - t0 < 3
