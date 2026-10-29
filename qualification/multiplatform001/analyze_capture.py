import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path

"""Read complete public SceneVibe campaigns, preserving source digests and separating platforms.

Only supplied JSON events are counted. Overlapping captures are deduplicated by
canonical event bytes after JSON decoding. Human labels are comparison evidence,
not automatic detection input. No physical advertising verdict is inferred.
"""


def analyze(paths):
    """Decode all source lines, group the two authorized platforms and omit private/unknown fields."""
    sources, seen = [], set()
    groups = {p: {'events': 0, 'duplicates': 0, 'markers': Counter(), 'types': Counter(),
                  'states': Counter(), 'markerTransitions': [], 'ownerAnnotations': [],
                  'conclusion': 'INSUFFICIENT_EVIDENCE'} for p in ['NETFLIX', 'DISNEY_PLUS']}
    previous = {}
    for path in paths:
        data = path.read_bytes()
        encoding = 'utf-16' if data[:2] in (b'\xff\xfe', b'\xfe\xff') else 'utf-8-sig'
        lines = data.decode(encoding).splitlines()
        sources.append({'filename': path.name, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(), 'encoding': encoding, 'lines': len(lines)})
        for number, line in enumerate(lines, 1):
            try:
                row = json.loads(line[line.index('{'):])
            except (ValueError, json.JSONDecodeError):
                continue
            platform = row.get('platform')
            if platform not in groups or not isinstance(row.get('elapsedMs'), int):
                continue
            group = groups[platform]
            canonical = json.dumps(row, sort_keys=True)
            if canonical in seen:
                group['duplicates'] += 1
                continue
            seen.add(canonical)
            group['events'] += 1
            if row.get('event') == 'OWNER_ANNOTATION':
                label = row.get('annotation')
                if label in ['AD_VISIBLE', 'CONTENT_VISIBLE', 'NORMAL_SEEK', 'NORMAL_PAUSE']:
                    group['ownerAnnotations'].append({'annotation': label, 'elapsedMs': row['elapsedMs'], 'wallTimeMs': row.get('wallTimeMs'), 'source': path.name, 'sourceLine': number})
                continue
            sample = row.get('sample', {}) if row.get('event') == 'PUBLIC_SAMPLE' else row
            marker, value_type = sample.get('marker'), sample.get('markerType')
            if marker not in ['ABSENT', 'CONTENT', 'AD', 'INVALID']:
                continue
            group['markers'][marker] += 1
            if value_type in ['ABSENT', 'NULL', 'LONG', 'STRING', 'BOOLEAN', 'OTHER', 'UNREADABLE']:
                group['types'][value_type] += 1
            state = sample.get('state')
            if state in ['NONE', 'STOPPED', 'PAUSED', 'PLAYING', 'FAST_FORWARDING', 'REWINDING', 'BUFFERING', 'ERROR', 'CONNECTING', 'SKIPPING_TO_PREVIOUS', 'SKIPPING_TO_NEXT', 'SKIPPING_TO_QUEUE_ITEM', 'UNKNOWN']:
                group['states'][state] += 1
            key = (platform, row.get('campaignStartedMs'), sample.get('session'))
            if previous.get(key) != marker:
                group['markerTransitions'].append({'from': previous.get(key), 'to': marker, 'elapsedMs': row['elapsedMs'], 'wallTimeMs': row.get('wallTimeMs'), 'event': row.get('event'), 'source': path.name, 'sourceLine': number})
                previous[key] = marker
    return {'scope': 'PUBLIC_SCALAR_AUDIT_NOT_A_PHYSICAL_COMPATIBILITY_VERDICT', 'sources': sources, 'platforms': groups}


def main():
    """Analyze existing captures in read-only mode and write a separate UTF-8 report."""
    parser = argparse.ArgumentParser()
    parser.add_argument('inputs', type=Path, nargs='+')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.resolve() in [p.resolve() for p in args.inputs]:
        raise ValueError('Output must not replace a source log')
    args.output.write_text(json.dumps(analyze(args.inputs), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
