"""Exercise unchanged E5-039 admission against controlled Android API test doubles.

The adapter, foreground reducer and transport owner are compiled from actual app sources.
Only Android API inputs/command counters are replaced. This proves reachable decisions and
information loss, not which unlogged Android branch ran on Sony or Android compatibility.
Run from any directory with Python 3 and JDK 17; no ADB, network or third-party jars are used.
"""

from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
BASE = 'b97aa06885b264d64274dc65f054ffbf2cd6e53e'
PACKAGE = 'com/scenevibe/tvcompanionpoc/'
SOURCES = ('AndroidNativeSessionPort', 'AndroidForegroundEvidence', 'NativeForegroundProof',
           'NativeSessionSnapshot', 'NativeQualificationPolicy', 'NativeSessionPort',
           'NativeTransportOwner')

# Public Android seams only. The application admission code is never rewritten or substituted.
STUBS = {
    'android/content/Context.java': '''package android.content;
public class Context {
 public final java.util.Map<Class<?>,Object> services = new java.util.HashMap<>();
 public Context getApplicationContext(){return this;}
 public String getPackageName(){return "com.scenevibe.tvcompanionpoc";}
 public <T> T getSystemService(Class<T> type){return type.cast(services.get(type));}
}''',
    'android/content/ComponentName.java': '''package android.content;
public class ComponentName { public ComponentName(Context c,Class<?> t){} }''',
    'android/app/AppOpsManager.java': '''package android.app;
public class AppOpsManager {
 public static final String OPSTR_GET_USAGE_STATS="usage";
 public static final int MODE_ALLOWED=0;
 public int mode=0;
 public int checkOpNoThrow(String s,int uid,String p){return mode;}
}''',
    'android/os/Process.java': '''package android.os;
public class Process { public static int myUid(){return 123;} }''',
    'android/os/SystemClock.java': '''package android.os;
public class SystemClock { public static long now=49073105;
 public static long elapsedRealtime(){return now;} }''',
    'android/app/usage/UsageEvents.java': '''package android.app.usage;
public class UsageEvents {
 public static class Event {
  public int type; public String pkg; public long time;
  public Event(){} public Event(int t,String p,long at){type=t;pkg=p;time=at;}
  public int getEventType(){return type;} public String getPackageName(){return pkg;}
 }
 private final java.util.List<Event> values; private int index;
 public UsageEvents(java.util.List<Event> v){values=v;}
 public boolean hasNextEvent(){return index<values.size();}
 public void getNextEvent(Event e){Event v=values.get(index++);e.type=v.type;e.pkg=v.pkg;e.time=v.time;}
}''',
    'android/app/usage/UsageStatsManager.java': '''package android.app.usage;
public class UsageStatsManager {
 public final java.util.List<UsageEvents.Event> events=new java.util.ArrayList<>();
 public UsageEvents queryEvents(long begin,long end){
  java.util.List<UsageEvents.Event> selected=new java.util.ArrayList<>();
  for(UsageEvents.Event e:events) if(e.time>=begin && e.time<=end) selected.add(e);
  return new UsageEvents(selected);
 }
}''',
    'android/media/MediaMetadata.java': '''package android.media;
public class MediaMetadata {
 public static final String METADATA_KEY_MEDIA_ID="id",METADATA_KEY_TITLE="title",
  METADATA_KEY_DISPLAY_SUBTITLE="subtitle",METADATA_KEY_DURATION="duration";
 public String id="synthetic-vod",title="",subtitle=""; public long duration=-1;
 public String getString(String k){return k.equals("id")?id:k.equals("title")?title:subtitle;}
 public long getLong(String k){return duration;}
}''',
    'android/media/session/PlaybackState.java': '''package android.media.session;
public class PlaybackState {
 public static final int STATE_NONE=0,STATE_STOPPED=1,STATE_PAUSED=2,STATE_ERROR=7;
 public static final long ACTION_PAUSE=2,ACTION_PLAY=4,ACTION_PLAY_PAUSE=512;
 public int state=3; public long position=2914566,updated=49073103,actions=6; public float speed=1;
 public int getState(){return state;} public long getPosition(){return position;}
 public long getLastPositionUpdateTime(){return updated;} public float getPlaybackSpeed(){return speed;}
 public long getActions(){return actions;}
}''',
    'android/media/session/MediaController.java': '''package android.media.session;
import android.media.MediaMetadata;
public class MediaController {
 public String pkg="com.amazon.amazonvideo.livingroom"; public Object token=new Object();
 public MediaMetadata metadata=new MediaMetadata(); public PlaybackState state=new PlaybackState();
 public MediaMetadata secondMetadata; public PlaybackState secondState;
 public int metadataReads,stateReads,pauses,plays; public Runnable afterToken;
 public String getPackageName(){return pkg;}
 public Object getSessionToken(){Object t=token; if(afterToken!=null){Runnable r=afterToken;afterToken=null;r.run();}return t;}
 public MediaMetadata getMetadata(){return (++metadataReads%2==0 && secondMetadata!=null)?secondMetadata:metadata;}
 public PlaybackState getPlaybackState(){return (++stateReads%2==0 && secondState!=null)?secondState:state;}
 public final class Controls { public void pause(){pauses++;} public void play(){plays++;} }
 public Controls getTransportControls(){return new Controls();}
}''',
    'android/media/session/MediaSessionManager.java': '''package android.media.session;
import android.content.ComponentName;
public class MediaSessionManager {
 public java.util.List<MediaController> controllers=new java.util.ArrayList<>();
 public java.util.List<MediaController> getActiveSessions(ComponentName c){return controllers;}
}''',
    PACKAGE + 'NotificationAccess.java': '''package com.scenevibe.tvcompanionpoc;
final class NotificationAccess {static boolean granted=true;
 static boolean isGranted(android.content.Context c){return granted;} }''',
    PACKAGE + 'MediaSessionAccessService.java': '''package com.scenevibe.tvcompanionpoc;
final class MediaSessionAccessService {}''',
}

REPLAY = r'''package com.scenevibe.tvcompanionpoc;
import android.app.usage.*;
import android.media.session.*;
import android.os.SystemClock;
import java.util.*;

/** Controlled inputs execute the unchanged selection and dispatch code, with independent states. */
public final class Admission040Replay {
 static int passed;
 static final String PRIME=NativeQualificationPolicy.PRIME_PACKAGE;
 static final NativeQualificationPolicy POLICY=NativeQualificationPolicy.local035();
 static final class Case {
  final android.content.Context context=new android.content.Context();
  final android.app.AppOpsManager access=new android.app.AppOpsManager();
  final UsageStatsManager usage=new UsageStatsManager();
  final MediaSessionManager manager=new MediaSessionManager();
  final MediaController prime=new MediaController();
  final AndroidNativeSessionPort port;
  final NativeTransportOwner owner;
  NativeSessionSnapshot checked;
  NativeQualificationPolicy.Evidence evidence;
  Case(){
   SystemClock.now=49073105; NotificationAccess.granted=true;
   context.services.put(android.app.AppOpsManager.class,access);
   context.services.put(UsageStatsManager.class,usage);
   context.services.put(MediaSessionManager.class,manager);
   usage.events.add(new UsageEvents.Event(1,PRIME,System.currentTimeMillis()-1000));
   manager.controllers.add(prime);
   port=new AndroidNativeSessionPort(context,new AndroidForegroundEvidence(context));
   owner=new NativeTransportOwner(port,POLICY,new NativeTransportOwner.Diagnostics(){
    public void record(String e,long g,NativeSessionSnapshot s,NativeTransportOwner.Reason r){}
    public void evaluated(String e,long g,NativeSessionSnapshot s,NativeTransportOwner.Reason r,
      long at,NativeQualificationPolicy.Evidence result){checked=s;evidence=result;}
   },SystemClock::elapsedRealtime);
  }
  boolean admit(boolean vod){return owner.requestPause(1,SystemClock.now,vod);}
  void paused(){
   SystemClock.now+=100; prime.state=new PlaybackState();
   prime.state.state=2;prime.state.speed=0;prime.state.position=2914668;prime.state.updated=SystemClock.now;
   require(owner.confirmPaused(1,SystemClock.now)==NativeTransportOwner.Result.CONFIRMED,"independent paused");
  }
 }
 /** Throw on the first discrepancy; a printed PASS is never inferred from a sent command. */
 static void require(boolean yes,String why){if(!yes)throw new AssertionError(why);}
 /** Check the identical lossy observation for each distinct null-selection input. */
 static void target(String label,Case c,boolean vod){
  require(!c.admit(vod),label+" refused");
  require(c.owner.reason()==NativeTransportOwner.Reason.TARGET,label+" TARGET");
  require(c.checked==null && c.evidence==NativeQualificationPolicy.Evidence.SAMPLE_MISSING,label+" missing");
  require(c.prime.pauses==0 && c.prime.plays==0,label+" no transport");
  ok(label+" TARGET snapshot=null guard=NO_OBSERVATION pause=0 play=0");
 }
 /** Report one scenario only after all of its assertions passed. */
 static void ok(String label){passed++;System.out.println("PASS "+label);}
 /** Execute both observed refusal shapes, indistinguishable alternatives and safety paths. */
 public static void main(String[] args){
  Case c=new Case(); c.usage.events.clear(); target("foreground-empty",c,true);
  c=new Case();c.usage.events.clear();
  c.usage.events.add(new UsageEvents.Event(1,PRIME,System.currentTimeMillis()-31000));
  target("foreground-resume-outside-30s",c,true);
  c=new Case();c.usage.events.add(new UsageEvents.Event(1,"other.app",System.currentTimeMillis()-500));
  target("foreground-concurrent",c,true);
  c=new Case();c.access.mode=1;target("usage-access-denied",c,true);
  c=new Case();NotificationAccess.granted=false;target("notification-access-denied",c,true);
  c=new Case();c.manager.controllers.clear();target("prime-session-absent",c,true);
  c=new Case();c.prime.pkg="other.app";target("wrong-package",c,true);
  c=new Case();MediaController other=new MediaController();other.pkg="other.app";
  c.manager.controllers.add(other);target("competing-playing-session",c,true);
  c=new Case();c.prime.secondState=new PlaybackState();c.prime.secondState.updated++;
  target("provider-update-between-double-read",c,true);
  c=new Case();c.prime.secondMetadata=new android.media.MediaMetadata();c.prime.secondMetadata.id="replacement";
  target("metadata-change-between-double-read",c,true);
  c=new Case();target("vod-not-attested",c,false);
  c=new Case();c.prime.metadata.duration=9000000;target("live-not-attested",c,false);
  c=new Case();SystemClock.now=49057465;c.prime.state.state=0;c.prime.state.position=0;c.prime.state.updated=SystemClock.now-35907;
  c.prime.metadata.id="";
  require(!c.admit(true) && c.owner.reason()==NativeTransportOwner.Reason.STALE_POSITION,"observed shape");
  require(c.evidence==NativeQualificationPolicy.Evidence.DURATION_OR_IDENTITY,"first evidence before age");
  require(c.prime.pauses==0,"no pause of uninitialized state");
  ok("observed-generation-2 STALE_POSITION firstGuard=DURATION_OR_IDENTITY age=35907 pause=0");
  c=new Case();c.prime.state.updated=SystemClock.now-35907;
  require(!c.admit(true) && c.evidence==NativeQualificationPolicy.Evidence.PROVIDER_STALE,"real stale");
  require(c.prime.pauses==0,"stale no pause");ok("genuinely-stale-position REFUSED pause=0");
  c=new Case();c.prime.state.position=-1;
  require(!c.admit(true) && c.evidence==NativeQualificationPolicy.Evidence.POSITION_MISSING,"missing position");
  ok("missing-position REFUSED");
  c=new Case();c.prime.state.updated=1700000000000L;
  require(!c.admit(true) && c.evidence==NativeQualificationPolicy.Evidence.UPDATE_TIME_INVALID,"wall time");
  ok("wall-clock-in-playback-timestamp REFUSED");
  c=new Case();final MediaController changed=c.prime;
  changed.afterToken=()->changed.token=new Object();
  require(!c.admit(true) && c.owner.reason()==NativeTransportOwner.Reason.PAUSE_DISPATCH_FAILED,"dispatch changed");
  require(c.prime.pauses==0,"changed target not paused");ok("token-changed-before-pause REFUSED pause=0");
  c=new Case();require(c.admit(true),"fresh unknown-duration explicit VOD");c.paused();
  SystemClock.now+=10000;
  require(c.owner.requestPlay(1,SystemClock.now,true),"EOF guarded resume");
  SystemClock.now+=100;c.prime.state=new PlaybackState();c.prime.state.position=2914768;c.prime.state.updated=SystemClock.now;
  require(c.owner.confirmPlaying(1,SystemClock.now)==NativeTransportOwner.Result.CONFIRMED,"observed resume");
  require(c.prime.pauses==1 && c.prime.plays==1,"one command each");ok("valid-unknown-duration-vod pause=1 confirmedPAUSED EOF play=1 confirmedPLAYING");
  c=new Case();require(c.admit(true),"admit before competitor");c.paused();
  other=new MediaController();other.pkg="other.app";c.manager.controllers.add(other);
  require(!c.owner.requestPlay(1,SystemClock.now,true) && c.prime.plays==0,"competitor blocks resume");
  ok("concurrent-session-after-pause REFUSED play=0");
  c=new Case();require(c.admit(true),"admit before seek");c.paused();c.prime.state.position++;
  require(!c.owner.requestPlay(1,SystemClock.now,true) && c.prime.plays==0,"one ms seek");
  ok("seek-after-pause REFUSED play=0");
  c=new Case();require(c.admit(true),"admit before interrupt");c.paused();
  require(!c.owner.requestPlay(1,SystemClock.now,false) && c.prime.plays==0,"not normal EOF");
  c.owner.release(1);require(c.prime.plays==0 && !c.owner.hasLease(),"release no play");
  ok("interruption-and-release REFUSED play=0");
  System.out.println("SCENARIOS="+passed+" FAILURES=0 ANDROID_DEVICE_EXECUTION=false");
 }
}
'''


def main():
    """Verify the immutable runtime bytes, compile them, then execute all replay assertions."""
    with tempfile.TemporaryDirectory(prefix='e5-admission-040-') as folder:
        temporary = Path(folder)
        sources = dict(STUBS)
        for name in SOURCES:
            relative = 'app/src/main/java/' + PACKAGE + name + '.java'
            actual = (ROOT / relative).read_bytes()
            expected = subprocess.check_output(['git', 'show', BASE + ':' + relative], cwd=ROOT)
            if actual != expected:
                raise AssertionError('Replay requires unchanged E5-039 source: ' + relative)
            sources[PACKAGE + name + '.java'] = actual.decode()
        sources[PACKAGE + 'Admission040Replay.java'] = REPLAY
        paths = []
        for name, text in sources.items():
            path = temporary / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding='utf-8')
            paths.append(str(path))
        classes = temporary / 'classes'
        subprocess.run(['javac', '-Xlint:all', '-d', str(classes), *paths], check=True)
        subprocess.run(['java', '-ea', '-cp', str(classes),
                        'com.scenevibe.tvcompanionpoc.Admission040Replay'], check=True)


if __name__ == '__main__':
    main()
