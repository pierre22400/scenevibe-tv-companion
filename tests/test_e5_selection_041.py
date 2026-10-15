import ast
import json
from pathlib import Path
import re
import subprocess
import unittest

from e5_selection_041_provenance import BASE, e5041_retained_bytes

"""Check actual observation-only E5-041 scope separately from historical byte reconstructions."""

ROOT = Path(__file__).resolve().parents[1]
JAVA = 'app/src/main/java/com/scenevibe/tvcompanionpoc/'


class E5Selection041Test(unittest.TestCase):
    """Preserve real transport guards, source docs, private data boundaries and finite provenance."""

    def test_application_delta_is_explicit_and_transport_guards_are_unchanged(self):
        """No application file outside the enumerated observer/wiring/version/test delta can change."""
        allowed = {JAVA + name + '.java' for name in ('AndroidNativeSessionPort',
                   'AndroidForegroundEvidence', 'NativeForegroundProof', 'NativeDiagnostics', 'OverlayService')}
        allowed |= {'app/build.gradle',
                    'app/src/e5QualificationAndroidTest/java/com/scenevibe/tvcompanionpoc/E5AdbTriggerInstrumentation.java'}
        names = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', BASE, 'app'],
                                        cwd=ROOT, text=True).splitlines()
        for name in names:
            if name not in allowed:
                self.assertEqual(subprocess.check_output(['git', 'show', BASE + ':' + name], cwd=ROOT),
                                 (ROOT / name).read_bytes(), name)

    def test_all_edits_recover_exact_e5040_bytes(self):
        """Full hashes and unique finite literals preserve every old file exactly, including tests."""
        data = json.loads((ROOT / '.github/scripts/e5-selection-041-provenance.json').read_text())
        for name in data['changes']:
            self.assertNotIn('*', name)
            self.assertEqual(subprocess.check_output(['git', 'show', BASE + ':' + name], cwd=ROOT),
                             e5041_retained_bytes(ROOT / name), name)

    def test_existing_docstrings_and_java_documentation_are_preserved(self):
        """Instrumentation adds explanations while retaining the existing pedagogical contracts."""
        data = json.loads((ROOT / '.github/scripts/e5-selection-041-provenance.json').read_text())
        for name in data['changes']:
            before = e5041_retained_bytes(ROOT / name).decode()
            after = (ROOT / name).read_text()
            if name.endswith('.java'):
                for doc in re.findall(r'/\*\*.*?\*/', before, re.S):
                    self.assertIn(doc, after, name)
            elif name.endswith('.py'):
                old = {n.name: ast.get_docstring(n) for n in ast.walk(ast.parse(before))
                       if isinstance(n, (ast.FunctionDef, ast.ClassDef))}
                new = {n.name: ast.get_docstring(n) for n in ast.walk(ast.parse(after))
                       if isinstance(n, (ast.FunctionDef, ast.ClassDef))}
                for function, doc in old.items():
                    self.assertEqual(doc, new[function], name + ':' + function)

    def test_trace_has_no_native_identity_or_command_capability(self):
        """The trace stores only primitive/enum evidence; it cannot carry a controller or transport."""
        trace = (ROOT / (JAVA + 'NativeSelectionTrace.java')).read_text()
        for forbidden in ('MediaController', 'MediaMetadata', 'PlaybackState', 'getTransportControls',
                          'mediaId', 'Context', 'java.net', 'String token'):
            self.assertNotIn(forbidden, trace)


if __name__ == '__main__':
    unittest.main()
