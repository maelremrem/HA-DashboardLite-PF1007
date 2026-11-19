"""Shared HA Frame entity base."""
from homeassistant.helpers.update_coordinator import CoordinatorEntity
from homeassistant.helpers.entity import DeviceInfo
from .const import DOMAIN


class FrameEntity(CoordinatorEntity):
    _attr_has_entity_name = True

    def __init__(self, coordinator, suffix: str, name: str) -> None:
        super().__init__(coordinator)
        self._attr_unique_id = f"{coordinator.entry.data['device_id']}_{suffix}"
        self._attr_name = name
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, coordinator.entry.data["device_id"])},
            name="HA Frame PF1007", manufacturer="HA Frame", model="PF1007",
            sw_version=coordinator.data.get("version"),
        )
