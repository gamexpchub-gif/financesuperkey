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


def test_wired_commands(tmp_path):
    b = WiredBackend(WiredSettings(), udid="abc123")
    assert b.build_command() == ["qvh", "gstreamer", "--udid=abc123"]
    rec = WiredBackend(WiredSettings(output="record", record_dir=str(tmp_path)), udid="abc123")
    argv = rec.build_command()
    assert argv[:2] == ["qvh", "record"] and argv[2].endswith(".h264") and argv[3].endswith(".wav")
    assert rec.mux_command()[-1].endswith(".mp4")


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
