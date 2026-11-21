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
from . import media_album

_LOGGER = logging.getLogger(__name__)


def layout_revision(config: dict) -> str:
    """Detect concurrent edits without storing credentials or settings in HA."""
    selected = {key: config.get(key) for key in ("dashboard_title", "custom_css", "pages", "slideshow")}
    payload = json.dumps(selected, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def validate_slideshow(value: dict) -> dict:
    """Bound sources and intervals, never accept arbitrary media URLs."""
    if not isinstance(value, dict) or set(value) - {
        "enabled", "idle_seconds", "interval_seconds", "show_clock", "source", "camera_entity",
        "transition_ms", "quiet_start", "quiet_end"
    }:
        raise ValueError("Invalid slideshow settings")
    if type(value.get("enabled")) is not bool or type(value.get("show_clock")) is not bool:
        raise ValueError("Invalid slideshow flags")
    if type(value.get("idle_seconds")) is not int or not 30 <= value["idle_seconds"] <= 3600:
        raise ValueError("Invalid idle interval")
    if type(value.get("interval_seconds")) is not int or not 5 <= value["interval_seconds"] <= 300:
        raise ValueError("Invalid photo interval")
    if value.get("source") not in {"local", "camera", "image"}:
        raise ValueError("Invalid photo source")
    entity = value.get("camera_entity", "")
    if not isinstance(entity, str) or (
        entity and not re.fullmatch(r"(?:camera|image)\.[a-z0-9_]+", entity)
    ) or (value["source"] != "local" and not entity):
        raise ValueError("Invalid slideshow camera")
    transition = value.get("transition_ms", 800)
    start = value.get("quiet_start", "")
    end = value.get("quiet_end", "")
    valid_clock = re.compile(r"(?:[01]\d|2[0-3]):[0-5]\d")
    if type(transition) is not int or not 0 <= transition <= 2500 or (
        bool(start) != bool(end)
    ) or any(not isinstance(v, str) or (v and not valid_clock.fullmatch(v)) for v in (start,end)):
        raise ValueError("Invalid photo transition or quiet hours")
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



@websocket_api.require_admin
@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/photos",
    vol.Required("entry_id"): str,
})
@websocket_api.async_response
async def editor_photos(hass, connection, msg):
    coordinator = _entry_coordinator(hass, msg["entry_id"])
    if coordinator is None:
        connection.send_error(msg["id"], "not_found", "Frame not found")
        return
    try:
        photos = await coordinator.api.photos()
    except FrameApiError:
        connection.send_error(msg["id"], "unavailable", "Could not list photos")
        return
    connection.send_result(msg["id"], {"photos": photos})


@websocket_api.require_admin
@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/photo_upload",
    vol.Required("entry_id"): str,
    vol.Required("data"): str,
})
@websocket_api.async_response
async def editor_photo_upload(hass, connection, msg):
    coordinator = _entry_coordinator(hass, msg["entry_id"])
    if coordinator is None:
        connection.send_error(msg["id"], "not_found", "Frame not found")
        return
    encoded = msg["data"]
    if len(encoded) > 128000 or not re.fullmatch(r"[A-Za-z0-9+/]+={0,2}", encoded):
        connection.send_error(msg["id"], "invalid_format", "JPEG exceeds 96 KB or is invalid")
        return
    try:
        photo_id = await coordinator.api.upload_photo(encoded)
    except FrameApiError:
        connection.send_error(msg["id"], "unavailable", "Photo upload rejected")
        return
    connection.send_result(msg["id"], {"id": photo_id})


@websocket_api.require_admin
@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/photo_delete",
    vol.Required("entry_id"): str,
    vol.Required("id"): str,
})
@websocket_api.async_response
async def editor_photo_delete(hass, connection, msg):
    coordinator = _entry_coordinator(hass, msg["entry_id"])
    if coordinator is None:
        connection.send_error(msg["id"], "not_found", "Frame not found")
        return
    if not re.fullmatch(r"photo_[0-9a-f]{16}\\.jpg", msg["id"]):
        connection.send_error(msg["id"], "invalid_format", "Invalid photo ID")
        return
    try:
        removed = await coordinator.api.delete_photo(msg["id"])
    except FrameApiError:
        connection.send_error(msg["id"], "unavailable", "Photo delete rejected")
        return
    connection.send_result(msg["id"], {"deleted": removed})


@websocket_api.require_admin
@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/apk_begin",
    vol.Required("entry_id"): str,
    vol.Required("bytes"): int,
    vol.Required("sha256"): str,
})
@websocket_api.async_response
async def editor_apk_begin(hass, connection, msg):
    coordinator = _entry_coordinator(hass, msg["entry_id"])
    if coordinator is None:
        connection.send_error(msg["id"], "not_found", "Frame not found")
        return
    if not (1024 <= msg["bytes"] <= 48 * 1024 * 1024) or not re.fullmatch(r"[0-9a-f]{64}", msg["sha256"]):
        connection.send_error(msg["id"], "invalid_format", "Invalid APK metadata")
        return
    try:
        session = await coordinator.api.apk_begin(msg["bytes"], msg["sha256"])
    except (FrameApiError, KeyError):
        connection.send_error(msg["id"], "unavailable", "Cannot stage update")
        return
    connection.send_result(msg["id"], {"session": session})


@websocket_api.require_admin
@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/apk_chunk",
    vol.Required("entry_id"): str,
    vol.Required("session"): str,
    vol.Required("part"): int,
    vol.Required("data"): str,
})
@websocket_api.async_response
async def editor_apk_chunk(hass, connection, msg):
    coordinator = _entry_coordinator(hass, msg["entry_id"])
    if coordinator is None:
        connection.send_error(msg["id"], "not_found", "Frame not found")
        return
    if not re.fullmatch(r"[0-9a-f]{32}", msg["session"]) or (
        len(msg["data"]) > 80008 or not re.fullmatch(r"[A-Za-z0-9+/]+={0,2}", msg["data"])
    ) or not 0 <= msg["part"] < 1200:
        connection.send_error(msg["id"], "invalid_format", "Invalid APK chunk")
        return
    try:
        received = await coordinator.api.apk_chunk(msg["session"], msg["part"], msg["data"])
    except FrameApiError:
        connection.send_error(msg["id"], "unavailable", "APK transfer interrupted")
        return
    connection.send_result(msg["id"], {"received": received})


@websocket_api.require_admin
@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/apk_finish",
    vol.Required("entry_id"): str,
    vol.Required("session"): str,
})
@websocket_api.async_response
async def editor_apk_finish(hass, connection, msg):
    coordinator = _entry_coordinator(hass, msg["entry_id"])
    if coordinator is None:
        connection.send_error(msg["id"], "not_found", "Frame not found")
        return
    if not re.fullmatch(r"[0-9a-f]{32}", msg["session"]):
        connection.send_error(msg["id"], "invalid_format", "Invalid session")
        return
    try:
        result = await coordinator.api.apk_finish(msg["session"])
    except FrameApiError:
        connection.send_error(msg["id"], "unavailable", "APK signature or checksum rejected")
        return
    connection.send_result(msg["id"], result)


@websocket_api.require_admin
@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/media_roots",
})
@websocket_api.async_response
async def editor_media_roots(hass, connection, msg):
    connection.send_result(msg["id"], {
        "roots": sorted((hass.config.media_dirs or {}).keys())
    })


@websocket_api.require_admin
@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/media_browse",
    vol.Required("source"): str,
    vol.Required("folder"): str,
})
@websocket_api.async_response
async def editor_media_browse(hass, connection, msg):
    try:
        content = await media_album.browse(hass, msg["source"], msg["folder"])
    except (OSError, ValueError):
        connection.send_error(msg["id"], "invalid_folder", "Media folder is unavailable")
        return
    connection.send_result(msg["id"], content)


@websocket_api.require_admin
@websocket_api.websocket_command({
    vol.Required("type"): "ha_frame/editor/media_sync",
    vol.Required("entry_id"): str,
    vol.Required("source"): str,
    vol.Required("folder"): str,
})
@websocket_api.async_response
async def editor_media_sync(hass, connection, msg):
    coordinator = _entry_coordinator(hass, msg["entry_id"])
    if coordinator is None:
        connection.send_error(msg["id"], "not_found", "Frame not found")
        return
    try:
        result = await media_album.sync(hass, coordinator, msg["source"], msg["folder"])
    except (FrameApiError, OSError, ValueError):
        connection.send_error(msg["id"], "sync_failed", "Album sync could not finish")
        return
    connection.send_result(msg["id"], result)


DIAGNOSTIC_FIELDS = {
    "version", "android_version", "uptime_seconds", "wifi_connected",
    "wifi_ssid", "wifi_ip", "wifi_rssi_dbm", "wifi_quality_percent",
    "wifi_link_speed_mbps", "wifi_frequency_mhz", "sleeping",
    "effective_brightness", "lux", "lux_source", "signing_cert_sha256",
    "heap_used_mb", "heap_max_mb", "battery_temperature_c", "battery_percent",
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
    vol.Required("type"): "ha_frame/editor/logs",
    vol.Required("entry_id"): str,
})
@websocket_api.async_response
async def editor_logs(hass, connection, msg):
    coordinator = _entry_coordinator(hass, msg["entry_id"])
    if coordinator is None:
        connection.send_error(msg["id"], "not_found", "Frame not found")
        return
    try:
        events = await coordinator.api.logs()
    except FrameApiError:
        connection.send_error(msg["id"], "unavailable", "Could not load device log")
        return
    connection.send_result(msg["id"], {"events": events})


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
    websocket_api.async_register_command(hass, editor_logs)
    websocket_api.async_register_command(hass, editor_photos)
    websocket_api.async_register_command(hass, editor_photo_upload)
    websocket_api.async_register_command(hass, editor_photo_delete)
    websocket_api.async_register_command(hass, editor_media_roots)
    websocket_api.async_register_command(hass, editor_media_browse)
    websocket_api.async_register_command(hass, editor_media_sync)
    websocket_api.async_register_command(hass, editor_apk_begin)
    websocket_api.async_register_command(hass, editor_apk_chunk)
    websocket_api.async_register_command(hass, editor_apk_finish)
    websocket_api.async_register_command(hass, editor_screenshot)
    websocket_api.async_register_command(hass, editor_reload)
