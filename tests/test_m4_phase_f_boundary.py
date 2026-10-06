import ast
import hashlib
import json
from pathlib import Path
import re
import unittest
from sony_corrective_provenance import retained_bytes, retained_text
from m5_phase_b_provenance import calendar_model_files

"""Qualify the live Cloud cutover without weakening the retained M4 evidence.

Only the Cloud client, service composition and two exact reset/constructor micro-edits
may change old production code. The historical HTTP oracle and modified old tests are
reversed to their exact starting blobs; old predicates/accounting are reversed likewise.
The executed JVM suites exercise the actual client, installer and service runtime ports.
Boot/diagnostics and Cloud/Video semantics remain byte-pinned for the later Phase G.
Phase G admits only its exact startup/metadata/reset exceptions; the G gate reverses and pins them.
Sony corrective provenance reverses only pinned edits; semantic assertions inspect actual production.

"""

ROOT = Path(__file__).resolve().parents[1]
PHASE_G_PATH = ROOT / '.github/scripts/m4-phase-g-baseline.json'
PHASE_G = json.loads(PHASE_G_PATH.read_text(encoding='utf-8')) if PHASE_G_PATH.exists() else {}
BASELINE = json.loads((ROOT / '.github/scripts/m4-phase-f-baseline.json').read_text(encoding='utf-8'))
JAVA = 'app/src/main/java/com/scenevibe/tvcompanionpoc/'
TEST = 'app/src/test/java/com/scenevibe/tvcompanionpoc/'


def code_only(source):
    """Inspect executable dependencies without treating explanatory Javadocs as calls."""
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.S)


def blob_hash(content):
    """Reproduce immutable Git blob identity independently of checkout/index metadata."""
    return hashlib.sha1(b'blob ' + str(len(content)).encode('ascii') + b'\0' + content).hexdigest()


def inverse(source, patches):
    """Undo only inventoried exact edits; a missing snippet cannot silently pass provenance."""
    for patch in reversed(patches):
        if patch['after'] not in source:
            raise ValueError('Missing inventoried Phase F edit')
        source = source.replace(patch['after'], patch['before'])
    return source


def java_block(source, signature):
    """Extract a known balanced method body for unchanged transport and deferred helper evidence."""
    start = source.index(signature)
    opening = source.index('{', start)
    depth = 1
    for end in range(opening + 1, len(source)):
        depth += (source[end] == '{') - (source[end] == '}')
        if depth == 0:
            return source[start:end + 1]
    raise ValueError('Unbalanced known Java method')


class M4PhaseFBoundaryTest(unittest.TestCase):
    """Pin the authorized live composition, preserved compatibility and exact reviewable scope."""

    def test_exact_start_bases_and_authorized_old_production_inventory(self):
        """The exception lists cannot expand into boot, diagnostics, Cloud server or runtime semantics."""
        self.assertEqual('eb38e34086f22f81e5cc9ef917197658ce63ecbd', BASELINE['referenceHead'])
        self.assertEqual('f4b2007245b7ecc39f8cabb1e8d36945d0650d4e', BASELINE['phaseEClosure'])
        self.assertEqual('ecdf77bec9f93babf15239a63bf7f702fd7ca293', BASELINE['baseTv'])
        self.assertEqual('5011c91aac61a0cc6dcc74c256a15b7dee03d785', BASELINE['baseCloud'])
        self.assertEqual({JAVA + name for name in ('CloudControlClient.java', 'OverlayService.java',
                                                  'CloudTrackRepository.java', 'installation/InstallationStore.java')},
                         set(BASELINE['authorizedProductionChanges']))
        self.assertEqual([JAVA + 'CloudControlClient.java', JAVA + 'OverlayService.java'],
                         BASELINE['authorizedLiveCallers'])
        self.assertEqual(JAVA + 'CloudV1InstallationAdapter.java', BASELINE['adapterFile'])

    def test_adapter_is_pure_outside_core_and_ack_binding_is_separate(self):
        """The immutable handoff contains only the inert request and historical finalTrackId, never credentials."""
        source = code_only((ROOT / BASELINE['adapterFile']).read_text())
        self.assertIn('package com.scenevibe.tvcompanionpoc;', source)
        self.assertNotRegex(source, r'\b(Context|Service|InstallationStore|PackageInstaller|CloudDeviceCredentials|'
                            r'MediaSyncedTrackScheduler|SceneRuntimeController|SceneRenderer|OverlayRenderer|'
                            r'VideoOverlayManifestBridge|TrackParser|OverlayManifestParser)\b')
        for token in ('android.', 'java.net.', 'java.io.', 'java.lang.reflect.', 'deviceToken',
                      'activationSecret', 'Authorization', 'getSharedPreferences', '.commit('):
            self.assertNotIn(token, source)
        assignment = java_block(source, 'static final class Assignment')
        self.assertEqual([('InstallRequest', 'request'), ('String', 'finalTrackId')],
                         re.findall(r'private final (\w+) (\w+);', assignment))
        self.assertNotIn('finalTrackId', code_only((ROOT / JAVA / 'installation/InstallRequest.java').read_text()))
        self.assertIn('CloudProtocol.validAssignment(envelope,cloudDeviceId,0)', source)
        self.assertIn('envelope.optString("finalTrackId","")', source)
        self.assertEqual(2, source.count('.toString().getBytes(StandardCharsets.UTF_8)'))

    def test_client_has_only_generic_install_dependencies_and_no_revision_branch(self):
        """Cloud transport cannot reacquire a product parser, old ManifestInstaller or independent revision authority."""
        source = code_only((ROOT / JAVA / 'CloudControlClient.java').read_text())
        self.assertNotRegex(source, r'\b(MediaSyncedTrackScheduler|CloudTrackRepository|ManifestInstaller|'
                            r'VideoOverlayManifestBridge|TrackParser|OverlayManifestParser|SceneRuntimeController|'
                            r'SceneRenderer|OverlayRenderer|VideoInstallationHandlers)\b')
        for token in ('runtimeTrack', 'overlayManifest', 'installManifested', 'activateLegacy',
                      'confirmArmed', 'revision <=', 'revision <', 'revision ==', 'revision >'):
            self.assertNotIn(token, source)
        self.assertEqual(1, source.count('installer::install'))
        self.assertEqual(1, source.count('installation.install(request,runtimePorts)'))
        self.assertIn('gate.call(()->currentClient.getAsBoolean()', source)

    def test_corruption_is_checked_before_fetch_and_only_exact_armed_precedes_ack(self):
        """The live ordering fails closed on corrupt durable state and persists ACK only after server confirmation."""
        source = code_only((ROOT / JAVA / 'CloudControlClient.java').read_text())
        fetch = java_block(source, 'private void fetchAssignment()')
        ordered = ['store.read()', 'ReadState.CORRUPT', 'durable.acknowledgedRevision()',
                   'CloudV1InstallationAdapter.adapt(', 'applyAssignment(',
                   'installed!=InstallationStatus.ARMED', 'assignment.finalTrackId()',
                   'CloudProtocol.validAck(', 'store.markAcknowledged(revision)',
                   'setLastSuccessfulAckRevision(revision)']
        offsets = [fetch.index(token) for token in ordered]
        self.assertEqual(sorted(offsets), offsets)
        self.assertEqual(1, fetch.count('http("POST"'))
        self.assertEqual(1, fetch.count('new JSONObject().put("revision",revision).put("finalTrackId",assignment.finalTrackId())'))
        self.assertNotIn('.put("trackId"', fetch)
        self.assertEqual(2, fetch.count('if(!running||!currentClient.getAsBoolean())return;'))

    def test_service_owns_one_generic_stack_and_real_main_thread_ports(self):
        """One service composition binds the existing core owners and proves the current Cloud client after dispatch."""
        source = code_only((ROOT / JAVA / 'OverlayService.java').read_text())
        for token in ('new AndroidInstallationBackend(', 'new InstallationStore(',
                      'new PackageInstaller(', 'new LiveVideoRuntimePorts('):
            self.assertEqual(1, source.count(token), token)
        self.assertIn('VideoInstallationHandlers.registry(), TvCapabilities.current()', source)
        self.assertIn('() -> Looper.myLooper() == Looper.getMainLooper()', source)
        historical = inverse((ROOT / JAVA / 'OverlayService.java').read_text(),
                             PHASE_G.get('productionPatches', {}).get(JAVA + 'OverlayService.java', []))
        self.assertIn('new CloudTrackRepository(installationStore)', code_only(historical))
        self.assertIn('videoRuntimePorts, videoRuntimePorts::abortActivation, () -> cloudClient', source)
        self.assertIn('!cloudResetPending && shouldReconstructCloudClient(', source)
        self.assertIn('private volatile CloudControlClient cloudClient;', source)
        self.assertNotIn('ManifestInstaller', source)

    def test_actual_runtime_ports_are_owner_guarded_and_runtime_only(self):
        """Retirement is synchronous, prepared values remain exact and abort cannot touch disk or ACK."""
        ports = code_only(java_block((ROOT / JAVA / 'OverlayService.java').read_text(),
                                     'static final class LiveVideoRuntimePorts'))
        self.assertNotRegex(ports, r'\b(InstallationStore|PackageInstaller|CloudControlClient|CloudTrackRepository|'
                            r'TrackParser|OverlayManifestParser|VideoOverlayManifestBridge)\b')
        for token in ('java.net.', 'markAcknowledged', '.commit(', 'new SceneRenderer', 'new OverlayRenderer'):
            self.assertNotIn(token, ports)
        self.assertEqual(6, ports.count('if(!isOwnerThread()'))
        for token in ('runtime.unload();retireScenes.run()', 'runtime.load(track)',
                      'runtime.replaceRevision(revision,manifest)', 'runtime.isSceneRendererActiveFor(revision)',
                      'selection.accept(revision)', 'runtime.clear()', 'selection.accept(0)'):
            self.assertIn(token, ports)
        self.assertIn('runtime.hasActiveManifest()', ports)
        fixture = (ROOT / TEST / 'M4PhaseFFixtures.java').read_text()
        self.assertIn('new OverlayService.LiveVideoRuntimePorts(', fixture)

    def test_reset_reuses_owner_gate_and_preserves_asynchronous_lifetime(self):
        """Reset makes the client spent before queuing identity/credential/durable/runtime clear on their owners."""
        source = code_only((ROOT / JAVA / 'CloudControlClient.java').read_text())
        reset = java_block(source, 'void reset(Runnable onComplete)')
        ordered = ['running=false', 'resetInstallationIdentity.run()', 'identity.reset()',
                   'mutations.call(', 'store.clearAll()', 'resetRuntime.run()', 'resetCloudObservations()']
        offsets = [reset.index(token) for token in ordered]
        self.assertEqual(sorted(offsets), offsets)
        self.assertIn('io.execute(wipe)', reset)
        self.assertIn('io.shutdown()', reset)
        self.assertIn('resetFallback.execute(wipe)', reset)
        self.assertNotIn('awaitTermination', reset)
        service = code_only((ROOT / JAVA / 'OverlayService.java').read_text())
        self.assertIn('cloudResetPending = true;', service)
        self.assertIn('spent.reset(', service)

    def test_store_and_repository_allow_only_exact_clear_and_constructor_micro_edits(self):
        """Inverse-hash proof preserves all qualified store/repository business methods and old injectable seams."""
        self.assertEqual({JAVA + 'installation/InstallationStore.java', JAVA + 'CloudTrackRepository.java'},
                         set(BASELINE['microProductionPatches']))
        for path, patches in BASELINE['microProductionPatches'].items():
            previous = inverse(retained_text(ROOT / path), patches)
            self.assertEqual(BASELINE['qualifiedRuntimeBlobs'][path], blob_hash(previous.encode()), path)
        store = code_only((ROOT / JAVA / 'installation/InstallationStore.java').read_text())
        clear = java_block(store, 'public boolean clearAll()')
        self.assertIn('synchronized (backend.monitor())', clear)
        self.assertIn('backend.commit(Collections.emptyMap(),Collections.emptySet(),true)', clear)
        self.assertIn('catch (RuntimeException failed) {return false;}', clear)
        self.assertIn('clearAll();', java_block(store, 'public void clearHistorical()'))

    def test_installer_handlers_wire_runtime_platform_and_signing_remain_byte_exact(self):
        """All old unexcepted production/resources/configuration bytes, including Phase E installer, remain frozen."""
        for path, expected in BASELINE['qualifiedRuntimeBlobs'].items():
            if path in BASELINE['authorizedProductionChanges'] or path in PHASE_G.get('authorizedProductionChanges', []):
                continue
            self.assertEqual(expected, blob_hash(retained_bytes(ROOT / path)), path)
        installer = JAVA + 'installation/PackageInstaller.java'
        self.assertIn(installer, BASELINE['qualifiedRuntimeBlobs'])
        self.assertEqual(BASELINE['qualifiedRuntimeBlobs'][installer], blob_hash(retained_bytes(ROOT / installer)))

    def test_auth_http_backoff_and_deferred_service_helpers_remain_unchanged(self):
        """Retain exact qualified auth/HTTPS/disconnect behavior and leave historical helpers for Phase G."""
        for name, key in [('CloudControlClient.java', 'protectedClientMethods'),
                          ('OverlayService.java', 'protectedServiceMethods')]:
            source = (ROOT / JAVA / name).read_text()
            historical = (ROOT / PHASE_G['historicalServiceFile']).read_text() if PHASE_G else source
            for signature, expected in BASELINE[key].items():
                if name == 'OverlayService.java' and any(method in signature for method in PHASE_G.get('removedServiceHelpers', [])):
                    method_source = historical
                else:
                    method_source = source
                self.assertEqual(expected, hashlib.sha256(java_block(method_source, signature).encode()).hexdigest(), signature)
        service = code_only((ROOT / (PHASE_G['historicalServiceFile'] if PHASE_G else JAVA + 'OverlayService.java')).read_text())
        for name in ('installManifestedRevision', 'confirmManifestedRevisionArmed', 'activateLegacyRevision'):
            self.assertEqual(1, len(re.findall(r'\b' + name + r'\s*\(', service)), name)

    def test_historical_client_oracle_and_changed_old_tests_reconstruct_exact_start(self):
        """Old assertions and fixtures survive unchanged; only exact old-client/reset-seam references are rewired."""
        expected_paths = {TEST + name for name in ('M4PhaseAFixtures.java', 'M4PhaseATransportTest.java',
                                                  'AssignmentMutationGateTest.java', 'ManifestInstallAckDecisionTest.java',
                                                  'CloudResetAsyncTest.java')}
        self.assertEqual(expected_paths, set(BASELINE['authorizedTestChanges']))
        oracle = (ROOT / BASELINE['historicalClientFile']).read_text().replace('M4PhaseFHistoricalCloudClient', 'CloudControlClient')
        self.assertEqual(BASELINE['qualifiedRuntimeBlobs'][JAVA + 'CloudControlClient.java'], blob_hash(oracle.encode()))
        for path, patches in BASELINE['testPatches'].items():
            previous = inverse(inverse(retained_text(ROOT / path),
                                       PHASE_G.get('testPatches', {}).get(path, [])), patches)
            self.assertEqual(BASELINE['frozenTestSources'][path], blob_hash(previous.encode()), path)

    def test_frozen_tests_fixtures_inventories_and_authoritative_documents_remain_exact(self):
        """All unmodified retained qualification evidence remains byte-exact rather than being rewritten to fit F."""
        frozen = {**BASELINE['frozenTestSources'], **BASELINE['frozenDocumentationBlobs']}
        for path, expected in frozen.items():
            if path in BASELINE['authorizedTestChanges'] or path in PHASE_G.get('authorizedProductionChanges', []):
                continue
            self.assertEqual(expected, blob_hash(retained_bytes(ROOT / path)), path)

    def test_production_inventory_and_semantic_bridge_have_no_phase_g_or_future_type(self):
        """Exactly one new adapter and one nested service port are permitted; the bridge remains handler-owned."""
        actual = {str(path.relative_to(ROOT)) for path in (ROOT / 'app/src/main/java').rglob('*.java')}
        self.assertEqual(set(BASELINE['productionFiles']) | calendar_model_files(), actual)
        bridges, ports = [], []
        for relative in actual:
            source = code_only((ROOT / relative).read_text())
            if re.search(r'VideoOverlayManifestBridge\s*\.\s*validate\s*\(', source):
                bridges.append(Path(relative).name)
            if re.search(r'\bimplements\s+VideoInstallationRuntimePorts\b', source):
                ports.append(Path(relative).name)
        self.assertEqual(['VideoManifestInstallationHandler.java'], bridges)
        self.assertEqual(['OverlayService.java'], ports)

    def test_prior_predicates_accounting_and_workflow_changes_are_exactly_reversible(self):
        """Reverse every inventoried boundary/CI addition to the exact F-start source; no earlier assertion is removed."""
        expected = {'tests/test_m4_phase_' + phase + '_boundary.py' for phase in 'bcde'}
        expected.update({'.github/scripts/m4-phase-a-test-summary.py', '.github/workflows/android-debug.yml'})
        self.assertEqual(expected, set(BASELINE['boundaryPatches']))
        for relative, patches in BASELINE['boundaryPatches'].items():
            previous = inverse(inverse(retained_text(ROOT / relative),
                                       PHASE_G.get('boundaryPatches', {}).get(relative, [])), patches)
            self.assertEqual(BASELINE['boundaryBlobs'][relative], blob_hash(previous.encode()), relative)

    def test_executed_phase_f_inventory_is_additive_and_private_skip_policy_unchanged(self):
        """The F bucket names/counts come from real execution, and accounting still requires all earlier suites."""
        self.assertEqual(12, len(BASELINE['phaseFSuites']))
        self.assertEqual(138, sum(BASELINE['phaseFSuites'].values()))
        self.assertTrue(all(name.startswith('M4PhaseF') and name.endswith('Test')
                            for name in BASELINE['phaseFSuites']))
        summary = (ROOT / '.github/scripts/m4-phase-a-test-summary.py').read_text()
        self.assertIn("**phase_f['phaseFSuites']", summary)
        self.assertIn("baseline['allowedOptInSkips']", summary)
        self.assertIn('if suites != expected:', summary)

    def test_modified_python_has_post_import_teaching_banners_and_every_function_documented(self):
        """Retain the code-quality rule for all new/modified Python, including helpers and accounting."""
        paths = [path for path in BASELINE['boundaryPatches'] if path.endswith('.py')]
        paths.append('tests/test_m4_phase_f_boundary.py')
        for relative in paths:
            tree = ast.parse((ROOT / relative).read_text())
            last_import = max(index for index, node in enumerate(tree.body)
                              if isinstance(node, (ast.Import, ast.ImportFrom)))
            self.assertTrue(any(isinstance(node, ast.Expr) and isinstance(node.value, ast.Constant)
                                and isinstance(node.value.value, str) for node in tree.body[last_import + 1:]), relative)
            for node in ast.walk(tree):
                if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                    self.assertTrue(ast.get_docstring(node), relative + ':' + node.name)


if __name__ == '__main__':
    unittest.main()
