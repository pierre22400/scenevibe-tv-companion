from pathlib import Path
import argparse
import hashlib
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

"""
Authorize the finite owner-reviewed local 008 delta over both exact GitHub bases.
Historical seals stay byte-identical. Qualification reads actual raw reports and
refuses hook failures, missing jobs, skips or a substituted legacy green result.
No command in this gate contacts a remote or changes a repository or device.
"""

ANDROID_BASE = 'fdc5f6d5f17a19ef775422bf0b6e63454cc0fbbb'
ANDROID_TREE = 'c6c9dfd63aa46f96619bf9d9c42d90a77f90c467'
CLOUD_BASE = 'd062ba4c34a00729bfe2512723ae0c356f8ecc60'
CLOUD_TREE = '553e5f6e34e6c632b3ce076996f52b9c2ad252f3'
MANIFEST_SHA256 = '2946f992eaa731b034183be0df0371e02889bf65368b911d6891255c14c1a7af'
PREFIX = 'qualification/tvfirst008/'
ROOT = Path(__file__).resolve().parents[2]


class GateError(Exception):
    """Represent a bounded qualification refusal without falling back to another profile."""


def require(condition, message):
    """Keep fail-closed checks active even when Python assertions would be disabled."""
    if not condition:
        raise GateError(message)


def git(root, *arguments):
    """Read only local Git objects; never fetch, commit, push or run hooks."""
    return subprocess.check_output(['git', '-C', str(root), *arguments], stderr=subprocess.PIPE)


def sha(data):
    """Hash exact bytes without line-ending or JSON normalization."""
    return hashlib.sha256(data).hexdigest()


def normalized(path, data):
    """Break only this gate's manifest self-reference; all other verifier bytes remain sealed."""
    if path != PREFIX + 'gate.py':
        return data
    changed, count = re.subn(rb"(?m)^MANIFEST_SHA256 = '[0-9a-f]{64}'$",
                            b"MANIFEST_SHA256 = '" + b'0' * 64 + b"'", data)
    require(count == 1, 'Invalid manifest declaration')
    return changed


def tree(root, revision):
    """Read a complete committed tree with ordinary source modes only."""
    entries = {}
    for item in git(root, 'ls-tree', '-rz', revision).split(b'\0'):
        if item:
            info, path = item.split(b'\t')
            mode, kind, blob = info.decode().split()
            require(mode == '100644' and kind == 'blob', 'Unexpected Git mode')
            entries[path.decode()] = blob
    return entries


def verify_repository(root, name, base, base_tree, files):
    """Require one isolated provenance commit containing exactly the declared delta and retained bytes."""
    root = Path(root).resolve()
    require(git(root, 'rev-parse', base + '^{tree}').decode().strip() == base_tree, name + ': wrong BASE tree')
    head = git(root, 'rev-parse', 'HEAD').decode().strip()
    require(git(root, 'rev-parse', 'HEAD^').decode().strip() == base, name + ': not a direct isolated BASE descendant')
    current, original = tree(root, head), tree(root, base)
    expected = set(original) | {path for path, value in files.items() if value['status'] == 'added'}
    if name == 'android':
        expected.add(PREFIX + 'manifest.json')
    require(set(current) == expected, name + ': unexpected source inventory')
    require(not git(root, 'status', '--porcelain', '--untracked-files=all'), name + ': dirty or undeclared source')
    changed = {path for path in current if current[path] != original.get(path)}
    require(changed == set(files) | ({PREFIX + 'manifest.json'} if name == 'android' else set()), name + ': undeclared delta')
    for path, blob in current.items():
        location = root / path
        require(not location.is_symlink(), 'Symlink source')
        require(location.read_bytes() == git(root, 'cat-file', 'blob', blob), 'Working source differs from HEAD: ' + path)
        if path in files:
            value = files[path]
            require(not path.startswith('.github/'), 'Remote workflow mutation outside 008 local scope')
            require(value['baseBlob'] == original.get(path), 'BASE blob mismatch: ' + path)
            require(value['status'] == ('modified' if path in original else 'added'), 'Delta status mismatch')
            require(sha(normalized(path, location.read_bytes())) == value['sha256'], 'Delta bytes changed: ' + path)
            if path in original and path.endswith(('.java', '.ts', '.tsx')):
                old = git(root, 'cat-file', 'blob', original[path])
                require(all(comment in location.read_bytes() for comment in re.findall(rb'/\*\*.*?\*/', old, re.S)),
                        'Historical docstring removed: ' + path)
        else:
            require(path not in original or blob == original[path], 'Retained source changed')
    return {'head': head, 'tree': git(root, 'rev-parse', 'HEAD^{tree}').decode().strip(), 'base': base,
            'baseTree': base_tree, 'files': len(current), 'modified': sum(v['status'] == 'modified' for v in files.values()),
            'added': sum(v['status'] == 'added' for v in files.values()) + (1 if name == 'android' else 0)}


def vitest(path, minimum):
    """Reject skipped cases and beforeAll/suite errors even when numFailedTests is zero."""
    result = json.loads(Path(path).read_text())
    require(result.get('success') is True and result.get('numFailedTestSuites') == 0 and result.get('numFailedTests') == 0,
            'Vitest failure or hook error: ' + Path(path).name)
    require(result.get('numPendingTests') == 0 and result.get('numTodoTests') == 0, 'Vitest skip or todo')
    cases = [case for suite in result['testResults'] for case in suite['assertionResults']]
    require(all(suite['status'] == 'passed' for suite in result['testResults']), 'Failed Vitest suite')
    require(len(cases) == result['numTotalTests'] == result['numPassedTests'] and len(cases) >= minimum, 'Vitest case inventory mismatch')
    require(all(case['status'] == 'passed' for case in cases), 'Unexecuted Vitest case')
    return len(cases)


def junit(directory, expected=None, allowed_skips=()):
    """Inspect actual case-level XML and independently identify every historical skipped case."""
    suites, skipped = {}, []
    for path in sorted(Path(directory).glob('TEST-*.xml')):
        suite = ET.parse(path).getroot()
        name, cases = suite.get('name'), suite.findall('testcase')
        short = name.rsplit('.', 1)[-1]
        require(short not in suites and cases and len(cases) == int(suite.get('tests')), 'JUnit suite inventory mismatch')
        require(int(suite.get('failures', 0)) == 0 and int(suite.get('errors', 0)) == 0, 'JUnit suite failed')
        require(len({case.get('name') for case in cases}) == len(cases), 'Duplicate JUnit case')
        suite_skips = []
        for case in cases:
            require(case.find('failure') is None and case.find('error') is None, 'JUnit case failed')
            if case.find('skipped') is not None:
                suite_skips.append(name + '.' + case.get('name'))
        require(len(suite_skips) == int(suite.get('skipped', 0)), 'JUnit skip counter mismatch')
        skipped.extend(suite_skips)
        suites[short] = len(cases)
    require(suites, 'Missing JUnit reports')
    require(sorted(skipped) == sorted(allowed_skips), 'Unexpected JUnit skip')
    if expected is not None:
        require(suites == expected, 'JUnit qualification profile/case count changed')
    return {'tests': sum(suites.values()), 'passed': sum(suites.values()) - len(skipped), 'skipped': skipped}


def qualification(evidence):
    """Combine independent current-source results; historical retention never substitutes for 008."""
    evidence = Path(evidence)
    cloud = vitest(evidence / 'cloud-native-final.json', 237)
    peer = vitest(evidence / 'tvfirst008-native-junction-final.json', 1)
    current = junit(evidence / 'android008-xml', {'TvFirst008BoundaryTest': 10, 'TvFirst008CacheTest': 12,
                                               'TvFirst008IdentityTest': 8, 'TvFirst008JourneyTest': 14, 'TvFirst008HttpTest': 6})
    observations = json.loads((evidence / 'tvfirst008-junction-observations.json').read_text())
    require(observations['status'] == 0 and observations['legacyAcknowledgements'] == 0 and observations['cacheReceipts'] > 0,
            'Cache/legacy-ACK junction failed')
    retained = json.loads((evidence / 'frozen-retention/python-retention.json').read_text())
    require(retained['tests'] == retained['pass'] == 118 and retained['skip'] == 0 and retained['oldSealsModified'] is False,
            'Frozen historical retention failed')
    historical = json.loads((evidence / 'current-regression/jvm-current.json').read_text())
    require(historical['debug']['pass'] == 1127 and historical['observation']['pass'] == 1135,
            'Current historical regression incomplete')
    commands = json.loads((evidence / 'command-results.json').read_text())
    for name in ['android-final', 'cloud-typecheck-final', 'web-build-final', 'web-closed-runtime', 'frozen006-retention']:
        require(any(row['name'] == name and row['exit_code'] == 0 for row in commands), 'Missing command success: ' + name)
    closed = json.loads((evidence / 'web-closed-runtime.json').read_text())
    require(closed['pageStatus'] == 200 and closed['closedMessage'] and closed['apiStatus'] == 503, 'Default-closed Next runtime failed')
    lint = ET.parse(evidence / 'lint-results-tvfirst008.xml').getroot()
    require(not any(issue.get('severity') in ('Fatal', 'Error') for issue in lint.findall('issue')), 'Android lint failed')
    return {'cloudCases': cloud, 'nativeJunctionCases': peer, 'android008': current, 'historicalPython': 118,
            'historicalDebugPass': 1127, 'historicalObservationPass': 1135, 'realClerk': 'NOT_EXECUTED',
            'sony': 'NOT_EXECUTED', 'media': 'NO_GO', 'rendering': 'FORBIDDEN'}


def verify(android, cloud, evidence=None):
    """Authenticate the exact dual-repository manifest before considering any test result."""
    require(sys.flags.optimize == 0, 'Run without python -O')
    raw = (Path(android) / PREFIX / 'manifest.json').read_bytes()
    require(sha(raw) == MANIFEST_SHA256, '008 manifest changed')
    manifest = json.loads(raw)
    require(manifest['profile'] == 'tvfirst008-cache-v1', 'Wrong qualification profile')
    result = {'verdict': 'PASS', 'scope': 'LOCAL_SOURCE' if evidence is None else 'LOCAL_SOFTWARE'}
    for name, root, base, base_tree in [('android', android, ANDROID_BASE, ANDROID_TREE), ('cloud', cloud, CLOUD_BASE, CLOUD_TREE)]:
        descriptor = manifest[name]
        require(descriptor['base'] == base and descriptor['baseTree'] == base_tree, 'Manifest BASE drift')
        result[name] = verify_repository(root, name, base, base_tree, descriptor['files'])
    if evidence is not None:
        result['qualification'] = qualification(evidence)
    result.update(oldSealsModified=False, remoteMutation=False, physicalQualification=False)
    return result


def main():
    """Print a nonzero refusal on every missing or failed gate, never a legacy PASS fallback."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--android', type=Path, default=ROOT)
    parser.add_argument('--cloud', type=Path, required=True)
    parser.add_argument('--evidence', type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.android, args.cloud, args.evidence), indent=2))
        return 0
    except (GateError, OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        print(json.dumps({'verdict': 'FAIL', 'reason': str(error)}))
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
