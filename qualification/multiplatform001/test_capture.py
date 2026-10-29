import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from analyze_capture import analyze

"""Synthetic collector tests use a fake local ADB executable, never a television or platform account."""


class CaptureTests(unittest.TestCase):
    """Prove full-file decoding, campaign separation, overlap handling and actual PowerShell argument routing."""

    def records(self):
        """Return deliberate scalar-only fixtures with owner annotations outside automatic samples."""
        return [
            {'platform': 'NETFLIX', 'event': 'PUBLIC_SAMPLE', 'sequence': 1, 'campaignStartedMs': 10, 'elapsedMs': 1000, 'sample': {'session': 1, 'marker': 'AD', 'markerType': 'LONG', 'state': 'PLAYING'}},
            {'platform': 'NETFLIX', 'event': 'OWNER_ANNOTATION', 'sequence': 2, 'campaignStartedMs': 10, 'elapsedMs': 1100, 'annotation': 'AD_VISIBLE'},
            {'platform': 'DISNEY_PLUS', 'event': 'PUBLIC_SAMPLE', 'sequence': 1, 'campaignStartedMs': 20, 'elapsedMs': 2000, 'sample': {'session': 1, 'marker': 'ABSENT', 'markerType': 'ABSENT', 'state': 'PAUSED'}}
        ]

    def test_complete_encodings_and_overlap(self):
        """UTF-16 and UTF-8 sources remain hashable and duplicates cannot become a second campaign."""
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            first, second = base / 'first.log', base / 'second.log'
            text = '\n'.join(json.dumps(row) for row in self.records())
            first.write_text(text, encoding='utf-16')
            second.write_text(text, encoding='utf-8-sig')
            result = analyze([first, second])
            self.assertEqual(result['platforms']['NETFLIX']['events'], 2)
            self.assertEqual(result['platforms']['NETFLIX']['duplicates'], 2)
            self.assertEqual(result['platforms']['NETFLIX']['markers']['AD'], 1)
            self.assertEqual(len(result['platforms']['NETFLIX']['ownerAnnotations']), 1)
            self.assertEqual(result['platforms']['DISNEY_PLUS']['markers']['ABSENT'], 1)
            self.assertEqual(result['sources'][0]['sha256'], hashlib.sha256(first.read_bytes()).hexdigest())
            self.assertEqual(result['platforms']['NETFLIX']['conclusion'], 'INSUFFICIENT_EVIDENCE')

    def test_powershell_capture_with_owned_fake_adb(self):
        """Exercise the shipped PowerShell script with paths containing spaces, filtered JSONL and preserved old files."""
        shell = os.environ.get('SCENEVIBE_TEST_POWERSHELL', 'pwsh')
        self.assertIsNotNone(shutil.which(shell), 'CI must provide PowerShell; do not silently skip this test')
        script = Path(__file__).resolve().parents[2] / 'scripts/Collect-SceneVibeMultiPlatform.ps1'
        with tempfile.TemporaryDirectory(prefix='scenevibe owned capture ') as directory:
            base = Path(directory)
            if os.name == 'nt':
                adb = base / 'owned adb.exe'
                source = base / 'owned-adb.cs'
                expected = ['-s', 'OWN_SIMULATED_SERIAL', 'logcat', '-v', 'raw', '-s', 'SceneVibeMultiAd:I', '*:S']
                code = 'using System; public class OwnedAdb { public static int Main(string[] args) { string[] expected = new string[] {' + ','.join(json.dumps(arg) for arg in expected) + '}; if(args.Length != expected.Length) return 3; for(int i=0;i<args.Length;i++) if(args[i]!=expected[i]) return 4; ' + ''.join('Console.WriteLine(' + json.dumps(json.dumps(row)) + ');' for row in self.records()) + 'return 0; } }'
                source.write_text(code, encoding='utf-8')
                compiler = base / 'compile-owned-adb.ps1'
                compiler.write_text('param([string]$SourcePath,[string]$OutputPath)\n$ErrorActionPreference="Stop"\nAdd-Type -TypeDefinition ([IO.File]::ReadAllText($SourcePath)) -OutputAssembly $OutputPath -OutputType ConsoleApplication\n', encoding='utf-8')
                result = subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(compiler), '-SourcePath', str(source), '-OutputPath', str(adb)], capture_output=True, text=True, timeout=60)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            else:
                adb = base / 'owned adb'
                adb.write_text('#!/usr/bin/env python3\nimport sys\nassert sys.argv[1:] == ["-s","OWN_SIMULATED_SERIAL","logcat","-v","raw","-s","SceneVibeMultiAd:I","*:S"]\n' +
                               '\n'.join('print(' + repr(json.dumps(row)) + ')' for row in self.records()) + '\n', encoding='utf-8')
                adb.chmod(0o700)
            output = base / 'capture output'
            for platform in ['NETFLIX', 'DISNEY_PLUS', 'NETFLIX']:
                before = {p: p.read_bytes() for p in output.glob('*')} if output.exists() else {}
                result = subprocess.run([shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script), '-Platform', platform, '-DeviceSerial', 'OWN_SIMULATED_SERIAL', '-AdbPath', str(adb), '-OutputDirectory', str(output)], capture_output=True, text=True, timeout=30)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                for path, data in before.items():
                    self.assertEqual(path.read_bytes(), data, 'Previous capture overwritten')
                new_json = [p for p in output.glob('*.jsonl') if p not in before]
                self.assertEqual(len(new_json), 1)
                rows = [json.loads(line) for line in new_json[0].read_text(encoding='utf-8').splitlines()]
                self.assertTrue(rows)
                self.assertEqual({row['platform'] for row in rows}, {platform})
                self.assertFalse(new_json[0].read_bytes().startswith(b'\xef\xbb\xbf'))


if __name__ == '__main__':
    unittest.main()
