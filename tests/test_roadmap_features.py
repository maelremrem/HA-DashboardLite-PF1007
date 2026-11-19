"""Regression tests for the roadmap's strict dashboard and slideshow configuration."""
from __future__ import annotations

import ast
from copy import deepcopy
import pathlib
import re
import runpy
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
PY = ROOT / "custom_components" / "ha_frame"
editor = runpy.run_path(str(PY / "dashboard_editor.py"))
source = (PY / "editor_ws.py").read_text()
tree = ast.parse(source)
functions = [node for node in tree.body if isinstance(node, ast.FunctionDef)
             and node.name in {"validate_layout", "validate_slideshow", "layout_revision"}]
scope = {"deepcopy": deepcopy, "re": re, "validate_pages": editor["validate_pages"]}
exec(compile(ast.Module(body=functions, type_ignores=[]), str(PY/"editor_ws.py"), "exec"), scope)


def sample():
    return {
        "dashboard_title": "Maison",
        "custom_css": "",
        "pages": [{"title":"Maison", "columns":3, "cards":[
            {"type":"metric", "label":"Température", "entity_id":"sensor.temp",
             "min":0, "max":100}
        ]}],
    }


class SmartDashboardTests(unittest.TestCase):
    def test_new_widgets(self):
        for name in ("calendar","todo","bambu","energy","air_quality",
                     "presence","circular_gauge","history"):
            layout=sample()
            layout["pages"][0]["cards"][0]["type"]=name
            editor["validate_pages"](layout["pages"])

    def test_smart_rules_and_time_windows(self):
        layout=sample()
        layout["pages"][0]["activate_when"] = {
            "entity_id":"", "operator":"time_between", "value":"22:00-07:00"}
        layout["pages"][0]["cards"][0]["rule"] = {
            "entity_id":"sensor.temp", "operator":"above", "value":"24.5",
            "background":"#ff3322", "hide":False}
        layout["pages"][0]["cards"][0]["tap_action"]="page_next"
        layout["pages"][0]["cards"][0]["long_press"]="settings"
        layout["pages"][0]["cards"][0]["history_hours"]=168
        editor["validate_pages"](layout["pages"])
        for patch in (
            {"operator":"__proto__"},
            {"operator":"above", "value":"import os"},
            {"operator":"time_between", "value":"25:70-88:88"},
            {"background":"url(evil)"},
            {"hide":"yes"},
            {"entity_id":"sensor.foo;rm -rf"},
            {"extra":"unsafe"}
        ):
            invalid=deepcopy(layout)
            invalid["pages"][0]["cards"][0]["rule"].update(patch)
            with self.assertRaises(ValueError, msg=str(patch)):
                editor["validate_pages"](invalid["pages"])

    def test_tap_action_allowlist(self):
        for key, bad in (("tap_action","shell_command"),
                         ("long_press","delete_all"),
                         ("history_hours",999)):
            layout=sample()
            layout["pages"][0]["cards"][0][key]=bad
            with self.assertRaises(ValueError):
                editor["validate_pages"](layout["pages"])

    def test_slideshow_roundtrip_and_limits(self):
        layout=sample()
        slideshow={"enabled":True,"idle_seconds":300,"interval_seconds":30,
                   "show_clock":True,"source":"image","camera_entity":"image.family"}
        layout["slideshow"]=slideshow
        self.assertEqual(scope["validate_layout"](layout),layout)
        for key,bad in (("source","https://example.org/image.jpg"),
                        ("interval_seconds",1),("idle_seconds",99999),
                        ("camera_entity","file:///sdcard/image.jpg"),
                        ("show_clock","true")):
            invalid=deepcopy(layout)
            invalid["slideshow"][key]=bad
            with self.assertRaises(ValueError):
                scope["validate_layout"](invalid)

    def test_private_fields_rejected(self):
        layout=sample()
        layout["slideshow"]={"enabled":False,"idle_seconds":300,
                             "interval_seconds":30,"show_clock":True,
                             "source":"local","camera_entity":""}
        for key in ("token","auth","url","device_api"):
            invalid=deepcopy(layout)
            invalid[key]="secret"
            with self.assertRaises(ValueError):
                scope["validate_layout"](invalid)


if __name__=="__main__":
    unittest.main()
