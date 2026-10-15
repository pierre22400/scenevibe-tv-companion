import ast
import json
from pathlib import Path
import re
import subprocess
import unittest

from e5_foreground_042_provenance import BASE, ROOT, e5042_retained_bytes

"""Require a finite functional correction and protect all preexisting native safety owners."""


class E5Foreground042Test(unittest.TestCase):
    """Separate historical provenance from assertions about the real foreground correction."""

    def test_protected_runtime_files_are_actual_unchanged_bytes(self):
        """Existing owner, policy, identity, renderer, endpoints and all JVM tests remain intact."""
        data = json.loads((ROOT / '.github/scripts/e5-foreground-042-provenance.json').read_text())
        allowed = set(data['applicationChanges'])
        files = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', BASE, 'app'], cwd=ROOT, text=True).splitlines()
        for name in files:
            if name not in allowed:
                self.assertEqual(subprocess.check_output(['git', 'show', BASE + ':' + name], cwd=ROOT),
                                 (ROOT / name).read_bytes(), name)
        self.assertEqual({
            'app/build.gradle',
            'app/src/main/java/com/scenevibe/tvcompanionpoc/AndroidForegroundEvidence.java',
            'app/src/main/java/com/scenevibe/tvcompanionpoc/AndroidNativeSessionPort.java',
            'app/src/main/java/com/scenevibe/tvcompanionpoc/NativeSelectionTrace.java',
            'app/src/e5QualificationAndroidTest/AndroidManifest.xml',
        }, allowed)

    def test_finite_inverse_and_docstrings(self):
        """Every preexisting documentation block is preserved and each old file reconstructs exactly."""
        data = json.loads((ROOT / '.github/scripts/e5-foreground-042-provenance.json').read_text())
        for name in data['changes']:
            before = e5042_retained_bytes(ROOT / name)
            self.assertEqual(subprocess.check_output(['git', 'show', BASE + ':' + name], cwd=ROOT), before, name)
            after = (ROOT / name).read_text()
            if name.endswith('.java'):
                for doc in re.findall(r'/\*\*.*?\*/', before.decode(), re.S):
                    self.assertIn(doc, after, name)
            if name.endswith('.py'):
                old = {n.name: ast.get_docstring(n) for n in ast.walk(ast.parse(before)) if isinstance(n, (ast.FunctionDef, ast.ClassDef))}
                new = {n.name: ast.get_docstring(n) for n in ast.walk(ast.parse(after)) if isinstance(n, (ast.FunctionDef, ast.ClassDef))}
                for function, doc in old.items():
                    self.assertEqual(doc, new[function], name + ':' + function)

    def test_no_new_privilege_or_hidden_android_foreground_api(self):
        """The new reducer does not obtain a new command boundary, permission or private API."""
        java = ROOT / 'app/src/main/java/com/scenevibe/tvcompanionpoc'
        source = (java / 'AndroidForegroundEvidence.java').read_text() + (java / 'NativeForegroundHistory.java').read_text()
        for forbidden in ('getRunningTasks', 'getRunningAppProcesses', 'AccessibilityService', 'getInstanceId',
                          'getTransportControls', 'java.lang.reflect', 'queryUsageStats', 'SharedPreferences'):
            self.assertNotIn(forbidden, source)
        self.assertIn('usage.queryEvents(0, now)', source)
        self.assertNotIn('now - EVENT_WINDOW_MS', source)


if __name__ == '__main__':
    unittest.main()
