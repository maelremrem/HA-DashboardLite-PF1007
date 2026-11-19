"""Night mode, light-sensor presence and Wi-Fi connection diagnostics."""
from homeassistant.components.binary_sensor import BinarySensorEntity
from homeassistant.helpers.entity import EntityCategory

from .const import DOMAIN
from .entity import FrameEntity


async def async_setup_entry(hass, entry, async_add_entities):
    coordinator = hass.data[DOMAIN][entry.entry_id]
    async_add_entities([
        FrameBinary(coordinator, "sleeping", "Screen dimmed"),
        FrameBinary(coordinator, "has_light_sensor", "Physical light sensor"),
        FrameBinary(coordinator, "wifi_connected", "Wi-Fi connected", diagnostic=True),
    ])


class FrameBinary(FrameEntity, BinarySensorEntity):
    def __init__(self, coordinator, key, name, diagnostic=False):
        super().__init__(coordinator, key, name)
        self.key = key
        if diagnostic:
            self._attr_entity_category = EntityCategory.DIAGNOSTIC

    @property
    def available(self):
        return super().available and self.key in self.coordinator.data

    @property
    def is_on(self):
        value = self.coordinator.data.get(self.key)
        return None if value is None else bool(value)
