"""Run explicitly separated retention, current JVM and release-packaging qualification."""

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
from gate import GateError, BASE, CI_EXCEPTION, APP_EDITS, ROOT, git, require, sha, verify

# No historical assertion is edited, suppressed or converted into a pass. Python
# executes the exact retained BASE view. JVM reports come from the actual current
# build. Only the frozen summary's input view separates its legacy XML bucket;
# all current XML suites are accounted for independently before that invocation.
NEW_DEBUG = {'TvNativeViewsTest': 16, 'TvPreparationTest': 8,
             'TvFocusSearchTest': 5, 'TvNavigationTest': 9}
REPORTS = ROOT / 'build/tvfirst003'


def retained_view(destination):
    """Reconstruct all 276 original paths after the current full-tree authorization passes."""
    count = 0
    for name in git(ROOT, 'ls-tree', '-r', '--name-only', BASE).decode().splitlines():
        original = git(ROOT, 'show', BASE + ':' + name)
        current = (ROOT / name).read_bytes()
        data = original if name in APP_EDITS | {CI_EXCEPTION} else current
        require(data == original, 'Retention bytes differ from BASE: ' + name)
        target = destination / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        count += 1
    require(count == 276, 'Incomplete historical view')


def run_python():
    """Execute every historical Python assertion without filtering and label its retained scope."""
    verify()
    with tempfile.TemporaryDirectory(prefix='scenevibe-tvfirst003-retained-') as directory:
        retained_view(Path(directory))
        process = subprocess.run([sys.executable, '-B', '-m', 'unittest', 'discover', '-s', 'tests', '-v'],
                                 cwd=directory, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    (REPORTS / 'python-retention.log').write_text(process.stdout)
    print(process.stdout, end='', flush=True)
    require(process.returncode == 0, 'Historical Python retention failed; see python-retention.log')
    require(re.search(r'Ran 118 tests\b', process.stdout) and '\nOK\n' in process.stdout,
            'Historical Python did not execute exactly 118 passing cases')
    return {'scope': 'EXACT BASE RETENTION — NOT CURRENT UI', 'tests': 118, 'pass': 118,
            'fail': 0, 'error': 0, 'skip': 0, 'viewFiles': 276}


def cases(directory):
    """Read actual JUnit XML, reject empty/duplicate suites and count every executed testcase."""
    suites = {}
    skipped = []
    for path in sorted(directory.glob('TEST-*.xml')):
        suite = ET.parse(path).getroot()
        name = suite.attrib['name']
        require(name not in suites, 'Duplicate JUnit suite: ' + name)
        items = suite.findall('testcase')
        require(items and len(items) == int(suite.attrib['tests']), 'Incomplete JUnit suite: ' + name)
        require(int(suite.attrib.get('errors', '0')) == 0 and int(suite.attrib.get('failures', '0')) == 0,
                'JUnit reported failures: ' + name)
        require(not any(c.find('failure') is not None or c.find('error') is not None for c in items),
                'Failed JUnit testcase: ' + name)
        actual_skips = [name.rsplit('.', 1)[-1] + '.' + c.attrib['name']
                        for c in items if c.find('skipped') is not None]
        require(len(actual_skips) == int(suite.attrib.get('skipped', '0')), 'JUnit skip count mismatch')
        require(len({c.attrib['name'] for c in items}) == len(items), 'Duplicate JUnit testcase: ' + name)
        skipped.extend(actual_skips)
        suites[name] = len(items)
    require(suites, 'Missing current JUnit XML in ' + str(directory))
    return suites, skipped


def run_jvm_summary():
    """Validate all current JVM reports and call the unchanged historical summary on its exact bucket."""
    verify()
    current = ROOT / 'app/build/test-results/testDebugUnitTest'
    suites, skipped = cases(current)
    new = {name: count for name, count in suites.items() if '.presentation.' in name}
    require({name.rsplit('.', 1)[-1]: count for name, count in new.items()} == NEW_DEBUG,
            'Presentation suite inventory/counts changed')
    with tempfile.TemporaryDirectory(prefix='scenevibe-tvfirst003-jvm-retained-') as directory:
        destination = Path(directory)
        retained_view(destination)
        reports = destination / 'app/build/test-results/testDebugUnitTest'
        reports.mkdir(parents=True)
        for path in current.glob('TEST-*.xml'):
            if ET.parse(path).getroot().attrib['name'] not in new:
                shutil.copyfile(path, reports / path.name)
        journal = destination / 'app/build/reports'
        journal.mkdir(parents=True)
        for name in ['m5-phase-b-oracle-traces.json', 'm5-phase-c-differential.json']:
            shutil.copyfile(ROOT / 'app/build/reports' / name, journal / name)
        script = destination / '.github/scripts/m4-phase-a-test-summary.py'
        require(script.read_bytes() == git(ROOT, 'show', BASE + ':.github/scripts/m4-phase-a-test-summary.py'),
                'Historical JVM summary changed')
        process = subprocess.run([sys.executable, '-B', str(script)], stdout=subprocess.PIPE,
                                 stderr=subprocess.STDOUT, text=True)
        (REPORTS / 'historical-jvm-summary.log').write_text(process.stdout)
        require(process.returncode == 0, 'Unchanged historical JVM summary failed')
        retained = json.loads((journal / 'm4-phase-a-summary.json').read_text())
        shutil.copyfile(journal / 'm4-phase-a-summary.json', REPORTS / 'historical-jvm-summary.json')
    require(retained['jvm'] == {'PASS': 1050, 'FAIL': 0, 'SKIP': 1, 'TOTAL': 1051},
            'Historical executed JVM result changed')
    require(sum(suites.values()) == 1089 and len(skipped) == 1, 'Current JVM totals changed')
    release_suites, release_skipped = cases(ROOT / 'app/build/test-results/testReleaseUnitTest')
    require(release_suites == {'com.scenevibe.tvcompanionpoc.presentation.TvReleaseSourceTest': 1}
            and not release_skipped, 'Release closure test missing or unexpected')
    return {'scope': 'ACTUAL CURRENT COMPILED SOURCE', 'debug': {'tests': 1089, 'pass': 1088,
            'fail': 0, 'error': 0, 'skip': skipped}, 'historicalDebugCases': 1051,
            'newDebugCases': 38, 'releaseClosure': {'tests': 1, 'pass': 1}, 'suites': suites}


def packaging():
    """Check fixture/source exclusion independently in the actual unsigned release build."""
    verify()
    result = {}
    for variant, filename in [('debug', 'app-debug.apk'), ('release', 'app-release-unsigned.apk')]:
        path = ROOT / 'app/build/outputs/apk' / variant / filename
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            demo = 'assets/tvfirst002/demo-offers.json' in names
            demo_class = any(b'TvSourceFactory$LocalDemo;' in archive.read(n) for n in names
                             if re.fullmatch(r'classes\d*\.dex', n))
            require(demo == (variant == 'debug') and demo_class == (variant == 'debug'),
                    'Fixture/source packaging violation in ' + variant)
            if variant == 'release':
                require(not any(n.startswith('META-INF/') and n.endswith(('.RSA', '.DSA', '.EC')) for n in names),
                        'Release unexpectedly contains a signing certificate')
        config = (ROOT / 'app/build/generated/source/buildConfig' / variant /
                  'com/scenevibe/tvcompanionpoc/BuildConfig.java').read_text()
        require('CLOUD_ORIGIN = "";' in config and 'ENABLE_LAN_DEV = false;' in config,
                'Build configuration unexpectedly enables Cloud or LAN DEV')
        result[variant] = {'cloudOrigin': '', 'lanDevEnabled': False,
                           'sha256': sha(path.read_bytes()), 'demoFixturePresent': demo,
                           'demoClassPresent': demo_class, 'physicalCandidate': False}
    return result


def gradle(tasks):
    """Run current-checkout Gradle with Cloud disabled, fresh execution and no build-cache substitution."""
    env = os.environ.copy()
    env.update(SCENEVIBE_CLOUD_ORIGIN='', SCENEVIBE_ENABLE_LAN_DEV='false')
    command = [env.get('SCENEVIBE_GRADLE', 'gradle'), '--no-daemon', '--console=plain',
               '--no-build-cache', '--rerun-tasks', '--max-workers=2']
    init = env.get('SCENEVIBE_QUALIFICATION_INIT_SCRIPT')
    if init:
        command += ['--init-script', init]
    process = subprocess.run(command + tasks, cwd=ROOT, env=env)
    require(process.returncode == 0, 'Current-code Gradle failed: ' + ' '.join(tasks))


def write(name, result):
    """Persist one explicitly scoped result for CI artifact upload without including APKs."""
    (REPORTS / (name + '.json')).write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({name: result}, indent=2), flush=True)


def execute(phase):
    """Execute one requested phase without concealing a failed intermediate control."""
    write('integrity', verify())
    if phase in ('all', 'python'):
        write('python-retention', run_python())
    if phase == 'all':
        require(not (ROOT / 'app/build').exists() and not (ROOT / '.gradle').exists(),
                'Full qualification requires a fresh checkout, without app/build or .gradle')
        gradle([':app:assembleDebug', ':app:assembleRelease'])
        # Preserve the assembly result before Robolectric's native-resource execution.
        write('packaging', packaging())
        gradle([':app:testDebugUnitTest', ':app:lintDebug', ':app:lintRelease'])
        gradle([':app:testReleaseUnitTest', '--tests', '*.presentation.TvReleaseSourceTest'])
    if phase in ('all', 'jvm'):
        write('jvm-current', run_jvm_summary())
    if phase == 'packaging':
        write('packaging', packaging())
    if phase == 'all':
        # Reports are outputs of this run; no test filtering or expected-failure conversion.
        write('result', {'verdict': 'PASS', 'head': verify()['head'], 'cloudOriginConfigured': False, 'lanDevEnabled': False,
                         'apkPublished': False, 'sonyQualification': False})
    return 0


def main():
    """Expose separated phases and replace stale success receipts with an explicit failure."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase', choices=['all', 'python', 'jvm', 'packaging'])
    args = parser.parse_args()
    REPORTS.mkdir(parents=True, exist_ok=True)
    if args.phase == 'all':
        (REPORTS / 'result.json').unlink(missing_ok=True)
    try:
        return execute(args.phase)
    except (GateError, OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        write('result' if args.phase == 'all' else args.phase + '-failure',
              {'verdict': 'FAIL', 'requestedPhase': args.phase, 'reason': str(error)})
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
