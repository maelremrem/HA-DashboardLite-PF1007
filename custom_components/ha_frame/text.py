"""Illuminance entity selector and dashboard title."""
from homeassistant.components.text import TextEntity
from homeassistant.helpers.entity import EntityCategory
from .const import DOMAIN
from .entity import FrameEntity
from .api import FrameApiError
from homeassistant.exceptions import HomeAssistantError

async def async_setup_entry(hass, entry, async_add_entities):
    c = hass.data[DOMAIN][entry.entry_id]
    async_add_entities([FrameText(c, "lux_entity_id", "Illuminance entity", 128),
                        FrameText(c, "dashboard_title", "Dashboard title", 28)])

class FrameText(FrameEntity, TextEntity):
    _attr_entity_category = EntityCategory.CONFIG

    def __init__(self, coordinator, key, name, maximum):
        super().__init__(coordinator, key, name)
        self.key = key
        self._attr_native_max = maximum

    @property
    def native_value(self):
        return self.coordinator.data.get(self.key, "")

    async def async_set_value(self, value):
        if self.key == "dashboard_title":
            try:
                await self.coordinator.api.update({self.key: value})
                await self.coordinator.async_request_refresh()
            except FrameApiError as exc:
                raise HomeAssistantError(str(exc)) from exc
        else:
            await self.coordinator.set_display({self.key: value})
