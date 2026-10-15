package com.scenevibe.tvcompanionpoc;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Identical native-input scenarios exercise the actual pre/post selectors, not a rewritten model. */
public final class Selection041Replay {
    private static boolean instrumented;
    private static int scenarios;
    private static final Set<String> codes = new HashSet<>();
    private static final Set<String> foregroundCodes = new HashSet<>();
    private static final Set<String> readCodes = new HashSet<>();
    private static final Set<String> metadataDifferences = new HashSet<>();
    private static final NativeQualificationPolicy POLICY = NativeQualificationPolicy.local035();
    private static final String PRIME = NativeQualificationPolicy.PRIME_PACKAGE;

    /** One independently configured Android world; transport commands never auto-confirm states. */
    private static final class World {
        final android.content.Context context = new android.content.Context();
        final android.app.AppOpsManager access = new android.app.AppOpsManager();
        final UsageStatsManager usage = new UsageStatsManager();
        final MediaSessionManager sessions = new MediaSessionManager();
        final MediaController prime = new MediaController();
        final AndroidNativeSessionPort port;
        final NativeTransportOwner owner;
        Object trace;
        boolean observerThrows;
        boolean observerForges;

        /** Build a fresh real adapter with optional observation sink, identical inputs in both runs. */
        World() throws Exception {
            SystemClock.now = 49073105;
            NotificationAccess.granted = true;
            NotificationAccess.fail = false;
            test.Api.calls.clear();
            context.services.put(android.app.AppOpsManager.class, access);
            context.services.put(UsageStatsManager.class, usage);
            context.services.put(MediaSessionManager.class, sessions);
            usage.events.add(new UsageEvents.Event(1, PRIME, System.currentTimeMillis() - 1000));
            sessions.controllers.add(prime);
            AndroidForegroundEvidence foreground = new AndroidForegroundEvidence(context);
            if (instrumented) {
                Constructor<AndroidNativeSessionPort> constructor = AndroidNativeSessionPort.class
                        .getDeclaredConstructor(android.content.Context.class,
                                AndroidForegroundEvidence.class, Consumer.class);
                port = constructor.newInstance(context, foreground, (Consumer<Object>) this::observed);
            } else {
                port = new AndroidNativeSessionPort(context, foreground);
            }
            owner = new NativeTransportOwner(port, POLICY, (e, g, s, r) -> {}, SystemClock::elapsedRealtime);
        }

        /** Only diagnostic data is mutable; changing its code or throwing cannot change transport. */
        private void observed(Object value) {
            trace = value;
            if (observerForges) {
                try {
                    Field code = value.getClass().getDeclaredField("code");
                    code.setAccessible(true);
                    code.set(value, code.getType().getDeclaredField("SELECTED").get(null));
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
            }
            if (observerThrows) throw new IllegalStateException("diagnostic observer unavailable");
        }

        /** Publish a PAUSED state independently from the already sent command. */
        void paused() {
            SystemClock.now += 100;
            prime.state = new PlaybackState();
            prime.state.state = 2;
            prime.state.speed = 0;
            prime.state.position = 2914668;
            prime.state.updated = SystemClock.now;
            require(owner.confirmPaused(1, SystemClock.now) == NativeTransportOwner.Result.CONFIRMED, "PAUSED observation");
        }
    }

    /** Stop at the first actual discrepancy rather than counting a sent command as a PASS. */
    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    /** Read a primitive/enum diagnostic field without linking the baseline to new app types. */
    private static String field(Object object, String name) throws Exception {
        Field value = object.getClass().getDeclaredField(name);
        value.setAccessible(true);
        return String.valueOf(value.get(object));
    }

    /** Assert candidate-only branch diagnostics; the shared admission expectations remain separate. */
    private static void diagnostic(World world, String code, String foreground, String read) throws Exception {
        if (!instrumented) return;
        require(world.trace != null, "missing query diagnostic");
        require(field(world.trace, "code").equals(code), "code " + code + " got " + field(world.trace, "code"));
        require(field(world.trace, "foreground").equals(foreground), "foreground " + foreground);
        require(field(world.trace, "read").equals(read), "read " + read);
        codes.add(code);
        foregroundCodes.add(foreground);
        readCodes.add(read);
        metadataDifferences.add(field(world.trace,"metadataDifference"));
        System.out.println("DIAGNOSTIC " + code + "/" + foreground + "/" + read);
    }

    /** Preserve critical API query order in addition to the final decision and command counts. */
    private static void outcome(String name, World world, String result) {
        scenarios++;
        System.out.println("DECISION " + name + " result=" + result + " reason=" + world.owner.reason()
                + " pauses=" + world.prime.pauses + " plays=" + world.prime.plays
                + " calls=" + String.join(",", test.Api.calls));
    }

    /** A null-selection refusal must remain TARGET with no accepted transport command. */
    private static void denied(String name, World world, boolean vod, String code,
            String foreground, String read) throws Exception {
        require(!world.owner.requestPause(1, SystemClock.now, vod), name + " admitted");
        require(world.owner.reason() == NativeTransportOwner.Reason.TARGET, name + " not TARGET");
        require(world.prime.pauses == 0 && world.prime.plays == 0, name + " command");
        diagnostic(world, code, foreground, read);
        outcome(name, world, "REFUSED");
    }

    /** Exceptions before the historical native catch must still propagate, without any command. */
    private static void exception(String name, World world, String code) throws Exception {
        try {
            world.owner.requestPause(1, SystemClock.now, true);
            throw new AssertionError("legacy exception disappeared");
        } catch (SecurityException expected) {
            require(world.prime.pauses == 0 && world.prime.plays == 0, "exception command");
            diagnostic(world, code, code.equals("FOREGROUND_EXCEPTION") ? "SYSTEM_EXCEPTION" : "PRIME_ALONE", "NOT_EVALUATED");
            outcome(name, world, "SecurityException");
        }
    }

    /** Require all defined terminal codes, including coherent acceptance and every read rejection. */
    private static void completeCoverage(String nested, Set<String> observed, String excluded) throws Exception {
        if (!instrumented) return;
        Class<?> type = Class.forName("com.scenevibe.tvcompanionpoc.NativeSelectionTrace$" + nested);
        for (Object constant : type.getEnumConstants()) {
            String name = constant.toString();
            require(name.equals(excluded) || observed.contains(name), "unexecuted diagnostic " + nested + "." + name);
        }
    }

    /** Exercise the actual selected target through observed pause, normal EOF and observed resume. */
    private static void successfulCycle() throws Exception {
        World w = new World();
        require(w.owner.requestPause(1, SystemClock.now, true), "valid attested unknown duration");
        diagnostic(w, "SELECTED", "PRIME_ALONE", "COHERENT");
        w.paused();
        if (instrumented) require(field(w.trace, "operation").equals("OBSERVE"), "observe operation");
        SystemClock.now += 10000;
        require(w.owner.requestPlay(1, SystemClock.now, true), "normal EOF resume");
        if (instrumented) require(field(w.trace, "operation").equals("PLAY"), "play operation");
        SystemClock.now += 100;
        w.prime.state = new PlaybackState();
        w.prime.state.position = 2914768;
        w.prime.state.updated = SystemClock.now;
        require(w.owner.confirmPlaying(1, SystemClock.now) == NativeTransportOwner.Result.CONFIRMED, "PLAYING observation");
        require(w.prime.pauses == 1 && w.prime.plays == 1, "single pause/play");
        outcome("complete-vod-cycle", w, "CONFIRMED");
    }

    /** Execute all distinct null exits, evidence races, unchanged guards and diagnostic fault paths. */
    public static void main(String[] args) throws Exception {
        instrumented = args[0].equals("after");
        World w = new World();
        denied("vod-missing", w, false, "VOD_NOT_ATTESTED", "NOT_EVALUATED", "NOT_EVALUATED");
        w = new World();w.access.mode = 1;
        denied("usage-denied", w, true, "FOREGROUND_REFUSED", "ACCESS_DENIED", "NOT_EVALUATED");
        w = new World();w.context.services.remove(android.app.AppOpsManager.class);
        denied("usage-ops-absent", w, true, "FOREGROUND_REFUSED", "ACCESS_DENIED", "NOT_EVALUATED");
        w = new World();w.context.services.remove(UsageStatsManager.class);
        denied("usage-manager-absent", w, true, "FOREGROUND_REFUSED", "MANAGER_UNAVAILABLE", "NOT_EVALUATED");
        w = new World();w.usage.nullEvents = true;
        denied("usage-list-absent", w, true, "FOREGROUND_REFUSED", "EVENTS_UNAVAILABLE", "NOT_EVALUATED");
        w = new World();w.usage.events.clear();
        denied("usage-empty", w, true, "FOREGROUND_REFUSED", "NO_RELEVANT_EVENTS", "NOT_EVALUATED");
        w = new World();w.usage.events.clear();w.usage.events.add(new UsageEvents.Event(99, PRIME, System.currentTimeMillis() - 100));
        denied("usage-unrelated", w, true, "FOREGROUND_REFUSED", "NO_RELEVANT_EVENTS", "NOT_EVALUATED");
        w = new World();w.usage.events.clear();w.usage.events.add(new UsageEvents.Event(1, PRIME, System.currentTimeMillis() - 31000));
        denied("usage-old-resume", w, true, "FOREGROUND_REFUSED", "NO_RELEVANT_EVENTS", "NOT_EVALUATED");
        w = new World();for(int i=0;i<1025;i++)w.usage.events.add(new UsageEvents.Event(99, "other", System.currentTimeMillis()-100));
        denied("usage-limit", w, true, "FOREGROUND_REFUSED", "EVENT_LIMIT", "NOT_EVALUATED");
        w = new World();w.usage.events.add(new UsageEvents.Event(1, "other", System.currentTimeMillis()-100));
        denied("foreground-ambiguous", w, true, "FOREGROUND_REFUSED", "AMBIGUOUS", "NOT_EVALUATED");
        w = new World();w.usage.events.add(new UsageEvents.Event(2, PRIME, System.currentTimeMillis()-100));
        denied("foreground-left", w, true, "FOREGROUND_REFUSED", "PRIME_NOT_FOREGROUND", "NOT_EVALUATED");
        w = new World();w.usage.events.clear();w.usage.events.add(new UsageEvents.Event(1,"other",System.currentTimeMillis()-100));
        denied("foreground-other-alone",w,true,"FOREGROUND_REFUSED","PRIME_NOT_FOREGROUND","NOT_EVALUATED");
        w = new World();w.access.fail=true;exception("ops-exception", w, "FOREGROUND_EXCEPTION");
        w = new World();w.usage.queryFails=true;exception("usage-query-exception", w, "FOREGROUND_EXCEPTION");
        w = new World();w.usage.iterationFails=true;exception("usage-iteration-exception", w, "FOREGROUND_EXCEPTION");
        w = new World();NotificationAccess.granted=false;
        denied("notification-denied", w, true, "NOTIFICATION_ACCESS_DENIED", "PRIME_ALONE", "NOT_EVALUATED");
        w = new World();NotificationAccess.fail=true;exception("notification-exception", w, "NOTIFICATION_ACCESS_EXCEPTION");
        w = new World();w.context.services.remove(MediaSessionManager.class);
        denied("session-manager-absent", w, true, "SESSION_MANAGER_UNAVAILABLE", "PRIME_ALONE", "NOT_EVALUATED");
        w = new World();w.sessions.controllers=null;
        denied("session-list-absent", w, true, "SESSION_LIST_UNAVAILABLE", "PRIME_ALONE", "NOT_EVALUATED");
        w = new World();w.sessions.controllers.clear();
        denied("prime-absent", w, true, "NO_PRIME_SESSION", "PRIME_ALONE", "NOT_EVALUATED");
        w = new World();w.prime.pkg="other";w.prime.state.state=2;
        denied("wrong-paused-package", w, true, "NO_PRIME_SESSION", "PRIME_ALONE", "COHERENT");
        w = new World();w.sessions.controllers.add(new MediaController());
        denied("multiple-prime", w, true, "MULTIPLE_PRIME_SESSIONS", "PRIME_ALONE", "COHERENT");
        w = new World();MediaController other=new MediaController();other.pkg="other";w.sessions.controllers.add(other);
        denied("other-playing", w, true, "COMPETING_SESSION", "PRIME_ALONE", "COHERENT");
        w = new World();w.sessions.controllers.add(null);
        denied("null-controller", w, true, "UNEXPECTED_CONTROLLER_ENTRY", "PRIME_ALONE", "COHERENT");
        w = new World();w.prime.pkg=null;
        denied("null-package", w, true, "UNEXPECTED_PACKAGE", "PRIME_ALONE", "NOT_EVALUATED");
        w = new World();w.sessions.fail=true;
        denied("sessions-query-exception", w, true, "SYSTEM_EXCEPTION", "PRIME_ALONE", "NOT_EVALUATED");
        w = new World();w.context.failingService=MediaSessionManager.class;
        denied("sessions-manager-exception", w, true, "SYSTEM_EXCEPTION", "PRIME_ALONE", "NOT_EVALUATED");
        w = new World();w.prime.packageFails=true;
        denied("package-read-exception", w, true, "SYSTEM_EXCEPTION", "PRIME_ALONE", "NOT_EVALUATED");
        for(int read=1;read<=2;read++) {
            w = new World();w.prime.metadataFailsAt=read;
            denied("metadata-exception-"+read,w,true,"SYSTEM_EXCEPTION","PRIME_ALONE","NOT_EVALUATED");
            w = new World();w.prime.stateFailsAt=read;
            denied("state-exception-"+read,w,true,"SYSTEM_EXCEPTION","PRIME_ALONE","NOT_EVALUATED");
            w = new World();w.prime.firstMetadataNull=read==1;w.prime.secondMetadataNull=read==2;
            denied("metadata-missing-"+read,w,true,"NATIVE_READ_REFUSED","PRIME_ALONE","METADATA_MISSING");
            w = new World();w.prime.firstStateNull=read==1;w.prime.secondStateNull=read==2;
            denied("state-missing-"+read,w,true,"NATIVE_READ_REFUSED","PRIME_ALONE","STATE_MISSING");
        }
        for(String changed:new String[]{"id","title","subtitle","duration"}) {
            w = new World();w.prime.secondMetadata=new android.media.MediaMetadata();
            if(changed.equals("id"))w.prime.secondMetadata.id="private-replacement";
            if(changed.equals("title"))w.prime.secondMetadata.title="private-title";
            if(changed.equals("subtitle"))w.prime.secondMetadata.subtitle="private-subtitle";
            if(changed.equals("duration"))w.prime.secondMetadata.duration=42;
            denied("metadata-change-"+changed,w,true,"NATIVE_READ_REFUSED","PRIME_ALONE","METADATA_CHANGED");
            if(instrumented)require(field(w.trace,"metadataDifference").equals(changed.equals("id")?"MEDIA_ID":changed.toUpperCase()),"metadata field category");
        }
        for(String changed:new String[]{"state","position","updated","speed","actions"}) {
            w = new World();w.prime.secondState=new PlaybackState();
            if(changed.equals("state"))w.prime.secondState.state=2;
            if(changed.equals("position"))w.prime.secondState.position++;
            if(changed.equals("updated"))w.prime.secondState.updated++;
            if(changed.equals("speed"))w.prime.secondState.speed=0;
            if(changed.equals("actions"))w.prime.secondState.actions=0;
            String expected=changed.equals("updated")?"UPDATE_TIME_CHANGED":changed.toUpperCase()+"_CHANGED";
            denied("state-change-"+changed,w,true,"NATIVE_READ_REFUSED","PRIME_ALONE",expected);
        }
        w = new World();w.prime.tokenFails=true;
        denied("token-read-exception",w,true,"SYSTEM_EXCEPTION","PRIME_ALONE","COHERENT");
        w = new World();MediaController self=new MediaController();self.pkg="com.scenevibe.tvcompanionpoc";self.firstMetadataNull=true;
        w.sessions.controllers.add(0,self);MediaController experiment=new MediaController();experiment.pkg="com.scenevibe.tvcompanionpoc.mediaexperiment.synthetic";
        experiment.firstMetadataNull=true;w.sessions.controllers.add(experiment);
        require(w.owner.requestPause(1,SystemClock.now,true),"self/experiment exclusion changed");
        diagnostic(w,"SELECTED","PRIME_ALONE","COHERENT");
        if(instrumented)require(field(w.trace,"ignoredSessions").equals("2"),"ignored session count");
        outcome("self-and-experiment-ignored",w,"PAUSE_REQUESTED");
        w = new World();other=new MediaController();other.pkg="other";other.state.state=2;w.sessions.controllers.add(other);
        require(w.owner.requestPause(1,SystemClock.now,true),"coherent paused other changed admission");
        diagnostic(w,"SELECTED","PRIME_ALONE","COHERENT");
        if(instrumented)require(field(w.trace,"readWasPrime").equals("false"),"last-read controller role");
        outcome("coherent-other-paused",w,"PAUSE_REQUESTED");
        w = new World();other=new MediaController();other.pkg="other";other.state.state=2;other.firstMetadataNull=true;w.sessions.controllers.add(other);
        denied("other-paused-missing-metadata",w,true,"NATIVE_READ_REFUSED","PRIME_ALONE","METADATA_MISSING");
        w = new World();final MediaController changedTarget=w.prime;changedTarget.afterToken=()->changedTarget.token=new Object();
        require(!w.owner.requestPause(1,SystemClock.now,true) && w.prime.pauses==0
                && w.owner.reason()==NativeTransportOwner.Reason.PAUSE_DISPATCH_FAILED,"changed dispatch target");
        diagnostic(w,"SELECTED","PRIME_ALONE","COHERENT");outcome("target-changed-before-pause",w,"REFUSED");
        w = new World();w.prime.state.state=0;w.prime.state.position=0;w.prime.state.updated=SystemClock.now-35907;w.prime.metadata.id="";
        require(!w.owner.requestPause(1,SystemClock.now,true) && w.owner.reason()==NativeTransportOwner.Reason.STALE_POSITION,"old Sony state");
        diagnostic(w,"SELECTED","PRIME_ALONE","COHERENT");outcome("sony-none-old",w,"REFUSED");
        w = new World();w.prime.state.updated=SystemClock.now-35907;
        require(!w.owner.requestPause(1,SystemClock.now,true) && w.owner.reason()==NativeTransportOwner.Reason.STALE_POSITION,"actual stale");
        diagnostic(w,"SELECTED","PRIME_ALONE","COHERENT");outcome("real-stale-position",w,"REFUSED");
        w = new World();w.observerThrows=true;
        require(w.owner.requestPause(1,SystemClock.now,true),"diagnostic failure changed valid admission");
        diagnostic(w,"SELECTED","PRIME_ALONE","COHERENT");outcome("observer-throws-valid",w,"PAUSE_REQUESTED");
        w = new World();w.observerForges=true;w.sessions.controllers.clear();
        require(!w.owner.requestPause(1,SystemClock.now,true) && w.prime.pauses==0,"diagnostics authorized transport");
        outcome("forged-selected-still-refused",w,"REFUSED");
        w = new World();w.usage.events.get(0).timestampFails=true;
        require(w.owner.requestPause(1,SystemClock.now,true),"extra event timestamp changed admission");
        diagnostic(w,"SELECTED","PRIME_ALONE","COHERENT");outcome("diagnostic-timestamp-failure",w,"PAUSE_REQUESTED");
        w = new World();w.sessions.controllers=new ArrayList<MediaController>() {
            private static final long serialVersionUID=1L;
            /** A diagnostic-only list count cannot turn a valid iterator into a refusal. */
            @Override public int size(){throw new IllegalStateException("diagnostic size");}
        };w.sessions.controllers.add(w.prime);
        require(w.owner.requestPause(1,SystemClock.now,true),"extra list size changed admission");
        diagnostic(w,"SELECTED","PRIME_ALONE","COHERENT");outcome("diagnostic-session-count-failure",w,"PAUSE_REQUESTED");
        successfulCycle();
        completeCoverage("Code",codes,"PENDING");
        completeCoverage("Foreground",foregroundCodes,"");
        completeCoverage("Read",readCodes,"");
        completeCoverage("MetadataDifference",metadataDifferences,"");
        System.out.println("SCENARIOS="+scenarios+" FAILURES=0 instrumented="+instrumented+" ANDROID_DEVICE_EXECUTION=false");
    }
}
