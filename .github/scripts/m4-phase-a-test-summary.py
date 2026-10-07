"""Count executed JVM cases without printing comments, payloads or failure messages."""

import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

"""Read executed results rather than estimating them from source annotations.

The Phase A inventory and fixtures stay frozen. Phase B extends that inventory in
its own file, so adding pure models cannot silently drop an old characterization.
The historical output path/artifact remains compatible and now includes both phases.
Phase C adds its own executed bucket while retaining those frozen inventories.
Phase D adds an independent handler/differential/ARM bucket, without losing earlier counts.
Phase E adds executed orchestration, readback, real-Video and historical-cache buckets.
Phase F adds actual Cloud/owner/runtime/reset compatibility execution without dropping old cases.
Phase G adds startup/boot/metadata/reset and post-restore Cloud execution without changing prior buckets.
The Sony corrective cycle adds its own executed bucket; native disk/process evidence is separate.
M6 B adds only its separately inventoried pure WALL contracts; all M5 counts remain required.
M6 C adds only its separately inventoried WALL runtime suites; all earlier counts remain required.
"""


def summarize(root):
    """Check retained suites and fixtures, then summarize actual Gradle JUnit XML."""
    baseline = json.loads((root / '.github/scripts/m4-phase-a-baseline.json').read_text(encoding='utf-8'))
    phase_b_path = root / '.github/scripts/m4-phase-b-baseline.json'
    phase_b = json.loads(phase_b_path.read_text(encoding='utf-8')) if phase_b_path.exists() else {'phaseBSuites': {}}
    phase_c_path = root / '.github/scripts/m4-phase-c-baseline.json'
    phase_c = json.loads(phase_c_path.read_text(encoding='utf-8')) if phase_c_path.exists() else {'phaseCSuites': {}}
    phase_d_path = root / '.github/scripts/m4-phase-d-baseline.json'
    phase_d = json.loads(phase_d_path.read_text(encoding='utf-8')) if phase_d_path.exists() else {'phaseDSuites': {}}
    phase_e_path = root / '.github/scripts/m4-phase-e-baseline.json'
    phase_e = json.loads(phase_e_path.read_text(encoding='utf-8')) if phase_e_path.exists() else {'phaseESuites': {}}
    phase_f_path = root / '.github/scripts/m4-phase-f-baseline.json'
    phase_f = json.loads(phase_f_path.read_text(encoding='utf-8')) if phase_f_path.exists() else {'phaseFSuites': {}}
    phase_g_path = root / '.github/scripts/m4-phase-g-baseline.json'
    phase_g = json.loads(phase_g_path.read_text(encoding='utf-8')) if phase_g_path.exists() else {'phaseGSuites': {}}
    sony_path = root / '.github/scripts/m4-phase-g-sony-corrective-baseline.json'
    sony = json.loads(sony_path.read_text(encoding='utf-8')) if sony_path.exists() else {'phaseGCorrectiveSuites': {}}
    m5_phase_b = json.loads((root / '.github/scripts/m5-phase-b-baseline.json').read_text(encoding='utf-8'))
    m5_phase_c = json.loads((root / '.github/scripts/m5-phase-c-baseline.json').read_text(encoding='utf-8'))
    m5_phase_d = json.loads((root / '.github/scripts/m5-phase-d-baseline.json').read_text(encoding='utf-8'))
    m6_phase_b = json.loads((root / '.github/scripts/m6-phase-b-baseline.json').read_text(encoding='utf-8'))
    m6_phase_c = json.loads((root / '.github/scripts/m6-phase-c-baseline.json').read_text(encoding='utf-8'))
    m6_phase_d = json.loads((root / '.github/scripts/m6-phase-d-baseline.json').read_text(encoding='utf-8'))
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
    expected = {**baseline['existingSuites'], **baseline['phaseASuites'], **phase_b['phaseBSuites'], **phase_c['phaseCSuites'], **phase_d['phaseDSuites'], **phase_e['phaseESuites'], **phase_f['phaseFSuites'], **phase_g['phaseGSuites'], **sony['phaseGCorrectiveSuites'], **m5_phase_b['m5PhaseBSuites'], **m5_phase_c['m5PhaseCSuites'], **m5_phase_d['m5PhaseDSuites'], **m6_phase_b['m6PhaseBSuites'], **m6_phase_c['m6PhaseCSuites']}
    expected.update(m6_phase_d['m6PhaseDSuites'])
    m6_phase_e_assembly = json.loads((root / '.github/scripts/m6-phase-e-assembly-baseline.json').read_text(encoding='utf-8'))
    expected.update(m6_phase_e_assembly['m6PhaseESuites'])
    if suites != expected:
        raise ValueError('Executed suite names/counts differ from the Phase A/B/C/D/E/F/G inventory')
    if any(name not in baseline['allowedOptInSkips'] for name in skipped):
        raise ValueError('An unexpected JVM case was skipped')
    for path, digest in baseline['fixtureSha256'].items():
        if hashlib.sha256((root / path).read_bytes()).hexdigest() != digest:
            raise ValueError('A frozen characterization fixture changed')
    for path, digest in m5_phase_b['fixtureSha256'].items():
        if hashlib.sha256((root / path).read_bytes()).hexdigest() != digest:
            raise ValueError('A frozen M5 Phase B fixture changed')
    oracle_journal = json.loads((root / 'app/build/reports/m5-phase-b-oracle-traces.json').read_text(encoding='utf-8'))
    raw_hashmap = {name: value['frames'] for name, value in oracle_journal['traces'].items() if value['environmentDependent']}
    if len(oracle_journal['traces']) != 82 or len(raw_hashmap) != 4 or oracle_journal['candidateImplemented']:
        raise ValueError('Missing raw executed M5 oracle evidence')
    candidate_journal = json.loads((root / 'app/build/reports/m5-phase-c-differential.json').read_text(encoding='utf-8'))
    c_hashmap = {name: value for name, value in candidate_journal['traces'].items() if value['environmentDependent']}
    if candidate_journal['compared'] != 82 or candidate_journal['divergences'] != 0 or len(c_hashmap) != 4:
        raise ValueError('Missing exact C differential evidence')
    if set(candidate_journal['traces']) != set(oracle_journal['traces']):
        raise ValueError('C differential inventory differs from B')
    for trace in candidate_journal['traces'].values():
        if trace['oracle'] != trace['candidate']:
            raise ValueError('Raw C differential trace mismatch')
    counts['TOTAL'] = sum(counts.values())
    summary = {
        'referenceHead': baseline['referenceHead'],
        'jvm': counts,
        'retainedCases': sum(baseline['existingSuites'].values()),
        'phaseACases': sum(baseline['phaseASuites'].values()),
        'phaseBCases': sum(phase_b['phaseBSuites'].values()),
        'phaseCCases': sum(phase_c['phaseCSuites'].values()),
        'phaseDCases': sum(phase_d['phaseDSuites'].values()),
        'phaseECases': sum(phase_e['phaseESuites'].values()),
        'phaseFCases': sum(phase_f['phaseFSuites'].values()),
        'phaseGCases': sum(phase_g['phaseGSuites'].values()),
        'phaseGCorrectiveCases': sum(sony['phaseGCorrectiveSuites'].values()),
        'm5PhaseBCases': sum(m5_phase_b['m5PhaseBSuites'].values()),
        'm5PhaseDCases': sum(m5_phase_d['m5PhaseDSuites'].values()),
        'm6PhaseBCases': sum(m6_phase_b['m6PhaseBSuites'].values()),
        'm6PhaseCCases': sum(m6_phase_c['m6PhaseCSuites'].values()),
        'm6PhaseDCases': sum(m6_phase_d['m6PhaseDSuites'].values()),
        'm5PhaseBFixtureSha256': m5_phase_b['fixtureSha256'],
        'm5PhaseBHashMapEnvironment': oracle_journal['environment'],
        'm5PhaseBHashMapRawFrames': raw_hashmap,
        'm5PhaseCCases': sum(m5_phase_c['m5PhaseCSuites'].values()),
        'm5PhaseCDifferential': candidate_journal,
        'm5PhaseCHashMapRawPairs': c_hashmap,
        'suites': suites,
        'skippedCases': skipped,
        'fixtureSha256': baseline['fixtureSha256'],
    }
    destination = root / 'app/build/reports/m4-phase-a-summary.json'
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
    print('JVM: ' + ' '.join(f'{name}={count}' for name, count in counts.items()))
    print(f'Retained baseline: {summary["retainedCases"]}; Phase A: {summary["phaseACases"]}; Phase B: {summary["phaseBCases"]}; Phase C: {summary["phaseCCases"]}; Phase D: {summary["phaseDCases"]}; Phase E: {summary["phaseECases"]}; Phase F: {summary["phaseFCases"]}; Phase G: {summary["phaseGCases"]}; Sony corrective: {summary["phaseGCorrectiveCases"]}')
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
        print('Phase A/B/C/D/E/F/G report validation failed; no payload or exception message emitted', file=sys.stderr)
        sys.exit(1)
