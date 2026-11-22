"""Visual identity: palette, stylesheet and the iCast logo."""

ACCENT = "#0A84FF"
ACCENT_HOVER = "#409CFF"
DANGER = "#FF453A"
SUCCESS = "#30D158"
WARNING = "#FFD60A"
BG = "#0E0F13"
SURFACE = "#16181D"
SURFACE_2 = "#1E2128"
BORDER = "#2A2E37"
TEXT = "#F2F3F5"
MUTED = "#8B90A0"

STATE_COLORS = {
    "idle": MUTED,
    "starting": WARNING,
    "waiting": ACCENT,
    "streaming": SUCCESS,
    "error": DANGER,
}

LOGO_SVG = f"""<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 128 128'>
<defs><linearGradient id='g' x1='0' y1='0' x2='1' y2='1'>
<stop offset='0' stop-color='#5AC8FA'/><stop offset='1' stop-color='#0A84FF'/></linearGradient></defs>
<rect width='128' height='128' rx='28' fill='url(#g)'/>
<rect x='40' y='22' width='38' height='70' rx='9' fill='none' stroke='white' stroke-width='6'/>
<circle cx='59' cy='82' r='3.5' fill='white'/>
<path d='M86 52a14 14 0 0 1 0 22M94 44a26 26 0 0 1 0 38' fill='none' stroke='white' stroke-width='6' stroke-linecap='round'/>
<path d='M32 106h64' stroke='white' stroke-width='6' stroke-linecap='round'/>
</svg>"""

STYLESHEET = f"""
* {{ font-family: "Inter", "SF Pro Display", "Segoe UI", "Ubuntu", sans-serif; font-size: 13px; color: {TEXT}; }}
QMainWindow, QWidget#root {{ background: {BG}; }}
QWidget#sidebar {{ background: {SURFACE}; border-right: 1px solid {BORDER}; }}
QLabel#brand {{ font-size: 20px; font-weight: 700; }}
QLabel#brandSub {{ color: {MUTED}; font-size: 11px; }}
QPushButton#nav {{ text-align: left; padding: 10px 14px; border: none; border-radius: 8px; background: transparent; color: {MUTED}; font-weight: 600; }}
QPushButton#nav:hover {{ background: {SURFACE_2}; color: {TEXT}; }}
QPushButton#nav:checked {{ background: {SURFACE_2}; color: {TEXT}; border-left: 3px solid {ACCENT}; }}
QLabel#h1 {{ font-size: 24px; font-weight: 700; }}
QLabel#h2 {{ font-size: 15px; font-weight: 600; }}
QLabel#muted {{ color: {MUTED}; }}
QFrame#card {{ background: {SURFACE}; border: 1px solid {BORDER}; border-radius: 14px; }}
QPushButton {{ background: {SURFACE_2}; border: 1px solid {BORDER}; border-radius: 8px; padding: 8px 14px; }}
QPushButton:hover {{ border-color: {ACCENT}; }}
QPushButton:disabled {{ color: {MUTED}; }}
QPushButton#primary {{ background: {ACCENT}; border: none; font-size: 15px; font-weight: 700; padding: 14px 28px; border-radius: 12px; }}
QPushButton#primary:hover {{ background: {ACCENT_HOVER}; }}
QPushButton#primary[active="true"] {{ background: {DANGER}; }}
QLineEdit, QComboBox, QSpinBox {{ background: {SURFACE_2}; border: 1px solid {BORDER}; border-radius: 8px; padding: 7px 10px; }}
QLineEdit:focus, QComboBox:focus, QSpinBox:focus {{ border-color: {ACCENT}; }}
QComboBox QAbstractItemView {{ background: {SURFACE_2}; selection-background-color: {ACCENT}; }}
QCheckBox::indicator {{ width: 18px; height: 18px; border-radius: 5px; border: 1px solid {BORDER}; background: {SURFACE_2}; }}
QCheckBox::indicator:checked {{ background: {ACCENT}; border-color: {ACCENT}; }}
QListWidget, QTableWidget {{ background: {SURFACE_2}; border: 1px solid {BORDER}; border-radius: 10px; padding: 4px; gridline-color: {BORDER}; }}
QListWidget::item {{ padding: 10px; border-radius: 8px; }}
QListWidget::item:selected {{ background: {ACCENT}; }}
QHeaderView::section {{ background: {SURFACE}; color: {MUTED}; border: none; padding: 6px; font-weight: 600; }}
QPlainTextEdit#log {{ background: #0A0B0E; border: 1px solid {BORDER}; border-radius: 10px; font-family: "JetBrains Mono", "Fira Code", monospace; font-size: 12px; color: #B7BCC8; }}
QScrollBar:vertical {{ background: transparent; width: 10px; }}
QScrollBar::handle:vertical {{ background: {BORDER}; border-radius: 5px; min-height: 30px; }}
QScrollBar::add-line, QScrollBar::sub-line {{ height: 0; }}
QStatusBar {{ background: {SURFACE}; color: {MUTED}; border-top: 1px solid {BORDER}; }}
"""
