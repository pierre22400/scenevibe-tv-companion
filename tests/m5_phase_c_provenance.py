from m5_phase_d_provenance import phase_d_retained_bytes
import hashlib
import json
from pathlib import Path

"""Admit only finite Phase C additions and reconstruct the complete accepted final B bytes.

C has no production inverse: all prior production, values, fixtures and oracle sources
remain identical. Only reviewed path admission and executed-accounting edits reverse
through unique contexts. Behavioral tests always inspect and execute actual bytes.
"""

ROOT = Path(__file__).resolve().parents[1]
INVENTORY = ROOT / '.github/scripts/m5-phase-c-baseline.json'
PRODUCTION = frozenset({
    'app/src/main/java/com/scenevibe/tvcompanionpoc/calendar/MediaCalendarScheduler.java',
    'app/src/main/java/com/scenevibe/tvcompanionpoc/VideoMediaObservationAdapter.java',
})


def blob_hash(content):
    """Compute an entire Git blob, including documentation and whitespace."""
    return hashlib.sha1(b'blob ' + str(len(content)).encode('ascii') + b'\0' + content).hexdigest()


def phase_c_production_files():
    """Return two exact authorized non-live paths, never a package wildcard."""
    return set(PRODUCTION)


def phase_c_added_paths():
    """Return the finite C inventory whose categories are independently pinned by the C gate."""
    return set(json.loads(INVENTORY.read_text(encoding='utf-8'))['additiveFiles'])


def phase_c_retained_bytes(path):
    """Undo only unique C provenance/accounting edits and require exact final B blob identity."""
    path = Path(path)
    content = phase_d_retained_bytes(path)
    inventory = json.loads(INVENTORY.read_text(encoding='utf-8'))
    relative = str(path.relative_to(ROOT))
    patches = inventory['provenancePatches'].get(relative)
    if patches is None:
        return content
    source = content.decode('utf-8')
    for patch in reversed(patches):
        if source.count(patch['after']) != 1:
            raise ValueError('Missing or ambiguous C inverse context')
        source = source.replace(patch['after'], patch['before'])
    previous = source.encode('utf-8')
    if blob_hash(previous) != inventory['startingBlobs'][relative]:
        raise ValueError('C inverse differs from the accepted final B blob')
    return previous
