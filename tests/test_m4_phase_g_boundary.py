import ast
import hashlib
import json
from pathlib import Path
import re
import unittest
from sony_corrective_provenance import retained_bytes, retained_text
from m5_phase_b_provenance import calendar_model_files

"""Qualify generic startup, metadata and reset with exact provenance for every retained gate.

Six old production owners may change only through uniquely reversible inventoried hunks.
The entire generic core, F client/wire/ACK path, media cores and build/signing inputs stay pinned.
Old service helpers move verbatim into a test-only oracle; their original assertions survive.
Executed G cases extend actual Gradle XML accounting and the Sony document remains a manual protocol.
Sony corrective provenance reverses only pinned edits; semantic assertions inspect actual production.

"""

ROOT = Path(__file__).resolve().parents[1]
BASELINE = json.loads((ROOT / '.github/scripts/m4-phase-g-baseline.json').read_text(encoding='utf-8'))
PHASE_F = json.loads((ROOT / '.github/scripts/m4-phase-f-baseline.json').read_text(encoding='utf-8'))
JAVA = 'app/src/main/java/com/scenevibe/tvcompanionpoc/'
TEST = 'app/src/test/java/com/scenevibe/tvcompanionpoc/'


def code_only(source):
    """Inspect executable references without counting historical explanations as production callers."""
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.S)


def blob_hash(content):
    """Match immutable starting GitHub blob identity without trusting a local Git index."""
    return hashlib.sha1(b'blob ' + str(len(content)).encode('ascii') + b'\0' + content).hexdigest()


def inverse(source, patches):
    """Reverse only exact reviewed unique edits; missing or duplicated context is a hard failure."""
    for patch in reversed(patches):
        if source.count(patch['after']) != 1:
            raise ValueError('Missing or ambiguous inventoried Phase G edit')
        source = source.replace(patch['after'], patch['before'])
    return source


def java_block(source, signature):
    """Extract a known balanced block for actual production ordering and retained method provenance."""
    start = source.index(signature)
    opening = source.index('{', start)
    depth = 1
    for end in range(opening + 1, len(source)):
        depth += (source[end] == '{') - (source[end] == '}')
        if depth == 0:
            return source[start:end + 1]
    raise ValueError('Unbalanced known Java block')


class M4PhaseGBoundaryTest(unittest.TestCase):
    """Pin the sole startup authority, read-only views, retained software behavior and Sony boundary."""

    def test_start_bases_and_authorized_scope_are_exact(self):
        """Inventory exceptions cannot expand into new product, core, Cloud server or platform semantics."""
        self.assertEqual('3a987f337a324060b67b78cc1e3f6c29233296f8', BASELINE['referenceHead'])
        self.assertEqual('eb0e0d823edc291bf1a39583ba70f098fac189e0', BASELINE['phaseFClosure'])
        self.assertEqual('ecdf77bec9f93babf15239a63bf7f702fd7ca293', BASELINE['baseTv'])
        self.assertEqual('5011c91aac61a0cc6dcc74c256a15b7dee03d785', BASELINE['baseCloud'])
        self.assertEqual({JAVA + name + '.java' for name in ('OverlayService', 'BootReceiver', 'RuntimeDiagnostics',
                                                          'DiagnosticsStore', 'DiagnosticsActivity', 'MainActivity')},
                         set(BASELINE['authorizedProductionChanges']))
        self.assertEqual({TEST + name + '.java' for name in ('M4PhaseAFixtures', 'AssignmentMutationGateTest',
                                                          'ManifestInstallAckDecisionTest')}, set(BASELINE['authorizedTestChanges']))

    def test_every_modified_production_hunk_is_exactly_reversible(self):
        """Reverse the reviewed G changes and prove all six full starting blobs, not selected favorable fragments."""
        self.assertEqual(set(BASELINE['authorizedProductionChanges']), set(BASELINE['productionPatches']))
        for path, patches in BASELINE['productionPatches'].items():
            self.assertTrue(patches, path)
            previous = inverse(retained_text(ROOT / path), patches)
            self.assertEqual(BASELINE['qualifiedRuntimeBlobs'][path], blob_hash(previous.encode()), path)

    def test_service_startup_reuses_same_installer_with_no_write_parser_or_lookup(self):
        """Only the existing install entry point may restore a durable snapshot; no new coordinator or fallback exists."""
        source = code_only((ROOT / JAVA / 'OverlayService.java').read_text())
        restore = java_block(source, 'static InstallationStatus restoreInstalledPackage(')
        self.assertEqual(1, restore.count('store.read()'))
        self.assertEqual(1, restore.count('installer.install(durable.snapshot().canonical(), ports)'))
        self.assertIn('ReadState.EMPTY', restore)
        self.assertIn('ReadState.CORRUPT', restore)
        self.assertIn('InstallationStatus.CACHE_FAILED', restore)
        self.assertIn('setLastStartupRestoreResult(result)', restore)
        for forbidden in ('.commit(', 'clearAll(', 'markAcknowledged', 'historicalValue', 'artifact(',
                          'findHandler(', 'findCodec(', 'restoreFromCache(', 'TrackParser', 'OverlayManifestParser',
                          'new JSONObject', 'CloudControlClient', 'CloudTrackRepository', 'catch (Throwable', 'catch (Error'):
            self.assertNotIn(forbidden, restore)
        self.assertEqual(2, source.count('restoreInstalledPackage('))

    def test_restore_completes_before_probe_or_cloud_on_one_main_owned_stack(self):
        """The actual composition restores once before either owner can consume passive media or live delivery."""
        source = code_only((ROOT / JAVA / 'OverlayService.java').read_text())
        start = java_block(source, 'public int onStartCommand(')
        for token in ('new InstallationStore(', 'new AndroidInstallationBackend(', 'new PackageInstaller(', 'new LiveVideoRuntimePorts('):
            self.assertEqual(1, source.count(token), token)
        self.assertIn('VideoInstallationHandlers.registry(), TvCapabilities.current()', start)
        self.assertIn('() -> Looper.myLooper() == Looper.getMainLooper()', start)
        ordered = ['new PackageInstaller(', 'new LiveVideoRuntimePorts(', 'restoreInstalledPackage(',
                   'mediaSessionProbe.start()', 'new CloudControlClient(', 'cloudClient.start()']
        offsets = [start.index(token) for token in ordered]
        self.assertEqual(sorted(offsets), offsets)
        self.assertIn('videoRuntimePorts, videoRuntimePorts::abortActivation, () -> cloudClient', start)

    def test_no_production_owner_can_use_historical_repository(self):
        """Retaining the isolated characterization type must not preserve any live/startup/diagnostic caller."""
        users = []
        for path in (ROOT / 'app/src/main/java').rglob('*.java'):
            source = code_only(path.read_text())
            if re.search(r'\bCloudTrackRepository\b', source):
                users.append(path.name)
        self.assertEqual(['CloudTrackRepository.java'], users)
        for name in BASELINE['removedServiceHelpers']:
            for path in (ROOT / 'app/src/main/java').rglob('*.java'):
                self.assertNotRegex(code_only(path.read_text()), r'\b' + name + r'\s*\(', path.name)

    def test_boot_receiver_reads_metadata_only_and_starts_only_boot_prepare(self):
        """Receiver permission/credential/opt-in logic delegates unchanged policy, without ARM, parsing or writes."""
        source = code_only((ROOT / JAVA / 'BootReceiver.java').read_text())
        self.assertIn('new InstallationStore(new AndroidInstallationBackend(app)).read()', source)
        self.assertIn('durable.state() == InstallationStore.ReadState.SNAPSHOT', source)
        self.assertIn('AutostartPolicy.decide(enabled, overlay, media, credential,', source)
        self.assertEqual(['OverlayService.ACTION_BOOT_PREPARE'], re.findall(r'\.setAction\(([^)]+)\)', source))
        for token in ('Intent.ACTION_BOOT_COMPLETED', 'Intent.ACTION_MY_PACKAGE_REPLACED', 'app.startForegroundService(prepare)'):
            self.assertIn(token, source)
        for forbidden in ('PackageInstaller', '.canonical(', '.artifact(', '.commit(', 'clearAll(', 'markAcknowledged',
                          'findHandler', 'VideoInstallationHandlers', 'TrackParser', 'new JSONObject', 'MainActivity'):
            self.assertNotIn(forbidden, source)

    def test_diagnostics_reads_one_generic_projection_with_coherent_old_aliases(self):
        """No opaque content is parsed or retained to populate a Video compatibility id."""
        source = code_only((ROOT / JAVA / 'RuntimeDiagnostics.java').read_text())
        self.assertIn('enum InstallationState { EMPTY, READY, CORRUPT }', source)
        self.assertIn('new InstallationStore(new AndroidInstallationBackend(context))', source)
        self.assertIn('return installationSnapshot(store, observed)', source)
        self.assertIn('new Builder().installation(store.read())', source)
        for token in ('cachedTrackPresent = installationPresent', 'cachedRevision = installedRevision',
                      'lastAcknowledgedRevision = acknowledgedRevision', 'cachedTrackId = null'):
            self.assertIn(token, source)
        for forbidden in ('.artifact(', '.artifacts(', '.canonical(', 'historicalValue(', 'TrackParser',
                          'OverlayManifestParser', 'new JSONObject', 'PackageInstaller', '.commit(', 'clearAll(', 'markAcknowledged'):
            self.assertNotIn(forbidden, source)

    def test_capture_remains_read_only_and_does_not_mint_or_migrate_identity(self):
        """The existing peeks and bounded secret-unavailable mapping remain intact in actual capture."""
        source = code_only(java_block((ROOT / JAVA / 'RuntimeDiagnostics.java').read_text(), 'static RuntimeDiagnostics capture('))
        self.assertIn('CloudDeviceCredentials.peek(context)', source)
        self.assertIn('new InstallationIdentity(context).peekInstallationId()', source)
        self.assertIn('CloudErrorCode.CREDENTIAL_UNAVAILABLE', source)
        for forbidden in ('.installationId()', '.deviceToken()', '.activationSecret()', '.reset(', '.commit(', '.rotateForCloudReset('):
            self.assertNotIn(forbidden, source)

    def test_diagnostics_screen_is_generic_first_with_labeled_compatibility(self):
        """The actual renderer exposes bounded metadata/outcomes only, keeping all older display fields."""
        source = code_only(java_block((ROOT / JAVA / 'DiagnosticsActivity.java').read_text(), 'static String render('))
        labels = ['Installed package', 'Package codec', 'Package handler', 'Installed revision', 'Acknowledged revision']
        for label in labels + ['Last startup restore', 'Cached track (compatibility)', 'Cached track id (compatibility)',
                               'Cached revision (compatibility)', 'Last acknowledged revision (compatibility)']:
            self.assertIn('"' + label + '"', source)
        self.assertLess(source.index('"Installed revision"'), source.index('"Cached revision (compatibility)"'))
        for forbidden in ('artifact', 'canonical', 'deviceToken', 'activationSecret', 'http', 'exception', 'InstallationStore'):
            self.assertNotIn(forbidden, source)

    def test_normal_screen_uses_snapshot_presence_without_installation_metadata(self):
        """Consumer saved-content wording does not treat corruption as usable or expose codec/revision/content."""
        source = code_only(java_block((ROOT / JAVA / 'MainActivity.java').read_text(), 'static String cloudStatus('))
        self.assertIn('durable.state() == InstallationStore.ReadState.SNAPSHOT', source)
        self.assertIn('Offline (using saved SceneVibe content)', source)
        for forbidden in ('.revision(', '.codecId(', '.handlerId(', '.canonical(', '.artifact(', 'getString(', 'new JSONObject'):
            self.assertNotIn(forbidden, source)
        refresh = code_only(java_block((ROOT / JAVA / 'MainActivity.java').read_text(), 'private void refreshCloud()'))
        self.assertIn('new InstallationStore(new AndroidInstallationBackend(this)).read()', refresh)
        self.assertIn('cloudStatus(identity.userCode()', refresh)

    def test_stopped_reset_is_explicit_whole_file_and_running_reset_stays_coordinated(self):
        """Only the explicit reset may clear installation state; identity and credentials stay separate owners."""
        source = code_only((ROOT / JAVA / 'DiagnosticsActivity.java').read_text())
        reset = java_block(source, 'static boolean resetStoppedInstallation(')
        ordered = ['rotateIdentity.run()', 'resetCredentials.run()', 'store.clearAll()', 'observed.resetCloudObservations()']
        offsets = [reset.index(token) for token in ordered]
        self.assertEqual(sorted(offsets), offsets)
        self.assertEqual(1, reset.count('store.clearAll()'))
        self.assertIn('CloudErrorCode.NETWORK', reset)
        dispatch = java_block(source, 'private void resetCloud()')
        self.assertIn('DiagnosticsStore.INSTANCE.serviceRunning()', dispatch)
        self.assertIn('.setAction(OverlayService.ACTION_CLOUD_RESET)', dispatch)
        self.assertIn('new InstallationIdentity(this).rotateForCloudReset()', dispatch)
        self.assertIn('new CloudDeviceCredentials(this).reset()', dispatch)
        self.assertNotIn('new CloudControlClient', dispatch)

    def test_cloud_installer_handlers_runtime_and_build_inputs_are_byte_exact(self):
        """Pin every unexcepted production/configuration blob, including the complete qualified F live ACK path."""
        for path, expected in BASELINE['qualifiedRuntimeBlobs'].items():
            if path in BASELINE['authorizedProductionChanges']:
                continue
            self.assertEqual(expected, blob_hash(retained_bytes(ROOT / path)), path)
        for name in ('CloudControlClient.java', 'CloudV1InstallationAdapter.java', 'CloudProtocol.java',
                     'AutostartPolicy.java', 'installation/PackageInstaller.java', 'installation/InstallationStore.java'):
            path = JAVA + name
            self.assertEqual(BASELINE['qualifiedRuntimeBlobs'][path], blob_hash(retained_bytes(ROOT / path)), name)

    def test_generic_core_bridge_and_static_registry_remain_isolated(self):
        """No new parser, registry, runtime capability, future milestone or second restore authority can appear."""
        actual = {str(path.relative_to(ROOT)) for path in (ROOT / 'app/src/main/java').rglob('*.java')}
        self.assertEqual(set(BASELINE['productionFiles']) | calendar_model_files(), actual)
        bridges, lookup = [], []
        for path in actual:
            source = code_only((ROOT / path).read_text())
            if re.search(r'VideoOverlayManifestBridge\s*\.\s*validate\s*\(', source):
                bridges.append(Path(path).name)
            if re.search(r'\.findHandler\(', source):
                lookup.append(Path(path).name)
            if '/installation/' in path:
                self.assertNotRegex(source, r'\b(Video\w*|Cloud\w*|Prime|FinalTrack|SceneRuntimeController|OverlayService|Banner|Language)\b')
                for token in ('org.json.', 'java.net.', 'java.lang.reflect.', 'Class.forName', 'ServiceLoader', 'DexClassLoader'):
                    self.assertNotIn(token, source, path)
                if not path.endswith('/AndroidInstallationBackend.java'):
                    self.assertNotIn('android.', source, path)
        self.assertEqual(['VideoManifestInstallationHandler.java'], bridges)
        self.assertEqual(['PackageInstaller.java'], lookup)

    def test_moved_historical_helpers_are_verbatim_and_actual_service_ports_are_unchanged(self):
        """Frozen characterization is test-only, while new G integration executes the real production ports."""
        oracle = (ROOT / BASELINE['historicalServiceFile']).read_text()
        self.assertEqual(BASELINE['historicalServiceSha256'], hashlib.sha256(oracle.encode()).hexdigest())
        start = oracle.index('    /**\n     * Android-free, unit-testable core of the manifested-install ACK decision')
        end = oracle.index('    /** This historical oracle', start)
        self.assertEqual(BASELINE['historicalServiceBodySha256'], hashlib.sha256(oracle[start:end].encode()).hexdigest())
        for signature, expected in PHASE_F['protectedServiceMethods'].items():
            source = oracle if any(name in signature for name in BASELINE['removedServiceHelpers']) else (ROOT / JAVA / 'OverlayService.java').read_text()
            self.assertEqual(expected, hashlib.sha256(java_block(source, signature).encode()).hexdigest(), signature)
        current = (ROOT / JAVA / 'OverlayService.java').read_text()
        previous = inverse(current, BASELINE['productionPatches'][JAVA + 'OverlayService.java'])
        self.assertEqual(java_block(previous, 'static final class LiveVideoRuntimePorts'), java_block(current, 'static final class LiveVideoRuntimePorts'))
        fixture = (ROOT / TEST / 'M4PhaseGFixtures.java').read_text()
        self.assertIn('new M4PhaseFFixtures.Runtime(', fixture)
        self.assertNotRegex(code_only(fixture), r'\b(CloudControlClient|CloudDeviceCredentials|CloudTrackRepository)\b')

    def test_all_retained_tests_fixtures_inventories_and_documents_preserve_provenance(self):
        """Only three historical helper names may change; every original assertion and retained F test stays exact."""
        self.assertEqual(set(BASELINE['authorizedTestChanges']), set(BASELINE['testPatches']))
        for path, expected in BASELINE['frozenTestSources'].items():
            previous = inverse(retained_text(ROOT / path), BASELINE['testPatches'].get(path, [])) if path in BASELINE['testPatches'] else retained_bytes(ROOT / path)
            content = previous.encode() if isinstance(previous, str) else previous
            self.assertEqual(expected, blob_hash(content), path)
        for path, expected in BASELINE['frozenDocumentationBlobs'].items():
            self.assertEqual(expected, blob_hash(retained_bytes(ROOT / path)), path)

    def test_old_boundary_predicates_and_accounting_changes_are_exactly_reversible(self):
        """Every old gate/assertion and workflow step survives after undoing only exact G exceptions/labels."""
        expected = {'tests/test_m4_phase_' + phase + '_boundary.py' for phase in 'bcdef'}
        expected.update({'.github/scripts/m4-phase-a-test-summary.py', '.github/workflows/android-debug.yml'})
        self.assertEqual(expected, set(BASELINE['boundaryPatches']))
        for path, patches in BASELINE['boundaryPatches'].items():
            previous = inverse(retained_text(ROOT / path), patches)
            self.assertEqual(BASELINE['boundaryBlobs'][path], blob_hash(previous.encode()), path)

    def test_executed_g_bucket_is_additive_and_only_the_private_skip_is_allowed(self):
        """G inventory is bound to actual execution; summary still compares the entire suite map and frozen fixtures."""
        self.assertEqual(9, len(BASELINE['phaseGSuites']))
        self.assertEqual(131, sum(BASELINE['phaseGSuites'].values()))
        actual = {str(path.relative_to(ROOT)) for path in (ROOT / TEST).glob('M4PhaseG*.java')}
        self.assertEqual(set(BASELINE['phaseGTestFiles']), actual)
        for suite in BASELINE['phaseGSuites']:
            self.assertTrue((ROOT / TEST / (suite + '.java')).is_file(), suite)
        summary = (ROOT / '.github/scripts/m4-phase-a-test-summary.py').read_text()
        self.assertIn("**phase_g['phaseGSuites']", summary)
        self.assertIn('if suites != expected:', summary)
        self.assertIn("baseline['allowedOptInSkips']", summary)
        self.assertIn("baseline['fixtureSha256']", summary)

    def test_modified_python_is_documented_and_sony_protocol_has_no_claimed_result(self):
        """Preserve teaching banners/docstrings and require the manual physical boundary rather than a CI claim."""
        paths = [path for path in BASELINE['boundaryPatches'] if path.endswith('.py')] + ['tests/test_m4_phase_g_boundary.py']
        for path in paths:
            tree = ast.parse((ROOT / path).read_text())
            last_import = max(index for index, node in enumerate(tree.body) if isinstance(node, (ast.Import, ast.ImportFrom)))
            self.assertTrue(any(isinstance(node, ast.Expr) and isinstance(node.value, ast.Constant)
                                and isinstance(node.value.value, str) for node in tree.body[last_import + 1:]), path)
            for node in ast.walk(tree):
                if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                    self.assertTrue(ast.get_docstring(node), path + ':' + node.name)
        protocol = (ROOT / 'docs/m4-sony-physical-qualification-protocol.md').read_text()
        self.assertIn('NOT PERFORMED', protocol)
        self.assertIn('adb install -r', protocol)
        self.assertIn('hard TV reboot', protocol)
        self.assertIn('PASS / FAIL / NOT RUN', protocol)
        self.assertIn('force-stop', protocol)
        self.assertIn('Unicode', protocol)
        self.assertEqual(list(range(1, 21)), [int(number) for number in re.findall(r'^(\d+)\. ', protocol, re.M)])


if __name__ == '__main__':
    unittest.main()
