package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationSnapshot;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.PackageInstaller;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

/**
 * Compose the real generic installer/store, Phase D registry, media scheduler and controller.
 * Only durable disk and native windows are deterministic boundaries. No copied Video parser,
 * handler algorithm, transport, credential object or confirmation callback is composed.
 */
final class M4PhaseEVideoFixtures {
    /** Give the exact frozen package another local revision without changing either artifact. */
    static InstallRequest request(boolean manifested,long revision) throws Exception {
        InstallRequest frozen=M4PhaseDHandlerFixtures.request(manifested);
        return new InstallRequest(revision,frozen.codecId(),frozen.artifacts());
    }

    /** Observe atomic publication independently of activation and confirmed-ACK writes. */
    static final class Backend implements InstallationStore.Backend {
        final Map<String,String> values;
        final List<String> trace=new ArrayList<>();
        int writes,ackWrites,clears;
        boolean writable=true;
        /** Share one exact historical fixture map or an empty deterministic file. */
        Backend(Map<String,String> values) {this.values=values;}
        /** Keep the real store's consistency monitor common across recreated readers. */
        @Override public Object monitor() {return values;}
        /** Snapshot reads expose ordering without logging canonical payloads. */
        @Override public String get(String key) {
            if (InstallationStore.SNAPSHOT_KEY.equals(key)) trace.add("read");
            return values.get(key);
        }
        /** Commit a complete batch atomically, preserving the prior view on deterministic failure. */
        @Override public boolean commit(Map<String,String> puts,Set<String> removed,boolean clear) {
            writes++;trace.add("commit");
            if (puts.containsKey("ackRevision")) ackWrites++;
            if (clear) clears++;
            if (!writable) return false;
            Map<String,String> next=clear?new HashMap<>():new HashMap<>(values);
            next.putAll(puts);for (String key:removed) next.remove(key);
            values.clear();values.putAll(next);return true;
        }
    }

    /** Owner ports delegate to the real scheduler/controller while observing a fake native visual sink. */
    static final class Ports implements VideoInstallationRuntimePorts {
        final List<String> trace;
        final SceneRuntimeController controller;
        final MediaSyncedTrackScheduler scheduler;
        boolean owner=true,legacyVisible,manifestVisible,throwing;
        String failAt;
        long activeRevision;
        int maxVisible,loads,shows;
        ScheduledTrack loaded;
        OverlayManifest armed;

        /** Compose passive media cores with one observed native visual owner. */
        Ports(List<String> trace) {
            this.trace=trace;
            controller=new SceneRuntimeController(new SceneRuntimeController.SceneSink() {
                /** Frozen text scenes need no remote or local asset acquisition. */
                @Override public boolean preflight(OverlayManifest.Scene scene) {return true;}
                /** Observe manifested visibility only when the actual controller shows a due scene. */
                @Override public void show(OverlayManifest.Scene scene) {manifestVisible=true;shows++;observe();}
                /** Native removal is synchronous at this deterministic boundary. */
                @Override public void hide(OverlayManifest.Scene scene) {manifestVisible=false;observe();}
                /** Unload/retirement drops all manifested visuals immediately. */
                @Override public void hideAll() {manifestVisible=false;observe();}
            });
            scheduler=new MediaSyncedTrackScheduler(new MediaSyncedTrackScheduler.Listener() {
                /** Route a due event through the actual controller or the isolated legacy visual. */
                @Override public void onRender(ScheduledTrack.Event event) {
                    if (controller.isSceneRendererActiveFor(activeRevision)) controller.onCommentDue(event);
                    else {legacyVisible=true;shows++;observe();}
                }
                /** Forward passive playback state without introducing a clock or player control. */
                @Override public void onPlayback(boolean playing,boolean freeze) {controller.onPlayback(playing,freeze);}
                /** Real media expiry reaches the same selected controller owner. */
                @Override public void onExpire(ScheduledTrack.Event event) {
                    if (controller.isSceneRendererActiveFor(activeRevision)) controller.onCommentExpired(event);
                }
                /** Load/identity loss cannot cause a second visual owner. */
                @Override public void onEligibility(boolean eligible) {
                    controller.onEligibility(eligible);if (!eligible) legacyVisible=false;observe();
                }
            });
        }
        /** Inject a bounded runtime refusal/exception after an observable partial mutation. */
        private boolean step(String name,Runnable mutation) {
            trace.add(name);mutation.run();observe();
            if (name.equals(failAt)) {
                if (throwing) throw new IllegalStateException("Injected owner-port failure");
                return false;
            }
            return true;
        }
        /** At every mutation, including retirement/failure, at most one fake native owner may be visible. */
        private void observe() {
            int visible=(legacyVisible?1:0)+(manifestVisible?1:0);maxVisible=Math.max(maxVisible,visible);
            assertTrue("one visual owner throughout replacement",visible<=1);
        }
        /** Non-owner calls permit no scheduler/window/controller mutation. */
        @Override public boolean isOwnerThread() {trace.add("owner");return owner;}
        /** Drop the previous legacy visual before any manifested activation. */
        @Override public boolean retireLegacyVisualOwner() {return step("retire-legacy",()->legacyVisible=false);}
        /** Unload the actual controller and invalidate old-generation callbacks synchronously. */
        @Override public boolean retireManifestedVisualOwner() {return step("retire-manifested",()->{controller.unload();armed=null;});}
        /** The production handler supplies its rebuilt trusted track, rather than a copied parser result. */
        @Override public boolean loadPreparedTrack(ScheduledTrack track) {return step("load",()->{loaded=track;loads++;scheduler.load(track);});}
        /** Arm the actual controller with the exact restored manifest/revision; do not show on ARM alone. */
        @Override public boolean armPreparedManifest(long revision,OverlayManifest manifest) {
            return step("manifest",()->{armed=manifest;controller.replaceRevision(revision,manifest);});
        }
        /** Select the revision only after activation succeeds, keeping the real visual selection predicate. */
        @Override public boolean selectActiveRevision(long revision,boolean manifested) {return step("select",()->activeRevision=revision);}
        /** Retire partial activation without persistence, confirmation or transport access. */
        @Override public void abortActivation() {
            trace.add("abort");controller.unload();scheduler.clear();legacyVisible=false;activeRevision=0;armed=null;observe();
        }
        /** Drive the real scheduler using an eligible passive snapshot at the first restored event. */
        void due() {
            long position=loaded.comments.get(0).startMs;
            ScheduledTrack.MediaIdentity media=loaded.mediaIdentity;
            scheduler.onPlaybackSnapshot(new MediaSessionProbe.Snapshot(loaded.targetPackage,
                    android.media.session.PlaybackState.STATE_PLAYING,"PLAYING",position,position,1.0f,0L,
                    media.videoId,media.title,"",media.durationMs));
        }
    }

    /** The only composition is local storage, build-static handlers, capabilities and owner ports. */
    static final class Harness {
        final Backend backend;
        final InstallationStore store;
        final PackageInstaller installer;
        final Ports ports;
        /** Accept an empty or exact historical cache, without migrating or creating any transport. */
        Harness(Map<String,String> values) {
            backend=new Backend(values);store=new InstallationStore(backend);
            installer=new PackageInstaller(store,VideoInstallationHandlers.registry(),TvCapabilities.current());
            ports=new Ports(backend.trace);
        }
        /** Compose a new empty cache with the exact same real dependencies. */
        Harness() {this(new HashMap<>());}
        /** Install frozen bytes at the supplied revision through the real generic lifecycle. */
        InstallationStatus install(boolean manifested,long revision) throws Exception {return installer.install(request(manifested,revision),ports);}
        /** Seed only a test-confirmed prior revision outside PackageInstaller and reset write observation. */
        void confirmPrior(long revision) {
            assertTrue(store.markAcknowledged(revision));resetObservation();
        }
        /** Preserve storage/live state while clearing bounded test stage and write counters. */
        void resetObservation() {backend.writes=0;backend.ackWrites=0;backend.clears=0;backend.trace.clear();}
    }

    /** Compare exact durable artifact names/bytes without leaking commentary into assertion output. */
    static void exact(InstallRequest expected,InstallationSnapshot durable) {
        assertEquals(expected.revision(),durable.revision());assertEquals(expected.codecId(),durable.codecId());
        assertEquals(expected.artifacts().keySet(),durable.canonical().artifacts().keySet());
        for (String key:expected.artifacts().keySet())
            assertTrue("exact canonical artifact bytes",Arrays.equals(expected.artifact(key),durable.canonical().artifact(key)));
    }
    /** Test-only composition helper has no implicit runtime singleton. */
    private M4PhaseEVideoFixtures() {}
}
