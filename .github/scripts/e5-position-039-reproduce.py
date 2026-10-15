import os
from pathlib import Path
import shutil
import subprocess
import tempfile

"""Compile the same Sony admission replay against immutable E5-038 and current E5-039.

The old source must really fail with STALE_POSITION before PAUSE, while current source
must pass. No assertions, thresholds, Android clocks or command confirmations are mocked away.
"""

ROOT = Path(__file__).resolve().parents[2]
BASE = 'efd3a7983a03458ec1f919d760c10f1b1f9c57ff'
PACKAGE = 'com/scenevibe/tvcompanionpoc/'


def main():
    """Compile real Java sources twice and retain complete output and the expected old failure."""
    output = ROOT / 'e5-position-039-reproduction'
    output.mkdir(exist_ok=True)
    jars = list(Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))).rglob('junit-4.13.2.jar'))
    jars += list(Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))).rglob('hamcrest-core-1.3.jar'))
    jars += list(Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))).rglob('json-20240303.jar'))
    classpath = os.environ.get('E5_REPLAY_CLASSPATH', os.pathsep.join(str(p) for p in jars))
    if not classpath:
        raise RuntimeError('Build unit tests first to resolve JUnit/JSON dependencies')
    with tempfile.TemporaryDirectory(prefix='e5-039-replay-') as temporary:
        for label in ('before', 'after'):
            source = Path(temporary) / label
            source.mkdir()
            if label == 'before':
                names = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', BASE,
                        'app/src/main/java', 'app/src/test/java/' + PACKAGE + 'E5TestHarness.java'], cwd=ROOT, text=True)
                for name in names.splitlines():
                    path = source / name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(subprocess.check_output(['git', 'show', BASE + ':' + name], cwd=ROOT))
            else:
                shutil.copytree(ROOT / 'app/src/main/java', source / 'app/src/main/java')
                target = source / ('app/src/test/java/' + PACKAGE)
                target.mkdir(parents=True)
                shutil.copyfile(ROOT / ('app/src/test/java/' + PACKAGE + 'E5TestHarness.java'), target / 'E5TestHarness.java')
            replay = source / 'SonyAdmissionReplay.java'
            shutil.copyfile(ROOT / 'docs/e5-position-039/reproduction/SonyAdmissionReplay.java', replay)
            classes = source / 'classes'
            classes.mkdir()
            compile_result = subprocess.run(['javac', '-cp', classpath, '-sourcepath',
                    str(source / 'app/src/main/java') + os.pathsep + str(source / 'app/src/test/java'),
                    '-d', str(classes), str(replay)], text=True, capture_output=True)
            (output / (label + '-compile.txt')).write_text(compile_result.stdout + compile_result.stderr)
            compile_result.check_returncode()
            result = subprocess.run(['java', '-cp', str(classes) + os.pathsep + classpath,
                    'org.junit.runner.JUnitCore', 'com.scenevibe.tvcompanionpoc.SonyAdmissionReplay'],
                    text=True, capture_output=True)
            text = result.stdout + result.stderr
            (output / (label + '-result.txt')).write_text(text)
            if label == 'before':
                if result.returncode == 0 or 'phase=REFUSED reason=STALE_POSITION pauses=0' not in text:
                    raise AssertionError('Immutable E5-038 did not reproduce the Sony admission refusal')
            elif result.returncode != 0 or 'OK (1 test)' not in text:
                raise AssertionError('Corrected admission replay failed')
            print(label + ': ' + ('EXPECTED BASELINE FAILURE' if label == 'before' else 'PASS'))


if __name__ == '__main__':
    main()
