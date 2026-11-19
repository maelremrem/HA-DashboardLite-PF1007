"""Layout validation and theme presets for the native PF1007 dashboard.

No Home Assistant imports: the model can be unit-tested without HA installed.
"""
from __future__ import annotations

from copy import deepcopy
from math import isfinite
import re

MAX_PAGES = 4
MAX_CARDS = 12
CARD_TYPES = ("metric", "control", "gauge", "thermostat", "light", "cover", "weather", "graph", "camera", "calendar", "todo", "bambu", "energy", "air_quality", "presence", "circular_gauge", "history")
CARD_ICONS = {"", "none", "home", "light", "thermometer", "energy", "water", "sun",
              "cloud", "lock", "camera", "chart", "fan", "battery", "wifi", "person",
              "settings", "bell", "door", "plug", "moon", "heart"}
ENTITY_ID = re.compile(r"^[a-z0-9_]+\.[a-z0-9_]+$")
MDI_ICON = re.compile(r"^mdi:[a-z0-9]+(?:-[a-z0-9]+)*$")
MDI_PATH = re.compile(r"^[MmLlHhVvCcSsQqTtAaZz0-9eE+,.\s-]+$")
MAX_MDI_PATH = 8192


def validate_icon(card: dict) -> None:
    icon = card.get("icon", "")
    path = card.get("icon_path", "")
    if not isinstance(icon, str) or not (icon in CARD_ICONS or MDI_ICON.fullmatch(icon)):
        raise ValueError("Icon must use mdi:name")
    if not isinstance(path, str) or len(path) > MAX_MDI_PATH or (
        path and (not MDI_PATH.fullmatch(path) or not re.match(r"^[Mm]", path))
    ):
        raise ValueError("Invalid MDI vector path")
    if path and not (MDI_ICON.fullmatch(icon) or icon in ("", "none")):
        raise ValueError("Vector path requires an MDI icon")
    if MDI_ICON.fullmatch(icon) and not path:
        raise ValueError("MDI icon is missing its vector path")


# Keep these presets aligned with app/src/main/assets/config/app.js.
PRESETS = {
    "original": "",
    "ocean": """:root { --accent: #58d5e7; }
body { background-color: #071c2b; }
.header, .clock { color: #b9f0ff; }
.metric, .control, .settings { background-color: #113249; border-radius: 23px; }
.control.on { background-color: #19575e; }
.control.pressed { background-color: #296b77; }
.accent { color: var(--accent); }""",
    "amber": """:root { --accent: #ffb25c; }
body { background-color: #1a1412; }
.header, .clock { color: #ffe4b7; }
.metric, .control, .settings { background-color: #2d231d; border-radius: 14px; }
.control.on { background-color: #53391e; }
.control.pressed { background-color: #704d2b; }
.accent { color: var(--accent); }""",
    "light": """:root { --accent: #167b72; }
body { background-color: #ebf2f5; }
.header, .clock, .control-label, .metric-value { color: #1c3445; }
.subtitle, .date, .section, .metric-label, .control-state, .status { color: #506677; }
.metric, .control, .settings { background-color: #ffffff; border-radius: 22px; }
.settings { color: #20394c; }
.control.on { background-color: #bcf1df; }
.control.pressed { background-color: #9fe3d3; }
.accent { color: var(--accent); }""",
}


# Canvas-compatible themes shared with the Studio and local Android editors.
THEME_PALETTES = {
    "midnight": {"bg":"#0b1020","card":"#171f34","on":"#263956","pressed":"#344763","text":"#f4f7ff","muted":"#a8b7cf","accent":"#8aaaff","tab":"#2e3f6a","activeText":"#dce5ff","radius":22},
    "forest": {"bg":"#071c18","card":"#113129","on":"#1b4b3b","pressed":"#285a4a","text":"#e8fff3","muted":"#aad6c2","accent":"#7ce5ae","tab":"#234e3a","activeText":"#9dffd1","radius":24},
    "lavender": {"bg":"#171126","card":"#29213b","on":"#463558","pressed":"#56456b","text":"#f8edff","muted":"#cbb9dc","accent":"#d5aaff","tab":"#41325e","activeText":"#f9d8ff","radius":28},
    "rose": {"bg":"#24131a","card":"#40212d","on":"#603044","pressed":"#764357","text":"#fff2f5","muted":"#ddb4c5","accent":"#ff9bb8","tab":"#653149","activeText":"#ffe4ed","radius":28},
    "graphite": {"bg":"#111315","card":"#262a2e","on":"#383e43","pressed":"#484f56","text":"#f0f1f2","muted":"#aeb8be","accent":"#81d5de","tab":"#394246","activeText":"#bbfaff","radius":8},
    "solar": {"bg":"#f8f1da","card":"#fff9ec","on":"#fde4b4","pressed":"#ecd29d","text":"#3c2b22","muted":"#795b47","accent":"#c96522","tab":"#f6ddb2","activeText":"#7b3c1c","radius":18},
    "arctic": {"bg":"#edf7fa","card":"#ffffff","on":"#d1eef7","pressed":"#b9e4ee","text":"#183246","muted":"#537588","accent":"#197ca5","tab":"#c5edf6","activeText":"#174d69","radius":22},
    "retro": {"bg":"#171c17","card":"#273326","on":"#4b5236","pressed":"#5a6442","text":"#f7f2cf","muted":"#c7c49a","accent":"#d5df74","tab":"#414d2e","activeText":"#eef0a9","radius":5},
    "synthwave": {"bg":"#170e2f","card":"#291a4b","on":"#4e2166","pressed":"#6f2b86","text":"#fcddff","muted":"#d4b2ec","accent":"#ff70d9","tab":"#512164","activeText":"#ffe6ff","radius":20},
    "sand": {"bg":"#25231f","card":"#3b352d","on":"#5d4b38","pressed":"#6a5741","text":"#fff1d5","muted":"#dac9a8","accent":"#ffd082","tab":"#665039","activeText":"#ffe4b2","radius":16},
}

for key, p in THEME_PALETTES.items():
    PRESETS[key] = "\n".join([
        f':root {{ --accent: {p["accent"]}; }}',
        f'body {{ background-color: {p["bg"]}; }}',
        f'.header, .clock, .metric-value, .settings {{ color: {p["text"]}; }}',
        f'.subtitle, .date, .status, .metric-label {{ color: {p["muted"]}; }}',
        f'.card, .settings {{ background-color: {p["card"]}; border-radius: {p["radius"]}px; }}',
        f'.page-tab {{ background-color: {p["card"]}; color: {p["muted"]}; border-radius: 12px; }}',
        f'.page-tab.active {{ background-color: {p["tab"]}; color: {p["activeText"]}; }}',
        f'.control.on {{ background-color: {p["on"]}; }}',
        f'.control.pressed {{ background-color: {p["pressed"]}; }}',
        '.accent { color: var(--accent); }',
    ])



STYLE_KEYS = {"background", "foreground", "accent", "radius"}
HEX_COLOR = re.compile(r"^#[0-9a-fA-F]{6}$")


def card_grid(page: dict) -> tuple[list[tuple[int, int, int, int]], int]:
    """Deterministic first-fit grid shared with Android's TileGrid.place."""
    columns = page["columns"]
    occupied = [[False] * columns for _ in range(3)]
    placements = []
    used_rows = 0
    for card in page["cards"]:
        width, height = card.get("col_span", 1), card.get("row_span", 1)
        if type(width) is not int or type(height) is not int or not (1 <= width <= columns and 1 <= height <= 2):
            raise ValueError("Invalid tile span")
        found = None
        for row in range(4 - height):
            for col in range(columns - width + 1):
                if all(not occupied[r][c]
                       for r in range(row, row + height)
                       for c in range(col, col + width)):
                    found = (col, row)
                    break
            if found is not None:
                break
        if found is None:
            raise ValueError("Tiles exceed the three-row grid")
        col, row = found
        for r in range(row, row + height):
            for c in range(col, col + width):
                occupied[r][c] = True
        placements.append((col, row, width, height))
        used_rows = max(used_rows, row + height)
    return placements, max(1, used_rows)


def validate_style(style: dict) -> None:
    if not isinstance(style, dict) or set(style) - STYLE_KEYS:
        raise ValueError("Unsupported tile style")
    for key in ("background", "foreground", "accent"):
        if key in style and (not isinstance(style[key], str) or not HEX_COLOR.fullmatch(style[key])):
            raise ValueError("Invalid tile color")
    if "radius" in style and (type(style["radius"]) is not int or not 0 <= style["radius"] <= 72):
        raise ValueError("Invalid tile radius")





RULE_OPERATORS = {"equals", "not_equals", "above", "below", "on", "off"}
TAP_ACTIONS = {"default", "none", "page_next", "page_prev", "settings", "camera_fullscreen"}
LONG_PRESS_ACTIONS = {"none", "toggle", "page_next", "settings"}
HISTORY_WINDOWS = {1, 24, 168}


def validate_condition(rule: dict, *, visual: bool) -> None:
    """Allow-list only state comparisons and Canvas-safe visual effects."""
    if not isinstance(rule, dict):
        raise ValueError("Invalid dashboard condition")
    allowed = {"entity_id", "operator", "value"}
    if visual:
        allowed |= {"hide", "background"}
    if set(rule) - allowed:
        raise ValueError("Unknown rule option")
    entity_id = rule.get("entity_id", "")
    operator = rule.get("operator")
    value = rule.get("value", "")
    if not isinstance(entity_id, str) or not ENTITY_ID.fullmatch(entity_id):
        raise ValueError("Rule requires a Home Assistant entity")
    if operator not in RULE_OPERATORS:
        raise ValueError("Unsupported rule comparison")
    if not isinstance(value, str) or len(value) > 64 or (
        operator in {"above", "below"} and (
            not re.fullmatch(r"-?\d{1,6}(?:\.\d{1,3})?", value)
            or not isfinite(float(value))
        )
    ):
        raise ValueError("Invalid rule value")
    if operator in {"equals", "not_equals"} and not value:
        raise ValueError("Rule comparison value required")
    if "hide" in rule and type(rule["hide"]) is not bool:
        raise ValueError("Invalid visibility rule")
    if "background" in rule and (
        not isinstance(rule["background"], str)
        or not HEX_COLOR.fullmatch(rule["background"])
    ):
        raise ValueError("Invalid conditional background")


def validate_pages(pages: list[dict]) -> None:
    """Match the limits enforced by FrameConfig.setPages on Android."""
    if not isinstance(pages, list) or not 1 <= len(pages) <= MAX_PAGES:
        raise ValueError("Expected between one and four pages")
    for page in pages:
        title = page.get("title")
        columns = page.get("columns")
        cards = page.get("cards")
        if not isinstance(title, str) or not 1 <= len(title.strip()) <= 32:
            raise ValueError("Invalid page title")
        if type(columns) is not int or columns not in (2, 3, 4):
            raise ValueError("Invalid column count")
        if not isinstance(cards, list) or len(cards) > min(MAX_CARDS, columns * 3):
            raise ValueError("At most three rows of cards per page")
        if "activate_when" in page:
            validate_condition(page["activate_when"], visual=False)
        for card in cards:
            if "style" in card:
                validate_style(card["style"])
            validate_icon(card)
            if "rule" in card:
                validate_condition(card["rule"], visual=True)
            if card.get("tap_action", "default") not in TAP_ACTIONS:
                raise ValueError("Unsupported tile tap action")
            if card.get("long_press", "none") not in LONG_PRESS_ACTIONS:
                raise ValueError("Unsupported tile long press")
            if type(card.get("history_hours", 24)) is not int or card.get("history_hours", 24) not in HISTORY_WINDOWS:
                raise ValueError("History window must be 1, 24 or 168 hours")
            if card.get("type") == "camera" and card.get("entity_id") and not card["entity_id"].startswith("camera."):
                raise ValueError("Camera widget requires a camera entity")
            if card.get("type") == "weather" and card.get("entity_id") and not card["entity_id"].startswith("weather."):
                raise ValueError("Weather widget requires a weather entity")
            if card.get("type") not in CARD_TYPES:
                raise ValueError("Invalid card type")
            label = card.get("label")
            entity_id = card.get("entity_id")
            if not isinstance(label, str) or len(label.strip()) > 48:
                raise ValueError("Invalid card label")
            if not isinstance(entity_id, str) or (entity_id and not ENTITY_ID.fullmatch(entity_id)):
                raise ValueError("Invalid entity ID")
            try:
                minimum, maximum = float(card.get("min", 0)), float(card.get("max", 100))
            except (ValueError, TypeError) as exc:
                raise ValueError("Invalid gauge range") from exc
            if not all(map(isfinite, (minimum, maximum))) or maximum <= minimum or maximum - minimum > 1_000_000:
                raise ValueError("Invalid gauge range")
        if sum(card.get("type") == "camera" for card in cards) > 1:
            raise ValueError("Only one camera per page is supported")
        card_grid(page)


def move(items: list, index: int, direction: int) -> bool:
    other = index + direction
    if index < 0 or other < 0 or index >= len(items) or other >= len(items):
        return False
    items[index], items[other] = items[other], items[index]
    return True


def pages_copy(pages: list[dict]) -> list[dict]:
    return deepcopy(pages)


def new_card(card_type: str, label: str, entity_id: str = "", minimum: float = 0,
             maximum: float = 100) -> dict:
    return {
        "type": card_type, "label": label.strip(), "entity_id": entity_id.strip(),
        "min": minimum, "max": maximum,
    }
