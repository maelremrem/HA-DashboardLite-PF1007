"""Frame metrics and read-only diagnostic telemetry."""
from homeassistant.components.sensor import SensorDeviceClass, SensorEntity
from homeassistant.helpers.entity import EntityCategory

from .const import DOMAIN
from .entity import FrameEntity


DIAGNOSTIC_SENSORS = (
    ("version", "App version", None),
    ("android_version", "Android version", None),
    ("uptime_seconds", "Device uptime", "s"),
    ("wifi_ssid", "Wi-Fi SSID", None),
    ("wifi_ip", "Wi-Fi IP address", None),
    ("wifi_rssi_dbm", "Wi-Fi signal strength", "dBm"),
    ("wifi_quality_percent", "Wi-Fi signal quality", "%"),
    ("wifi_link_speed_mbps", "Wi-Fi link speed", "Mbit/s"),
    ("wifi_frequency_mhz", "Wi-Fi frequency", "MHz"),
)


async def async_setup_entry(hass, entry, async_add_entities):
    coordinator = hass.data[DOMAIN][entry.entry_id]
    entities = [
        FrameSensor(coordinator, "lux", "Illuminance", "lx"),
        FrameSensor(coordinator, "effective_brightness", "Effective brightness", "%"),
        FrameSensor(coordinator, "device_id", "Device ID", None, diagnostic=True),
    ]
    entities.extend(
        FrameSensor(coordinator, key, name, unit, diagnostic=True)
        for key, name, unit in DIAGNOSTIC_SENSORS
    )
    async_add_entities(entities)


class FrameSensor(FrameEntity, SensorEntity):
    def __init__(self, coordinator, key, name, unit, diagnostic=False):
        super().__init__(coordinator, key, name)
        self.key = key
        self._attr_native_unit_of_measurement = unit
        if key == "lux":
            self._attr_device_class = SensorDeviceClass.ILLUMINANCE
        elif key == "uptime_seconds":
            self._attr_device_class = SensorDeviceClass.DURATION
        if diagnostic:
            self._attr_entity_category = EntityCategory.DIAGNOSTIC

    @property
    def available(self):
        return super().available and self.key in self.coordinator.data

    @property
    def native_value(self):
        return self.coordinator.data.get(self.key)
