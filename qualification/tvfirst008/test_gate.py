from pathlib import Path
import json
import tempfile
import unittest
from gate import GateError, junit, vitest

"""Negative qualification controls: hook failures and unexecuted tests must never look green."""


class QualificationControls(unittest.TestCase):
    """Use independent malformed reports, not duplicated feature implementation logic."""

    def report(self, **changes):
        """Build the smallest valid raw Vitest result, then alter a single evidence coordinate."""
        value = {'success': True, 'numFailedTestSuites': 0, 'numFailedTests': 0, 'numPendingTests': 0,
                 'numTodoTests': 0, 'numTotalTests': 1, 'numPassedTests': 1,
                 'testResults': [{'status': 'passed', 'assertionResults': [{'status': 'passed'}]}]}
        value.update(changes)
        return value

    def run_vitest(self, value):
        """Feed a temporary raw report to the actual verifier without a production artifact."""
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'result.json'
            path.write_text(json.dumps(value))
            return vitest(path, 1)

    def test_executed_case_is_accepted(self):
        """A complete actual-case result is the positive control."""
        self.assertEqual(1, self.run_vitest(self.report()))

    def test_before_all_failure_is_refused_even_with_zero_failed_cases(self):
        """A migration/setup failure can report no failed cases; the suite failure still refuses."""
        with self.assertRaises(GateError):
            self.run_vitest(self.report(success=False, numFailedTestSuites=1, numPassedTests=0))

    def test_skip_is_not_pass(self):
        """No native 008 prerequisite can become a green skipped job."""
        with self.assertRaises(GateError):
            self.run_vitest(self.report(numPendingTests=1, numPassedTests=0))

    def test_forged_totals_cannot_hide_unexecuted_cases(self):
        """A green summary cannot substitute for the actual assertion statuses."""
        with self.assertRaises(GateError):
            self.run_vitest(self.report(testResults=[{'status': 'passed', 'assertionResults': [{'status': 'skipped'}]}]))

    def test_missing_reports_are_refused(self):
        """Absent software execution is never historical qualification."""
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(GateError):
                junit(folder)

    def test_duplicate_suite_cannot_overwrite_a_result(self):
        """A second report cannot replace the first result under the same class key."""
        with tempfile.TemporaryDirectory() as folder:
            for name in ['one', 'two']:
                (Path(folder) / ('TEST-' + name + '.xml')).write_text('<testsuite name="test" tests="1"><testcase name="case"/></testsuite>')
            with self.assertRaises(GateError):
                junit(folder)

    def test_inconsistent_skip_counter_is_refused(self):
        """A producer's summary must agree with the actual XML cases."""
        with tempfile.TemporaryDirectory() as folder:
            (Path(folder) / 'TEST-case.xml').write_text('<testsuite name="test" tests="1" skipped="1"><testcase name="case"/></testsuite>')
            with self.assertRaises(GateError):
                junit(folder)

    def test_case_failure_is_refused_despite_green_xml_header(self):
        """Inspect the actual XML child rather than trusting aggregate counters."""
        with tempfile.TemporaryDirectory() as folder:
            (Path(folder) / 'TEST-case.xml').write_text('<testsuite name="test" tests="1" failures="0"><testcase name="case"><failure/></testcase></testsuite>')
            with self.assertRaises(GateError):
                junit(folder)


if __name__ == '__main__':
    unittest.main()
