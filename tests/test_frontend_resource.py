"""Behavior checks for the HA Frame Studio Lovelace resource installer."""
from __future__ import annotations

import ast
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import AsyncMock, Mock


ROOT = Path(__file__).resolve().parent.parent
FRONTEND = ROOT / "custom_components" / "ha_frame" / "frontend_resource.py"
source = ast.parse(FRONTEND.read_text())
register_node = next(
    node for node in source.body
    if isinstance(node, ast.AsyncFunctionDef)
    and node.name == "async_register_editor_frontend"
)
scope = {
    "__file__": str(FRONTEND),
    "Path": Path,
    "StaticPathConfig": lambda url, path, cache: (url, path, cache),
    "RESOURCE_PATH": "/ha-frame/ha-frame-editor.js",
    "RESOURCE_VERSION": "1.0.0",
    "_LOGGER": Mock(),
}
exec(compile(ast.Module(body=[register_node], type_ignores=[]), str(FRONTEND), "exec"), scope)
register = scope["async_register_editor_frontend"]


class FakeResources:
    def __init__(self, items=None):
        self.loaded = False
        self._items = list(items or [])
        self.load_count = 0
        self.created = []
        self.updated = []

    async def async_get_info(self):
        self.loaded = True
        self.load_count += 1
        return {"resources": len(self._items)}

    def async_items(self):
        if not self.loaded:
            raise AssertionError("Resources must be loaded before being inspected")
        return self._items

    async def async_create_item(self, item):
        self.created.append(item)

    async def async_update_item(self, item_id, item):
        self.updated.append((item_id, item))


class FrontendResourceTests(unittest.IsolatedAsyncioTestCase):
    def make_hass(self, mode="storage", items=None):
        resources = FakeResources(items)
        http = SimpleNamespace(async_register_static_paths=AsyncMock())
        hass = SimpleNamespace(
            http=http,
            data={"lovelace": SimpleNamespace(resource_mode=mode, resources=resources)},
        )
        return hass, resources

    async def test_registers_resource_even_if_no_dashboard_was_opened(self):
        hass, resources = self.make_hass()
        await register(hass)
        self.assertEqual(resources.load_count, 1)
        self.assertEqual(resources.created, [{
            "res_type": "module",
            "url": "/ha-frame/ha-frame-editor.js?v=1.0.0",
        }])
        self.assertEqual(resources.updated, [])
        hass.http.async_register_static_paths.assert_awaited_once()

    async def test_updates_existing_resource_instead_of_creating_duplicate(self):
        hass, resources = self.make_hass(items=[{
            "id": "existing",
            "url": "/ha-frame/ha-frame-editor.js",
            "res_type": "js",
        }])
        await register(hass)
        self.assertEqual(resources.created, [])
        self.assertEqual(resources.updated, [("existing", {
            "res_type": "module",
            "url": "/ha-frame/ha-frame-editor.js?v=1.0.0",
        })])

    async def test_yaml_resource_mode_only_serves_file(self):
        hass, resources = self.make_hass(mode="yaml")
        await register(hass)
        self.assertEqual(resources.load_count, 0)
        self.assertEqual(resources.created, [])
        hass.http.async_register_static_paths.assert_awaited_once()


if __name__ == "__main__":
    unittest.main()
