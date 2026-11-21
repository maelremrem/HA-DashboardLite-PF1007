"""Checks for the admin-only sidebar Studio registered by the integration."""
from __future__ import annotations

import ast
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import AsyncMock, Mock


FRONTEND = (
    Path(__file__).resolve().parent.parent
    / "custom_components" / "ha_frame" / "frontend_resource.py"
)
node = next(
    node for node in ast.parse(FRONTEND.read_text()).body
    if isinstance(node, ast.AsyncFunctionDef)
    and node.name == "async_register_studio_panel"
)

panel_exists = Mock(return_value=False)
register_panel = AsyncMock()
scope = {
    "frontend": SimpleNamespace(async_panel_exists=panel_exists),
    "panel_custom": SimpleNamespace(async_register_panel=register_panel),
    "PANEL_URL_PATH": "ha-frame-studio",
    "PANEL_RESOURCE_PATH": "/ha-frame/ha-frame-studio-panel.js",
    "RESOURCE_VERSION": "2.2.0",
}
exec(compile(ast.Module(body=[node], type_ignores=[]), str(FRONTEND), "exec"), scope)


class SidebarPanelTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        panel_exists.reset_mock()
        panel_exists.return_value = False
        register_panel.reset_mock()

    async def test_admin_only_panel_is_registered(self):
        hass = object()
        await scope["async_register_studio_panel"](hass)
        panel_exists.assert_called_once_with(hass, "ha-frame-studio")
        register_panel.assert_awaited_once_with(
            hass,
            frontend_url_path="ha-frame-studio",
            webcomponent_name="ha-frame-studio-panel",
            sidebar_title="HA Frame Studio",
            sidebar_icon="mdi:monitor-dashboard",
            module_url="/ha-frame/ha-frame-studio-panel.js?v=2.2.0",
            require_admin=True,
            embed_iframe=False,
        )

    async def test_existing_panel_is_not_registered_again(self):
        panel_exists.return_value = True
        await scope["async_register_studio_panel"](object())
        register_panel.assert_not_awaited()


if __name__ == "__main__":
    unittest.main()
