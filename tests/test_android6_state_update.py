"""Regression guards for the Home Assistant state-update crash on Android 6."""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parent.parent
CLIENT = ROOT / "app/src/main/java/dev/maelab/haframe/HaClient.java"


class Android6StateUpdateTests(unittest.TestCase):
    def test_api23_safe_trend_initialization(self):
        code = CLIENT.read_text()
        method = code.split("private void putState(", 1)[1].split("float[] recentSamples(", 1)[0]
        self.assertNotIn("computeIfAbsent(", method)
        self.assertNotIn("->", method)
        self.assertIn("trends.putIfAbsent(id, created)", method)
        self.assertIn("values = existing == null ? created : existing", method)
        self.assertIn("synchronized (values)", method)
        self.assertIn("while (values.size() > 24)", method)

    def test_android6_compatibility_and_release_version(self):
        gradle = (ROOT / "app/build.gradle").read_text()
        self.assertRegex(gradle, r"minSdk\s+23\b")
        self.assertIn("versionName '2.2.0'", gradle)


if __name__ == "__main__":
    unittest.main()
