import hashlib
import json
from pathlib import Path

"""Reverse only the named Phase E assembly before the frozen historical E→D chain.

Complete independent blob pairs reject unknown bytes and baseline-only retargeting.
Current behavior is tested separately on actual classes; these inverses serve provenance.
"""

ROOT = Path(__file__).resolve().parents[1]
INVENTORY = ROOT / '.github/scripts/m6-phase-e-assembly-baseline.json'
ADDED = frozenset(['.github/scripts/m6-phase-e-assembly-baseline.json', '.github/scripts/m6-phase-e-peer.init.gradle', 'app/src/test/java/com/scenevibe/tvcompanionpoc/M6PhaseEAssemblyTest.java', 'app/src/test/java/com/scenevibe/tvcompanionpoc/M6PhaseEHttpPeer.java', 'docs/m6-phase-e-software-gate.json', 'docs/m6-phase-e-sony-operator-protocol.md', 'tests/m6_phase_e_assembly_provenance.py', 'tests/test_m6_phase_e_assembly_boundary.py'])
ADMISSION_BLOBS = {'.github/scripts/m4-phase-a-test-summary.py': ('6e0d1172f17896a8412b14a8d85ca1b05632c1d8', 'd9ccbde96ea96abd4da18a2b93507f2002d2f583'), '.github/workflows/android-debug.yml': ('a2ec93d8e2876b603bbed7725f25a1aaacdcc7a6', '502a89a35726e27f86cb396b92834464b59cb646'), 'app/build.gradle': ('043a1b11da8e89107b2d82f0badffb3993bbf880', '87b01020b0123509d4a72d9ba0953b74acb0df6d'), 'app/src/main/java/com/scenevibe/tvcompanionpoc/CloudControlClient.java': ('4e736e1444f7419f3ba66207cbf2866ec341f33d', 'e6702d5aed2f0168aa50d6ddcf5b049887ed26dd'), 'app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayRuntimePorts.java': ('4a6610cc25e6d23077f89e78f0adc26fe9939c88', '71bb1d5dac42da8fe0cc0b4c0be511788a41c9b5'), 'app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayService.java': ('4241fed818cd1b9a05331d5c419c59fe8389cf29', 'ab1dd664a262128e3b608ff6e1813e1ea942131a'), 'app/src/main/java/com/scenevibe/tvcompanionpoc/installation/TvCapabilities.java': ('0509a9247735dea0722455eb71491f13c0dfa86e', '012c10c452e2032c24c20bde31bd3fbce4e86ef6'), 'docs/m6-phase-e-final-qualification-report.md': ('f5ede43c411d7d487c20c32a9834dac6b4784352', '4ec5829c4ab278a3182c32d9a74c44e7fc09d8e8'), 'tests/m6_phase_e_provenance.py': ('3a14c379d76e311c67a71b05651f1d35dbf8b0c6', 'dd7cfc9cea09b21a75e6dd7c1f34a8d6e5d9e65c'), 'tests/test_m6_phase_d_boundary.py': ('2d61ad851090f5ca6ffb83f01d26df0dcaa2961e', 'c16dcdab7498d141826e70f3f611dba760ffcd80'), 'tests/test_m6_phase_e_boundary.py': ('de6f6169b4de6892a3bd472ecde5d113d169b179', '962b741a89692586e5516780f93191ee9a5516a1')}
STARTING_DIGEST = '403a9db5e22767109a4492a2a9fd4770aa1d3b29e14d7e8664549aace69d5555'


def blob_hash(content):
    """Hash the complete Git blob, retaining comments and exact whitespace."""
    return hashlib.sha1(b'blob ' + str(len(content)).encode() + b'\0' + content).hexdigest()


def inventory():
    """Require independently fixed origin, inventory, paths, pairs and executed suite counts."""
    data = json.loads(INVENTORY.read_text(encoding='utf-8'))
    pairs = {path: (item['beforeSha'], item['afterSha']) for path, item in data['admissions'].items()}
    digest = hashlib.sha256(json.dumps(data['startingBlobs'], sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    if (data['startingHead'] != 'f72d696e3e46fcd994dce251d1ad1e775d56a8a9'
            or data['startingTree'] != 'b675d997f295d44e97961f22f22666eb16be72d6'
            or digest != STARTING_DIGEST or pairs != ADMISSION_BLOBS
            or set(data['additiveFiles']) != ADDED or data['m6PhaseESuites'] != {'M6PhaseEAssemblyTest': 6}):
        raise ValueError('Unknown M6 E assembly admission')
    return data


def m6_assembly_added_paths():
    """Return only the explicitly enumerated assembly additions."""
    return set(ADDED)


def restore_blob(relative, content):
    """Undo one exact approved whole-file assembly edit; every other mutation fails closed."""
    admission = inventory()['admissions'].get(relative)
    if admission is None:
        return content
    digest = blob_hash(content)
    if digest == admission['beforeSha']:
        return content
    if digest != admission['afterSha']:
        raise ValueError('Unknown M6 E assembly blob')
    source = content.decode('utf-8')
    for inverse in reversed(admission['patches']):
        if source.count(inverse['after']) != 1:
            raise ValueError('Missing or ambiguous M6 E assembly inverse')
        source = source.replace(inverse['after'], inverse['before'], 1)
    previous = source.encode('utf-8')
    if blob_hash(previous) != admission['beforeSha']:
        raise ValueError('M6 E assembly inverse differs from starting bytes')
    return previous


def m6_assembly_retained_bytes(path):
    """Return the exact f72d696e bytes before the historic E validator runs."""
    path = Path(path)
    return restore_blob(str(path.relative_to(ROOT)), path.read_bytes())
