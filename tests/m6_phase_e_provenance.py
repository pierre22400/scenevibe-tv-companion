import hashlib
import json
from pathlib import Path
from m6_phase_e_assembly_provenance import m6_assembly_added_paths, m6_assembly_retained_bytes

"""Retain the exact M6 D software while admitting only the finite E blocked report.

This overlay authorizes no runtime, capability, permission, build, fixture or
workflow change. Two current provenance blobs have whole-file inverses; unknown
bytes and altered pins are rejected independently before D -> C -> B -> M5 -> M4.
"""

ROOT = Path(__file__).resolve().parents[1]
INVENTORY = ROOT / '.github/scripts/m6-phase-e-baseline.json'
STARTING_HEAD = 'd311c05ecd4c0b73c5e23afa6280e76230f4d0f3'
STARTING_TREE = 'a6d09b55da2982c59d3ff245be1ed47dc968b40d'
STARTING_INVENTORY_SHA256 = '7054c6b0acc909859b353aacc080480616ef8fc425a195dc04f1016c5acaf937'
REPORT_BLOB = 'f5ede43c411d7d487c20c32a9834dac6b4784352'
ADDED = frozenset(['.github/scripts/m6-phase-e-baseline.json', 'docs/m6-phase-e-final-qualification-report.md', 'tests/m6_phase_e_provenance.py', 'tests/test_m6_phase_e_boundary.py'])
ADMISSION_BLOBS = {'tests/m6_phase_d_provenance.py': ('cdf7c0d0b7b6044528408a89eeb35942f0d44471', '4c33a55b882be89e32de974aadc6f1d925df1ce0'), 'tests/test_m6_phase_d_boundary.py': ('63655d2ef07906fb8c584701ddba91e7badd9238', '2d61ad851090f5ca6ffb83f01d26df0dcaa2961e')}


def blob_hash(content):
    """Identify the whole Git blob, including teaching comments and whitespace."""
    return hashlib.sha1(b'blob ' + str(len(content)).encode('ascii') + b'\0' + content).hexdigest()


def inventory():
    """Require the fixed D authority, exact inventory and independently pinned E admissions."""
    data = json.loads(INVENTORY.read_text(encoding='utf-8'))
    serialized = json.dumps(data['startingBlobs'], sort_keys=True, separators=(',', ':')).encode('utf-8')
    pairs = {path: (item['beforeSha'], item['afterSha']) for path, item in data['admissions'].items()}
    if (data['startingHead'] != STARTING_HEAD or data['startingTree'] != STARTING_TREE
            or hashlib.sha256(serialized).hexdigest() != STARTING_INVENTORY_SHA256
            or pairs != ADMISSION_BLOBS or set(data['additiveFiles']) != ADDED
            or data['productionAdded'] != [] or data['runtimeChanged'] is not False
            or data['addedBlobs'].get('docs/m6-phase-e-final-qualification-report.md') != REPORT_BLOB):
        raise ValueError('Unknown M6 E documentary admission')
    return data


def m6_phase_e_added_paths():
    """Return four literal documentary/provenance additions, never a namespace admission."""
    return set(ADDED) | m6_assembly_added_paths()


def restore_blob(relative, content):
    """Reverse exactly one known E whole-file blob to its qualified D identity."""
    admission = inventory()['admissions'].get(relative)
    if admission is None:
        return content
    digest = blob_hash(content)
    if digest == admission['beforeSha']:
        return content
    if digest != admission['afterSha']:
        raise ValueError('Unknown M6 E admission blob')
    source = content.decode('utf-8')
    for inverse in reversed(admission['patches']):
        if source.count(inverse['after']) != 1:
            raise ValueError('Missing or ambiguous M6 E inverse')
        source = source.replace(inverse['after'], inverse['before'], 1)
    previous = source.encode('utf-8')
    if blob_hash(previous) != admission['beforeSha']:
        raise ValueError('M6 E inverse differs from exact Phase D blob')
    return previous


def m6_phase_e_retained_bytes(path):
    """Read current bytes and remove only the closed E documentary/provenance overlay."""
    path = Path(path)
    return restore_blob(str(path.relative_to(ROOT)), m6_assembly_retained_bytes(path))
