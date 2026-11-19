"""Frame telemetry."""
from homeassistant.components.sensor import SensorEntity, SensorDeviceClass
from .const import DOMAIN
from .entity import FrameEntity

async def async_setup_entry(hass, entry, async_add_entities):
    c = hass.data[DOMAIN][entry.entry_id]
    async_add_entities([FrameSensor(c, "lux", "Illuminance", "lx"),
                        FrameSensor(c, "effective_brightness", "Effective brightness", "%"),
                        FrameSensor(c, "device_id", "Device ID", None)])

class FrameSensor(FrameEntity, SensorEntity):
    def __init__(self, coordinator, key, name, unit):
        super().__init__(coordinator, key, name)
        self.key = key
        self._attr_native_unit_of_measurement = unit
        if key == "lux":
            self._attr_device_class = SensorDeviceClass.ILLUMINANCE

    @property
    def native_value(self):
        return self.coordinator.data.get(self.key)
