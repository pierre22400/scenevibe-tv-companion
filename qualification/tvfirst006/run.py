from pathlib import Path
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from gate import BASE, ROOT, GateError, git, require, sha, verify

"""Qualify the real 006 source while keeping immutable historical retention separately labelled."""

REPORTS = ROOT / 'build/tvfirst006'
HISTORICAL = '17cbe36ae99ac7f48aaf861e0d1feac702a9e521'
PRESENTATION = {'TvNativeViewsTest': 16, 'TvPreparationTest': 8, 'TvFocusSearchTest': 5, 'TvNavigationTest': 9}
COMMON = {'MediaEvidenceTest': 15, 'ObservationDiagnosticsTest': 4, 'PassiveMediaReaderTest': 18}
SERVICE = {'debug': {'TvFirstDefaultServiceTest': 2}, 'observation': {'TvFirstServiceObservationTest': 26}}


def write(name, result):
    """Save one result atomically enough for local CI and never preserve an obsolete PASS receipt."""
    (REPORTS / (name + '.json')).write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({name: result}, indent=2), flush=True)


def process(command, cwd, log):
    """Execute a bounded local check and retain its entire stdout/stderr without filtering failures."""
    with (REPORTS / log).open('w') as output:
        result = subprocess.run(command, cwd=cwd, stdout=output, stderr=subprocess.STDOUT)
    require(result.returncode == 0, 'Command failed; see ' + log)


def retained_view(destination):
    """Reconstruct the exact 276-file historical assertion view, only after the frozen 003 gate passed."""
    paths = git(ROOT, 'ls-tree', '-r', '--name-only', HISTORICAL).decode().splitlines()
    require(len(paths) == 276, 'Wrong retained Python view inventory')
    for path in paths:
        target = destination / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(git(ROOT, 'show', HISTORICAL + ':' + path))


def retention():
    """Authorize the full frozen 003 tree before constructing or executing any historical projection."""
    with tempfile.TemporaryDirectory(prefix='tvfirst006-retention-') as directory:
        frozen = Path(directory) / 'frozen003'
        subprocess.run(['git', 'clone', '--quiet', '--shared', '--no-checkout', str(ROOT), str(frozen)], check=True)
        subprocess.run(['git', '-C', str(frozen), 'checkout', '--quiet', '--detach', BASE], check=True)
        process([sys.executable, '-B', 'qualification/tvfirst003/gate.py', '--expected-head', BASE],
                frozen, 'frozen003-gate.log')
        process([sys.executable, '-B', 'qualification/tvfirst003/test_gate.py', '-v'], frozen,
                'frozen003-gate-controls.log')
        view = Path(directory) / 'python276'
        retained_view(view)
        process([sys.executable, '-B', '-m', 'unittest', 'discover', '-s', 'tests', '-v'], view,
                'python-retention.log')
    log = (REPORTS / 'python-retention.log').read_text()
    require(re.search(r'Ran 118 tests\b', log) and '\nOK\n' in log, 'Retained Python count/result changed')
    return {'scope': 'EXACT 276-FILE HISTORICAL VIEW; NOT CURRENT SOURCE', 'tests': 118,
            'pass': 118, 'skip': 0, 'frozen003Head': BASE, 'frozen003Controls': 8, 'oldSealsModified': False}


def cases(directory):
    """Count actual executed JUnit cases and reject failures, duplicate suites, missing cases or silent skips."""
    suites, skipped = {}, []
    for path in sorted(directory.glob('TEST-*.xml')):
        suite = ET.parse(path).getroot()
        name = suite.attrib['name']
        items = suite.findall('testcase')
        require(name not in suites and items and len(items) == int(suite.attrib['tests']),
                'Invalid JUnit suite inventory: ' + name)
        require(not int(suite.attrib.get('failures', 0)) and not int(suite.attrib.get('errors', 0)),
                'JUnit suite failed: ' + name)
        require(not any(c.find('failure') is not None or c.find('error') is not None for c in items),
                'JUnit testcase failed: ' + name)
        require(len({c.attrib['name'] for c in items}) == len(items), 'Duplicate JUnit testcase: ' + name)
        actual = [name + '.' + c.attrib['name'] for c in items if c.find('skipped') is not None]
        require(len(actual) == int(suite.attrib.get('skipped', 0)), 'JUnit skip mismatch: ' + name)
        skipped.extend(actual)
        suites[name] = len(items)
    require(suites, 'Missing current JUnit reports: ' + str(directory))
    return suites, skipped


def historical_jvm(current, new):
    """Run the unchanged historical JVM counter against its original bucket of current compiled test results."""
    with tempfile.TemporaryDirectory(prefix='tvfirst006-jvm-bucket-') as directory:
        view = Path(directory)
        retained_view(view)
        reports = view / 'app/build/test-results/testDebugUnitTest'
        reports.mkdir(parents=True)
        for path in current.glob('TEST-*.xml'):
            if ET.parse(path).getroot().attrib['name'] not in new:
                shutil.copyfile(path, reports / path.name)
        journal = view / 'app/build/reports'
        journal.mkdir(parents=True)
        for name in ['m5-phase-b-oracle-traces.json', 'm5-phase-c-differential.json']:
            shutil.copyfile(ROOT / 'app/build/reports' / name, journal / name)
        process([sys.executable, '-B', str(view / '.github/scripts/m4-phase-a-test-summary.py')], view,
                'historical-jvm-summary.log')
        result = json.loads((journal / 'm4-phase-a-summary.json').read_text())
        shutil.copyfile(journal / 'm4-phase-a-summary.json', REPORTS / 'historical-jvm-summary.json')
    require(result['jvm'] == {'PASS': 1050, 'FAIL': 0, 'SKIP': 1, 'TOTAL': 1051},
            'Historical current JVM bucket changed')
    return result


def jvm():
    """Require the complete historical suites plus every declared new suite on both actual service profiles."""
    debug_path = ROOT / 'app/build/test-results/testDebugUnitTest'
    debug, debug_skips = cases(debug_path)
    added_debug = {n: c for n, c in debug.items() if '.presentation.' in n or '.tvfirst.' in n or 'TvFirst' in n}
    expected_debug = PRESENTATION | COMMON | SERVICE['debug']
    require({n.rsplit('.', 1)[-1]: c for n, c in added_debug.items()} == expected_debug,
            'Current debug new-suite inventory/count changed')
    historical_jvm(debug_path, added_debug)
    baseline = {n: c for n, c in debug.items() if n not in added_debug}
    require(len(debug_skips) == 1, 'Current debug must retain exactly the historical skip')
    observation, observation_skips = cases(ROOT / 'app/build/test-results/testObservationUnitTest')
    added_observation = {n: c for n, c in observation.items() if '.tvfirst.' in n or 'TvFirst' in n}
    presentation_observation = {n: c for n, c in observation.items() if '.presentation.' in n}
    require({n.rsplit('.', 1)[-1]: c for n, c in presentation_observation.items()} ==
            {n: c for n, c in PRESENTATION.items() if n != 'TvNativeViewsTest'},
            'Observation common-presentation inventory/count changed')
    require({n.rsplit('.', 1)[-1]: c for n, c in added_observation.items()} == COMMON | SERVICE['observation'],
            'Observation new-suite inventory/count changed')
    require({n: c for n, c in observation.items() if n not in added_observation and n not in presentation_observation} == baseline,
            'Observation profile omitted or changed a historical common JVM suite')
    require(observation_skips == debug_skips, 'Observation added a skip or changed its context')
    release, release_skips = cases(ROOT / 'app/build/test-results/testReleaseUnitTest')
    require(release == {'com.scenevibe.tvcompanionpoc.presentation.TvReleaseSourceTest': 1} and not release_skips,
            'Release source closure test missing or unexpected')
    return {'scope': 'ACTUAL CURRENT COMPILED SOURCE', 'historicalCasesPerFullProfile': 1051,
            'presentationDebugCases': 38, 'presentationObservationCases': 22,
            'new006DebugCases': sum(COMMON.values()) + 2,
            'new006ObservationCases': sum(COMMON.values()) + sum(SERVICE['observation'].values()),
            'debug': {'tests': sum(debug.values()), 'pass': sum(debug.values()) - 1, 'skip': debug_skips, 'suites': debug},
            'observation': {'tests': sum(observation.values()), 'pass': sum(observation.values()) - 1,
                            'skip': observation_skips, 'suites': observation},
            'releaseClosure': {'tests': 1, 'pass': 1}}


def packaging():
    """Inspect actual unsigned APK intermediates and generated constants; never publish or sign an APK."""
    result = {}
    for variant in ['debug', 'release', 'observation']:
        directory = ROOT / 'app/build/outputs/apk' / variant
        apks = list(directory.glob('*.apk'))
        require(len(apks) == 1, 'Missing or ambiguous unsigned packaging: ' + variant)
        path = apks[0]
        require(b'APK Sig Block 42' not in path.read_bytes(), 'APK signing block in qualification output: ' + variant)
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            demo = 'assets/tvfirst002/demo-offers.json' in names
            dex = b''.join(archive.read(n) for n in names if re.fullmatch(r'classes\d*\.dex', n))
            demo_class = b'TvSourceFactory$LocalDemo;' in dex
            require(demo == (variant == 'debug') and demo_class == (variant == 'debug'),
                    'Demo fixture/source escaped debug: ' + variant)
            require(b'synthetic-native-id' not in dex and b'synthetic-test-session' not in dex,
                    'MEDIA test fixture escaped test source: ' + variant)
            require(not any(n.startswith('META-INF/') and n.endswith(('.RSA', '.DSA', '.EC')) for n in names),
                    'Signing certificate in qualification output: ' + variant)
        config = (ROOT / 'app/build/generated/source/buildConfig' / variant /
                  'com/scenevibe/tvcompanionpoc/BuildConfig.java').read_text()
        require('CLOUD_ORIGIN = "";' in config and 'ENABLE_LAN_DEV = false;' in config,
                'Cloud or LAN enabled: ' + variant)
        enabled = variant == 'observation'
        require('TVFIRST_OBSERVATION_ONLY = ' + str(enabled).lower() + ';' in config,
                'Wrong observation flag: ' + variant)
        metadata = json.loads((directory / 'output-metadata.json').read_text())
        require(metadata['applicationId'] == 'com.scenevibe.tvcompanionpoc', 'Application identity drift')
        result[variant] = {'sha256': sha(path.read_bytes()), 'observationOnly': enabled, 'cloudOrigin': '',
                           'lanDevEnabled': False, 'demoFixturePresent': demo, 'demoClassPresent': demo_class,
                           'signed': False, 'physicalCandidate': False, 'published': False}
    return result


def gradle(tasks, log):
    """Build/test only the current checkout with unsigned output, no transport, and fresh task execution."""
    env = os.environ.copy()
    env.update(SCENEVIBE_CLOUD_ORIGIN='', SCENEVIBE_ENABLE_LAN_DEV='false')
    command = [env.get('SCENEVIBE_GRADLE', 'gradle'), '--no-daemon', '--console=plain', '--no-build-cache',
               '--rerun-tasks', '--max-workers=2', '-PscenevibeQualificationUnsigned=true']
    with (REPORTS / log).open('w') as output:
        result = subprocess.run(command + tasks, cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
    require(result.returncode == 0, 'Current source Gradle failed; see ' + log)


def execute(phase):
    """Authorize before retention or Gradle, then keep current-code and historical evidence distinct."""
    write('integrity', verify(expected_head=os.environ.get('EXPECTED_HEAD')))
    if phase in ('all', 'python'):
        write('python-retention', retention())
    if phase == 'all':
        require(not (ROOT / 'app/build').exists() and not (ROOT / '.gradle').exists(),
                'Full qualification requires a fresh checkout without app/build or .gradle')
        process([sys.executable, '-B', 'qualification/tvfirst006/test_gate.py', '-v'], ROOT, 'gate-controls.log')
        gradle([':app:assembleDebug', ':app:assembleRelease', ':app:assembleObservation'], 'gradle-packaging.log')
        write('packaging', packaging())
        gradle([':app:testDebugUnitTest', ':app:lintDebug', ':app:lintRelease', ':app:lintObservation'], 'gradle-debug-lint.log')
        gradle([':app:testObservationUnitTest'], 'gradle-observation.log')
        gradle([':app:testReleaseUnitTest', '--tests', '*.presentation.TvReleaseSourceTest'], 'gradle-release-closure.log')
    if phase in ('all', 'jvm'):
        # This phase is only reachable after the frozen-tree check above in a full run.
        if phase == 'jvm':
            write('python-retention', retention())
        write('jvm-current', jvm())
    if phase == 'packaging':
        write('packaging', packaging())
    write('result', {'verdict': 'PASS', 'scope': phase, 'head': verify()['head'], 'signed': False,
                     'published': False, 'physicalCandidate': False, 'sonyQualification': False})


def main():
    """Overwrite any previous result with FAIL whenever a requested step fails; no historical fallback exists."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase', choices=['all', 'python', 'jvm', 'packaging'])
    args = parser.parse_args()
    REPORTS.mkdir(parents=True, exist_ok=True)
    write('result', {'verdict': 'FAIL', 'scope': args.phase, 'reason': 'Qualification not completed'})
    try:
        execute(args.phase)
        return 0
    except (GateError, OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        write('result', {'verdict': 'FAIL', 'scope': args.phase, 'reason': str(error)})
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
