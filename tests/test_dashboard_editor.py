"""Pure Python regression tests for the Home Assistant dashboard editor."""
from __future__ import annotations

from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
from copy import deepcopy
import math
import unittest

path = Path(__file__).resolve().parents[1] / "custom_components" / "ha_frame" / "dashboard_editor.py"
spec = spec_from_file_location("dashboard_editor", path)
model = module_from_spec(spec)
spec.loader.exec_module(model)


class DashboardEditorTests(unittest.TestCase):
    def setUp(self):
        self.pages = [{"title": "Maison", "columns": 3, "cards": [
            model.new_card("metric", "Temperature", "sensor.temp"),
            model.new_card("light", "Salon", "light.salon"),
        ]}]

    def test_existing_layout_and_deep_copy(self):
        model.validate_pages(self.pages)
        other = deepcopy(self.pages)
        self.assertTrue(model.move(other[0]["cards"], 0, 1))
        self.assertEqual(self.pages[0]["cards"][0]["entity_id"], "sensor.temp")
        self.assertFalse(model.move(other, 0, -1))

    def test_pages_and_row_limits(self):
        model.validate_pages(self.pages * 4)
        with self.assertRaises(ValueError):
            model.validate_pages(self.pages * 5)
        broken = deepcopy(self.pages)
        broken[0]["columns"] = 2
        broken[0]["cards"] *= 4
        with self.assertRaises(ValueError):
            model.validate_pages(broken)

    def test_invalid_gauge_ranges(self):
        for minimum, maximum in [(1, 1), (100, 0), (0, math.inf), (0, 1_000_001)]:
            invalid = deepcopy(self.pages)
            invalid[0]["cards"][0].update(min=minimum, max=maximum)
            with self.assertRaises(ValueError):
                model.validate_pages(invalid)

    def test_invalid_entities_and_titles(self):
        invalid = deepcopy(self.pages)
        invalid[0]["cards"][0]["entity_id"] = "sensor.bad entity"
        with self.assertRaises(ValueError):
            model.validate_pages(invalid)
        invalid = deepcopy(self.pages)
        invalid[0]["title"] = ""
        with self.assertRaises(ValueError):
            model.validate_pages(invalid)

    def test_advanced_icons_and_spans(self):
        advanced = deepcopy(self.pages)
        advanced[0]["cards"] = [
            {**model.new_card("camera", "Entrée", "camera.front"),
             "col_span": 2, "row_span": 2, "icon": "camera",
             "style": {"background": "#101820", "foreground": "#ffffff",
                       "accent": "#ff9900", "radius": 18}},
            {**model.new_card("weather", "Météo", "weather.house"), "icon": "sun"},
        ]
        model.validate_pages(advanced)
        places, rows = model.card_grid(advanced[0])
        self.assertEqual((places[0], rows), ((0, 0, 2, 2), 2))
        too_large = deepcopy(advanced)
        too_large[0]["cards"][0]["row_span"] = 3
        with self.assertRaises(ValueError):
            model.validate_pages(too_large)
        bad_entity = deepcopy(advanced)
        bad_entity[0]["cards"][0]["entity_id"] = "sensor.not_a_camera"
        with self.assertRaises(ValueError):
            model.validate_pages(bad_entity)
        for field, bad in (("icon", "unsupported"),
                           ("style", {"background": "red"}),
                           ("style", {"unknown": True}),
                           ("style", {"radius": 73})):
            invalid = deepcopy(advanced)
            invalid[0]["cards"][0][field] = bad
            with self.assertRaises(ValueError):
                model.validate_pages(invalid)

    def test_grid_rejects_oversized_tiles(self):
        full = deepcopy(self.pages)
        full[0]["columns"] = 2
        full[0]["cards"] = [
            {**model.new_card("metric", "A"), "col_span": 2}
            for _ in range(4)
        ]
        with self.assertRaises(ValueError):
            model.validate_pages(full)

    def test_presets(self):
        self.assertEqual(set(model.PRESETS), {"original","ocean","amber","light","midnight","forest","lavender","rose","graphite","solar","arctic","retro","synthwave","sand"})
        self.assertTrue(all(len(css) <= 12000 for css in model.PRESETS.values()))


if __name__ == "__main__":
    unittest.main()
