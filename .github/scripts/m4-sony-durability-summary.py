import json
from pathlib import Path
import re
import sys

"""Count completed native storage scenarios from instrumentation output, never from source declarations.

Seed/reload must both PASS in different OS processes. Android 12 also proves the pinned old
backend reproduces exact XML corruption. No payload, raw preference or exception is exported.
These counts are independent Android evidence, not additional JVM cases or Sony qualification.
"""


def summarize(directory, sdk):
    """Validate the full native scenario inventory, both process ids and the old-build negative control."""
    profiles = ['generic-' + profile + '-' + ack for profile in ('manifested', 'legacy')
                for ack in ('confirmed', 'pending')]
    fixed = profiles + ['historical-manifested', 'historical-legacy', 'invalid-generic',
                        'ack-ahead-without-generic', 'empty', 'maximum', 'failed-commit']
    expected = [('fixed', name) for name in fixed]
    if sdk == '31':
        expected += [('baseline', name) for name in profiles]
    if sdk not in ('31', '35'):
        raise ValueError('Unexpected Android SDK')
    results = []
    for build, name in expected:
        phases = []
        for phase in ('seed', 'reload'):
            content = (directory / f'{build}-{name}-{phase}.txt').read_text()
            if not re.search(r'^INSTRUMENTATION_RESULT: result=PASS$', content, re.M):
                raise ValueError('Missing native PASS')
            if not re.search(r'^INSTRUMENTATION_CODE: -1$', content, re.M):
                raise ValueError('Missing instrumentation completion')
            pid = re.search(r'^INSTRUMENTATION_RESULT: pid=(\d+)$', content, re.M)
            if not pid:
                raise ValueError('Missing native process id')
            if build == 'baseline' and phase == 'reload' and 'checkpoint=BASELINE_CORRUPTION_REPRODUCED' not in content:
                raise ValueError('Missing pinned baseline failure proof')
            phases.append(int(pid.group(1)))
        if phases[0] == phases[1]:
            raise ValueError('A process-local view survived the reload')
        results.append({'build': build, 'scenario': name, 'result': 'PASS',
                        'seedPid': phases[0], 'reloadPid': phases[1]})
    if len(list(directory.glob('*.txt'))) != 2 * len(expected):
        raise ValueError('Unexpected native result inventory')
    summary = {'sdk': int(sdk), 'baselineHead': '824339de562c0de8542f4c1b2a22266832c8abef',
               'fixedScenarios': len(fixed), 'baselineNegativeControls': len(expected) - len(fixed),
               'instrumentationInvocations': len(expected) * 2, 'results': results,
               'sonyPhysicalRequalification': 'NOT PERFORMED'}
    (directory / 'native-durability-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    print(f'Native Android {sdk}: {len(fixed)} corrective PASS; {len(expected)-len(fixed)} pinned pre-fix corruption controls; {len(expected)*2} distinct-process invocations')


def main():
    """Use explicit disposable-runner result paths and fail closed on missing or inconsistent evidence."""
    summarize(Path(sys.argv[1]), sys.argv[2])


if __name__ == '__main__':
    main()
