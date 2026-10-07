import ast
import copy
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import m6_phase_c_provenance as provenance

"""Admit the finite M6 Phase C WALL runtime integration and reject any provenance weakening.

Phase C owns the TV-local WALL runtime: a common runtime owner, the Android WALL clock
driver, the local Banner handler/codec, restore and Banner-aware autostart, plus the WALL
diagnostics taxonomy. This gate independently pins the frozen 2d2c22a starting inventory,
asserts the exact whole-repository inventory equals that start plus the literal Phase C
additions, pins every Phase C-added blob, pins the eight production admissions and their
whole-file inverses to the exact frozen byte, enforces the Video/MEDIA non-regression
invariants (MediaCalendarScheduler and the pure wall/*.java byte-unchanged, TvCapabilities
Video constants unchanged, no new Android permission), checks additive executed counts with
zero new skip, and runs a negative control proving an unlisted file is rejected by the
Phase C and every inherited inventory gate. It never rewrites or weakens an older baseline.
"""

ROOT = Path(__file__).resolve().parents[1]
FROZEN = '2d2c22ac2926531ce728575ab7b87a4b5a66fd99'
JAVA = 'app/src/main/java/com/scenevibe/tvcompanionpoc/'
WALL = JAVA + 'wall/'
BASELINE = ROOT / '.github/scripts/m6-phase-c-baseline.json'
# The frozen 2d2c22a identities of the files Phase C must never change.
FROZEN_WALL = {
    WALL + 'WallEvent.java': 'bf5b5d4d57a8689bb9fe1796f2d109497bc52540',
    WALL + 'WallCalendar.java': 'bbb068c6f55414c421788da8f1f7a84eff9e692e',
    WALL + 'WallCalendarScheduler.java': '40e85e91d9e93a5884854f6094fbca910e16513a',
}
FROZEN_MEDIA = {JAVA + 'calendar/MediaCalendarScheduler.java': '817447e570b8e1ee6ecd1e9eaeaa4eb1f8eb5c19'}


def blob_hash(content):
    """Compute the complete Git blob identity, including documentation and whitespace."""
    return hashlib.sha1(b'blob ' + str(len(content)).encode('ascii') + b'\0' + content).hexdigest()


def code_only(source):
    """Inspect executable references without treating explanatory comments as dependencies."""
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.S)


class M6PhaseCBoundaryTest(unittest.TestCase):
    """Protect the exact finite Phase C scope, every inverse and the Video/MEDIA non-regression."""

    def test_starting_head_tree_and_scope_are_independently_pinned(self):
        """A different starting head, tree or admission set cannot silently bless current bytes."""
        data = provenance.inventory()
        self.assertEqual(FROZEN, data['startingHead'])
        tree = subprocess.run(['git', 'rev-parse', FROZEN + '^{tree}'], cwd=ROOT,
                              capture_output=True, timeout=10, check=True).stdout.decode().strip()
        self.assertEqual(tree, data['startingTree'])
        self.assertEqual(provenance.m6_phase_c_authorized_changes(), set(data['authorizedChanges']))
        self.assertEqual(provenance.m6_phase_c_production_files(), set(data['productionAdded']))
        production = {path for path, item in data['admissions'].items() if item['kind'] == 'production'}
        gates = {path for path, item in data['admissions'].items() if item['kind'] == 'inherited-gate'}
        self.assertEqual(provenance.m6_phase_c_authorized_changes(), production)
        self.assertEqual(provenance.INHERITED_GATES, gates)
        for path in data['additiveFiles']:
            self.assertNotIn('*', path)
            self.assertNotIn('?', path)

    def test_exact_whole_repository_inventory_has_no_namespace_admission(self):
        """Tracked and unignored new paths must equal the frozen start plus the literal Phase C additions."""
        result = subprocess.run(['git', 'ls-files', '--cached', '--others', '--exclude-standard'],
                                cwd=ROOT, capture_output=True, timeout=10, check=True)
        actual = {name for name in result.stdout.decode('utf-8').splitlines()
                  if not ('__pycache__' in Path(name).parts and name.endswith('.pyc'))}
        frozen_tree = subprocess.run(['git', 'ls-tree', '-r', '--name-only', FROZEN],
                                     cwd=ROOT, capture_output=True, timeout=10, check=True)
        start = set(frozen_tree.stdout.decode('utf-8').splitlines())
        expected = start | provenance.m6_phase_c_added_paths()
        self.assertEqual(expected, actual)
        for path in provenance.m6_phase_c_added_paths() | provenance.m6_phase_c_authorized_changes():
            self.assertNotIn('*', path)
            self.assertNotIn('?', path)

    def test_every_phase_c_added_blob_is_pinned(self):
        """Pin every addition except the self-referential baseline and the evolving planner feature file."""
        pins = provenance.inventory()['addedBlobs']
        exclude = {'.github/scripts/m6-phase-c-baseline.json',
                   '.agents/tasks/task-m6-phase-c-wall-android-driver-common-owner/features/FEAT-005.json'}
        self.assertEqual(provenance.m6_phase_c_added_paths() - exclude, set(pins))
        for relative, digest in pins.items():
            self.assertEqual(digest, blob_hash((ROOT / relative).read_bytes()), relative)

    def test_production_admissions_reverse_to_the_exact_frozen_byte(self):
        """Each of the eight edited production files reconstructs its exact frozen 2d2c22a byte."""
        for relative, admission in provenance.inventory()['admissions'].items():
            if admission['kind'] != 'production':
                continue
            actual = (ROOT / relative).read_bytes()
            self.assertEqual(admission['afterSha'], blob_hash(actual), relative)
            frozen = subprocess.run(['git', 'show', FROZEN + ':' + relative], cwd=ROOT,
                                    capture_output=True, timeout=10, check=True).stdout
            self.assertEqual(admission['beforeSha'], blob_hash(frozen), relative)
            restored = provenance.m6_phase_c_retained_bytes(ROOT / relative)
            self.assertEqual(frozen, restored, relative)
            self.assertEqual(admission['beforeSha'], blob_hash(restored), relative)

    def test_inherited_gate_admissions_reverse_to_the_exact_pre_phase_c_byte(self):
        """Each inherited gate Phase C edits reconstructs its exact pre-Phase-C (frozen Phase B era) byte."""
        for relative, admission in provenance.inventory()['admissions'].items():
            if admission['kind'] != 'inherited-gate':
                continue
            actual = (ROOT / relative).read_bytes()
            self.assertEqual(admission['afterSha'], blob_hash(actual), relative)
            restored = provenance.m6_phase_c_retained_bytes(ROOT / relative)
            self.assertEqual(admission['beforeSha'], blob_hash(restored), relative)
            self.assertEqual(restored, provenance.m6_phase_c_retained_bytes(ROOT / relative), relative)

    def test_unknown_whole_blob_mutations_are_rejected_before_inverse(self):
        """Reject mutations per admitted path, including a trailing character and an extra line."""
        for relative, admission in provenance.inventory()['admissions'].items():
            actual = (ROOT / relative).read_bytes()
            mutants = [actual + b' ', actual + b'\n// unknown\n', actual[:-1], b'// unknown\n' + actual]
            for mutated in mutants:
                with self.assertRaises(ValueError):
                    provenance.restore_blob(relative, mutated)

    def test_new_inventory_cannot_retarget_an_independent_after_blob_pin(self):
        """A rewritten baseline alone cannot replace any independently pinned accepted blob."""
        data = provenance.inventory()
        with tempfile.TemporaryDirectory(prefix='m6c-pins-negative-') as temporary:
            inventory_path = Path(temporary) / 'inventory.json'
            for relative in list(data['admissions']):
                mutated = copy.deepcopy(data)
                mutated['admissions'][relative]['afterSha'] = '0' * 40
                inventory_path.write_text(json.dumps(mutated), encoding='utf-8')
                with patch.object(provenance, 'INVENTORY', inventory_path), self.assertRaises(ValueError):
                    provenance.restore_blob(relative, (ROOT / relative).read_bytes())

    def test_pure_wall_core_and_media_scheduler_are_byte_unchanged(self):
        """The pure WALL models/scheduler and the M5 MediaCalendarScheduler stay exactly the frozen bytes."""
        for relative, digest in {**FROZEN_WALL, **FROZEN_MEDIA}.items():
            self.assertEqual(digest, blob_hash((ROOT / relative).read_bytes()), relative)
            self.assertNotIn(relative, provenance.m6_phase_c_authorized_changes())
            self.assertNotIn(relative, provenance.m6_phase_c_production_files())

    def test_wall_android_driver_lives_outside_the_pure_wall_package(self):
        """The Android WALL clock/driver/timer code must not enter the clock-free, payload-free wall/ package."""
        wall_dir = ROOT / 'app/src/main/java/com/scenevibe/tvcompanionpoc/wall'
        self.assertEqual({'WallEvent.java', 'WallCalendar.java', 'WallCalendarScheduler.java'},
                         {p.name for p in wall_dir.glob('*.java')})
        scheduler = code_only((ROOT / (WALL + 'WallCalendarScheduler.java')).read_text(encoding='utf-8'))
        for forbidden in ('android', 'System.currentTimeMillis', 'elapsedRealtime', 'Handler', 'postDelayed'):
            self.assertNotIn(forbidden, scheduler)

    def test_wall_driver_constants_and_banner_profile_are_the_tested_values(self):
        """The driver constants and the Banner codec/profile are exactly those qualified by the JVM suites."""
        driver = (ROOT / (JAVA + 'WallClockDriver.java')).read_text(encoding='utf-8')
        for token in ('READ_WINDOW_MS', 'MAX_IMMEDIATE_RETRIES', 'DRIFT_THRESHOLD_MS', 'WALL_MAX_WAIT_MS'):
            self.assertIn(token, driver)
        capabilities = (ROOT / (JAVA + 'installation/TvCapabilities.java')).read_text(encoding='utf-8')
        self.assertIn('scenevibe.banner-wall-overlay.v1', capabilities)
        handler = (ROOT / (JAVA + 'BannerInstallationHandler.java')).read_text(encoding='utf-8')
        self.assertIn('TvCapabilities.CODEC_BANNER_WALL_OVERLAY', handler)

    def test_tv_capabilities_video_constants_are_unchanged(self):
        """Phase C must not alter the advertised Video codec/contract/version constant values."""
        capabilities = provenance.m6_phase_c_retained_bytes(ROOT / (JAVA + 'installation/TvCapabilities.java')).decode('utf-8')
        current = (ROOT / (JAVA + 'installation/TvCapabilities.java')).read_text(encoding='utf-8')
        for constant in re.findall(r'(CODEC_[A-Z_]+\s*=\s*"[^"]*")', capabilities):
            self.assertIn(constant, current, constant)
        self.assertIn('supportsWallClockExecution()', current)

    def test_no_new_android_permission_is_added(self):
        """The AndroidManifest permission set must be byte-identical to the frozen 2d2c22a manifest."""
        manifest = 'app/src/main/AndroidManifest.xml'
        frozen = subprocess.run(['git', 'show', FROZEN + ':' + manifest], cwd=ROOT,
                                capture_output=True, timeout=10, check=True).stdout.decode('utf-8')
        current = (ROOT / manifest).read_text(encoding='utf-8')
        frozen_perms = sorted(re.findall(r'uses-permission[^>]*android:name="([^"]+)"', frozen))
        current_perms = sorted(re.findall(r'uses-permission[^>]*android:name="([^"]+)"', current))
        self.assertEqual(frozen_perms, current_perms)

    def test_executed_counts_are_additive_with_zero_new_skip(self):
        """The three Phase C JVM suites add only their individually executed cases and no new skip."""
        self.assertEqual({'M6BannerOwnerTest': 15, 'M6WallDriverTest': 25, 'M6BannerHandlerTest': 23},
                         provenance.inventory()['m6PhaseCSuites'])
        for relative in ('M6BannerOwnerTest', 'M6WallDriverTest', 'M6BannerHandlerTest'):
            source = (ROOT / ('app/src/test/java/com/scenevibe/tvcompanionpoc/' + relative + '.java')).read_text(encoding='utf-8')
            self.assertNotIn('@Ignore', source)
            self.assertNotIn('Assume.', source)
            self.assertEqual(provenance.inventory()['m6PhaseCSuites'][relative], source.count('@Test'))

    def test_all_phase_c_and_inherited_gates_reject_an_unlisted_document(self):
        """The real Phase C and inherited inventory assertions reject one temporary unlisted path."""
        extra = ROOT / 'docs/m6-phase-c-unlisted-negative-control.md'
        self.assertFalse(extra.exists())
        extra.write_text('Temporary unlisted Phase C provenance control.\n', encoding='utf-8')
        names = [
            'test_m6_phase_c_boundary.M6PhaseCBoundaryTest.test_exact_whole_repository_inventory_has_no_namespace_admission',
            'test_m6_phase_b_boundary.M6PhaseBBoundaryTest.test_exact_whole_repository_inventory_has_no_namespace_admission',
            'test_m5_phase_d_boundary.M5PhaseDBoundaryTest.test_exact_file_inventory',
            'test_m5_phase_c_boundary.M5PhaseCBoundaryTest.test_no_unlisted_file_in_any_retained_scope',
            'test_m5_phase_b_boundary.M5PhaseBBoundaryTest.test_no_unlisted_production_test_config_or_document_file',
            'test_m4_sony_corrective_boundary.M4SonyCorrectiveBoundaryTest.test_retained_java_tests_fixtures_and_prior_inventories_have_no_exception',
        ]
        try:
            result = subprocess.run([sys.executable, '-m', 'unittest'] + names, cwd=ROOT / 'tests',
                                    capture_output=True, timeout=60, check=False)
            output = result.stderr.decode('utf-8')
            self.assertEqual(1, result.returncode, output)
            self.assertIn('Ran ' + str(len(names)) + ' tests', output)
            self.assertIn('FAILED (failures=' + str(len(names)) + ')', output)
            self.assertGreaterEqual(output.count('m6-phase-c-unlisted-negative-control.md'), len(names))
        finally:
            extra.unlink()

    def test_phase_c_python_keeps_teaching_banners_and_function_docstrings(self):
        """Every new or modified Phase C Python file documents its scope and each function."""
        changed = ['tests/m6_phase_c_provenance.py', 'tests/test_m6_phase_c_boundary.py']
        changed += sorted(provenance.INHERITED_GATES)
        for relative in changed:
            tree = ast.parse((ROOT / relative).read_text(encoding='utf-8'))
            last_import = max(i for i, node in enumerate(tree.body) if isinstance(node, (ast.Import, ast.ImportFrom)))
            self.assertTrue(any(isinstance(node, ast.Expr) and isinstance(node.value, ast.Constant)
                                and isinstance(node.value.value, str) for node in tree.body[last_import + 1:]), relative)
            for node in ast.walk(tree):
                if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                    self.assertTrue(ast.get_docstring(node), relative + ':' + node.name)

    def test_phase_b_baseline_and_media_scheduler_are_not_rewritten(self):
        """Phase C never rewrites the frozen Phase B baseline or the frozen MEDIA scheduler."""
        phase_b_baseline = '.github/scripts/m6-phase-b-baseline.json'
        self.assertEqual('13e36b17bc273bc70b09c7f640a45f495232a55e4fc9371263dbefcc2f58c088',
                         self._phase_b_starting_hash(phase_b_baseline))
        self.assertNotIn(phase_b_baseline, provenance.m6_phase_c_added_paths())
        self.assertNotIn(phase_b_baseline, set(provenance.inventory()['admissions']))

    def _phase_b_starting_hash(self, phase_b_baseline):
        """Recompute the canonical Phase B startingBlobs sha256 to prove it is untouched."""
        data = json.loads((ROOT / phase_b_baseline).read_text(encoding='utf-8'))
        serialized = json.dumps(data['startingBlobs'], sort_keys=True, separators=(',', ':')).encode('utf-8')
        return hashlib.sha256(serialized).hexdigest()


if __name__ == '__main__':
    unittest.main()
