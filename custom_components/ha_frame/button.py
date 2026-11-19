"""Temporarily wake the frame."""
from homeassistant.components.button import ButtonEntity
from homeassistant.exceptions import HomeAssistantError
from .api import FrameApiError
from .const import DOMAIN
from .entity import FrameEntity

async def async_setup_entry(hass, entry, async_add_entities):
    c = hass.data[DOMAIN][entry.entry_id]
    async_add_entities([FrameWake(c, "wake", "Wake screen")])

class FrameWake(FrameEntity, ButtonEntity):
    async def async_press(self):
        try:
            await self.coordinator.api.wake()
        except FrameApiError as exc:
            raise HomeAssistantError(str(exc)) from exc
