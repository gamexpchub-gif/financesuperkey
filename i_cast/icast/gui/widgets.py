"""Custom-painted widgets that make up the iCast look."""

from __future__ import annotations

import math

from PySide6.QtCore import QPointF, QRectF, QSize, Qt, Signal
from PySide6.QtGui import (
    QBrush, QColor, QFont, QFontMetrics, QIcon, QLinearGradient, QPainter, QPainterPath, QPen, QPixmap,
    QRadialGradient,
)
from PySide6.QtWidgets import (
    QFrame, QGraphicsDropShadowEffect, QHBoxLayout, QLabel, QPushButton, QSizePolicy, QVBoxLayout, QWidget,
)

from icast.gui import theme
from icast.gui.icons import icon_pixmap, render_icon

AA = QPainter.Antialiasing


def qcolor(c, alpha: int | None = None) -> QColor:
    col = QColor(*c) if isinstance(c, tuple) else QColor(c)
    if alpha is not None:
        col.setAlpha(alpha)
    return col


def hgrad(rect: QRectF, colors, vertical: bool = False, diagonal: bool = False) -> QLinearGradient:
    end = rect.bottomRight() if diagonal else (rect.bottomLeft() if vertical else rect.topRight())
    g = QLinearGradient(rect.topLeft(), end)
    n = len(colors)
    for i, c in enumerate(colors):
        g.setColorAt(i / (n - 1) if n > 1 else 0, qcolor(c))
    return g


def add_glow(widget: QWidget, color, blur: int = 40, dy: int = 8, alpha: int = 150) -> None:
    eff = QGraphicsDropShadowEffect(widget)
    eff.setColor(qcolor(color, alpha))
    eff.setBlurRadius(blur)
    eff.setOffset(0, dy)
    widget.setGraphicsEffect(eff)


def text_label(text: str, px: int, weight=QFont.Normal, color: str = theme.TEXT, wrap: bool = True) -> QLabel:
    lbl = QLabel(text)
    lbl.setFont(theme.font(px, weight))
    lbl.setStyleSheet(f"color: {color}; background: transparent;")
    lbl.setWordWrap(wrap)
    return lbl


def soft_glow(p: QPainter, center: QPointF, radius: float, color, alpha: int) -> None:
    g = QRadialGradient(center, radius)
    g.setColorAt(0, qcolor(color, alpha))
    g.setColorAt(1, qcolor(color, 0))
    p.setPen(Qt.NoPen)
    p.setBrush(g)
    p.drawEllipse(center, radius, radius)


# --------------------------------------------------------------------- logo
def paint_logo(p: QPainter, r: QRectF, glow: bool = True) -> None:
    p.save()
    p.setRenderHint(AA)
    if glow:
        soft_glow(p, r.center(), r.width() * 0.75, theme.VIOLET, 110)
    rad = r.width() * 0.26
    body = QPainterPath()
    body.addRoundedRect(r, rad, rad)
    p.fillPath(body, hgrad(r, ("#1E8BFF", "#3A5BFF", "#8A3DFF"), diagonal=True))
    sheen = QLinearGradient(r.topLeft(), r.bottomLeft())
    sheen.setColorAt(0, QColor(255, 255, 255, 70))
    sheen.setColorAt(0.55, QColor(255, 255, 255, 0))
    p.fillPath(body, sheen)
    p.setPen(QPen(QColor(255, 255, 255, 90), max(1.0, r.width() * 0.012)))
    p.setBrush(Qt.NoBrush)
    p.drawPath(body)

    w, x, y = r.width(), r.x(), r.y()
    pen = QPen(Qt.white, w * 0.075, Qt.SolidLine, Qt.RoundCap, Qt.RoundJoin)
    p.setPen(pen)
    frame = QPainterPath()
    frame.moveTo(x + w * 0.52, y + w * 0.24)
    frame.lineTo(x + w * 0.30, y + w * 0.24)
    frame.quadTo(x + w * 0.22, y + w * 0.24, x + w * 0.22, y + w * 0.32)
    frame.lineTo(x + w * 0.22, y + w * 0.68)
    frame.quadTo(x + w * 0.22, y + w * 0.76, x + w * 0.30, y + w * 0.76)
    frame.lineTo(x + w * 0.52, y + w * 0.76)
    p.drawPath(frame)
    tri = QPainterPath()
    tri.moveTo(x + w * 0.38, y + w * 0.36)
    tri.lineTo(x + w * 0.38, y + w * 0.64)
    tri.lineTo(x + w * 0.62, y + w * 0.50)
    tri.closeSubpath()
    p.setPen(QPen(Qt.white, w * 0.03, Qt.SolidLine, Qt.RoundCap, Qt.RoundJoin))
    p.setBrush(hgrad(QRectF(x + w * 0.38, y + w * 0.36, w * 0.24, w * 0.28), (theme.CYAN, "#C9F2FF"), vertical=True))
    p.drawPath(tri)
    p.setBrush(Qt.NoBrush)
    for rr in (0.16, 0.27):
        p.setPen(QPen(Qt.white, w * 0.065, Qt.SolidLine, Qt.RoundCap))
        arc = QRectF(x + w * 0.5 - w * rr, y + w * 0.5 - w * rr, 2 * w * rr, 2 * w * rr)
        p.drawArc(arc, int(-50 * 16), int(100 * 16))
    p.restore()


def logo_pixmap(size: int) -> QPixmap:
    pix = QPixmap(size * 2, size * 2)
    pix.fill(Qt.transparent)
    p = QPainter(pix)
    paint_logo(p, QRectF(size * 0.06, size * 0.06, size * 1.88, size * 1.88), glow=False)
    p.end()
    pix.setDevicePixelRatio(2)
    return pix


def logo_icon() -> QIcon:
    ic = QIcon()
    for s in (16, 32, 64, 128, 256):
        ic.addPixmap(logo_pixmap(s))
    return ic


class LogoMark(QWidget):
    def __init__(self, size: int, glow: bool = True, glow_pad: float = 0.35) -> None:
        super().__init__()
        self._glow = glow
        pad = int(size * glow_pad) if glow else 0
        self._pad = pad
        self.setFixedSize(size + 2 * pad, size + 2 * pad)

    def paintEvent(self, _e) -> None:
        p = QPainter(self)
        s = self.width() - 2 * self._pad
        paint_logo(p, QRectF(self._pad, self._pad, s, s), glow=self._glow)


# ----------------------------------------------------------------- surfaces
class GlassCard(QFrame):
    def __init__(self, radius: int = 20, fill=theme.GLASS_FILL, border=theme.GLASS_BORDER) -> None:
        super().__init__()
        self.radius, self.fill, self.border = radius, fill, border
        self.setAttribute(Qt.WA_StyledBackground, False)

    def paintEvent(self, _e) -> None:
        p = QPainter(self)
        p.setRenderHint(AA)
        r = QRectF(self.rect()).adjusted(0.5, 0.5, -0.5, -0.5)
        path = QPainterPath()
        path.addRoundedRect(r, self.radius, self.radius)
        p.fillPath(path, qcolor(self.fill))
        top = QLinearGradient(r.topLeft(), r.bottomLeft())
        top.setColorAt(0, QColor(255, 255, 255, 14))
        top.setColorAt(0.4, QColor(255, 255, 255, 0))
        p.fillPath(path, top)
        p.setPen(QPen(qcolor(self.border), 1))
        p.drawPath(path)


def card_layout(card: QWidget, margins=(26, 22, 26, 22), spacing: int = 14) -> QVBoxLayout:
    lay = QVBoxLayout(card)
    lay.setContentsMargins(*margins)
    lay.setSpacing(spacing)
    return lay


# ------------------------------------------------------------------ buttons
class GradientButton(QPushButton):
    def __init__(self, text: str, colors=theme.GRAD_PRIMARY, arrow: bool = True, height: int = 58,
                 font_px: int = 20, leading_icon: str | None = None, glow: bool = True) -> None:
        super().__init__(text)
        self.colors, self.arrow, self.leading_icon = tuple(colors), arrow, leading_icon
        self.setAttribute(Qt.WA_Hover)
        self.setCursor(Qt.PointingHandCursor)
        self.setFont(theme.font(font_px, QFont.DemiBold))
        self.setFixedHeight(height)
        self._glow = glow
        if glow:
            add_glow(self, self.colors[-1], blur=38, dy=6, alpha=120)
        self._fit()

    def set_colors(self, colors) -> None:
        self.colors = tuple(colors)
        if self._glow and self.graphicsEffect():
            self.graphicsEffect().setColor(qcolor(self.colors[-1], 120))
        self.update()

    def setText(self, text: str) -> None:  # noqa: N802
        super().setText(text)
        self._fit()

    def _fit(self) -> None:
        fm = QFontMetrics(self.font())
        extra = (self.height() * 0.5 if self.arrow else 0) + (self.height() * 0.5 if self.leading_icon else 0)
        self.setMinimumWidth(int(fm.horizontalAdvance(self.text()) + extra + self.height() * 1.1))

    def sizeHint(self) -> QSize:
        return QSize(self.minimumWidth(), self.height())

    def paintEvent(self, _e) -> None:
        p = QPainter(self)
        p.setRenderHint(AA)
        r = QRectF(self.rect()).adjusted(1, 1, -1, -1)
        rad = r.height() / 2
        path = QPainterPath()
        path.addRoundedRect(r, rad, rad)
        p.fillPath(path, hgrad(r, self.colors))
        sheen = QLinearGradient(r.topLeft(), r.bottomLeft())
        hover = self.underMouse() and self.isEnabled()
        sheen.setColorAt(0, QColor(255, 255, 255, 75 if hover else 45))
        sheen.setColorAt(0.55, QColor(255, 255, 255, 0))
        p.fillPath(path, sheen)
        p.setPen(QPen(QColor(255, 255, 255, 60), 1))
        p.drawPath(path)
        if self.isDown():
            p.fillPath(path, QColor(0, 0, 0, 45))
        if not self.isEnabled():
            p.fillPath(path, QColor(10, 12, 30, 140))

        fm = QFontMetrics(self.font())
        tw = fm.horizontalAdvance(self.text())
        isz = r.height() * 0.40
        gap = r.height() * 0.2
        total = tw + (gap + isz if self.arrow else 0) + (gap + isz if self.leading_icon else 0)
        x = r.left() + (r.width() - total) / 2
        cy = r.center().y()
        if self.leading_icon:
            render_icon(p, self.leading_icon, QRectF(x, cy - isz / 2, isz, isz), "#FFFFFF", 2.2)
            x += isz + gap
        p.setPen(Qt.white)
        p.setFont(self.font())
        p.drawText(QRectF(x, r.top(), tw + 4, r.height()), Qt.AlignVCenter | Qt.AlignLeft, self.text())
        if self.arrow:
            render_icon(p, "arrow", QRectF(x + tw + gap, cy - isz / 2, isz, isz), "#FFFFFF", 2.4)


class OutlineButton(QPushButton):
    def __init__(self, text: str, icon_name: str | None = None, height: int = 50, font_px: int = 17,
                 colors=("#4C6BFF", "#B14BFF")) -> None:
        super().__init__(text)
        self.icon_name, self.colors = icon_name, colors
        self.setAttribute(Qt.WA_Hover)
        self.setCursor(Qt.PointingHandCursor)
        self.setFont(theme.font(font_px, QFont.Medium))
        self.setFixedHeight(height)
        fm = QFontMetrics(self.font())
        self.setMinimumWidth(fm.horizontalAdvance(text) + (height if icon_name else 0) + 52)

    def paintEvent(self, _e) -> None:
        p = QPainter(self)
        p.setRenderHint(AA)
        r = QRectF(self.rect()).adjusted(1.5, 1.5, -1.5, -1.5)
        path = QPainterPath()
        path.addRoundedRect(r, r.height() / 2, r.height() / 2)
        p.fillPath(path, QColor(255, 255, 255, 26 if self.underMouse() else 8))
        p.setPen(QPen(QBrush(hgrad(r, self.colors)), 1.8))
        p.drawPath(path)
        fm = QFontMetrics(self.font())
        tw = fm.horizontalAdvance(self.text())
        isz = r.height() * 0.46 if self.icon_name else 0
        gap = 12 if self.icon_name else 0
        x = r.left() + (r.width() - tw - isz - gap) / 2
        if self.icon_name:
            render_icon(p, self.icon_name, QRectF(x, r.center().y() - isz / 2, isz, isz), "#FFFFFF", 2.1)
        p.setPen(Qt.white)
        p.setFont(self.font())
        p.drawText(QRectF(x + isz + gap, r.top(), tw + 4, r.height()), Qt.AlignVCenter | Qt.AlignLeft, self.text())


class IconButton(QPushButton):
    def __init__(self, icon_name: str, size: int = 50, tooltip: str = "") -> None:
        super().__init__()
        self.icon_name = icon_name
        self.badge = False
        self.setFixedSize(size, size)
        self.setAttribute(Qt.WA_Hover)
        self.setCursor(Qt.PointingHandCursor)
        self.setToolTip(tooltip)

    def set_badge(self, on: bool) -> None:
        self.badge = on
        self.update()

    def paintEvent(self, _e) -> None:
        p = QPainter(self)
        p.setRenderHint(AA)
        r = QRectF(self.rect()).adjusted(1, 1, -1, -1)
        p.setPen(QPen(QColor(255, 255, 255, 40), 1))
        p.setBrush(QColor(255, 255, 255, 34 if self.underMouse() else 14))
        p.drawEllipse(r)
        s = r.width() * 0.46
        render_icon(p, self.icon_name, QRectF(r.center().x() - s / 2, r.center().y() - s / 2, s, s), "#FFFFFF", 2.0)
        if self.badge:
            p.setPen(QPen(QColor(theme.NAVY), 2))
            p.setBrush(QColor(theme.RED))
            p.drawEllipse(QPointF(r.right() - r.width() * 0.24, r.top() + r.height() * 0.24), 5.5, 5.5)


class WindowButton(QPushButton):
    def __init__(self, icon_name: str, danger: bool = False) -> None:
        super().__init__()
        self.icon_name, self.danger = icon_name, danger
        self.setFixedSize(46, 34)
        self.setAttribute(Qt.WA_Hover)

    def paintEvent(self, _e) -> None:
        p = QPainter(self)
        p.setRenderHint(AA)
        if self.underMouse():
            p.setPen(Qt.NoPen)
            p.setBrush(QColor(theme.RED) if self.danger else QColor(255, 255, 255, 28))
            p.drawRoundedRect(QRectF(self.rect()).adjusted(2, 2, -2, -2), 8, 8)
        s = 18
        render_icon(p, self.icon_name, QRectF((self.width() - s) / 2, (self.height() - s) / 2, s, s), "#FFFFFF", 1.8)


class NavButton(QPushButton):
    def __init__(self, text: str, icon_name: str) -> None:
        super().__init__(text)
        self.icon_name = icon_name
        self.setCheckable(True)
        self.setAttribute(Qt.WA_Hover)
        self.setCursor(Qt.PointingHandCursor)
        self.setFixedHeight(54)
        self.setFont(theme.font(17, QFont.Medium))

    def paintEvent(self, _e) -> None:
        p = QPainter(self)
        p.setRenderHint(AA)
        r = QRectF(self.rect()).adjusted(2, 2, -2, -2)
        path = QPainterPath()
        path.addRoundedRect(r, 13, 13)
        if self.isChecked():
            for i, a in ((6, 18), (3, 30)):
                glow = QPainterPath()
                glow.addRoundedRect(r.adjusted(-i / 2, -i / 2, i / 2, i / 2), 15, 15)
                p.setPen(QPen(QColor(61, 125, 255, a), i))
                p.drawPath(glow)
            p.fillPath(path, hgrad(r, ("#2A63FF", "#3C7BFF", "#2E5EF0")))
            sheen = QLinearGradient(r.topLeft(), r.bottomLeft())
            sheen.setColorAt(0, QColor(255, 255, 255, 50))
            sheen.setColorAt(0.6, QColor(255, 255, 255, 0))
            p.fillPath(path, sheen)
            p.setPen(QPen(QColor(150, 190, 255, 150), 1))
            p.drawPath(path)
        elif self.underMouse():
            p.fillPath(path, QColor(255, 255, 255, 16))
        s = 26
        render_icon(p, self.icon_name, QRectF(r.left() + 18, r.center().y() - s / 2, s, s), "#FFFFFF", 2.0)
        p.setPen(QColor(theme.TEXT))
        p.setFont(self.font())
        p.drawText(r.adjusted(62, 0, 0, 0), Qt.AlignVCenter | Qt.AlignLeft, self.text())


class PillButton(QPushButton):
    """Rounded glass dropdown button (e.g. the device picker)."""

    def __init__(self, text: str, icon_name: str = "phone") -> None:
        super().__init__(text)
        self.icon_name = icon_name
        self.setAttribute(Qt.WA_Hover)
        self.setCursor(Qt.PointingHandCursor)
        self.setFixedHeight(50)
        self.setFont(theme.font(17, QFont.Medium))
        self._fit()

    def setText(self, text: str) -> None:  # noqa: N802
        super().setText(text)
        self._fit()

    def _fit(self) -> None:
        self.setFixedWidth(QFontMetrics(self.font()).horizontalAdvance(self.text()) + 104)

    def paintEvent(self, _e) -> None:
        p = QPainter(self)
        p.setRenderHint(AA)
        r = QRectF(self.rect()).adjusted(1, 1, -1, -1)
        path = QPainterPath()
        path.addRoundedRect(r, r.height() / 2, r.height() / 2)
        p.fillPath(path, QColor(255, 255, 255, 30 if self.underMouse() else 14))
        p.setPen(QPen(QColor(255, 255, 255, 40), 1))
        p.drawPath(path)
        render_icon(p, self.icon_name, QRectF(r.left() + 20, r.center().y() - 12, 24, 24), "#FFFFFF", 2.0)
        p.setFont(self.font())
        p.setPen(Qt.white)
        p.drawText(r.adjusted(56, 0, -40, 0), Qt.AlignVCenter | Qt.AlignLeft, self.text())
        render_icon(p, "chevron", QRectF(r.right() - 38, r.center().y() - 9, 18, 18), "#FFFFFF", 2.2)


# --------------------------------------------------------------- indicators
class StatusPill(QWidget):
    def __init__(self) -> None:
        super().__init__()
        self.text, self.color = "Ready to Cast", theme.GREEN
        self.setFont(theme.font(17, QFont.Medium))
        self.setFixedHeight(50)
        self._fit()

    def set_state(self, text: str, color: str) -> None:
        self.text, self.color = text, color
        self._fit()
        self.update()

    def _fit(self) -> None:
        self.setFixedWidth(QFontMetrics(self.font()).horizontalAdvance(self.text) + 82)

    def paintEvent(self, _e) -> None:
        p = QPainter(self)
        p.setRenderHint(AA)
        r = QRectF(self.rect()).adjusted(1, 1, -1, -1)
        path = QPainterPath()
        path.addRoundedRect(r, r.height() / 2, r.height() / 2)
        p.fillPath(path, QColor(255, 255, 255, 16))
        p.setPen(QPen(QColor(255, 255, 255, 34), 1))
        p.drawPath(path)
        c = QPointF(r.left() + 28, r.center().y())
        soft_glow(p, c, 18, self.color, 150)
        p.setBrush(QColor(self.color))
        p.setPen(QPen(QColor(255, 255, 255, 120), 1.5))
        p.drawEllipse(c, 8, 8)
        p.setPen(Qt.white)
        p.setFont(self.font())
        p.drawText(r.adjusted(52, 0, 0, 0), Qt.AlignVCenter | Qt.AlignLeft, self.text)


class GradientTitle(QWidget):
    def __init__(self, text: str, px: int = 112, colors=("#FFFFFF", "#D8F4FF", "#7FD8FF", "#B48BFF", "#C46BFF")) -> None:
        super().__init__()
        self.text, self.colors = text, colors
        self.setFont(theme.font(px, QFont.Black))
        fm = QFontMetrics(self.font())
        self.setFixedHeight(int(fm.ascent() * 1.04))
        self.setMinimumWidth(fm.horizontalAdvance(text) + 20)

    def paintEvent(self, _e) -> None:
        p = QPainter(self)
        p.setRenderHint(AA)
        fm = QFontMetrics(self.font())
        path = QPainterPath()
        path.addText(QPointF(2, fm.ascent() * 0.98), self.font(), self.text)
        br = path.boundingRect()
        for w, a in ((14, 22), (7, 36)):
            p.strokePath(path, QPen(QColor(110, 120, 255, a), w, Qt.SolidLine, Qt.RoundCap, Qt.RoundJoin))
        g = QLinearGradient(br.topLeft(), QPointF(br.right(), br.bottom()))
        for i, c in enumerate(self.colors):
            g.setColorAt(i / (len(self.colors) - 1), QColor(c))
        p.fillPath(path, g)


class FeatureChip(GlassCard):
    def __init__(self, icon_name: str, text: str, icon_color: str) -> None:
        super().__init__(radius=14, fill=(255, 255, 255, 12), border=(255, 255, 255, 28))
        lay = QHBoxLayout(self)
        lay.setContentsMargins(14, 10, 18, 10)
        lay.setSpacing(12)
        ic = QLabel()
        ic.setPixmap(icon_pixmap(icon_name, 40, icon_color, 2.0))
        self.label = text_label(text, 17, QFont.Medium, wrap=False)
        lay.addWidget(ic)
        lay.addWidget(self.label)
        self.setFixedHeight(66)


class VSeparator(QWidget):
    def __init__(self) -> None:
        super().__init__()
        self.setFixedWidth(1)
        self.setStyleSheet("background: rgba(255,255,255,0.14);")
        self.setAttribute(Qt.WA_StyledBackground, True)


# ----------------------------------------------------------- illustrations
def _phone(p: QPainter, center: QPointF, h: float, angle: float, screen_colors, alpha: int = 255,
           outline_only: bool = False) -> None:
    w = h * 0.49
    p.save()
    p.translate(center)
    p.rotate(angle)
    body = QRectF(-w / 2, -h / 2, w, h)
    rad = w * 0.17
    path = QPainterPath()
    path.addRoundedRect(body, rad, rad)
    if outline_only:
        p.setPen(QPen(QColor(150, 200, 255, alpha), max(2.0, h * 0.018)))
        p.setBrush(QColor(10, 20, 60, int(alpha * 0.35)))
        p.drawPath(path)
        inner = body.adjusted(w * 0.07, w * 0.07, -w * 0.07, -w * 0.07)
        ip = QPainterPath()
        ip.addRoundedRect(inner, rad * 0.75, rad * 0.75)
        p.setPen(QPen(QColor(150, 200, 255, int(alpha * 0.55)), max(1.0, h * 0.008)))
        p.setBrush(Qt.NoBrush)
        p.drawPath(ip)
        s = w * 0.42
        p.setOpacity(alpha / 255)
        render_icon(p, "cast", QRectF(-s / 2, -s / 2, s, s), "#DCEBFF", 2.0)
        p.restore()
        return
    frame = QLinearGradient(body.topLeft(), body.bottomRight())
    frame.setColorAt(0, QColor("#E3E8FF"))
    frame.setColorAt(0.35, QColor("#6C76C8"))
    frame.setColorAt(0.7, QColor("#C8CFFF"))
    frame.setColorAt(1, QColor("#3A3F7A"))
    p.setPen(QPen(QBrush(frame), max(2.5, h * 0.012)))
    p.setBrush(QColor("#04050D"))
    p.drawPath(path)
    screen = body.adjusted(w * 0.055, w * 0.055, -w * 0.055, -w * 0.055)
    sp = QPainterPath()
    sp.addRoundedRect(screen, rad * 0.8, rad * 0.8)
    p.fillPath(sp, hgrad(screen, screen_colors, diagonal=True))
    p.save()
    p.setClipPath(sp)
    for i, a in enumerate((70, 45, 30)):
        wave = QPainterPath()
        y0 = screen.top() + screen.height() * (0.25 + i * 0.22)
        wave.moveTo(screen.left() - 10, y0)
        wave.cubicTo(screen.left() + screen.width() * 0.4, y0 - screen.height() * 0.25,
                     screen.left() + screen.width() * 0.6, y0 + screen.height() * 0.35,
                     screen.right() + 10, y0 + screen.height() * 0.05)
        p.setPen(QPen(QColor(255, 255, 255, a), screen.width() * (0.05 + i * 0.03)))
        p.setBrush(Qt.NoBrush)
        p.drawPath(wave)
    soft_glow(p, QPointF(screen.center().x(), screen.top() + screen.height() * 0.3), screen.width() * 0.9, "#FFFFFF", 40)
    p.restore()
    p.setPen(Qt.NoPen)
    p.setBrush(QColor("#000000"))
    p.drawRoundedRect(QRectF(-w * 0.17, screen.top() + w * 0.05, w * 0.34, w * 0.09), w * 0.045, w * 0.045)
    p.setBrush(QColor(255, 255, 255, 200))
    p.drawRoundedRect(QRectF(-w * 0.18, screen.bottom() - w * 0.08, w * 0.36, w * 0.025), w * 0.0125, w * 0.0125)
    s = w * 0.5
    render_icon(p, "cast", QRectF(-s / 2, -s * 0.55, s, s), "#FFFFFF", 2.1)
    # side buttons
    p.setBrush(QColor("#8A93D8"))
    p.drawRoundedRect(QRectF(body.right() - 1, -h * 0.22, 4, h * 0.12), 2, 2)
    p.restore()


class HeroArt(QWidget):
    """Phone casting to a monitor: the right side of the home hero."""

    def __init__(self) -> None:
        super().__init__()
        self.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
        self.setMinimumSize(420, 320)
        self.live = False

    def set_live(self, live: bool) -> None:
        self.live = live
        self.update()

    def paintEvent(self, _e) -> None:
        p = QPainter(self)
        p.setRenderHint(AA)
        p.setRenderHint(QPainter.SmoothPixmapTransform)
        W, H = self.width(), self.height()
        # light streaks, faded at both ends so the widget edge never shows
        for i, (col, a, wdt) in enumerate(((theme.MAGENTA, 110, 3), (theme.BLUE_2, 120, 2), (theme.CYAN, 90, 1.5),
                                           (theme.PURPLE, 60, 7))):
            path = QPainterPath()
            y = H * (0.80 + i * 0.04)
            path.moveTo(W * 0.02, y + 20)
            path.cubicTo(W * 0.3, y - H * 0.25, W * 0.6, y + H * 0.15, W * 0.98, y - H * (0.35 + i * 0.05))
            fade = QLinearGradient(QPointF(0, 0), QPointF(W, 0))
            fade.setColorAt(0, qcolor(col, 0))
            fade.setColorAt(0.35, qcolor(col, a))
            fade.setColorAt(0.8, qcolor(col, a))
            fade.setColorAt(1, qcolor(col, 0))
            p.setPen(QPen(QBrush(fade), wdt))
            p.setBrush(Qt.NoBrush)
            p.drawPath(path)

        # monitor
        mw, mh = W * 0.60, H * 0.56
        mx, my = W * 0.38, H * 0.10
        p.save()
        p.translate(mx, my)
        p.shear(0, -0.03)
        frame = QRectF(0, 0, mw, mh)
        fp = QPainterPath()
        fp.addRoundedRect(frame, 14, 14)
        p.setPen(QPen(QColor(120, 140, 230, 160), 2))
        p.setBrush(QColor("#080B1E"))
        p.drawPath(fp)
        screen = frame.adjusted(10, 10, -10, -10)
        sp = QPainterPath()
        sp.addRoundedRect(screen, 6, 6)
        p.fillPath(sp, hgrad(screen, ("#0A1E8C", "#1550FF", "#2A6BFF", "#6B2BE8"), diagonal=True))
        p.save()
        p.setClipPath(sp)
        for i in range(5):
            wave = QPainterPath()
            y0 = screen.top() + screen.height() * (0.15 + i * 0.18)
            wave.moveTo(screen.left() - 20, y0 + 40)
            wave.cubicTo(screen.left() + screen.width() * 0.35, y0 - screen.height() * 0.35,
                         screen.left() + screen.width() * 0.65, y0 + screen.height() * 0.40,
                         screen.right() + 20, y0 - 10)
            p.setPen(QPen(QColor(255, 255, 255, 10 + i * 4), screen.height() * (0.05 + i * 0.02)))
            p.drawPath(wave)
        soft_glow(p, QPointF(screen.center().x(), screen.center().y()), screen.width() * 0.5, "#FFFFFF", 45)
        p.restore()
        s = min(screen.width(), screen.height()) * 0.42
        render_icon(p, "cast", QRectF(screen.center().x() - s / 2, screen.center().y() - s / 2, s, s), "#FFFFFF", 2.0)
        # brand + stand
        p.setPen(QColor(200, 210, 255, 120))
        p.setFont(theme.font(9, QFont.DemiBold))
        p.drawText(QRectF(0, frame.bottom() - 10, mw, 10), Qt.AlignCenter, "iCast")
        p.restore()
        neck = QPainterPath()
        bx = mx + mw * 0.52
        by = my + mh - mw * 0.03 * 0.5
        neck.moveTo(bx - mw * 0.05, by)
        neck.lineTo(bx + mw * 0.05, by)
        neck.lineTo(bx + mw * 0.08, by + H * 0.14)
        neck.lineTo(bx - mw * 0.08, by + H * 0.14)
        neck.closeSubpath()
        p.fillPath(neck, hgrad(neck.boundingRect(), ("#2A2F5C", "#141838"), vertical=True))
        base = QRectF(bx - mw * 0.24, by + H * 0.125, mw * 0.48, H * 0.05)
        p.setPen(QPen(QColor(140, 160, 255, 90), 1))
        p.setBrush(hgrad(base, ("#232850", "#0D1030"), vertical=True))
        p.drawEllipse(base)

        # phone + glow ring
        ph = H * 0.80
        pc = QPointF(W * 0.33, H * 0.47)
        ring = QRectF(pc.x() - ph * 0.56, pc.y() + ph * 0.10, ph * 1.12, ph * 0.28)
        floor = QRectF(pc.x() - ph * 0.55, pc.y() + ph * 0.42, ph * 1.4, ph * 0.12)
        p.setPen(QPen(QColor(120, 140, 255, 70), 1.5))
        p.setBrush(QColor(40, 50, 140, 60))
        p.drawEllipse(floor)
        self._ring(p, ring, back=True)
        _phone(p, pc, ph, 11, ("#2B44FF", "#5A2BFF", "#9B35F5", "#2B6CFF"))
        self._ring(p, ring, back=False)

        # "Only for iPhone"
        p.setFont(theme.font(20, QFont.Medium, italic=True))
        txt = "Only for iPhone"
        tw = QFontMetrics(p.font()).horizontalAdvance(txt)
        tx, ty = W - tw - 14, H - 26
        render_icon(p, "phone", QRectF(tx - 32, ty - 13, 24, 24), "#FFFFFF", 2.0)
        p.setPen(Qt.white)
        p.drawText(QPointF(tx, ty + 7), txt)

    def _ring(self, p: QPainter, r: QRectF, back: bool) -> None:
        start, span = (0, 180) if back else (180, 180)
        g = QLinearGradient(r.topLeft(), r.topRight())
        g.setColorAt(0, QColor(theme.CYAN))
        g.setColorAt(0.5, QColor("#7FE3FF"))
        g.setColorAt(1, QColor(theme.PURPLE))
        p.setBrush(Qt.NoBrush)
        for wdt, a in ((14, 0.12), (8, 0.25), (3.5, 1.0)):
            p.setOpacity(a)
            p.setPen(QPen(QBrush(g), wdt, Qt.SolidLine, Qt.RoundCap))
            p.drawArc(r, start * 16, span * 16)
        p.setOpacity(1)


class ModeCard(QWidget):
    """Big gradient call-to-action card (Wireless / Wired)."""

    def __init__(self, kind: str, title: str, desc: str, button: str) -> None:
        super().__init__()
        self.kind = kind
        self.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Fixed)
        self.setFixedHeight(240)
        colors = theme.GRAD_WIRELESS if kind == "wireless" else theme.GRAD_WIRED
        lay = QVBoxLayout(self)
        lay.setContentsMargins(196, 34, 150, 26)
        lay.setSpacing(6)
        self.title = text_label(title, 30, QFont.Bold)
        self.desc = text_label(desc, 19, QFont.Normal, theme.TEXT_2)
        self.button = GradientButton(button, colors, height=54, font_px=19)
        lay.addWidget(self.title)
        lay.addWidget(self.desc)
        lay.addStretch(1)
        lay.addWidget(self.button, 0, Qt.AlignLeft)
        self.active = False

    def set_active(self, active: bool, label: str) -> None:
        self.active = active
        self.button.setText(label)
        self.button.set_colors(theme.GRAD_STOP if active else
                               (theme.GRAD_WIRELESS if self.kind == "wireless" else theme.GRAD_WIRED))
        self.update()

    def paintEvent(self, _e) -> None:
        p = QPainter(self)
        p.setRenderHint(AA)
        r = QRectF(self.rect()).adjusted(1, 1, -1, -1)
        path = QPainterPath()
        path.addRoundedRect(r, 24, 24)
        wireless = self.kind == "wireless"
        bg = ("#0A1C6E", "#1238C8", "#1F5BFF") if wireless else ("#2A0E78", "#5520C8", "#8A3DFF")
        p.fillPath(path, hgrad(r, bg, diagonal=True))
        p.save()
        p.setClipPath(path)
        soft_glow(p, QPointF(r.right() - r.width() * 0.15, r.top()), r.width() * 0.45,
                  "#6FB6FF" if wireless else "#D08BFF", 90)
        for i in range(4):
            wave = QPainterPath()
            y = r.bottom() - r.height() * (0.10 + i * 0.12)
            wave.moveTo(r.left() + r.width() * 0.35, r.bottom() + 10)
            wave.cubicTo(r.left() + r.width() * 0.6, y, r.left() + r.width() * 0.8, y + 40, r.right() + 10, y - 50)
            p.setPen(QPen(QColor(255, 255, 255, 18 + i * 8), 2 + i * 3))
            p.drawPath(wave)
        _phone(p, QPointF(r.right() - 80, r.center().y() + 18), r.height() * 0.92, 16, (), alpha=210,
               outline_only=True)
        p.restore()
        border = QLinearGradient(r.topLeft(), r.bottomRight())
        border.setColorAt(0, QColor(140, 200, 255, 180) if wireless else QColor(220, 160, 255, 180))
        border.setColorAt(1, QColor(255, 255, 255, 30))
        p.setPen(QPen(QBrush(border), 1.5))
        p.drawPath(path)
        if self.active:
            p.setPen(QPen(QColor(theme.GREEN), 2.5))
            p.drawPath(path)

        # icon disc
        c = QPointF(r.left() + 98, r.top() + r.height() * 0.42)
        rad = 64
        disc = QRadialGradient(c, rad)
        disc.setColorAt(0, QColor(255, 255, 255, 30))
        disc.setColorAt(1, QColor(255, 255, 255, 6))
        p.setBrush(disc)
        p.setPen(QPen(QColor(255, 255, 255, 40), 1))
        p.drawEllipse(c, rad, rad)
        if wireless:
            soft_glow(p, c, rad * 0.9, theme.CYAN, 90)
            s = rad * 1.35
            render_icon(p, "wifi", QRectF(c.x() - s / 2, c.y() - s / 2, s, s), "#3FD6FF", 2.6)
        else:
            soft_glow(p, c, rad * 0.9, "#E6B8FF", 70)
            self._plugs(p, c, rad)

    def _plugs(self, p: QPainter, c: QPointF, rad: float) -> None:
        for dx, ang, bend in ((-0.30, -14, 0.9), (0.26, 12, 0.45)):
            p.save()
            p.translate(c.x() + dx * rad, c.y() - rad * 0.05)
            p.rotate(ang)
            p.setPen(QPen(QColor("#EAE4FF"), rad * 0.13, Qt.SolidLine, Qt.RoundCap))
            p.setBrush(Qt.NoBrush)
            cable = QPainterPath()
            cable.moveTo(0, rad * 0.10)
            cable.cubicTo(0, rad * 0.55, -rad * bend * 0.3, rad * 0.80, -rad * bend, rad * 1.05)
            p.drawPath(cable)
            plug = QRectF(-rad * 0.17, -rad * 0.50, rad * 0.34, rad * 0.62)
            p.setPen(QPen(QColor("#B9A6FF"), 1.5))
            p.setBrush(hgrad(plug, ("#FFFFFF", "#ECE6FF", "#BFAEF7")))
            p.drawRoundedRect(plug, rad * 0.09, rad * 0.09)
            tip = QRectF(-rad * 0.12, -rad * 0.74, rad * 0.24, rad * 0.26)
            p.setBrush(hgrad(tip, ("#F4F1FF", "#C9C0EE")))
            p.drawRoundedRect(tip, rad * 0.07, rad * 0.07)
            p.setPen(QPen(QColor("#9C8CE0"), 1.2))
            p.drawLine(QPointF(-rad * 0.06, -rad * 0.62), QPointF(rad * 0.06, -rad * 0.62))
            p.restore()
