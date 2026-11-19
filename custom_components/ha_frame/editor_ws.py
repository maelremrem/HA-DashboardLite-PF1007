"""Authenticated bridge between a Home Assistant dashboard card and paired frames."""
from __future__ import annotations

from copy import deepcopy
import hashlib
import json
import logging

from homeassistant.components import websocket_api
import voluptuous as vol

from .api import FrameApiError
from .const import DOMAIN
from .dashboard_editor import validate_pages

_LOGGER = logging.getLogger(__name__)


def layout_revision(config: dict) -> str:
    """Detect concurrent edits without storing credentials or settings in HA."""
    selected = {key: config.get(key) for key in ("dashboard_title", "custom_css", "pages")}
    payload = json.dumps(selected, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def validate_layout(config: dict) -> dict:
    """Permit only dashboard keys and enforce Android layout bounds."""
    if set(config) != {"dashboard_title", "custom_css", "pages"}:
        raise ValueError("Unexpected configuration fields")
    title = config["dashboard_title"]
    css = config["custom_css"]
    if not isinstance(title, str) or not 1 <= len(title.strip()) <= 28:
        raise ValueError("Dashboard title must contain 1–28 characters")
    if not isinstance(css, str) or len(css) > 12000:
        raise ValueError("Custom CSS must be no longer than 12000 characters")
    validate_pages(config["pages"])
    return {
        "dashboard_title": title.strip(),
        "custom_css": css,
        "pages": deepcopy(config["pages"]),
    }


def _entry_coordinator(hass, entry_id):
    return hass.data.get(DOMAIN, {}).get(entry_id)


@websocket_api.websocket_command({vol.Required("type"): "ha_frame/editor/list"})
@websocket_api.require_admin
@websocket_api.async_response
async def editor_list(hass, connection, msg):
    """List only paired frames; never expose bearer tokens to browsers."""
    frames = []
    for entry_id, coordinator in hass.data.get(DOMAIN, {}).items():
        state = coordinator.data or {}
        frames.append({
            "entry_id": entry_id,
            "name": coordinator.entry.title,
            "device_id": coordinator.entry.data.get("device_id"),
            "version": state.get("version"),
            "online": bool(coordinator.last_update_success),
        })
    connection.send_result(msg["id"], {"frames": sorted(frames, key=lambda frame: frame["name"])})


@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/get",
    vol.Required("entry_id"): str,
})
@websocket_api.require_admin
@websocket_api.async_response
async def editor_get(hass, connection, msg):
    """Read the layout directly from one selected frame."""
    coordinator = _entry_coordinator(hass, msg["entry_id"])
    if coordinator is None:
        connection.send_error(msg["id"], "not_found", "Frame not found")
        return
    try:
        config = await coordinator.api.config()
        safe = validate_layout({
            "dashboard_title": config["dashboard_title"],
            "custom_css": config["custom_css"],
            "pages": config["pages"],
        })
    except (FrameApiError, ValueError, KeyError, TypeError) as exc:
        _LOGGER.warning("Could not read HA Frame layout: %s", type(exc).__name__)
        connection.send_error(msg["id"], "unavailable", "Could not load frame configuration")
        return
    connection.send_result(msg["id"], {"config": safe, "revision": layout_revision(safe)})


@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/save",
    vol.Required("entry_id"): str,
    vol.Required("revision"): str,
    vol.Required("config"): dict,
})
@websocket_api.require_admin
@websocket_api.async_response
async def editor_save(hass, connection, msg):
    """Validate and save atomically for the selected frame; block stale edits."""
    coordinator = _entry_coordinator(hass, msg["entry_id"])
    if coordinator is None:
        connection.send_error(msg["id"], "not_found", "Frame not found")
        return
    try:
        proposed = validate_layout(msg["config"])
    except (ValueError, KeyError, TypeError):
        connection.send_error(msg["id"], "invalid_config", "Invalid dashboard layout")
        return

    try:
        current = await coordinator.api.config()
        if layout_revision(current) != msg["revision"]:
            connection.send_error(msg["id"], "conflict", "The frame was changed elsewhere. Reload before saving.")
            return
        await coordinator.api.update(proposed)
        updated = await coordinator.api.config()
        await coordinator.async_request_refresh()
    except FrameApiError as exc:
        _LOGGER.warning("Could not save HA Frame layout: %s", type(exc).__name__)
        connection.send_error(msg["id"], "unavailable", "Could not save layout to the frame")
        return
    connection.send_result(msg["id"], {"revision": layout_revision(updated)})


def async_register_editor_commands(hass):
    """Called once during integration setup, never once per frame."""
    websocket_api.async_register_command(hass, editor_list)
    websocket_api.async_register_command(hass, editor_get)
    websocket_api.async_register_command(hass, editor_save)
