"""Vector icon set (24×24 SVG, stroke-based) rendered with QtSvg in any colour."""

from __future__ import annotations

from functools import lru_cache

from PySide6.QtCore import QByteArray, QRectF, QSize, Qt
from PySide6.QtGui import QIcon, QPainter, QPixmap
from PySide6.QtSvg import QSvgRenderer
from PySide6.QtWidgets import QApplication

_ICONS = {
    "home": '<path d="M3.5 10.6 12 3.5l8.5 7.1V20a1 1 0 0 1-1 1H15v-6.2H9V21H4.5a1 1 0 0 1-1-1z" fill="currentColor" stroke="none"/>',
    "wifi": '<path d="M2.5 8.8a14 14 0 0 1 19 0"/><path d="M5.8 12.3a9.2 9.2 0 0 1 12.4 0"/>'
            '<path d="M9 15.7a4.6 4.6 0 0 1 6 0"/><circle cx="12" cy="19.2" r="1.4" fill="currentColor" stroke="none"/>',
    "cable": '<rect x="4" y="2.5" width="6" height="7.5" rx="1.6"/><path d="M6 2.5V1M8 2.5V1"/>'
             '<path d="M7 10v4.5a5 5 0 0 0 5 5h0a5 5 0 0 0 5-5V13"/><rect x="14" y="5.5" width="6" height="7.5" rx="1.6"/>',
    "history": '<path d="M3.5 12a8.5 8.5 0 1 0 2.6-6.1L3.5 8.4"/><path d="M3.5 3.5v4.9h4.9"/><path d="M12 7.5V12l3.2 2"/>',
    "settings": '<circle cx="12" cy="12" r="6.6" fill="currentColor" stroke="none"/>'
                '<path d="M18 12h3.4M16.2 7.8l2.4-2.4M12 6V2.6M7.8 7.8 5.4 5.4M6 12H2.6M7.8 16.2l-2.4 2.4M12 18v3.4M16.2 16.2l2.4 2.4" stroke-width="3.4" stroke-linecap="round"/>'
                '<circle cx="12" cy="12" r="2.6" fill="#0B0F2E" stroke="none"/>',
    "info": '<circle cx="12" cy="12" r="10" fill="currentColor" stroke="none"/>'
            '<path d="M12 11v6" stroke="#0B0F2E" stroke-width="2.4"/><circle cx="12" cy="7.4" r="1.4" fill="#0B0F2E" stroke="none"/>',
    "bell": '<path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.8 1.8H4.2z"/><path d="M10 21a2 2 0 0 0 4 0"/>',
    "headset": '<path d="M4 14v-2a8 8 0 0 1 16 0v2"/><rect x="3" y="13" width="4" height="6.5" rx="1.6"/>'
               '<rect x="17" y="13" width="4" height="6.5" rx="1.6"/><path d="M19 19.5c0 1.6-2.2 2.5-5 2.5"/>',
    "refresh": '<path d="M20 12a8 8 0 0 1-14.3 4.9"/><path d="M4 12A8 8 0 0 1 18.3 7.1"/>'
               '<path d="M18.6 3v4.4h-4.4"/><path d="M5.4 21v-4.4h4.4"/>',
    "arrow": '<path d="M4.5 12h15M13.5 6l6 6-6 6"/>',
    "bolt": '<path d="M13.5 1.5 4 13.5h7l-1.5 9 10-12.5h-7z" fill="currentColor" stroke="none"/>',
    "shield": '<path d="M12 1.8 3.8 5v6.1c0 5.2 3.5 9.4 8.2 11.1 4.7-1.7 8.2-5.9 8.2-11.1V5z" fill="currentColor" stroke="none"/>'
              '<rect x="9" y="11" width="6" height="5" rx="1" fill="#0B0F2E" stroke="none"/>'
              '<path d="M10.2 11V9.6a1.8 1.8 0 0 1 3.6 0V11" stroke="#0B0F2E" stroke-width="1.6"/>',
    "hd": '<rect x="1.8" y="4.5" width="20.4" height="15" rx="3"/>'
          '<path d="M6.3 8.6v6.8M6.3 12h3.9M10.2 8.6v6.8M13.4 8.6v6.8h1.7a3.4 3.4 0 0 0 0-6.8z" stroke-width="1.9"/>',
    "crown": '<path d="M2.5 7.5 7 11.5l5-7 5 7 4.5-4-2 12h-15z" fill="currentColor" stroke="none"/>'
             '<rect x="4.5" y="20" width="15" height="2" rx="1" fill="currentColor" stroke="none"/>',
    "check": '<circle cx="12" cy="12" r="10.5" fill="currentColor" stroke="none"/>'
             '<path d="M7.2 12.4l3.2 3.2 6.4-6.6" stroke="#0B0F2E" stroke-width="2.6"/>',
    "alert": '<circle cx="12" cy="12" r="10.5" fill="currentColor" stroke="none"/>'
             '<path d="M12 6.8v6.4" stroke="#0B0F2E" stroke-width="2.6"/><circle cx="12" cy="17" r="1.5" fill="#0B0F2E" stroke="none"/>',
    "signal": '<rect x="3" y="15" width="3.2" height="6" rx="1" fill="currentColor" stroke="none"/>'
              '<rect x="8.3" y="11" width="3.2" height="10" rx="1" fill="currentColor" stroke="none"/>'
              '<rect x="13.6" y="7" width="3.2" height="14" rx="1" fill="currentColor" stroke="none"/>'
              '<rect x="18.9" y="3" width="3.2" height="18" rx="1" fill="currentColor" stroke="none"/>',
    "phone": '<rect x="6" y="1.8" width="12" height="20.4" rx="3"/><path d="M10.5 18.6h3"/>',
    "cast": '<path d="M2.5 9V6.5a2.5 2.5 0 0 1 2.5-2.5h14a2.5 2.5 0 0 1 2.5 2.5v11a2.5 2.5 0 0 1-2.5 2.5h-5"/>'
            '<path d="M2.5 13a7.5 7.5 0 0 1 7.5 7.5"/><path d="M2.5 16.6a3.9 3.9 0 0 1 3.9 3.9"/>'
            '<circle cx="3" cy="20" r="1.2" fill="currentColor" stroke="none"/>',
    "chevron": '<path d="M6 9l6 6 6-6"/>',
    "min": '<path d="M5 12h14"/>',
    "max": '<rect x="5" y="5" width="14" height="14" rx="1.5"/>',
    "restore": '<rect x="4" y="8" width="12" height="12" rx="1.5"/><path d="M8 8V5.5A1.5 1.5 0 0 1 9.5 4H18.5A1.5 1.5 0 0 1 20 5.5v9a1.5 1.5 0 0 1-1.5 1.5H16"/>',
    "close": '<path d="M6 6l12 12M18 6 6 18"/>',
    "play": '<path d="M7 4.5v15l13-7.5z" fill="currentColor" stroke="none"/>',
    "stop": '<rect x="5.5" y="5.5" width="13" height="13" rx="2.5" fill="currentColor" stroke="none"/>',
    "folder": '<path d="M3 6.5A1.5 1.5 0 0 1 4.5 5h4.6l2 2.2h8.4A1.5 1.5 0 0 1 21 8.7v9.8a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 18.5z"/>',
    "trash": '<path d="M4 7h16M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13"/>',
}


def svg_for(name: str, color: str, stroke: float = 2.0) -> bytes:
    body = _ICONS[name].replace("currentColor", color)
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="{color}" '
        f'stroke-width="{stroke}" stroke-linecap="round" stroke-linejoin="round">{body}</svg>'
    ).encode()


@lru_cache(maxsize=512)
def _renderer(name: str, color: str, stroke: float) -> QSvgRenderer:
    return QSvgRenderer(QByteArray(svg_for(name, color, stroke)))


def render_icon(painter: QPainter, name: str, rect: QRectF, color: str = "#FFFFFF", stroke: float = 2.0) -> None:
    _renderer(name, color, stroke).render(painter, QRectF(rect))


def icon_pixmap(name: str, size: int, color: str = "#FFFFFF", stroke: float = 2.0) -> QPixmap:
    app = QApplication.instance()
    dpr = app.devicePixelRatio() if app else 1.0
    pix = QPixmap(QSize(int(size * dpr), int(size * dpr)))
    pix.fill(Qt.transparent)
    p = QPainter(pix)
    p.setRenderHint(QPainter.Antialiasing)
    render_icon(p, name, QRectF(0, 0, size * dpr, size * dpr), color, stroke)
    p.end()
    pix.setDevicePixelRatio(dpr)
    return pix


def icon(name: str, size: int = 24, color: str = "#FFFFFF", stroke: float = 2.0) -> QIcon:
    return QIcon(icon_pixmap(name, size, color, stroke))


NAMES = tuple(_ICONS)
