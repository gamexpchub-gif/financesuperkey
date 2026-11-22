"""iCast visual identity: neon blue → violet on deep navy, glassmorphism surfaces."""

from __future__ import annotations

from PySide6.QtGui import QFont, QFontDatabase

# Palette
NAVY = "#070A24"
NAVY_2 = "#0B0F33"
CYAN = "#22C9FF"
BLUE = "#1F6BFF"
BLUE_2 = "#3D7DFF"
VIOLET = "#7B3BFF"
PURPLE = "#A64BFF"
MAGENTA = "#D04BFF"
GOLD = "#FFC53D"
GREEN = "#22D36B"
RED = "#FF4D6A"
AMBER = "#FFB020"
TEXT = "#FFFFFF"
TEXT_2 = "#D9DEFF"
MUTED = "#9AA3CF"

GLASS_FILL = (18, 22, 62, 150)
GLASS_BORDER = (255, 255, 255, 26)

GRAD_PRIMARY = (CYAN, BLUE, VIOLET, MAGENTA)
GRAD_WIRELESS = (CYAN, "#1E8CFF", BLUE)
GRAD_WIRED = ("#B04BFF", "#8A3DFF", "#6E3BFF")
GRAD_STOP = ("#FF6A88", RED, "#E0245E")

STATE_COLORS = {"idle": GREEN, "starting": AMBER, "waiting": CYAN, "streaming": GREEN, "error": RED}
STATE_PILL = {
    "idle": "Ready to Cast",
    "starting": "Starting…",
    "waiting": "Waiting for iPhone",
    "streaming": "Live — Casting",
    "error": "Needs attention",
}

_FONT_CANDIDATES = ("Inter", "Inter Display", "Poppins", "Montserrat", "SF Pro Display",
                    "Segoe UI", "Ubuntu", "Cantarell", "Noto Sans", "DejaVu Sans")
FONT_FAMILY = "Sans Serif"


def resolve_font() -> str:
    global FONT_FAMILY
    families = set(QFontDatabase.families())
    FONT_FAMILY = next((f for f in _FONT_CANDIDATES if f in families), FONT_FAMILY)
    return FONT_FAMILY


def font(px: int, weight: QFont.Weight = QFont.Normal, italic: bool = False) -> QFont:
    f = QFont(FONT_FAMILY)
    f.setPixelSize(px)
    f.setWeight(weight)
    f.setItalic(italic)
    f.setHintingPreference(QFont.PreferNoHinting)
    return f


def stylesheet() -> str:
    return f"""
* {{ font-family: "{FONT_FAMILY}"; color: {TEXT}; outline: 0; }}
QLabel {{ background: transparent; }}
QLabel#muted {{ color: {MUTED}; }}
QLabel#h1 {{ font-size: 34px; font-weight: 800; }}
QLabel#h2 {{ font-size: 20px; font-weight: 700; }}
QLabel#sub {{ color: {TEXT_2}; font-size: 16px; }}
QLabel#field {{ color: {TEXT_2}; font-size: 14px; }}
QLineEdit, QComboBox, QSpinBox {{
    background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.12);
    border-radius: 10px; padding: 9px 12px; font-size: 14px; selection-background-color: {BLUE};
}}
QLineEdit:focus, QComboBox:focus, QSpinBox:focus {{ border: 1px solid {CYAN}; background: rgba(34,201,255,0.07); }}
QComboBox::drop-down {{ border: none; width: 26px; }}
QComboBox QAbstractItemView {{ background: #11163F; border: 1px solid rgba(255,255,255,0.14);
    selection-background-color: {BLUE}; padding: 4px; border-radius: 8px; }}
QSpinBox::up-button, QSpinBox::down-button {{ width: 0; border: none; }}
QCheckBox {{ font-size: 14px; spacing: 10px; color: {TEXT_2}; }}
QCheckBox::indicator {{ width: 20px; height: 20px; border-radius: 6px;
    border: 1px solid rgba(255,255,255,0.25); background: rgba(255,255,255,0.05); }}
QCheckBox::indicator:checked {{ background: qlineargradient(x1:0,y1:0,x2:1,y2:1, stop:0 {CYAN}, stop:1 {VIOLET});
    border: 1px solid {CYAN}; }}
QListWidget, QTableWidget {{ background: rgba(255,255,255,0.035); border: 1px solid rgba(255,255,255,0.10);
    border-radius: 14px; padding: 6px; font-size: 14px; gridline-color: rgba(255,255,255,0.06); }}
QListWidget::item {{ padding: 12px; border-radius: 10px; margin: 2px; }}
QListWidget::item:hover {{ background: rgba(255,255,255,0.06); }}
QListWidget::item:selected {{ background: qlineargradient(x1:0,y1:0,x2:1,y2:0, stop:0 {BLUE}, stop:1 {VIOLET}); }}
QTableWidget::item {{ padding: 6px; }}
QHeaderView::section {{ background: transparent; color: {MUTED}; border: none; padding: 8px 6px;
    font-weight: 700; font-size: 12px; text-transform: uppercase; }}
QHeaderView {{ background: transparent; border: none; border-bottom: 1px solid rgba(255,255,255,0.10); }}
QTableCornerButton::section {{ background: transparent; border: none; }}
QPlainTextEdit {{ background: rgba(3,5,20,0.65); border: 1px solid rgba(255,255,255,0.08); border-radius: 14px;
    font-family: "JetBrains Mono", "Fira Code", "DejaVu Sans Mono", monospace; font-size: 12px; color: #B9C2F0; padding: 8px; }}
QScrollBar:vertical {{ background: transparent; width: 10px; margin: 4px; }}
QScrollBar::handle:vertical {{ background: rgba(255,255,255,0.18); border-radius: 3px; min-height: 30px; }}
QScrollBar:horizontal {{ background: transparent; height: 10px; margin: 4px; }}
QScrollBar::handle:horizontal {{ background: rgba(255,255,255,0.18); border-radius: 3px; min-width: 30px; }}
QScrollBar::add-line, QScrollBar::sub-line, QScrollBar::add-page, QScrollBar::sub-page {{ width: 0; height: 0; background: none; }}
QScrollArea {{ background: transparent; border: none; }}
QScrollArea > QWidget > QWidget {{ background: transparent; }}
QMenu {{ background: #10153D; border: 1px solid rgba(255,255,255,0.14); border-radius: 12px; padding: 8px; font-size: 14px; }}
QMenu::item {{ padding: 9px 18px; border-radius: 8px; }}
QMenu::item:selected {{ background: rgba(61,125,255,0.35); }}
QMenu::item:disabled {{ color: {MUTED}; }}
QMenu::separator {{ height: 1px; background: rgba(255,255,255,0.10); margin: 6px 4px; }}
QToolTip {{ background: #10153D; color: {TEXT}; border: 1px solid rgba(255,255,255,0.18); padding: 6px; border-radius: 6px; }}
QMessageBox {{ background: {NAVY_2}; }}
QMessageBox QPushButton, QDialog QPushButton {{ background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.18);
    border-radius: 10px; padding: 8px 18px; min-width: 80px; }}
QMessageBox QPushButton:hover, QDialog QPushButton:hover {{ border-color: {CYAN}; }}
QDialog {{ background: {NAVY_2}; }}
QSizeGrip {{ background: transparent; }}
"""
