"""Brightness and night lux threshold numeric controls."""
from homeassistant.components.number import NumberEntity
from homeassistant.helpers.entity import EntityCategory
from .const import DOMAIN
from .entity import FrameEntity

FIELDS = {
    "brightness": ("Manual brightness", 1, 100, "%"),
    "auto_min": ("Minimum auto brightness", 1, 100, "%"),
    "auto_max": ("Maximum auto brightness", 1, 100, "%"),
    "night_lux": ("Night lux threshold", 0, 10000, "lx"),
}

async def async_setup_entry(hass, entry, async_add_entities):
    c = hass.data[DOMAIN][entry.entry_id]
    async_add_entities([FrameNumber(c, key, *spec) for key, spec in FIELDS.items()])

class FrameNumber(FrameEntity, NumberEntity):
    _attr_entity_category = EntityCategory.CONFIG
    _attr_native_step = 1

    def __init__(self, coordinator, key, name, low, high, unit):
        super().__init__(coordinator, key, name)
        self.key = key
        self._attr_native_min_value = low
        self._attr_native_max_value = high
        self._attr_native_unit_of_measurement = unit

    @property
    def native_value(self):
        return self.coordinator.data.get(self.key)

    async def async_set_native_value(self, value):
        await self.coordinator.set_display({self.key: int(value)})
