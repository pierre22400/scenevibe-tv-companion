from pathlib import Path
import json
import subprocess
import tempfile
import unittest
from gate import BASE, ROOT, verify

"""Exercise the actual authorization boundary in isolated local clones only."""


class AuthorizationTest(unittest.TestCase):
    """Refuse unauthorized bytes and identities before retention or build execution."""

    def setUp(self):
        """Copy only local Git objects; never contact GitHub or edit the reference tree."""
        self.temporary = tempfile.TemporaryDirectory(prefix='tvfirst006-gate-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name) / 'repo'
        subprocess.run(['git', 'clone', '--shared', '--quiet', str(ROOT), str(self.root)], check=True)

    def mutate(self, path):
        """Alter one temporary source file, preserving its other contents for a specific refusal."""
        target = self.root / path
        target.write_bytes(target.read_bytes() + b'\n// unapproved negative control\n')

    def reject(self, reason):
        """Run the reviewed verifier rather than a mutable verifier supplied by the negative clone."""
        result = subprocess.run(['python3', '-B', str(ROOT / 'qualification/tvfirst006/gate.py'),
                                 '--root', str(self.root)], stdout=subprocess.PIPE, text=True)
        self.assertEqual(1, result.returncode, result.stdout)
        self.assertIn(reason, json.loads(result.stdout)['reason'])

    def test_exact_committed_delta(self):
        """The authorized local descendant is accepted with its exact content."""
        self.assertEqual('PASS', verify(self.root)['verdict'])

    def test_protected_runtime_changed(self):
        """A protected renderer mutation cannot be admitted by the new profile."""
        self.mutate('app/src/main/java/com/scenevibe/tvcompanionpoc/SceneRenderer.java')
        self.reject('Protected historical file changed')

    def test_authorized_path_still_has_exact_bytes(self):
        """Even an editable service path requires its exact reviewed content."""
        self.mutate('app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayService.java')
        self.reject('Authorized delta changed')

    def test_manifest_changed(self):
        """Changing hashes in the manifest does not authorize a different delta."""
        self.mutate('qualification/tvfirst006/manifest.json')
        self.reject('006 manifest changed')

    def test_file_added_inside_allowed_directory(self):
        """An allowed directory is not an open-ended path exception."""
        (self.root / 'qualification/tvfirst006/unexpected.txt').write_text('unapproved\n')
        self.reject('Source inventory mismatch')

    def test_removed_old_gate(self):
        """A missing historical gate fails before a fallback can be selected."""
        (self.root / 'qualification/tvfirst003/gate.py').unlink()
        self.reject('Source inventory mismatch')

    def test_workflow_changed(self):
        """Routing and workflow bytes are covered by the same exact delta authorization."""
        self.mutate('.github/workflows/android-debug.yml')
        self.reject('Authorized delta changed')

    def test_wrong_event_head(self):
        """A valid tree cannot qualify a different GitHub event identity."""
        with self.assertRaisesRegex(Exception, 'Checkout HEAD differs'):
            verify(self.root, BASE)

    def test_executable_mode(self):
        """Unexpected executable modes are rejected even when content bytes are identical."""
        path = self.root / 'qualification/tvfirst006/README.md'
        path.chmod(0o755)
        self.reject('Executable source')

    def test_symlink(self):
        """A symlink cannot substitute an authorized source file."""
        path = self.root / 'qualification/tvfirst006/README.md'
        path.unlink(); path.symlink_to('/dev/null')
        self.reject('Symlink file')

    def test_committed_mutation_is_not_authorized(self):
        """A new commit identity does not authorize modified service content."""
        self.mutate('app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayService.java')
        subprocess.run(['git', '-C', str(self.root), 'add', '-A'], check=True)
        subprocess.run(['git', '-C', str(self.root), '-c', 'user.name=Local qualification', '-c',
                        'user.email=qualification@scenevibe.invalid', 'commit', '--quiet', '-m', 'negative only'], check=True)
        self.reject('Authorized delta changed')

    def test_index_must_match_head(self):
        """Staging older bytes while retaining accepted working bytes cannot hide index drift."""
        path = 'app/build.gradle'
        blob = subprocess.check_output(['git', '-C', str(self.root), 'rev-parse', BASE + ':' + path]).decode().strip()
        subprocess.run(['git', '-C', str(self.root), 'update-index', '--cacheinfo', '100644,' + blob + ',' + path], check=True)
        self.reject('Index differs from HEAD')

    def test_stale_pass_becomes_fail_before_build(self):
        """An integrity failure replaces a stale PASS and cannot invoke Gradle or retention."""
        self.mutate('app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayService.java')
        reports = self.root / 'build/tvfirst006'
        reports.mkdir(parents=True)
        (reports / 'result.json').write_text('{"verdict":"PASS"}')
        result = subprocess.run(['python3', '-B', 'qualification/tvfirst006/run.py', 'all'],
                                cwd=self.root, stdout=subprocess.PIPE, text=True)
        self.assertEqual(1, result.returncode)
        self.assertEqual('FAIL', json.loads((reports / 'result.json').read_text())['verdict'])
        self.assertFalse((self.root / 'app/build').exists())
        self.assertFalse((reports / 'python-retention.log').exists())

    def test_missing_current_gate_is_not_a_fallback(self):
        """Deleting the new gate is an inventory failure, not admission by generation 003."""
        (self.root / 'qualification/tvfirst006/gate.py').unlink()
        self.reject('Source inventory mismatch')


if __name__ == '__main__':
    unittest.main()
