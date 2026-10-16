"""Exercise authorization positives and deliberate negatives exclusively in temporary clones."""

from pathlib import Path
import json
import subprocess
import tempfile
import unittest
from gate import BASE, JAVA, ROOT, verify

# The reference checkout is read-only throughout these tests. Each case owns its
# complete Git clone. Even committed mutations, extra paths and mode changes are
# rejected by the content seal; merely changing HEAD never authorizes new bytes.


class AuthorizationTest(unittest.TestCase):
    """Require an exact committed delivery and refuse drift before any qualification projection."""

    def setUp(self):
        """Create a private clone from the current local commit without touching its source."""
        self.temporary = tempfile.TemporaryDirectory(prefix='scenevibe-tvfirst003-negative-')
        self.root = Path(self.temporary.name) / 'repo'
        subprocess.run(['git', 'clone', '--no-hardlinks', '--quiet', str(ROOT), str(self.root)], check=True)
        self.addCleanup(self.temporary.cleanup)

    def command(self, *args):
        """Run a local clone-only Git operation for a reproducible test scenario."""
        return subprocess.check_output(['git', '-C', str(self.root), *args], stderr=subprocess.PIPE)

    def mutate(self, name):
        """Change one cloned file while leaving the accepted reference bytes intact."""
        path = self.root / name
        path.write_bytes(path.read_bytes() + b'\n// unexpected qualification mutation\n')

    def reject(self, expected):
        """Use the reviewed verifier itself, and require a specific nonzero JSON rejection."""
        process = subprocess.run(['python3', '-B', str(ROOT / 'qualification/tvfirst003/gate.py'),
                                  '--root', str(self.root)], stdout=subprocess.PIPE, text=True)
        self.assertEqual(1, process.returncode, process.stdout)
        result = json.loads(process.stdout)
        self.assertEqual('FAIL', result['verdict'])
        self.assertIn(expected, result['reason'])

    def test_exact_delivery_after_commit(self):
        """A real delivery commit is admitted even though its HEAD is no longer BASE."""
        result = verify(self.root)
        self.assertEqual('PASS', result['verdict'])
        self.assertNotEqual(BASE, result['head'])
        self.assertEqual(60, result['deliveryFilesExact'])
        self.assertEqual(274, result['protectedComparedToBase'])

    def test_protected_historical_mutation(self):
        """Reject an unexpected edit of the actual historical runtime before retention is built."""
        self.mutate(JAVA + 'OverlayService.java')
        self.reject('Protected historical file changed: ' + JAVA + 'OverlayService.java')

    def test_presentation_mutation(self):
        """Reject an altered delivered presentation file, not just changes to the old core."""
        self.mutate(JAVA + 'presentation/TvNavigation.java')
        self.reject('Accepted 002 file changed: ')

    def test_committed_protected_mutation(self):
        """Reject historical drift even when it has been committed under a new valid descendant HEAD."""
        self.mutate(JAVA + 'OverlayService.java')
        self.command('add', JAVA + 'OverlayService.java')
        self.command('-c', 'user.name=Local negative qualification', '-c',
                     'user.email=qualification@scenevibe.invalid', 'commit', '--quiet', '-m', 'negative control only')
        self.reject('Protected historical file changed: ')

    def test_unexpected_added_source(self):
        """Refuse an extra file outside the authorized exact inventory."""
        (self.root / 'unexpected.txt').write_text('unapproved\n')
        self.reject('Source inventory mismatch:')

    def test_ci_overlay_mutation(self):
        """Refuse an altered CI script even though its path belongs to qualification."""
        self.mutate('qualification/tvfirst003/run.py')
        self.reject('Authorized CI file changed:')

    def test_manifest_mutation(self):
        """Reject an attempt to change the accepted delivery manifest itself."""
        self.mutate('qualification/tvfirst003/delivery002-files.json')
        self.reject('Accepted 002 manifest changed')

    def test_wrong_authorized_event_head(self):
        """A mismatched event checkout cannot qualify a different commit by accident."""
        with self.assertRaisesRegex(Exception, 'Checkout HEAD differs'):
            verify(self.root, BASE)


if __name__ == '__main__':
    unittest.main()
