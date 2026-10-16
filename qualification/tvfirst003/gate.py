"""Authorize the exact TV-FIRST 002 delivery plus the reviewed CI-only 003 overlay."""

from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys

# This gate admits content, not an arbitrary HEAD or a directory-wide exception.
# The seal hashes every CI overlay file, including this verifier with only its own
# seal digest normalized. The external delivery report records the unnormalized
# hashes as the review trust anchor. There is deliberately no reseal command here.
BASE = '17cbe36ae99ac7f48aaf861e0d1feac702a9e521'
DELIVERY_MANIFEST_SHA256 = '5035f6f5b2ed4ef6a7373e555d069b089039fdb78d5d6bd9a3bcf7c7258e53ee'
SEAL_SHA256 = 'c251162d570894ff63ed19d7b543bb739e3e7ff794843811ea1fd39ddf83d4f9'
PREFIX = 'qualification/tvfirst003/'
CI_EXCEPTION = '.github/workflows/android-debug.yml'
JAVA = 'app/src/main/java/com/scenevibe/tvcompanionpoc/'
APP_EDITS = {'app/build.gradle', JAVA + 'MainActivity.java'}
ROOT = Path(__file__).resolve().parents[2]


class GateError(Exception):
    """Report a specific authorization failure without hiding a failed historical check."""


def require(condition, message):
    """Fail even under python -O; authorization must not depend on assert statements."""
    if not condition:
        raise GateError(message)


def git(root, *args):
    """Read local Git objects; never fetch, commit, change the index or contact a remote."""
    return subprocess.check_output(['git', '-C', str(root), *args], stderr=subprocess.PIPE)


def sha(data):
    """Hash exact bytes rather than text with normalized newlines."""
    return hashlib.sha256(data).hexdigest()


def normalized_gate(data):
    """Break only the seal's self-reference, retaining every other verifier byte."""
    pattern = rb"(?m)^SEAL_SHA256 = '[0-9a-f]{64}'$"
    normalized, count = re.subn(pattern, b"SEAL_SHA256 = '" + b'0' * 64 + b"'", data)
    require(count == 1, 'Verifier seal declaration is not unique')
    return normalized


def source_files(root):
    """Inventory all source files, including untracked/ignored surprises, without build outputs."""
    excluded = {'.git', '.gradle', 'build', 'app/build'}
    found = set()
    for directory, dirs, files in os.walk(root, followlinks=False):
        relative = Path(directory).relative_to(root)
        for name in list(dirs):
            path = (relative / name).as_posix()
            if path in excluded:
                dirs.remove(name)
            else:
                require(not (Path(directory) / name).is_symlink(), 'Symlink directory: ' + path)
        for name in files:
            path = (relative / name).as_posix()
            if path != 'local.properties':
                require(not (root / path).is_symlink(), 'Symlink file: ' + path)
                found.add(path)
    return found


def method(source, signature):
    """Extract a balanced legacy Java method for exact body and teaching-comment retention."""
    start = source.index(signature)
    opening = source.index('{', start)
    depth = 1
    for end in range(opening + 1, len(source)):
        depth += (source[end] == '{') - (source[end] == '}')
        if depth == 0:
            return source[start:end + 1]
    raise GateError('Unbalanced Java method: ' + signature)


def verify(root=ROOT, expected_head=None):
    """Admit only the immutable base, all 60 accepted files and the exact sealed CI overlay."""
    require(sys.flags.optimize == 0, 'Run qualification without python -O')
    root = root.resolve()
    head = git(root, 'rev-parse', 'HEAD').decode().strip()
    require(re.fullmatch(r'[0-9a-f]{40}', head), 'Invalid HEAD identity')
    if expected_head:
        require(head == expected_head, 'Checkout HEAD differs from authorized event HEAD')
    require(git(root, 'rev-parse', BASE + '^{commit}').decode().strip() == BASE, 'Immutable BASE missing')
    require(subprocess.run(['git', '-C', str(root), 'merge-base', '--is-ancestor', BASE, head],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0,
            'BASE is not an ancestor of HEAD')
    base = {}
    for entry in git(root, 'ls-tree', '-rz', BASE).split(b'\0'):
        if entry:
            info, path = entry.split(b'\t')
            mode, kind, blob = info.decode().split()
            require(mode == '100644' and kind == 'blob', 'Unexpected BASE file mode')
            base[path.decode()] = git(root, 'cat-file', 'blob', blob)
    require(len(base) == 276, 'Historical BASE inventory is not 276')
    manifest_bytes = (root / PREFIX / 'delivery002-files.json').read_bytes()
    require(sha(manifest_bytes) == DELIVERY_MANIFEST_SHA256, 'Accepted 002 manifest changed')
    delivery = json.loads(manifest_bytes)
    entries = delivery['files']
    require(delivery['base'] == BASE and len(entries) == 60, 'Invalid 002 scope')
    accepted = {f['path']: f for f in entries}
    require(len(accepted) == 60, 'Duplicate delivery path')
    require({f['path'] for f in entries if f['status'] == 'modified'} == APP_EDITS,
            'Unexpected 002 modified paths')
    require(len(set(accepted) - set(base)) == 58, 'Expected 58 accepted additions')
    for path, entry in accepted.items():
        data = (root / path).read_bytes()
        require(len(data) == entry['bytes'] and sha(data) == entry['sha256'],
                'Accepted 002 file changed: ' + path)
    seal_bytes = (root / PREFIX / 'seal.json').read_bytes()
    require(sha(seal_bytes) == SEAL_SHA256, 'CI authorization seal changed')
    seal = json.loads(seal_bytes)
    require(seal['base'] == BASE and seal['ciModifiedBaseFiles'] == [CI_EXCEPTION], 'Invalid CI exceptions')
    ci = seal['files']
    require(set(ci) & set(base) == {CI_EXCEPTION}, 'CI overlay modifies a forbidden historical path')
    require(not set(ci) & set(accepted), 'CI overlay replaces an accepted 002 file')
    require(set(ci) - {CI_EXCEPTION, '.github/workflows/android-tvfirst003.yml'} <=
            {p for p in ci if p.startswith(PREFIX)}, 'CI overlay escaped qualification scope')
    for path, digest in ci.items():
        data = (root / path).read_bytes()
        if path == PREFIX + 'gate.py':
            data = normalized_gate(data)
        require(sha(data) == digest, 'Authorized CI file changed: ' + path)
    protected = set(base) - APP_EDITS
    for path in protected:
        if path != CI_EXCEPTION:
            require((root / path).read_bytes() == base[path], 'Protected historical file changed: ' + path)
    expected = set(base) | set(accepted) | set(ci) | {PREFIX + 'seal.json'}
    actual = source_files(root)
    require(actual == expected, 'Source inventory mismatch: added=' + repr(sorted(actual - expected)) +
            ' missing=' + repr(sorted(expected - actual)))
    head_entries = {}
    for entry in git(root, 'ls-tree', '-rz', head).split(b'\0'):
        if entry:
            info, path = entry.split(b'\t')
            mode, kind, blob = info.decode().split()
            require(mode == '100644' and kind == 'blob', 'Unauthorized HEAD file mode: ' + path.decode())
            head_entries[path.decode()] = blob
    require(set(head_entries) == expected, 'HEAD tree does not contain exactly the authorized files')
    for path, blob in head_entries.items():
        require((root / path).read_bytes() == git(root, 'cat-file', 'blob', blob),
                'Working source differs from committed HEAD: ' + path)
        require(not (root / path).stat().st_mode & 0o111, 'Unauthorized executable source: ' + path)
    require(not git(root, 'diff', '--cached', '--name-only'), 'Index differs from committed HEAD')
    original = base[JAVA + 'MainActivity.java'].decode()
    current = (root / JAVA / 'MainActivity.java').read_text()
    signatures = ['private void startPairing()', 'private void resetPairing()', 'private void refreshPairing()',
                  'private void refreshCloud()', 'static String cloudStatus(', 'private void connectCloud()',
                  'private void disconnectCloud()', 'private void openDiagnostics()', 'private String lanIpv4()',
                  'private void toggleAutostart()', 'private String autostartLabel()', 'private Button addButton(',
                  'private void openPermissionSettings()', 'private void openMediaSessionAccessSettings()',
                  'private boolean startOverlay(', 'private void stopOverlay()', 'private int dp(']
    for signature in signatures:
        require(method(original, signature) == method(current, signature), 'Legacy method changed: ' + signature)
    comments = re.findall(r'/\*\*.*?\*/', original, re.S)
    require(all(comment in current for comment in comments), 'Existing MainActivity docstring lost')
    require(original[original.index('        LinearLayout controls'):original.index('        ScrollView scroll')] ==
            current[current.index('        LinearLayout controls = new LinearLayout'):current.index('        return controls;')],
            'Legacy controls changed')
    return {'verdict': 'PASS', 'head': head, 'tree': git(root, 'rev-parse', 'HEAD^{tree}').decode().strip(),
            'base': BASE, 'deliveryFilesExact': 60, 'protectedComparedToBase': len(protected),
            'protectedByteIdentical': len(protected) - 1, 'explicitSealedCiException': [CI_EXCEPTION],
            'historicalMethodsExact': len(signatures), 'existingDocstringsRetained': len(comments),
            'authorizedSourceFiles': len(expected), 'sealSha256': SEAL_SHA256}


def main():
    """Print a machine-readable gate result and return nonzero for every rejected state."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--expected-head')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    try:
        result = verify(args.root, args.expected_head)
    except (GateError, OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        print(json.dumps({'verdict': 'FAIL', 'reason': str(error)}, ensure_ascii=False))
        return 1
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
