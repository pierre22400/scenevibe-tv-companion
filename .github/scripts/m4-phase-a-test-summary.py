"""Count executed JVM cases without printing comments, payloads or failure messages."""

import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def summarize(root):
    """Check retained suites and fixtures, then summarize actual Gradle JUnit XML."""
    baseline = json.loads((root / '.github/scripts/m4-phase-a-baseline.json').read_text(encoding='utf-8'))
    counts = {'PASS': 0, 'FAIL': 0, 'SKIP': 0}
    suites = {}
    skipped = []
    reports = sorted((root / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
    if not reports:
        raise ValueError('No executed JVM reports found')
    for report in reports:
        suite = ET.parse(report).getroot()
        for case in suite.findall('testcase'):
            name = case.attrib['classname'].rsplit('.', 1)[-1]
            suites[name] = suites.get(name, 0) + 1
            if case.find('failure') is not None or case.find('error') is not None:
                counts['FAIL'] += 1
            elif case.find('skipped') is not None:
                counts['SKIP'] += 1
                skipped.append(name + '.' + case.attrib['name'])
            else:
                counts['PASS'] += 1
    expected = {**baseline['existingSuites'], **baseline['phaseASuites']}
    if suites != expected:
        raise ValueError('Executed suite names/counts differ from the frozen Phase A inventory')
    if any(name not in baseline['allowedOptInSkips'] for name in skipped):
        raise ValueError('An unexpected JVM case was skipped')
    for path, digest in baseline['fixtureSha256'].items():
        if hashlib.sha256((root / path).read_bytes()).hexdigest() != digest:
            raise ValueError('A frozen characterization fixture changed')
    counts['TOTAL'] = sum(counts.values())
    summary = {
        'referenceHead': baseline['referenceHead'],
        'jvm': counts,
        'retainedCases': sum(baseline['existingSuites'].values()),
        'phaseACases': sum(baseline['phaseASuites'].values()),
        'suites': suites,
        'skippedCases': skipped,
        'fixtureSha256': baseline['fixtureSha256'],
    }
    destination = root / 'app/build/reports/m4-phase-a-summary.json'
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
    print('JVM: ' + ' '.join(f'{name}={count}' for name, count in counts.items()))
    print(f'Retained baseline: {summary["retainedCases"]}; Phase A: {summary["phaseACases"]}')
    for name in skipped:
        print('Opt-in SKIP: ' + name)
    return 1 if counts['FAIL'] else 0


def main():
    """Use a repository-relative root so local Gradle and GitHub Actions share the same proof."""
    return summarize(Path(__file__).resolve().parents[2])


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, KeyError, OSError, ET.ParseError):
        print('Phase A report validation failed; no payload or exception message emitted', file=sys.stderr)
        sys.exit(1)
