import pathlib
import re
import subprocess
import unittest
import xml.etree.ElementTree as ET

"""Architecture contracts for the standalone probe; no network or Android device is used."""

ROOT = pathlib.Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
BASE = "90b55c1c9dcb6894a8285c6a1dbe830a584bba8c"


class BoundaryTests(unittest.TestCase):
    """Check permissions, transport authority, build independence and protected source identity."""

    def test_no_permissions(self):
        """The installed diagnostic must request no manifest uses-permission."""
        root = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        self.assertEqual([], root.findall("uses-permission"))

    def test_exported_surface(self):
        """Only the manual Activity and system-protected accessibility service are exported."""
        app = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot().find("application")
        self.assertEqual("false", app.get(ANDROID + "allowBackup"))
        self.assertEqual(1, len(app.findall("activity")))
        self.assertEqual(1, len(app.findall("service")))
        self.assertEqual([], app.findall("receiver") + app.findall("provider"))
        self.assertEqual("android.permission.BIND_ACCESSIBILITY_SERVICE", app.find("service").get(ANDROID + "permission"))

    def test_no_player_or_transport_authority(self):
        """Production diagnostic code cannot issue input, player, network or capture commands."""
        text = "\n".join(p.read_text() for p in (ROOT / "app/src/main/java").rglob("*.java"))
        for forbidden in ("performAction(", "performGlobalAction(", "dispatchGesture(", "requestFocus(",
                          "MediaController", "MediaSessionManager", "AudioManager", "MediaProjection",
                          "java.net", "Socket(", "WebView", "Runtime.getRuntime", "ProcessBuilder", "getExtras(",
                          "getTitle(", "event.toString(", "android.util.Log", "Class.forName"):
            self.assertNotIn(forbidden, text)

    def test_build_independent(self):
        """The project uses the distinct package and does not include the Companion build."""
        build = (ROOT / "app/build.gradle").read_text()
        self.assertIn("applicationId 'com.scenevibe.accessibilityprobe003'", build)
        self.assertNotIn("tvcompanionpoc", build)
        self.assertNotIn("project(", (ROOT / "settings.gradle").read_text())
        self.assertNotIn("secrets.", (REPO / ".github/workflows/accessibility-probe003.yml").read_text())

    def test_content_capability_only(self):
        """No gesture/key/touch-exploration capability is requested; the initial scope is own control."""
        config = ET.parse(ROOT / "app/src/main/res/xml/probe_accessibility.xml").getroot()
        self.assertEqual("true", config.get(ANDROID + "canRetrieveWindowContent"))
        self.assertEqual("false", config.get(ANDROID + "isAccessibilityTool"))
        self.assertEqual("com.scenevibe.accessibilityprobe003", config.get(ANDROID + "packageNames"))
        for name in ("canPerformGestures", "canRequestFilterKeyEvents", "canRequestTouchExplorationMode"):
            self.assertNotEqual("true", config.get(ANDROID + name))

    def test_no_protected_diff(self):
        """All tracked or staged differences remain within the probe and its new CI workflow."""
        changed = subprocess.check_output(["git", "diff", BASE, "--name-only"], cwd=REPO, text=True).splitlines()
        added = subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard"], cwd=REPO, text=True).splitlines()
        for name in changed + added:
            self.assertTrue(name.startswith("diagnostics/accessibility-probe003/") or name == ".github/workflows/accessibility-probe003.yml", name)

    def test_all_java_methods_documented(self):
        """Every declared method/constructor in added Java sources has a directly preceding Javadoc."""
        signature = re.compile(r"^\s*(?:(?:public|private|protected|static|final|synchronized)\s+)+(?:[\w<>?,\[\].]+\s+)?\w+\([^;]*\)\s*(?:throws [^{]+)?\{")
        for path in ROOT.rglob("*.java"):
            lines = path.read_text().splitlines()
            for index, line in enumerate(lines):
                candidate = line.replace("@Override ", "")
                if signature.match(candidate):
                    self.assertTrue(index > 0 and lines[index - 1].strip().endswith("*/"), f"{path}:{index + 1}")


if __name__ == "__main__":
    unittest.main()
