"""Screen, night mode and automatic brightness switches."""
from homeassistant.components.switch import SwitchEntity
from homeassistant.helpers.entity import EntityCategory
from .const import DOMAIN
from .entity import FrameEntity

FIELDS = {
    "screen_enabled": "Screen enabled",
    "night_enabled": "Night mode",
    "auto_brightness": "Automatic brightness",
    "time_fallback": "Night time fallback",
}

async def async_setup_entry(hass, entry, async_add_entities):
    c = hass.data[DOMAIN][entry.entry_id]
    async_add_entities([FrameSwitch(c, key, name) for key, name in FIELDS.items()])

class FrameSwitch(FrameEntity, SwitchEntity):
    _attr_entity_category = EntityCategory.CONFIG

    def __init__(self, coordinator, key, name):
        super().__init__(coordinator, key, name)
        self.key = key

    @property
    def is_on(self):
        return bool(self.coordinator.data.get(self.key, False))

    async def async_turn_on(self, **kwargs):
        await self.coordinator.set_display({self.key: True})

    async def async_turn_off(self, **kwargs):
        await self.coordinator.set_display({self.key: False})
