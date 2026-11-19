"""Source-level integration checks that do not require Home Assistant imports."""
import ast
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parent.parent


class DeviceDiagnosticsContractTests(unittest.TestCase):
    def test_android_and_home_assistant_share_the_same_fields(self):
        android = (ROOT / "app/src/main/java/dev/maelab/haframe/DeviceDiagnostics.java").read_text()
        sensor_file = (ROOT / "custom_components/ha_frame/sensor.py").read_text()
        module = ast.parse(sensor_file)
        spec = next(
            ast.literal_eval(node.value)
            for node in module.body if isinstance(node, ast.Assign)
            and any(isinstance(target, ast.Name) and target.id == "DIAGNOSTIC_SENSORS"
                    for target in node.targets)
        )
        for key, name, unit in spec:
            with self.subTest(key=key):
                self.assertIn(f'data.put("{key}"', android)
                self.assertTrue(name)
                self.assertIsNone(unit) if key in ("version", "android_version", "wifi_ssid", "wifi_ip") else None

        self.assertIn('data.put("wifi_connected"', android)
        self.assertIn('"wifi_connected"', (ROOT / "custom_components/ha_frame/binary_sensor.py").read_text())

    def test_app_version_is_not_hardcoded_in_status(self):
        diagnostics = (ROOT / "app/src/main/java/dev/maelab/haframe/DeviceDiagnostics.java").read_text()
        server = (ROOT / "app/src/main/java/dev/maelab/haframe/IntegrationApiServer.java").read_text()
        self.assertIn("getPackageInfo(context.getPackageName(), 0).versionName", diagnostics)
        self.assertIn("DeviceDiagnostics.snapshot(context)", server)
        self.assertNotIn('.put("version", "0.7.0")', server)

    def test_permission_and_versions(self):
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
        gradle = (ROOT / "app/build.gradle").read_text()
        integration = (ROOT / "custom_components/ha_frame/manifest.json").read_text()
        self.assertIn("android.permission.ACCESS_WIFI_STATE", manifest)
        self.assertIn("versionName '2.1.0'", gradle)
        self.assertGreaterEqual(tuple(map(int, __import__("json").loads(integration)["version"].split("."))), (0, 9, 0))
        self.assertIn("EntityCategory.DIAGNOSTIC",
                      (ROOT / "custom_components/ha_frame/sensor.py").read_text())


if __name__ == "__main__":
    unittest.main()
