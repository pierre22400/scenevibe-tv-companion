import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

"""Permit only the Phase C persistence extraction while keeping qualified behavior pinned.

Pure snapshot/store/codec compile without Android or Video classes. Only the Android
backend may use Context/SharedPreferences. The old repository's entire behavioral tail
and injectable seam remain byte-exact; current owners cannot route through handlers.
The Phase B exception is balanced by exact Phase C scope and retained-source checks.
Phase D preserves this store byte-for-byte and adds narrowly authorized handler-owned
semantics; frozen historical reference and orchestration fragments guard that exception.
"""

ROOT = Path(__file__).resolve().parents[1]
BASELINE = json.loads((ROOT / '.github/scripts/m4-phase-c-baseline.json').read_text(encoding='utf-8'))
PHASE_B = json.loads((ROOT / '.github/scripts/m4-phase-b-baseline.json').read_text(encoding='utf-8'))
PHASE_D_PATH = ROOT / '.github/scripts/m4-phase-d-baseline.json'
PHASE_D = json.loads(PHASE_D_PATH.read_text(encoding='utf-8')) if PHASE_D_PATH.exists() else {}


def code_only(source):
    """Check executable references rather than interpreting explanatory comments as dependencies."""
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.S)


def blob_hash(content):
    """Reproduce the authoritative starting GitHub blob identity without relying on the index."""
    return hashlib.sha1(b'blob ' + str(len(content)).encode('ascii') + b'\0' + content).hexdigest()


class M4PhaseCBoundaryTest(unittest.TestCase):
    """Prove generic persistence boundaries, protected behavior and no Phase D cutover."""

    def test_store_snapshot_and_codec_compile_with_jdk_only(self):
        """A real empty-classpath compile cannot accidentally link any Android or product parser."""
        javac, java = shutil.which('javac'), shutil.which('java')
        self.assertTrue(javac or java, 'JDK compiler required for store isolation gate')
        compiler = [javac] if javac else [java, '-m', 'jdk.compiler/com.sun.tools.javac.Main']
        with tempfile.TemporaryDirectory(prefix='scenevibe-m4c-') as directory:
            work = Path(directory)
            empty, output = work / 'empty', work / 'classes'
            empty.mkdir()
            output.mkdir()
            paths = PHASE_B['genericModelFiles'] + BASELINE['pureStoreFiles']
            result = subprocess.run(compiler + ['-encoding', 'UTF-8', '-classpath', str(empty),
                                               '-sourcepath', str(empty), '-d', str(output)]
                                    + [str(ROOT / path) for path in paths],
                                    capture_output=True, timeout=30, check=False)
            self.assertEqual(0, result.returncode, 'JDK-only generic durable store compile failed')
            self.assertTrue((output / 'com/scenevibe/tvcompanionpoc/installation/InstallationStore.class').is_file())

    def test_store_and_backend_have_no_runtime_product_or_network_dependencies(self):
        """Allow Android persistence in its one backend, and forbid scheduler/renderer/parsing/discovery everywhere."""
        forbidden = r'\b(MediaSyncedTrackScheduler|SceneRuntimeController|SceneRenderer|OverlayRenderer|OverlayService|'
        forbidden += r'CloudControlClient|CloudProtocol|VideoOverlayManifestBridge|TrackParser|OverlayManifestParser|'
        forbidden += r'FinalTrack|Prime|Pass0|Pass1|Pass2|Banner|Language|PackageInstaller|Thread)\b'
        for path in BASELINE['genericStoreFiles']:
            source = code_only((ROOT / path).read_text(encoding='utf-8'))
            self.assertIsNone(re.search(forbidden, source), path)
            for token in ('java.net.', 'java.io.', 'java.nio.file.', 'org.json.', 'java.lang.reflect.',
                          'Class.forName', 'ServiceLoader', 'DexClassLoader', 'WebView', 'HttpURLConnection'):
                self.assertNotIn(token, source, path)
            imports = re.findall(r'\bimport\s+([\w.]+)\s*;', source)
            for imported in imports:
                allowed = imported.startswith('java.util.') or imported == 'java.nio.charset.StandardCharsets'
                if path.endswith('/AndroidInstallationBackend.java'):
                    allowed = allowed or imported in ('android.content.Context', 'android.content.SharedPreferences',
                                                       'java.lang.ref.WeakReference')
                self.assertTrue(allowed, path)

    def test_repository_behavior_and_injectable_seam_remain_byte_exact(self):
        """Only the Android constructor/imports/banner may change; all Video/ACK/restore methods stay frozen."""
        path = ROOT / BASELINE['authorizedPersistenceExtraction']
        source = path.read_text(encoding='utf-8')
        tail = source[source.index('    /** Injectable persistence boundary'):]
        start = source.index('final class CloudTrackRepository')
        end = source.index('    /** Wraps SharedPreferences.commit')
        if PHASE_D:
            reference = (ROOT / PHASE_D['historicalReferenceFile']).read_text(encoding='utf-8')
            reference = code_only(reference.replace('M4PhaseDHistoricalRepository', 'CloudTrackRepository'))
            reference = '\n'.join(line for line in reference.splitlines() if line.strip())
            self.assertEqual(PHASE_D['historicalRepositoryCodeSha256'], hashlib.sha256(reference.encode()).hexdigest())
            for anchor, frozen in PHASE_D['repositoryFrozenFragments'].items():
                fragment = source[source.index(anchor):source.index(frozen['end'])]
                self.assertEqual(frozen['sha256'], hashlib.sha256(fragment.encode()).hexdigest(), anchor)
        else:
            self.assertEqual(BASELINE['repositoryCoreSha256'], hashlib.sha256(tail.encode()).hexdigest())
        self.assertEqual(BASELINE['repositorySeamSha256'], hashlib.sha256(source[start:end].encode()).hexdigest())
        constructor = code_only(source[end:source.index('    /** Injectable persistence boundary')])
        self.assertNotIn('SharedPreferences', code_only(source))
        for forbidden in ('getSharedPreferences', '.edit(', 'store.read(', 'store.commit(', 'InstallationHandler',
                          'PackageInstaller', 'InstallRequest', 'PreparedInstallation'):
            self.assertNotIn(forbidden, constructor)
        self.assertIn('new AndroidInstallationBackend(context)', constructor)

    def test_production_inventory_and_handlers_remain_in_exact_phase_c_scope(self):
        """Keep the only permitted new definitions and prohibit production handler implementations/callers."""
        old = {path for path in BASELINE['qualifiedRuntimeBlobs'] if path.endswith('.java')}
        old.add(BASELINE['authorizedPersistenceExtraction'])
        expected = old | set(BASELINE['genericStoreFiles'])
        expected.update(PHASE_D.get('videoHandlerFiles', []))
        actual = {str(path.relative_to(ROOT)) for path in (ROOT / 'app/src/main/java').rglob('*.java')}
        self.assertEqual(expected, actual)
        for path in actual:
            source = code_only((ROOT / path).read_text(encoding='utf-8'))
            if path not in PHASE_D.get('videoHandlerFiles', []):
                self.assertIsNone(re.search(r'\bimplements\s+InstallationHandler\b', source), path)
            self.assertNotIn('PackageInstaller', source, path)
        for path, digest in BASELINE['qualifiedRuntimeBlobs'].items():
            if path in PHASE_D.get('additiveContractFiles', []):
                continue
            self.assertEqual(digest, blob_hash((ROOT / path).read_bytes()), path)

    def test_every_retained_jvm_test_and_frozen_inventory_remains_byte_exact(self):
        """Do not weaken old characterization/model tests to make the persistence refactor pass."""
        for path, digest in BASELINE['frozenTestSources'].items():
            self.assertEqual(digest, blob_hash((ROOT / path).read_bytes()), path)


if __name__ == '__main__':
    unittest.main()
