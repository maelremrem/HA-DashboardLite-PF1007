"""Night blackout status and physical ambient light sensor availability."""
from homeassistant.components.binary_sensor import BinarySensorEntity
from .const import DOMAIN
from .entity import FrameEntity

async def async_setup_entry(hass, entry, async_add_entities):
    c = hass.data[DOMAIN][entry.entry_id]
    async_add_entities([FrameBinary(c, "sleeping", "Screen dimmed"),
                        FrameBinary(c, "has_light_sensor", "Physical light sensor")])

class FrameBinary(FrameEntity, BinarySensorEntity):
    def __init__(self, coordinator, key, name):
        super().__init__(coordinator, key, name)
        self.key = key

    @property
    def is_on(self):
        return bool(self.coordinator.data.get(self.key, False))
