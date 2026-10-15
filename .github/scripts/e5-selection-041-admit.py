import hashlib
import json
from pathlib import Path
import runpy
import subprocess

"""Admit the exact finite E5-041 delta without weakening any historical or current test.

The original ledger keeps its M5 base. The new ledger checks complete current/base hashes
and unique reversible hunks for E5-041. Runtime tests always compile the real candidate.
"""

ROOT = Path(__file__).resolve().parents[2]
BASE = '0206a498c412dbf70535295e90426488cadede91'
OLD = '.github/scripts/e5-native-035-provenance.json'
NEW = '.github/scripts/e5-selection-041-provenance.json'
INVERSES = runpy.run_path(str(ROOT / '.github/scripts/e5-position-039-admit.py'))['inverses']


def blob(content):
    """Hash every byte using Git's whole-blob identity, including comments and source docs."""
    return hashlib.sha1(b'blob ' + str(len(content)).encode() + b'\0' + content).hexdigest()


def original(name):
    """Read the immutable E5-040 object rather than a mutable branch or reconstructed source."""
    return subprocess.check_output(['git', 'show', BASE + ':' + name], cwd=ROOT)


def main():
    """Append only explicitly enumerated hashes and verified inverse patches to both scopes."""
    data = json.loads(original(OLD))
    tracked = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', BASE],
                                      cwd=ROOT, text=True).splitlines()
    corrective = {'baseHead': BASE, 'changes': {}}
    for name in tracked:
        path = ROOT / name
        if name == OLD or not path.is_file():
            continue
        before, after = original(name), path.read_bytes()
        if before == after:
            continue
        patch = {'beforeSha': blob(before), 'afterSha': blob(after), 'inverse': INVERSES(before, after)}
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
            if name != OLD and (name in data['additions'] or name not in data['startingBlobs']):
                data['additions'][name] = blob(path.read_bytes())
    (ROOT / OLD).write_text(json.dumps(data, indent=2) + '\n')
    print('Exact E5-041 changed files:', len(corrective['changes']))


if __name__ == '__main__':
    main()
