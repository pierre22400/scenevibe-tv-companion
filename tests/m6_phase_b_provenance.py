import hashlib
import json
from pathlib import Path

"""Admit a finite pure WALL addition while reconstructing the exact reconciled M6 tree.

Twelve whole-blob pairs admit only literal inventory extensions, executed accounting
and the initial inverse call. Their unique inverse restores HEAD 92b77df before
M6 reconciliation -> Sony closure -> M5 D/C/B -> M4. No runtime inverse exists.
Older boundary forms retain their exact already-qualified whole-blob identities.
"""

ROOT = Path(__file__).resolve().parents[1]
INVENTORY = ROOT / '.github/scripts/m6-phase-b-baseline.json'
PRODUCTION = frozenset({
    'app/src/main/java/com/scenevibe/tvcompanionpoc/wall/WallEvent.java',
    'app/src/main/java/com/scenevibe/tvcompanionpoc/wall/WallCalendar.java',
    'app/src/main/java/com/scenevibe/tvcompanionpoc/wall/WallCalendarScheduler.java',
})
ADDED = PRODUCTION | frozenset({
    'app/src/test/java/com/scenevibe/tvcompanionpoc/wall/M6WallContract.java',
    'app/src/test/java/com/scenevibe/tvcompanionpoc/wall/M6WallCoreTest.java',
    'tests/m6_phase_b_provenance.py',
    'tests/test_m6_phase_b_boundary.py',
    '.github/scripts/m6-phase-b-baseline.json',
    'docs/m6-phase-b-wall-models-scheduler-report.md',
})
ADMISSION_BLOBS = {'.github/scripts/m4-phase-a-test-summary.py': ('6cfed896f2d87e525a8b45835a008e874e75337b',
                                                '203f3fc1b35eb04850c84b74c265a3130a3c5fcf'),
 'tests/m5_phase_d_provenance.py': ('7c9a629d03fbea339623b0e377edc4ed9d522969',
                                    '0ee712cffa6e8c73e5cbf5092769966a8d34a2b8'),
 'tests/test_m4_phase_b_boundary.py': ('328a89b4198d47cdee02a004667282b4020d3bab',
                                       'f9fb5c7fd37a738c8bf29ef580106af5367515ac'),
 'tests/test_m4_phase_c_boundary.py': ('b7608edbd9fd058e5298218c2d9b5230652c9199',
                                       '43c57ebe90cb2b0e9d3dd91925cc8f46415602a8'),
 'tests/test_m4_phase_d_boundary.py': ('73ffcdc70297243a096adfa89a12ac88fa97c356',
                                       '40a0cd68a7c0c4ee584b560425f7bdb2fb53b5ad'),
 'tests/test_m4_phase_e_boundary.py': ('546b12dc839a1e07b40587aaa658c77765641fdc',
                                       '25b1412af5091a324d752496bfe94b1e6e4681d0'),
 'tests/test_m4_phase_f_boundary.py': ('010e734b8aae3171792dac09d0b1cc23d5865119',
                                       '47b76c5da2ec5b1fecd2a4e2b391c939b87b306f'),
 'tests/test_m4_phase_g_boundary.py': ('07603666cc1b9211b51be64309e23f945dee9227',
                                       'a7544b297f157ecce52bb50a431e2abadb2e4a2b'),
 'tests/test_m4_sony_corrective_boundary.py': ('4e1706e1dc9f88ba6d9171871ff77605f426fb41',
                                               'ee4bd693891105719014c6e8507a5f61f42641ec'),
 'tests/test_m5_phase_b_boundary.py': ('4d759935943bca64d7b04a6f4f7ffda4cfa4d3d7',
                                       'fbf40281f1b76d74fcb688aedae416731b4aac49'),
 'tests/test_m5_phase_c_boundary.py': ('d4650c166010740c73622227c24e874095ebbec8',
                                       '38c5c37c971e7709c23eed043a8aa6543b726d8f'),
 'tests/test_m5_phase_d_boundary.py': ('c1a703f53bcc2addd8f4605ce325ec8b6008d79f',
                                       'c1fc5aadcb1aa374aa84cd6cddba9e3714b40306')}
HISTORICAL_BOUNDARY_BLOBS = {
    'tests/test_m4_sony_corrective_boundary.py': ('3fc903e831a4e88009655d80f0a2786faa4b4360', 'fd56e63441ab7c915f4010f6444d4b13cc19f30a'),
    'tests/test_m5_phase_b_boundary.py': ('7fe6a17d52f6ce4b20b2542137ab51ac74eb6139', '670b30a77a9a622fe9deb4a2f0f0961ba570bcb8'),
    'tests/test_m5_phase_c_boundary.py': ('8a7e5d795e7f805094e6934b8ef249ab7003a3c0', '35e5e4de7f9e313dbd86afb3011f75b9fb3f5d4b'),
    'tests/test_m5_phase_d_boundary.py': ('f79a6a2622a233eb0c10287d4a95e35757ba3042', '695318a2954c09f02ef9f3736e1ef246ef1058be'),
}


def blob_hash(content):
    """Hash complete Git blob bytes, including docstrings, comments and whitespace."""
    return hashlib.sha1(b'blob ' + str(len(content)).encode('ascii') + b'\0' + content).hexdigest()


def inventory():
    """Require the independent whole-blob pairs and exact finite additions before normalizing."""
    data = json.loads(INVENTORY.read_text(encoding='utf-8'))
    pairs = {path: (item['beforeSha'], item['afterSha']) for path, item in data['admissions'].items()}
    if pairs != ADMISSION_BLOBS or set(data['additiveFiles']) != ADDED or set(data['productionAdded']) != PRODUCTION:
        raise ValueError('Unknown M6 B inventory or whole-blob admissions')
    return data


def m6_phase_b_added_paths():
    """Return nine literal paths, never a filename or package wildcard."""
    return set(ADDED)


def m6_phase_b_production_files():
    """Return only the three independently compiled pure WALL source files."""
    return set(PRODUCTION)


def restore_blob(relative, content):
    """Reverse one recognized complete blob through unique literal contexts to HEAD 92b77df."""
    admission = inventory()['admissions'].get(relative)
    if admission is None:
        return content
    digest = blob_hash(content)
    if digest == admission['beforeSha'] or digest in HISTORICAL_BOUNDARY_BLOBS.get(relative, ()):
        return content
    if digest != admission['afterSha']:
        raise ValueError('Unknown M6 B admission blob')
    source = content.decode('utf-8')
    for patch in reversed(admission['patches']):
        if source.count(patch['after']) != 1:
            raise ValueError('Missing or ambiguous M6 B inverse context')
        source = source.replace(patch['after'], patch['before'], 1)
    previous = source.encode('utf-8')
    if blob_hash(previous) != admission['beforeSha']:
        raise ValueError('M6 B inverse differs from reconciled starting blob')
    return previous


def m6_phase_b_retained_bytes(path):
    """Read actual bytes then apply only the twelve pinned inverses before historical provenance."""
    path = Path(path)
    return restore_blob(str(path.relative_to(ROOT)), path.read_bytes())
