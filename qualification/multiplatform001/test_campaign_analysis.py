import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from analyze_capture import analyze
from campaign_analysis import compare_positions, markdown_report

"""Exercise offline public facts, deliberately synthetic controls and privacy boundaries.

All fixtures are owned scalar records. Their advertisement labels never become
physical evidence or automatic protection, even when a simulated AD key exists.
"""


class CampaignAnalysisTests(unittest.TestCase):
    """Prove campaign isolation, arithmetic, correlation limits and truthful partial-capture reporting."""

    def sample(self, time, position, state='PLAYING', session=1, speed=1, marker='ABSENT'):
        """Build one owned public-scalar frame with its capture provenance and bounded freshness."""
        return {'event': 'PUBLIC_SAMPLE', 'platform': 'NETFLIX', 'campaignStartedMs': 10, 'elapsedMs': time,
                'sequence': time, 'publisherScope': 'OWN_FIXTURE_NOT_REAL_PLATFORM',
                'sample': {'session': session, 'state': state, 'capturedAtMs': time, 'sampleAgeMs': 0,
                           'rawMs': position, 'estimatedMs': position, 'speed': speed, 'anchorAgeMs': 0,
                           'marker': marker, 'markerType': 'ABSENT' if marker == 'ABSENT' else 'LONG'}}

    def report(self, rows):
        """Read actual temporary UTF-8 bytes through the shipped analyzer, without mutating the inputs."""
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'owned.jsonl'
            path.write_text('\n'.join(json.dumps(row) for row in rows), encoding='utf-8')
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            result = analyze([path])
            self.assertEqual(result['sources'][0]['sha256'], digest)
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), digest)
            return result

    def test_numeric_progress_speed_and_jitter(self):
        """Constant speed two and 20ms jitter produce a finite residual, never an advertising boundary."""
        old = self.sample(1000, 10000, speed=2)['sample']
        current = self.sample(2000, 12020, speed=2)['sample']
        facts = compare_positions(current, old)
        self.assertTrue(facts['comparable'])
        self.assertEqual(facts['expectedPositionDeltaMs'], 2000)
        self.assertEqual(facts['positionResidualMs'], 20)
        self.assertFalse(facts['discontinuity'])

    def test_uncomparable_pairs(self):
        """Changed session/state/speed/identity, stale timestamps and absent positions stay explicitly incomparable."""
        old = self.sample(1000, 10000)['sample']
        for patch in [{'session': 2}, {'state': 'PAUSED'}, {'speed': 2}, {'identityChanged': True}, {'capturedAtMs': 1000}, {'capturedAtMs': 7000}, {'rawMs': -1}, {'anchorAgeMs': -1}, {'anchorAgeMs': 120001}, {'sampleAgeMs': 2501}]:
            current = dict(self.sample(2000, 11000)['sample'], **patch)
            facts = compare_positions(current, old)
            self.assertFalse(facts['comparable'], patch)
            self.assertIsNone(facts['positionResidualMs'])

    def test_platform_and_campaign_separation(self):
        """Successive campaigns and two target platforms cannot share position anchors or owner labels."""
        a, b, c = self.sample(1000, 10000), self.sample(2000, 5), self.sample(3000, 6)
        b['campaignStartedMs'] = 20
        c['platform'] = 'DISNEY_PLUS'
        result = self.report([a, b, c])
        self.assertEqual(len(result['platforms']['NETFLIX']['campaigns']), 2)
        self.assertEqual(len(result['platforms']['DISNEY_PLUS']['campaigns']), 1)
        for group in result['platforms'].values():
            for campaign in group['campaigns']:
                self.assertEqual(campaign['discontinuities'], [])

    def test_partial_repeated_and_missing_annotations(self):
        """Repeated owner labels, missing DISABLED and short context are reported without manufactured boundaries."""
        rows = [self.sample(1000, 10000)]
        for time, label in [(1100, 'AD_VISIBLE'), (1200, 'AD_VISIBLE'), (1300, 'NORMAL_SEEK'), (1400, 'CONTENT_VISIBLE')]:
            rows.append({'platform': 'NETFLIX', 'event': 'OWNER_ANNOTATION', 'campaignStartedMs': 10, 'sequence': time, 'elapsedMs': time, 'annotation': label})
        campaign = self.report(rows)['platforms']['NETFLIX']['campaigns'][0]
        self.assertFalse(campaign['captureComplete'])
        self.assertEqual(len(campaign['annotationWindows']), 4)
        self.assertTrue(campaign['annotationWindows'][0]['truncatedBefore'])
        self.assertTrue(campaign['annotationWindows'][0]['truncatedAfter'])
        self.assertEqual(len(campaign['annotatedIntervals']), 1)
        self.assertEqual(campaign['annotatedIntervals'][0]['physicalDuration'], 'NOT_MEASURED')
        self.assertTrue(campaign['negativeControls'][0]['overlapsAnnotatedAdInterval'])
        self.assertEqual(campaign['conclusion'], 'INSUFFICIENT_EVIDENCE')

    def test_complete_capture_and_sequence_conflicts(self):
        """Conflicting rows sharing a sequence are retained, while only byte-equivalent decoded JSON is deduplicated."""
        start = {'platform': 'NETFLIX', 'event': 'ENABLED', 'campaignStartedMs': 10, 'sequence': 1, 'elapsedMs': 100}
        finish = dict(start, event='DISABLED', sequence=3, elapsedMs=4000)
        first, changed = self.sample(1000, 10000), self.sample(1000, 20000)
        campaign = self.report([start, first, first, changed, finish])['platforms']['NETFLIX']['campaigns'][0]
        self.assertTrue(campaign['captureComplete'])
        self.assertEqual(campaign['duplicates'], 1)
        self.assertEqual(campaign['publicSamples'], 2)
        self.assertEqual(len(campaign['sequenceConflicts']), 1)
        self.assertEqual(len(campaign['records'][1]['sourceRefs']), 2)

    def test_allowlist_privacy_and_markdown(self):
        """Private unknown values, custom event names and raw publisher strings never enter the JSON or Markdown export."""
        row = self.sample(1000, 10000)
        row['privateToken'] = 'SECRET_CREDENTIAL'
        row['sample'].update(title='PRIVATE_TITLE', metadataKeys='PRIVATE_KEY', sessionExtras='SECRET_EXTRAS', identityCompatibility='PRIVATE_ID')
        result = self.report([row])
        text = json.dumps(result) + markdown_report(result)
        for private in ['SECRET_CREDENTIAL', 'PRIVATE_TITLE', 'PRIVATE_KEY', 'SECRET_EXTRAS', 'PRIVATE_ID']:
            self.assertNotIn(private, text)
        self.assertIn('OBSERVATIONS', text)
        self.assertIn('CORRÉLATIONS', text)
        self.assertIn('HYPOTHÈSES', text)
        self.assertIn('LIMITES', text)

    def test_archived_negative_fixtures(self):
        """Execute every explicit synthetic scenario, including fake ads, ordinary controls and unavailable data."""
        fixtures = json.loads((Path(__file__).parent / 'cases001b.json').read_text(encoding='utf-8'))
        self.assertEqual(fixtures['provenance'], 'OWN_SYNTHETIC_NOT_OFFICIAL_PLATFORM_OR_SONY')
        self.assertGreaterEqual(len(fixtures['cases']), 10)
        for fixture in fixtures['cases']:
            result = self.report(fixture['records'])
            for group in result['platforms'].values():
                self.assertEqual(group['conclusion'], 'INSUFFICIENT_EVIDENCE', fixture['name'])
            self.assertNotIn('AD_START', json.dumps(result))
            self.assertNotIn('AD_END', json.dumps(result))

    def test_partial_and_nonfinite_lines_are_audited(self):
        """Malformed partial lines and nonfinite JSON numbers are counted as ignored without changing valid facts."""
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'partial.log'
            bad = dict(self.sample(2000, 20), badNumber=float('nan'))
            path.write_text('not json\n{"platform":\n' + json.dumps(bad) + '\n' + json.dumps(self.sample(1000, 10)), encoding='utf-16')
            result = analyze([path])
            self.assertEqual(result['sources'][0]['ignoredLines'], 3)
            self.assertEqual(result['platforms']['NETFLIX']['events'], 1)
            self.assertEqual(result['sources'][0]['sha256'], hashlib.sha256(path.read_bytes()).hexdigest())

    def test_session_loss_prevents_continuity(self):
        """Loss invalidates diagnostic comparison history even if the next supplied ordinal is reused."""
        lost = {'event': 'UNAVAILABLE', 'platform': 'NETFLIX', 'campaignStartedMs': 10, 'elapsedMs': 1500}
        result = self.report([self.sample(1000, 50000), lost, self.sample(2000, 5)])
        campaign = result['platforms']['NETFLIX']['campaigns'][0]
        self.assertEqual(campaign['discontinuities'], [])


if __name__ == '__main__':
    unittest.main()
