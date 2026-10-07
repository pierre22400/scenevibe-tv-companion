import ast
import json
from pathlib import Path
import re
import unittest
from m5_phase_d_provenance import blob_hash, phase_d_retained_bytes
from m6_phase_b_provenance import m6_phase_b_added_paths, m6_phase_b_retained_bytes

"""Qualify the unique actual Video temporal owner and preserve exact C→B→M4 provenance.

Historical inverses cannot authorize executable drift in the qualified temporal core,
Cloud, persistence or fixtures. Actual source checks require fixed tokens, captured ARM
generation, pending/active promotion, owner-only cleanup and passive adapter projection.
The JVM bucket executes both Video shapes against the real service router and core.
"""

ROOT = Path(__file__).resolve().parents[1]
BASE = json.loads((ROOT / '.github/scripts/m5-phase-d-baseline.json').read_text())
JAVA = 'app/src/main/java/com/scenevibe/tvcompanionpoc/'
TEST = 'app/src/test/java/com/scenevibe/tvcompanionpoc/'
PRODUCTION = {JAVA + n + '.java' for n in ('OverlayService', 'SceneRuntimeController', 'VideoPreparedState', 'VideoInstallationRuntimePorts', 'DiagnosticsActivity')}
ADDED = {'.github/scripts/m5-phase-d-baseline.json', 'tests/m5_phase_d_provenance.py',
         'tests/test_m5_phase_d_boundary.py', TEST + 'M5VideoCutoverTest.java',
         'docs/m5-phase-d-video-cutover-report.md', 'docs/m5-sony-targeted-qualification-protocol.md'}
CHANGED = PRODUCTION | {TEST + n + '.java' for n in ('AssignmentMutationGateTest', 'M4PhaseAFixtures',
    'M4PhaseAInstallationTest', 'M4PhaseDArmTest', 'M4PhaseEVideoFixtures', 'M4PhaseEVideoInstallerTest',
    'M4PhaseFFixtures', 'M4PhaseFRuntimeTest', 'M4PhaseGCloudAfterRestoreTest', 'M4PhaseGStartupTest',
    'ManifestInstallAckDecisionTest', 'SceneRuntimeControllerTest')} | {
    '.github/scripts/m4-phase-a-test-summary.py',
    'app/src/androidTest/java/com/scenevibe/tvcompanionpoc/M4SonyDurabilityInstrumentation.java',
    'tests/m5_phase_c_provenance.py', 'tests/test_m4_phase_f_boundary.py', 'tests/test_m4_phase_g_boundary.py',
    'tests/test_m4_sony_corrective_boundary.py', 'tests/test_m5_phase_b_boundary.py', 'tests/test_m5_phase_c_boundary.py'}


def code(path):
    """Inspect actual executable Java while preserving all teaching comments in provenance."""
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', (ROOT / path).read_text(), flags=re.S)


def block(source, signature):
    """Extract one exact balanced Java body for ordering and method-specific authority checks."""
    start = source.index(signature)
    opening = source.index('{', start)
    depth = 1
    for end in range(opening + 1, len(source)):
        depth += (source[end] == '{') - (source[end] == '}')
        if depth == 0:
            return source[start:end + 1]
    raise ValueError('Unbalanced actual Java body')


class M5PhaseDBoundaryTest(unittest.TestCase):
    """Require exact scope and actual cutover behavior independently of historical provenance."""

    def test_exact_start_scope_and_baselines(self):
        """Only five production paths and six additions belong to this authorized phase."""
        self.assertEqual('f99f662b4dd1c2e2285a1272b6abced9e89862b0', BASE['referenceHead'])
        self.assertEqual('67b81045258b1692073c6927b956db4899c6ad1a', BASE['baseTvM5'])
        self.assertEqual('5011c91aac61a0cc6dcc74c256a15b7dee03d785', BASE['baseCloudM5'])
        self.assertEqual(PRODUCTION, set(BASE['authorizedProductionChanges']))
        self.assertEqual(ADDED, set(BASE['additiveFiles']))
        self.assertEqual(CHANGED, set(BASE['authorizedChanges']))
        self.assertEqual(CHANGED, set(BASE['provenancePatches']))
        for phase in ('B', 'C'):
            path = '.github/scripts/m5-phase-' + phase.lower() + '-baseline.json'
            self.assertEqual(BASE['phase' + phase + 'BaselineBlob'], blob_hash((ROOT / path).read_bytes()))
        self.assertEqual(set(BASE['startingBlobs']) - CHANGED, set(BASE['forbiddenChanges']))

    def test_every_retained_blob_reconstructs_exact_c(self):
        """Unknown or ambiguous hunks cannot pass even if an older inverse would ignore them."""
        self.assertEqual(269, len(BASE['startingBlobs']))
        for path, digest in BASE['startingBlobs'].items():
            self.assertEqual(digest, blob_hash(phase_d_retained_bytes(ROOT / path)), path)
            if path not in CHANGED:
                self.assertEqual(digest, blob_hash(m6_phase_b_retained_bytes(ROOT / path)), path)
        for path, digest in {**BASE['qualifiedCoreBlobs'], **BASE['oracleAndFixtureBlobs']}.items():
            self.assertNotIn(path, CHANGED)
            self.assertEqual(digest, blob_hash((ROOT / path).read_bytes()), path)

    def test_exact_file_inventory(self):
        """No unlisted test, hidden engine, wildcard or alternate activation path can enter scope."""
        actual = set()
        for directory in ('app/src', 'tests', '.github', 'docs'):
            actual.update(str(p.relative_to(ROOT)) for p in (ROOT / directory).rglob('*')
                          if p.is_file() and '__pycache__' not in p.parts)
        expected = {p for p in set(BASE['startingBlobs']) | ADDED | {'docs/m5-final-sony-physical-closure.md'} | {'docs/scenevibe-os-m6-banner-wall-clock-architecture.md', 'docs/m6-phase-a-architecture-report.md'} | m6_phase_b_added_paths() if p.startswith(('app/src/', 'tests/', '.github/', 'docs/'))}
        self.assertEqual(expected, actual)
        self.assertFalse(any('*' in p for p in ADDED | CHANGED))

    def test_unique_live_engine_and_passive_probe_projection(self):
        """Exactly one candidate is service-owned; no production path constructs the historical engine."""
        sources = {str(p.relative_to(ROOT)): code(str(p.relative_to(ROOT))) for p in (ROOT / 'app/src/main/java').rglob('*.java')}
        self.assertEqual([JAVA + 'OverlayService.java'], [p for p, s in sources.items() if 'new MediaCalendarScheduler(' in s])
        self.assertEqual(1, sources[JAVA + 'OverlayService.java'].count('new MediaCalendarScheduler('))
        self.assertFalse(any(re.search(r'new\s+MediaSyncedTrackScheduler\s*\(', s) for s in sources.values()))
        service = sources[JAVA + 'OverlayService.java']
        self.assertNotIn('MediaSyncedTrackScheduler', service)
        self.assertNotIn('onPlaybackSnapshot', service)
        self.assertIn('VideoMediaObservationAdapter.observe(active.state.track,snapshot)', service)
        self.assertIn('trackScheduler.onUnavailable()', service)
        probe = block(service, 'mediaSessionProbe = new MediaSessionProbe')
        self.assertLess(probe.index('setLastObservedMediaApp'), probe.index('videoRuntimePorts.onSnapshot(snapshot)'))
        self.assertLess(probe.index('BLOCK_CODE_SESSION_UNAVAILABLE'), probe.index('trackScheduler.onUnavailable()'))
        for forbidden in ('FeatureFlag', 'shadowScheduler', 'parallelScheduler', 'System.currentTimeMillis', 'getTransportControls'):
            self.assertNotIn(forbidden, service)

    def test_preparation_projects_once_outside_core(self):
        """Temporal values come only from parsed events and bind reuses calendar/index identities."""
        state = code(JAVA + 'VideoPreparedState.java')
        self.assertEqual(1, state.count('new MediaCalendar('))
        self.assertIn('new SceneEvent(event.id,event.startMs,event.durationMs)', state)
        self.assertIn('Collections.unmodifiableMap(index)', state)
        self.assertIn('this.calendar=state.calendar', state)
        self.assertIn('this.eventsById=state.eventsById', state)
        self.assertIn('new MediaCalendar(events,track.pauseFreezesDisplay)', state)
        for forbidden in ('JSONObject', 'TrackParser', 'InstallationStore', 'CloudControlClient', 'encodeForCache', '.commit('):
            self.assertNotIn(forbidden, state)
        ports = code(JAVA + 'VideoInstallationRuntimePorts.java')
        self.assertIn('loadPreparedVideo(VideoPreparedState state)', ports)
        self.assertNotIn('loadPreparedTrack', ports)
        arm = block(state, 'static InstallationStatus arm(')
        ordered = ['boolean retired=', 'runtime.loadPreparedVideo(state)', 'runtime.armPreparedManifest(', 'runtime.selectActiveRevision(']
        self.assertEqual(sorted(arm.index(t) for t in ordered), [arm.index(t) for t in ordered])

    def test_pending_activation_captures_fixed_local_identity(self):
        """Load emits a fresh local token; manifest ARM captures generation; selection alone promotes."""
        ports = block(code(JAVA + 'OverlayService.java'), 'static final class LiveVideoRuntimePorts')
        load = block(ports, 'boolean loadPreparedVideo(')
        self.assertIn('"video-activation-"+(++nextActivation)', load)
        self.assertLess(load.index('pending=new Activation'), load.index('runtime.load(state.calendar,pending.token)'))
        for forbidden in ('revision', 'ackRevision', 'installationId', 'currentGeneration', 'cloudDeviceId'):
            self.assertNotIn(forbidden, load)
        manifest = block(ports, 'boolean armPreparedManifest(')
        self.assertLess(manifest.index('runtime.replaceRevision('), manifest.index('runtime.currentGeneration()'))
        self.assertEqual(1, ports.count('currentGeneration()'))
        selection = block(ports, 'boolean selectActiveRevision(')
        self.assertIn('active=pending;pending=null', selection)
        self.assertLess(selection.index('selection.accept(revision)'), selection.index('active=pending'))
        matching = block(ports, 'Activation matching(')
        self.assertIn('active.token.equals(token)', matching)
        self.assertNotIn('pending', matching)
        for name in ('onDue(', 'onExpire(', 'onPlayback('):
            callback = block(ports, 'void ' + name)
            self.assertIn('matching(token)', callback)
            self.assertNotIn('currentGeneration', callback)
        self.assertIn('runtime.onEventDue(eventId,binding.generation)', ports)
        self.assertIn('runtime.onEventExpired(eventId,binding.generation)', ports)
        self.assertIn('binding.state.eventsById.get(eventId)', ports)

    def test_eligibility_and_abort_preserve_native_order(self):
        """Only synchronous old false invalidation bypasses matching; all cleanup owners are attempted."""
        ports = block(code(JAVA + 'OverlayService.java'), 'static final class LiveVideoRuntimePorts')
        eligibility = block(ports, 'void onEligibility(')
        self.assertIn('!invalidating||eligible', eligibility)
        self.assertIn('!retiring.token.equals(token)', eligibility)
        ordered = ['diagnostics.accept(eligible)', 'runtime.onEligibility(eligible)', 'if(!eligible)']
        self.assertEqual(sorted(eligibility.index(t) for t in ordered), [eligibility.index(t) for t in ordered])
        abort = block(ports, 'void abortActivation(')
        for token in ('invalidate()', 'runtime.unload()', 'cleanup(retireScenes)', 'cleanup(retireLegacy)',
                      'runtime.clear()', 'pending=null;active=null;retiring=null', 'selection.accept(0)'):
            self.assertIn(token, abort)
        self.assertIn('finally', abort)
        self.assertIn('if(!isOwnerThread())return', abort)
        self.assertIn('catch(RuntimeException refused)', ports)

    def test_controller_id_generation_api_and_observational_label(self):
        """The regie remains clock-free and each ID entry retains every existing visual predicate."""
        controller = code(JAVA + 'SceneRuntimeController.java')
        self.assertNotIn('ScheduledTrack', controller)
        for token in ('void onEventDue(String eventId, long callbackGeneration)',
                      'void onEventExpired(String eventId, long callbackGeneration)',
                      'if (eventId == null) return', 'if (callbackGeneration != generation) return',
                      'if (!eligible) return', 'scenesById.get(eventId)', 'if (!sink.preflight(scene)) return',
                      'sink.show(scene)', '!visibleScene.id.equals(eventId)'):
            self.assertIn(token, controller)
        for forbidden in ('Handler', 'postDelayed', 'currentTimeMillis', 'elapsedRealtime', 'PlaybackState'):
            self.assertNotIn(forbidden, controller)
        diagnostics = code(JAVA + 'DiagnosticsActivity.java')
        self.assertEqual(1, diagnostics.count('line(sb, "Temporal engine", "scenevibe.media-calendar.v1")'))
        self.assertNotIn('scenevibe.media-calendar.v1', code(JAVA + 'OverlayService.java'))

    def test_exact_executed_bucket_and_preserved_native_fixture(self):
        """Count actual JUnit XML, keep both native APIs and retain all old storage assertions."""
        self.assertEqual({'M5VideoCutoverTest': 44}, BASE['m5PhaseDSuites'])
        summary = (ROOT / '.github/scripts/m4-phase-a-test-summary.py').read_text()
        for token in ("**m5_phase_b['m5PhaseBSuites']", "**m5_phase_c['m5PhaseCSuites']", "**m5_phase_d['m5PhaseDSuites']", 'if suites != expected:'):
            self.assertIn(token, summary)
        native = code('app/src/androidTest/java/com/scenevibe/tvcompanionpoc/M4SonyDurabilityInstrumentation.java')
        for token in ('getParameterTypes().length==6', 'getParameterTypes().length==8', 'constructor.newInstance(owner',
                      'SAME_REVISION_STARTUP_ARMED', 'backend.commits==0&&backend.acks==0&&backend.clears==0'):
            self.assertIn(token, native)
        integration = code(TEST + 'M5VideoCutoverTest.java')
        for token in ('new MediaCalendarScheduler(', 'new OverlayService.LiveVideoRuntimePorts(',
                      'installer.install(', 'OverlayService.restoreInstalledPackage(', 'r.token("pending")',
                      'r.controller.replaceRevision(13,r.prepared.manifest)', 'assertEquals(before,r.visual)'):
            self.assertIn(token, integration)

    def test_new_python_has_scope_banner_and_all_docstrings(self):
        """Finite provenance is readable and every new Python function documents its contract."""
        for path in ('tests/m5_phase_d_provenance.py', 'tests/test_m5_phase_d_boundary.py'):
            tree = ast.parse((ROOT / path).read_text())
            last_import = max(i for i, n in enumerate(tree.body) if isinstance(n, (ast.Import, ast.ImportFrom)))
            self.assertTrue(any(isinstance(n, ast.Expr) and isinstance(n.value, ast.Constant)
                                and isinstance(n.value.value, str) for n in tree.body[last_import + 1:]))
            for node in ast.walk(tree):
                if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                    self.assertTrue(ast.get_docstring(node), path + ':' + node.name)


if __name__ == '__main__':
    unittest.main()
