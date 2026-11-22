"""iCast main window."""

from __future__ import annotations

import time

from PySide6.QtCore import QByteArray, QSize, Qt, QTimer
from PySide6.QtGui import QAction, QIcon, QPixmap
from PySide6.QtWidgets import (
    QButtonGroup, QCheckBox, QComboBox, QFileDialog, QFormLayout, QFrame, QHBoxLayout, QHeaderView,
    QLabel, QLineEdit, QListWidget, QListWidgetItem, QMainWindow, QMessageBox, QPlainTextEdit,
    QPushButton, QSpinBox, QStackedWidget, QSystemTrayIcon, QMenu, QTableWidget, QTableWidgetItem,
    QVBoxLayout, QWidget,
)

from icast import __app_name__, __version__
from icast.backends.base import CastState
from icast.backends.wired import WiredBackend
from icast.backends.wireless import WirelessBackend
from icast.core import deps, devices
from icast.core.config import AUDIO_SINKS, FLIPS, FRAME_RATES, RESOLUTIONS, ROTATIONS, VIDEO_SINKS, AppSettings
from icast.gui import theme
from icast.gui.controller import CastController, DeviceWatcher

STATE_TEXT = {
    "idle": "Ready",
    "starting": "Starting engine…",
    "waiting": "Waiting for iPhone",
    "streaming": "Live — mirroring",
    "error": "Something went wrong",
}


def logo_icon() -> QIcon:
    pix = QPixmap()
    pix.loadFromData(QByteArray(theme.LOGO_SVG.encode()), "SVG")
    return QIcon(pix)


def card() -> tuple[QFrame, QVBoxLayout]:
    frame = QFrame(objectName="card")
    lay = QVBoxLayout(frame)
    lay.setContentsMargins(22, 20, 22, 20)
    lay.setSpacing(12)
    return frame, lay


def label(text: str, name: str = "") -> QLabel:
    lbl = QLabel(text)
    if name:
        lbl.setObjectName(name)
    lbl.setWordWrap(True)
    return lbl


class StatusPanel(QFrame):
    """Hero card: status dot, headline, timer and the big Start/Stop button."""

    def __init__(self, on_toggle) -> None:
        super().__init__(objectName="card")
        lay = QHBoxLayout(self)
        lay.setContentsMargins(24, 22, 24, 22)
        self.dot = QLabel()
        self.dot.setFixedSize(14, 14)
        text = QVBoxLayout()
        self.title = label("Ready", "h1")
        self.detail = label("Choose a mode and press Start.", "muted")
        text.addWidget(self.title)
        text.addWidget(self.detail)
        self.timer_lbl = label("", "h2")
        self.button = QPushButton("Start casting", objectName="primary")
        self.button.setCursor(Qt.PointingHandCursor)
        self.button.clicked.connect(on_toggle)
        lay.addWidget(self.dot, 0, Qt.AlignTop)
        lay.addSpacing(8)
        lay.addLayout(text, 1)
        lay.addWidget(self.timer_lbl)
        lay.addSpacing(16)
        lay.addWidget(self.button)
        self.set_state("idle", "")

    def set_state(self, state: str, detail: str) -> None:
        color = theme.STATE_COLORS.get(state, theme.MUTED)
        self.dot.setStyleSheet(f"background:{color}; border-radius:7px;")
        self.title.setText(STATE_TEXT.get(state, state))
        if detail:
            self.detail.setText(detail)
        active = state in ("starting", "waiting", "streaming")
        self.button.setText("Stop casting" if active else "Start casting")
        self.button.setProperty("active", "true" if active else "false")
        self.button.style().unpolish(self.button)
        self.button.style().polish(self.button)


class MainWindow(QMainWindow):
    def __init__(self) -> None:
        super().__init__()
        self.settings = AppSettings.load()
        self.ctrl = CastController()
        self.watcher = DeviceWatcher()
        self.phones: list[devices.IPhone] = []

        self.setWindowTitle(f"{__app_name__} — iPhone Screen Cast")
        self.setWindowIcon(logo_icon())
        self.resize(1120, 760)
        self.setMinimumSize(900, 620)

        root = QWidget(objectName="root")
        self.setCentralWidget(root)
        outer = QHBoxLayout(root)
        outer.setContentsMargins(0, 0, 0, 0)
        outer.setSpacing(0)
        outer.addWidget(self._build_sidebar())

        main = QVBoxLayout()
        main.setContentsMargins(28, 24, 28, 20)
        main.setSpacing(16)
        self.status = StatusPanel(self.toggle_cast)
        main.addWidget(self.status)
        self.pages = QStackedWidget()
        self.pages.addWidget(self._page_wireless())
        self.pages.addWidget(self._page_wired())
        self.pages.addWidget(self._page_diagnostics())
        self.pages.addWidget(self._page_logs())
        main.addWidget(self.pages, 1)
        outer.addLayout(main, 1)

        self.statusBar().showMessage(f"{__app_name__} {__version__}")
        self._build_tray()

        self.ctrl.state_changed.connect(self._on_state)
        self.ctrl.log_line.connect(self._append_log)
        self.watcher.found.connect(self._on_devices)

        self._clock = QTimer(self, interval=1000, timeout=self._tick)
        self._clock.start()
        self._scan_timer = QTimer(self, interval=2500, timeout=self.watcher.scan)
        self._scan_timer.start()
        self.watcher.scan()
        self._select_mode(0 if self.settings.mode == "wireless" else 1)

    # ------------------------------------------------------------------ layout
    def _build_sidebar(self) -> QWidget:
        side = QWidget(objectName="sidebar")
        side.setFixedWidth(230)
        lay = QVBoxLayout(side)
        lay.setContentsMargins(16, 22, 16, 16)
        lay.setSpacing(6)
        head = QHBoxLayout()
        logo = QLabel()
        logo.setPixmap(logo_icon().pixmap(QSize(38, 38)))
        names = QVBoxLayout()
        names.setSpacing(0)
        names.addWidget(label(__app_name__, "brand"))
        names.addWidget(label("iPhone Screen Cast", "brandSub"))
        head.addWidget(logo)
        head.addLayout(names, 1)
        lay.addLayout(head)
        lay.addSpacing(22)

        self.nav = QButtonGroup(self)
        self.nav.setExclusive(True)
        for idx, text in enumerate(("  📶   Wireless · AirPlay", "  🔌   Wired · USB", "  🩺   Diagnostics", "  📜   Activity log")):
            btn = QPushButton(text, objectName="nav", checkable=True)
            btn.setCursor(Qt.PointingHandCursor)
            self.nav.addButton(btn, idx)
            lay.addWidget(btn)
        self.nav.idClicked.connect(self._select_mode)
        lay.addStretch(1)
        lay.addWidget(label(f"v{__version__}", "muted"))
        return side

    def _page_wireless(self) -> QWidget:
        page = QWidget()
        grid = QHBoxLayout(page)
        grid.setContentsMargins(0, 0, 0, 0)
        grid.setSpacing(16)
        w = self.settings.wireless

        how, how_l = card()
        how_l.addWidget(label("Wireless mirroring", "h2"))
        steps = [
            "Connect the iPhone and this computer to the <b>same Wi‑Fi</b>.",
            "Press <b>Start casting</b>.",
            "On iPhone open <b>Control Center → Screen Mirroring</b>.",
            f"Tap <b>{w.receiver_name}</b>. Enter the PIN if asked.",
        ]
        for i, s in enumerate(steps, 1):
            how_l.addWidget(label(f"<span style='color:{theme.ACCENT}'><b>{i}</b></span>&nbsp;&nbsp;{s}"))
        how_l.addStretch(1)
        self.ww_tip = label("Tip: 5 GHz Wi‑Fi gives the smoothest picture.", "muted")
        how_l.addWidget(self.ww_tip)

        opts, opts_l = card()
        opts_l.addWidget(label("Receiver settings", "h2"))
        form = QFormLayout()
        form.setHorizontalSpacing(18)
        form.setVerticalSpacing(10)
        self.w_name = QLineEdit(w.receiver_name)
        self.w_res = QComboBox(); self.w_res.addItems(RESOLUTIONS); self.w_res.setCurrentText(w.resolution)
        self.w_fps = QComboBox(); self.w_fps.addItems([str(f) for f in FRAME_RATES]); self.w_fps.setCurrentText(str(w.fps))
        self.w_full = QCheckBox("Start in fullscreen"); self.w_full.setChecked(w.fullscreen)
        self.w_lowlat = QCheckBox("Low-latency mode"); self.w_lowlat.setChecked(w.low_latency)
        self.w_pin_on = QCheckBox("Require PIN"); self.w_pin_on.setChecked(w.pin_enabled)
        self.w_pin = QLineEdit(w.pin); self.w_pin.setPlaceholderText("4 digits (blank = random)"); self.w_pin.setMaxLength(4)
        self.w_rot = QComboBox(); self.w_rot.addItems(ROTATIONS); self.w_rot.setCurrentText(w.rotation)
        self.w_flip = QComboBox(); self.w_flip.addItems(FLIPS); self.w_flip.setCurrentText(w.flip)
        self.w_audio = QComboBox(); self.w_audio.addItems(AUDIO_SINKS); self.w_audio.setCurrentText(w.audio_sink)
        self.w_video = QComboBox(); self.w_video.addItems(VIDEO_SINKS); self.w_video.setCurrentText(w.video_sink)
        self.w_fixed = QCheckBox("Fixed ports (for firewalls)"); self.w_fixed.setChecked(w.fixed_ports)
        self.w_port = QSpinBox(); self.w_port.setRange(1024, 65000); self.w_port.setValue(w.base_port)
        self.w_extra = QLineEdit(w.extra_args); self.w_extra.setPlaceholderText("advanced uxplay flags")
        form.addRow("Receiver name", self.w_name)
        form.addRow("Resolution", self.w_res)
        form.addRow("Frame rate", self.w_fps)
        form.addRow("", self.w_full)
        form.addRow("", self.w_lowlat)
        form.addRow("", self.w_pin_on)
        form.addRow("PIN", self.w_pin)
        form.addRow("Rotate", self.w_rot)
        form.addRow("Flip", self.w_flip)
        form.addRow("Audio output", self.w_audio)
        form.addRow("Video output", self.w_video)
        form.addRow("", self.w_fixed)
        form.addRow("Base port", self.w_port)
        form.addRow("Extra args", self.w_extra)
        opts_l.addLayout(form)
        opts_l.addStretch(1)

        grid.addWidget(how, 1)
        grid.addWidget(opts, 1)
        return page

    def _page_wired(self) -> QWidget:
        page = QWidget()
        grid = QHBoxLayout(page)
        grid.setContentsMargins(0, 0, 0, 0)
        grid.setSpacing(16)
        u = self.settings.wired

        dev, dev_l = card()
        head = QHBoxLayout()
        head.addWidget(label("Connected iPhones", "h2"), 1)
        refresh = QPushButton("Refresh")
        refresh.clicked.connect(self.watcher.scan)
        head.addWidget(refresh)
        dev_l.addLayout(head)
        self.dev_list = QListWidget()
        self.dev_list.setIconSize(QSize(22, 22))
        dev_l.addWidget(self.dev_list, 1)
        self.dev_hint = label("Plug in your iPhone with a cable, unlock it and tap “Trust”.", "muted")
        dev_l.addWidget(self.dev_hint)
        row = QHBoxLayout()
        self.trust_btn = QPushButton("Trust this computer")
        self.trust_btn.clicked.connect(self._pair_selected)
        row.addWidget(self.trust_btn)
        row.addStretch(1)
        dev_l.addLayout(row)

        opts, opts_l = card()
        opts_l.addWidget(label("USB mirroring", "h2"))
        opts_l.addWidget(label("Cable mirroring uses Apple's screen-capture protocol: no Wi‑Fi, "
                               "lowest latency, and full-quality picture.", "muted"))
        form = QFormLayout()
        form.setVerticalSpacing(10)
        self.u_output = QComboBox(); self.u_output.addItems(["window", "record"]); self.u_output.setCurrentText(u.output)
        dir_row = QHBoxLayout()
        self.u_dir = QLineEdit(u.record_dir)
        browse = QPushButton("…"); browse.setFixedWidth(40); browse.clicked.connect(self._pick_dir)
        dir_row.addWidget(self.u_dir, 1); dir_row.addWidget(browse)
        self.u_auto = QCheckBox("Start automatically when an iPhone is plugged in"); self.u_auto.setChecked(u.auto_start_on_connect)
        self.u_extra = QLineEdit(u.extra_args); self.u_extra.setPlaceholderText("advanced qvh flags")
        form.addRow("Output", self.u_output)
        form.addRow("Recordings", dir_row)
        form.addRow("", self.u_auto)
        form.addRow("Extra args", self.u_extra)
        opts_l.addLayout(form)
        opts_l.addStretch(1)

        grid.addWidget(dev, 1)
        grid.addWidget(opts, 1)
        return page

    def _page_diagnostics(self) -> QWidget:
        page, lay = card()
        head = QHBoxLayout()
        head.addWidget(label("System check", "h2"), 1)
        recheck = QPushButton("Re-check")
        recheck.clicked.connect(self._refresh_diag)
        head.addWidget(recheck)
        lay.addLayout(head)
        self.diag = QTableWidget(0, 4)
        self.diag.setHorizontalHeaderLabels(["", "Component", "Purpose", "Status / install"])
        self.diag.verticalHeader().hide()
        self.diag.setEditTriggers(QTableWidget.NoEditTriggers)
        self.diag.setSelectionMode(QTableWidget.NoSelection)
        hh = self.diag.horizontalHeader()
        hh.setSectionResizeMode(0, QHeaderView.ResizeToContents)
        hh.setSectionResizeMode(1, QHeaderView.ResizeToContents)
        hh.setSectionResizeMode(2, QHeaderView.Stretch)
        hh.setSectionResizeMode(3, QHeaderView.Stretch)
        lay.addWidget(self.diag, 1)
        lay.addWidget(label("Missing something? Run <code>scripts/install.sh</code> from the iCast folder.", "muted"))
        self._refresh_diag()
        return page

    def _page_logs(self) -> QWidget:
        page, lay = card()
        head = QHBoxLayout()
        head.addWidget(label("Activity log", "h2"), 1)
        clear = QPushButton("Clear")
        head.addWidget(clear)
        lay.addLayout(head)
        self.log = QPlainTextEdit(objectName="log", readOnly=True)
        self.log.setMaximumBlockCount(5000)
        clear.clicked.connect(self.log.clear)
        lay.addWidget(self.log, 1)
        return page

    def _build_tray(self) -> None:
        self.tray = None
        if not QSystemTrayIcon.isSystemTrayAvailable():
            return
        self.tray = QSystemTrayIcon(logo_icon(), self)
        menu = QMenu()
        self.tray_toggle = QAction("Start casting", self, triggered=self.toggle_cast)
        menu.addAction(QAction("Show iCast", self, triggered=self.showNormal))
        menu.addAction(self.tray_toggle)
        menu.addSeparator()
        menu.addAction(QAction("Quit", self, triggered=self.close))
        self.tray.setContextMenu(menu)
        self.tray.setToolTip(__app_name__)
        self.tray.show()

    # ----------------------------------------------------------------- actions
    def _select_mode(self, idx: int) -> None:
        self.nav.button(idx).setChecked(True)
        self.pages.setCurrentIndex(idx)
        if idx in (0, 1) and not self.ctrl.active:
            self.settings.mode = "wireless" if idx == 0 else "wired"
            hint = ("Wireless mode — iPhone on the same Wi‑Fi." if idx == 0
                    else "Wired mode — connect iPhone with a USB cable.")
            self.status.detail.setText(hint)

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
        w.extra_args = self.w_extra.text()
        u.output = self.u_output.currentText()
        u.record_dir = self.u_dir.text().strip() or u.record_dir
        u.auto_start_on_connect = self.u_auto.isChecked()
        u.extra_args = self.u_extra.text()
        sel = self.dev_list.currentItem()
        if sel:
            u.preferred_udid = sel.data(Qt.UserRole)
        self.settings.validate()
        try:
            self.settings.save()
        except OSError as exc:
            self._append_log(f"Could not save settings: {exc}")

    def toggle_cast(self) -> None:
        if self.ctrl.active:
            self.ctrl.stop()
            return
        self._collect()
        if self.settings.mode == "wireless":
            self.ctrl.start(WirelessBackend(self.settings.wireless))
        else:
            udid = self.settings.wired.preferred_udid
            known = [p.udid for p in self.phones]
            if not known:
                self._on_state("error", "No iPhone detected over USB. Connect the cable and unlock the phone.")
                return
            if udid not in known:
                udid = known[0]
            self.ctrl.start(WiredBackend(self.settings.wired, udid=udid))

    def _pair_selected(self) -> None:
        item = self.dev_list.currentItem()
        if not item:
            QMessageBox.information(self, __app_name__, "Select an iPhone first.")
            return
        ok, msg = devices.pair(item.data(Qt.UserRole))
        QMessageBox.information(self, __app_name__,
                                msg if ok else f"{msg}\n\nUnlock the iPhone and tap “Trust”, then try again.")
        self.watcher.scan()

    def _pick_dir(self) -> None:
        d = QFileDialog.getExistingDirectory(self, "Recordings folder", self.u_dir.text())
        if d:
            self.u_dir.setText(d)

    # ------------------------------------------------------------------ events
    def _on_state(self, state: str, detail: str) -> None:
        self.status.set_state(state, detail)
        self.ctrl.state = CastState(state)
        if self.tray:
            self.tray_toggle.setText("Stop casting" if self.ctrl.active else "Start casting")
            if state == "streaming":
                self.tray.showMessage(__app_name__, "iPhone connected — mirroring live.", logo_icon(), 2500)
        if state == "error":
            self._append_log(f"⚠ {detail}")
        self.statusBar().showMessage(f"{STATE_TEXT.get(state, state)}  {('· ' + detail) if detail else ''}")

    def _on_devices(self, phones: list) -> None:
        before = {p.udid for p in self.phones}
        self.phones = phones
        current = self.dev_list.currentItem().data(Qt.UserRole) if self.dev_list.currentItem() else None
        self.dev_list.clear()
        for p in phones:
            trust = {True: "✓ Trusted", False: "⚠ Not trusted", None: ""}[p.paired]
            item = QListWidgetItem(f"📱  {p.label}\n      {trust}   ·   {p.udid[:8]}…")
            item.setData(Qt.UserRole, p.udid)
            self.dev_list.addItem(item)
            if p.udid in (current, self.settings.wired.preferred_udid):
                self.dev_list.setCurrentItem(item)
        if phones and not self.dev_list.currentItem():
            self.dev_list.setCurrentRow(0)
        self.dev_hint.setVisible(not phones)
        self.trust_btn.setEnabled(bool(phones))
        new = {p.udid for p in phones} - before
        if (new and self.settings.mode == "wired" and self.u_auto.isChecked() and not self.ctrl.active):
            self.toggle_cast()

    def _refresh_diag(self) -> None:
        rows = deps.check_all()
        self.diag.setRowCount(len(rows))
        for r, st in enumerate(rows):
            mark = QTableWidgetItem("✔" if st.ok else "✘")
            mark.setForeground(Qt.green if st.ok else Qt.red)
            self.diag.setItem(r, 0, mark)
            self.diag.setItem(r, 1, QTableWidgetItem(st.dep.binary))
            self.diag.setItem(r, 2, QTableWidgetItem(f"{st.dep.purpose}  ({st.dep.required_for})"))
            self.diag.setItem(r, 3, QTableWidgetItem(st.path or f"install: {st.dep.apt_package}"))

    def _append_log(self, line: str) -> None:
        self.log.appendPlainText(f"{time.strftime('%H:%M:%S')}  {line}")

    def _tick(self) -> None:
        if self.ctrl.state == CastState.STREAMING and self.ctrl.started_at:
            secs = int(time.monotonic() - self.ctrl.started_at)
            self.status.timer_lbl.setText(f"● {secs // 3600:02d}:{secs % 3600 // 60:02d}:{secs % 60:02d}")
        else:
            self.status.timer_lbl.setText("")

    def closeEvent(self, event) -> None:  # noqa: N802 (Qt API)
        self._collect()
        self.ctrl.stop()
        if self.tray:
            self.tray.hide()
        event.accept()
