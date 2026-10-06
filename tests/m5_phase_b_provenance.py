import hashlib
import json
from pathlib import Path
from m5_phase_c_provenance import phase_c_retained_bytes

"""Admit exactly three non-live M5 values while retaining every prior M4 predicate.

The finite inverse inventory restores only reviewed B provenance/accounting edits
to the exact 689eb3a starting bytes. Semantic tests always inspect actual sources;
no production result, fixture or business assertion is reconstructed or replaced.
"""

ROOT = Path(__file__).resolve().parents[1]
INVENTORY = ROOT / '.github/scripts/m5-phase-b-baseline.json'
CALENDAR_MODELS = frozenset({
    'app/src/main/java/com/scenevibe/tvcompanionpoc/calendar/SceneEvent.java',
    'app/src/main/java/com/scenevibe/tvcompanionpoc/calendar/MediaCalendar.java',
    'app/src/main/java/com/scenevibe/tvcompanionpoc/calendar/MediaObservation.java',
})


def blob_hash(content):
    """Compute the complete Git blob identity without consulting a mutable index."""
    return hashlib.sha1(b'blob ' + str(len(content)).encode('ascii') + b'\0' + content).hexdigest()


def calendar_model_files():
    """Return only the exact three authorized production paths, never a namespace wildcard."""
    return set(CALENDAR_MODELS)


def phase_b_added_paths():
    """Read the finite additions whose exact inventory is independently checked by B's gate."""
    inventory = json.loads(INVENTORY.read_text(encoding='utf-8'))
    return set(inventory['additiveFiles']) | set(inventory['documents'])


def qualification_retained_bytes(path):
    """Undo only finite pre-Phase-C qualification edits to the exact ce724e9 blobs.

    Compilation and behavior checks still execute actual bytes. This inverse is
    exclusively for provenance, before the existing B and Sony inverse layers.
    """
    path = Path(path)
    content = phase_c_retained_bytes(path)
    inventory = json.loads(INVENTORY.read_text(encoding='utf-8'))['qualificationCorrective']
    relative = str(path.relative_to(ROOT))
    patches = inventory['patches'].get(relative)
    if patches is None:
        return content
    source = content.decode('utf-8')
    for patch in reversed(patches):
        if source.count(patch['after']) != 1:
            raise ValueError('Missing or ambiguous pre-Phase-C qualification edit')
        source = source.replace(patch['after'], patch['before'])
    previous = source.encode('utf-8')
    if blob_hash(previous) != inventory['startingBlobs'][relative]:
        raise ValueError('Qualification inverse differs from immutable ce724e9 blob')
    return previous


def phase_b_retained_bytes(path):
    """Reverse uniquely inventoried provenance edits and verify the complete starting blob."""
    path = Path(path)
    content = qualification_retained_bytes(path)
    inventory = json.loads(INVENTORY.read_text(encoding='utf-8'))
    relative = str(path.relative_to(ROOT))
    patches = inventory['provenancePatches'].get(relative)
    if patches is None:
        return content
    source = content.decode('utf-8')
    for patch in reversed(patches):
        if source.count(patch['after']) != 1:
            raise ValueError('Missing or ambiguous M5 provenance edit')
        source = source.replace(patch['after'], patch['before'])
    previous = source.encode('utf-8')
    if blob_hash(previous) != inventory['startingBlobs'][relative]:
        raise ValueError('M5 provenance inverse differs from immutable starting blob')
    return previous
