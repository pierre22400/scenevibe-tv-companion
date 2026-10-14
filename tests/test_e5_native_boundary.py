import ast
from collections import Counter
import hashlib
from pathlib import Path
import re
import unittest

from e5_native_provenance import BASE_HEAD, ROOT, blob_hash, ledger, retained_from_bytes, e5_retained_bytes

"""Validate the ACTUAL E5 candidate and the finite historical-scope adapter.

Old assertions/baselines stay present, while candidate runtime tests execute the
actual app. This gate proves that no unlisted file or unreviewed edit can hide
behind historical reconstruction and that callbacks/flags retain their explicit scope.
"""


class E5NativeBoundaryTest(unittest.TestCase):
    """Check complete bytes, source authority, fixture identity and retained old assertions."""

    def test_complete_finite_inventory(self):
        """Every real scoped file is either immutable base, explicit delta or the ledger itself."""
        data = ledger()
        actual = set()
        for directory in ('app/src', 'tests', '.github', 'docs'):
            actual.update(str(p.relative_to(ROOT)) for p in (ROOT / directory).rglob('*')
                          if p.is_file() and '__pycache__' not in p.parts)
        expected = {p for p in set(data['startingBlobs']) | set(data['additions']) | {data['ledgerPath']}
                    if p.startswith(('app/src/', 'tests/', '.github/', 'docs/'))}
        self.assertEqual(expected, actual)
        self.assertEqual(BASE_HEAD, data['baseHead'])
        self.assertFalse(any('*' in name for name in data['changes'] | data['additions']))

    def test_all_base_bytes_and_new_files_are_checked(self):
        """Pin all base files; permitted edits must reconstruct their full immutable Git blobs."""
        data = ledger()
        for name, before_sha in data['startingBlobs'].items():
            self.assertEqual(before_sha, blob_hash(e5_retained_bytes(ROOT / name)), name)
        for name, sha in data['additions'].items():
            self.assertEqual(sha, blob_hash((ROOT / name).read_bytes()), name)

    def test_unreviewed_changed_file_is_rejected(self):
        """An additional byte in an authorized file cannot be hidden by the inverse layer."""
        path = ROOT / 'app/src/main/java/com/scenevibe/tvcompanionpoc/SceneRuntimeController.java'
        with self.assertRaises(ValueError):
            retained_from_bytes(path, path.read_bytes() + b'\n')

    def test_protected_runtime_contracts_are_current_byte_exact(self):
        """Actual MEDIA identity/calendar/parser, Cloud, storage and main permissions remain unchanged."""
        names = ['MediaSessionProbe.java', 'MediaIdentityMatcher.java', 'VideoMediaObservationAdapter.java',
                 'OverlayManifest.java', 'OverlayManifestParser.java', 'VideoOverlayManifestBridge.java',
                 'CloudControlClient.java', 'CloudProtocol.java', 'calendar/MediaCalendarScheduler.java',
                 'installation/InstallationStore.java', 'installation/PackageInstaller.java']
        data = ledger()
        paths = ['app/src/main/java/com/scenevibe/tvcompanionpoc/' + n for n in names]
        paths += ['app/src/main/AndroidManifest.xml']
        for name in paths:
            self.assertNotIn(name, data['changes'])
            self.assertEqual(data['startingBlobs'][name], blob_hash((ROOT / name).read_bytes()), name)

    def test_one_host_and_one_native_command_boundary(self):
        """The video port contains no second window or transport; only the native port dispatches."""
        java = ROOT / 'app/src/main/java/com/scenevibe/tvcompanionpoc'
        port = (java / 'AndroidLocalVideoPort.java').read_text()
        self.assertNotRegex(port, r'^import .*WindowManager', 'Second window owner')
        self.assertNotIn('getTransportControls', port)
        renderer = (java / 'SceneRenderer.java').read_text()
        self.assertEqual(1, renderer.count('windows.addView('))
        dispatchers = [p.name for p in java.glob('*.java') if 'getTransportControls()' in p.read_text()]
        self.assertEqual(['AndroidNativeSessionPort.java'], dispatchers)
        self.assertNotIn('requestAudioFocus', port)

    def test_exact_recovered_fixture(self):
        """Candidate bytes, size and SHA match the originally recovered Spike file."""
        p = ROOT / 'app/src/e5Qualification/assets/scenevibe_interlude_10s.mp4'
        self.assertEqual(2146888, p.stat().st_size)
        self.assertEqual('c66ded0b6a2ea41ef34320a289f2c1ec37e2a87d34ef580d9ac45d08815682ec',
                         hashlib.sha256(p.read_bytes()).hexdigest())

    def test_historical_test_methods_assertions_and_docstrings_retained(self):
        """Every former test method/assertion/docstring remains; no new skip is introduced."""
        data = ledger()
        for name in data['startingBlobs']:
            if not name.startswith('tests/test_') or not name.endswith('.py'):
                continue
            original_bytes = e5_retained_bytes(ROOT / name)
            self.assertEqual(data['startingBlobs'][name], blob_hash(original_bytes), name)
            original = original_bytes.decode('utf-8')
            current = (ROOT / name).read_text()
            old_tree, new_tree = ast.parse(original), ast.parse(current)
            old_methods = {n.name: n for n in ast.walk(old_tree) if isinstance(n, ast.FunctionDef)}
            new_methods = {n.name: n for n in ast.walk(new_tree) if isinstance(n, ast.FunctionDef)}
            self.assertEqual(set(old_methods), set(new_methods), name)
            for method, old in old_methods.items():
                new = new_methods[method]
                self.assertEqual(ast.get_docstring(old), ast.get_docstring(new), (name, method))
                old_asserts = Counter(n.func.attr for n in ast.walk(old)
                                      if isinstance(n, ast.Call) and isinstance(n.func, ast.Attribute)
                                      and n.func.attr.startswith('assert'))
                new_asserts = Counter(n.func.attr for n in ast.walk(new)
                                      if isinstance(n, ast.Call) and isinstance(n.func, ast.Attribute)
                                      and n.func.attr.startswith('assert'))
                self.assertEqual(old_asserts, new_asserts, (name, method))
                self.assertEqual([ast.dump(d) for d in old.decorator_list],
                                 [ast.dump(d) for d in new.decorator_list], (name, method))


if __name__ == '__main__':
    unittest.main()
