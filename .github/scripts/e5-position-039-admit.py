import difflib
import hashlib
import json
from pathlib import Path
import subprocess

"""Update exact finite provenance for reviewed E5-039 edits, without weakening old assertions.

Both full Git blob hashes and unique literal inverses are retained. The old M5 reconstruction
and the E5-038 reconstruction remain separately checkable; current runtime tests use real bytes.
"""

ROOT = Path(__file__).resolve().parents[2]
BASE = 'efd3a7983a03458ec1f919d760c10f1b1f9c57ff'
OLD = '.github/scripts/e5-native-035-provenance.json'
NEW = '.github/scripts/e5-position-039-provenance.json'


def blob(data):
    """Compute a whole Git blob identity, including every comment and docstring byte."""
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()


def original(name):
    """Read the immutable E5-038 source rather than any mutable tracking branch."""
    return subprocess.check_output(['git', 'show', BASE + ':' + name], cwd=ROOT)


def inverses(before, after):
    """Derive uniquely matched finite literal hunks and verify their complete round trip."""
    left, right = before.decode().splitlines(keepends=True), after.decode().splitlines(keepends=True)
    patches = []
    for group in difflib.SequenceMatcher(None, left, right, autojunk=False).get_grouped_opcodes(4):
        old = ''.join(left[group[0][1]:group[-1][2]])
        new = ''.join(right[group[0][3]:group[-1][4]])
        if after.decode().count(new) != 1:
            patches = [{'before': before.decode(), 'after': after.decode()}]
            break
        patches.append({'before': old, 'after': new})
    check = after.decode()
    for patch in reversed(patches):
        if check.count(patch['after']) != 1:
            raise ValueError('Ambiguous inverse')
        check = check.replace(patch['after'], patch['before'], 1)
    if check.encode() != before:
        raise ValueError('Inverse did not recover immutable bytes')
    return patches


def main():
    """Admit only the enumerated, hashed current paths and their verified original bytes."""
    data = json.loads(original(OLD))
    tracked = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', BASE], cwd=ROOT, text=True).splitlines()
    corrective = {'baseHead': BASE, 'changes': {}}
    for name in tracked:
        path = ROOT / name
        if name == OLD or not path.is_file():
            continue
        before, after = original(name), path.read_bytes()
        if before == after:
            continue
        patch = {'beforeSha': blob(before), 'afterSha': blob(after), 'inverse': inverses(before, after)}
        corrective['changes'][name] = patch
        if name in data['changes']:
            data['changes'][name]['afterSha'] = blob(after)
            data['changes'][name]['inverse'] += patch['inverse']
        elif name in data['additions']:
            data['additions'][name] = blob(after)
        elif name in data['startingBlobs']:
            data['changes'][name] = patch
    (ROOT / NEW).write_text(json.dumps(corrective, indent=2) + '\n')
    for directory in ('app/src', 'tests', '.github', 'docs'):
        for path in (ROOT / directory).rglob('*'):
            if not path.is_file() or '__pycache__' in path.parts:
                continue
            name = str(path.relative_to(ROOT))
            if name == OLD:
                continue
            if name in data['additions'] or name not in data['startingBlobs']:
                data['additions'][name] = blob(path.read_bytes())
    (ROOT / OLD).write_text(json.dumps(data, indent=2) + '\n')
    print('Exact E5-039 changes:', len(corrective['changes']))


if __name__ == '__main__':
    main()
