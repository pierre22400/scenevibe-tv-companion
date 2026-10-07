import ast
import copy
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import m6_phase_b_provenance as provenance
from m6_phase_c_provenance import m6_phase_c_added_paths, m6_phase_c_authorized_changes, m6_phase_c_retained_bytes
from m6_phase_d_provenance import m6_phase_d_added_paths, m6_phase_d_production_files

"""Execute pure WALL contracts and reject temporal or provenance weakening.

Actual sources compile with empty classpath/sourcepath. Seven temporary compiled
mutants must fail exact boundary/transition witnesses, never compilation alone.
The immutable 278-file starting inventory and twelve pinned inverses preserve M5;
the real four inherited gates also reject a temporary unlisted document.
"""

ROOT = Path(__file__).resolve().parents[1]
CORE = 'app/src/main/java/com/scenevibe/tvcompanionpoc/wall/'
TEST = 'app/src/test/java/com/scenevibe/tvcompanionpoc/wall/'
RUNNER = 'com.scenevibe.tvcompanionpoc.wall.M6WallContract'
PRODUCTION = {CORE + name + '.java' for name in ('WallEvent', 'WallCalendar', 'WallCalendarScheduler')}
ADDED = PRODUCTION | {TEST + 'M6WallContract.java', TEST + 'M6WallCoreTest.java',
    'tests/m6_phase_b_provenance.py', 'tests/test_m6_phase_b_boundary.py',
    '.github/scripts/m6-phase-b-baseline.json', 'docs/m6-phase-b-wall-models-scheduler-report.md'}
CHANGED = {'.github/scripts/m4-phase-a-test-summary.py', 'tests/m5_phase_d_provenance.py',
    'tests/test_m4_sony_corrective_boundary.py', 'tests/test_m5_phase_b_boundary.py',
    'tests/test_m5_phase_c_boundary.py', 'tests/test_m5_phase_d_boundary.py'} | {
    'tests/test_m4_phase_b_boundary.py', 'tests/test_m4_phase_c_boundary.py',
    'tests/test_m4_phase_d_boundary.py', 'tests/test_m4_phase_e_boundary.py',
    'tests/test_m4_phase_f_boundary.py', 'tests/test_m4_phase_g_boundary.py'}


def executable(path):
    """Inspect dependencies in executable Java while retaining all original teaching comments."""
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', (ROOT / path).read_text(encoding='utf-8'), flags=re.S)


def compile_core(directory, mutations=()):
    """Compile actual source copies and the unchanged standalone contract, with no external classes."""
    empty = directory / 'empty'
    classes = directory / 'classes'
    source = directory / 'source'
    for path in (empty, classes, source):
        path.mkdir()
    files = []
    for relative in sorted(PRODUCTION) + [TEST + 'M6WallContract.java']:
        content = (ROOT / relative).read_text(encoding='utf-8')
        if relative == CORE + 'WallCalendarScheduler.java':
            for before, after in mutations:
                if content.count(before) != 1:
                    raise AssertionError('Missing or ambiguous test-only mutation')
                content = content.replace(before, after, 1)
        target = source / Path(relative).name
        target.write_text(content, encoding='utf-8')
        files.append(str(target))
    java = shutil.which('java')
    if not java:
        raise AssertionError('JDK execution is mandatory')
    javac = shutil.which('javac')
    compiler = [javac] if javac else [java, '-m', 'jdk.compiler/com.sun.tools.javac.Main']
    result = subprocess.run(compiler + ['-encoding', 'UTF-8', '-classpath', str(empty),
        '-sourcepath', str(empty), '-d', str(classes)] + files, capture_output=True, timeout=30, check=False)
    if result.returncode != 0:
        raise AssertionError('Pure JDK compilation failed: ' + result.stderr.decode('utf-8'))
    return classes


def execute_core(classes, witness=None):
    """Run compiled production against all contracts or one exact mutation witness."""
    command = [shutil.which('java'), '-classpath', str(classes), RUNNER]
    if witness is not None:
        command.append(witness)
    return subprocess.run(command, capture_output=True, timeout=30, check=False)


class M6PhaseBBoundaryTest(unittest.TestCase):
    """Protect literal scope, independent models, executed counts and all inherited provenance."""

    @classmethod
    def setUpClass(cls):
        """Compile the actual pure core once for shared classfile and executed-contract checks."""
        cls.temporary = tempfile.TemporaryDirectory(prefix='m6b-pure-')
        cls.addClassCleanup(cls.temporary.cleanup)
        cls.classes = compile_core(Path(cls.temporary.name))

    def test_starting_head_tree_and_inventory_are_independently_pinned(self):
        """A different starting inventory or historical baseline cannot silently bless current bytes."""
        data = provenance.inventory()
        self.assertEqual('92b77df8d62dc6098269f7d61c05f44f0aa0a188', data['referenceHead'])
        self.assertEqual('83ff488a1e1cb32f0966e77fcf41d596afe70d5b', data['referenceTree'])
        self.assertEqual('17cbe36ae99ac7f48aaf861e0d1feac702a9e521', data['baseTvM6'])
        self.assertEqual('5011c91aac61a0cc6dcc74c256a15b7dee03d785', data['baseCloudM6'])
        self.assertEqual(278, len(data['startingBlobs']))
        serialized = json.dumps(data['startingBlobs'], sort_keys=True, separators=(',', ':')).encode('utf-8')
        self.assertEqual('13e36b17bc273bc70b09c7f640a45f495232a55e4fc9371263dbefcc2f58c088',
                         hashlib.sha256(serialized).hexdigest())
        self.assertEqual(CHANGED, set(data['authorizedChanges']))
        self.assertEqual(CHANGED, set(data['admissions']))
        self.assertEqual(ADDED, set(data['additiveFiles']))
        self.assertEqual(PRODUCTION, set(data['productionAdded']))

    def test_every_retained_blob_and_inverse_matches_reconciled_head(self):
        """All MEDIA, Android, Cloud transport, fixtures, baselines, signing and workflow bytes stay exact."""
        for relative, digest in provenance.inventory()['startingBlobs'].items():
            if relative in m6_phase_c_authorized_changes():
                continue
            reconstructed = provenance.restore_blob(relative, m6_phase_c_retained_bytes(ROOT / relative))
            self.assertEqual(digest, provenance.blob_hash(reconstructed), relative)
            if relative not in CHANGED:
                self.assertEqual(digest, provenance.blob_hash(m6_phase_c_retained_bytes(ROOT / relative)), relative)
        self.assertFalse(any(path.startswith('app/') for path in CHANGED))

    def test_exact_whole_repository_inventory_has_no_namespace_admission(self):
        """Tracked and unignored new paths must equal the 278-file start plus nine literal additions."""
        result = subprocess.run(['git', 'ls-files', '--cached', '--others', '--exclude-standard'],
                                cwd=ROOT, capture_output=True, timeout=10, check=True)
        actual = {name for name in result.stdout.decode('utf-8').splitlines()
                  if not ('__pycache__' in Path(name).parts and name.endswith('.pyc'))}
        expected = set(provenance.inventory()['startingBlobs']) | ADDED | (m6_phase_c_added_paths() | m6_phase_d_added_paths())
        self.assertEqual(expected, actual)
        self.assertFalse(any('*' in path or '?' in path for path in ADDED | CHANGED))

    def test_all_added_code_and_document_blobs_are_pinned(self):
        """Pin every addition except the self-referential inventory file itself."""
        pins = provenance.inventory()['addedBlobs']
        self.assertEqual(ADDED - {'.github/scripts/m6-phase-b-baseline.json'}, set(pins))
        for relative, digest in pins.items():
            self.assertEqual(digest, provenance.blob_hash(m6_phase_c_retained_bytes(ROOT / relative)), relative)

    def test_pure_core_has_no_clock_platform_payload_or_io_dependency(self):
        """Inspect actual imports and qualified references independently of successful JDK compilation."""
        forbidden = (r'android(?:x)?\.', r'org\.json', r'java\.(?:io|nio|net|time)\.',
            r'java\.lang\.reflect', r'java\.util\.concurrent', r'\bSystem\.',
            r'\b(?:Thread|Executor|Timer|Handler|Looper|Clock|Date|Calendar|MediaSession|PlaybackState|'
            r'SceneEvent|MediaCalendar|MediaObservation|MediaCalendarScheduler|View|Bitmap|SceneRenderer|'
            r'OverlayService|OverlayManifest|CloudControlClient|InstallationStore|PackageInstaller|'
            r'SharedPreferences|Bundle|Map|Object|ClassLoader|ServiceLoader)\b')
        for relative in PRODUCTION:
            source = executable(relative)
            for expression in forbidden:
                self.assertIsNone(re.search(expression, source), relative + ':' + expression)
            for imported in re.findall(r'\bimport\s+([^;]+);', source):
                self.assertTrue(imported.startswith('java.util.'), relative)

    def test_existing_runtime_never_imports_or_constructs_wall(self):
        """A pure addition does not register WALL, change capabilities or wire a live Banner path."""
        for relative in provenance.inventory()['startingBlobs']:
            if relative.startswith('app/src/main/') and relative.endswith('.java'):
                raw = m6_phase_c_retained_bytes(ROOT / relative).decode('utf-8')
                source = re.sub(r'/\*.*?\*/|//[^\n]*', '', raw, flags=re.S)
                self.assertNotIn('com.scenevibe.tvcompanionpoc.wall', source, relative)
                self.assertIsNone(re.search(r'\b(?:WallEvent|WallCalendar|WallCalendarScheduler)\b', source), relative)
        capabilities = re.sub(r'/\*.*?\*/|//[^\n]*', '',
                              m6_phase_c_retained_bytes(ROOT / 'app/src/main/java/com/scenevibe/tvcompanionpoc/installation/TvCapabilities.java').decode('utf-8'), flags=re.S)
        self.assertIn('supportsWallClockExecution()', capabilities)

    def test_models_have_only_exact_immutable_identity_and_temporal_fields(self):
        """No payload bag, media field, revision, generation, anchor or timer state enters the values."""
        fields = {
            'WallEvent': [('String', 'eventId'), ('long', 'startEpochMs'), ('long', 'endEpochMs')],
            'WallCalendar': [('long', 'horizonStartEpochMs'), ('long', 'horizonEndEpochMs'), ('List<WallEvent>', 'events')],
        }
        for name, expected in fields.items():
            source = executable(CORE + name + '.java')
            self.assertIn('public final class ' + name, source)
            self.assertEqual(expected, re.findall(r'private final ([\w<>]+) (\w+);', source))
        scheduler = executable(CORE + 'WallCalendarScheduler.java')
        self.assertEqual(['calendar', 'selected'], re.findall(r'private (?:WallCalendar|WallEvent) (\w+);', scheduler))
        self.assertIn('enum ExitReason { END, SUPERSEDED, CLOCK_REEVALUATED, CLEAR }', scheduler)

    def test_executed_accounting_is_additive_with_zero_new_skip(self):
        """Keep every M5 bucket and exact XML comparison; add only 68 individually executed WALL cases."""
        self.assertEqual({'M6WallCoreTest': 68}, provenance.inventory()['m6PhaseBSuites'])
        source = (ROOT / '.github/scripts/m4-phase-a-test-summary.py').read_text(encoding='utf-8')
        for token in ("**m5_phase_b['m5PhaseBSuites']", "**m5_phase_c['m5PhaseCSuites']",
                      "**m5_phase_d['m5PhaseDSuites']", "**m6_phase_b['m6PhaseBSuites']",
                      'if suites != expected:', "baseline['allowedOptInSkips']"):
            self.assertIn(token, source)
        for relative in (TEST + 'M6WallCoreTest.java', TEST + 'M6WallContract.java'):
            self.assertNotIn('@Ignore', executable(relative))
            self.assertNotIn('Assume.', executable(relative))

    def test_all_changed_python_preserves_teaching_banners_and_function_docstrings(self):
        """Every new responsibility is documented after imports and every function keeps its docstring."""
        for relative in sorted(path for path in ADDED | CHANGED if path.endswith('.py')):
            tree = ast.parse((ROOT / relative).read_text(encoding='utf-8'))
            last_import = max(i for i, node in enumerate(tree.body) if isinstance(node, (ast.Import, ast.ImportFrom)))
            self.assertTrue(any(isinstance(node, ast.Expr) and isinstance(node.value, ast.Constant)
                and isinstance(node.value.value, str) for node in tree.body[last_import + 1:]), relative)
            for node in ast.walk(tree):
                if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                    self.assertTrue(ast.get_docstring(node), relative + ':' + node.name)

    def test_empty_classpath_produces_only_the_expected_core_and_contract_classes(self):
        """Require actual compiler output with no JUnit, Android stub, JSON or MEDIA classfile."""
        expected = {'WallEvent.class', 'WallCalendar.class', 'WallCalendarScheduler.class',
            'WallCalendarScheduler$Effect.class', 'WallCalendarScheduler$Result.class',
            'WallCalendarScheduler$Kind.class', 'WallCalendarScheduler$ExitReason.class', 'M6WallContract.class'}
        self.assertEqual(expected, {path.name for path in self.classes.rglob('*.class')})

    def test_all_68_contracts_really_execute_on_the_pure_jdk(self):
        """Execute all numerical, ordering, eligibility, reload, jump and immutable-result contracts."""
        result = execute_core(self.classes)
        self.assertEqual(0, result.returncode, result.stderr.decode('utf-8'))
        self.assertEqual('WALL contracts PASS=68 FAIL=0 SKIP=0\n', result.stdout.decode('utf-8'))

    def test_before_after_blobs_and_complete_inverses_are_recognized(self):
        """Require every current whole blob and reconstructed starting blob to roundtrip exactly."""
        for relative, admission in provenance.inventory()['admissions'].items():
            actual = m6_phase_c_retained_bytes(ROOT / relative)
            self.assertEqual(admission['afterSha'], provenance.blob_hash(actual), relative)
            previous = provenance.restore_blob(relative, actual)
            self.assertEqual(admission['beforeSha'], provenance.blob_hash(previous), relative)
            self.assertEqual(previous, provenance.restore_blob(relative, previous), relative)

    def test_unknown_whole_blob_mutations_are_rejected_before_inverse(self):
        """Reject five mutations per admitted path, including duplicate contexts and an extra character."""
        for relative, admission in provenance.inventory()['admissions'].items():
            actual = m6_phase_c_retained_bytes(ROOT / relative)
            mutants = [actual + b' ', actual + b'\n# unknown\n', actual[:-1], b'# unknown\n' + actual,
                       actual + admission['patches'][0]['after'].encode('utf-8')]
            for mutated in mutants:
                with self.assertRaises(ValueError):
                    provenance.restore_blob(relative, mutated)

    def test_missing_ambiguous_or_inexact_inverse_cannot_be_admitted(self):
        """Keep the whole-blob pair fixed while rejecting three inverse corruptions per path."""
        data = provenance.inventory()
        with tempfile.TemporaryDirectory(prefix='m6b-inverse-negative-') as temporary:
            inventory_path = Path(temporary) / 'inventory.json'
            for relative in CHANGED:
                for replacement in ('missing unique inverse context', '\n', None):
                    mutated = copy.deepcopy(data)
                    inverse = mutated['admissions'][relative]['patches'][0]
                    if replacement is None:
                        inverse['before'] += '# unknown inverse drift\n'
                    else:
                        inverse['after'] = replacement
                    inventory_path.write_text(json.dumps(mutated), encoding='utf-8')
                    with patch.object(provenance, 'INVENTORY', inventory_path), self.assertRaises(ValueError):
                        provenance.restore_blob(relative, m6_phase_c_retained_bytes(ROOT / relative))

    def test_new_inventory_cannot_retarget_independent_after_blob_pins(self):
        """A new baseline alone cannot replace any of the twelve independently pinned accepted blobs."""
        data = provenance.inventory()
        with tempfile.TemporaryDirectory(prefix='m6b-pins-negative-') as temporary:
            inventory_path = Path(temporary) / 'inventory.json'
            for relative in CHANGED:
                mutated = copy.deepcopy(data)
                mutated['admissions'][relative]['afterSha'] = '0' * 40
                inventory_path.write_text(json.dumps(mutated), encoding='utf-8')
                with patch.object(provenance, 'INVENTORY', inventory_path), self.assertRaises(ValueError):
                    provenance.inventory()

    def test_all_four_inherited_gates_reject_an_unlisted_document(self):
        """Run the real inherited inventory assertions against one temporary path, then remove it."""
        extra = ROOT / 'docs/m6-phase-b-unlisted-negative-control.md'
        self.assertFalse(extra.exists())
        extra.write_text('Temporary unlisted provenance control.\n', encoding='utf-8')
        names = [
            'test_m4_sony_corrective_boundary.M4SonyCorrectiveBoundaryTest.test_retained_java_tests_fixtures_and_prior_inventories_have_no_exception',
            'test_m5_phase_b_boundary.M5PhaseBBoundaryTest.test_no_unlisted_production_test_config_or_document_file',
            'test_m5_phase_c_boundary.M5PhaseCBoundaryTest.test_no_unlisted_file_in_any_retained_scope',
            'test_m5_phase_d_boundary.M5PhaseDBoundaryTest.test_exact_file_inventory',
        ]
        try:
            result = subprocess.run([sys.executable, '-m', 'unittest'] + names, cwd=ROOT / 'tests',
                                    capture_output=True, timeout=30, check=False)
            output = result.stderr.decode('utf-8')
            self.assertEqual(1, result.returncode)
            self.assertIn('Ran 4 tests', output)
            self.assertIn('FAILED (failures=4)', output)
            self.assertGreaterEqual(output.count('m6-phase-b-unlisted-negative-control.md'), 4)
        finally:
            extra.unlink()

    def assert_mutant_rejected(self, witness, mutations):
        """A mutation counts as detected only after successful compilation and the exact witness failure."""
        original = execute_core(self.classes, witness)
        self.assertEqual(0, original.returncode, original.stderr.decode('utf-8'))
        with tempfile.TemporaryDirectory(prefix='m6b-temporal-mutant-') as temporary:
            classes = compile_core(Path(temporary), mutations)
            result = execute_core(classes, witness)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('WALL contract failed: ' + witness, result.stderr.decode('utf-8'))

    def test_mutant_exclusive_start_diverges(self):
        """Changing inclusive start to exclusive must fail at the exact start epoch."""
        self.assert_mutant_rejected('startExact', [('event.startEpochMs() <= nowEpochMs', 'event.startEpochMs() < nowEpochMs')])

    def test_mutant_inclusive_end_diverges(self):
        """Changing exclusive end to inclusive must fail exact-end retirement."""
        self.assert_mutant_rejected('endExact', [('nowEpochMs < event.endEpochMs()', 'nowEpochMs <= event.endEpochMs()')])

    def test_mutant_oldest_start_diverges(self):
        """Selecting the older overlapping window must fail the newer-start replacement contract."""
        self.assert_mutant_rejected('overlap', [('event.startEpochMs() > best.startEpochMs()', 'event.startEpochMs() < best.startEpochMs()')])

    def test_mutant_largest_id_diverges(self):
        """Reversing the ASCII tie-break must fail same-start selection in the actual core."""
        self.assert_mutant_rejected('equalStart', [('event.eventId().compareTo(best.eventId()) < 0', 'event.eventId().compareTo(best.eventId()) > 0')])

    def test_mutant_due_before_exit_diverges(self):
        """Prepending DUE must fail the ordered EXIT then DUE journal at adjacent windows."""
        self.assert_mutant_rejected('adjacent', [('effects.add(Effect.due(next.eventId()))', 'effects.add(0, Effect.due(next.eventId()))')])

    def test_mutant_replaying_past_window_diverges(self):
        """Removing the end predicate must fail a late load after all windows have ended."""
        self.assert_mutant_rejected('noPastReplay', [('nowEpochMs < event.endEpochMs()', 'event.endEpochMs() > 0L')])

    def test_mutant_suppressing_backward_reselection_diverges(self):
        """A test-only monotonic cursor must fail return to an already selected and expired window."""
        self.assert_mutant_rejected('backwardReselect', [
            ('    private WallEvent selected;', '    private WallEvent selected;\n    private long latestEpochMs = -1L;'),
            ('WallEvent next = eligible ? select(calendar, nowEpochMs) : null;',
             'WallEvent next = eligible ? select(calendar, Math.max(nowEpochMs, latestEpochMs)) : null;\n'
             '        latestEpochMs = Math.max(nowEpochMs, latestEpochMs);'),
        ])


if __name__ == '__main__':
    unittest.main()
