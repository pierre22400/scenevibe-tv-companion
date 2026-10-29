import argparse
import hashlib
import json
import re
from pathlib import Path

"""
Lit tous les octets des captures Sony et conserve seulement les scalaires publics
SceneVibe déjà journalisés. Les annotations humaines restent séparées des entrées
automatiques. Aucun échantillon intermédiaire, callback, jeton ou titre n'est créé.
Le temps de capture de fixture est l'elapsedMs exporté par le journal, pas une
prétendue ancre MediaSession absente du fichier source.
"""

FIELDS = {'event', 'sequence', 'elapsedMs', 'wallTimeMs', 'session', 'marker',
          'state', 'rawMs', 'selectedMs', 'anchorAgeMs', 'speed', 'durationMs',
          'identityCompatible', 'identityChanged', 'candidate', 'reason', 'edge',
          'adStarts', 'adEnds', 'annotation'}


def extract(path, seen):
    """Decode the complete source, deduplicate observations, and separate owner labels."""
    data = path.read_bytes()
    encoding = 'utf-16' if data[:2] in (b'\xff\xfe', b'\xfe\xff') else 'utf-8-sig'
    lines = data.decode(encoding).splitlines()
    records, inputs, annotations = [], [], []
    duplicates = 0
    for number, line in enumerate(lines, 1):
        if 'SceneVibeAdProbe' not in line or '{' not in line:
            continue
        payload = json.loads(line[line.index('{'):])
        canonical = json.dumps(payload, sort_keys=True)
        if canonical in seen:
            duplicates += 1
            continue
        seen.add(canonical)
        record = {k: v for k, v in payload.items() if k in FIELDS}
        record['sourceLine'] = number
        stamp = re.search(r'\d\d-\d\d \d\d:\d\d:\d\d\.\d{3}', line)
        record['logTime'] = stamp.group(0) if stamp else None
        records.append(record)
        if record['event'] == 'OWNER_ANNOTATION':
            annotations.append(record)
        elif record['event'] in ('PUBLIC_SAMPLE', 'METADATA_CALLBACK'):
            inputs.append(record)
    return {'source': {'filename': path.name, 'sha256': hashlib.sha256(data).hexdigest(),
                       'bytes': len(data), 'encoding': encoding, 'lines': len(lines),
                       'duplicateProbeRecordsRemoved': duplicates},
            'scope': 'ACTUAL_SONY_PUBLIC_SCALARS_REPLAY_NOT_NEW_PHYSICAL_PROTECTION_TEST',
            'captureTimeBasis': 'exact exported elapsedMs; underlying probe capturedAtMs not logged',
            'inputs': inputs, 'owner_annotations': annotations, 'records': records}


def main():
    """Create deterministic fixtures and redacted log projections from the supplied originals."""
    parser = argparse.ArgumentParser()
    parser.add_argument('--input-dir', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    seen = set()
    for label, filename in [('empty', 'scenevibe-ad-detection-20261028-215220(1).log'),
                            ('215514', 'scenevibe-ad-detection-20261028-215514(2).log'),
                            ('222334', 'scenevibe-ad-detection-20261028-222334(2).log')]:
        result = extract(args.input_dir / filename, seen)
        (args.output_dir / (label + '.json')).write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        (args.output_dir / (label + '-redacted.log')).write_text(''.join(json.dumps(row, ensure_ascii=False) + '\n' for row in result['records']))
        print(json.dumps({'source': result['source'], 'automaticRecords': len(result['inputs']), 'ownerAnnotations': len(result['owner_annotations'])}))


if __name__ == '__main__':
    main()
