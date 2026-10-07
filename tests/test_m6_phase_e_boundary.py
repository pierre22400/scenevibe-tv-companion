import ast
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import m6_phase_e_provenance as provenance

"""Qualify the finite E blocked-report overlay without claiming any Phase E software PASS.

The exact D tree is retained, and no production source or historical baseline is
admitted. Negative controls reject unlisted paths, mutated blobs, baseline-only
retargeting and broken inverses. Every new Python function remains documented.
"""

ROOT = Path(__file__).resolve().parents[1]
HELPER_BLOB = '3a14c379d76e311c67a71b05651f1d35dbf8b0c6'


class M6PhaseEBoundaryTest(unittest.TestCase):
    """Prove the blocked report is finite and the complete qualified D software is unchanged."""

    def test_exact_start_inventory_and_literal_scope(self):
        """Every tracked/unignored file equals the frozen D tree plus four explicit E additions."""
        data = provenance.inventory()
        result = subprocess.run(['git', 'ls-files', '--cached', '--others', '--exclude-standard'],
                                cwd=ROOT, capture_output=True, timeout=10, check=True)
        actual = {name for name in result.stdout.decode('utf-8').splitlines()
                  if '__pycache__' not in Path(name).parts}
        self.assertEqual(set(data['startingBlobs']) | provenance.ADDED, actual)
        self.assertEqual(325, len(data['startingBlobs']))
        for relative in set(data['admissions']) | provenance.ADDED:
            self.assertNotIn('*', relative)
            self.assertNotIn('?', relative)

    def test_every_phase_d_blob_reconstructs_exactly(self):
        """All D production, build, permission, fixture and historical baseline bytes remain exact."""
        data = provenance.inventory()
        for relative, digest in data['startingBlobs'].items():
            self.assertEqual(digest, provenance.blob_hash(provenance.m6_phase_e_retained_bytes(ROOT / relative)), relative)
        self.assertEqual({'tests/m6_phase_d_provenance.py', 'tests/test_m6_phase_d_boundary.py'}, set(data['admissions']))
        self.assertEqual([], data['productionAdded'])
        self.assertFalse(data['runtimeChanged'])

    def test_new_blobs_and_helper_identity_are_pinned(self):
        """Only the self-referential E baseline is excluded from whole-blob identities."""
        pins = provenance.inventory()['addedBlobs']
        self.assertEqual(provenance.ADDED - {'.github/scripts/m6-phase-e-baseline.json'}, set(pins))
        self.assertEqual(HELPER_BLOB, pins['tests/m6_phase_e_provenance.py'])
        for relative, digest in pins.items():
            self.assertEqual(digest, provenance.blob_hash((ROOT / relative).read_bytes()), relative)

    def test_unknown_mutations_retargeting_and_broken_inverse_are_rejected(self):
        """No baseline-only edit or arbitrary source/inverse mutation can admit other current bytes."""
        data = provenance.inventory()
        with tempfile.TemporaryDirectory(prefix='m6e-provenance-') as temporary:
            target = Path(temporary) / 'inventory.json'
            for relative in data['admissions']:
                current = (ROOT / relative).read_bytes()
                with self.assertRaises(ValueError):
                    provenance.restore_blob(relative, current + b'unknown')
                changed = copy.deepcopy(data)
                changed['admissions'][relative]['afterSha'] = '0' * 40
                target.write_text(json.dumps(changed), encoding='utf-8')
                with patch.object(provenance, 'INVENTORY', target), self.assertRaises(ValueError):
                    provenance.inventory()
                changed = copy.deepcopy(data)
                changed['admissions'][relative]['patches'][0]['before'] += 'unknown inverse drift\n'
                target.write_text(json.dumps(changed), encoding='utf-8')
                with patch.object(provenance, 'INVENTORY', target), self.assertRaises(ValueError):
                    provenance.restore_blob(relative, current)

    def test_unknown_document_is_rejected_by_actual_e_inventory(self):
        """One temporary path must fail the exact E gate and is removed after the negative control."""
        extra = ROOT / 'docs/m6-phase-e-unlisted-negative-control.md'
        self.assertFalse(extra.exists())
        extra.write_text('Unlisted Phase E negative control.\n', encoding='utf-8')
        try:
            result = subprocess.run([sys.executable, '-m', 'unittest',
                'test_m6_phase_e_boundary.M6PhaseEBoundaryTest.test_exact_start_inventory_and_literal_scope'],
                cwd=ROOT / 'tests', capture_output=True, timeout=30, check=False)
            self.assertEqual(1, result.returncode)
            self.assertIn(extra.name, result.stderr.decode('utf-8'))
        finally:
            extra.unlink()

    def test_changed_python_retains_teaching_banners_and_function_docstrings(self):
        """Every new/modified Python file has a post-import banner and documented functions."""
        paths = set(provenance.inventory()['admissions']) | provenance.ADDED
        for relative in sorted(path for path in paths if path.endswith('.py')):
            tree = ast.parse((ROOT / relative).read_text(encoding='utf-8'))
            last_import = max(index for index, node in enumerate(tree.body)
                              if isinstance(node, (ast.Import, ast.ImportFrom)))
            self.assertTrue(any(isinstance(node, ast.Expr) and isinstance(node.value, ast.Constant)
                                and isinstance(node.value.value, str) for node in tree.body[last_import + 1:]), relative)
            for node in ast.walk(tree):
                if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                    self.assertTrue(ast.get_docstring(node), relative + ':' + node.name)


if __name__ == '__main__':
    unittest.main()
