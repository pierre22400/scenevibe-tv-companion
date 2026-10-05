package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;
import org.junit.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/**
 * Reproduces the real Cloud -> cache -> scheduler -> regie installation chain
 * with an owner-thread-checking scene sink. Android window calls require that
 * owner; previous worker-thread installation violates it while a scene is visible.
 * Latches prove ACK waiting, stop and cancellation without timing-based sleeps.
 */
public final class AssignmentMutationGateTest {
    /** Own one deterministic UI-like executor and shut it down after each test. */
    private static final class Owner implements AutoCloseable {
        final AtomicReference<Thread> thread=new AtomicReference<>();
        final ExecutorService executor=Executors.newSingleThreadExecutor(work->{
            Thread worker=new Thread(work,"m1-test-window-owner");
            thread.set(worker);
            return worker;
        });
        final AssignmentMutationGate gate=new AssignmentMutationGate(executor,
                ()->Thread.currentThread()==thread.get());
        /** Release the test worker; production uses the existing Android main Looper. */
        @Override public void close() throws Exception {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));
        }
    }

    /** Record atomic cache writes without replacing the production repository logic. */
    private static final class Memory implements CloudTrackRepository.Storage {
        final Map<String,String> values=new HashMap<>();
        /** Read the repository's durable test state. */
        @Override public String get(String key) {return values.get(key);}
        /** Legacy writes atomically remove the previous manifest. */
        @Override public boolean save(long revision,String runtime) {
            return save(revision,runtime,null);
        }
        /** Keep revision, runtime and optional manifest in one test commit. */
        @Override public boolean save(long revision,String runtime,String manifest) {
            values.put("revision",String.valueOf(revision));values.put("runtime",runtime);
            if(manifest==null)values.remove("manifest");else values.put("manifest",manifest);
            return true;
        }
        /** Record successful ACKs independently of runtime persistence. */
        @Override public boolean saveAck(long revision) {
            values.put("ackRevision",String.valueOf(revision));return true;
        }
        /** Implement the existing explicit reset boundary. */
        @Override public void clear() {values.clear();}
    }

    /** Enforce Android's owner constraint only when a visible window is touched. */
    private static final class WindowSink implements SceneRuntimeController.SceneSink {
        final Owner owner;
        boolean visible;
        int hides;
        /** Bind the owner against which actual regie callbacks are checked. */
        WindowSink(Owner owner) {this.owner=owner;}
        /** Fail exactly as a wrong-thread window or animator operation would. */
        private void check() {assertSame("window mutation must use owner",owner.thread.get(),
                Thread.currentThread());}
        /** Text-only preflight still runs on the serial owner chain. */
        @Override public boolean preflight(OverlayManifest.Scene scene) {check();return true;}
        /** Create one simulated native window. */
        @Override public void show(OverlayManifest.Scene scene) {check();visible=true;}
        /** Retire the visible window with an owner-thread assertion. */
        @Override public void hide(OverlayManifest.Scene scene) {
            if(visible){check();hides++;visible=false;}
        }
        /** Model immediate retirement without inventing a media clock. */
        @Override public void hideAll() {
            if(visible){check();hides++;visible=false;}
        }
    }

    /** Read the exact baseline envelope exported by Cloud CI, never a copied fixture. */
    private static JSONObject envelope() throws Exception {
        try(InputStream stream=AssignmentMutationGateTest.class.getResourceAsStream(
                "/m1/cloud-envelopes.json")) {
            assertNotNull(stream);
            return new JSONObject(new String(stream.readAllBytes(),StandardCharsets.UTF_8))
                    .getJSONArray("cases").getJSONObject(0).getJSONObject("envelope");
        }
    }

    /** Wire the same installer cores used by the production OverlayService. */
    private static M4PhaseFHistoricalCloudClient.ManifestInstaller installer(CloudTrackRepository cache,
            MediaSyncedTrackScheduler scheduler,SceneRuntimeController controller) {
        return new M4PhaseFHistoricalCloudClient.ManifestInstaller() {
            /** Install and arm via the production cache/bridge/regie chain. */
            @Override public boolean install(long revision,String runtime,String manifest) {
                return M4PhaseGHistoricalService.installManifestedRevision(cache,scheduler,controller,
                        DiagnosticsStore.INSTANCE,revision,runtime,manifest)>0;
            }
            /** Confirm idempotent arm through the production redelivery core. */
            @Override public boolean confirmArmed(long revision) {
                return M4PhaseGHistoricalService.confirmManifestedRevisionArmed(cache,scheduler,controller,
                        revision)>0;
            }
            /** Preserve the manifested -> legacy production handoff. */
            @Override public boolean activateLegacy(long revision) {
                return M4PhaseGHistoricalService.activateLegacyRevision(controller,revision)>0;
            }
        };
    }

    /** Exercise the scheduler load's real eligibility callback during installation. */
    private static MediaSyncedTrackScheduler scheduler(SceneRuntimeController controller) {
        return new MediaSyncedTrackScheduler(new MediaSyncedTrackScheduler.Listener() {
            /** Forward a due comment through the actual regie. */
            @Override public void onRender(ScheduledTrack.Event event) {
                controller.onCommentDue(event);
            }
            /** Preserve playback as a passive state input. */
            @Override public void onPlayback(boolean playing,boolean freeze) {
                controller.onPlayback(playing,freeze);
            }
            /** This callback previously retired windows from the Cloud io thread. */
            @Override public void onEligibility(boolean eligible) {
                controller.onEligibility(eligible);
            }
        });
    }

    /** Install, display, replace and return to legacy on the owner BEFORE ACK eligibility. */
    @Test public void visibleReplacementAndLegacyHandoffFinishOnOwnerBeforeAck() throws Exception {
        try(Owner owner=new Owner()) {
            JSONObject e=envelope();
            String runtime=e.getJSONObject("runtimeTrack").toString();
            String manifest=e.getJSONObject("overlayManifest").toString();
            Memory memory=new Memory();CloudTrackRepository cache=new CloudTrackRepository(memory);
            WindowSink sink=new WindowSink(owner);
            SceneRuntimeController controller=new SceneRuntimeController(sink);
            MediaSyncedTrackScheduler scheduler=scheduler(controller);
            M4PhaseFHistoricalCloudClient.ManifestInstaller installer=installer(cache,scheduler,controller);
            assertTrue(M4PhaseFHistoricalCloudClient.applyAssignment(owner.gate,()->true,4,runtime,manifest,
                    cache,scheduler,installer));
            ScheduledTrack track=TrackParser.parse(e.getJSONObject("runtimeTrack"),media->null);
            owner.gate.call(()->{controller.onEligibility(true);
                controller.onCommentDue(track.comments.get(0));return null;});
            assertTrue(sink.visible);

            assertTrue(M4PhaseFHistoricalCloudClient.applyAssignment(owner.gate,()->true,5,runtime,manifest,
                    cache,scheduler,installer));
            assertFalse("old scene must be retired before ACK eligibility",sink.visible);
            assertEquals(5,cache.revision());assertEquals(5,controller.activeRevision());
            assertTrue(cache.markAcknowledged(5));
            int hides=sink.hides;
            assertTrue(M4PhaseFHistoricalCloudClient.applyAssignment(owner.gate,()->true,5,runtime,manifest,
                    cache,scheduler,installer));
            assertEquals("redelivery does not retire anything again",hides,sink.hides);

            owner.gate.call(()->{controller.onEligibility(true);
                controller.onCommentDue(track.comments.get(0));return null;});
            assertTrue(sink.visible);
            assertTrue(M4PhaseFHistoricalCloudClient.applyAssignment(owner.gate,()->true,6,runtime,null,
                    cache,scheduler,installer));
            assertFalse(sink.visible);assertNull(memory.get("manifest"));
            assertFalse(controller.hasActiveManifest());
            assertEquals(6,cache.revision());
        }
    }

    /** Demonstrate why the previous direct worker call violates the native window boundary. */
    @Test public void directWorkerReplacementViolatesWindowOwner() throws Exception {
        try(Owner owner=new Owner()) {
            JSONObject e=envelope();String runtime=e.getJSONObject("runtimeTrack").toString();
            String manifest=e.getJSONObject("overlayManifest").toString();
            CloudTrackRepository cache=new CloudTrackRepository(new Memory());
            WindowSink sink=new WindowSink(owner);SceneRuntimeController controller=
                    new SceneRuntimeController(sink);
            MediaSyncedTrackScheduler scheduler=scheduler(controller);
            M4PhaseFHistoricalCloudClient.ManifestInstaller installer=installer(cache,scheduler,controller);
            assertTrue(M4PhaseFHistoricalCloudClient.applyAssignment(owner.gate,()->true,4,runtime,manifest,
                    cache,scheduler,installer));
            ScheduledTrack track=TrackParser.parse(e.getJSONObject("runtimeTrack"),media->null);
            owner.gate.call(()->{controller.onEligibility(true);
                controller.onCommentDue(track.comments.get(0));return null;});
            assertThrows(AssertionError.class,()->installer.install(5,runtime,manifest));
        }
    }

    /** Owner-thread callers execute inline and never deadlock waiting on themselves. */
    @Test public void nestedOwnerCallCompletesInline() throws Exception {
        try(Owner owner=new Owner()) {
            assertEquals(Integer.valueOf(7),owner.gate.call(()->owner.gate.call(()->7)));
        }
    }

    /** Failure and rejection cannot become a successful installation/ACK decision. */
    @Test public void failureAndRejectedDispatchPropagate() throws Exception {
        try(Owner owner=new Owner()) {
            assertThrows(IllegalStateException.class,()->owner.gate.call(()->{
                throw new IllegalStateException("native retirement failed");}));
        }
        AssignmentMutationGate rejected=new AssignmentMutationGate(work->{
            throw new RejectedExecutionException("owner closed");},()->false);
        assertThrows(RejectedExecutionException.class,()->rejected.call(()->true));
    }

    /** Interruption cancels an undispatched mutation instead of leaving a stale callback. */
    @Test public void interruptedQueuedMutationNeverRunsLater() throws Exception {
        AtomicReference<Runnable> queued=new AtomicReference<>();
        CountDownLatch posted=new CountDownLatch(1),finished=new CountDownLatch(1);
        AtomicBoolean interrupted=new AtomicBoolean();AtomicInteger writes=new AtomicInteger();
        AssignmentMutationGate gate=new AssignmentMutationGate(work->{queued.set(work);
            posted.countDown();},()->false);
        Thread caller=new Thread(()->{
            try {gate.call(()->writes.incrementAndGet());}
            catch(InterruptedException expected){interrupted.set(Thread.currentThread().isInterrupted());}
            catch(Exception failure){throw new AssertionError(failure);}
            finally {finished.countDown();}
        },"m1-test-network-caller");
        caller.start();assertTrue(posted.await(5,TimeUnit.SECONDS));
        caller.interrupt();assertTrue(finished.await(5,TimeUnit.SECONDS));caller.join(5000);
        queued.get().run();
        assertTrue(interrupted.get());assertEquals(0,writes.get());
    }

    /** Stop is checked on dispatch completion; queued work cannot touch a newer service. */
    @Test public void stoppedClientCannotApplyQueuedRevision() throws Exception {
        AtomicReference<Runnable> queued=new AtomicReference<>();
        CountDownLatch posted=new CountDownLatch(1),finished=new CountDownLatch(1);
        AtomicBoolean current=new AtomicBoolean(true),result=new AtomicBoolean(true);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        AssignmentMutationGate gate=new AssignmentMutationGate(work->{queued.set(work);
            posted.countDown();},()->false);
        CloudTrackRepository cache=new CloudTrackRepository(new Memory());
        Thread caller=new Thread(()->{
            try {result.set(M4PhaseFHistoricalCloudClient.applyAssignment(gate,current::get,4,"unused",
                    null,cache,null,null));}
            catch(Throwable error){failure.set(error);}
            finally {finished.countDown();}
        },"m1-test-stopped-caller");
        caller.start();assertTrue(posted.await(5,TimeUnit.SECONDS));current.set(false);
        queued.get().run();assertTrue(finished.await(5,TimeUnit.SECONDS));caller.join(5000);
        assertNull(failure.get());assertFalse(result.get());assertEquals(0,cache.revision());
    }
}
