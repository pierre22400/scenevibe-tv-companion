import json
from pathlib import Path

from e5_native_provenance import blob_hash
from e5_foreground_042_provenance import e5042_retained_bytes

"""Reverse only exactly hashed E5-041 edits for historical source comparisons.

Current JVM, Android and selector differential tests execute the real instrumented sources.
This helper cannot admit a runtime outcome; it preserves older byte assertions/docstrings
against their immutable bases while a separate E5-041 test checks the actual changed scope.
"""

ROOT = Path(__file__).resolve().parents[1]
BASE = '0206a498c412dbf70535295e90426488cadede91'


def e5041_retained_bytes(path):
    """Verify complete candidate bytes and finite inverse hunks before exposing the base bytes."""
    path = Path(path)
    content = e5042_retained_bytes(path)
    data = json.loads((ROOT / '.github/scripts/e5-selection-041-provenance.json').read_text())
    if data['baseHead'] != BASE:
        raise ValueError('Unknown E5-041 base')
    change = data['changes'].get(str(path.relative_to(ROOT)))
    if change is None:
        return content
    if blob_hash(content) != change['afterSha']:
        raise ValueError('Unknown E5-041 candidate bytes')
    text = content.decode('utf-8')
    for patch in reversed(change['inverse']):
        if text.count(patch['after']) != 1:
            raise ValueError('Ambiguous E5-041 inverse')
        text = text.replace(patch['after'], patch['before'], 1)
    before = text.encode('utf-8')
    if blob_hash(before) != change['beforeSha']:
        raise ValueError('E5-040 bytes not recovered exactly')
    return before
