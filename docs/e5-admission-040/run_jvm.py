"""Run the existing 80 E5 JVM tests with real JUnit/JSON dependencies and current sources.

Set E5_REPLAY_CLASSPATH to JUnit 4.13.2, Hamcrest 1.3 and JSON 20240303 jars.
Only Android logging and the unused default execution clock are stubbed; every application
source and existing assertion is compiled without replacement. This is not Android execution.
"""

import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = 'com.scenevibe.tvcompanionpoc.'
TESTS = ('E5LocalPlanAndForegroundTest', 'NativeTransportOwnerTest',
         'SceneRuntimeLocalInterludeTest', 'SonyStalePosition039Test', 'NativeDiagnostics039Test')


def main():
    """Compile independent current-source classes and require JUnit's actual successful exit."""
    classpath = os.environ['E5_REPLAY_CLASSPATH']
    with tempfile.TemporaryDirectory(prefix='e5-040-jvm-') as folder:
        temporary = Path(folder)
        stubs = {
            'android/os/SystemClock.java': 'package android.os; public class SystemClock {'
                'public static long elapsedRealtime(){return 100000;}}',
            'android/util/Log.java': 'package android.util; public class Log {'
                'public static int i(String a,String b){return 0;}'
                'public static int w(String a,String b){return 0;}}',
        }
        for name, text in stubs.items():
            path = temporary / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
        sourcepath = os.pathsep.join(str(path) for path in (
            ROOT / 'app/src/main/java', ROOT / 'app/src/test/java', temporary))
        sources = [str(ROOT / 'app/src/test/java' / (PACKAGE + name).replace('.', '/'))
                   + '.java' for name in TESTS]
        subprocess.run(['javac', '-cp', classpath, '-sourcepath', sourcepath,
                        '-d', str(temporary / 'classes'), *sources], check=True)
        subprocess.run(['java', '-cp', str(temporary / 'classes') + os.pathsep + classpath,
                        'org.junit.runner.JUnitCore', *(PACKAGE + name for name in TESTS)], check=True)


if __name__ == '__main__':
    main()
