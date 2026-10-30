import hashlib
import json
import math
import re
from collections import Counter
from pathlib import Path

"""Audit complete public campaigns without inferring advertisement boundaries.

This offline module has no Android or commentary authority. Only allowlisted
scalars survive export; original sources stay read-only and retain their hashes.
Overlaps retain all source-line references. Human labels describe delayed owner
observations, not automatic detector inputs or precise physical boundary times.
"""

PLATFORMS = ('NETFLIX', 'DISNEY_PLUS')
STATES = ('NONE', 'STOPPED', 'PAUSED', 'PLAYING', 'FAST_FORWARDING', 'REWINDING', 'BUFFERING', 'ERROR', 'CONNECTING', 'SKIPPING_TO_PREVIOUS', 'SKIPPING_TO_NEXT', 'SKIPPING_TO_QUEUE_ITEM', 'UNKNOWN')
MARKERS = ('ABSENT', 'CONTENT', 'AD', 'INVALID', 'UNAVAILABLE')
TYPES = ('ABSENT', 'NULL', 'LONG', 'STRING', 'BOOLEAN', 'OTHER', 'UNREADABLE')
LABELS = ('AD_VISIBLE', 'CONTENT_VISIBLE', 'NORMAL_SEEK', 'NORMAL_PAUSE')
EVENTS = ('ENABLED', 'DISABLED', 'SELECTION', 'PUBLIC_SAMPLE', 'PUBLIC_TRANSITION', 'METADATA_CALLBACK', 'PLAYBACK_CALLBACK', 'UNAVAILABLE', 'OWNER_ANNOTATION', 'SESSION_DISAPPEARED', 'SESSION_REPLACED')
CHANGES = ('SESSION_APPEARED', 'SESSION_DISAPPEARED', 'SESSION_REPLACED', 'PLAYBACK_STATE_CHANGED', 'PLAYBACK_RESUMED', 'SPEED_CHANGED', 'MEDIA_IDENTITY_CHANGED', 'METADATA_CHANGED', 'MARKER_CHANGED', 'POSITION_RESET', 'POSITION_JUMP_FORWARD', 'POSITION_JUMP_BACKWARD')
NUMBERS = ('session', 'stateCode', 'capturedAtMs', 'sampleAgeMs', 'rawMs', 'estimatedMs', 'anchorAgeMs', 'durationMs', 'speed', 'metadataCallbacks', 'playbackCallbacks', 'extrasCallbacks', 'ignoredCustomEvents', 'updatedAtMs', 'activeSessionCount', 'targetSessionCount', 'previousSession')
BOOLEANS = ('metadataPresent', 'identityPresent', 'identityChanged', 'selectedByHistoricalProbe')


def finite_number(value):
    """Accept finite JSON numeric facts only; booleans and publisher strings are not numbers."""
    return type(value) in (int, float) and math.isfinite(value) and abs(value) <= 2**63 - 1


def scalars(value):
    """Project only closed facts; never serialize arbitrary extras, identities or unknown metadata keys."""
    if not isinstance(value, dict):
        return {}
    out = {key: value[key] for key in NUMBERS if finite_number(value.get(key))}
    out.update({key: value[key] for key in BOOLEANS if type(value.get(key)) is bool})
    for key, allowed in [('state', STATES), ('marker', MARKERS), ('markerStatus', MARKERS), ('markerType', TYPES), ('identityCompatibility', ('MATCH', 'MISMATCH', 'NOT_EVALUATED'))]:
        if value.get(key) in allowed:
            out[key] = value[key]
    keys = value.get('metadataKeys')
    if isinstance(keys, str) and (keys in ('ABSENT', 'UNREADABLE', 'OMITTED') or re.fullmatch(r'(?:(?:ADVERTISEMENT|MEDIA_ID|TITLE|DISPLAY_TITLE|DISPLAY_SUBTITLE|DURATION|ART|ALBUM_ART|DISPLAY_ICON|MEDIA_URI),?)*;otherKeys=[0-9]+', keys)):
        out['metadataKeys'] = keys
    for key in ('sessionExtras', 'playbackExtras'):
        extra = value.get(key)
        if isinstance(extra, str) and (extra in ('ABSENT', 'UNREADABLE', 'UNAVAILABLE', 'OMITTED') or re.fullmatch(r'keys=[0-9]+;standardAdKey=(?:ABSENT|EXPLICIT_ZERO|EXPLICIT_NONZERO|WRONG_TYPE)', extra)):
            out[key] = extra
    return out


def load_events(paths):
    """Decode entire sources and deduplicate exact JSON events while retaining every occurrence's line."""
    sources, unique = [], {}
    for path in paths:
        path = Path(path)
        data = path.read_bytes()
        encoding = 'utf-16' if data[:2] in (b'\xff\xfe', b'\xfe\xff') else 'utf-8-sig'
        lines = data.decode(encoding).splitlines()
        source = {'filename': path.name, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(), 'encoding': encoding, 'lines': len(lines), 'ignoredLines': 0}
        sources.append(source)
        for number, line in enumerate(lines, 1):
            try:
                raw = json.loads(line[line.index('{'):])
                if not isinstance(raw, dict) or raw.get('platform') not in PLATFORMS or type(raw.get('elapsedMs')) is not int or raw.get('event') not in EVENTS:
                    raise ValueError('Not an authorized scalar event')
                canonical = json.dumps(raw, sort_keys=True, separators=(',', ':'), allow_nan=False)
            except (ValueError, json.JSONDecodeError):
                source['ignoredLines'] += 1
                continue
            ref = {'source': path.name, 'sourceLine': number}
            if canonical in unique:
                unique[canonical]['sourceRefs'].append(ref)
                continue
            row = {key: raw[key] for key in ('platform', 'event', 'elapsedMs')}
            for key in ('campaignStartedMs', 'sequence', 'wallTimeMs'):
                if type(raw.get(key)) is int:
                    row[key] = raw[key]
            row['sourceRefs'] = [ref]
            row.update(ref)
            row.update(scalars(raw))
            if raw.get('publisherScope') in ('PUBLIC_PLATFORM_PACKAGE', 'OWN_FIXTURE_NOT_REAL_PLATFORM'):
                row['publisherScope'] = raw['publisherScope']
            if raw.get('event') in ('PUBLIC_SAMPLE', 'PUBLIC_TRANSITION'):
                row['sample'] = scalars(raw.get('sample'))
            if raw.get('event') == 'OWNER_ANNOTATION' and raw.get('annotation') in LABELS:
                row['annotation'] = raw['annotation']
                row['origin'] = 'OWNER_LABEL_NOT_AUTOMATIC_SIGNAL'
            row['changes'] = [v for v in raw.get('changes', []) if v in CHANGES] if isinstance(raw.get('changes'), list) else []
            unique[canonical] = row
    return sources, list(unique.values())


def compare_positions(current, previous):
    """Compare emitted estimates from one session, with the same documented v27.1 diagnostic limits."""
    result = {'comparable': False, 'reason': 'FIRST_SESSION_SAMPLE', 'positionBasis': 'EXISTING_SAMPLER_ESTIMATE_NOT_NEW_CLOCK', 'deltaPositionMs': None, 'deltaElapsedMs': None, 'expectedPositionDeltaMs': None, 'positionResidualMs': None, 'discontinuity': False, 'thresholdMs': 5000}
    if previous is None:
        return result
    required = ('capturedAtMs', 'rawMs', 'estimatedMs', 'anchorAgeMs', 'speed')
    if any(not finite_number(row.get(key)) for row in (current, previous) for key in required):
        result['reason'] = 'MISSING_NUMERIC_FACTS'
        return result
    dt = current['capturedAtMs'] - previous['capturedAtMs']
    delta = current['estimatedMs'] - previous['estimatedMs']
    result.update(deltaElapsedMs=dt, deltaPositionMs=delta)
    reason = 'COMPARABLE'
    if current.get('session') != previous.get('session'):
        reason = 'DIFFERENT_SESSION'
    elif dt <= 0 or dt > 5000:
        reason = 'NONCOMPARABLE_CAPTURE_TIME'
    elif not finite_number(current.get('sampleAgeMs')) or not 0 <= current['sampleAgeMs'] <= 2500:
        reason = 'STALE_OR_UNRECORDED_CAPTURE'
    elif current.get('identityChanged'):
        reason = 'IDENTITY_CHANGED'
    elif current.get('durationMs', -1) > 0 and previous.get('durationMs', -1) > 0 and current['durationMs'] != previous['durationMs']:
        reason = 'DURATION_CHANGED'
    elif min(current['rawMs'], previous['rawMs'], current['estimatedMs'], previous['estimatedMs']) < 0:
        reason = 'POSITION_UNAVAILABLE'
    elif min(current['anchorAgeMs'], previous['anchorAgeMs']) < 0:
        reason = 'ANCHOR_UNAVAILABLE'
    elif max(current['anchorAgeMs'], previous['anchorAgeMs']) > 120000:
        reason = 'OLD_ANCHOR'
    elif current.get('state') != previous.get('state'):
        reason = 'STATE_CHANGED'
    elif current['speed'] != previous['speed']:
        reason = 'SPEED_CHANGED'
    elif current.get('state') not in ('PLAYING', 'PAUSED', 'STOPPED'):
        reason = 'STATE_NOT_PROGRESS_COMPARABLE'
    result['reason'] = reason
    if reason == 'COMPARABLE':
        expected = dt * previous['speed'] if previous['state'] == 'PLAYING' else 0
        result.update(comparable=True, expectedPositionDeltaMs=expected, positionResidualMs=delta - expected, discontinuity=abs(delta - expected) > 5000)
    return result


def important_changes(sample, previous, comparison):
    """Describe changes as observations, without labeling any position discontinuity as advertising."""
    if previous is None:
        return ['SESSION_APPEARED']
    changes = []
    if sample.get('state') != previous.get('state'):
        changes.append('PLAYBACK_STATE_CHANGED')
        if sample.get('state') == 'PLAYING' and previous.get('state') in ('PAUSED', 'BUFFERING'):
            changes.append('PLAYBACK_RESUMED')
    if sample.get('speed') != previous.get('speed'):
        changes.append('SPEED_CHANGED')
    if sample.get('identityChanged'):
        changes.append('MEDIA_IDENTITY_CHANGED')
    if any(sample.get(k) != previous.get(k) for k in ('metadataPresent', 'metadataCallbacks', 'metadataKeys')):
        changes.append('METADATA_CHANGED')
    if any(sample.get(k) != previous.get(k) for k in ('marker', 'markerType')):
        changes.append('MARKER_CHANGED')
    if finite_number(sample.get('rawMs')) and 0 <= sample['rawMs'] <= 1000 and previous.get('rawMs', -1) > 10000:
        changes.append('POSITION_RESET')
    if comparison['discontinuity']:
        changes.append('POSITION_JUMP_FORWARD' if comparison['deltaPositionMs'] > 0 else 'POSITION_JUMP_BACKWARD')
    return changes


def annotation_windows(events, annotations):
    """Retain actual available +/-30s contexts; never interpolate missing time or precise human boundaries."""
    windows = []
    if not events:
        return windows
    for label in annotations:
        start, end = label['elapsedMs'] - 30000, label['elapsedMs'] + 30000
        selected = [row for row in events if start <= row['elapsedMs'] <= end]
        windows.append({'annotation': label['annotation'], 'labelElapsedMs': label['elapsedMs'], 'sourceRefs': label['sourceRefs'],
                        'requestedStartMs': start, 'requestedEndMs': end,
                        'availableStartMs': selected[0]['elapsedMs'] if selected else None,
                        'availableEndMs': selected[-1]['elapsedMs'] if selected else None,
                        'truncatedBefore': events[0]['elapsedMs'] > start, 'truncatedAfter': events[-1]['elapsedMs'] < end,
                        'boundaryUncertainty': 'HUMAN_REACTION_DELAY_UNKNOWN', 'events': selected})
    return windows


def analyze_campaign(events):
    """Analyze one platform/campaign, preserving session boundaries, sequence conflicts and partial capture limits."""
    events = sorted(events, key=lambda row: (row['elapsedMs'], row.get('sequence', -1)))
    samples, annotations, timeline, discontinuities = [], [], [], []
    prior, marker_prior, sequences = {}, {}, {}
    conflicts, markers, types, states = [], Counter(), Counter(), Counter()
    transitions = []
    for row in events:
        seq = row.get('sequence')
        if seq is not None and seq in sequences:
            conflicts.append({'sequence': seq, 'sources': [sequences[seq], row['sourceRefs']]})
        sequences[seq] = row['sourceRefs']
        event = row['event']
        if event == 'OWNER_ANNOTATION' and 'annotation' in row:
            annotations.append(row)
        sample = row.get('sample', {}) if event in ('PUBLIC_SAMPLE', 'PUBLIC_TRANSITION') else row
        # Keep historical marker counts: PUBLIC_SAMPLE plus marker callbacks, not duplicated transition payloads.
        if event != 'PUBLIC_TRANSITION' and sample.get('marker') in MARKERS:
            marker = sample['marker']
            markers[marker] += 1
            if sample.get('markerType') in TYPES:
                types[sample['markerType']] += 1
            if sample.get('state') in STATES:
                states[sample['state']] += 1
            session = sample.get('session')
            if marker_prior.get(session) != marker:
                transitions.append({'from': marker_prior.get(session), 'to': marker, 'elapsedMs': row['elapsedMs'], 'wallTimeMs': row.get('wallTimeMs'), 'event': event, 'source': row['source'], 'sourceLine': row['sourceLine'], 'sourceRefs': row['sourceRefs']})
                marker_prior[session] = marker
        if event == 'PUBLIC_SAMPLE':
            session = sample.get('session')
            old = prior.get(session)
            comparison = compare_positions(sample, old)
            row['offlineComparison'] = comparison
            row['offlineChanges'] = important_changes(sample, old, comparison)
            samples.append(row)
            if comparison['discontinuity']:
                discontinuities.append({'elapsedMs': row['elapsedMs'], 'session': session, 'comparison': comparison, 'sourceRefs': row['sourceRefs'], 'advertisementMeaning': 'NOT_ESTABLISHED'})
            if row['offlineChanges']:
                timeline.append(row)
            if type(session) is int:
                prior[session] = sample
        else:
            timeline.append(row)
        if event in ('UNAVAILABLE', 'SESSION_DISAPPEARED'):
            if event == 'UNAVAILABLE':
                prior.clear()
            else:
                prior.pop(row.get('session'), None)
    enabled, disabled = any(r['event'] == 'ENABLED' for r in events), any(r['event'] == 'DISABLED' for r in events)
    intervals, open_label, label_warnings = [], None, []
    for label in annotations:
        if label['annotation'] == 'AD_VISIBLE':
            if open_label is None:
                open_label = label
            else:
                label_warnings.append('REPEATED_AD_LABEL_NOT_A_SECOND_AUTOMATIC_BREAK')
        elif label['annotation'] == 'CONTENT_VISIBLE':
            if open_label:
                intervals.append({'adLabelElapsedMs': open_label['elapsedMs'], 'contentLabelElapsedMs': label['elapsedMs'], 'annotationIntervalMs': label['elapsedMs'] - open_label['elapsedMs'], 'physicalDuration': 'NOT_MEASURED', 'startBoundary': 'OWNER_LABEL_WITH_UNKNOWN_DELAY', 'endBoundary': 'OWNER_LABEL_WITH_UNKNOWN_DELAY', 'automaticDetection': False})
                open_label = None
            else:
                label_warnings.append('CONTENT_LABEL_WITHOUT_AD_LABEL')
    if open_label:
        label_warnings.append('AD_LABEL_WITHOUT_CONTENT_LABEL')
    controls = [{'annotation': r['annotation'], 'elapsedMs': r['elapsedMs'], 'sourceRefs': r['sourceRefs'],
                 'overlapsAnnotatedAdInterval': any(i['adLabelElapsedMs'] <= r['elapsedMs'] <= i['contentLabelElapsedMs'] for i in intervals),
                 'actualOutsideAdContext': 'NOT_ESTABLISHED_BY_CAPTURE_ALONE'} for r in annotations if r['annotation'] in ('NORMAL_SEEK', 'NORMAL_PAUSE')]
    sessions = sorted({row['sample']['session'] for row in samples if type(row['sample'].get('session')) is int})
    return {'platform': events[0]['platform'], 'campaignStartedMs': events[0].get('campaignStartedMs'), 'events': len(events),
            'duplicates': sum(len(row['sourceRefs']) - 1 for row in events), 'enabledPresent': enabled, 'disabledPresent': disabled,
            'captureComplete': enabled and disabled, 'sequenceConflicts': conflicts,
            'publicSamples': len(samples), 'sessions': sessions, 'markers': markers, 'types': types, 'states': states,
            'sampleMarkers': Counter(row['sample'].get('marker', 'UNAVAILABLE') for row in samples),
            'markerStatuses': Counter(row['sample'].get('markerStatus', 'NOT_RECORDED_V27') for row in samples),
            'markerTransitions': transitions, 'ownerAnnotations': annotations, 'annotationWindows': annotation_windows(events, annotations),
            'annotatedIntervals': intervals, 'negativeControls': controls, 'discontinuities': discontinuities,
            'timeline': timeline, 'records': events,
            'hypotheses': ['Position/state changes may also follow SEEK, pause, buffering or episode changes; they are not an ad signature.'],
            'limits': label_warnings + ([] if enabled and disabled else ['CAPTURE_PARTIAL_OR_MISSING_ENABLE_DISABLE']) +
                      ['OWNER_LABEL_DELAY_UNKNOWN', 'NO_INDEPENDENT_SCREEN_OR_ACOUSTIC_MEASUREMENT', 'NO_VALIDATED_ALTERNATIVE_AD_SIGNATURE'],
            'conclusion': 'INSUFFICIENT_EVIDENCE'}


def detailed_analysis(paths):
    """Group sources strictly by platform and monotonic campaign, retaining the v27 aggregate keys additively."""
    sources, rows = load_events(paths)
    campaigns = {}
    for row in rows:
        key = (row['platform'], row.get('campaignStartedMs'))
        campaigns.setdefault(key, []).append(row)
    reports = [analyze_campaign(events) for events in campaigns.values()]
    groups = {}
    for platform in PLATFORMS:
        items = [r for r in reports if r['platform'] == platform]
        groups[platform] = {'events': sum(r['events'] for r in items), 'duplicates': sum(r['duplicates'] for r in items),
                            'markers': sum((Counter(r['markers']) for r in items), Counter()),
                            'types': sum((Counter(r['types']) for r in items), Counter()),
                            'states': sum((Counter(r['states']) for r in items), Counter()),
                            'markerTransitions': [v for r in items for v in r['markerTransitions']],
                            'ownerAnnotations': [v for r in items for v in r['ownerAnnotations']],
                            'campaigns': items, 'conclusion': 'INSUFFICIENT_EVIDENCE'}
    return {'scope': 'PUBLIC_SCALAR_AUDIT_NOT_A_PHYSICAL_COMPATIBILITY_VERDICT', 'sources': sources, 'platforms': groups,
            'schemaVersion': 2, 'privacy': 'ALLOWLIST_SCALARS_NO_TITLES_IDENTITIES_TOKENS_PRIVATE_EXTRAS'}


def markdown_report(report):
    """Render separate observation/correlation/hypothesis/limit conclusions for each actual campaign."""
    lines = ['# Analyse des campagnes publiques SceneVibe', '', 'Aucune décision automatique de publicité ; annotations humaines distinctes.', '']
    for platform, group in report['platforms'].items():
        for campaign in group['campaigns']:
            lines += [f"## {platform} — campagne {campaign['campaignStartedMs']}", '', '### OBSERVATIONS', '',
                      f"{campaign['events']} événements uniques ; {campaign['duplicates']} doublons écartés ; {campaign['publicSamples']} PUBLIC_SAMPLE ; sessions {campaign['sessions']}.",
                      f"Marqueurs des échantillons : {dict(campaign['sampleMarkers'])}. Capture clôturée : {campaign['captureComplete']}.", '',
                      '| Temps monotone (ms) | Événement | État / position (ms) | Changements | Source:ligne |',
                      '|---|---|---|---|---|']
            for row in campaign['timeline']:
                sample = row.get('sample', row)
                change = ', '.join(row.get('offlineChanges', row.get('changes', [])))
                lines.append(f"| {row['elapsedMs']} | {row['event']} {row.get('annotation', '')} | {sample.get('state', '')} / {sample.get('rawMs', '')} | {change} | {row['source']}:{row['sourceLine']} |")
            lines += ['', '### CORRÉLATIONS', '', f"{len(campaign['annotationWindows'])} fenêtres ±30 s, bornées aux événements disponibles ; détails complets dans le JSON."]
            for interval in campaign['annotatedIntervals']:
                lines.append(f"AD_VISIBLE {interval['adLabelElapsedMs']} → CONTENT_VISIBLE {interval['contentLabelElapsedMs']} : intervalle entre annotations {interval['annotationIntervalMs']} ms ; durée physique non mesurée.")
            lines += [f"{len(campaign['discontinuities'])} discontinuités numériques comparables ; {len(campaign['negativeControls'])} contrôles SEEK/PAUSE annotés. Aucun de ces faits ne confirme une publicité.", '', '### HYPOTHÈSES', ''] + campaign['hypotheses']
            lines += ['', '### LIMITES', ''] + campaign['limits'] + ['', '### CONCLUSION', '', campaign['conclusion'], '']
    lines += ['## Intégrité des sources', '', '| Source | Octets | SHA-256 |', '|---|---:|---|']
    lines += [f"| {s['filename']} | {s['bytes']} | {s['sha256']} |" for s in report['sources']]
    return '\n'.join(lines) + '\n'
