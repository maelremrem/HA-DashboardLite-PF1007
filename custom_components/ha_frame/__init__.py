"""HA Frame custom integration: native controls and notifications."""
from __future__ import annotations

from datetime import timedelta
import json
import logging

import voluptuous as vol
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, ServiceCall
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers.aiohttp_client import async_get_clientsession
from homeassistant.helpers.update_coordinator import DataUpdateCoordinator, UpdateFailed

from .api import FrameApi, FrameApiError
from .const import DOMAIN, PLATFORMS
from .editor_ws import async_register_editor_commands
from .frontend_resource import async_register_editor_frontend

_LOGGER = logging.getLogger(__name__)


class FrameCoordinator(DataUpdateCoordinator):
    def __init__(self, hass: HomeAssistant, entry: ConfigEntry) -> None:
        super().__init__(hass, _LOGGER, name="HA Frame", update_interval=timedelta(seconds=30))
        self.entry = entry
        self.api = FrameApi(async_get_clientsession(hass), entry.data["host"], entry.data["token"])

    async def _async_update_data(self):
        try:
            return await self.api.status()
        except FrameApiError as error:
            raise UpdateFailed(str(error)) from error

    async def set_display(self, values: dict) -> None:
        try:
            await self.api.update({"display": values})
            await self.async_request_refresh()
        except FrameApiError as error:
            raise HomeAssistantError(str(error)) from error


async def async_setup(hass: HomeAssistant, config: dict) -> bool:
    hass.data.setdefault(DOMAIN, {})
    async_register_editor_commands(hass)
    hass.async_create_task(async_register_editor_frontend(hass))

    async def set_dashboard(call: ServiceCall) -> None:
        device_id = call.data["device_id"]
        coordinator = next((c for c in hass.data[DOMAIN].values()
            if c.entry.data["device_id"] == device_id), None)
        if coordinator is None:
            raise HomeAssistantError("HA Frame device not found")
        update = {}
        if "dashboard_title" in call.data:
            update["dashboard_title"] = call.data["dashboard_title"]
        if "custom_css" in call.data:
            update["custom_css"] = call.data["custom_css"]
        if "pages" in call.data:
            try:
                pages = json.loads(call.data["pages"])
            except ValueError as exc:
                raise HomeAssistantError("Invalid pages JSON") from exc
            if not isinstance(pages, list):
                raise HomeAssistantError("pages must be a JSON array")
            update["pages"] = pages
        try:
            await coordinator.api.update(update)
            await coordinator.async_request_refresh()
        except FrameApiError as error:
            raise HomeAssistantError(str(error)) from error

    hass.services.async_register(DOMAIN, "set_dashboard", set_dashboard,
        schema=vol.Schema({
            vol.Required("device_id"): str,
            vol.Optional("dashboard_title"): str,
            vol.Optional("custom_css"): str,
            vol.Optional("pages"): str,
        }))
    return True


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    coordinator = FrameCoordinator(hass, entry)
    await coordinator.async_config_entry_first_refresh()
    hass.data[DOMAIN][entry.entry_id] = coordinator
    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    return True


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    if await hass.config_entries.async_unload_platforms(entry, PLATFORMS):
        hass.data[DOMAIN].pop(entry.entry_id, None)
        return True
    return False
