import json
from pathlib import Path

from e5_native_provenance import blob_hash

"""Retain E5-038's historical byte assertions while checking each E5-039 edit exactly.

This adapter is used only by historical source comparisons. All JVM and Android tests
compile and execute the current candidate, including the unchanged negative transport tests.
"""

ROOT = Path(__file__).resolve().parents[1]


def e5039_retained_bytes(path):
    """Validate full corrective bytes, reverse finite literals, and verify the E5-038 blob."""
    path = Path(path)
    content = path.read_bytes()
    name = str(path.relative_to(ROOT))
    data = json.loads((ROOT / '.github/scripts/e5-position-039-provenance.json').read_text())
    if data['baseHead'] != 'efd3a7983a03458ec1f919d760c10f1b1f9c57ff':
        raise ValueError('Unexpected E5-039 base')
    change = data['changes'].get(name)
    if change is None:
        return content
    if blob_hash(content) != change['afterSha']:
        raise ValueError('Unknown E5-039 bytes: ' + name)
    text = content.decode('utf-8')
    for patch in reversed(change['inverse']):
        if text.count(patch['after']) != 1:
            raise ValueError('Ambiguous E5-039 inverse: ' + name)
        text = text.replace(patch['after'], patch['before'], 1)
    previous = text.encode('utf-8')
    if blob_hash(previous) != change['beforeSha']:
        raise ValueError('E5-038 historical bytes changed: ' + name)
    return previous


def e5039_retained_text(path):
    """Decode only the exactly validated historical source comparison."""
    return e5039_retained_bytes(path).decode('utf-8')
