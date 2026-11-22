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
