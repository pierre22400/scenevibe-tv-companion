import json
from pathlib import Path
import subprocess
import unittest

from e5_position_039_provenance import e5039_retained_bytes

"""Check exact scope and historical assertions separately from actual candidate runtime tests."""

ROOT = Path(__file__).resolve().parents[1]
BASE = 'efd3a7983a03458ec1f919d760c10f1b1f9c57ff'


class E5Position039Test(unittest.TestCase):
    """Require finite reversible corrections without mutating unrelated native/MEDIA/Banner code."""

    def test_corrective_sources_recover_exact_e5038(self):
        """Each full corrective file validates and reverses to its exact immutable E5-038 source."""
        data = json.loads((ROOT / '.github/scripts/e5-position-039-provenance.json').read_text())
        for name in data['changes']:
            self.assertNotIn('*', name)
            self.assertEqual(subprocess.check_output(['git', 'show', BASE + ':' + name], cwd=ROOT),
                             e5039_retained_bytes(ROOT / name), name)

    def test_old_negative_jvm_guards_and_security_endpoint_unchanged(self):
        """Preserve actual old negative tests and the protected ADB receiver byte for byte."""
        names = ['app/src/test/java/com/scenevibe/tvcompanionpoc/NativeTransportOwnerTest.java',
                 'app/src/test/java/com/scenevibe/tvcompanionpoc/SceneRuntimeLocalInterludeTest.java',
                 'app/src/test/java/com/scenevibe/tvcompanionpoc/E5LocalPlanAndForegroundTest.java',
                 'app/src/e5Qualification/java/com/scenevibe/tvcompanionpoc/E5AdbTriggerReceiver.java',
                 'app/src/e5QualificationAndroidTest/java/com/scenevibe/tvcompanionpoc/E5AdbSenderInstrumentation.java',
                 'app/src/main/java/com/scenevibe/tvcompanionpoc/AndroidNativeSessionPort.java',
                 'app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayService.java',
                 'app/src/main/AndroidManifest.xml']
        for name in names:
            self.assertEqual(subprocess.check_output(['git', 'show', BASE + ':' + name], cwd=ROOT),
                             (ROOT / name).read_bytes(), name)

    def test_sony_trace_not_substituted_for_physical_qualification(self):
        """The redacted evidence records one captured refusal and never invents a PAUSE command."""
        proof = json.loads((ROOT / 'docs/e5-position-039/sony-evidence.json').read_text())
        self.assertEqual(1113616, proof['size_bytes'])
        self.assertEqual(1, proof['captured_complete_attempts'])
        text = '\n'.join(line['text'] for line in proof['redacted_lines'])
        self.assertIn('NATIVE_ADMISSION_STALE_POSITION', text)
        self.assertIn('durationMs=-1', text)
        self.assertNotIn('PAUSE_SENT', text)
        self.assertNotIn('amzn1.dv.gti.', text)


if __name__ == '__main__':
    unittest.main()
