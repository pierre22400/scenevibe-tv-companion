import pathlib
import re
import subprocess
import unittest
import xml.etree.ElementTree as ET

"""Static G2 isolation and authority checks; no Android capture result is inferred from these tests."""

ROOT = pathlib.Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
BASE = "fe3a797197ed4bc36459f1cb4e7fc8627bf88dc4"


class BoundaryTests(unittest.TestCase):
    """Check independent packaging, permissions and preservation of qualified files."""

    def test_manifest(self):
        """Only the two normal projection FGS permissions and a private service are present."""
        tree = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        self.assertEqual({"android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"},
                         {p.get(ANDROID + "name") for p in tree.findall("uses-permission")})
        app = tree.find("application")
        self.assertEqual("false", app.get(ANDROID + "allowBackup"))
        self.assertEqual(1, len(app.findall("service")))
        self.assertEqual("false", app.find("service").get(ANDROID + "exported"))
        self.assertEqual([], app.findall("provider") + app.findall("receiver"))

    def test_no_authority(self):
        """Production source contains no accessibility, transport, network, OCR or privileged backend."""
        source = "\n".join(p.read_text() for p in (ROOT / "app/src/main/java").rglob("*.java"))
        for forbidden in ("performAction(", "dispatchGesture(", "injectInputEvent(", "MediaController", "MediaRecorder", "AccessibilityService", "java.net", "Socket(", "Runtime.getRuntime", "ProcessBuilder", "MLKit", "Tesseract", "getExternalStorage"):
            self.assertNotIn(forbidden, source)

    def test_isolation(self):
        """All differences are confined to G2 and the essential dedicated qualification workflow."""
        changed = subprocess.check_output(["git", "diff", BASE, "--name-only"], cwd=REPO, text=True).splitlines()
        added = subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard"], cwd=REPO, text=True).splitlines()
        for name in changed + added:
            self.assertTrue(name.startswith("diagnostics/timebase-capture-g2/") or name == ".github/workflows/timebase-capture-g2.yml", name)

    def test_build_independent(self):
        """No parent build or production signing configuration enters the instrument."""
        self.assertNotIn("tvcompanionpoc", (ROOT / "app/build.gradle").read_text())
        self.assertNotIn("project(", (ROOT / "settings.gradle").read_text())
        self.assertIn("com.scenevibe.timebasecaptureg2", (ROOT / "app/build.gradle").read_text())

    def test_one_shot(self):
        """One creation site, terminal cleanup and volatile fixture gate remain explicit."""
        s = (ROOT / "app/src/main/java/com/scenevibe/timebasecaptureg2/CaptureService.java").read_text()
        self.assertEqual(1, s.count("projection.createVirtualDisplay("))
        self.assertIn("START_NOT_STICKY", s)
        self.assertIn("SessionMemory.preparationVisible", s)
        for required in ("image.close()", "reader.close()", "display.release()", "projection.stop()", "Arrays.fill(crop,0)", "compareAndSet(false,true)"):
            self.assertIn(required, s)

    def test_inspection_and_scope(self):
        """Inspection is user-opened only after closure; no service launches or foreground discovery."""
        src = ROOT / "app/src/main/java/com/scenevibe/timebasecaptureg2"
        service = (src / "CaptureService.java").read_text()
        self.assertNotIn("startActivity", service)
        for forbidden in ("UsageStatsManager", "getRunningTasks", "getActiveSessions", "queryIntentActivities"):
            self.assertNotIn(forbidden, service)
        result = (src / "ResultActivity.java").read_text()
        for required in ("FLAG_SECURE", "CaptureService.active", "bitmap.eraseColor(0)", "bitmap.recycle()", "onStop()"):
            self.assertIn(required, result)
        self.assertIn("SIMULATED_", (src / "SessionMemory.java").read_text())
        self.assertIn("ROI_MS=300000", (src / "CapturePolicy.java").read_text())

    def test_java_documentation(self):
        """Every new Java method/constructor has adjacent pedagogical documentation."""
        signature = re.compile(r"^\s*(?:(?:public|private|protected|static|final|synchronized)\s+)+(?:[\w<>?,\[\].]+\s+)?\w+\([^;]*\)\s*(?:throws [^{]+)?\{")
        for path in ROOT.rglob("*.java"):
            lines = path.read_text().splitlines()
            for index, line in enumerate(lines):
                if signature.match(line.replace("@Override ", "")):
                    prior = index - 1
                    while prior >= 0 and lines[prior].strip().startswith("@"):
                        prior -= 1
                    self.assertTrue(prior >= 0 and lines[prior].strip().endswith("*/"), f"{path}:{index+1}")


if __name__ == "__main__":
    unittest.main()
