import json
from pathlib import Path
import runpy
import subprocess
import tempfile

"""Compile actual E5-041/E5-042 owners and replay the physical signature plus guard regressions.

Android API seams are controlled inputs only. The before source is pinned to the verified Git
base; the after source is the working candidate. No foreground/transport model replaces them.
"""

ROOT = Path(__file__).resolve().parents[2]
BASE = 'bb13d7cf7a1448fc23f6b5e0aebb5c6a1a022ec2'
P = 'com/scenevibe/tvcompanionpoc/'
LEGACY = runpy.run_path(str(ROOT / '.github/scripts/e5-selection-041-replay.py'))
STUBS = dict(LEGACY['STUBS'])
STUBS['android/app/usage/UsageEvents.java'] = STUBS['android/app/usage/UsageEvents.java'].replace(
    'public int type;public String pkg;', 'public String activity="Player";public int type;public String pkg;').replace(
    'public int getEventType()', 'public String getClassName(){return activity;}public int getEventType()').replace(
    'public void getNextEvent(Event e)', 'public boolean getNextEvent(Event e)').replace(
    'e.time=v.time;e.timestampFails=v.timestampFails;', 'e.time=v.time;e.activity=v.activity;e.timestampFails=v.timestampFails;return true;')
STUBS['android/app/usage/UsageStatsManager.java'] = STUBS['android/app/usage/UsageStatsManager.java'].replace(
    'public boolean nullEvents,queryFails,iterationFails;', 'public Runnable afterQuery; public boolean nullEvents,queryFails,iterationFails;').replace(
    'return result;', 'if(afterQuery!=null)afterQuery.run();return result;')
STUBS.update({
    'android/os/PowerManager.java': 'package android.os; public class PowerManager {public boolean interactive=true;public boolean isInteractive(){return interactive;}}',
    'android/os/UserManager.java': 'package android.os; public class UserManager {public boolean unlocked=true;public boolean isUserUnlocked(){return unlocked;}}',
    'android/app/KeyguardManager.java': 'package android.app; public class KeyguardManager {public boolean locked;public boolean isKeyguardLocked(){return locked;}}',
})


def main():
    """Compile each pinned/current variant, execute assertions, and retain exact stdout evidence."""
    output = ROOT / 'e5-foreground-042-replay'
    output.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='e5-042-') as folder:
        for label in ('before', 'after'):
            sources = dict(STUBS)
            names = LEGACY['NAMES'] + ('NativeSelectionTrace',) + (('NativeForegroundHistory',) if label == 'after' else ())
            for name in names:
                relative = 'app/src/main/java/' + P + name + '.java'
                sources[P + name + '.java'] = (subprocess.check_output(['git', 'show', BASE + ':' + relative], cwd=ROOT).decode()
                    if label == 'before' else (ROOT / relative).read_text())
            sources[P + 'Foreground042Replay.java'] = (ROOT / 'docs/e5-foreground-042/Foreground042Replay.java').read_text()
            directory = Path(folder) / label
            paths = []
            for name, content in sources.items():
                path = directory / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
                paths.append(str(path))
            result = subprocess.run(['javac', '-Xlint:all', '-d', str(directory / 'classes'), *paths], text=True, capture_output=True)
            (output / (label + '-compile.txt')).write_text(result.stdout + result.stderr)
            result.check_returncode()
            result = subprocess.run(['java', '-ea', '-cp', str(directory / 'classes'),
                'com.scenevibe.tvcompanionpoc.Foreground042Replay', label], text=True, capture_output=True)
            (output / (label + '-result.txt')).write_text(result.stdout + result.stderr)
            print(result.stdout + result.stderr)
            result.check_returncode()


if __name__ == '__main__':
    main()
