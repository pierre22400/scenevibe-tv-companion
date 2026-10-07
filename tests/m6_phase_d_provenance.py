import hashlib
import json
from pathlib import Path

"""Admit only the finite M6 D transport changes before the frozen C→B→M5→M4 chain.

Every existing-file admission has a complete before/after Git blob pair and a
unique whole-file inverse to the exact 1f1aa52 starting bytes. The independent
literal pairs prevent a changed baseline alone from blessing an unknown edit.
No inherited baseline, test, skip or production permission is rewritten.
"""

ROOT = Path(__file__).resolve().parents[1]
INVENTORY = ROOT / '.github/scripts/m6-phase-d-baseline.json'
STARTING_HEAD = '1f1aa52d01cb5c8211a2956acfaa30c43708c8d2'
JAVA = 'app/src/main/java/com/scenevibe/tvcompanionpoc/'
PRODUCTION = frozenset({JAVA + 'CloudPackageInstallationAdapter.java', JAVA + 'CloudPackageVideoInstallationHandler.java'})
ADDED = PRODUCTION | frozenset({
    'app/src/test/java/com/scenevibe/tvcompanionpoc/M6PackageFixtures.java',
    'app/src/test/java/com/scenevibe/tvcompanionpoc/M6PackageTransportTest.java',
    'app/src/test/resources/m6/banner-package-assignment.json',
    'tests/m6_phase_d_provenance.py', 'tests/test_m6_phase_d_boundary.py',
    '.github/scripts/m6-phase-d-baseline.json', 'docs/m6-phase-d-package-transport-ack-report.md',
})
ADMISSION_BLOBS = {'.github/scripts/m4-phase-a-test-summary.py': ('3315c920951f3d63c401f32e20b4ebec5cc10988',
                                                '6e0d1172f17896a8412b14a8d85ca1b05632c1d8'),
 'app/src/main/java/com/scenevibe/tvcompanionpoc/CloudControlClient.java': ('48fccfe62b2964f792fabd8d750106c424df4957',
                                                                            'c7e445be877b221ed9b0eee5e4b210aa12f81beb'),
 'app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayInstallationHandlers.java': ('abd958fb20f2666a6b8cba7c413323c97de4df1f',
                                                                                     '48d315a29a14d7e431e86846b9eaa287a0a97bae'),
 'tests/m6_phase_c_provenance.py': ('b159aeeb373612cc07705e9515b11f007273806d',
                                    '0279c9b6c47e1d5aa4a8ccc7ea65099fef379e7a'),
 'tests/test_m4_phase_b_boundary.py': ('bb49ee9732429ae8ebf59734b23fcdb18c43135f',
                                       '7f6b8fe7fc3dad56f36f5455fa44f59de0fd7a29'),
 'tests/test_m4_phase_c_boundary.py': ('9e041944d5139f4733f461bc1797234368f0872a',
                                       '8ff387e08678539b3c02e3bce385d0e02db834f8'),
 'tests/test_m4_phase_d_boundary.py': ('5a21516c5ebc204808857f889d36baf46a0de7f5',
                                       '9a1b48410a583d8b18eb144d9377fb4fc569b615'),
 'tests/test_m4_phase_e_boundary.py': ('a29da538a57b7aae321300e0a85fbfd8840b4760',
                                       'ca24c339d7a2203183c4d8460b7f05b0a7631374'),
 'tests/test_m4_phase_f_boundary.py': ('c864b841d80f3f30ada637f508216c7bc2df7a44',
                                       'fb960d7a64c72a59b1cf4ff0ac2063f8964c72d6'),
 'tests/test_m4_phase_g_boundary.py': ('cb5bf610a9883707ae9a33a8d9f3d9c1ebc958bb',
                                       'd1d8f5bf55971bc2b014302d52a605da63acd6fd'),
 'tests/test_m4_sony_corrective_boundary.py': ('4a13c8b7febdeedad851b50ecd2ed5f4267df07c',
                                               '5e40143605dbb5ef24bc1f5190428f21bfef2920'),
 'tests/test_m5_phase_b_boundary.py': ('1dbbde2fc8d1f389b513991a658ecb66301ca0f4',
                                       '28755b609bb6caa914b36520096f8f26b3a469c9'),
 'tests/test_m5_phase_c_boundary.py': ('de1045cfc1def808d9498634cabc4e7fd7b7e0a7',
                                       'd80d9be39d27eff083e4145a0cb907632c9453c5'),
 'tests/test_m5_phase_d_boundary.py': ('65b88e70ce76f878911dbcb302980a0b8906b90f',
                                       '46e2c153c503cf5ebd1a9375a6c11012e5d29961'),
 'tests/test_m6_phase_b_boundary.py': ('79641db98c532ca555dba12735d0f0475cf1b298',
                                       '93eb8cd0473185d9e1076f104de30e22f8d2c0fd'),
 'tests/test_m6_phase_c_boundary.py': ('05432b90effca5965cf66cc6d0ac1a2e9cf9176b',
                                       'cdb0b55b8c26dbe97f8fde1d5196da76f6399f03')}


def blob_hash(content):
    """Hash the complete Git blob, including teaching comments and whitespace."""
    return hashlib.sha1(b'blob ' + str(len(content)).encode('ascii') + b'\0' + content).hexdigest()


def inventory():
    """Require independent finite paths, starting identity and exact whole-blob pairs."""
    data = json.loads(INVENTORY.read_text(encoding='utf-8'))
    pairs = {path: (item['beforeSha'], item['afterSha']) for path, item in data['admissions'].items()}
    if data['startingHead'] != STARTING_HEAD or data['startingTree'] != '7e6cce27be7f78a78b62bf468df94860c89f1f50':
        raise ValueError('Unknown M6 D starting identity')
    if pairs != ADMISSION_BLOBS or set(data['additiveFiles']) != ADDED or set(data['productionAdded']) != PRODUCTION:
        raise ValueError('Unknown M6 D finite admission')
    return data


def m6_phase_d_added_paths():
    """Return only nine literal additions, never a namespace or wildcard admission."""
    return set(ADDED)


def m6_phase_d_production_files():
    """Return only the two build-local transport/Video composition classes."""
    return set(PRODUCTION)


def restore_blob(relative, content):
    """Reverse only a recognized current blob through its complete unique inverse to 1f1aa52."""
    admission = inventory()['admissions'].get(relative)
    if admission is None:
        return content
    digest = blob_hash(content)
    if digest == admission['beforeSha']:
        return content
    if digest != admission['afterSha']:
        raise ValueError('Unknown M6 D admission blob')
    source = content.decode('utf-8')
    for inverse in reversed(admission['patches']):
        if source.count(inverse['after']) != 1:
            raise ValueError('Missing or ambiguous M6 D inverse')
        source = source.replace(inverse['after'], inverse['before'], 1)
    previous = source.encode('utf-8')
    if blob_hash(previous) != admission['beforeSha']:
        raise ValueError('M6 D inverse differs from exact starting blob')
    return previous


def m6_phase_d_retained_bytes(path):
    """Read actual current source and apply only the finite M6 D whole-blob inverses."""
    path = Path(path)
    return restore_blob(str(path.relative_to(ROOT)), path.read_bytes())
