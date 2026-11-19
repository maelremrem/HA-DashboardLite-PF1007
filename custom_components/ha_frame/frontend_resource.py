"""Serve the Lovelace editor bundled with the custom integration."""
from __future__ import annotations

import logging
from pathlib import Path

from homeassistant.components.http import StaticPathConfig

_LOGGER = logging.getLogger(__name__)

RESOURCE_PATH = "/ha-frame/ha-frame-editor.js"
RESOURCE_VERSION = "1.0.0"


async def async_register_editor_frontend(hass) -> None:
    """Expose the card and add its Lovelace resource in storage mode."""
    path = Path(__file__).parent / "frontend" / "ha-frame-editor.js"
    await hass.http.async_register_static_paths([
        StaticPathConfig(RESOURCE_PATH, str(path), False)
    ])

    lovelace = hass.data.get("lovelace")
    if lovelace is None or getattr(lovelace, "resource_mode", None) != "storage":
        _LOGGER.info(
            "HA Frame editor served at %s; add it manually as a module resource in YAML mode",
            RESOURCE_PATH,
        )
        return

    resources = lovelace.resources
    # A fresh HA restart may not have opened any dashboards yet. Ensure the
    # resource collection is loaded instead of waiting for a frontend request.
    await resources.async_get_info()

    url = f"{RESOURCE_PATH}?v={RESOURCE_VERSION}"
    existing = next(
        (item for item in resources.async_items()
         if item.get("url", "").split("?")[0] == RESOURCE_PATH),
        None,
    )
    if existing:
        if existing["url"] != url or existing.get("res_type") != "module":
            await resources.async_update_item(existing["id"], {
                "res_type": "module",
                "url": url,
            })
    else:
        await resources.async_create_item({"res_type": "module", "url": url})
