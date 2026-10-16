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
import m6_phase_d_provenance as provenance
from m6_phase_e_provenance import m6_phase_e_added_paths, m6_phase_e_retained_bytes

"""Qualify finite M6 D transport provenance without relaxing the inherited C/B/M5/M4 gates.

The exact starting inventory, immutable historical baselines and retained bytes
are checked independently. Behavioral JVM suites run actual current classes;
inverses are used only for historical provenance. Unknown files, whole-blob
mutations, retargeted pins and broken inverses must fail before acceptance.
"""

ROOT = Path(__file__).resolve().parents[1]
STARTING_INVENTORY_SHA256 = '32e4e625b384deca2ea2da7c36c12d9a479de340bc37115630e90ce93c101ec0'


class M6PhaseDBoundaryTest(unittest.TestCase):
    """Require exact starting provenance, finite additions and individually executed new JVM cases."""

    def test_starting_identity_inventory_and_scope(self):
        """Neither a new baseline nor a filename wildcard can admit another starting tree or scope."""
        data = provenance.inventory()
        self.assertEqual('1f1aa52d01cb5c8211a2956acfaa30c43708c8d2', data['startingHead'])
        self.assertEqual('7e6cce27be7f78a78b62bf468df94860c89f1f50', data['startingTree'])
        encoded = json.dumps(data['startingBlobs'], sort_keys=True, separators=(',', ':')).encode('utf-8')
        self.assertEqual(STARTING_INVENTORY_SHA256, hashlib.sha256(encoded).hexdigest())
        for relative in set(data['admissions']) | provenance.ADDED:
            self.assertNotIn('*', relative)
            self.assertNotIn('?', relative)

    def test_exact_whole_repository_inventory(self):
        """Every tracked or unignored path equals the pinned start plus nine literal additions."""
        result = subprocess.run(['git', 'ls-files', '--cached', '--others', '--exclude-standard'], cwd=ROOT,
                                capture_output=True, timeout=10, check=True)
        actual = {p for p in result.stdout.decode('utf-8').splitlines() if '__pycache__' not in Path(p).parts}
        self.assertEqual(set(provenance.inventory()['startingBlobs']) | provenance.ADDED | m6_phase_e_added_paths(), actual)

    def test_all_retained_bytes_and_complete_inverses(self):
        """Every original source, test, permission, fixture and old baseline reconstructs exact 1f1aa52 bytes."""
        data = provenance.inventory()
        for relative, digest in data['startingBlobs'].items():
            retained = provenance.m6_phase_d_retained_bytes(ROOT / relative)
            self.assertEqual(digest, provenance.blob_hash(retained), relative)
        for relative, admission in data['admissions'].items():
            actual = m6_phase_e_retained_bytes(ROOT / relative)
            self.assertEqual(admission['afterSha'], provenance.blob_hash(actual), relative)
            self.assertEqual(data['startingBlobs'][relative], admission['beforeSha'], relative)
            self.assertEqual(provenance.restore_blob(relative, actual), provenance.restore_blob(relative, retained := provenance.restore_blob(relative, actual)))
            self.assertEqual(admission['beforeSha'], provenance.blob_hash(retained), relative)

    def test_all_added_blobs_are_pinned(self):
        """Only the self-referential new baseline is excluded from exact added-file identities."""
        pins = provenance.inventory()['addedBlobs']
        self.assertEqual(provenance.ADDED - {'.github/scripts/m6-phase-d-baseline.json'}, set(pins))
        for relative, digest in pins.items():
            self.assertEqual(digest, provenance.blob_hash(m6_phase_e_retained_bytes(ROOT / relative)), relative)

    def test_unknown_blob_mutations_and_retargeted_baseline_are_rejected(self):
        """A trailing character or baseline-only pin change cannot authorize unknown whole-file bytes."""
        data = provenance.inventory()
        with tempfile.TemporaryDirectory(prefix='m6d-pins-') as temporary:
            path = Path(temporary) / 'inventory.json'
            for relative in data['admissions']:
                actual = m6_phase_e_retained_bytes(ROOT / relative)
                for mutant in (actual + b' ', actual[:-1], b'unknown\n' + actual):
                    with self.assertRaises(ValueError):
                        provenance.restore_blob(relative, mutant)
                changed = copy.deepcopy(data)
                changed['admissions'][relative]['afterSha'] = '0' * 40
                path.write_text(json.dumps(changed), encoding='utf-8')
                with patch.object(provenance, 'INVENTORY', path), self.assertRaises(ValueError):
                    provenance.inventory()

    def test_missing_ambiguous_and_inexact_inverse_are_rejected(self):
        """Exact blob pins do not excuse an inverse with missing, repeated or altered reconstruction context."""
        data = provenance.inventory()
        with tempfile.TemporaryDirectory(prefix='m6d-inverses-') as temporary:
            path = Path(temporary) / 'inventory.json'
            for relative in data['admissions']:
                for kind in ('missing', 'ambiguous', 'inexact'):
                    changed = copy.deepcopy(data)
                    inverse = changed['admissions'][relative]['patches'][0]
                    if kind == 'missing':
                        inverse['after'] = 'missing whole-file context'
                    elif kind == 'ambiguous':
                        inverse['after'] = '\n'
                    else:
                        inverse['before'] += 'unknown inverse drift\n'
                    path.write_text(json.dumps(changed), encoding='utf-8')
                    with patch.object(provenance, 'INVENTORY', path), self.assertRaises(ValueError):
                        provenance.restore_blob(relative, (ROOT / relative).read_bytes())

    def test_executed_counts_are_additive_and_live_wall_remains_deferred(self):
        """All 19 new JVM cases are required with zero new skips and unchanged public capabilities."""
        self.assertEqual({'M6PackageTransportTest': 19}, provenance.inventory()['m6PhaseDSuites'])
        source = (ROOT / 'app/src/test/java/com/scenevibe/tvcompanionpoc/M6PackageTransportTest.java').read_text(encoding='utf-8')
        self.assertEqual(19, source.count('@Test'))
        self.assertNotIn('@Ignore', source)
        self.assertNotIn('Assume.', source)
        summary = (ROOT / '.github/scripts/m4-phase-a-test-summary.py').read_text(encoding='utf-8')
        for token in ("expected.update(m6_phase_d['m6PhaseDSuites'])", 'if suites != expected:', "baseline['allowedOptInSkips']"):
            self.assertIn(token, summary)
        capabilities = m6_phase_e_retained_bytes(ROOT / 'app/src/main/java/com/scenevibe/tvcompanionpoc/installation/TvCapabilities.java').decode('utf-8')
        self.assertIn('supportsWallClockExecution() {return false;}', capabilities)

    def test_changed_python_has_teaching_banners_and_every_function_docstring(self):
        """New responsibilities retain the post-import banner and pedagogical docstrings."""
        paths = set(provenance.inventory()['admissions']) | provenance.ADDED
        for relative in sorted(p for p in paths if p.endswith('.py')):
            tree = ast.parse((ROOT / relative).read_text(encoding='utf-8'))
            last_import = max(i for i, node in enumerate(tree.body) if isinstance(node, (ast.Import, ast.ImportFrom)))
            self.assertTrue(any(isinstance(node, ast.Expr) and isinstance(node.value, ast.Constant)
                                and isinstance(node.value.value, str) for node in tree.body[last_import + 1:]), relative)
            for node in ast.walk(tree):
                if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                    self.assertTrue(ast.get_docstring(node), relative + ':' + node.name)

    def test_every_inventory_gate_rejects_an_unlisted_document(self):
        """The real D/C/B/M5/Sony inventory assertions reject the same unlisted negative control."""
        extra = ROOT / 'docs/m6-phase-d-unlisted-negative-control.md'
        self.assertFalse(extra.exists())
        extra.write_text('Unlisted M6 D negative control.\n', encoding='utf-8')
        names = [
            'test_m6_phase_d_boundary.M6PhaseDBoundaryTest.test_exact_whole_repository_inventory',
            'test_m6_phase_c_boundary.M6PhaseCBoundaryTest.test_exact_whole_repository_inventory_has_no_namespace_admission',
            'test_m6_phase_b_boundary.M6PhaseBBoundaryTest.test_exact_whole_repository_inventory_has_no_namespace_admission',
            'test_m5_phase_d_boundary.M5PhaseDBoundaryTest.test_exact_file_inventory',
            'test_m5_phase_c_boundary.M5PhaseCBoundaryTest.test_no_unlisted_file_in_any_retained_scope',
            'test_m5_phase_b_boundary.M5PhaseBBoundaryTest.test_no_unlisted_production_test_config_or_document_file',
            'test_m4_sony_corrective_boundary.M4SonyCorrectiveBoundaryTest.test_retained_java_tests_fixtures_and_prior_inventories_have_no_exception',
        ]
        try:
            result = subprocess.run([sys.executable, '-m', 'unittest'] + names, cwd=ROOT / 'tests', capture_output=True, timeout=60, check=False)
            output = result.stderr.decode('utf-8')
            self.assertEqual(1, result.returncode, output)
            self.assertIn('FAILED (failures=7)', output)
            self.assertGreaterEqual(output.count(extra.name), 7)
        finally:
            extra.unlink()


if __name__ == '__main__':
    unittest.main()
