"""Contract tests for the per-frame, admin-only visual configuration bridge."""
from __future__ import annotations

import ast
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import re
import runpy
import unittest


ROOT = Path(__file__).resolve().parent.parent
PY = ROOT / "custom_components" / "ha_frame"
MODEL = runpy.run_path(str(PY / "dashboard_editor.py"))

parsed = ast.parse((PY / "editor_ws.py").read_text())
function_nodes = [
    node for node in parsed.body
    if isinstance(node, ast.FunctionDef)
    and node.name in ("layout_revision", "validate_layout")
]
globals_ = {
    "deepcopy": deepcopy,
    "hashlib": hashlib,
    "json": json,
    "validate_pages": MODEL["validate_pages"],
}
exec(compile(ast.Module(body=function_nodes, type_ignores=[]), "<editor_ws>", "exec"), globals_)


class VisualEditorTests(unittest.TestCase):
    def setUp(self):
        self.layout = {
            "dashboard_title": "Maison",
            "custom_css": "",
            "pages": [{"title": "Pièce", "columns": 2, "cards": [
                {"type": "metric", "label": "Température", "entity_id": "sensor.temperature",
                 "min": 0, "max": 100}
            ]}],
        }

    def test_valid_layout(self):
        self.assertEqual(globals_["validate_layout"](self.layout), self.layout)

    def test_reject_excess_pages_and_bad_entity(self):
        many = deepcopy(self.layout)
        many["pages"] *= 5
        with self.assertRaises(ValueError):
            globals_["validate_layout"](many)
        invalid = deepcopy(self.layout)
        invalid["pages"][0]["cards"][0]["entity_id"] = "bad id"
        with self.assertRaises(ValueError):
            globals_["validate_layout"](invalid)

    def test_reject_extra_config_field(self):
        extra = {**self.layout, "token": "must-not-leak"}
        with self.assertRaises(ValueError):
            globals_["validate_layout"](extra)

    def test_revision_detects_changes_without_frame_credentials(self):
        revision = globals_["layout_revision"](self.layout)
        changed = deepcopy(self.layout)
        changed["pages"][0]["cards"][0]["label"] = "Humidité"
        self.assertNotEqual(revision, globals_["layout_revision"](changed))
        with_token = {**self.layout, "token": "a"}
        self.assertEqual(revision, globals_["layout_revision"](with_token))

    def test_admin_only_ws_and_no_raw_frame_access(self):
        content = (PY / "editor_ws.py").read_text()
        for endpoint in ("list", "get", "save"):
            self.assertIn('"ha_frame/editor/' + endpoint + '"', content)
        self.assertGreaterEqual(content.count("@websocket_api.require_admin"), 3)
        self.assertEqual(content.count("@websocket_api.require_admin"),
                         content.count("websocket_api.async_register_command(hass,"))
        self.assertRegex(content, r"@websocket_api.require_admin\s+@websocket_api.websocket_command")
        self.assertIn('coordinator = _entry_coordinator(hass, msg["entry_id"])', content)
        self.assertIn('connection.send_error(msg["id"], "conflict"', content)

    def test_resource_and_versions(self):
        manifest = json.loads((PY / "manifest.json").read_text())
        self.assertEqual(manifest["version"], "1.5.0")
        self.assertIn("frontend", manifest["dependencies"])
        self.assertIn("panel_custom", manifest["dependencies"])
        frontend = (PY / "frontend_resource.py").read_text()
        self.assertIn("async_register_static_paths", frontend)
        self.assertIn("resources.async_create_item", frontend)
        self.assertTrue((PY / "frontend" / "ha-frame-editor.js").is_file())


if __name__ == "__main__":
    unittest.main()
