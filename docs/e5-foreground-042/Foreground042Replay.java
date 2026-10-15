package com.scenevibe.tvcompanionpoc;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;

/** Execute actual foreground/session/transport sources with controlled Android boundary inputs. */
public final class Foreground042Replay {
    private static final String PRIME = NativeQualificationPolicy.PRIME_PACKAGE;
    private static final NativeQualificationPolicy POLICY = NativeQualificationPolicy.local035();
    private static int cases;

    /** One fresh world has a retained startup, an old explicit resume and no recent resume. */
    private static final class World {
        final android.content.Context context = new android.content.Context();
        final UsageStatsManager usage = new UsageStatsManager();
        final MediaSessionManager sessions = new MediaSessionManager();
        final MediaController prime = new MediaController();
        final android.os.PowerManager power = new android.os.PowerManager();
        final android.app.KeyguardManager keyguard = new android.app.KeyguardManager();
        final android.os.UserManager user = new android.os.UserManager();
        final AndroidNativeSessionPort port;
        final NativeTransportOwner owner;
        final long wall = System.currentTimeMillis();

        /** Bind actual owners with fresh independently published metadata and playback values. */
        World() {
            SystemClock.now = 49073105;
            NotificationAccess.granted = true;
            NotificationAccess.fail = false;
            context.services.put(android.app.AppOpsManager.class, new android.app.AppOpsManager());
            context.services.put(UsageStatsManager.class, usage);
            context.services.put(MediaSessionManager.class, sessions);
            context.services.put(android.os.PowerManager.class, power);
            context.services.put(android.os.UserManager.class, user);
            context.services.put(android.app.KeyguardManager.class, keyguard);
            add(27, null, null, 120000);
            add(1, PRIME, "Player", 60000);
            add(7, PRIME, "Player", 1000);
            sessions.controllers.add(prime);
            port = new AndroidNativeSessionPort(context, new AndroidForegroundEvidence(context));
            owner = new NativeTransportOwner(port, POLICY, (e,g,s,r) -> {}, SystemClock::elapsedRealtime);
        }

        /** Append a public lifecycle event relative to the independent wall clock. */
        void add(int type, String pkg, String activity, long age) {
            UsageEvents.Event e = new UsageEvents.Event(type, pkg, wall - age);
            e.activity = activity;
            usage.events.add(e);
        }

        /** Publish PAUSED independently after the actual request, then require its confirmation. */
        void paused() {
            SystemClock.now += 100;
            prime.state = new PlaybackState();
            prime.state.state = 2;
            prime.state.speed = 0;
            prime.state.position = 2914668;
            prime.state.updated = SystemClock.now;
            check(owner.confirmPaused(1, SystemClock.now) == NativeTransportOwner.Result.CONFIRMED,
                    "pause confirmation");
        }
    }

    /** Record one assertion or stop immediately on the first unsafe transport outcome. */
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    /** Refusal must never issue either command, irrespective of the exposed refusal category. */
    private static void refused(String label, World w) {
        check(!w.owner.requestPause(1, SystemClock.now, true), label);
        check(w.prime.pauses == 0 && w.prime.plays == 0, label + " transport");
        cases++;
    }

    /** Replay the physical signature before/after, then exercise all retained native guards. */
    public static void main(String[] args) {
        World w = new World();
        boolean selected = w.port.select(true) != null;
        if (args[0].equals("before")) {
            check(!selected, "E5-041 signature must refuse the one irrelevant recent event");
            System.out.println("BEFORE foreground=NO_RELEVANT_EVENTS selection=FOREGROUND_REFUSED commands=0");
            return;
        }
        check(selected, "old explicit resume with coherent epoch survives 30 seconds");
        cases++;
        w = new World();
        w.add(2, PRIME, "Player", 500);
        refused("departure", w);
        w = new World();
        w.add(2, PRIME, "Player", 500);
        w.add(1, "other", "Other", 400);
        refused("switch app", w);
        w = new World();
        w.add(1, "other", "Other", 500);
        refused("ambiguous multi resume", w);
        w = new World();
        w.usage.events.remove(0);
        refused("retained epoch absent", w);
        w = new World();
        w.usage.events.clear();
        w.add(7, PRIME, "Player", 500);
        refused("incomplete E5-041 slice alone", w);
        w = new World();
        w.add(1, PRIME, "Player", 500);
        refused("contradictory duplicate", w);
        w = new World();
        w.add(2, "other", "Unknown", 500);
        refused("missing lifecycle", w);
        w = new World();
        w.usage.events.get(1).activity = null;
        refused("missing activity identity", w);
        w = new World();
        w.usage.nullEvents = true;
        refused("query unavailable", w);
        w = new World();
        w.power.interactive = false;
        refused("screen off now", w);
        w = new World();
        w.keyguard.locked = true;
        refused("keyguard now", w);
        w = new World();
        w.user.unlocked = false;
        refused("user locked", w);
        w = new World();
        w.sessions.controllers.clear();
        refused("no session", w);
        w = new World();
        MediaController other = new MediaController();
        other.pkg = "other";
        w.sessions.controllers.add(other);
        refused("competing media", w);
        w = new World();
        w.sessions.controllers.add(new MediaController());
        refused("two Prime sessions", w);
        w = new World();
        w.prime.firstMetadataNull = true;
        refused("metadata absent", w);
        w = new World();
        w.prime.secondMetadata = new android.media.MediaMetadata();
        w.prime.secondMetadata.id = "replacement";
        refused("metadata changed during read", w);
        w = new World();
        w.prime.state.updated = SystemClock.now - POLICY.providerMaxAgeMs - 1;
        refused("provider stale", w);
        w = new World();
        w.prime.state.position = -1;
        refused("position invalid", w);
        w = new World();
        check(!w.owner.requestPause(1, SystemClock.now, false) && w.prime.pauses == 0, "VOD absent");
        cases++;
        w = new World();
        final World leaving = w;
        w.prime.afterToken = () -> leaving.add(2, PRIME, "Player", 500);
        refused("departure between select and pause", w);
        w = new World();
        check(w.owner.requestPause(1, SystemClock.now, true), "valid pause");
        w.paused();
        w.add(2, PRIME, "Player", 500);
        check(!w.owner.requestPlay(1, SystemClock.now, true) && w.prime.plays == 0, "leave during interlude");
        cases++;
        w = new World();
        check(w.owner.requestPause(1, SystemClock.now, true), "valid pause");
        w.paused();
        w.prime.token = new Object();
        check(!w.owner.requestPlay(1, SystemClock.now, true) && w.prime.plays == 0, "session replaced");
        cases++;
        w = new World();
        check(w.owner.requestPause(1, SystemClock.now, true), "valid pause");
        w.paused();
        check(!w.owner.requestPlay(1, SystemClock.now, false) && w.prime.plays == 0, "abnormal EOF");
        cases++;
        w = new World();
        check(w.owner.requestPause(1, SystemClock.now, true), "nominal pause");
        w.paused();
        check(w.owner.pauseStillOwned(1, SystemClock.now), "interlude owned");
        SystemClock.now += 10000;
        check(w.owner.requestPlay(1, SystemClock.now, true), "normal EOF play");
        SystemClock.now += 100;
        w.prime.state = new PlaybackState();
        w.prime.state.updated = SystemClock.now;
        w.prime.state.position = 2914768;
        check(w.owner.confirmPlaying(1, SystemClock.now) == NativeTransportOwner.Result.CONFIRMED,
                "independent playing confirmation");
        check(w.prime.pauses == 1 && w.prime.plays == 1, "one command each");
        cases++;
        System.out.println("AFTER PASS scenarios=" + cases + " native=CONTROLLED_ANDROID_INPUTS sony=NOT_EXECUTED");
    }
}
