"""Regression and security contracts for the full feature bundle."""
from __future__ import annotations

import ast
from copy import deepcopy
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import MagicMock

ROOT = Path(__file__).resolve().parents[1]
PY = ROOT / "custom_components" / "ha_frame"


def load(path, name):
    spec = spec_from_file_location(name, path)
    module = module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


rules = load(PY / "dashboard_editor.py", "rules")
media = load(PY / "media_album.py", "media")


class CompletionTests(unittest.TestCase):
    def test_native_paths_are_constrained(self):
        with tempfile.TemporaryDirectory() as parent:
            root = Path(parent) / "media"
            root.mkdir()
            (root / "family").mkdir()
            (root / "family" / "photo.jpg").write_bytes(b"x" * 120)
            outside = Path(parent) / "private"
            outside.mkdir()
            (root / "symlink").symlink_to(outside, target_is_directory=True)
            hass = types.SimpleNamespace(config=types.SimpleNamespace(
                media_dirs={"local": str(root)}))
            self.assertEqual(media._browse(hass, "local", "")["folders"], ["family"])
            self.assertEqual(len(media._photos(hass, "local", "family")), 1)
            for candidate in ("../private", "/tmp", "family/../../private", "symlink"):
                with self.assertRaises(ValueError, msg=candidate):
                    media._folder(hass, "local", candidate)
            with self.assertRaises(ValueError):
                media._root(hass, "missing")

    def test_conditional_mdi_validation(self):
        good = {
            "entity_id": "sensor.room",
            "operator": "above",
            "value": "26",
            "icon": "mdi:thermometer-alert",
            "icon_path": "M12,2L20,22H4Z",
        }
        rules.validate_condition(good, visual=True)
        for bad in (
            {"icon": "mdi:bad icon"},
            {"icon": "mdi:thermometer-alert", "icon_path": "<svg>"},
            {"icon": "mdi:thermometer-alert", "icon_path": ""},
            {"icon": "javascript:alert"},
        ):
            changed = {**good, **bad}
            with self.assertRaises(ValueError, msg=str(bad)):
                rules.validate_condition(changed, visual=True)

    def test_security_contracts(self):
        android = ROOT / "app/src/main/java/dev/maelab/haframe"
        endpoints = (android / "IntegrationApiServer.java").read_text()
        for path in (
            "/api/v1/photos/import_begin", "/api/v1/photos/import_chunk",
            "/api/v1/photos/import_finish", "/api/v1/update/begin",
            "/api/v1/update/chunk", "/api/v1/update/finish",
        ):
            self.assertIn(path, endpoints)
        self.assertLess(
            endpoints.index('if (!authenticated(req))'),
            endpoints.index('"/api/v1/photos/import_begin"'),
        )
        update = (android / "ApkStaging.java").read_text()
        self.assertIn("UpdateVerifier.verify(context)", update)
        self.assertIn('MessageDigest.getInstance("SHA-256")', update)
        self.assertNotIn("Runtime.getRuntime().exec", update)
        self.assertIn("ACTION_VIEW", (android / "UpdateVerifier.java").read_text())
        self.assertIn("PackageManager.GET_SIGNATURES", (android / "UpdateVerifier.java").read_text())
        self.assertIn("backupInstalled", (android / "SettingsActivity.java").read_text())

    def test_admin_guards_on_media_and_update_routes(self):
        module = (PY / "editor_ws.py").read_text()
        for fn in ("editor_media_sync", "editor_apk_begin", "editor_apk_chunk",
                   "editor_apk_finish", "editor_photo_upload"):
            node = next(x for x in ast.parse(module).body
                        if isinstance(x, ast.AsyncFunctionDef) and x.name == fn)
            decorators = [ast.unparse(expr) for expr in node.decorator_list]
            self.assertIn("websocket_api.require_admin", decorators)
        self.assertIn("is_admin", (PY / "__init__.py").read_text())


if __name__ == "__main__":
    unittest.main()
