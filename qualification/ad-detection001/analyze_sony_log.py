import argparse
import collections
import hashlib
import json
import re
from pathlib import Path

"""
Analyse tous les octets du journal Sony fourni sans en publier les messages tiers.
Le résultat ne conserve que des scalaires SceneVibe explicitement autorisés.
Les observations acoustiques du propriétaire restent des annotations séparées,
jamais des marqueurs publicitaires déduits rétroactivement d'Android.
"""

LINE = re.compile(r'(\d\d-\d\d\s+\d\d:\d\d:\d\d\.\d{3})\s+.*?\b([VDIWEF])\s+([^:]+?)\s*:\s*(.*)$')
TAGS = {'SceneVibeRuntime', 'SceneVibeAdSync', 'SceneVibeText009', 'SceneVibeMedia'}
FIELDS = set('event ordinal scheduledMs stage reason traversed consumedWithoutStart due skipped rearmed prepareRequests prepared started stopped completed refused errors code positionMs renderRequests state adSignal rawMs selectedMs contentMs offsetMs playback anchorAgeMs clock textOn audioOn active compatible selected speed total consumed future eligible windows textDue audioDue textRearmed textRestoreRequests nextMs interrupted replay'.split())


def parse(data):
    """Parse every source line, deduplicate identical lines and project only safe known scalar fields."""
    encoding = 'utf-16' if data[:2] in (b'\xff\xfe', b'\xfe\xff') else 'utf-8-sig'
    lines = data.decode(encoding).splitlines()
    tags = collections.Counter()
    records = []
    duplicates = 0
    seen = set()
    parsed = 0
    explicit_ad_edges = 0
    for number, line in enumerate(lines, 1):
        match = LINE.search(line)
        if not match:
            continue
        parsed += 1
        time, _level, tag, message = match.groups()
        tags[tag] += 1
        if re.search(r'\bAD_START\b|\bAD_END\b', message):
            explicit_ad_edges += 1
        if line in seen:
            duplicates += 1
            continue
        seen.add(line)
        if tag not in TAGS or not ('10-28 16:32:50.000' <= time <= '10-28 16:35:57.999'):
            continue
        values = {'time': time, 'source_line': number, 'tag': tag}
        for key, value in re.findall(r'([A-Za-z][A-Za-z0-9]*)=([A-Za-z0-9_.-]+)', message):
            if key in FIELDS and len(value) <= 80:
                values[key] = value
        if len(values) > 3:
            records.append(values)
    return {'source': {'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data), 'encoding': encoding,
                       'lines': len(lines), 'parsed_records': parsed, 'duplicate_lines_removed': duplicates,
                       'explicit_AD_START_AD_END_messages': explicit_ad_edges},
            'scope': 'FILTERED_OWN_SCALARS_NO_THIRD_PARTY_PAYLOAD_OR_RETROACTIVE_AD_LABEL',
            'owner_observations': {'audio3_audible_during_ad_at': '10-28 16:33:02.677',
                                   'capture_filename_time': '10-28 16:32:57',
                                   'ad_end_coarse_upper_bound': '10-28 16:33:57',
                                   'exact_AD_START_known': False, 'exact_AD_END_known': False,
                                   'provenance': 'Owner mandate/corrigendum, not automated acoustic measurement'},
            'records': records}


def main():
    """Read one supplied log and write a deterministic privacy-filtered JSON evidence file."""
    parser = argparse.ArgumentParser(description='Audit passif du journal Sony AD-DETECTION 001')
    parser.add_argument('input', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = parse(args.input.read_bytes())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'source': result['source'], 'saved_records': len(result['records'])}, ensure_ascii=False))


if __name__ == '__main__':
    main()
