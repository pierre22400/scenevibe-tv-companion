import json
import os
from pathlib import Path
import runpy
import subprocess
import tempfile

"""Compare the real E5-040 and E5-041 selectors under identical controlled Android inputs.

Only Android API objects are test doubles. Actual foreground/session/transport app sources are
compiled twice. The same Java scenarios assert the legacy outcome, command counts and critical
API query order; the instrumented run additionally checks every stable selection/read code.
This is deterministic software evidence, never an Android/Sony physical qualification.
"""

ROOT = Path(__file__).resolve().parents[2]
BASE = '0206a498c412dbf70535295e90426488cadede91'
PACKAGE = 'com/scenevibe/tvcompanionpoc/'
NAMES = ('AndroidNativeSessionPort', 'AndroidForegroundEvidence', 'NativeForegroundProof',
         'NativeSessionSnapshot', 'NativeQualificationPolicy', 'NativeSessionPort', 'NativeTransportOwner')
STUBS = runpy.run_path(str(ROOT / 'docs/e5-admission-040/reproduce_admission.py'))['STUBS']
STUBS.update({
    'android/content/Context.java': '''package android.content;
public class Context {
 public final java.util.Map<Class<?>,Object> services=new java.util.HashMap<>();
 public Class<?> failingService;
 public Context getApplicationContext(){return this;}
 public String getPackageName(){return "com.scenevibe.tvcompanionpoc";}
 public <T> T getSystemService(Class<T> t){
  test.Api.calls.add("service:"+t.getSimpleName());
  if(t==failingService)throw new SecurityException("private-exception-payload");
  return t.cast(services.get(t));
 }
}''',
    'android/app/AppOpsManager.java': '''package android.app;
public class AppOpsManager {
 public static final String OPSTR_GET_USAGE_STATS="usage";public static final int MODE_ALLOWED=0;
 public int mode;public boolean fail;
 public int checkOpNoThrow(String s,int uid,String p){test.Api.calls.add("usage-access");
  if(fail)throw new SecurityException("private-exception-payload");return mode;}
}''',
    'android/app/usage/UsageEvents.java': '''package android.app.usage;
public class UsageEvents {
 public static class Event {
  public int type;public String pkg;public long time;public boolean timestampFails;
  public Event(){}public Event(int t,String p,long at){type=t;pkg=p;time=at;}
  public int getEventType(){return type;}public String getPackageName(){return pkg;}
  public long getTimeStamp(){if(timestampFails)throw new RuntimeException("diagnostic-only");return time;}
 }
 private final java.util.List<Event> values;private int index;public boolean iterationFails;
 public UsageEvents(java.util.List<Event> v){values=v;}
 public boolean hasNextEvent(){if(iterationFails)throw new SecurityException();return index<values.size();}
 public void getNextEvent(Event e){Event v=values.get(index++);e.type=v.type;e.pkg=v.pkg;
  e.time=v.time;e.timestampFails=v.timestampFails;}
}''',
    'android/app/usage/UsageStatsManager.java': '''package android.app.usage;
public class UsageStatsManager {
 public final java.util.List<UsageEvents.Event> events=new java.util.ArrayList<>();
 public boolean nullEvents,queryFails,iterationFails;
 public UsageEvents queryEvents(long begin,long end){test.Api.calls.add("usage-query");
  if(queryFails)throw new SecurityException();if(nullEvents)return null;
  java.util.List<UsageEvents.Event> selected=new java.util.ArrayList<>();
  for(UsageEvents.Event e:events)if(e.time>=begin && e.time<end)selected.add(e);
  UsageEvents result=new UsageEvents(selected);result.iterationFails=iterationFails;return result;
 }
}''',
    'android/media/session/MediaSessionManager.java': '''package android.media.session;
import android.content.ComponentName;
public class MediaSessionManager {
 public java.util.List<MediaController> controllers=new java.util.ArrayList<>();public boolean fail;
 public java.util.List<MediaController> getActiveSessions(ComponentName c){test.Api.calls.add("sessions-query");
  if(fail)throw new SecurityException();return controllers;}
}''',
    'android/media/session/MediaController.java': '''package android.media.session;
import android.media.MediaMetadata;
public class MediaController {
 public String pkg="com.amazon.amazonvideo.livingroom";public Object token=new Object();
 public MediaMetadata metadata=new MediaMetadata(),secondMetadata;
 public PlaybackState state=new PlaybackState(),secondState;
 public boolean firstMetadataNull,secondMetadataNull,firstStateNull,secondStateNull,tokenFails,packageFails;
 public int metadataFailsAt,stateFailsAt,metadataReads,stateReads,pauses,plays;public Runnable afterToken;
 public String getPackageName(){test.Api.calls.add("controller-package");if(packageFails)throw new SecurityException();return pkg;}
 public Object getSessionToken(){test.Api.calls.add("controller-token");if(tokenFails)throw new SecurityException();
  Object t=token;if(afterToken!=null){Runnable r=afterToken;afterToken=null;r.run();}return t;}
 public MediaMetadata getMetadata(){test.Api.calls.add("controller-metadata");metadataReads++;
  if(metadataReads==metadataFailsAt)throw new SecurityException();boolean second=metadataReads%2==0;
  if(second?secondMetadataNull:firstMetadataNull)return null;return second && secondMetadata!=null?secondMetadata:metadata;}
 public PlaybackState getPlaybackState(){test.Api.calls.add("controller-state");stateReads++;
  if(stateReads==stateFailsAt)throw new SecurityException();boolean second=stateReads%2==0;
  if(second?secondStateNull:firstStateNull)return null;return second && secondState!=null?secondState:state;}
 public final class Controls {public void pause(){pauses++;}public void play(){plays++;}}
 public Controls getTransportControls(){return new Controls();}
}''',
    PACKAGE + 'NotificationAccess.java': '''package com.scenevibe.tvcompanionpoc;
final class NotificationAccess {static boolean granted=true,fail;
 static boolean isGranted(android.content.Context c){test.Api.calls.add("notification-access");
  if(fail)throw new SecurityException();return granted;}}
''',
    'test/Api.java': '''package test;
public final class Api {public static final java.util.List<String> calls=new java.util.ArrayList<>();}
''',
})


def main():
    """Compile the same replay against immutable baseline and candidate, then compare outcomes."""
    output = ROOT / 'e5-selection-041-replay'
    output.mkdir(exist_ok=True)
    replay = (ROOT / 'docs/e5-selection-041/Selection041Replay.java').read_text()
    decisions = {}
    with tempfile.TemporaryDirectory(prefix='e5-041-') as folder:
        temporary = Path(folder)
        for label in ('before', 'after'):
            sources = dict(STUBS)
            names = NAMES + (('NativeSelectionTrace',) if label == 'after' else ())
            for name in names:
                relative = 'app/src/main/java/' + PACKAGE + name + '.java'
                sources[PACKAGE + name + '.java'] = (
                    subprocess.check_output(['git', 'show', BASE + ':' + relative], cwd=ROOT).decode()
                    if label == 'before' else (ROOT / relative).read_text())
            sources[PACKAGE + 'Selection041Replay.java'] = replay
            directory = temporary / label
            paths = []
            for name, text in sources.items():
                path = directory / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text)
                paths.append(str(path))
            result = subprocess.run(['javac', '-Xlint:all', '-d', str(directory / 'classes'), *paths],
                                    text=True, capture_output=True)
            (output / (label + '-compile.txt')).write_text(result.stdout + result.stderr)
            result.check_returncode()
            result = subprocess.run(['java', '-ea', '-cp', str(directory / 'classes'),
                                     'com.scenevibe.tvcompanionpoc.Selection041Replay', label],
                                    text=True, capture_output=True)
            (output / (label + '-result.txt')).write_text(result.stdout + result.stderr)
            result.check_returncode()
            decisions[label] = [line for line in result.stdout.splitlines() if line.startswith('DECISION ')]
            print(result.stdout.splitlines()[-1])
    if not decisions['before'] or decisions['before'] != decisions['after']:
        raise AssertionError('Admission/transport/API-call behavior differs from immutable E5-040')
    proof = {'baseHead': BASE, 'scenarios': len(decisions['after']), 'differences': 0,
             'compared': 'decision, reason, command counts and critical Android query order',
             'androidDeviceExecution': False}
    (output / 'equivalence.json').write_text(json.dumps(proof, indent=2) + '\n')
    print(json.dumps(proof))


if __name__ == '__main__':
    main()
