"""Pair a PF1007 with its on-screen one-time code."""
from __future__ import annotations

import voluptuous as vol
from homeassistant import config_entries
from homeassistant.core import callback
from homeassistant.helpers.aiohttp_client import async_get_clientsession

from .api import FrameApi, FrameApiError
from .const import DOMAIN
from .options_flow import FrameOptionsFlow


class FrameConfigFlow(config_entries.ConfigFlow, domain=DOMAIN):
    VERSION = 1

    @staticmethod
    @callback
    def async_get_options_flow(config_entry):
        """Open the native Home Assistant dashboard configuration editor."""
        return FrameOptionsFlow()

    async def async_step_user(self, user_input=None):
        errors = {}
        if user_input is not None:
            try:
                client = FrameApi(async_get_clientsession(self.hass), user_input["host"])
                paired = await client.pair(user_input["code"])
                client.token = paired["token"]
                status = await client.status()
                device_id = status["device_id"]
                await self.async_set_unique_id(device_id)
                self._abort_if_unique_id_configured()
                return self.async_create_entry(title=status.get("name", "HA Frame PF1007"), data={
                    "host": user_input["host"], "token": paired["token"], "device_id": device_id
                })
            except (FrameApiError, KeyError):
                errors["base"] = "cannot_connect"
        return self.async_show_form(step_id="user", data_schema=vol.Schema({
            vol.Required("host"): str,
            vol.Required("code"): vol.All(str, vol.Match(r"^[0-9]{6}$")),
        }), errors=errors)
