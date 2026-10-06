from m5_phase_d_provenance import phase_d_added_paths, phase_d_retained_bytes
import ast
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
from m5_phase_b_provenance import blob_hash, calendar_model_files, phase_b_retained_bytes, qualification_retained_bytes
from m5_phase_c_provenance import phase_c_production_files, phase_c_added_paths

"""Qualify the additive temporal values and the byte-exact test-only legacy oracle.

A real empty-classpath JDK compilation protects the core, independently of Gradle.
Every retained source/test/config/document remains pinned to Phase B's starting
tree. Only nine finite provenance/accounting files reverse through reviewed edits;
the original M4 assertions and whole-blob pins still execute in their own suites.
"""

ROOT = Path(__file__).resolve().parents[1]
BASELINE = json.loads((ROOT / '.github/scripts/m5-phase-b-baseline.json').read_text(encoding='utf-8'))
CORE = 'app/src/main/java/com/scenevibe/tvcompanionpoc/calendar/'
TEST = 'app/src/test/java/com/scenevibe/tvcompanionpoc/'
ORACLE_BLOBS = {
    'ScheduledTrack': '38219f1130a0243272d3b1df5ad2e63461d7cc1e',
    'MediaSyncedTrackScheduler': '441d983409d94ae6d5e31198ec739dec86166754',
    'MediaIdentityMatcher': 'f022a48c78151a11508d7feb12ba91239191a43a',
    'MediaSessionProbe': '9c205b09ff5ffd85977250175eaab5019984b1b1',
    'NotificationAccess': 'b79a16d128c533169305e8a814b56488c3846559',
    'MediaSessionAccessService': 'c8728643be07f405a47b62a84f525302dbb73cab',
}
PROVENANCE_FILES = {
    'tests/test_m4_phase_b_boundary.py', 'tests/test_m4_phase_c_boundary.py',
    'tests/test_m4_phase_d_boundary.py', 'tests/test_m4_phase_e_boundary.py',
    'tests/test_m4_phase_f_boundary.py', 'tests/test_m4_phase_g_boundary.py',
    'tests/test_m4_sony_corrective_boundary.py', 'tests/sony_corrective_provenance.py',
    '.github/scripts/m4-phase-a-test-summary.py',
}


def code(path):
    """Inspect executable source while leaving educational documentation unchanged."""
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', (ROOT / path).read_text(encoding='utf-8'), flags=re.S)


class M5PhaseBBoundaryTest(unittest.TestCase):
    """Protect exact scope, pure values, frozen references and actual executed accounting."""

    def test_authoritative_bases_and_finite_scope(self):
        """Bind the additive cycle to the accepted Phase A HEAD and closed M4 base."""
        self.assertEqual('67b81045258b1692073c6927b956db4899c6ad1a', BASELINE['baseTvM5'])
        self.assertEqual('5011c91aac61a0cc6dcc74c256a15b7dee03d785', BASELINE['baseCloudM5'])
        self.assertEqual('689eb3a0243522eea6690ea2af61913bdb64322e', BASELINE['referenceHead'])
        self.assertEqual(calendar_model_files(), set(BASELINE['productionAdded']))
        self.assertEqual(PROVENANCE_FILES, set(BASELINE['authorizedChanges']))
        self.assertEqual(PROVENANCE_FILES, set(BASELINE['provenancePatches']))
        self.assertEqual(['docs/m5-phase-b-models-oracle-report.md'], BASELINE['documents'])
        self.assertEqual(['M6 WALL/Banner/anchors', 'M7 remote assets/cache', 'M8+ partner/SDK/UX'],
                         BASELINE['excludedScope'])
        for path in BASELINE['additiveFiles'] + BASELINE['documents']:
            self.assertNotIn('*', path)
            self.assertTrue((ROOT / path).is_file(), path)

    def test_all_retained_bytes_and_reversible_provenance_are_exact(self):
        """Pin runtime, Cloud/store/ACK/identity, old tests, docstrings and signing/config bytes."""
        for path, digest in BASELINE['startingBlobs'].items():
            content = phase_b_retained_bytes(ROOT / path)
            self.assertEqual(digest, blob_hash(content), path)
        self.assertNotIn('.github/scripts/m4-phase-g-sony-corrective-baseline.json', PROVENANCE_FILES)

    def test_pre_phase_c_qualification_inverse_and_acceptance_are_exact(self):
        """Pin the finite test-only corrective separately from B and reject wider exception acceptance."""
        corrective = BASELINE['qualificationCorrective']
        owner = TEST + 'M4PhaseFOwnerGateTest.java'
        exact = {owner, 'tests/m5_phase_b_provenance.py',
                 'tests/test_m4_sony_corrective_boundary.py', 'tests/test_m5_phase_b_boundary.py'}
        self.assertEqual('ce724e9058dbf56eee2b235174e18556807bcf1d', corrective['referenceHead'])
        self.assertEqual(exact, set(corrective['patches']))
        self.assertEqual(exact, set(corrective['startingBlobs']))
        self.assertEqual(exact | {'.github/scripts/m5-phase-b-baseline.json',
                                 'docs/m5-phase-b-models-oracle-report.md'}, set(corrective['authorizedChanges']))
        self.assertEqual('68a94c47dc7f9ef6d75e858221acbec618202a0a', corrective['startingBlobs'][owner])
        for path in exact:
            self.assertEqual(corrective['startingBlobs'][path], blob_hash(qualification_retained_bytes(ROOT / path)), path)
        source = code(owner)
        method = source.split('public void interruptedWaitCancelsLateOwnerInstallation()', 1)[1].split(
            '@Test public void replacementAfterArmPreventsAck()', 1)[0]
        self.assertEqual(1, method.count('catch(InterruptedException expected)'))
        self.assertEqual(1, method.count('catch(Exception expected)'))
        self.assertIn('assertEquals("com.scenevibe.tvcompanionpoc.CloudControlClient$CloudException",', method)
        self.assertIn('expected.getClass().getName());', method)
        self.assertIn('assertEquals("Local installation refused",expected.getMessage());', method)
        self.assertIn('fail("stopped owner wait must fail closed");', method)
        self.assertIn('h.onOwner(()->null);', method)
        for invariant in ('h.installCalls', 'h.acks', 'h.backend.candidateWrites',
                          'h.backend.ackWrites', 'h.store.read().acknowledgedRevision()'):
            self.assertIn('assertEquals(0,' + invariant + ');', method)
        self.assertNotIn('Thread.sleep', method)
        self.assertNotIn('@Ignore', method)

    def test_no_unlisted_production_test_config_or_document_file(self):
        """A finite path inventory rejects another engine, feature, gate exception or unreviewed fixture."""
        expected = set(BASELINE['startingBlobs']) | set(BASELINE['additiveFiles']) | set(BASELINE['documents']) | phase_c_added_paths() | phase_d_added_paths()
        actual = set()
        for directory in ('app/src', 'tests', '.github', 'docs'):
            for path in (ROOT / directory).rglob('*'):
                if path.is_file() and '__pycache__' not in path.parts:
                    actual.add(str(path.relative_to(ROOT)))
        self.assertEqual({path for path in expected if path.startswith(('app/src/', 'tests/', '.github/', 'docs/'))}, actual)
        old_java = {path for path in BASELINE['startingBlobs'] if path.startswith('app/src/main/') and path.endswith('.java')}
        self.assertEqual(old_java | calendar_model_files() | phase_c_production_files(), {p for p in actual if p.startswith('app/src/main/') and p.endswith('.java')})

    def test_core_compiles_with_jdk_only_empty_classpath_and_sourcepath(self):
        """Execute the JDK compiler against only the three values and verify exactly their three classfiles."""
        javac = shutil.which('javac')
        java = shutil.which('java')
        self.assertTrue(javac or java, 'A JDK compiler is mandatory')
        compiler = [javac] if javac else [java, '-m', 'jdk.compiler/com.sun.tools.javac.Main']
        with tempfile.TemporaryDirectory(prefix='scenevibe-m5b-core-') as temporary:
            empty = Path(temporary) / 'empty'
            output = Path(temporary) / 'classes'
            empty.mkdir()
            output.mkdir()
            result = subprocess.run(compiler + ['-encoding', 'UTF-8', '-classpath', str(empty),
                                    '-sourcepath', str(empty), '-d', str(output)]
                                    + [str(ROOT / path) for path in sorted(calendar_model_files())]
                                    + [str(ROOT / (CORE + "MediaCalendarScheduler.java"))],
                                    capture_output=True, timeout=30, check=False)
            self.assertEqual(0, result.returncode, 'JDK-only temporal-value compilation failed')
            self.assertEqual({'SceneEvent.class', 'MediaCalendar.class', 'MediaObservation.class'}
                             | {'MediaCalendarScheduler.class', 'MediaCalendarScheduler$Sink.class'},
                             {path.name for path in output.rglob('*.class')})

    def test_core_has_only_exact_immutable_value_fields(self):
        """Reject payload bags, clock/state authority or any extra value field independently of model assertions."""
        expected = {
            'SceneEvent': [('String', 'eventId'), ('long', 'startMs'), ('long', 'durationMs')],
            'MediaCalendar': [('List<SceneEvent>', 'events'), ('boolean', 'freezeOnPause')],
            'MediaObservation': [('boolean', 'eligible'), ('long', 'positionMs'), ('boolean', 'playing')],
        }
        for name, fields in expected.items():
            source = code(CORE + name + '.java')
            self.assertIn('public final class ' + name, source)
            self.assertEqual(fields, re.findall(r'private final ([\w<>]+) (\w+);', source))
            self.assertEqual(len(fields), len(re.findall(r'\b(?:private|public|protected)\s+[^;{}()]+;', source)))

    def test_core_excludes_platform_product_io_network_clock_and_dynamic_apis(self):
        """Inspect imports and qualified references, not merely successful Android compilation."""
        forbidden = (r'android(?:x)?\.', r'org\.json', r'java\.(?:io|net|nio|time)\.',
                     r'java\.lang\.reflect', r'java\.util\.concurrent',
                     r'\b(?:Bitmap|MediaSession|PlaybackState|View|WindowManager|OverlayManifest|SceneRenderer|'
                     r'ScheduledTrack|FinalTrack|VideoPreparedState|CloudControlClient|CloudProtocol|PackageInstaller|'
                     r'InstallationStore|SharedPreferences|Thread|Executor|Future|Timer|Handler|Looper|'
                     r'Object|Map|Bundle|Serializable|Clock|Date|Calendar|ClassLoader|ServiceLoader|InputStream|OutputStream)\b',
                     r'\bSystem\.', r'Class\.forName', r'Runtime\.getRuntime')
        for path in calendar_model_files():
            source = code(path)
            for token in forbidden:
                self.assertIsNone(re.search(token, source), path)
            for imported in re.findall(r'\bimport\s+([^;]+);', source):
                self.assertTrue(imported.startswith('java.util.'), path)

    def test_existing_production_has_no_calendar_consumer(self):
        """Reconstructed accepted B/C callers remain non-live; D checks actual temporal ownership."""
        for path in BASELINE['startingBlobs']:
            if path.startswith('app/src/main/java/') and path.endswith('.java'):
                source = re.sub(r'/\*.*?\*/|//[^\n]*', '', phase_d_retained_bytes(ROOT / path).decode('utf-8'), flags=re.S)
                self.assertNotIn('com.scenevibe.tvcompanionpoc.calendar', source, path)
                self.assertIsNone(re.search(r'\b(?:SceneEvent|MediaCalendar|MediaObservation)\b', source), path)

    def test_frozen_oracle_sources_match_independent_hard_pins(self):
        """Changing a baseline reference alone cannot bless different historical algorithm bytes."""
        self.assertEqual(ORACLE_BLOBS, {item['class']: item['blob'] for item in BASELINE['oracleSources']})
        harness = code(TEST + 'M5FrozenLegacyOracle.java')
        for item in BASELINE['oracleSources']:
            self.assertTrue(item['resource'].endswith('.java.txt'))
            self.assertEqual(item['blob'], blob_hash((ROOT / item['resource']).read_bytes()), item['resource'])
            self.assertEqual(item['blob'], BASELINE['startingBlobs'][item['production']])
            self.assertIn(item['blob'], harness)
        self.assertEqual([], BASELINE['oracleAdaptations'])
        for token in ('jdk.compiler/com.sun.tools.javac.Main', 'new ProcessBuilder(command)', 'new FrozenLoader', 'findClass(name)',
                      'getMethod("onPlaybackSnapshot"', 'getMethod("onPlaybackUnavailable"'):
            self.assertIn(token, harness)
        for token in ('MAX_LATE_MS', 'FORWARD_SEEK_THRESHOLD_MS', 'BACKWARD_SEEK_THRESHOLD_MS',
                      'skipTooOld', 'skipThrough', 'rearmFrom', 'renderDue', 'expireElapsed'):
            self.assertNotIn(token, harness)

    def test_fixture_inventory_covers_all_48_families_and_preserves_raw_hashmap_cases(self):
        """Invalid-value fixtures are distinct; raw map-order evidence is never a portable expected order."""
        corpus = json.loads((ROOT / 'app/src/test/resources/m5-phase-b/corpus.json').read_text(encoding='utf-8'))
        cases = corpus['cases']
        self.assertEqual(BASELINE['corpusCases'], [case['id'] for case in cases])
        self.assertEqual(92, len(cases))
        self.assertEqual(set(range(1, 49)), {family for case in cases for family in case['families']})
        self.assertEqual(10, sum(case['kind'] == 'model-rejection' for case in cases))
        self.assertEqual(4, sum(case.get('environmentDependent', False) for case in cases))
        for case in cases:
            if case['kind'] == 'model-rejection':
                self.assertNotIn('expected', case)
                continue
            frames = case['referenceTrace'] if case.get('environmentDependent') else case['expected']
            self.assertEqual(len(case['actions']), len(frames), case['id'])
            self.assertEqual(list(range(len(frames))), [frame['input'] for frame in frames], case['id'])
            if case.get('environmentDependent'):
                self.assertNotIn('expected', case)
        for path, digest in BASELINE['fixtureSha256'].items():
            self.assertEqual(digest, hashlib.sha256((ROOT / path).read_bytes()).hexdigest(), path)

    def test_journal_and_projection_cannot_replace_a_scheduler(self):
        """No sorting/dedupe/tolerance or legacy scheduling conditions hide a temporal divergence."""
        journal = code(TEST + 'M5TemporalJournal.java')
        for token in ('.sort(', 'Set<', 'HashSet', 'TreeSet', 'distinct(', 'debounce', 'Math.abs', 'nanoTime', 'currentTimeMillis'):
            self.assertNotIn(token, journal)
        for token in ('a.input != b.input', '!Objects.equals(a.token, b.token)', 'a.effects.size() != b.effects.size()',
                      '!a.effects.get(j).same(b.effects.get(j))'):
            self.assertIn(token, journal)
        projection = code(TEST + 'M5VideoTestProjection.java')
        for token in ('event.id, event.startMs, event.durationMs', 'track.pauseFreezesDisplay'):
            self.assertIn(token, projection)
        for token in ('TrackParser', 'OverlayManifest', 'Cloud', 'Store', 'MediaIdentityMatcher', 'org.json', 'Bitmap'):
            self.assertNotIn(token, projection)

    def test_executed_accounting_keeps_all_retained_suites_and_adds_only_b(self):
        """A changed case count, removed retained suite or new skip cannot pass actual XML accounting."""
        self.assertEqual({'M5SceneEventTest': 7, 'M5MediaCalendarTest': 9, 'M5MediaObservationTest': 4,
                          'M5JournalComparatorTest': 11, 'M5VideoProjectionTest': 4,
                          'M5LegacyOracleCorpusTest': 92}, BASELINE['m5PhaseBSuites'])
        summary = (ROOT / '.github/scripts/m4-phase-a-test-summary.py').read_text(encoding='utf-8')
        for token in ("**m5_phase_b['m5PhaseBSuites']", "**sony['phaseGCorrectiveSuites']",
                      'if suites != expected:', "baseline['allowedOptInSkips']", "baseline['fixtureSha256']",
                      "m5_phase_b['fixtureSha256']"):
            self.assertIn(token, summary)

    def test_new_python_keeps_teaching_banners_and_function_docstrings(self):
        """Every new Python helper explains its scope after imports and documents each function."""
        for path in ('tests/m5_phase_b_provenance.py', 'tests/test_m5_phase_b_boundary.py'):
            tree = ast.parse((ROOT / path).read_text(encoding='utf-8'))
            last_import = max(i for i, node in enumerate(tree.body) if isinstance(node, (ast.Import, ast.ImportFrom)))
            self.assertTrue(any(isinstance(node, ast.Expr) and isinstance(node.value, ast.Constant)
                                and isinstance(node.value.value, str) for node in tree.body[last_import + 1:]), path)
            for node in ast.walk(tree):
                if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                    self.assertTrue(ast.get_docstring(node), path + ':' + node.name)


if __name__ == '__main__':
    unittest.main()
