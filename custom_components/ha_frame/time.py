"""Start and end of the night time window."""
from datetime import time
from homeassistant.components.time import TimeEntity
from homeassistant.helpers.entity import EntityCategory
from .const import DOMAIN
from .entity import FrameEntity

async def async_setup_entry(hass, entry, async_add_entities):
    c = hass.data[DOMAIN][entry.entry_id]
    async_add_entities([FrameTime(c, "night_start", "Night start"), FrameTime(c, "night_end", "Night end")])

class FrameTime(FrameEntity, TimeEntity):
    _attr_entity_category = EntityCategory.CONFIG

    def __init__(self, coordinator, key, name):
        super().__init__(coordinator, key, name)
        self.key = key

    @property
    def native_value(self):
        value = self.coordinator.data.get(self.key, "22:00")
        try:
            return time.fromisoformat(value)
        except ValueError:
            return None

    async def async_set_value(self, value: time):
        await self.coordinator.set_display({self.key: value.strftime("%H:%M")})
