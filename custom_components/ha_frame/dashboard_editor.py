"""Layout validation and theme presets for the native PF1007 dashboard.

No Home Assistant imports: the model can be unit-tested without HA installed.
"""
from __future__ import annotations

from copy import deepcopy
from math import isfinite
import re

MAX_PAGES = 4
MAX_CARDS = 12
CARD_TYPES = ("metric", "control", "gauge", "thermostat", "light", "cover")
ENTITY_ID = re.compile(r"^[a-z0-9_]+\.[a-z0-9_]+$")

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
        for card in cards:
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
