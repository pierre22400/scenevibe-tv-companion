import ast
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
from sony_corrective_provenance import retained_bytes, retained_text
from m5_phase_b_provenance import calendar_model_files

"""Keep Phase E's single orchestrator isolated and all qualified production callers frozen.

The exact starting GitHub tree pins every old production/resource/configuration blob,
every old JVM test/fixture and the earlier inventories. Earlier boundary predicates
and CI accounting may gain only the documented Phase E inventory/bucket exceptions.
A real JDK-only compile verifies the installer cannot acquire platform dependencies.
Phase F admits only its exact live adapter/caller/reset exceptions; the F gate pins all other bytes.
Phase G admits only its exact startup/metadata/reset exceptions; the G gate reverses and pins them.
Sony corrective provenance reverses only pinned edits; semantic assertions inspect actual production.

"""

ROOT = Path(__file__).resolve().parents[1]
PHASE_G_PATH = ROOT / '.github/scripts/m4-phase-g-baseline.json'
PHASE_G = json.loads(PHASE_G_PATH.read_text(encoding='utf-8')) if PHASE_G_PATH.exists() else {}
PHASE_F_PATH = ROOT / '.github/scripts/m4-phase-f-baseline.json'
PHASE_F = json.loads(PHASE_F_PATH.read_text(encoding='utf-8')) if PHASE_F_PATH.exists() else {}
BASELINE = json.loads((ROOT / '.github/scripts/m4-phase-e-baseline.json').read_text(encoding='utf-8'))
INSTALLER = 'app/src/main/java/com/scenevibe/tvcompanionpoc/installation/PackageInstaller.java'


def code_only(source):
    """Inspect executable references without treating pedagogical comments as dependencies."""
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.S)


def blob_hash(content):
    """Match exact starting GitHub blob identities independently of the current index."""
    return hashlib.sha1(b'blob ' + str(len(content)).encode('ascii') + b'\0' + content).hexdigest()


def without_phase_e_accounting(relative, source):
    """Undo only the explicitly authorized E inventory/bucket additions, preserving prior predicates."""
    if relative.endswith('android-debug.yml'):
        return source.replace('Phase A/B/C/D/E', 'Phase A/B/C/D')
    if relative.endswith('m4-phase-a-test-summary.py'):
        source = source.replace('Phase E adds executed orchestration, readback, real-Video and historical-cache buckets.\n', '')
        source = source.replace("    phase_e_path = root / '.github/scripts/m4-phase-e-baseline.json'\n", '')
        source = source.replace("    phase_e = json.loads(phase_e_path.read_text(encoding='utf-8')) if phase_e_path.exists() else {'phaseESuites': {}}\n", '')
        source = source.replace(", **phase_e['phaseESuites']", '')
        source = source.replace("        'phaseECases': sum(phase_e['phaseESuites'].values()),\n", '')
        source = source.replace('; Phase E: {summary["phaseECases"]}', '')
        return source.replace('Phase A/B/C/D/E', 'Phase A/B/C/D')
    banners = (
        'Phase E admits exactly its isolated PackageInstaller, with no existing-caller exception.\n',
        'Phase E adds only its explicitly inventoried orchestration class; the store remains pinned.\n',
        'Phase E permits its one generic orchestrator definition, never a current-caller cutover.\n',
    )
    for banner in banners:
        source = source.replace(banner, '')
    source = source.replace("PHASE_E_PATH = ROOT / '.github/scripts/m4-phase-e-baseline.json'\n", '')
    source = source.replace("PHASE_E = json.loads(PHASE_E_PATH.read_text(encoding='utf-8')) if PHASE_E_PATH.exists() else {}\n", '')
    for variable in ('new_files', 'expected'):
        source = source.replace(f"        if PHASE_E:\n            {variable}.add(PHASE_E['installerFile'])\n", '')
    for variable in ('path', 'relative'):
        source = source.replace(f"            if {variable} != PHASE_E.get('installerFile'):\n                self.assertNotIn('PackageInstaller', source, {variable})\n",
                                f"            self.assertNotIn('PackageInstaller', source, {variable})\n")
    source = source.replace("            pattern = forbidden.replace('|PackageInstaller', '') if str(path.relative_to(ROOT)) == PHASE_E.get('installerFile') else forbidden\n", '')
    return source.replace('            self.assertIsNone(re.search(pattern, source), path.name)\n',
                          '            self.assertIsNone(re.search(forbidden, source), path.name)\n')


class M4PhaseEBoundaryTest(unittest.TestCase):
    """Prove pure orchestration, unchanged handlers/store/callers and exact authorized scope."""

    def test_installer_api_is_local_small_and_in_generic_package(self):
        """One final type may expose only its explicit constructor and synchronous install entry point."""
        self.assertEqual(INSTALLER, BASELINE['installerFile'])
        source = code_only((ROOT / INSTALLER).read_text(encoding='utf-8'))
        self.assertIn('package com.scenevibe.tvcompanionpoc.installation;', source)
        self.assertIn('public final class PackageInstaller', source)
        self.assertEqual(['PackageInstaller', 'install'], re.findall(
            r'\bpublic\s+(?:synchronized\s+)?(?:InstallationStatus\s+)?(\w+)\s*\(', source))
        fields = re.findall(r'private final (\w+) \w+;', source)
        self.assertEqual(['InstallationStore', 'InstallationHandlerRegistry', 'TvCapabilities'], fields)
        self.assertNotRegex(source, r'\bpublic\s+.*\b(boot|reboot|restore|ack)\s*\(')

    def test_installer_and_pure_model_compile_with_empty_external_classpath(self):
        """Compile the real store/model/installer together with JDK only, excluding the Android backend."""
        javac, java = shutil.which('javac'), shutil.which('java')
        self.assertTrue(javac or java, 'JDK compiler required for Phase E isolation')
        compiler = [javac] if javac else [java, '-m', 'jdk.compiler/com.sun.tools.javac.Main']
        paths = sorted((ROOT / INSTALLER).parent.glob('*.java'))
        paths = [str(path) for path in paths if path.name != 'AndroidInstallationBackend.java']
        with tempfile.TemporaryDirectory(prefix='scenevibe-m4e-') as directory:
            work = Path(directory)
            empty, output = work / 'empty', work / 'classes'
            empty.mkdir()
            output.mkdir()
            result = subprocess.run(compiler + ['-encoding', 'UTF-8', '-classpath', str(empty),
                                               '-sourcepath', str(empty), '-d', str(output)] + paths,
                                    capture_output=True, timeout=30, check=False)
            self.assertEqual(0, result.returncode, 'JDK-only PackageInstaller compilation failed')
            self.assertTrue((output / 'com/scenevibe/tvcompanionpoc/installation/PackageInstaller.class').is_file())

    def test_installer_has_no_product_platform_network_or_dynamic_dependency(self):
        """Reject domain/platform references and both imported and qualified side-effect APIs."""
        source = code_only((ROOT / INSTALLER).read_text(encoding='utf-8'))
        forbidden = r'\b(Video\w*|Prime\w*|FinalTrack\w*|Cloud\w*|Overlay\w*|SceneRenderer|SceneRuntimeController|'
        forbidden += r'MediaSyncedTrackScheduler|TrackParser|ScheduledTrack|Context|Service|Handler|Looper|View|'
        forbidden += r'WindowManager|Banner|Language|DiagnosticsStore|Thread|ClassLoader|DexClassLoader|WebView)\b'
        self.assertNotRegex(source, forbidden)
        for token in ('android.', 'org.json.', 'java.net.', 'java.io.', 'java.nio.file.', 'java.lang.reflect.',
                      'java.util.concurrent.', 'Class.forName', 'ServiceLoader', 'getClass(', 'getMethod(',
                      'getDeclaredMethod(', 'Runtime.getRuntime', 'System.', 'credential', 'finalTrackId'):
            self.assertNotIn(token, source)
        self.assertEqual(['java.util.Arrays', 'java.util.Map'], re.findall(r'\bimport\s+([\w.]+)\s*;', source))

    def test_ack_and_vm_fatal_errors_stay_outside_installer(self):
        """No confirmation API/callback/flag exists, and ordinary runtime rejection never swallows VM errors."""
        source = code_only((ROOT / INSTALLER).read_text(encoding='utf-8'))
        self.assertNotRegex(source, r'(?i)\b(markAcknowledged|ackEligible|ackEligibility|ackCallback|ackRevision|finalTrackId)\b')
        caught = re.findall(r'\bcatch\s*\(\s*(\w+)\s+\w+\s*\)', source)
        self.assertTrue(caught)
        self.assertEqual({'RuntimeException'}, set(caught))
        self.assertEqual(1, source.count('store.commit('))
        self.assertEqual(1, source.count('.restoreFromCache('))
        self.assertEqual(1, source.count('.arm('))

    def test_all_old_production_handlers_store_and_configs_remain_byte_exact(self):
        """Pin all prior Java/resources/manifest/build/signing inputs, including every current production caller."""
        for path, expected in BASELINE['qualifiedRuntimeBlobs'].items():
            if path in PHASE_F.get('authorizedProductionChanges', []) or path in PHASE_F.get('authorizedTestChanges', []) or path in PHASE_G.get('authorizedProductionChanges', []) or path in PHASE_G.get('authorizedTestChanges', []):
                continue
            self.assertEqual(expected, blob_hash(retained_bytes(ROOT / path)), path)
        for name in ('CloudControlClient', 'CloudTrackRepository', 'OverlayService', 'BootReceiver'):
            source = code_only((ROOT / 'app/src/main/java/com/scenevibe/tvcompanionpoc' / (name + '.java')).read_text())
            if str((ROOT / 'app/src/main/java/com/scenevibe/tvcompanionpoc' / (name + '.java')).relative_to(ROOT)) not in PHASE_F.get('authorizedLiveCallers', []):
                self.assertNotIn('PackageInstaller', source, name)

    def test_every_retained_jvm_test_fixture_inventory_and_work_order_stays_frozen(self):
        """Protect earlier qualification evidence and authoritative documents without changing fixtures to fit E."""
        frozen = {**BASELINE['frozenTestSources'], **BASELINE['frozenDocumentationBlobs']}
        for path, expected in frozen.items():
            if path in PHASE_F.get('authorizedProductionChanges', []) or path in PHASE_F.get('authorizedTestChanges', []) or path in PHASE_G.get('authorizedProductionChanges', []) or path in PHASE_G.get('authorizedTestChanges', []):
                continue
            self.assertEqual(expected, blob_hash(retained_bytes(ROOT / path)), path)

    def test_production_inventory_admits_exactly_one_unwired_orchestration_type(self):
        """No Cloud adapter, service port, restore coordinator or future milestone type can appear."""
        prior = {path for path in BASELINE['qualifiedRuntimeBlobs'] if path.endswith('.java')}
        actual = {str(path.relative_to(ROOT)) for path in (ROOT / 'app/src/main/java').rglob('*.java')}
        self.assertEqual(prior | {INSTALLER} | ({PHASE_F['adapterFile']} if PHASE_F else set()) | calendar_model_files(), actual)
        callers = []
        for path in actual:
            if re.search(r'\bPackageInstaller\b', code_only((ROOT / path).read_text())):
                callers.append(path)
        self.assertEqual(sorted([INSTALLER] + PHASE_F.get('authorizedLiveCallers', [])), sorted(callers))

    def test_prior_boundary_predicates_and_workflow_only_gain_exact_e_exceptions(self):
        """Reverse the small authorized additions and demand the earlier gate/workflow bytes exactly."""
        for relative, expected in BASELINE['frozenAccountingBoundaryBlobs'].items():
            source = retained_text(ROOT / relative)
            for patch in reversed(PHASE_G.get('boundaryPatches', {}).get(relative, [])):
                source = source.replace(patch['after'], patch['before'])
            if PHASE_F:
                for patch in reversed(PHASE_F['boundaryPatches'].get(relative, [])):
                    source = source.replace(patch['after'], patch['before'])
            previous = without_phase_e_accounting(relative, source)
            self.assertEqual(expected, blob_hash(previous.encode('utf-8')), relative)

    def test_modified_python_keeps_pedagogical_banners_and_function_docstrings(self):
        """All new/modified Python functions retain documentation and a module teaching banner after imports."""
        paths = [path for path in BASELINE['frozenAccountingBoundaryBlobs'] if path.endswith('.py')]
        paths.append('tests/test_m4_phase_e_boundary.py')
        for relative in paths:
            tree = ast.parse((ROOT / relative).read_text(encoding='utf-8'))
            last_import = max(index for index, node in enumerate(tree.body) if isinstance(node, (ast.Import, ast.ImportFrom)))
            self.assertTrue(any(isinstance(node, ast.Expr) and isinstance(node.value, ast.Constant)
                                and isinstance(node.value.value, str) for node in tree.body[last_import + 1:]), relative)
            for node in ast.walk(tree):
                if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                    self.assertTrue(ast.get_docstring(node), relative + ':' + node.name)


if __name__ == '__main__':
    unittest.main()
