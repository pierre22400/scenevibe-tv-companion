"""Guard the POC's narrow Android permission and component boundary."""

import pathlib
import unittest
import xml.etree.ElementTree as ET


ROOT = pathlib.Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
ANDROID = "{http://schemas.android.com/apk/res/android}"


class PocContractTests(unittest.TestCase):
    """Detect accidental expansion into capture, network or exported services."""

    def test_permissions_remain_limited_to_overlay_and_foreground_service(self):
        """Reject new privileged capabilities outside this physical overlay test."""
        root = ET.parse(MANIFEST).getroot()
        permissions = {node.attrib[ANDROID + "name"] for node in root.findall("uses-permission")}
        self.assertEqual(
            permissions,
            {
                "android.permission.SYSTEM_ALERT_WINDOW",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            },
        )

    def test_tv_launcher_and_private_service(self):
        """Keep the UI launchable on TV and the overlay service inaccessible externally."""
        root = ET.parse(MANIFEST).getroot()
        application = root.find("application")
        self.assertIsNotNone(application)
        service = application.find("service")
        self.assertEqual(service.attrib[ANDROID + "exported"], "false")
        self.assertEqual(service.attrib[ANDROID + "foregroundServiceType"], "specialUse")
        activity = application.find("activity")
        categories = {
            element.attrib[ANDROID + "name"]
            for element in activity.findall("intent-filter/category")
        }
        self.assertIn("android.intent.category.LEANBACK_LAUNCHER", categories)
        features = {
            element.attrib[ANDROID + "name"]: element.attrib[ANDROID + "required"]
            for element in root.findall("uses-feature")
        }
        self.assertEqual(features["android.hardware.touchscreen"], "false")


if __name__ == "__main__":
    unittest.main()

