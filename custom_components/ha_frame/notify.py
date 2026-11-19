"""Send Home Assistant messages to the PF1007 display."""
from homeassistant.components.notify import NotifyEntity, NotifyEntityFeature
from homeassistant.exceptions import HomeAssistantError
from .api import FrameApiError
from .const import DOMAIN
from .entity import FrameEntity

async def async_setup_entry(hass, entry, async_add_entities):
    c = hass.data[DOMAIN][entry.entry_id]
    async_add_entities([FrameNotify(c, "notify", "Notifications")])

class FrameNotify(FrameEntity, NotifyEntity):
    _attr_supported_features = NotifyEntityFeature.TITLE

    async def async_send_message(self, message: str, title: str | None = None) -> None:
        try:
            await self.coordinator.api.notify(message, title or "Home Assistant")
        except FrameApiError as exc:
            raise HomeAssistantError(str(exc)) from exc
