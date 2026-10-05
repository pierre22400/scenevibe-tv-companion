import hashlib
import json
from pathlib import Path

"""Preserve every prior A–G whole-blob pin while admitting the localized Sony correction.

Only byte/provenance checks call these helpers. Semantic checks and compilation still read
the actual sources. Each allowed edit reverses uniquely to the exact 824339d starting blob;
unknown edits, missing contexts and expanded exceptions are hard failures in the new gate.
No production behavior or test outcome is normalized, skipped or replaced.
"""

ROOT = Path(__file__).resolve().parents[1]
INVENTORY = ROOT / '.github/scripts/m4-phase-g-sony-corrective-baseline.json'


def blob_hash(content):
    """Compare complete immutable Git blob identities, independently of the working Git index."""
    return hashlib.sha1(b'blob ' + str(len(content)).encode('ascii') + b'\0' + content).hexdigest()


def retained_bytes(path):
    """Undo only reviewed unique corrective edits before an older gate's full-file provenance check."""
    path = Path(path)
    content = path.read_bytes()
    inventory = json.loads(INVENTORY.read_text(encoding='utf-8'))
    relative = str(path.relative_to(ROOT))
    patches = inventory['correctivePatches'].get(relative)
    if patches is None:
        return content
    source = content.decode('utf-8')
    for patch in reversed(patches):
        if source.count(patch['after']) != 1:
            raise ValueError('Missing or ambiguous corrective provenance edit')
        source = source.replace(patch['after'], patch['before'])
    previous = source.encode('utf-8')
    if blob_hash(previous) != inventory['startingBlobs'][relative]:
        raise ValueError('Corrective inverse differs from immutable starting blob')
    return previous


def retained_text(path):
    """Provide the exact starting text only for earlier inverse-patch provenance comparisons."""
    return retained_bytes(path).decode('utf-8')
