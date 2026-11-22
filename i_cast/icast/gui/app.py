"""Desktop entry point."""

from __future__ import annotations

import sys

from PySide6.QtWidgets import QApplication

from icast import __app_name__, __version__
from icast.gui import theme
from icast.gui.main_window import MainWindow, logo_icon


def run(argv: list[str] | None = None) -> int:
    app = QApplication.instance() or QApplication(argv or sys.argv)
    app.setApplicationName(__app_name__)
    app.setApplicationVersion(__version__)
    app.setDesktopFileName("icast")
    app.setWindowIcon(logo_icon())
    app.setStyle("Fusion")
    app.setStyleSheet(theme.STYLESHEET)
    win = MainWindow()
    win.show()
    return app.exec()
