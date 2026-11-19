"""Authenticated bridge between a Home Assistant dashboard card and paired frames."""
from __future__ import annotations

from copy import deepcopy
import hashlib
import json
import logging
import re

from homeassistant.components import websocket_api
import voluptuous as vol

from .api import FrameApiError
from .const import DOMAIN
from .dashboard_editor import validate_pages
from . import studio_store

_LOGGER = logging.getLogger(__name__)


def layout_revision(config: dict) -> str:
    """Detect concurrent edits without storing credentials or settings in HA."""
    selected = {key: config.get(key) for key in ("dashboard_title", "custom_css", "pages", "slideshow")}
    payload = json.dumps(selected, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def validate_slideshow(value: dict) -> dict:
    """Bound sources and intervals, never accept arbitrary media URLs."""
    if not isinstance(value, dict) or set(value) - {
        "enabled", "idle_seconds", "interval_seconds", "show_clock", "source", "camera_entity"
    }:
        raise ValueError("Invalid slideshow settings")
    if type(value.get("enabled")) is not bool or type(value.get("show_clock")) is not bool:
        raise ValueError("Invalid slideshow flags")
    if type(value.get("idle_seconds")) is not int or not 30 <= value["idle_seconds"] <= 3600:
        raise ValueError("Invalid idle interval")
    if type(value.get("interval_seconds")) is not int or not 5 <= value["interval_seconds"] <= 300:
        raise ValueError("Invalid photo interval")
    if value.get("source") not in {"local", "camera"}:
        raise ValueError("Invalid photo source")
    entity = value.get("camera_entity", "")
    if not isinstance(entity, str) or (
        entity and not re.fullmatch(r"camera\.[a-z0-9_]+", entity)
    ) or (value["source"] == "camera" and not entity):
        raise ValueError("Invalid slideshow camera")
    return deepcopy(value)


def validate_layout(config: dict) -> dict:
    """Permit only dashboard keys and enforce Android layout bounds."""
    required = {"dashboard_title", "custom_css", "pages"}
    if not isinstance(config, dict) or not required <= set(config) or set(config) - required - {"slideshow"}:
        raise ValueError("Unexpected configuration fields")
    title = config["dashboard_title"]
    css = config["custom_css"]
    if not isinstance(title, str) or not 1 <= len(title.strip()) <= 28:
        raise ValueError("Dashboard title must contain 1–28 characters")
    if not isinstance(css, str) or len(css) > 12000:
        raise ValueError("Custom CSS must be no longer than 12000 characters")
    validate_pages(config["pages"])
    result = {
        "dashboard_title": title.strip(),
        "custom_css": css,
        "pages": deepcopy(config["pages"]),
    }
    if "slideshow" in config:
        result["slideshow"] = validate_slideshow(config["slideshow"])
    return result


def _entry_coordinator(hass, entry_id):
    return hass.data.get(DOMAIN, {}).get(entry_id)


@websocket_api.require_admin
@websocket_api.websocket_command({vol.Required("type"): "ha_frame/editor/list"})
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


@websocket_api.require_admin
@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/get",
    vol.Required("entry_id"): str,
})
@websocket_api.async_response
async def editor_get(hass, connection, msg):
    """Read the layout directly from one selected frame."""
    coordinator = _entry_coordinator(hass, msg["entry_id"])
    if coordinator is None:
        connection.send_error(msg["id"], "not_found", "Frame not found")
        return
    try:
        config = await coordinator.api.config()
        layout = {
            "dashboard_title": config["dashboard_title"],
            "custom_css": config["custom_css"],
            "pages": config["pages"],
        }
        if "slideshow" in config:
            layout["slideshow"] = config["slideshow"]
        safe = validate_layout(layout)
    except (FrameApiError, ValueError, KeyError, TypeError) as exc:
        _LOGGER.warning("Could not read HA Frame layout: %s", type(exc).__name__)
        connection.send_error(msg["id"], "unavailable", "Could not load frame configuration")
        return
    connection.send_result(msg["id"], {"config": safe, "revision": layout_revision(safe)})


@websocket_api.require_admin
@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/save",
    vol.Required("entry_id"): str,
    vol.Required("revision"): str,
    vol.Required("config"): dict,
})
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
        try:
            previous = {key: current[key] for key in ("dashboard_title", "custom_css", "pages")}
            if "slideshow" in current:
                previous["slideshow"] = current["slideshow"]
            await studio_store.capture_snapshot(
                hass, msg["entry_id"], msg["revision"], validate_layout(previous)
            )
        except Exception:
            _LOGGER.exception("Could not capture optional HA Frame layout snapshot")
        await coordinator.async_request_refresh()
    except FrameApiError as exc:
        _LOGGER.warning("Could not save HA Frame layout: %s", type(exc).__name__)
        connection.send_error(msg["id"], "unavailable", "Could not save layout to the frame")
        return
    connection.send_result(msg["id"], {"revision": layout_revision(updated)})



@websocket_api.require_admin
@websocket_api.websocket_command({vol.Required("type"): "ha_frame/editor/profiles"})
@websocket_api.async_response
async def editor_profiles(hass, connection, msg):
    connection.send_result(msg["id"], {"profiles": await studio_store.list_profiles(hass)})


@websocket_api.require_admin
@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/profile_get",
    vol.Required("profile_id"): str,
})
@websocket_api.async_response
async def editor_profile_get(hass, connection, msg):
    config = await studio_store.get_profile(hass, msg["profile_id"])
    if config is None:
        connection.send_error(msg["id"], "not_found", "Profile not found")
    else:
        connection.send_result(msg["id"], {"config": validate_layout(config)})


@websocket_api.require_admin
@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/profile_save",
    vol.Required("name"): str,
    vol.Required("config"): dict,
})
@websocket_api.async_response
async def editor_profile_save(hass, connection, msg):
    try:
        config = validate_layout(msg["config"])
        profile_id = await studio_store.save_profile(hass, msg["name"], config)
    except (TypeError, ValueError):
        connection.send_error(msg["id"], "invalid_config", "Invalid profile")
        return
    connection.send_result(msg["id"], {"profile_id": profile_id})


@websocket_api.require_admin
@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/profile_delete",
    vol.Required("profile_id"): str,
})
@websocket_api.async_response
async def editor_profile_delete(hass, connection, msg):
    deleted = await studio_store.delete_profile(hass, msg["profile_id"])
    connection.send_result(msg["id"], {"deleted": deleted})


@websocket_api.require_admin
@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/snapshots",
    vol.Required("entry_id"): str,
})
@websocket_api.async_response
async def editor_snapshots(hass, connection, msg):
    if _entry_coordinator(hass, msg["entry_id"]) is None:
        connection.send_error(msg["id"], "not_found", "Frame not found")
        return
    history = await studio_store.list_snapshots(hass, msg["entry_id"])
    connection.send_result(msg["id"], {"snapshots": history})


@websocket_api.require_admin
@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/snapshot_get",
    vol.Required("entry_id"): str,
    vol.Required("revision"): str,
})
@websocket_api.async_response
async def editor_snapshot_get(hass, connection, msg):
    if _entry_coordinator(hass, msg["entry_id"]) is None:
        connection.send_error(msg["id"], "not_found", "Frame not found")
        return
    config = await studio_store.get_snapshot(hass, msg["entry_id"], msg["revision"])
    if config is None:
        connection.send_error(msg["id"], "not_found", "Snapshot not found")
    else:
        connection.send_result(msg["id"], {"config": validate_layout(config)})



DIAGNOSTIC_FIELDS = {
    "version", "android_version", "uptime_seconds", "wifi_connected",
    "wifi_ssid", "wifi_ip", "wifi_rssi_dbm", "wifi_quality_percent",
    "wifi_link_speed_mbps", "wifi_frequency_mhz", "sleeping",
    "effective_brightness", "lux", "lux_source", "signing_cert_sha256",
}


@websocket_api.require_admin
@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/diagnostics",
    vol.Required("entry_id"): str,
})
@websocket_api.async_response
async def editor_diagnostics(hass, connection, msg):
    coordinator = _entry_coordinator(hass, msg["entry_id"])
    if coordinator is None:
        connection.send_error(msg["id"], "not_found", "Frame not found")
        return
    try:
        info = await coordinator.api.status()
    except FrameApiError:
        connection.send_error(msg["id"], "unavailable", "Could not read frame status")
        return
    connection.send_result(msg["id"], {
        "diagnostics": {key: info.get(key) for key in DIAGNOSTIC_FIELDS}
    })


@websocket_api.require_admin
@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/screenshot",
    vol.Required("entry_id"): str,
})
@websocket_api.async_response
async def editor_screenshot(hass, connection, msg):
    coordinator = _entry_coordinator(hass, msg["entry_id"])
    if coordinator is None:
        connection.send_error(msg["id"], "not_found", "Frame not found")
        return
    try:
        image = await coordinator.api.screenshot()
    except FrameApiError:
        connection.send_error(msg["id"], "unavailable", "Screenshot unavailable")
        return
    connection.send_result(msg["id"], image)


@websocket_api.require_admin
@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/reload",
    vol.Required("entry_id"): str,
    vol.Required("confirmation"): "reload_dashboard",
})
@websocket_api.async_response
async def editor_reload(hass, connection, msg):
    coordinator = _entry_coordinator(hass, msg["entry_id"])
    if coordinator is None:
        connection.send_error(msg["id"], "not_found", "Frame not found")
        return
    try:
        await coordinator.api.reload_dashboard()
    except FrameApiError:
        connection.send_error(msg["id"], "unavailable", "Dashboard reload failed")
        return
    connection.send_result(msg["id"], {"ok": True})


def async_register_editor_commands(hass):
    """Called once during integration setup, never once per frame."""
    websocket_api.async_register_command(hass, editor_list)
    websocket_api.async_register_command(hass, editor_get)
    websocket_api.async_register_command(hass, editor_save)
    websocket_api.async_register_command(hass, editor_profiles)
    websocket_api.async_register_command(hass, editor_profile_get)
    websocket_api.async_register_command(hass, editor_profile_save)
    websocket_api.async_register_command(hass, editor_profile_delete)
    websocket_api.async_register_command(hass, editor_snapshots)
    websocket_api.async_register_command(hass, editor_snapshot_get)
    websocket_api.async_register_command(hass, editor_diagnostics)
    websocket_api.async_register_command(hass, editor_screenshot)
    websocket_api.async_register_command(hass, editor_reload)
