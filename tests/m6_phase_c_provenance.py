import hashlib
import json
from pathlib import Path

"""Admit the finite M6 Phase C WALL integration while reconstructing the frozen 2d2c22a tree.

Phase C is the TV-local WALL runtime integration: a common runtime owner, the Android
WALL clock driver, the local Banner handler/codec, restore, Banner-aware autostart and
the WALL diagnostics taxonomy. It adds new production Java, three JVM suites, this gate,
the Phase C report and the planner task tree, and it legitimately edits eight existing
production files plus the inherited whole-repository inventory gates so they admit the
Phase C growth. This helper exposes the exact finite Phase C inventory, every whole-file
admission and its unique inverse, chaining AFTER Phase B (M6 C -> M6 B -> reconciliation
-> Sony closure -> D -> C -> B(M5) -> M4). Each inverse restores the exact pre-Phase-C
byte (frozen 2d2c22a for production, the Phase-B-era byte for the inherited gates); it
never rewrites or weakens an older baseline, deletes a test, skips, or admits a wildcard.
"""

ROOT = Path(__file__).resolve().parents[1]
INVENTORY = ROOT / '.github/scripts/m6-phase-c-baseline.json'
STARTING_HEAD = '2d2c22ac2926531ce728575ab7b87a4b5a66fd99'
JAVA = 'app/src/main/java/com/scenevibe/tvcompanionpoc/'
AUTHORIZED_CHANGES = frozenset({
    JAVA + 'AutostartPolicy.java',
    JAVA + 'BootReceiver.java',
    JAVA + 'DiagnosticsActivity.java',
    JAVA + 'DiagnosticsStore.java',
    JAVA + 'OverlayService.java',
    JAVA + 'RuntimeDiagnostics.java',
    JAVA + 'installation/InstallationHandlerRegistry.java',
    JAVA + 'installation/TvCapabilities.java',
})
PRODUCTION_ADDED = frozenset({
    JAVA + 'AndroidWallClockSource.java',
    JAVA + 'AndroidWallSignalReceiver.java',
    JAVA + 'AndroidWallWaitScheduler.java',
    JAVA + 'BannerInstallationHandler.java',
    JAVA + 'BannerInstallationRuntimePorts.java',
    JAVA + 'BannerOverlayManifestBridge.java',
    JAVA + 'BannerPreparedState.java',
    JAVA + 'BannerProfileParser.java',
    JAVA + 'LiveBannerRuntimePorts.java',
    JAVA + 'OverlayInstallationHandlers.java',
    JAVA + 'OverlayRuntimePorts.java',
    JAVA + 'WallClockDriver.java',
    JAVA + 'WallClockSource.java',
    JAVA + 'WallDriverDiagnostics.java',
    JAVA + 'WallWaitScheduler.java',
})
INHERITED_GATES = frozenset({
    'tests/test_m4_phase_b_boundary.py',
    'tests/test_m4_phase_c_boundary.py',
    'tests/test_m4_phase_d_boundary.py',
    'tests/test_m4_phase_e_boundary.py',
    'tests/test_m4_phase_f_boundary.py',
    'tests/test_m4_phase_g_boundary.py',
    'tests/test_m4_sony_corrective_boundary.py',
    'tests/test_m5_phase_b_boundary.py',
    'tests/test_m5_phase_c_boundary.py',
    'tests/test_m5_phase_d_boundary.py',
    'tests/test_m6_phase_b_boundary.py',
    'tests/m5_phase_d_provenance.py',
})


def blob_hash(content):
    """Compute the complete Git blob identity, including documentation and whitespace."""
    return hashlib.sha1(b'blob ' + str(len(content)).encode('ascii') + b'\0' + content).hexdigest()


def inventory():
    """Require the independent whole-file admissions and finite additions before any use."""
    data = json.loads(INVENTORY.read_text(encoding='utf-8'))
    if data['startingHead'] != STARTING_HEAD:
        raise ValueError('Unknown M6 C starting head')
    production = {path for path, item in data['admissions'].items() if item['kind'] == 'production'}
    gates = {path for path, item in data['admissions'].items() if item['kind'] == 'inherited-gate'}
    if production != set(AUTHORIZED_CHANGES):
        raise ValueError('Unknown M6 C production admissions')
    if gates != set(INHERITED_GATES):
        raise ValueError('Unknown M6 C inherited-gate admissions')
    if set(data['productionAdded']) != set(PRODUCTION_ADDED):
        raise ValueError('Unknown M6 C production additions')
    return data


def m6_phase_c_added_paths():
    """Return the finite literal Phase C additions, never a namespace or filename wildcard."""
    return set(inventory()['additiveFiles'])


def m6_phase_c_production_files():
    """Return only the newly added Phase C production Java paths, never a package wildcard."""
    return set(PRODUCTION_ADDED)


def m6_phase_c_authorized_changes():
    """Return the eight existing production files Phase C legitimately edits, pinned with inverses."""
    return set(AUTHORIZED_CHANGES)


def restore_blob(relative, content):
    """Reverse one recognized whole blob through its unique inverse to the exact pre-Phase-C byte."""
    admission = inventory()['admissions'].get(relative)
    if admission is None:
        return content
    digest = blob_hash(content)
    if digest == admission['beforeSha']:
        return content
    if digest != admission['afterSha']:
        raise ValueError('Unknown M6 C admission blob')
    source = content.decode('utf-8')
    for patch in reversed(admission['patches']):
        if source.count(patch['after']) != 1:
            raise ValueError('Missing or ambiguous M6 C inverse context')
        source = source.replace(patch['after'], patch['before'], 1)
    previous = source.encode('utf-8')
    if blob_hash(previous) != admission['beforeSha']:
        raise ValueError('M6 C inverse differs from the exact pre-Phase-C blob')
    return previous


def m6_phase_c_retained_bytes(path):
    """Read actual bytes then reverse only the recognized Phase C edits to the pre-Phase-C byte.

    For every other path the current bytes are returned unchanged, so the inherited chain
    reverses its own historical edits exactly as before. Only provenance checks call this;
    behavioural and compilation checks still execute the actual current source.
    """
    path = Path(path)
    relative = str(path.relative_to(ROOT))
    return restore_blob(relative, path.read_bytes())
