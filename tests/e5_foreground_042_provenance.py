import hashlib
import json
from pathlib import Path

"""Recover only the exactly pinned E5-041 bytes for historical observer-only assertions.

All E5-042 runtime tests compile and execute current sources. This adapter validates complete
before/after hashes and finite inverse edits, without replacing a runtime decision or test.
"""

ROOT = Path(__file__).resolve().parents[1]
BASE = 'bb13d7cf7a1448fc23f6b5e0aebb5c6a1a022ec2'


def blob(content):
    """Calculate a complete Git blob identity for a finite provenance comparison."""
    return hashlib.sha1(b'blob ' + str(len(content)).encode() + b'\0' + content).hexdigest()


def e5042_retained_bytes(path):
    """Reverse only this task's uniquely reviewed edits after checking their whole-file hashes."""
    path = Path(path)
    content = path.read_bytes()
    data = json.loads((ROOT / '.github/scripts/e5-foreground-042-provenance.json').read_text())
    if data['baseHead'] != BASE:
        raise ValueError('Unknown E5-042 base')
    change = data['changes'].get(str(path.relative_to(ROOT)))
    if change is None:
        return content
    if blob(content) != change['afterSha']:
        raise ValueError('Unknown E5-042 bytes: ' + str(path))
    text = content.decode()
    for patch in reversed(change['inverse']):
        if text.count(patch['after']) != 1:
            raise ValueError('Ambiguous E5-042 inverse')
        text = text.replace(patch['after'], patch['before'], 1)
    before = text.encode()
    if blob(before) != change['beforeSha']:
        raise ValueError('E5-041 bytes not recovered')
    return before
