"""iCast main window — frameless, glassmorphism UI."""

from __future__ import annotations

import os
import time

from PySide6.QtCore import QPointF, QRectF, QRunnable, Qt, QThreadPool, QTimer, Signal
from PySide6.QtGui import QAction, QColor, QFont, QPainter, QPainterPath, QPen
from PySide6.QtWidgets import (
    QAbstractItemView, QApplication, QButtonGroup, QCheckBox, QComboBox, QFileDialog, QGridLayout, QHBoxLayout, QHeaderView,
    QLabel, QLineEdit, QListWidget, QListWidgetItem, QMainWindow, QMenu, QMessageBox, QPlainTextEdit, QScrollArea,
    QSizeGrip, QSpinBox, QStackedWidget, QSystemTrayIcon, QTableWidget, QTableWidgetItem, QVBoxLayout, QWidget,
)

from icast import __app_name__, __version__
from icast.backends.base import CastState, run_steps
from icast.backends.wired import USB_HELPER, WiredBackend, apple_device_connected, recover_usb
from icast.backends.wireless import WirelessBackend
from icast.core import deps, devices, history
from icast.core.config import config_dir, AUDIO_SINKS, FLIPS, FRAME_RATES, RESOLUTIONS, ROTATIONS, VIDEO_SINKS, AppSettings
from icast.gui import theme
from icast.gui.controller import CastController, DeviceWatcher, safe_emit
from icast.gui.icons import icon_pixmap
from icast.gui.widgets import (
    AA, FeatureChip, GlassCard, GradientButton, GradientTitle, HeroArt, IconButton, LogoMark, ModeCard, NavButton,
    OutlineButton, PillButton, StatusPill, VSeparator, WindowButton, card_layout, logo_icon, soft_glow, text_label,
)

PAGES = ("Home", "Wireless Cast", "Wired Cast", "Connection History", "Settings", "About")
NAV_ICONS = ("home", "wifi", "cable", "history", "settings", "info")
RES_LABEL = {"1280x720": "720p", "1920x1080": "1080p", "2560x1440": "1440p", "3840x2160": "4K"}
HD_CHIP = {"1280x720": "HD\n720P", "1920x1080": "HD\n1080P", "2560x1440": "QHD\n1440P", "3840x2160": "Ultra HD\n4K"}

HELP_TEXT = """<h3>Quick help</h3>
<p><b>Wireless:</b> keep the iPhone and this computer on the same Wi‑Fi, press <i>Start Wireless</i>, then on the
iPhone open <i>Control Center → Screen Mirroring</i> and choose your iCast receiver.</p>
<p><b>Wired:</b> connect the iPhone with a USB cable, unlock it and tap <i>Trust</i>, then press <i>Start Wired</i>.</p>
<p><b>iCast doesn't show on the iPhone?</b> Make sure Avahi is running (<code>sudo systemctl enable --now avahi-daemon</code>),
the router doesn't isolate Wi‑Fi clients, and the firewall allows 5353/udp + ports 7100‑7102.</p>
<p><b>USB iPhone not detected?</b> Unlock it, tap Trust, try another cable, and run <code>sudo systemctl restart usbmuxd</code>.</p>
<p>The <b>About</b> page shows a full system check.</p>"""


def field(text: str) -> QLabel:
    lbl = QLabel(text)
    lbl.setObjectName("field")
    return lbl


def combo(items, current) -> QComboBox:
    c = QComboBox()
    c.addItems([str(i) for i in items])
    c.setCurrentText(str(current))
    c.setCursor(Qt.PointingHandCursor)
    return c


def form_grid() -> QGridLayout:
    g = QGridLayout()
    g.setHorizontalSpacing(18)
    g.setVerticalSpacing(12)
    g.setColumnStretch(1, 1)
    return g


def page_header(title: str, subtitle: str) -> QVBoxLayout:
    lay = QVBoxLayout()
    lay.setSpacing(4)
    t = QLabel(title)
    t.setObjectName("h1")
    s = QLabel(subtitle)
    s.setObjectName("sub")
    lay.addWidget(t)
    lay.addWidget(s)
    return lay


def scroll(widget: QWidget) -> QScrollArea:
    area = QScrollArea()
    area.setWidget(widget)
    area.setWidgetResizable(True)
    area.setFrameShape(QScrollArea.NoFrame)
    area.setHorizontalScrollBarPolicy(Qt.ScrollBarAlwaysOff)
    return area


class Backdrop(QWidget):
    """Deep navy gradient with neon glows; rounded when not maximised."""

    def paintEvent(self, _e) -> None:
        p = QPainter(self)
        p.setRenderHint(AA)
        W, H = self.width(), self.height()
        maxed = self.window().isMaximized() or self.window().isFullScreen()
        rad = 0 if maxed else 16
        path = QPainterPath()
        path.addRoundedRect(QRectF(self.rect()).adjusted(0.5, 0.5, -0.5, -0.5), rad, rad)
        p.setClipPath(path)
        p.fillRect(self.rect(), QColor(theme.NAVY))
        soft_glow(p, QPointF(W * 0.72, H * 0.42), W * 0.45, theme.VIOLET, 95)
        soft_glow(p, QPointF(W * 0.95, H * 0.12), W * 0.35, theme.BLUE, 90)
        soft_glow(p, QPointF(W * 0.25, H * 0.05), W * 0.30, "#1A2F9A", 70)
        soft_glow(p, QPointF(W * 0.55, H * 1.0), W * 0.40, "#3A1590", 90)
        p.setClipping(False)
        p.setPen(QPen(QColor(255, 255, 255, 30), 1))
        p.setBrush(Qt.NoBrush)
        p.drawPath(path)


class TitleBar(QWidget):
    def __init__(self, win: QMainWindow) -> None:
        super().__init__()
        self.win = win
        self.setFixedHeight(48)
        lay = QHBoxLayout(self)
        lay.setContentsMargins(18, 6, 10, 0)
        lay.setSpacing(10)
        lay.addWidget(LogoMark(28, glow=False))
        lay.addWidget(text_label(__app_name__, 18, QFont.DemiBold, wrap=False))
        lay.addStretch(1)
        self.b_min, self.b_max, self.b_close = WindowButton("min"), WindowButton("max"), WindowButton("close", True)
        self.b_min.clicked.connect(win.showMinimized)
        self.b_max.clicked.connect(self.toggle_max)
        self.b_close.clicked.connect(win.close)
        for b in (self.b_min, self.b_max, self.b_close):
            lay.addWidget(b)

    def toggle_max(self) -> None:
        if self.win.isMaximized():
            self.win.showNormal()
        else:
            self.win.showMaximized()

    def sync(self) -> None:
        self.b_max.icon_name = "restore" if self.win.isMaximized() else "max"
        self.b_max.update()

    def mousePressEvent(self, e) -> None:  # noqa: N802
        if e.button() == Qt.LeftButton and self.win.windowHandle():
            self.win.windowHandle().startSystemMove()

    def mouseDoubleClickEvent(self, _e) -> None:  # noqa: N802
        self.toggle_max()


class Sidebar(GlassCard):
    def __init__(self) -> None:
        super().__init__(radius=22, fill=(8, 11, 36, 170), border=(255, 255, 255, 22))
        self.setFixedWidth(272)
        lay = QVBoxLayout(self)
        lay.setContentsMargins(16, 4, 16, 18)
        lay.setSpacing(6)
        logo = LogoMark(104, glow_pad=0.16)
        lay.addWidget(logo, 0, Qt.AlignHCenter)
        name = text_label(__app_name__, 44, QFont.Bold, wrap=False)
        name.setAlignment(Qt.AlignHCenter)
        tag = text_label("Cast. Connect. Share.", 17, QFont.Normal, theme.TEXT_2, wrap=False)
        tag.setAlignment(Qt.AlignHCenter)
        lay.addWidget(name)
        lay.addWidget(tag)
        lay.addSpacing(22)

        self.group = QButtonGroup(self)
        self.group.setExclusive(True)
        for i, (text, ic) in enumerate(zip(PAGES, NAV_ICONS)):
            b = NavButton(text, ic)
            self.group.addButton(b, i)
            lay.addWidget(b)
        lay.addStretch(1)

        promo = GlassCard(radius=18, fill=(255, 255, 255, 10), border=(255, 255, 255, 30))
        pl = QVBoxLayout(promo)
        pl.setContentsMargins(18, 16, 18, 16)
        pl.setSpacing(12)
        row = QHBoxLayout()
        row.setSpacing(12)
        crown = QLabel()
        crown.setPixmap(icon_pixmap("crown", 42, theme.GOLD))
        txt = QVBoxLayout()
        txt.setSpacing(0)
        txt.addWidget(text_label("Go Premium", 19, QFont.Bold, theme.GOLD, wrap=False))
        txt.addWidget(text_label("Unlock more features", 14, QFont.Normal, theme.TEXT_2, wrap=False))
        row.addWidget(crown)
        row.addLayout(txt, 1)
        pl.addLayout(row)
        self.upgrade = GradientButton("Upgrade Now", (theme.CYAN, theme.BLUE, theme.VIOLET, theme.MAGENTA),
                                      height=50, font_px=18, glow=False)
        pl.addWidget(self.upgrade)
        lay.addWidget(promo)
        lay.addSpacing(14)
        sep = QWidget()
        sep.setFixedHeight(1)
        sep.setAttribute(Qt.WA_StyledBackground, True)
        sep.setStyleSheet("background: rgba(255,255,255,0.14);")
        lay.addWidget(sep)
        lay.addSpacing(10)
        ver = text_label(f"Version {__version__}", 14, QFont.Normal, theme.TEXT_2, wrap=False)
        ver.setAlignment(Qt.AlignHCenter)
        lay.addWidget(ver)
        made = QHBoxLayout()
        made.addStretch(1)
        ic = QLabel()
        ic.setPixmap(icon_pixmap("phone", 18, "#FFFFFF"))
        made.addWidget(ic)
        made.addWidget(text_label("Made for iPhone", 14, QFont.Normal, theme.TEXT_2, wrap=False))
        made.addStretch(1)
        lay.addLayout(made)


class StatusStrip(GlassCard):
    def __init__(self) -> None:
        super().__init__(radius=20, fill=(12, 16, 48, 160))
        self.setFixedHeight(80)
        lay = QHBoxLayout(self)
        lay.setContentsMargins(26, 0, 16, 0)
        lay.setSpacing(16)
        self.s_icon, self.s_text = QLabel(), text_label("", 18, QFont.Normal, wrap=False)
        self.r_icon, self.r_text = QLabel(), text_label("", 18, QFont.Normal, wrap=False)
        self.c_icon, self.c_text = QLabel(), text_label("", 18, QFont.Normal, wrap=False)
        self.r_icon.setPixmap(icon_pixmap("hd", 32, "#3FA8FF", 1.8))
        for ic, tx in ((self.s_icon, self.s_text), (self.r_icon, self.r_text), (self.c_icon, self.c_text)):
            lay.addWidget(ic)
            lay.addWidget(tx)
            if tx is not self.c_text:
                lay.addSpacing(18)
                lay.addWidget(VSeparator())
                lay.addSpacing(18)
        lay.addStretch(1)
        self.scan = OutlineButton("Scan Devices", "refresh", height=54, font_px=18)
        lay.addWidget(self.scan)

    def set_status(self, text: str, ok: bool, warn: bool = False) -> None:
        color = theme.GREEN if ok else (theme.AMBER if warn else theme.RED)
        self.s_icon.setPixmap(icon_pixmap("check" if ok else "alert", 32, color))
        short = text if len(text) <= 58 else text[:56].rstrip() + "…"
        self.s_text.setText(short)
        self.s_text.setToolTip(text)
        self.setToolTip(text if short != text else "")

    def set_resolution(self, text: str) -> None:
        self.r_text.setText(f"Resolution: {text}")

    def set_connection(self, text: str, color: str) -> None:
        self.c_icon.setPixmap(icon_pixmap("signal", 30, color))
        self.c_text.setText(f"Connection: {text}")


class _Recover(QRunnable):
    """Self-heal: bring back usbmuxd / an iPhone left stuck by an earlier session."""

    def __init__(self, log, done=None, force: bool = False) -> None:
        super().__init__()
        self.log, self.done, self.force = log, done, force

    def run(self) -> None:
        msg = recover_usb(force=self.force)
        if msg:
            safe_emit(self.log, msg)
        if self.done is not None:
            safe_emit(self.done)


class MainWindow(QMainWindow):
    recovered = Signal()

    def __init__(self) -> None:
        super().__init__()
        self._auto_start_wired = False
        self.settings = AppSettings.load()
        self.ctrl = CastController()
        self.watcher = DeviceWatcher()
        self.phones: list[devices.IPhone] = []
        self.events: list[str] = []

        self.setWindowTitle(f"{__app_name__} — Premium Screen Casting")
        self.setWindowIcon(logo_icon())
        self.setWindowFlags(Qt.FramelessWindowHint | Qt.Window)
        self.setAttribute(Qt.WA_TranslucentBackground)
        self.resize(1672, 941)
        self.setMinimumSize(1280, 800)

        root = Backdrop()
        self.setCentralWidget(root)
        outer = QVBoxLayout(root)
        outer.setContentsMargins(0, 0, 0, 0)
        outer.setSpacing(0)
        self.titlebar = TitleBar(self)
        outer.addWidget(self.titlebar)

        body = QHBoxLayout()
        body.setContentsMargins(14, 4, 26, 6)
        body.setSpacing(30)
        self.sidebar = Sidebar()
        body.addWidget(self.sidebar)

        content = QVBoxLayout()
        content.setSpacing(18)
        content.addLayout(self._build_topbar())
        self.stack = QStackedWidget()
        for builder in (self._page_home, self._page_wireless, self._page_wired, self._page_history,
                        self._page_settings, self._page_about):
            self.stack.addWidget(builder())
        content.addWidget(self.stack, 1)
        self.strip = StatusStrip()
        content.addWidget(self.strip)
        body.addLayout(content, 1)
        outer.addLayout(body, 1)

        grip_row = QHBoxLayout()
        grip_row.setContentsMargins(0, 0, 4, 4)
        grip_row.addStretch(1)
        grip_row.addWidget(QSizeGrip(root), 0, Qt.AlignBottom | Qt.AlignRight)
        outer.addLayout(grip_row)

        self._build_tray()
        self.sidebar.group.idClicked.connect(self.go)
        self.sidebar.upgrade.clicked.connect(self._premium)
        self.strip.scan.clicked.connect(self._manual_scan)
        self.ctrl.state_changed.connect(self._on_state)
        self.ctrl.log_line.connect(self._log)
        self.ctrl.history_changed.connect(self._refresh_history)
        self.watcher.found.connect(self._on_devices)

        self._clock = QTimer(self, interval=1000, timeout=self._tick)
        self._clock.start()
        self._scan_timer = QTimer(self, interval=3000, timeout=self.watcher.scan)
        self._scan_timer.start()
        self.recovered.connect(self._after_recover)
        QThreadPool.globalInstance().start(_Recover(self.ctrl.log_line, self.recovered))
        self.go(0)
        self._on_state("idle", "")

    # =============================================================== top bar
    def _build_topbar(self):
        row = QHBoxLayout()
        row.setSpacing(14)
        self.pill = StatusPill()
        row.addWidget(self.pill)
        row.addStretch(1)
        self.device_btn = PillButton("iPhone Only")
        self.device_btn.clicked.connect(self._device_menu)
        self.bell = IconButton("bell", tooltip="Notifications")
        self.bell.clicked.connect(self._show_events)
        self.help_btn = IconButton("headset", tooltip="Help & support")
        self.help_btn.clicked.connect(lambda: QMessageBox.information(self, "iCast Help", HELP_TEXT))
        row.addWidget(self.device_btn)
        row.addWidget(self.bell)
        row.addWidget(self.help_btn)
        return row

    # ================================================================= pages
    def _page_home(self) -> QWidget:
        page = QWidget()
        lay = QVBoxLayout(page)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(22)

        hero = QHBoxLayout()
        hero.setSpacing(0)
        left = QVBoxLayout()
        left.setSpacing(10)
        left.addWidget(GradientTitle("iCast", 118))
        headline = QLabel(f"Premium Screen <span style='color:#2FC6FF'>Casting</span> "
                          f"<span style='color:#B35BFF'>Software</span>")
        headline.setFont(theme.font(40, QFont.Bold))
        left.addWidget(headline)
        left.addWidget(text_label("Cast your iPhone screen wirelessly or with cable\nto your PC, with ultra HD quality.",
                                  24, QFont.Normal, theme.TEXT_2))
        left.addSpacing(8)
        chips = QHBoxLayout()
        chips.setSpacing(14)
        self.chip_hd = FeatureChip("hd", HD_CHIP[self.settings.wireless.resolution], "#3FA8FF")
        chips.addWidget(self.chip_hd)
        chips.addWidget(FeatureChip("bolt", "Ultra Fast", "#2FA8FF"))
        chips.addWidget(FeatureChip("shield", "Secure &\nPrivate", "#3F8BFF"))
        chips.addStretch(1)
        left.addLayout(chips)
        left.addSpacing(16)
        self.get_started = GradientButton("Get Started", (theme.CYAN, theme.BLUE, theme.VIOLET, theme.MAGENTA),
                                          height=76, font_px=26)
        self.get_started.setMinimumWidth(292)
        self.get_started.clicked.connect(self._get_started)
        left.addWidget(self.get_started, 0, Qt.AlignLeft)
        left.addStretch(1)
        hero.addLayout(left, 0)
        self.hero_art = HeroArt()
        hero.addWidget(self.hero_art, 1)
        lay.addLayout(hero, 1)

        cards = QHBoxLayout()
        cards.setSpacing(28)
        self.card_wireless = ModeCard("wireless", "Wireless Cast", "Cast your iPhone screen\nwirelessly via Wi‑Fi.",
                                      "Start Wireless")
        self.card_wired = ModeCard("wired", "Wired Cast", "Connect with a USB cable\nfor stable casting.",
                                   "Start Wired")
        self.card_wireless.button.clicked.connect(lambda: self.cast("wireless"))
        self.card_wired.button.clicked.connect(lambda: self.cast("wired"))
        cards.addWidget(self.card_wireless)
        cards.addWidget(self.card_wired)
        lay.addLayout(cards)
        return page

    def _page_wireless(self) -> QWidget:
        w = self.settings.wireless
        page = QWidget()
        lay = QVBoxLayout(page)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(20)
        lay.addLayout(page_header("Wireless Cast", "Mirror your iPhone over Wi‑Fi using AirPlay Screen Mirroring."))
        row = QHBoxLayout()
        row.setSpacing(22)

        steps = GlassCard()
        sl = card_layout(steps, spacing=18)
        sl.addWidget(text_label("How to connect", 21, QFont.Bold))
        self.step_name = None
        for i, s in enumerate(("Connect your iPhone and this PC to the <b>same Wi‑Fi</b>.",
                               "Press <b>Start Wireless</b> below.",
                               "On iPhone, open <b>Control Center → Screen Mirroring</b>.",
                               f"Tap <b>{w.receiver_name}</b> — enter the PIN if asked.")):
            r = QHBoxLayout()
            r.setSpacing(14)
            num = QLabel(str(i + 1))
            num.setFixedSize(34, 34)
            num.setAlignment(Qt.AlignCenter)
            num.setFont(theme.font(16, QFont.Bold))
            num.setStyleSheet("border-radius:17px; background: qlineargradient(x1:0,y1:0,x2:1,y2:1,"
                              f"stop:0 {theme.CYAN}, stop:1 {theme.VIOLET});")
            t = text_label(s, 17, QFont.Normal, theme.TEXT_2)
            if i == 3:
                self.step_name = t
            r.addWidget(num, 0, Qt.AlignTop)
            r.addWidget(t, 1)
            sl.addLayout(r)
        sl.addStretch(1)
        sl.addWidget(text_label("Tip: 5 GHz Wi‑Fi gives the smoothest, lowest-latency picture.", 14,
                                QFont.Normal, theme.MUTED))
        self.btn_wireless = GradientButton("Start Wireless", theme.GRAD_WIRELESS, height=60, font_px=20)
        self.btn_wireless.clicked.connect(lambda: self.cast("wireless"))
        sl.addWidget(self.btn_wireless, 0, Qt.AlignLeft)

        opts = GlassCard()
        ol = card_layout(opts)
        ol.addWidget(text_label("Receiver", 21, QFont.Bold))
        g = form_grid()
        self.w_name = QLineEdit(w.receiver_name)
        self.w_name.textChanged.connect(lambda t: self.step_name and self.step_name.setText(
            f"Tap <b>{t.strip() or 'iCast'}</b> — enter the PIN if asked."))
        self.w_res = combo(RESOLUTIONS, w.resolution)
        self.w_res.currentTextChanged.connect(self._sync_resolution)
        self.w_fps = combo(FRAME_RATES, w.fps)
        self.w_full = QCheckBox("Open the mirror window in fullscreen")
        self.w_full.setChecked(w.fullscreen)
        self.w_lowlat = QCheckBox("Low-latency mode")
        self.w_lowlat.setChecked(w.low_latency)
        self.w_pin_on = QCheckBox("Require a PIN to connect")
        self.w_pin_on.setChecked(w.pin_enabled)
        self.w_pin = QLineEdit(w.pin)
        self.w_pin.setPlaceholderText("4 digits — blank for a random PIN each time")
        self.w_pin.setMaxLength(4)
        rows = (("Receiver name", self.w_name), ("Resolution", self.w_res), ("Frame rate", self.w_fps),
                ("", self.w_full), ("", self.w_lowlat), ("", self.w_pin_on), ("PIN", self.w_pin))
        for i, (lbl, wdg) in enumerate(rows):
            g.addWidget(field(lbl), i, 0)
            g.addWidget(wdg, i, 1)
        ol.addLayout(g)
        ol.addStretch(1)

        row.addWidget(steps, 1)
        row.addWidget(opts, 1)
        lay.addLayout(row, 1)
        return page

    def _page_wired(self) -> QWidget:
        u = self.settings.wired
        page = QWidget()
        lay = QVBoxLayout(page)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(20)
        lay.addLayout(page_header("Wired Cast", "USB cable mirroring — no Wi‑Fi, lowest latency, rock-solid picture."))
        row = QHBoxLayout()
        row.setSpacing(22)

        dev = GlassCard()
        dl = card_layout(dev)
        dl.addWidget(text_label("Connected iPhones", 21, QFont.Bold))
        self.dev_list = QListWidget()
        self.dev_list.setSelectionMode(QAbstractItemView.SingleSelection)
        dl.addWidget(self.dev_list, 1)
        self.dev_hint = text_label("Plug in your iPhone with a cable, unlock it and tap “Trust”.", 15,
                                   QFont.Normal, theme.MUTED)
        dl.addWidget(self.dev_hint)
        br = QHBoxLayout()
        br.setSpacing(12)
        self.trust_btn = OutlineButton("Trust this computer", "shield", height=48, font_px=15)
        self.trust_btn.clicked.connect(self._pair_selected)
        refresh = OutlineButton("Refresh", "refresh", height=48, font_px=15)
        refresh.clicked.connect(self._manual_scan)
        br.addWidget(self.trust_btn)
        br.addWidget(refresh)
        reset = OutlineButton("Reset USB", "refresh", height=48, font_px=15, colors=("#FF8A4C", "#FF4D6A"))
        reset.setToolTip("Takes the iPhone out of a stuck screen-capture mode (qvh deactivate)")
        reset.clicked.connect(self._reset_usb)
        br.addWidget(reset)
        br.addStretch(1)
        dl.addLayout(br)

        opts = GlassCard()
        ol = card_layout(opts)
        ol.addWidget(text_label("USB mirroring", 21, QFont.Bold))
        ol.addWidget(text_label("Your iPhone fills one big fullscreen window, showing the newest frame. "
                                "Esc stops casting · F toggles fullscreen.", 15, QFont.Normal, theme.MUTED))
        g = form_grid()
        self.u_output = QComboBox()
        for key, label in (("fullscreen", "Fullscreen — big single screen (recommended)"),
                           ("window", "Window — classic small window"),
                           ("record", "Record to MP4 file")):
            self.u_output.addItem(label, key)
        self.u_output.setCurrentIndex(max(0, self.u_output.findData(u.output)))
        self.u_output.setCursor(Qt.PointingHandCursor)
        self.u_latency = QComboBox()
        self.u_latency.addItem("Ultra — newest frame, zero lag", "ultra")
        self.u_latency.addItem("Smooth — steadier on slower PCs", "smooth")
        self.u_latency.setCurrentIndex(max(0, self.u_latency.findData(u.latency_mode)))
        self.u_latency.setCursor(Qt.PointingHandCursor)
        self.u_audio = QCheckBox("Play iPhone sound (low-latency, in sync)")
        self.u_audio.setChecked(u.audio)
        dir_row = QHBoxLayout()
        dir_row.setSpacing(8)
        self.u_dir = QLineEdit(u.record_dir)
        browse = IconButton("folder", 42, "Choose folder")
        browse.clicked.connect(self._pick_dir)
        dir_row.addWidget(self.u_dir, 1)
        dir_row.addWidget(browse)
        self.u_auto = QCheckBox("Start automatically when an iPhone is plugged in")
        self.u_auto.setChecked(u.auto_start_on_connect)
        g.addWidget(field("Display"), 0, 0)
        g.addWidget(self.u_output, 0, 1)
        g.addWidget(field("Latency"), 1, 0)
        g.addWidget(self.u_latency, 1, 1)
        g.addWidget(self.u_audio, 2, 1)
        g.addWidget(field("Recordings"), 3, 0)
        g.addLayout(dir_row, 3, 1)
        g.addWidget(self.u_auto, 4, 1)
        ol.addLayout(g)
        ol.addStretch(1)
        self.btn_wired = GradientButton("Start Wired", theme.GRAD_WIRED, height=60, font_px=20)
        self.btn_wired.clicked.connect(lambda: self.cast("wired"))
        ol.addWidget(self.btn_wired, 0, Qt.AlignLeft)

        row.addWidget(dev, 1)
        row.addWidget(opts, 1)
        lay.addLayout(row, 1)
        return page

    def _page_history(self) -> QWidget:
        page = QWidget()
        lay = QVBoxLayout(page)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(20)
        head = QHBoxLayout()
        head.addLayout(page_header("Connection History", "Every casting session, newest first."), 1)
        clear = OutlineButton("Clear history", "trash", height=48, font_px=15)
        clear.clicked.connect(self._clear_history)
        copy = OutlineButton("Copy log", "folder", height=48, font_px=15)
        copy.clicked.connect(self._copy_log)
        head.addWidget(copy, 0, Qt.AlignBottom)
        head.addSpacing(10)
        head.addWidget(clear, 0, Qt.AlignBottom)
        lay.addLayout(head)

        row = QHBoxLayout()
        row.setSpacing(22)
        tcard = GlassCard()
        tl = card_layout(tcard)
        self.hist = QTableWidget(0, 5)
        self.hist.setHorizontalHeaderLabels(["Date", "Mode", "Device", "Duration", "Result"])
        self.hist.verticalHeader().hide()
        self.hist.setShowGrid(False)
        self.hist.setEditTriggers(QAbstractItemView.NoEditTriggers)
        self.hist.setSelectionMode(QAbstractItemView.NoSelection)
        self.hist.setFocusPolicy(Qt.NoFocus)
        self.hist.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.hist.horizontalHeader().setDefaultAlignment(Qt.AlignLeft | Qt.AlignVCenter)
        self.hist.verticalHeader().setDefaultSectionSize(44)
        tl.addWidget(self.hist, 1)

        lcard = GlassCard()
        ll = card_layout(lcard)
        ll.addWidget(text_label("Live activity log", 19, QFont.Bold))
        self.log = QPlainTextEdit()
        self.log.setReadOnly(True)
        self.log.setMaximumBlockCount(5000)
        ll.addWidget(self.log, 1)
        row.addWidget(tcard, 3)
        row.addWidget(lcard, 2)
        lay.addLayout(row, 1)
        self._refresh_history()
        return page

    def _page_settings(self) -> QWidget:
        w, u = self.settings.wireless, self.settings.wired
        inner = QWidget()
        lay = QVBoxLayout(inner)
        lay.setContentsMargins(0, 0, 12, 0)
        lay.setSpacing(20)
        head = QHBoxLayout()
        head.addLayout(page_header("Settings", "Fine-tune video, audio and network behaviour."), 1)
        save = GradientButton("Save", theme.GRAD_WIRELESS, arrow=False, height=50, font_px=17)
        save.clicked.connect(self._save_clicked)
        head.addWidget(save, 0, Qt.AlignBottom)
        lay.addLayout(head)

        grid = QGridLayout()
        grid.setSpacing(22)
        av = GlassCard()
        al = card_layout(av)
        al.addWidget(text_label("Video & audio", 20, QFont.Bold))
        g = form_grid()
        self.w_rot = combo(ROTATIONS, w.rotation)
        self.w_flip = combo(FLIPS, w.flip)
        self.w_audio = combo(AUDIO_SINKS, w.audio_sink)
        self.w_video = combo(VIDEO_SINKS, w.video_sink)
        for i, (lbl, wdg) in enumerate((("Rotate (L/R)", self.w_rot), ("Flip", self.w_flip),
                                        ("Audio output", self.w_audio), ("Video output", self.w_video))):
            g.addWidget(field(lbl), i, 0)
            g.addWidget(wdg, i, 1)
        al.addLayout(g)
        al.addStretch(1)

        net = GlassCard()
        nl = card_layout(net)
        nl.addWidget(text_label("Network & security", 20, QFont.Bold))
        g2 = form_grid()
        self.w_fixed = QCheckBox("Use fixed ports (for firewalls)")
        self.w_fixed.setChecked(w.fixed_ports)
        self.w_port = QSpinBox()
        self.w_port.setRange(1024, 65000)
        self.w_port.setValue(w.base_port)
        self.w_host = QCheckBox("Append computer name to receiver name")
        self.w_host.setChecked(w.append_hostname)
        g2.addWidget(self.w_fixed, 0, 1)
        g2.addWidget(field("Base port"), 1, 0)
        g2.addWidget(self.w_port, 1, 1)
        g2.addWidget(self.w_host, 2, 1)
        g2.addWidget(text_label("Fixed ports use base, base+1 and base+2 (TCP/UDP) plus 5353/udp for discovery.",
                                13, QFont.Normal, theme.MUTED), 3, 1)
        nl.addLayout(g2)
        nl.addStretch(1)

        adv = GlassCard()
        vl = card_layout(adv)
        vl.addWidget(text_label("Advanced", 20, QFont.Bold))
        g3 = form_grid()
        self.w_extra = QLineEdit(w.extra_args)
        self.w_extra.setPlaceholderText("extra uxplay flags, e.g. -d")
        self.u_extra = QLineEdit(u.extra_args)
        self.u_extra.setPlaceholderText("extra qvh flags")
        g3.addWidget(field("Wireless engine"), 0, 0)
        g3.addWidget(self.w_extra, 0, 1)
        g3.addWidget(field("Wired engine"), 1, 0)
        g3.addWidget(self.u_extra, 1, 1)
        vl.addLayout(g3)
        vl.addStretch(1)

        grid.addWidget(av, 0, 0)
        grid.addWidget(net, 0, 1)
        grid.addWidget(adv, 1, 0, 1, 2)
        lay.addLayout(grid)
        lay.addStretch(1)
        return scroll(inner)

    def _page_about(self) -> QWidget:
        page = QWidget()
        lay = QHBoxLayout(page)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(22)

        info = GlassCard()
        il = card_layout(info, margins=(30, 26, 30, 26))
        il.addWidget(LogoMark(96), 0, Qt.AlignHCenter)
        t = GradientTitle("iCast", 64)
        il.addWidget(t, 0, Qt.AlignHCenter)
        v = text_label(f"Version {__version__}  ·  Made for iPhone", 15, QFont.Normal, theme.TEXT_2, wrap=False)
        v.setAlignment(Qt.AlignHCenter)
        il.addWidget(v)
        il.addSpacing(8)
        il.addWidget(text_label(
            "iCast mirrors your iPhone to this computer over Wi‑Fi (AirPlay) or a USB cable. "
            "Your screen never leaves your local network or cable — nothing is uploaded.",
            16, QFont.Normal, theme.TEXT_2))
        il.addStretch(1)
        il.addWidget(text_label(
            "Engines: UxPlay (GPL‑3.0) and qvh (MIT), launched as separate programs. "
            "iPhone and AirPlay are trademarks of Apple Inc.; iCast is not affiliated with Apple.",
            12, QFont.Normal, theme.MUTED))

        diag = GlassCard()
        dl = card_layout(diag)
        head = QHBoxLayout()
        head.addWidget(text_label("System check", 21, QFont.Bold), 1)
        re = OutlineButton("Re-check", "refresh", height=46, font_px=15)
        re.clicked.connect(self._refresh_diag)
        head.addWidget(re)
        dl.addLayout(head)
        self.diag = QTableWidget(0, 3)
        self.diag.setHorizontalHeaderLabels(["Component", "Used for", "Status"])
        self.diag.verticalHeader().hide()
        self.diag.setShowGrid(False)
        self.diag.setEditTriggers(QAbstractItemView.NoEditTriggers)
        self.diag.setSelectionMode(QAbstractItemView.NoSelection)
        self.diag.setFocusPolicy(Qt.NoFocus)
        self.diag.verticalHeader().setDefaultSectionSize(42)
        hh = self.diag.horizontalHeader()
        hh.setDefaultAlignment(Qt.AlignLeft | Qt.AlignVCenter)
        hh.setSectionResizeMode(0, QHeaderView.ResizeToContents)
        hh.setSectionResizeMode(1, QHeaderView.Stretch)
        hh.setSectionResizeMode(2, QHeaderView.Stretch)
        dl.addWidget(self.diag, 1)
        dl.addWidget(text_label("Missing something? Run scripts/install.sh from the iCast folder.", 14,
                                QFont.Normal, theme.MUTED))
        self._refresh_diag()

        lay.addWidget(info, 2)
        lay.addWidget(diag, 3)
        return page

    def _build_tray(self) -> None:
        self.tray = None
        if not QSystemTrayIcon.isSystemTrayAvailable():
            return
        self.tray = QSystemTrayIcon(logo_icon(), self)
        menu = QMenu()
        menu.addAction(QAction("Show iCast", self, triggered=self.showNormal))
        menu.addAction(QAction("Start / stop Wireless", self, triggered=lambda: self.cast("wireless")))
        menu.addAction(QAction("Start / stop Wired", self, triggered=lambda: self.cast("wired")))
        menu.addSeparator()
        menu.addAction(QAction("Quit", self, triggered=self.close))
        self.tray.setContextMenu(menu)
        self.tray.setToolTip(__app_name__)
        self.tray.show()

    # =============================================================== actions
    def go(self, idx: int) -> None:
        self.sidebar.group.button(idx).setChecked(True)
        self.stack.setCurrentIndex(idx)

    def _get_started(self) -> None:
        self.go(2 if self.phones else 1)

    def cast(self, mode: str) -> None:
        if self.ctrl.active:
            if self.ctrl.mode.lower() == mode:
                self.ctrl.stop()
            else:
                QMessageBox.information(self, __app_name__, "Stop the current cast before switching mode.")
            return
        if self.ctrl.cleaning_up:
            QMessageBox.information(self, __app_name__, "Giving the iPhone back to the system — try again in a moment.")
            return
        self._collect()
        self.settings.mode = mode
        if mode == "wireless":
            self.go(1)
            self.ctrl.start(WirelessBackend(self.settings.wireless), "Wireless", "AirPlay client")
            return
        self.go(2)
        if not self.phones:
            if os.path.exists(USB_HELPER) and apple_device_connected():
                # Linux sees an iPhone that usbmuxd doesn't: reset it, then start automatically
                self._auto_start_wired = True
                self._on_state("starting", "Resetting the iPhone's USB connection…")
                QThreadPool.globalInstance().start(_Recover(self.ctrl.log_line, self.recovered, force=True))
                return
            self._on_state("error", "No iPhone detected over USB — connect the cable and unlock the phone.")
            return
        udid = self.settings.wired.preferred_udid
        phone = next((p for p in self.phones if p.udid == udid), self.phones[0])
        self.ctrl.start(WiredBackend(self.settings.wired, udid=phone.udid, screen=self._screen_rect()),
                        "Wired", phone.name)

    def _screen_rect(self) -> tuple[int, int, int, int]:
        """Physical-pixel geometry of the monitor iCast is on — the viewer fills exactly this screen."""
        scr = self.screen() or QApplication.primaryScreen()
        g, dpr = scr.geometry(), scr.devicePixelRatio()
        return (round(g.x() * dpr), round(g.y() * dpr), round(g.width() * dpr), round(g.height() * dpr))

    def _collect(self) -> None:
        w, u = self.settings.wireless, self.settings.wired
        w.receiver_name = self.w_name.text().strip() or "iCast"
        w.resolution = self.w_res.currentText()
        w.fps = int(self.w_fps.currentText())
        w.fullscreen = self.w_full.isChecked()
        w.low_latency = self.w_lowlat.isChecked()
        w.pin_enabled = self.w_pin_on.isChecked()
        w.pin = self.w_pin.text().strip()
        w.rotation = self.w_rot.currentText()
        w.flip = self.w_flip.currentText()
        w.audio_sink = self.w_audio.currentText()
        w.video_sink = self.w_video.currentText()
        w.fixed_ports = self.w_fixed.isChecked()
        w.base_port = self.w_port.value()
        w.append_hostname = self.w_host.isChecked()
        w.extra_args = self.w_extra.text()
        u.output = self.u_output.currentData()
        u.latency_mode = self.u_latency.currentData()
        u.audio = self.u_audio.isChecked()
        u.record_dir = self.u_dir.text().strip() or u.record_dir
        u.auto_start_on_connect = self.u_auto.isChecked()
        u.extra_args = self.u_extra.text()
        item = self.dev_list.currentItem()
        if item:
            u.preferred_udid = item.data(Qt.UserRole)
        self.settings.validate()
        try:
            self.settings.save()
        except OSError as exc:
            self._log(f"Could not save settings: {exc}")

    def _save_clicked(self) -> None:
        self._collect()
        self._event("Settings saved")
        self.strip.set_status("Settings saved", True)

    def _sync_resolution(self, res: str) -> None:
        self.chip_hd.label.setText(HD_CHIP.get(res, "HD"))
        if not self.ctrl.active:
            self.strip.set_resolution(RES_LABEL.get(res, res))

    def _device_menu(self) -> None:
        menu = QMenu(self)
        head = menu.addAction("USB iPhones")
        head.setEnabled(False)
        if not self.phones:
            a = menu.addAction("   No iPhone connected by cable")
            a.setEnabled(False)
        for p in self.phones:
            a = menu.addAction(f"   {p.label}")
            a.setCheckable(True)
            a.setChecked(p.udid == self.settings.wired.preferred_udid)
            a.triggered.connect(lambda _=False, u=p.udid: self._choose_phone(u))
        menu.addSeparator()
        wl = menu.addAction("Wireless: any iPhone on the same Wi‑Fi")
        wl.triggered.connect(lambda: self.go(1))
        menu.addAction("Scan for devices", self._manual_scan)
        menu.exec(self.device_btn.mapToGlobal(self.device_btn.rect().bottomLeft()))

    def _choose_phone(self, udid: str) -> None:
        self.settings.wired.preferred_udid = udid
        for i in range(self.dev_list.count()):
            if self.dev_list.item(i).data(Qt.UserRole) == udid:
                self.dev_list.setCurrentRow(i)
        self._update_device_button()

    def _show_events(self) -> None:
        menu = QMenu(self)
        if not self.events:
            a = menu.addAction("No notifications yet")
            a.setEnabled(False)
        for e in self.events[:12]:
            menu.addAction(e).setEnabled(False)
        self.bell.set_badge(False)
        menu.exec(self.bell.mapToGlobal(self.bell.rect().bottomLeft()))

    def _event(self, text: str) -> None:
        self.events.insert(0, f"{time.strftime('%H:%M')}  ·  {text}")
        del self.events[50:]
        self.bell.set_badge(True)

    def _premium(self) -> None:
        QMessageBox.information(self, "iCast Premium",
                                "<h3>You're all set</h3><p>Every iCast feature is unlocked in this version: "
                                "Wireless & Wired casting, up to 4K, PIN protection, recording and history.</p>")

    def _manual_scan(self) -> None:
        self.strip.set_status("Scanning for iPhones…", True)
        self.watcher.scan()

    def _reset_usb(self) -> None:
        if self.ctrl.busy:
            QMessageBox.information(self, __app_name__, "Stop casting first.")
            return
        item = self.dev_list.currentItem()
        udid = item.data(Qt.UserRole) if item else ""
        backend = WiredBackend(self.settings.wired, udid=udid)
        steps = backend.cleanup_steps()
        if not steps:  # no iPhone listed (usbmuxd lost it): reset every connected iPhone
            steps = [["sudo", "-n", USB_HELPER, "recover"]] if os.path.exists(USB_HELPER) else [backend.reset_command()]
        err = run_steps(steps, backend, self._log, best_effort=True)
        QTimer.singleShot(2500, self.watcher.scan)
        QMessageBox.information(self, __app_name__,
                                ("USB reset done. Unplug the iPhone, plug it back in, unlock it, "
                                 "then press Start Wired." if not err else f"Reset USB failed:\n{err}"))

    def _after_recover(self) -> None:
        self.watcher.scan()
        if self._auto_start_wired:
            QTimer.singleShot(4000, self._auto_start_after_reset)

    def _auto_start_after_reset(self) -> None:
        if not self._auto_start_wired:
            return
        self._auto_start_wired = False
        if self.phones and not self.ctrl.busy:
            self.cast("wired")
        else:
            self._on_state("error", "The iPhone still isn't visible. Unplug the cable, plug it back in, "
                                    "unlock the iPhone, then press Start Wired.")

    def _copy_log(self) -> None:
        text = self.log.toPlainText()
        QApplication.clipboard().setText(text)
        path = config_dir() / "last-log.txt"
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
            where = f"\n\nAlso saved to:\n{path}"
        except OSError:
            where = ""
        QMessageBox.information(self, __app_name__, f"Log copied to the clipboard.{where}")

    def _pair_selected(self) -> None:
        item = self.dev_list.currentItem()
        if not item:
            QMessageBox.information(self, __app_name__, "Connect and select an iPhone first.")
            return
        ok, msg = devices.pair(item.data(Qt.UserRole))
        QMessageBox.information(self, __app_name__,
                                msg if ok else f"{msg}\n\nUnlock the iPhone, tap “Trust”, then try again.")
        self.watcher.scan()

    def _pick_dir(self) -> None:
        d = QFileDialog.getExistingDirectory(self, "Recordings folder", self.u_dir.text())
        if d:
            self.u_dir.setText(d)

    def _clear_history(self) -> None:
        history.clear()
        self._refresh_history()

    # ================================================================ events
    def _on_state(self, state: str, detail: str) -> None:
        self.ctrl.state = CastState(state)
        color = theme.STATE_COLORS.get(state, theme.GREEN)
        self.pill.set_state(theme.STATE_PILL.get(state, state), color)
        active = state in ("starting", "waiting", "streaming")
        mode = self.ctrl.mode.lower()
        wl_active = active and mode == "wireless"
        wd_active = active and mode == "wired"
        self.card_wireless.set_active(wl_active, "Stop Wireless" if wl_active else "Start Wireless")
        self.card_wired.set_active(wd_active, "Stop Wired" if wd_active else "Start Wired")
        self.btn_wireless.setText("Stop Wireless" if wl_active else "Start Wireless")
        self.btn_wireless.set_colors(theme.GRAD_STOP if wl_active else theme.GRAD_WIRELESS)
        self.btn_wired.setText("Stop Wired" if wd_active else "Start Wired")
        self.btn_wired.set_colors(theme.GRAD_STOP if wd_active else theme.GRAD_WIRED)
        self.hero_art.set_live(state == "streaming")

        name = self.settings.wireless.receiver_name
        if state == "idle":
            msg = (f"{self.phones[0].name} connected via USB" if self.phones else "Your iPhone is ready to connect")
            self.strip.set_status(msg, True)
            self.strip.set_connection("Ready", theme.GREEN)
        elif state == "starting":
            self.strip.set_status("Starting casting engine…", True, warn=True)
            self.strip.set_connection("Starting", theme.AMBER)
        elif state == "waiting":
            msg = (f"Open Screen Mirroring on iPhone → “{name}”" if wl_active else "Waiting for iPhone screen…")
            self.strip.set_status(msg, True, warn=True)
            self.strip.set_connection("Waiting", theme.CYAN)
            self._event("Receiver ready — waiting for iPhone")
        elif state == "streaming":
            self.strip.set_status("Casting live from your iPhone", True)
            self.strip.set_connection("Stable", theme.GREEN)
            self._event(f"{self.ctrl.mode} cast started")
            if self.tray:
                self.tray.showMessage(__app_name__, "iPhone connected — casting live.", logo_icon(), 2500)
        else:
            self.strip.set_status(detail or "Something went wrong — see Connection History", False)
            self.strip.set_connection("Error", theme.RED)
            self._event(detail or "Error")
            if detail:
                self._log(f"⚠ {detail}")
        res = self.settings.wireless.resolution
        self.strip.set_resolution("Native" if wd_active else RES_LABEL.get(res, res))

    def _on_devices(self, phones: list) -> None:
        if self.ctrl.busy:
            return  # usbmuxd is paused while casting over USB; keep the last known list
        before = {p.udid for p in self.phones}
        self.phones = phones
        cur = self.dev_list.currentItem().data(Qt.UserRole) if self.dev_list.currentItem() else None
        self.dev_list.clear()
        for p in phones:
            trust = {True: "✓ Trusted", False: "⚠ Not trusted — click “Trust this computer”", None: ""}[p.paired]
            item = QListWidgetItem(f"{p.label}\n{trust}")
            item.setIcon(icon_pixmap("phone", 28, "#FFFFFF"))
            item.setData(Qt.UserRole, p.udid)
            self.dev_list.addItem(item)
            if p.udid in (cur, self.settings.wired.preferred_udid):
                self.dev_list.setCurrentItem(item)
        if phones and not self.dev_list.currentItem():
            self.dev_list.setCurrentRow(0)
        self.dev_hint.setVisible(not phones)
        self.trust_btn.setEnabled(bool(phones))
        self._update_device_button()
        if self._auto_start_wired and phones and not self.ctrl.busy:
            self._auto_start_wired = False
            QTimer.singleShot(0, lambda: self.cast("wired"))
            return
        new = {p.udid for p in phones} - before
        for p in phones:
            if p.udid in new:
                self._event(f"{p.name} connected via USB")
        if self.ctrl.state == CastState.IDLE:
            self._on_state("idle", "")
        if new and self.u_auto.isChecked() and not self.ctrl.active:
            self.cast("wired")

    def _update_device_button(self) -> None:
        udid = self.settings.wired.preferred_udid
        phone = next((p for p in self.phones if p.udid == udid), self.phones[0] if self.phones else None)
        self.device_btn.setText(phone.name if phone else "iPhone Only")

    def _refresh_diag(self) -> None:
        rows = deps.check_all()
        self.diag.setRowCount(len(rows))
        for r, st in enumerate(rows):
            name = QTableWidgetItem(st.dep.binary)
            name.setIcon(icon_pixmap("check" if st.ok else "alert", 20, theme.GREEN if st.ok else theme.RED))
            self.diag.setItem(r, 0, name)
            self.diag.setItem(r, 1, QTableWidgetItem(f"{st.dep.purpose} · {st.dep.required_for}"))
            status = QTableWidgetItem(st.path or f"Install: {st.dep.apt_package}")
            status.setForeground(QColor(theme.TEXT_2 if st.ok else "#FF8A9E"))
            self.diag.setItem(r, 2, status)

    def _refresh_history(self) -> None:
        recs = history.load()
        self.hist.setRowCount(len(recs))
        colors = {"Completed": theme.GREEN, "No connection": theme.AMBER, "Failed": theme.RED}
        for i, rec in enumerate(recs):
            vals = (rec.started, rec.mode, rec.device, history.fmt_duration(rec.duration_s), rec.result)
            for c, v in enumerate(vals):
                it = QTableWidgetItem(v)
                if c == 4:
                    it.setForeground(QColor(colors.get(v, theme.TEXT)))
                self.hist.setItem(i, c, it)

    def _log(self, line: str) -> None:
        self.log.appendPlainText(f"{time.strftime('%H:%M:%S')}  {line}")

    def _tick(self) -> None:
        if self.ctrl.state == CastState.STREAMING and self.ctrl.started_at:
            secs = int(time.monotonic() - self.ctrl.started_at)
            self.pill.set_state(f"Live — {history.fmt_duration(secs)}", theme.GREEN)

    def changeEvent(self, e) -> None:  # noqa: N802
        super().changeEvent(e)
        if hasattr(self, "titlebar"):
            self.titlebar.sync()
            self.centralWidget().update()

    def closeEvent(self, event) -> None:  # noqa: N802
        self._collect()
        self._clock.stop()
        self._scan_timer.stop()
        self.ctrl.shutdown()  # stops the cast, waits for workers, hands the iPhone back before quitting
        if self.tray:
            self.tray.hide()
        event.accept()
