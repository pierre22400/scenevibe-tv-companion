import hashlib
import json
from pathlib import Path
import re
import unittest
from sony_corrective_provenance import retained_bytes, retained_text
from m5_phase_b_provenance import calendar_model_files
from m5_phase_c_provenance import phase_c_production_files

"""Keep Video semantic handlers outside the generic core and forbid an early installer cutover.

The frozen reference is the actual starting repository, changed only in name/comments.
Byte-pinned runtime/store/configuration/test blobs and unchanged orchestration fragments
balance the narrow repository/typed-state exceptions to the older Phase B/C gates.
Phase E permits its one generic orchestrator definition, never a current-caller cutover.
Phase F admits only its exact live adapter/caller/reset exceptions; the F gate pins all other bytes.
Phase G admits only its exact startup/metadata/reset exceptions; the G gate reverses and pins them.
Sony corrective provenance reverses only pinned edits; semantic assertions inspect actual production.

"""

ROOT = Path(__file__).resolve().parents[1]
PHASE_G_PATH = ROOT / '.github/scripts/m4-phase-g-baseline.json'
PHASE_G = json.loads(PHASE_G_PATH.read_text(encoding='utf-8')) if PHASE_G_PATH.exists() else {}
PHASE_F_PATH = ROOT / '.github/scripts/m4-phase-f-baseline.json'
PHASE_F = json.loads(PHASE_F_PATH.read_text(encoding='utf-8')) if PHASE_F_PATH.exists() else {}
BASELINE = json.loads((ROOT / '.github/scripts/m4-phase-d-baseline.json').read_text(encoding='utf-8'))
PHASE_E_PATH = ROOT / '.github/scripts/m4-phase-e-baseline.json'
PHASE_E = json.loads(PHASE_E_PATH.read_text(encoding='utf-8')) if PHASE_E_PATH.exists() else {}
PRODUCTION = ROOT / 'app/src/main/java/com/scenevibe/tvcompanionpoc'


def code_only(source):
    """Inspect executable references without interpreting preserved Javadocs as imports or calls."""
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.S)


def blob_hash(content):
    """Match the byte-exact original GitHub blob identity without trusting the current index."""
    return hashlib.sha1(b'blob ' + str(len(content)).encode('ascii') + b'\0' + content).hexdigest()


class M4PhaseDBoundaryTest(unittest.TestCase):
    """Protect generic isolation, explicit Video composition, frozen behavior and Phase D-only scope."""

    def test_generic_package_stays_product_and_runtime_free(self):
        """Typed preparation may not pull a concrete Video value, runtime owner or network into the core."""
        forbidden = r'\b(Video\w*|Prime|ScheduledTrack|TrackParser|OverlayManifest\w*|MediaSyncedTrackScheduler|'
        forbidden += r'SceneRuntimeController|SceneRenderer|OverlayRenderer|OverlayService|CloudControlClient|'
        forbidden += r'CloudProtocol|CloudDeviceCredentials|FinalTrack|Banner|Language|PackageInstaller)\b'
        for path in (PRODUCTION / 'installation').glob('*.java'):
            source = code_only(path.read_text(encoding='utf-8'))
            pattern = forbidden.replace('|PackageInstaller', '') if str(path.relative_to(ROOT)) == PHASE_E.get('installerFile') else forbidden
            self.assertIsNone(re.search(pattern, source), path.name)
            for token in ('java.net.', 'org.json.', 'java.lang.reflect.', 'Class.forName', 'ServiceLoader'):
                self.assertNotIn(token, source, path.name)
            if path.name != 'AndroidInstallationBackend.java':
                self.assertNotIn('android.', source, path.name)

    def test_bridge_call_belongs_only_to_manifested_handler(self):
        """No production repository or generic class can independently repeat Video cross-contract rules."""
        callers = []
        for path in PRODUCTION.rglob('*.java'):
            source = code_only(path.read_text(encoding='utf-8'))
            if re.search(r'VideoOverlayManifestBridge\s*\.\s*validate\s*\(', source):
                callers.append(path.name)
        self.assertEqual(['VideoManifestInstallationHandler.java'], callers)
        source = code_only((ROOT / BASELINE['authorizedSemanticDelegation']).read_text(encoding='utf-8'))
        self.assertNotIn('TrackParser.parse', source)
        self.assertNotIn('OverlayManifestParser.parse', source)

    def test_legacy_handler_has_no_graphical_parser_or_bridge(self):
        """Legacy input remains text-only and cannot acquire a manifested parsing branch."""
        source = code_only((PRODUCTION / 'VideoLegacyInstallationHandler.java').read_text(encoding='utf-8'))
        for token in ('OverlayManifest', 'OverlayManifestParser', 'VideoOverlayManifestBridge'):
            self.assertNotIn(token, source)
        common = code_only((PRODUCTION / 'VideoRuntimePreparation.java').read_text(encoding='utf-8'))
        self.assertIn('TrackParser.parse', common)
        self.assertNotIn('OverlayManifestParser', common)
        self.assertNotIn('VideoOverlayManifestBridge', common)

    def test_video_handlers_have_no_platform_transport_or_dynamic_dependencies(self):
        """All new Video definitions are memory/typed-port code without Context, windows, secrets or network."""
        forbidden = r'\b(Context|Service|WindowManager|View|Handler|Looper|CloudControlClient|CloudProtocol|'
        forbidden += r'CloudDeviceCredentials|FinalTrack|Pass0|Pass1|Pass2|DexClassLoader|WebView|PackageInstaller)\b'
        for relative in BASELINE['videoHandlerFiles']:
            source = code_only((ROOT / relative).read_text(encoding='utf-8'))
            self.assertIsNone(re.search(forbidden, source), relative)
            for token in ('android.', 'java.net.', 'java.io.', 'java.nio.file.', 'java.lang.reflect.',
                          'Class.forName', 'ServiceLoader', 'Runtime.getRuntime', 'getSharedPreferences', '.commit('):
                self.assertNotIn(token, source, relative)

    def test_registry_is_explicit_eager_and_exactly_two_entries(self):
        """Composition must bind the two existing durable identities without discovery or lookup construction."""
        source = code_only((PRODUCTION / 'VideoInstallationHandlers.java').read_text(encoding='utf-8'))
        self.assertEqual(2, source.count('new InstallationHandlerRegistry.Entry('))
        self.assertEqual(1, source.count('new VideoManifestInstallationHandler('))
        self.assertEqual(1, source.count('new VideoLegacyInstallationHandler('))
        for token in ('InstallationStore.COMPAT_OVERLAY_HANDLER_ID', 'InstallationStore.COMPAT_TRACK_HANDLER_ID',
                      'TvCapabilities.CODEC_TRACK_OVERLAY', 'TvCapabilities.CODEC_TRACK',
                      'static InstallationHandlerRegistry registry() {return REGISTRY;}'):
            self.assertIn(token, source)

    def test_production_has_no_installer_transport_or_restore_cutover(self):
        """Permit exactly the Phase D files/handlers while Cloud transport and service remain frozen callers."""
        old = {path for path in BASELINE['qualifiedRuntimeBlobs'] if path.endswith('.java')}
        expected = old | set(BASELINE['additiveContractFiles']) | {BASELINE['authorizedSemanticDelegation']} | set(BASELINE['videoHandlerFiles'])
        if PHASE_E:
            expected.add(PHASE_E['installerFile'])
        if PHASE_F:
            expected.add(PHASE_F['adapterFile'])
        expected.update(calendar_model_files() | phase_c_production_files())
        actual = {str(path.relative_to(ROOT)) for path in PRODUCTION.rglob('*.java')}
        self.assertEqual(expected, actual)
        implementations = []
        for relative in actual:
            source = code_only((ROOT / relative).read_text(encoding='utf-8'))
            if relative != PHASE_E.get('installerFile') and relative not in PHASE_F.get('authorizedLiveCallers', []):
                self.assertNotIn('PackageInstaller', source, relative)
            if re.search(r'\bimplements\s+InstallationHandler(?=\s|,|\{)', source):
                implementations.append(Path(relative).name)
        self.assertEqual(['VideoLegacyInstallationHandler.java', 'VideoManifestInstallationHandler.java'], sorted(implementations))
        client = code_only((PRODUCTION / 'CloudControlClient.java').read_text(encoding='utf-8'))
        for token in ('InstallRequest', 'InstallationHandlerRegistry', 'VideoInstallationHandlers', 'VideoInstallationRuntimePorts'):
            if token != 'InstallRequest' or not PHASE_F:
                self.assertNotIn(token, client)
        repository = code_only((ROOT / BASELINE['authorizedSemanticDelegation']).read_text(encoding='utf-8'))
        for token in ('.registry(', '.findCodec(', '.findHandler(', 'encodeForCache(', 'restoreFromCache(',
                      'new InstallRequest', 'new PreparedInstallation', 'store.read(', 'store.commit('):
            self.assertNotIn(token, repository)

    def test_qualified_runtime_store_wire_and_configs_remain_byte_exact(self):
        """No protected scheduler/renderer/identity/store/transport/manifest/signing input can drift."""
        for path, expected in BASELINE['qualifiedRuntimeBlobs'].items():
            if path in PHASE_F.get('authorizedProductionChanges', []) or path in PHASE_F.get('authorizedTestChanges', []) or path in PHASE_G.get('authorizedProductionChanges', []) or path in PHASE_G.get('authorizedTestChanges', []):
                continue
            self.assertEqual(expected, blob_hash(retained_bytes(ROOT / path)), path)
        source = (ROOT / BASELINE['authorizedSemanticDelegation']).read_text(encoding='utf-8')
        for patch in reversed(PHASE_F.get('microProductionPatches', {}).get(
                'app/src/main/java/com/scenevibe/tvcompanionpoc/CloudTrackRepository.java', [])):
            source = source.replace(patch['after'], patch['before'])
        for anchor, frozen in BASELINE['repositoryFrozenFragments'].items():
            fragment = source[source.index(anchor):source.index(frozen['end'])]
            self.assertEqual(frozen['sha256'], hashlib.sha256(fragment.encode()).hexdigest(), anchor)

    def test_differential_reference_is_the_qualified_starting_code(self):
        """The executable oracle retains every original code line; only its class name/docs differ."""
        source = (ROOT / BASELINE['historicalReferenceFile']).read_text(encoding='utf-8')
        normalized = code_only(source.replace('M4PhaseDHistoricalRepository', 'CloudTrackRepository'))
        normalized = '\n'.join(line for line in normalized.splitlines() if line.strip())
        self.assertEqual(BASELINE['historicalRepositoryCodeSha256'], hashlib.sha256(normalized.encode()).hexdigest())
        for path, expected in BASELINE['frozenTestSources'].items():
            if path in PHASE_F.get('authorizedProductionChanges', []) or path in PHASE_F.get('authorizedTestChanges', []) or path in PHASE_G.get('authorizedProductionChanges', []) or path in PHASE_G.get('authorizedTestChanges', []):
                continue
            self.assertEqual(expected, blob_hash(retained_bytes(ROOT / path)), path)

    def test_prepared_state_contract_is_typed_and_additive_only(self):
        """The original constructor stays available; the new slot accepts no raw Object or concrete product."""
        handler = code_only((PRODUCTION / 'installation/InstallationHandler.java').read_text(encoding='utf-8'))
        prepared = code_only((PRODUCTION / 'installation/PreparedInstallation.java').read_text(encoding='utf-8'))
        self.assertIn('interface PreparedState {}', handler)
        self.assertIn('InstallationHandler.PreparedState preparedState', prepared)
        self.assertEqual(2, prepared.count('public PreparedInstallation('))
        self.assertIn('this(canonical,handlerId,values,requirements,capabilities,null);', prepared)
        self.assertIsNone(re.search(r'\bObject\b', handler + prepared))


if __name__ == '__main__':
    unittest.main()
