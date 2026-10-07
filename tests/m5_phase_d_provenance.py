import hashlib
import json
from pathlib import Path
import m6_phase_b_provenance
from m6_phase_c_provenance import m6_phase_c_retained_bytes

"""Reconstruct exact accepted C bytes before the existing C/B/M4 provenance chain.

Only finite uniquely reversible D API, composition and executed-accounting edits are
admitted. Runtime/business tests execute actual source; inverses are restricted to
historical whole-blob provenance and historical non-live-stage boundaries.

Final Sony closure admissions are reversed first, only for four exact boundary
files. Both complete accepted blobs are pinned; every other edit fails closed.
The immutable B/C/D inventories and all runtime/fixture bytes remain unchanged.

Post-merge M6 architecture admissions are reversed before Sony closure. Exactly
two Markdown paths and four whole-file pairs are admitted, never a namespace.
The reconstructed bytes must equal the merged M5 blobs before older inverses run.
"""

ROOT = Path(__file__).resolve().parents[1]
INVENTORY = ROOT / '.github/scripts/m5-phase-d-baseline.json'
PHYSICAL_CLOSURE_ADMISSIONS = {
    'tests/test_m4_sony_corrective_boundary.py': {
        'beforeSha': '3fc903e831a4e88009655d80f0a2786faa4b4360',
        'afterSha': 'fd56e63441ab7c915f4010f6444d4b13cc19f30a',
        'before': "    'docs/m5-phase-a-architecture-report.md',\n",
        'after': "    'docs/m5-phase-a-architecture-report.md',\n"
                 "    'docs/m5-final-sony-physical-closure.md',\n",
    },
    'tests/test_m5_phase_b_boundary.py': {
        'beforeSha': '7fe6a17d52f6ce4b20b2542137ab51ac74eb6139',
        'afterSha': '670b30a77a9a622fe9deb4a2f0f0961ba570bcb8',
        'before': "| phase_d_added_paths()\n",
        'after': "| phase_d_added_paths() | {'docs/m5-final-sony-physical-closure.md'}\n",
    },
    'tests/test_m5_phase_c_boundary.py': {
        'beforeSha': '8a7e5d795e7f805094e6934b8ef249ab7003a3c0',
        'afterSha': '35e5e4de7f9e313dbd86afb3011f75b9fb3f5d4b',
        'before': "| phase_d_added_paths()\n",
        'after': "| phase_d_added_paths() | {'docs/m5-final-sony-physical-closure.md'}\n",
    },
    'tests/test_m5_phase_d_boundary.py': {
        'beforeSha': 'f79a6a2622a233eb0c10287d4a95e35757ba3042',
        'afterSha': '695318a2954c09f02ef9f3736e1ef246ef1058be',
        'before': "| ADDED if p.startswith(('app/src/', 'tests/', '.github/', 'docs/'))}\n",
        'after': "| ADDED | {'docs/m5-final-sony-physical-closure.md'} "
                 "if p.startswith(('app/src/', 'tests/', '.github/', 'docs/'))}\n",
    },
}

M6_RECONCILIATION_ADMISSIONS = {
    'tests/test_m4_sony_corrective_boundary.py': {
        'beforeSha': 'fd56e63441ab7c915f4010f6444d4b13cc19f30a',
        'afterSha': '4e1706e1dc9f88ba6d9171871ff77605f426fb41',
        'before': "    'docs/m5-final-sony-physical-closure.md',\n",
        'after': "    'docs/m5-final-sony-physical-closure.md',\n"
                 "    'docs/scenevibe-os-m6-banner-wall-clock-architecture.md',\n"
                 "    'docs/m6-phase-a-architecture-report.md',\n",
    },
    'tests/test_m5_phase_b_boundary.py': {
        'beforeSha': '670b30a77a9a622fe9deb4a2f0f0961ba570bcb8',
        'afterSha': '4d759935943bca64d7b04a6f4f7ffda4cfa4d3d7',
        'before': "| {'docs/m5-final-sony-physical-closure.md'}\n",
        'after': "| {'docs/m5-final-sony-physical-closure.md'} "
                 "| {'docs/scenevibe-os-m6-banner-wall-clock-architecture.md', 'docs/m6-phase-a-architecture-report.md'}\n",
    },
    'tests/test_m5_phase_c_boundary.py': {
        'beforeSha': '35e5e4de7f9e313dbd86afb3011f75b9fb3f5d4b',
        'afterSha': 'd4650c166010740c73622227c24e874095ebbec8',
        'before': "| {'docs/m5-final-sony-physical-closure.md'}\n",
        'after': "| {'docs/m5-final-sony-physical-closure.md'} "
                 "| {'docs/scenevibe-os-m6-banner-wall-clock-architecture.md', 'docs/m6-phase-a-architecture-report.md'}\n",
    },
    'tests/test_m5_phase_d_boundary.py': {
        'beforeSha': '695318a2954c09f02ef9f3736e1ef246ef1058be',
        'afterSha': 'c1a703f53bcc2addd8f4605ce325ec8b6008d79f',
        'before': "| {'docs/m5-final-sony-physical-closure.md'} if p.startswith",
        'after': "| {'docs/m5-final-sony-physical-closure.md'} "
                 "| {'docs/scenevibe-os-m6-banner-wall-clock-architecture.md', 'docs/m6-phase-a-architecture-report.md'} if p.startswith",
    },
}


def blob_hash(content):
    """Compute the full immutable Git blob identity without trusting the working index."""
    return hashlib.sha1(b'blob ' + str(len(content)).encode('ascii') + b'\0' + content).hexdigest()


def phase_d_added_paths():
    """Return exact inventoried D paths; no namespace or filename wildcard is admitted."""
    return set(json.loads(INVENTORY.read_text(encoding='utf-8'))['additiveFiles'])


def phase_d_changed_paths():
    """Return the finite reviewed inverse paths, checked independently by the D gate."""
    return set(json.loads(INVENTORY.read_text(encoding='utf-8'))['provenancePatches'])


def m6_reconciliation_retained_bytes(path):
    """Reverse exact M6 document admissions to merged M5, preserving both older accepted blobs."""
    path = Path(path)
    relative = str(path.relative_to(ROOT))
    content = m6_phase_b_provenance.restore_blob(relative, m6_phase_c_retained_bytes(path))
    admission = M6_RECONCILIATION_ADMISSIONS.get(relative)
    if admission is None:
        return content
    digest = blob_hash(content)
    if digest in (admission['beforeSha'], PHYSICAL_CLOSURE_ADMISSIONS[relative]['beforeSha']):
        return content
    if digest != admission['afterSha']:
        raise ValueError('Unknown M6 reconciliation admission blob')
    source = content.decode('utf-8')
    if source.count(admission['after']) != 1:
        raise ValueError('Missing or ambiguous M6 reconciliation admission')
    previous = source.replace(admission['after'], admission['before'], 1).encode('utf-8')
    if blob_hash(previous) != admission['beforeSha']:
        raise ValueError('M6 reconciliation inverse differs from merged M5 blob')
    return previous


def physical_closure_retained_bytes(path):
    """Reverse only the four exact document admissions to their qualified c9b0efd blobs.

    Whole-file hashes reject unrelated edits before a unique literal inverse runs.
    This affects historical provenance only; actual inventory and behavior checks
    still read and execute the current source, with no rewritten baseline.
    """
    path = Path(path)
    content = m6_reconciliation_retained_bytes(path)
    admission = PHYSICAL_CLOSURE_ADMISSIONS.get(str(path.relative_to(ROOT)))
    if admission is None:
        return content
    digest = blob_hash(content)
    if digest == admission['beforeSha']:
        return content
    if digest != admission['afterSha']:
        raise ValueError('Unknown final Sony closure admission blob')
    source = content.decode('utf-8')
    if source.count(admission['after']) != 1:
        raise ValueError('Missing or ambiguous final Sony closure admission')
    previous = source.replace(admission['after'], admission['before'], 1).encode('utf-8')
    if blob_hash(previous) != admission['beforeSha']:
        raise ValueError('Final Sony closure inverse differs from qualified software blob')
    return previous


def phase_d_retained_bytes(path):
    """Undo unique D hunks and require the entire accepted C blob before older inverses run."""
    path = Path(path)
    content = physical_closure_retained_bytes(path)
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
