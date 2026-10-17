from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys

"""Authorize the finite 006 delta over the immutable, previously accepted 003 integration."""

BASE = 'a3cf497dc8bba6a2d1980476d7c24cbbe1aa153a'
BASE_TREE = 'f51796913d549e660133243a94cb62d19bffb2ce'
MANIFEST_SHA256 = 'cbab2d5c14200baf103ec161649febf1741c049bf92ed4f2b6eec8537f9a067c'
ROOT = Path(__file__).resolve().parents[2]
PREFIX = 'qualification/tvfirst006/'
JAVA = 'app/src/main/java/com/scenevibe/tvcompanionpoc/'
MODIFIED = {'app/build.gradle', '.github/workflows/android-debug.yml',
            JAVA + 'OverlayService.java', JAVA + 'MediaSessionProbe.java', JAVA + 'DiagnosticsActivity.java'}
ADDED = {PREFIX + name for name in ['gate.py', 'run.py', 'test_gate.py', 'manifest.json', 'README.md']} | {
    '.github/workflows/android-tvfirst006.yml', 'docs/tv-first-006-observation.md',
    *[JAVA + 'tvfirst/' + name + '.java' for name in ['MediaEvidence', 'MediaEvidencePolicy',
                                                  'ObservationDiagnostics', 'PassiveMediaReader']],
    *['app/src/test/java/com/scenevibe/tvcompanionpoc/tvfirst/' + name + '.java'
      for name in ['MediaEvidenceTest', 'ObservationDiagnosticsTest', 'PassiveMediaReaderTest']],
    'app/src/testDebug/java/com/scenevibe/tvcompanionpoc/TvFirstDefaultServiceTest.java',
    'app/src/testObservation/java/com/scenevibe/tvcompanionpoc/TvFirstServiceObservationTest.java',
}


class GateError(Exception):
    """Describe authorization failure before a retained view or a build can run."""


def require(condition, message):
    """Keep security checks active independently of Python assertion optimization."""
    if not condition:
        raise GateError(message)


def git(root, *args):
    """Read local objects only; never fetch, commit or contact a remote."""
    return subprocess.check_output(['git', '-C', str(root), *args], stderr=subprocess.PIPE)


def sha(data):
    """Hash exact bytes without newline normalization."""
    return hashlib.sha256(data).hexdigest()


def normalized(path, data):
    """Break only this gate's manifest self-reference; all other verifier bytes remain sealed."""
    if path != PREFIX + 'gate.py':
        return data
    result, count = re.subn(rb"(?m)^MANIFEST_SHA256 = '[0-9a-f]{64}'$",
                            b"MANIFEST_SHA256 = '" + b'0' * 64 + b"'", data)
    require(count == 1, 'Invalid manifest declaration')
    return result


def inventory(root):
    """Reject source surprises, symlinks and modes while excluding only named tool outputs."""
    found = set()
    for directory, dirs, files in os.walk(root, followlinks=False):
        relative = Path(directory).relative_to(root)
        for name in list(dirs):
            path = (relative / name).as_posix()
            if path in {'.git', '.gradle', 'build', 'app/build'}:
                dirs.remove(name)
            else:
                require(not (Path(directory) / name).is_symlink(), 'Symlink directory: ' + path)
        for name in files:
            path = (relative / name).as_posix()
            if path in {'.git', 'local.properties'}:
                continue
            require(not (root / path).is_symlink(), 'Symlink file: ' + path)
            require(not (root / path).stat().st_mode & 0o111, 'Executable source: ' + path)
            found.add(path)
    return found


def tree(root, revision):
    """Read a finite committed tree with ordinary source-file modes only."""
    entries = {}
    for entry in git(root, 'ls-tree', '-rz', revision).split(b'\0'):
        if entry:
            info, path = entry.split(b'\t')
            mode, kind, blob = info.decode().split()
            require(mode == '100644' and kind == 'blob', 'Unexpected Git mode: ' + path.decode())
            entries[path.decode()] = blob
    return entries


def verify(root=ROOT, expected_head=None):
    """Admit exactly the reviewed delta and all retained bytes, never a permissive path prefix."""
    require(sys.flags.optimize == 0, 'Run without python -O')
    root = Path(root).resolve()
    head = git(root, 'rev-parse', 'HEAD').decode().strip()
    if expected_head:
        require(head == expected_head, 'Checkout HEAD differs from event HEAD')
    require(git(root, 'rev-parse', BASE + '^{tree}').decode().strip() == BASE_TREE, 'Wrong immutable BASE tree')
    require(subprocess.run(['git', '-C', str(root), 'merge-base', '--is-ancestor', BASE, head],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0,
            'BASE is not an ancestor')
    base = tree(root, BASE)
    require(len(base) == 341, 'Wrong 003 BASE inventory')
    raw = (root / PREFIX / 'manifest.json').read_bytes()
    require(sha(raw) == MANIFEST_SHA256, '006 manifest changed')
    manifest = json.loads(raw)
    require(manifest['base'] == BASE and manifest['baseTree'] == BASE_TREE, 'Manifest BASE mismatch')
    files = manifest['files']
    require(set(files) == MODIFIED | (ADDED - {PREFIX + 'manifest.json'}), 'Delta scope changed')
    require(MODIFIED <= set(base) and not ADDED & set(base), 'Invalid delta classification')
    expected = set(base) | ADDED
    require(inventory(root) == expected, 'Source inventory mismatch')
    for path, blob in base.items():
        original = git(root, 'cat-file', 'blob', blob)
        current = (root / path).read_bytes()
        if path not in MODIFIED:
            require(current == original, 'Protected historical file changed: ' + path)
        elif path.endswith('.java'):
            require(all(comment in current for comment in re.findall(rb'/\*\*.*?\*/', original, re.S)),
                    'Existing docstring removed: ' + path)
    for path, entry in files.items():
        require(entry['status'] == ('modified' if path in MODIFIED else 'added'), 'Status mismatch: ' + path)
        require(entry['baseBlob'] == base.get(path), 'Original blob mismatch: ' + path)
        data = (root / path).read_bytes()
        require(sha(normalized(path, data)) == entry['sha256'], 'Authorized delta changed: ' + path)
    committed = tree(root, head)
    require(set(committed) == expected, 'HEAD inventory differs from authorized source')
    for path, blob in committed.items():
        require((root / path).read_bytes() == git(root, 'cat-file', 'blob', blob),
                'Working source differs from HEAD: ' + path)
    require(not git(root, 'diff', '--cached', '--name-only'), 'Index differs from HEAD')
    return {'verdict': 'PASS', 'head': head, 'tree': git(root, 'rev-parse', 'HEAD^{tree}').decode().strip(),
            'base': BASE, 'baseTree': BASE_TREE, 'retainedFiles': len(base) - len(MODIFIED),
            'modifiedFiles': len(MODIFIED), 'addedFiles': len(ADDED), 'sourceFiles': len(expected),
            'manifestSha256': MANIFEST_SHA256, 'oldSealsModified': False}


def main():
    """Return a bounded failure instead of falling back to another qualification profile."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--expected-head')
    args = parser.parse_args()
    try:
        result = verify(args.root, args.expected_head)
    except (GateError, OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        print(json.dumps({'verdict': 'FAIL', 'reason': str(error)}))
        return 1
    print(json.dumps(result, indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
