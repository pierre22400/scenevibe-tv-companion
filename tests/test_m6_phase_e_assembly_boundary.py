import ast
import copy
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import m6_phase_e_assembly_provenance as provenance

"""Require exact current assembly scope and reconstruct every historical starting byte.

Unknown paths, edits, inventory retargeting and inverse drift are rejected. This gate
also checks the actual service composition; inherited tests retain their old assertions.
"""

ROOT = Path(__file__).resolve().parents[1]
HELPER_BLOB = '7913dabfea315b6ead346b11d4f77ebfd8359fd1'


class M6PhaseEAssemblyBoundaryTest(unittest.TestCase):
    """Check the additive assembly without weakening any older byte or behavioral gate."""

    def test_exact_inventory(self):
        """Reject any unlisted source, fixture, build, workflow or document path."""
        data = provenance.inventory()
        result = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard'], cwd=ROOT)
        actual = {path for path in result.decode().splitlines() if '__pycache__' not in Path(path).parts}
        self.assertEqual(set(data['startingBlobs']) | provenance.ADDED, actual)

    def test_all_original_bytes_and_historical_baselines(self):
        """Reconstruct every f72d696e blob, including immutable historical JSON baselines."""
        data = provenance.inventory()
        for relative, digest in data['startingBlobs'].items():
            self.assertEqual(digest, provenance.blob_hash(provenance.m6_assembly_retained_bytes(ROOT / relative)), relative)
        for relative in data['admissions']:
            self.assertFalse(relative.endswith('-baseline.json'), relative)
            self.assertNotIn('*', relative)

    def test_added_blobs(self):
        """Require actual complete new files and an independently pinned validation helper."""
        data = provenance.inventory()
        self.assertEqual(provenance.ADDED - {'.github/scripts/m6-phase-e-assembly-baseline.json'}, set(data['addedBlobs']))
        self.assertEqual(HELPER_BLOB, data['addedBlobs']['tests/m6_phase_e_assembly_provenance.py'])
        for relative, digest in data['addedBlobs'].items():
            self.assertEqual(digest, provenance.blob_hash((ROOT / relative).read_bytes()), relative)

    def test_unknown_bytes_and_baseline_retargeting(self):
        """A new baseline cannot bless another source blob or an inexact inverse."""
        data = provenance.inventory()
        with tempfile.TemporaryDirectory(prefix='m6e-assembly-') as directory:
            target = Path(directory) / 'inventory.json'
            for relative in data['admissions']:
                current = (ROOT / relative).read_bytes()
                with self.assertRaises(ValueError):
                    provenance.restore_blob(relative, current + b'unknown')
                changed = copy.deepcopy(data)
                changed['admissions'][relative]['afterSha'] = '0' * 40
                target.write_text(json.dumps(changed))
                with patch.object(provenance, 'INVENTORY', target), self.assertRaises(ValueError):
                    provenance.inventory()
                changed = copy.deepcopy(data)
                changed['admissions'][relative]['patches'][0]['before'] += 'drift'
                target.write_text(json.dumps(changed))
                with patch.object(provenance, 'INVENTORY', target), self.assertRaises(ValueError):
                    provenance.restore_blob(relative, current)

    def test_actual_named_service_composition(self):
        """The actual candidate uses one generic client and common ports after WALL construction."""
        service = (ROOT / 'app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayService.java').read_text()
        client = (ROOT / 'app/src/main/java/com/scenevibe/tvcompanionpoc/CloudControlClient.java').read_text()
        self.assertIn('BuildConfig.M6_QUALIFICATION?TransportMode.PACKAGE_V1:TransportMode.VIDEO_V1', client)
        self.assertIn('BuildConfig.M6_QUALIFICATION ? overlayRuntimePorts : videoRuntimePorts', service)
        self.assertLess(service.index('wallClockDriver = new WallClockDriver'), service.index('packageInstaller = new PackageInstaller'))
        self.assertEqual(1, service.count('cloudClient = new CloudControlClient'))
        self.assertIn('TvCapabilities.packageQualification()', service)

    def test_documented_python(self):
        """Every newly edited utility retains a teaching banner and function docstrings."""
        paths = set(provenance.inventory()['admissions']) | provenance.ADDED
        for relative in sorted(path for path in paths if path.endswith('.py')):
            tree = ast.parse((ROOT / relative).read_text())
            last_import = max(index for index, node in enumerate(tree.body) if isinstance(node, (ast.Import, ast.ImportFrom)))
            self.assertTrue(any(isinstance(node, ast.Expr) and isinstance(node.value, ast.Constant)
                                and isinstance(node.value.value, str) for node in tree.body[last_import + 1:]), relative)
            for node in ast.walk(tree):
                if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                    self.assertTrue(ast.get_docstring(node), relative + ':' + node.name)
