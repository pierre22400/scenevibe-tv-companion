import ast
import json
from pathlib import Path
import re
import unittest
from sony_corrective_provenance import blob_hash, retained_bytes

"""Qualify the localized hard-reboot correction without weakening a single retained A–G gate.

The four runtime edits reverse to the exact failed Sony build. Retained Java tests, fixtures,
Cloud/installer/runtime owners, build/manifest/signing and earlier inventories stay byte-exact.
Actual-source checks protect the strict logical codec, observational diagnostic and test-only
native negative-control/process gate. Software preparation never counts as Sony acceptance.
"""

ROOT = Path(__file__).resolve().parents[1]
BASELINE = json.loads((ROOT / '.github/scripts/m4-phase-g-sony-corrective-baseline.json').read_text())
JAVA = 'app/src/main/java/com/scenevibe/tvcompanionpoc/'


def executable(path):
    """Inspect actual executable references, keeping explanatory comments out of dependency checks."""
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', (ROOT / path).read_text(), flags=re.S)


class M4SonyCorrectiveBoundaryTest(unittest.TestCase):
    """Bind the correction to the failed physical build and preserve finite, reviewable scope."""

    def test_start_bases_and_four_runtime_edits_are_exact(self):
        """No phase extension, new owner, platform permission, Cloud server or installation semantics is admitted."""
        self.assertEqual('824339de562c0de8542f4c1b2a22266832c8abef', BASELINE['referenceHead'])
        self.assertEqual('ecdf77bec9f93babf15239a63bf7f702fd7ca293', BASELINE['baseTv'])
        self.assertEqual('5011c91aac61a0cc6dcc74c256a15b7dee03d785', BASELINE['baseCloud'])
        self.assertEqual('eb0e0d823edc291bf1a39583ba70f098fac189e0', BASELINE['phaseFClosure'])
        self.assertEqual({JAVA + path for path in ('installation/AndroidInstallationBackend.java',
                         'installation/InstallationStore.java','RuntimeDiagnostics.java','DiagnosticsActivity.java')},
                         set(BASELINE['authorizedProductionChanges']))
        actual = {str(path.relative_to(ROOT)) for path in (ROOT / 'app/src/main/java').rglob('*.java')}
        self.assertEqual(set(BASELINE['productionFiles']), actual)

    def test_every_retained_blob_and_corrective_inverse_is_exact(self):
        """Every starting production/config/test/evidence file is preserved or reconstructed with unique finite edits."""
        expected = set(BASELINE['authorizedProductionChanges']) | set(BASELINE['authorizedProvenanceChanges'])
        expected.add('docs/m4-sony-physical-qualification-protocol.md')
        self.assertEqual(expected, set(BASELINE['correctivePatches']))
        for path, digest in BASELINE['startingBlobs'].items():
            content = retained_bytes(ROOT / path) if path in expected else (ROOT / path).read_bytes()
            self.assertEqual(digest, blob_hash(content), path)

    def test_retained_java_tests_fixtures_and_prior_inventories_have_no_exception(self):
        """No retained business assertion, frozen fixture or Phase A–G inventory may be rewritten or skipped."""
        for path, digest in BASELINE['startingBlobs'].items():
            if path.startswith('app/src/test/') or re.fullmatch(r'\.github/scripts/m4-phase-[a-g]-baseline.json', path):
                self.assertNotIn(path, BASELINE['correctivePatches'])
                self.assertEqual(digest, blob_hash((ROOT / path).read_bytes()), path)
        allowed = set(BASELINE['additiveFiles'])
        allowed.add('docs/m4-phase-g-sony-hard-reboot-corrective-report.md')
        for directory in ('app/src','tests','.github','docs'):
            for path in (ROOT / directory).rglob('*'):
                if not path.is_file() or '__pycache__' in path.parts:
                    continue
                relative = str(path.relative_to(ROOT))
                self.assertIn(relative, set(BASELINE['startingBlobs']) | allowed, relative)

    def test_only_android_transport_changes_not_logical_validation_or_authority(self):
        """The envelope protects XML text termination; old corruption is never trimmed or transparently repaired."""
        source = executable(JAVA + 'installation/AndroidInstallationBackend.java')
        for token in ('SNAPSHOT_PREFIX+value+SNAPSHOT_END', 'InstallationSnapshotCodec.MAX_ENCODED_CHARACTERS',
                      '!value.endsWith(SNAPSHOT_END)', 'editor.putString(key,previous)',
                      'preferenceValue(value.getKey(),value.getValue())'):
            self.assertIn(token, source)
        for forbidden in ('.trim(', '.strip(', '.replace(', 'clearAll(', 'markAcknowledged(',
                          'deviceToken', 'SharedPreferences.OnSharedPreferenceChangeListener'):
            self.assertNotIn(forbidden, source)
        codec = JAVA + 'installation/InstallationSnapshotCodec.java'
        self.assertEqual(BASELINE['startingBlobs'][codec], blob_hash((ROOT / codec).read_bytes()))
        for name in ('PackageInstaller.java','InstallRequest.java','InstallationSnapshot.java'):
            path = JAVA + 'installation/' + name
            self.assertEqual(BASELINE['startingBlobs'][path], blob_hash((ROOT / path).read_bytes()))

    def test_bounded_read_failure_is_observation_only_with_unchanged_closed_result(self):
        """Read-stage enums contain no payload/cause and cannot authorize a fallback, ACK, ARM or recovery."""
        store = executable(JAVA + 'installation/InstallationStore.java')
        self.assertIn('ack>(snapshot==null?0:snapshot.revision())', store)
        self.assertIn('new ReadResult(ReadState.CORRUPT,null,0,failure)', store)
        self.assertIn('catch (RuntimeException unavailable) {throw new BackendReadFailure();}', store)
        for token in ('catch (Throwable', 'catch (Error', '.getMessage()', 'printStackTrace', 'initCause('):
            self.assertNotIn(token, store)
        users = []
        for path in (ROOT / 'app/src/main/java').rglob('*.java'):
            if 'ReadFailure' in executable(str(path.relative_to(ROOT))):
                users.append(path.name)
        self.assertEqual(['DiagnosticsActivity.java','InstallationStore.java','RuntimeDiagnostics.java'], sorted(users))
        renderer = executable(JAVA + 'DiagnosticsActivity.java')
        self.assertIn('"Installation read failure", d.installationReadFailure.name()', renderer)

    def test_native_gate_requires_real_disk_and_distinct_target_processes(self):
        """A new store on the same process cannot satisfy the gate; native XML bytes and zero restore writes are required."""
        script = (ROOT / '.github/scripts/m4-sony-durability.sh').read_text()
        for token in ('invoke "$build" "$name" seed','am force-stop "$APP_ID"',
                      'pidof "$APP_ID"','invoke "$build" "$name" reload','"$first" = "$second"'):
            self.assertIn(token, script)
        fixture = executable('app/src/androidTest/java/com/scenevibe/tvcompanionpoc/M4SonyDurabilityInstrumentation.java')
        for token in ('new AndroidInstallationBackend(target)','getSharedPreferences("cloud_track",Context.MODE_PRIVATE)',
                      'seedPid!=Process.myPid()', 'diskDigest()', 'SAME_REVISION_STARTUP_ARMED',
                      'EXACT_ARTIFACT_BYTES','EXACT_CODEC_AND_HANDLER','EXACT_HISTORICAL_RESIDUE',
                      'backend.commits==0&&backend.acks==0&&backend.clears==0',
                      'OverlayService.restoreInstalledPackage(', 'new OverlayService.LiveVideoRuntimePorts(',
                      'REAL_DISK_COMMIT_REFUSED','android.system.Os.chmod(directory.getPath(),0500)',
                      'finally {android.system.Os.chmod(directory.getPath(),mode);}'):
            self.assertIn(token, fixture)
        for forbidden in ('Thread.sleep', 'getSharedPreferences("identity', 'new CloudControlClient', 'new CloudDeviceCredentials'):
            self.assertNotIn(forbidden, fixture)

    def test_same_native_fixture_executes_the_pinned_before_fix_negative_control(self):
        """Old actual Android code must reproduce present generic padding/CORRUPT before corrected persistence passes."""
        workflow = (ROOT / '.github/workflows/android-installation-durability.yml').read_text()
        self.assertIn('api: [31, 35]', workflow)
        self.assertIn('git archive 824339de562c0de8542f4c1b2a22266832c8abef', workflow)
        self.assertIn('SCENEVIBE_DURABILITY_TEST_ROOT: ${{ github.workspace }}', workflow)
        self.assertIn('matrix.api == 31', workflow)
        fixture = executable('app/src/androidTest/java/com/scenevibe/tvcompanionpoc/M4SonyDurabilityInstrumentation.java')
        for token in ('(expected+"    ").equals(backend.get(InstallationStore.SNAPSHOT_KEY))',
                      'BASELINE_POST_PROCESS_CORRUPT', 'BASELINE_RAW_ACK_EXACT',
                      'BASELINE_STARTUP_FAIL_CLOSED', 'BASELINE_CORRUPTION_REPRODUCED'):
            self.assertIn(token, fixture)
        init = (ROOT / '.github/scripts/m4-sony-durability.init.gradle').read_text()
        self.assertIn('sourceSets.androidTest.java.srcDirs', init)
        self.assertIn('sourceSets.androidTest.assets.srcDirs', init)
        self.assertNotIn('sourceSets.main', init)

    def test_actual_result_accounting_is_additive_and_native_evidence_is_separate(self):
        """The full actual Gradle suite map/private skip policy remains required; native counts cannot inflate JVM cases."""
        self.assertEqual({'M4SonyPreferenceTransportTest':10,'M4SonyReadFailureTest':9}, BASELINE['phaseGCorrectiveSuites'])
        summary = (ROOT / '.github/scripts/m4-phase-a-test-summary.py').read_text()
        for token in ("**sony['phaseGCorrectiveSuites']", 'if suites != expected:',
                      "baseline['allowedOptInSkips']", "baseline['fixtureSha256']"):
            self.assertIn(token, summary)
        native = (ROOT / '.github/scripts/m4-sony-durability-summary.py').read_text()
        for token in ('Missing native PASS','Missing instrumentation completion','Missing native process id',
                      "phases[0] == phases[1]",'Missing pinned baseline failure proof',
                      "'sonyPhysicalRequalification': 'NOT PERFORMED'"):
            self.assertIn(token, native)
        self.assertNotIn('SKIP', native)

    def test_all_modified_python_keeps_teaching_banners_and_function_docstrings(self):
        """Retained documentation survives inverse checks and every new Python responsibility has an explicit docstring."""
        paths = [path for path in BASELINE['correctivePatches'] if path.endswith('.py')]
        paths += [path for path in BASELINE['additiveFiles'] if path.endswith('.py')]
        for path in paths:
            tree = ast.parse((ROOT / path).read_text())
            last_import = max(i for i,node in enumerate(tree.body) if isinstance(node,(ast.Import,ast.ImportFrom)))
            self.assertTrue(any(isinstance(node,ast.Expr) and isinstance(node.value,ast.Constant)
                                and isinstance(node.value.value,str) for node in tree.body[last_import+1:]),path)
            for node in ast.walk(tree):
                if isinstance(node,(ast.FunctionDef,ast.AsyncFunctionDef)):
                    self.assertTrue(ast.get_docstring(node),path+':'+node.name)

    def test_sony_protocol_preserves_first_failure_and_pending_physical_gate(self):
        """The unchanged numbered acceptance checks record the old failure and require a genuine offline reboot of the candidate."""
        protocol = (ROOT / 'docs/m4-sony-physical-qualification-protocol.md').read_text()
        for token in ('PARTIAL / FAIL','NOT PERFORMED','GENERIC_INVALID / CACHE_FAILED',
                      '3aa97275855b85dd2ccecda395bb5c2c0724c9ccd4713576c523c127c4f9a6e2',
                      'Installation read failure: NONE','Last startup restore: ARMED','adb install -r',
                      'hard TV reboot','NOT RUN','existing explicit Reset Cloud'):
            self.assertIn(token,protocol)
        self.assertEqual(list(range(1,21)),[int(n) for n in re.findall(r'^(\d+)\. ',protocol,re.M)])
        self.assertNotIn('M4 COMPLETE',protocol)


if __name__ == '__main__':
    unittest.main()
