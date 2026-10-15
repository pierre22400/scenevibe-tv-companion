import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

"""Exercise the real owner PowerShell with a disposable fake ADB, never a device.

The test host replaces only Start-Sleep with bounded synthetic-fixture publication waits.
Native processes, stdout redirection, both logcat
formats, JSON correlation, terminal refusal and command counts use the actual shipped script.
No test mode, retry or timing bypass is inserted into the owner's script.
"""

ROOT = Path(__file__).resolve().parents[2]
FAKE = r'''#!/usr/bin/env python3
import json
import os
from pathlib import Path
import sys
import time

"""Respond only to the three ADB operations expected from the diagnostic collection script."""

directory = Path(os.environ['FAKE_E5_DIR'])
scenario = os.environ['FAKE_E5_SCENARIO']
args = sys.argv[1:]
with (directory / 'calls.jsonl').open('a') as stream:
    stream.write(json.dumps(args) + '\n')


def emit(event):
    """Print one structured E5 record in the selected real Android logcat text layout."""
    prefix = '10-15 08:30:00.000  1234  1234 I SceneVibeE5: '
    if scenario == 'brief':
        prefix = '10-15 08:30:00.000 I/SceneVibeE5( 1234): '
    print(prefix + json.dumps(event), flush=True)


if 'dumpsys' in args:
    version = 16 if scenario == 'wrong-version' else 17
    print('versionCode=' + str(version) + ' minSdk=26 targetSdk=35')
    print('versionName=0.8.2-tv-release-hardening-e5-native041-diagnostics')
elif 'broadcast' in args:
    if scenario == 'delivery-rejected':
        print('Broadcast completed: result=0, data="REFUSED_MALFORMED"')
    else:
        (directory / 'broadcast').write_text('once')
        print('Broadcast completed: result=1, data="QUEUED_GUARDS_PENDING"')
elif 'logcat' in args:
    emit({'event': 'REQUEST', 'attemptId': 'old-1', 'phase': 'PREPARING', 'terminal': False})
    (directory / 'capture-ready').write_text('published')
    while not (directory / 'broadcast').exists():
        time.sleep(0.01)
    emit({'event': 'REQUEST', 'attemptId': 'new-2', 'phase': 'PREPARING', 'terminal': False})
    if scenario == 'ambiguous':
        emit({'event': 'REQUEST', 'attemptId': 'unexpected-3', 'phase': 'PREPARING', 'terminal': False})
    selection = {'query': 1, 'operation': 'SELECT', 'code': 'FOREGROUND_REFUSED',
                 'foreground': 'NO_RELEVANT_EVENTS', 'read': 'NOT_EVALUATED',
                 'sessionCount': -1, 'primeCandidates': 0}
    emit({'event': 'NATIVE_ADMISSION_CHECK', 'attemptId': 'new-2', 'phase': 'PAUSE_REQUESTED',
          'reason': 'NONE', 'selection': selection, 'terminal': False})
    terminal = {'event': 'CLEANUP', 'attemptId': 'new-2', 'phase': 'REFUSED',
                'reason': 'TARGET', 'terminal': True}
    if scenario != 'missing-selection':
        terminal['selection'] = selection
    emit(terminal)
    (directory / 'emitted').write_text('published')
    while True:
        time.sleep(0.1)
else:
    raise SystemExit('Unexpected fake ADB operation')
'''


def ps_quote(value):
    """Quote a literal PowerShell argument without shell interpolation or executable substitutions."""
    return "'" + str(value).replace("'", "''") + "'"


def main():
    """Require correct refusal reporting, exact attempt selection and one-or-zero broadcasts."""
    pwsh = os.environ.get('PWSH', shutil.which('pwsh'))
    if not pwsh:
        raise RuntimeError('PowerShell required for host execution, not just source inspection')
    results = []
    with tempfile.TemporaryDirectory(prefix='e5-041-powershell-') as temporary:
        for scenario in ('threadtime', 'brief', 'missing-selection', 'wrong-version',
                         'delivery-rejected', 'ambiguous'):
            directory = Path(temporary) / scenario
            directory.mkdir()
            fake = directory / 'adb-fake'
            fake.write_text(FAKE)
            fake.chmod(0o700)
            script = ROOT / 'docs/e5-selection-041/Invoke-E5-041.ps1'
            command = ('function Start-Sleep { param([int]$Seconds,[int]$Milliseconds) '
                       '$name=if($Seconds){"emitted"}else{"capture-ready"}; '
                       '$fixture=Join-Path $env:FAKE_E5_DIR $name; '
                       '$limit=[DateTime]::UtcNow.AddSeconds(5); '
                       'while(-not (Test-Path -LiteralPath $fixture)){ '
                       'if([DateTime]::UtcNow -gt $limit){throw "Fake publication timeout"}; '
                       '[System.Threading.Thread]::Sleep(10) }; '
                       '[System.Threading.Thread]::Sleep(50) }; '
                       '& ' + ps_quote(script) + ' -AdbPath ' + ps_quote(fake)
                       + ' -Serial emulator-FAKE -VodConfirmed -OutputDirectory ' + ps_quote(directory))
            env = dict(os.environ, FAKE_E5_DIR=str(directory), FAKE_E5_SCENARIO=scenario)
            result = subprocess.run([pwsh, '-NoProfile', '-Command', command], env=env,
                                    capture_output=True, text=True, timeout=20)
            calls = [json.loads(line) for line in (directory / 'calls.jsonl').read_text().splitlines()]
            broadcasts = [call for call in calls if 'broadcast' in call]
            expected = 0 if scenario == 'wrong-version' else 1
            if len(broadcasts) != expected:
                raise AssertionError('Unexpected broadcast count: ' + scenario)
            for call in calls:
                if any(token in call for token in ('install', 'input', 'start', 'keyevent', 'play', 'pause')):
                    raise AssertionError('Forbidden native/UI command: ' + scenario)
                if 'logcat' in call and not all(token in call for token in ('SceneVibeE5:V', 'SceneVibeE5Adb:V', '*:S')):
                    raise AssertionError('Unscoped Android log export')
            success = scenario in ('threadtime', 'brief')
            if (result.returncode == 0) != success:
                raise AssertionError(scenario + '\n' + result.stdout + result.stderr)
            if success:
                if not all(value in result.stdout for value in (
                        'new-2', 'REFUSED', 'TARGET', 'FOREGROUND_REFUSED', 'NO_RELEVANT_EVENTS')):
                    raise AssertionError('Missing actual refusal: ' + scenario)
                evidence = json.loads(next(directory.glob('*-attempt.json')).read_text(encoding='utf-8-sig'))
                if any(event['attemptId'] != 'new-2' for event in evidence):
                    raise AssertionError('Old/foreign attempt leaked into diagnostic export')
            results.append({'scenario': scenario, 'pass': True, 'broadcasts': expected,
                            'expectedExitSuccess': success})
    output = ROOT / 'e5-selection-041-powershell.json'
    output.write_text(json.dumps({'cases': results, 'sonyAccess': False}, indent=2) + '\n')
    print('PowerShell fake ADB: 6 PASS; one broadcast per admitted invocation; zero native/UI commands')


if __name__ == '__main__':
    main()
