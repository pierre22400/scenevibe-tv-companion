import hashlib
import json
from pathlib import Path

"""Reconstruct exact accepted C bytes before the existing C/B/M4 provenance chain.

Only finite uniquely reversible D API, composition and executed-accounting edits are
admitted. Runtime/business tests execute actual source; inverses are restricted to
historical whole-blob provenance and historical non-live-stage boundaries.
"""

ROOT = Path(__file__).resolve().parents[1]
INVENTORY = ROOT / '.github/scripts/m5-phase-d-baseline.json'


def blob_hash(content):
    """Compute the full immutable Git blob identity without trusting the working index."""
    return hashlib.sha1(b'blob ' + str(len(content)).encode('ascii') + b'\0' + content).hexdigest()


def phase_d_added_paths():
    """Return exact inventoried D paths; no namespace or filename wildcard is admitted."""
    return set(json.loads(INVENTORY.read_text(encoding='utf-8'))['additiveFiles'])


def phase_d_changed_paths():
    """Return the finite reviewed inverse paths, checked independently by the D gate."""
    return set(json.loads(INVENTORY.read_text(encoding='utf-8'))['provenancePatches'])


def phase_d_retained_bytes(path):
    """Undo unique D hunks and require the entire accepted C blob before older inverses run."""
    path = Path(path)
    content = path.read_bytes()
    inventory = json.loads(INVENTORY.read_text(encoding='utf-8'))
    relative = str(path.relative_to(ROOT))
    patches = inventory['provenancePatches'].get(relative)
    if patches is None:
        return content
    source = content.decode('utf-8')
    for patch in reversed(patches):
        if source.count(patch['after']) != 1:
            raise ValueError('Missing or ambiguous D inverse context')
        source = source.replace(patch['after'], patch['before'])
    previous = source.encode('utf-8')
    if blob_hash(previous) != inventory['startingBlobs'][relative]:
        raise ValueError('D inverse differs from the exact accepted C blob')
    return previous
