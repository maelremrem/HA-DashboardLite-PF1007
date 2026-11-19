"""Test private HA Studio profile and per-frame revision backups without HA runtime."""
from __future__ import annotations

import asyncio
import ast
from copy import deepcopy
from pathlib import Path
import unittest

FILE = (Path(__file__).resolve().parents[1] /
    "custom_components" / "ha_frame" / "studio_store.py")
tree = ast.parse(FILE.read_text())
functions = [n for n in tree.body if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef))
             and n.name in {"_state","_load","_now","list_profiles","get_profile",
                            "save_profile","delete_profile","capture_snapshot",
                            "list_snapshots","get_snapshot"}]

class FakeStore:
    data = None
    def __init__(self, hass, version, name, private=False):
        assert private is True
    async def async_load(self):
        return deepcopy(self.data)
    async def async_save(self, data):
        self.data = deepcopy(data)

class FakeHass:
    def __init__(self):
        self.data = {}

scope = {
    "Store":FakeStore,"asyncio":asyncio,"deepcopy":deepcopy,
    "uuid":__import__("uuid"),"datetime":__import__("datetime").datetime,
    "timezone":__import__("datetime").timezone,
    "KEY":"test-ha-studio","MAX_PROFILES":20,"MAX_SNAPSHOTS":8
}
exec(compile(ast.Module(body=functions,type_ignores=[]),str(FILE),"exec"),scope)


class PrivateStudioStoreTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        FakeStore.data=None
        self.hass=FakeHass()
        self.config={"dashboard_title":"Maison","custom_css":"",
                     "pages":[{"title":"Salon","columns":2,"cards":[]}]}

    async def test_profiles_and_copy_isolation(self):
        profile_id=await scope["save_profile"](self.hass,"Maison",self.config)
        self.config["dashboard_title"]="Modified"
        loaded=await scope["get_profile"](self.hass,profile_id)
        self.assertEqual(loaded["dashboard_title"],"Maison")
        loaded["dashboard_title"]="Unrelated"
        reloaded=await scope["get_profile"](self.hass,profile_id)
        self.assertEqual(reloaded["dashboard_title"],"Maison")
        listing=await scope["list_profiles"](self.hass)
        self.assertEqual(len(listing),1)
        self.assertNotIn("config",listing[0])
        self.assertTrue(await scope["delete_profile"](self.hass,profile_id))
        self.assertFalse(await scope["delete_profile"](self.hass,profile_id))

    async def test_history_bounded_and_per_frame(self):
        for i in range(12):
            await scope["capture_snapshot"](self.hass,"frame-a",str(i),self.config)
        await scope["capture_snapshot"](self.hass,"frame-b","other",self.config)
        history=await scope["list_snapshots"](self.hass,"frame-a")
        self.assertEqual(len(history),8)
        self.assertEqual(history[0]["revision"],"4")
        self.assertIsNone(await scope["get_snapshot"](self.hass,"frame-a","0"))
        self.assertEqual(len(await scope["list_snapshots"](self.hass,"frame-b")),1)
        self.assertIsNotNone(await scope["get_snapshot"](self.hass,"frame-a","11"))

    async def test_profile_capacity(self):
        for i in range(20):
            await scope["save_profile"](self.hass,f"Profile {i}",self.config)
        with self.assertRaises(ValueError):
            await scope["save_profile"](self.hass,"Overflow",self.config)


if __name__=="__main__":
    unittest.main()
