# iCast — iPhone Screen Cast

Mirror your iPhone screen to a Linux computer in two ways:

| Mode | How it connects | Engine | Best for |
|---|---|---|---|
| **Wireless · AirPlay** | Same Wi‑Fi. On iPhone: *Control Center → Screen Mirroring → iCast* | [UxPlay](https://github.com/FDH2/UxPlay) | No cable, presentations, TV-style use |
| **Wired · USB** | Lightning / USB‑C cable | [qvh](https://github.com/danielpaulus/quicktime_video_hack) + libimobiledevice | Lowest delay, best picture, no Wi‑Fi needed |

iCast is the control app: a dark desktop UI plus a CLI. It finds your devices,
builds the right engine command, watches the engine's state
(Starting → Waiting → **Live**), shows a live timer, records over USB to MP4,
and runs health checks.

## Install (Ubuntu / Debian / Mint)

```bash
cd i_cast
./scripts/install.sh
```

The script installs UxPlay, Avahi, usbmuxd, libimobiledevice, GStreamer and ffmpeg, builds `qvh`,
adds a udev rule for iPhone USB access, opens firewall ports if `ufw` is on, and installs the `icast`
command and its launcher.

Check everything is ready:

```bash
icast doctor
```

## Use

**Desktop app:** run `icast` (or open *iCast* from the app menu).

**Command line:**

```bash
icast wireless --name "Living Room" --resolution 1920x1080 --fps 60 --pin 1234
icast wired                 # live window over USB
icast wired --record        # record to ~/Videos/iCast/*.mp4
icast devices               # list USB iPhones
icast pair <udid>           # show the "Trust This Computer" prompt
```

### বাংলায় দ্রুত গাইড
- **Wireless:** iPhone আর কম্পিউটার একই Wi‑Fi তে রাখুন → iCast এ *Start casting* চাপুন → iPhone এ Control Center → Screen Mirroring → **iCast** সিলেক্ট করুন।
- **Wired:** ক্যাবল দিয়ে iPhone লাগান → ফোন unlock করে **Trust** চাপুন → *Wired · USB* ট্যাবে ফোন দেখাবে → *Start casting*।

## Troubleshooting

| Problem | Fix |
|---|---|
| iCast doesn't appear in Screen Mirroring | `sudo systemctl enable --now avahi-daemon`; same Wi‑Fi; disable "client isolation" on the router; turn on *Fixed ports* and allow 7100‑7102 + 5353/udp |
| Choppy wireless video | Use 5 GHz Wi‑Fi, try 1280×720 or 30 fps, keep *Low‑latency mode* on |
| iPhone not listed under USB | Unlock the phone, tap *Trust*, run `sudo systemctl restart usbmuxd`, try another cable |
| "Not trusted" | Click *Trust this computer* in iCast and accept on the iPhone |
| No sound | Set *Audio output* to `pulsesink` or `pipewiresink` |

Settings are saved in `~/.config/icast/settings.json`.

## Development

```bash
python3 -m venv .venv && . .venv/bin/activate
pip install -e ".[dev]"
pytest
python -m icast
```

Code layout:

```
icast/
  core/       config (JSON settings), deps (doctor), devices (USB iPhones), process (engine supervisor)
  backends/   wireless.py (UxPlay), wired.py (qvh)
  gui/        theme, controller (Qt signals/threads), main_window
  cli.py      command-line interface
```

## Licensing note
iCast is MIT. It launches UxPlay (GPL‑3.0) and qvh (MIT) as separate programs and does not bundle them.
AirPlay and iPhone are trademarks of Apple Inc.; iCast is not affiliated with Apple.
