"""Register HA Frame Studio assets, Lovelace card, and sidebar panel."""
from __future__ import annotations

import logging
from pathlib import Path

from homeassistant.components import frontend, panel_custom
from homeassistant.components.http import StaticPathConfig

_LOGGER = logging.getLogger(__name__)

RESOURCE_PATH = "/ha-frame/ha-frame-editor.js"
PANEL_RESOURCE_PATH = "/ha-frame/ha-frame-studio-panel.js"
PANEL_URL_PATH = "ha-frame-studio"
RESOURCE_VERSION = "1.5.0"


async def async_register_editor_frontend(hass) -> None:
    """Serve both frontend modules; optionally register the legacy Lovelace card."""
    asset_dir = Path(__file__).parent / "frontend"
    await hass.http.async_register_static_paths([
        StaticPathConfig(RESOURCE_PATH, str(asset_dir / "ha-frame-editor.js"), False),
        StaticPathConfig(
            PANEL_RESOURCE_PATH, str(asset_dir / "ha-frame-studio-panel.js"), False
        ),
    ])

    lovelace = hass.data.get("lovelace")
    if lovelace is None or getattr(lovelace, "resource_mode", None) != "storage":
        _LOGGER.debug("Optional Lovelace card resource not in storage mode")
        return

    try:
        resources = lovelace.resources
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
                    "res_type": "module", "url": url,
                })
        else:
            await resources.async_create_item({"res_type": "module", "url": url})
    except Exception:
        # The standalone sidebar Studio doesn't depend on the Lovelace resource.
        _LOGGER.exception("Could not register optional HA Frame Lovelace card")


async def async_register_studio_panel(hass) -> None:
    """Show the full editor in the sidebar, independently of Lovelace resources."""
    if frontend.async_panel_exists(hass, PANEL_URL_PATH):
        return
    await panel_custom.async_register_panel(
        hass,
        frontend_url_path=PANEL_URL_PATH,
        webcomponent_name="ha-frame-studio-panel",
        sidebar_title="HA Frame Studio",
        sidebar_icon="mdi:monitor-dashboard",
        module_url=f"{PANEL_RESOURCE_PATH}?v={RESOURCE_VERSION}",
        require_admin=True,
        embed_iframe=False,
    )
