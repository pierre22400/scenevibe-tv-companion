from pathlib import Path
import subprocess
import unittest
import xml.etree.ElementTree as ET

from e5_position_039_provenance import e5039_retained_bytes, e5039_retained_text

"""Check the actual shell entry and enforce byte-exact excluded E5/M5 runtime files."""

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/com/scenevibe/tvcompanionpoc'
BASE = '47f675dbd05218fb1caca2b090fd81c822e513e0'


class E5Adb038Test(unittest.TestCase):
    """Admission changes must not introduce another native transport or renderer owner."""

    def test_shared_request_path(self):
        """Both user entry points invoke the single fixed service-envelope factory."""
        diagnostics = (JAVA / 'DiagnosticsActivity.java').read_text()
        receiver = (ROOT / 'app/src/e5Qualification/java/com/scenevibe/tvcompanionpoc/E5AdbTriggerReceiver.java').read_text()
        factory = (JAVA / 'E5LocalRequest.java').read_text()
        self.assertIn('startForegroundService(E5LocalRequest.confirmedVod(this))', diagnostics)
        self.assertIn('startForegroundService(E5LocalRequest.confirmedVod(context))', receiver)
        self.assertIn('new Intent(context, OverlayService.class)', factory)
        self.assertIn('setAction(OverlayService.ACTION_E5_LOCAL)', factory)
        self.assertNotIn('startActivity(', receiver)
        self.assertNotIn('getTransportControls', receiver)
        self.assertNotIn('requestLocal(', receiver)

    def test_protected_qualification_only_component(self):
        """DUMP is enforced by Android delivery; no filter or production declaration exists."""
        manifest = ET.parse(ROOT / 'app/src/e5Qualification/AndroidManifest.xml').getroot()
        android = '{http://schemas.android.com/apk/res/android}'
        receiver = manifest.find('application/receiver')
        self.assertEqual('.E5AdbTriggerReceiver', receiver.get(android + 'name'))
        self.assertEqual('android.permission.DUMP', receiver.get(android + 'permission'))
        self.assertEqual('true', receiver.get(android + 'exported'))
        self.assertIsNone(receiver.find('intent-filter'))
        self.assertNotIn('E5AdbTriggerReceiver', (ROOT / 'app/src/main/AndroidManifest.xml').read_text())
        gradle = e5039_retained_text(ROOT / 'app/build.gradle')
        self.assertIn('sourceSets.debug.java.srcDirs += ["src/e5Qualification/java"]', gradle)
        self.assertIn('defaultConfig.versionCode = 15', gradle)

    def test_excluded_runtime_is_byte_exact(self):
        """All runtime sources other than the Diagnostics envelope remain exactly the E5 HEAD."""
        for relative in subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', BASE,
                                                 'app/src/main', 'app/src/e5Qualification/assets'], cwd=ROOT).decode().splitlines():
            if relative.endswith('/DiagnosticsActivity.java'):
                continue
            expected = subprocess.check_output(['git', 'show', BASE + ':' + relative], cwd=ROOT)
            self.assertEqual(expected, e5039_retained_bytes(ROOT / relative), relative)


if __name__ == '__main__':
    unittest.main()
