import json
from pathlib import Path
import sys

"""Validate raw native traces, including input/token/effect order, without sorting HashMap emissions.

This gate is separate from native M4 durability and does not claim a physical Sony run.
The corpus stays byte-exact and all 82 legacy/candidate pairs share one Android VM.
"""


def validate(path):
    """Require every oracle case and exact raw journal equality, including the four environment cases."""
    evidence = json.loads(Path(path).read_text(encoding='utf-8'))
    root = Path(__file__).resolve().parents[2]
    corpus = json.loads((root / 'app/src/test/resources/m5-phase-b/corpus.json').read_text(encoding='utf-8'))
    cases = {case['id']: case for case in corpus['cases'] if case['kind'] == 'oracle'}
    if evidence['api'] not in (31, 35) or evidence['compared'] != 82 or evidence['hashmap'] != 4 or evidence['divergences'] != 0:
        raise ValueError('Incomplete native differential count or platform')
    if set(evidence['traces']) != set(cases):
        raise ValueError('Missing or extra native differential case')
    for name, trace in evidence['traces'].items():
        if trace['oracle'] != trace['candidate'] or len(trace['oracle']) != len(cases[name]['actions']):
            raise ValueError('Raw native oracle/candidate divergence')
        if trace['environmentDependent'] != cases[name].get('environmentDependent', False):
            raise ValueError('Changed environment-dependent classification')
    print('M5 native API ' + str(evidence['api']) + ': 82 compared / 4 raw HashMap / 0 divergences')


if __name__ == '__main__':
    validate(sys.argv[1])
