import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

"""Pin Phase B to a JDK-only additive island and preserve the qualified runtime.

Compilation uses an empty classpath/sourcepath, so Android, JSON, Cloud and Video
classes cannot become accidental dependencies. Git blob hashes come from the actual
starting GitHub tree; they protect existing callers, wire, persistence, identity,
permissions, signature configuration and every existing characterization test.
Phase C permits only its explicit store files and the constructor-only persistence
extraction, balanced by the additional Phase C byte/scope boundary tests.
Phase D adds only its explicit Video-side files and typed prepared-state contract;
the Phase D differential and scope gates protect the authorized semantic extraction.
"""

ROOT = Path(__file__).resolve().parents[1]
BASELINE = json.loads((ROOT / '.github/scripts/m4-phase-b-baseline.json').read_text(encoding='utf-8'))
PHASE_C_PATH = ROOT / '.github/scripts/m4-phase-c-baseline.json'
PHASE_C = json.loads(PHASE_C_PATH.read_text(encoding='utf-8')) if PHASE_C_PATH.exists() else {}
PHASE_D_PATH = ROOT / '.github/scripts/m4-phase-d-baseline.json'
PHASE_D = json.loads(PHASE_D_PATH.read_text(encoding='utf-8')) if PHASE_D_PATH.exists() else {}


def code_only(source):
    """Remove comments for dependency checks without interpreting documentation as a caller."""
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.S)


def git_blob_digest(content):
    """Match Git's byte-exact blob identity, independently of the local Git index."""
    return hashlib.sha1(b'blob ' + str(len(content)).encode('ascii') + b'\0' + content).hexdigest()


class M4PhaseBBoundaryTest(unittest.TestCase):
    """Verify architectural constraints and absence of Phase C production wiring."""

    def test_generic_models_compile_without_android_cloud_or_video_classpath(self):
        """A real isolated compile must succeed using only Java's standard library."""
        javac = shutil.which('javac')
        java = shutil.which('java')
        self.assertTrue(javac or java, 'JDK compiler is required for the import-boundary gate')
        compiler = [javac] if javac else [java, '-m', 'jdk.compiler/com.sun.tools.javac.Main']
        with tempfile.TemporaryDirectory(prefix='scenevibe-m4b-') as temporary:
            work = Path(temporary)
            empty = work / 'empty'
            output = work / 'classes'
            empty.mkdir()
            output.mkdir()
            result = subprocess.run(
                compiler + ['-encoding', 'UTF-8', '-classpath', str(empty), '-sourcepath', str(empty),
                            '-d', str(output)] + [str(ROOT / path) for path in BASELINE['genericModelFiles']],
                capture_output=True, timeout=30, check=False,
            )
            self.assertEqual(0, result.returncode, 'JDK-only installation-model compilation failed')
            self.assertTrue((output / 'com/scenevibe/tvcompanionpoc/installation/InstallRequest.class').is_file())

    def test_generic_sources_exclude_domain_network_and_dynamic_execution(self):
        """Reject platform/business imports and hidden side-effect APIs, including qualified references."""
        forbidden = (
            'android.', 'org.json.', 'java.io.', 'java.net.', 'java.nio.file.',
            'java.lang.reflect.', 'java.util.concurrent.', 'Class.forName', 'ServiceLoader',
            'DexClassLoader', 'PathClassLoader', 'System.', 'Runtime.getRuntime',
        )
        domain = r'\b(FinalTrack|Video|Prime|Pipeline|LLM|CloudControlClient|CloudProtocol|CloudTrackRepository|'
        domain += r'CloudDeviceCredentials|OverlayService|OverlayRenderer|SceneRenderer|SceneRuntimeController|'
        domain += r'MediaSyncedTrackScheduler|DiagnosticsStore|Thread|Banner|Language|PackageInstaller|InstallationStore)\b'
        for path in BASELINE['genericModelFiles']:
            source = code_only((ROOT / path).read_text(encoding='utf-8'))
            imports = re.findall(r'\bimport\s+([\w.]+)\s*;', source)
            for imported in imports:
                self.assertTrue(imported.startswith('java.util.') or imported == 'java.nio.charset.StandardCharsets', path)
            for token in forbidden:
                self.assertNotIn(token, source, path)
            self.assertIsNone(re.search(domain, source), path)

    def test_qualified_runtime_wire_and_phase_a_sources_remain_byte_exact(self):
        """Protect production behavior and all retained tests using the starting GitHub blob hashes."""
        frozen = {**BASELINE['qualifiedRuntimeBlobs'], **BASELINE['frozenPhaseASourceBlobs']}
        for path, expected in frozen.items():
            # The authorized constructor-only extraction is guarded separately by Phase C.
            if path == PHASE_C.get('authorizedPersistenceExtraction'):
                continue
            self.assertEqual(expected, git_blob_digest((ROOT / path).read_bytes()), path)

    def test_no_current_caller_or_future_phase_production_component_is_added(self):
        """Allow only the eight generic definitions; every old caller must remain unwired."""
        production = ROOT / 'app/src/main/java'
        old_files = {path for path in BASELINE['qualifiedRuntimeBlobs'] if path.endswith('.java')}
        new_files = set(BASELINE['genericModelFiles'])
        new_files.update(PHASE_C.get('genericStoreFiles', []))
        new_files.update(PHASE_D.get('videoHandlerFiles', []))
        actual = {str(path.relative_to(ROOT)) for path in production.rglob('*.java')}
        self.assertEqual(old_files | new_files, actual, 'Unexpected production component outside Phase B')
        names = '|'.join(Path(path).stem for path in new_files)
        for path in old_files:
            # Only the persistence facade may import the explicitly permitted store/backend.
            if path == PHASE_C.get('authorizedPersistenceExtraction'):
                continue
            source = code_only((ROOT / path).read_text(encoding='utf-8'))
            self.assertNotIn('com.scenevibe.tvcompanionpoc.installation', source, path)
            self.assertIsNone(re.search(r'\b(' + names + r')\b', source), path)


if __name__ == '__main__':
    unittest.main()
