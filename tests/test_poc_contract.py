"""Guard the POC's narrow Android permission and component boundary."""

import pathlib
import unittest
import xml.etree.ElementTree as ET


ROOT = pathlib.Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
ANDROID = "{http://schemas.android.com/apk/res/android}"


class PocContractTests(unittest.TestCase):
    """Detect accidental expansion beyond the overlay and narrow LAN transport."""

    def test_permissions_remain_limited_to_overlay_foreground_and_lan(self):
        """Allow only overlay, foreground service and the bounded LAN transport."""
        root = ET.parse(MANIFEST).getroot()
        permissions = {node.attrib[ANDROID + "name"] for node in root.findall("uses-permission")}
        self.assertEqual(
            permissions,
            {
                "android.permission.SYSTEM_ALERT_WINDOW",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
                "android.permission.INTERNET",
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

    def test_commentary_transport_is_bounded_and_non_exported(self):
        """Pin the v0.3 message contract, image bounds and local renderer boundary."""
        server = (ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc/CommentaryServer.java").read_text()
        renderer = (ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayRenderer.java").read_text()
        self.assertIn("PORT = 8765", server)
        self.assertIn("MAX_BODY_BYTES = 3 * 1024 * 1024", server)\n        self.assertIn("MAX_IMAGE_BYTES = 2 * 1024 * 1024", server)\n        self.assertIn("MAX_RENDER_WIDTH = 1280", server)\n        self.assertIn("MAX_RENDER_HEIGHT = 720", server)
        self.assertIn("scenevibe.commentary.v1", server)
        self.assertIn("scenevibe.commentary.ack.v1", server)
        self.assertIn("showCommentary", renderer)\n        self.assertIn("ImageView", renderer)\n        self.assertIn("media.dataBase64", server)\n        self.assertIn("image/jpeg", server)\n        self.assertIn("image/png", server)

    def test_latest_commentary_owns_expiry(self):
        """A replacement commentary must cancel the previous pending expiry."""
        renderer = (ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayRenderer.java").read_text()
        self.assertIn("private final Runnable commentaryExpiry", renderer)
        self.assertIn("handler.removeCallbacks(commentaryExpiry)", renderer)
        self.assertIn("handler.postDelayed(commentaryExpiry, durationMs)", renderer)
        self.assertNotIn("commentaryUntil", renderer)


if __name__ == "__main__":
    unittest.main()
