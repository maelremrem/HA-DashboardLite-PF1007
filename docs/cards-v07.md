# HA Frame v0.7 — native card types

The web editor at `http://<frame-ip>:8765` allows up to 4 pages, 12 cards/page (depending on columns), with six native Canvas card types:

| Type | Entity | Touch behavior |
| --- | --- | --- |
| `metric` | Any | Show state + unit |
| `control` | light, switch, fan, cover, scene, script, input_boolean, button, input_button | Toggle / press / open / close |
| `gauge` | Numeric sensor | Show value and progress bar between `min` and `max` |
| `thermostat` | `climate.*` | Show current/target temperature; adjust target using − / + |
| `light` | `light.*` | Toggle on card, adjust brightness on lower bar |
| `cover` | `cover.*` | Open/close on card, set position on lower bar |

Each card has `{ "type": "...", "label": "...", "entity_id": "..." }`. Gauges also accept `min` and `max` (default 0 and 100), which remain backward-compatible with v0.6 saved layouts.

The Canvas style engine supports `.gauge`, `.thermostat`, `.light`, and `.cover` in addition to existing theme classes. Gestures are intentionally coarse and latency-friendly for the PF1007 touchscreen. Real hardware interaction still requires validation.
