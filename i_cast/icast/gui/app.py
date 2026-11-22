"""Desktop entry point."""

from __future__ import annotations

import sys

from PySide6.QtCore import Qt
from PySide6.QtWidgets import QApplication

from icast import __app_name__, __version__
from icast.gui import theme


def run(argv: list[str] | None = None) -> int:
    QApplication.setHighDpiScaleFactorRoundingPolicy(Qt.HighDpiScaleFactorRoundingPolicy.PassThrough)
    app = QApplication.instance() or QApplication(argv or sys.argv)
    app.setApplicationName(__app_name__)
    app.setApplicationVersion(__version__)
    app.setDesktopFileName("icast")
    app.setStyle("Fusion")
    theme.resolve_font()
    app.setFont(theme.font(14))
    app.setStyleSheet(theme.stylesheet())

    from icast.gui.main_window import MainWindow
    from icast.gui.widgets import logo_icon

    app.setWindowIcon(logo_icon())
    win = MainWindow()
    win.show()
    return app.exec()
