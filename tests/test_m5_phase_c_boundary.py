from m5_phase_d_provenance import phase_d_added_paths, phase_d_changed_paths, phase_d_retained_bytes
import ast
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
from m5_phase_c_provenance import blob_hash, phase_c_retained_bytes, phase_c_production_files
from m6_phase_b_provenance import m6_phase_b_added_paths, m6_phase_b_production_files
from m6_phase_c_provenance import m6_phase_c_added_paths, m6_phase_c_authorized_changes, m6_phase_c_inherited_jvm_tests
from m6_phase_d_provenance import m6_phase_d_added_paths, m6_phase_d_production_files, m6_phase_d_retained_bytes

"""Bind the non-live candidate to the accepted B tree and actual differential gates.

Complete Git blobs protect every retained source, fixture, baseline and teaching comment.
The only reversible edits admit exact C paths or count executed cases. This gate also
executes a real empty-classpath JDK compile of all four temporal core classes.
"""

ROOT = Path(__file__).resolve().parents[1]
BASE = json.loads((ROOT / '.github/scripts/m5-phase-c-baseline.json').read_text(encoding='utf-8'))
JAVA = 'app/src/main/java/com/scenevibe/tvcompanionpoc/'
TEST = 'app/src/test/java/com/scenevibe/tvcompanionpoc/'
ADDED = {
    JAVA + 'calendar/MediaCalendarScheduler.java', JAVA + 'VideoMediaObservationAdapter.java',
    TEST + 'M5CDifferentialHarness.java', TEST + 'M5CandidateDifferentialTest.java',
    TEST + 'M5SchedulerDirectTest.java', TEST + 'M5VideoObservationAdapterTest.java',
    TEST + 'M5SinkFailureDifferentialTest.java', TEST + 'M5NegativeSensitivityTest.java',
    'app/src/androidTest/java/com/scenevibe/tvcompanionpoc/M5MediaDifferentialInstrumentation.java',
    '.github/scripts/m5-phase-c-baseline.json', '.github/scripts/m5-media-differential.init.gradle',
    '.github/scripts/m5-media-differential.sh', '.github/scripts/m5-media-differential-summary.py',
    '.github/workflows/android-media-differential.yml', 'tests/m5_phase_c_provenance.py',
    'tests/test_m5_phase_c_boundary.py', 'docs/m5-phase-c-scheduler-differential-report.md',
}
CHANGED = {
    'tests/test_m4_phase_b_boundary.py', 'tests/test_m4_phase_c_boundary.py',
    'tests/test_m4_phase_d_boundary.py', 'tests/test_m4_phase_e_boundary.py',
    'tests/test_m4_phase_f_boundary.py', 'tests/test_m4_phase_g_boundary.py',
    'tests/test_m4_sony_corrective_boundary.py', 'tests/m5_phase_b_provenance.py',
    'tests/test_m5_phase_b_boundary.py', '.github/scripts/m4-phase-a-test-summary.py',
}


def code(path):
    """Inspect actual executable Java references without rejecting educational comments."""
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', (ROOT / path).read_text(encoding='utf-8'), flags=re.S)


class M5PhaseCBoundaryTest(unittest.TestCase):
    """Require exact scope, purity, non-live consumers and real executed accounting."""

    def test_exact_bases_inventory_and_inverse_paths(self):
        """The immutable start and two production additions cannot widen through a baseline edit."""
        self.assertEqual('46000bf11961572e0cc6003dc1b46094eeb9d846', BASE['referenceHead'])
        self.assertEqual('67b81045258b1692073c6927b956db4899c6ad1a', BASE['baseTvM5'])
        self.assertEqual('5011c91aac61a0cc6dcc74c256a15b7dee03d785', BASE['baseCloudM5'])
        self.assertEqual(ADDED, set(BASE['additiveFiles']))
        self.assertEqual(CHANGED, set(BASE['authorizedChanges']))
        self.assertEqual(CHANGED, set(BASE['provenancePatches']))
        self.assertEqual(phase_c_production_files(), set(BASE['productionAdded']))
        self.assertEqual(set(BASE['startingBlobs']) - CHANGED, set(BASE['forbiddenChanges']))
        self.assertEqual(BASE['phaseBBaselineBlob'], blob_hash((ROOT / '.github/scripts/m5-phase-b-baseline.json').read_bytes()))
        for path in ADDED | CHANGED:
            self.assertNotIn('*', path)
            self.assertTrue((ROOT / path).is_file(), path)

    def test_every_retained_blob_including_models_oracle_owner_and_workflows(self):
        """No inverse exists for production or fixtures; complete final-B bytes are independently restored."""
        for path, digest in BASE['startingBlobs'].items():
            if path in m6_phase_c_authorized_changes():
                continue
            # The reversing provenance chain must restore the exact frozen byte for every retained
            # path, including the one frozen inherited JVM test M6 Phase C adapts (TvCapabilitiesTest):
            # m6_phase_c_retained_bytes undoes the Phase C edit to the exact pre-Phase-C byte.
            self.assertEqual(digest, blob_hash(phase_c_retained_bytes(ROOT / path)), path)
            if path not in CHANGED | phase_d_changed_paths() | m6_phase_c_inherited_jvm_tests():
                self.assertEqual(digest, blob_hash(m6_phase_d_retained_bytes(ROOT / path)), path)
        self.assertFalse(any(path.startswith('app/') for path in CHANGED))
        old_b = json.loads((ROOT / '.github/scripts/m5-phase-b-baseline.json').read_text())
        self.assertEqual(old_b['oracleSources'], BASE['oracleSources'])

    def test_no_unlisted_file_in_any_retained_scope(self):
        """A namespace wildcard, extra engine or hidden fixture cannot pass the finite inventory."""
        expected = set(BASE['startingBlobs']) | ADDED | phase_d_added_paths() | {'docs/m5-final-sony-physical-closure.md'} | {'docs/scenevibe-os-m6-banner-wall-clock-architecture.md', 'docs/m6-phase-a-architecture-report.md'} | m6_phase_b_added_paths() | (m6_phase_c_added_paths() | m6_phase_d_added_paths())
        actual = set()
        for directory in ('app/src', 'tests', '.github', 'docs'):
            for path in (ROOT / directory).rglob('*'):
                if path.is_file() and '__pycache__' not in path.parts:
                    actual.add(str(path.relative_to(ROOT)))
        self.assertEqual({p for p in expected if p.startswith(('app/src/', 'tests/', '.github/', 'docs/'))}, actual)

    def test_four_core_classes_compile_with_empty_classpath_and_sourcepath(self):
        """Execute the host JDK with no Android dependency and assert exactly four core classes plus Sink."""
        compiler = [shutil.which('javac')] if shutil.which('javac') else [shutil.which('java'), '-m', 'jdk.compiler/com.sun.tools.javac.Main']
        with tempfile.TemporaryDirectory(prefix='m5c-pure-') as temporary:
            empty = Path(temporary) / 'empty'
            output = Path(temporary) / 'classes'
            empty.mkdir()
            output.mkdir()
            names = ('SceneEvent', 'MediaCalendar', 'MediaObservation', 'MediaCalendarScheduler')
            result = subprocess.run(compiler + ['-classpath', str(empty), '-sourcepath', str(empty), '-d', str(output)]
                                    + [str(ROOT / (JAVA + 'calendar/' + name + '.java')) for name in names],
                                    capture_output=True, timeout=30, check=False)
            self.assertEqual(0, result.returncode, result.stderr.decode('utf-8'))
            self.assertEqual({name + '.class' for name in names} | {'MediaCalendarScheduler$Sink.class'}, {p.name for p in output.rglob('*.class')})

    def test_core_is_passive_silent_and_serialized_with_exact_sink_types(self):
        """Reject platform, clock, payload, reflection, threads, persistence and deferred callback machinery."""
        source = code(JAVA + 'calendar/MediaCalendarScheduler.java')
        for token in (r'android', r'org\.json', r'java\.(io|nio|net|time)', r'java\.lang\.reflect',
                      r'java\.util\.concurrent', r'\bSystem\.', r'\b(Thread|Executor|Timer|Handler|Clock|Date|Bitmap|Object|Bundle|ScheduledTrack|InstallationStore|CloudControlClient)\b',
                      r'\btry\b', r'\bcatch\b'):
            self.assertIsNone(re.search(token, source), token)
        self.assertEqual(4, len(re.findall(r'public synchronized void ', source)))
        self.assertEqual(['onEligibility', 'onPlayback', 'onDue', 'onExpire'], re.findall(r'void (on\w+)\(', source.split('private final Sink', 1)[0]))
        for expression in ('new HashSet<>()', 'new HashMap<>()', 'deltaMs > FORWARD_SEEK_THRESHOLD_MS', 'deltaMs < -BACKWARD_SEEK_THRESHOLD_MS', 'final String token = activationToken;'):
            self.assertIn(expression, source)

    def test_adapter_is_external_and_old_callers_remain_nonlive(self):
        """The exact C boundary remains non-live; D separately proves the sole actual live engine."""
        adapter = code(JAVA + 'VideoMediaObservationAdapter.java')
        for token in ('track == null || snapshot == null', 'MediaIdentityMatcher.matches(track, snapshot)',
                      'snapshot.estimatedPositionMs >= 0L', 'snapshot.state == PlaybackState.STATE_PLAYING',
                      'new MediaObservation(false, -1L, false)'):
            self.assertIn(token, adapter)
        self.assertNotIn('MediaCalendarScheduler', adapter)
        for path in BASE['startingBlobs']:
            if path in m6_phase_c_authorized_changes():
                continue
            if path.startswith('app/src/main/') and path.endswith('.java'):
                raw = phase_d_retained_bytes(ROOT / path).decode('utf-8')
                source = re.sub(r'/\*.*?\*/|//[^\n]*', '', raw, flags=re.S)
                self.assertNotIn('MediaCalendarScheduler', source, path)
                self.assertNotIn('VideoMediaObservationAdapter', source, path)

    def test_recorders_and_sensitivity_do_not_contain_a_scheduling_port(self):
        """Production is executed directly; the candidate copy is mutated solely in temporary negative tests."""
        harness = code(TEST + 'M5CDifferentialHarness.java')
        for token in ('new MediaCalendarScheduler(', 'new MediaSyncedTrackScheduler(', 'M5TemporalJournal.exactlyEqual', 'checkToken(token,binding[0])'):
            self.assertIn(token, harness)
        for token in ('skipTooOld', 'skipThrough', 'rearmFrom', 'renderDue', 'expireElapsed', '.sort(', 'Math.abs'):
            self.assertNotIn(token, harness)
        negative = code(TEST + 'M5NegativeSensitivityTest.java')
        for token in ('jdk.compiler/com.sun.tools.javac.Main', 'Files.createTempDirectory', 'deltaMs >= FORWARD_SEEK_THRESHOLD_MS', 'assertFalse(', 'M5FrozenLegacyOracle.execute'):
            self.assertIn(token, negative)
        failures = code(TEST + 'M5SinkFailureDifferentialTest.java')
        for boundary in ('ELIGIBLE', 'PLAYBACK', 'DUE', 'EXPIRE'):
            self.assertIn('"' + boundary + '"', failures)
        self.assertIn('assertEquals(run(false),run(true))', failures)

    def test_exact_accounting_and_native_api_inventory(self):
        """Count actual suites independently of annotations and keep M4 native evidence separate."""
        self.assertEqual({'M5CandidateDifferentialTest': 82, 'M5SchedulerDirectTest': 11,
                          'M5VideoObservationAdapterTest': 4, 'M5SinkFailureDifferentialTest': 4,
                          'M5NegativeSensitivityTest': 2}, BASE['m5PhaseCSuites'])
        summary = (ROOT / '.github/scripts/m4-phase-a-test-summary.py').read_text()
        self.assertIn("**m5_phase_c['m5PhaseCSuites']", summary)
        self.assertIn("trace['oracle'] != trace['candidate']", summary)
        workflow = (ROOT / '.github/workflows/android-media-differential.yml').read_text()
        self.assertIn('api: [31, 35]', workflow)
        self.assertIn('m5-media-differential.sh', workflow)
        init = (ROOT / '.github/scripts/m5-media-differential.init.gradle').read_text()
        for name in ('M5TemporalJournal', 'M5VideoTestProjection', 'M5CDifferentialHarness'):
            self.assertIn(name + '.java', init)
        self.assertNotIn('src/main/java', init)

    def test_new_python_documents_its_scope_and_every_function(self):
        """Keep teaching banners and docstrings rather than making provenance opaque."""
        for path in ('tests/m5_phase_c_provenance.py', 'tests/test_m5_phase_c_boundary.py', '.github/scripts/m5-media-differential-summary.py'):
            tree = ast.parse((ROOT / path).read_text())
            last_import = max(i for i, node in enumerate(tree.body) if isinstance(node, (ast.Import, ast.ImportFrom)))
            self.assertTrue(any(isinstance(n, ast.Expr) and isinstance(n.value, ast.Constant) and isinstance(n.value.value, str) for n in tree.body[last_import + 1:]))
            for node in ast.walk(tree):
                if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                    self.assertTrue(ast.get_docstring(node), path + ':' + node.name)


if __name__ == '__main__':
    unittest.main()
