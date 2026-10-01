package com.scenevibe.tvcompanionpoc;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;

/**
 * Deterministic JVM tests for the CORRECTED asynchronous {@link CloudControlClient#reset()}
 * (final PR #8 runtime fix). The prior implementation blocked the calling thread on
 * {@code io.awaitTermination(15s)}; because {@link OverlayService#onStartCommand} runs reset()
 * on the Android MAIN thread that was an unacceptable main-thread wait for the TV runtime.
 *
 * <p>The fix must: flip {@code running=false} synchronously; queue the wipe onto the io
 * executor so it serializes AFTER any request already queued/in flight; call
 * {@code io.shutdown()} (not shutdownNow) so the queued wipe still runs; and NOT wait on the
 * calling thread. reset() must therefore RETURN before the executor has run the wipe.
 *
 * <p>These tests drive {@link CloudControlClient} through its injectable-executor seam with a
 * {@link ManualExecutor} that only runs queued work when the test explicitly pumps it. That
 * makes the ordering deterministic: we can observe that reset() returned with the wipe still
 * pending, that a task queued BEFORE reset runs first, that the wipe then clears the
 * scheduler / cache / credential state, that the executor is shut down (client cannot poll),
 * and that a later SceneVibe entry would reconstruct a fresh client.
 */
public final class CloudResetAsyncTest {

    /** In-memory non-secret cloud_identity store; mirrors CloudResetTest.CloudMemory. */
    private static final class CloudMemory implements CloudDeviceCredentials.Storage {
        final Map<String,String> values=new HashMap<>();
        final Map<String,Boolean> flags=new HashMap<>();
        @Override public String getString(String key){return values.get(key);}
        @Override public boolean getFlag(String key){return flags.getOrDefault(key,false);}
        @Override public void putFlag(String key,boolean value){flags.put(key,value);}
        @Override public boolean persistActivation(String cloudDeviceId,String deviceToken,
                String activationId,String activationSecret,String userCode) {
            values.put("cloudDeviceId",cloudDeviceId);values.remove("deviceToken");
            values.put("activationId",activationId);values.remove("activationSecret");
            values.put("userCode",userCode);return true;
        }
        @Override public boolean confirmClaimed() {
            values.remove("activationId");values.remove("userCode");
            flags.put("connected",true);return true;
        }
        @Override public void clearActivationTemporaries() {
            values.remove("activationId");values.remove("userCode");
        }
        @Override public void disconnect() {
            values.remove("activationId");values.remove("userCode");
            flags.put("connected",false);
        }
        @Override public void reset() {values.clear();flags.clear();}
        @Override public void removeLegacyPlaintext(String key) {values.remove(key);}
    }

    /** In-memory cloud_track cache store mirroring the production SharedPreferences batches. */
    private static final class TrackMemory implements CloudTrackRepository.Storage {
        final Map<String,String> values=new HashMap<>();
        @Override public String get(String key){return values.get(key);}
        @Override public boolean save(long revision,String json) {
            values.put("revision",String.valueOf(revision));values.put("runtime",json);return true;
        }
        @Override public boolean saveAck(long revision){values.put("ackRevision",String.valueOf(revision));return true;}
        @Override public void clear(){values.clear();}
    }

    /** Records scheduler activity so the test can assert a track was cleared by the wipe. */
    private static final class RecordingListener implements MediaSyncedTrackScheduler.Listener {
        int renders;
        int eligibleTrue;
        @Override public void onRender(ScheduledTrack.Event event) {renders++;}
        @Override public void onPlayback(boolean playing,boolean freeze) {}
        @Override public void onEligibility(boolean eligible) {if(eligible)eligibleTrue++;}
    }

    private static MediaSessionProbe.Snapshot matchingSnapshot(long positionMs) {
        return new MediaSessionProbe.Snapshot(
                "com.amazon.amazonvideo.livingroom",
                android.media.session.PlaybackState.STATE_PLAYING,"PLAYING",
                positionMs,positionMs,1.0f,0L,
                "video-1","Columbo","",5884768L);
    }

    private static String track(String id) {
        return "{\"type\":\"scenevibe.track.v1\",\"trackId\":\""+id+"\",\"targetPackage\":\"com.amazon.amazonvideo.livingroom\",\"mediaIdentity\":{\"platform\":\"prime_video\",\"videoId\":\"video-1\",\"title\":\"Columbo\",\"durationMs\":5884768},\"pauseFreezesDisplay\":true,\"comments\":[{\"id\":\"c1\",\"text\":\"Hello\",\"startMs\":1000,\"durationMs\":6000}]}";
    }

    /**
     * A single-thread {@link ScheduledExecutorService} stand-in that NEVER runs work on its
     * own: queued tasks run only when the test calls {@link #runNext()} / {@link #runAll()}.
     * This makes the reset ordering deterministic and lets the test prove reset() returned
     * BEFORE the wipe executed. Only the methods CloudControlClient.reset() actually uses are
     * implemented; the rest fail fast so an accidental new dependency is caught.
     */
    private static final class ManualExecutor implements ScheduledExecutorService {
        private final Deque<Runnable> queue=new ArrayDeque<>();
        private boolean shutdown;
        int executeCalls;
        int shutdownCalls;
        int shutdownNowCalls;

        boolean hasPending(){return !queue.isEmpty();}
        int pending(){return queue.size();}

        /** Runs the oldest queued task, mirroring a single-thread FIFO executor. */
        void runNext() {
            Runnable next=queue.pollFirst();
            if(next!=null) next.run();
        }
        void runAll() {while(!queue.isEmpty()) queue.pollFirst().run();}

        @Override public void execute(Runnable command) {
            executeCalls++;
            if(shutdown) throw new java.util.concurrent.RejectedExecutionException("shut down");
            queue.addLast(command);
        }
        @Override public void shutdown() {shutdownCalls++;shutdown=true;}
        @Override public List<Runnable> shutdownNow() {
            shutdownNowCalls++;shutdown=true;
            List<Runnable> drained=new ArrayList<>(queue);queue.clear();return drained;
        }
        @Override public boolean isShutdown() {return shutdown;}
        @Override public boolean isTerminated() {return shutdown&&queue.isEmpty();}
        @Override public boolean awaitTermination(long timeout,TimeUnit unit) {
            throw new AssertionError("reset() must NOT block the calling thread on awaitTermination");
        }
        @Override public ScheduledFuture<?> schedule(Runnable command,long delay,TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
        @Override public <V> ScheduledFuture<V> schedule(Callable<V> callable,long delay,TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
        @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,long initialDelay,long period,TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command,long initialDelay,long delay,TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
        @Override public <T> Future<T> submit(Callable<T> task){throw new UnsupportedOperationException();}
        @Override public <T> Future<T> submit(Runnable task,T result){throw new UnsupportedOperationException();}
        @Override public Future<?> submit(Runnable task){throw new UnsupportedOperationException();}
        @Override public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks){throw new UnsupportedOperationException();}
        @Override public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,long timeout,TimeUnit unit){throw new UnsupportedOperationException();}
        @Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks){throw new UnsupportedOperationException();}
        @Override public <T> T invokeAny(Collection<? extends Callable<T>> tasks,long timeout,TimeUnit unit){throw new UnsupportedOperationException();}
    }

    private static CloudDeviceCredentials credentials(CloudMemory cloud,SecretStore secrets) {
        return new CloudDeviceCredentials(cloud,secrets);
    }

    /**
     * The corrected reset returns WITHOUT waiting for the executor: after reset() the wipe is
     * still pending on the manual executor and none of the wipe's effects have happened yet.
     * Pumping the executor then runs the wipe, which clears scheduler / cache / credential
     * state. The executor is shut down (so the spent client cannot poll) but shutdownNow is
     * never used (so the queued wipe survives).
     */
    @Test public void resetReturnsBeforeWipeThenWipesEverythingOnPump() throws Exception {
        CloudMemory cloud=new CloudMemory();SecretStore secrets=new SecretStore.InMemorySecretStore();
        CloudDeviceCredentials identity=credentials(cloud,secrets);
        identity.persistActivation("cloud-uuid","device-token","act-1","secret","123456");
        identity.confirmClaimed();

        RecordingListener recorder=new RecordingListener();
        MediaSyncedTrackScheduler scheduler=new MediaSyncedTrackScheduler(recorder);
        TrackMemory trackMemory=new TrackMemory();
        CloudTrackRepository cache=new CloudTrackRepository(trackMemory);

        // Load an active track so we can prove the wipe clears it.
        assertTrue(cache.install(2,track("active"),scheduler));
        scheduler.onPlaybackSnapshot(matchingSnapshot(2000));
        assertTrue("track should render before reset",recorder.renders>=1);
        assertNotNull(cache.cachedTrackId());
        assertEquals("device-token",identity.deviceToken());

        ManualExecutor io=new ManualExecutor();
        DiagnosticsStore diagnostics=DiagnosticsStore.INSTANCE;
        diagnostics.setLastSuccessfulAckRevision(2);
        CloudControlClient client=new CloudControlClient(io,identity,cache,scheduler);

        // reset() returns immediately: the wipe is queued but NOT yet run.
        client.reset();
        assertTrue("wipe must be queued on the io executor",io.hasPending());
        assertEquals("wipe queued via execute()",1,io.executeCalls);
        assertEquals("io must be shut down so the spent client cannot poll",1,io.shutdownCalls);
        assertEquals("shutdownNow must NOT be used, or the queued wipe would be dropped",
                0,io.shutdownNowCalls);
        assertTrue(io.isShutdown());
        // Nothing has been wiped yet: reset() did not block waiting for completion.
        assertEquals("credential must still be present until the wipe runs",
                "device-token",identity.deviceToken());
        assertNotNull("cache must still be present until the wipe runs",cache.cachedTrackId());

        // Pump the executor: now the wipe runs and clears everything.
        io.runAll();
        assertNull("deviceToken wiped",identity.deviceToken());
        assertNull("cloudDeviceId wiped",identity.cloudDeviceId());
        assertEquals("cache revision cleared",0,cache.revision());
        assertNull("cached track cleared",cache.cachedTrackId());
        assertFalse("no secret ciphertext remains",secrets.contains("deviceToken"));
        assertFalse(secrets.contains("activationSecret"));

        // The cleared scheduler no longer renders the previously loaded track.
        int rendersAfterWipe=recorder.renders;
        scheduler.onPlaybackSnapshot(matchingSnapshot(3000));
        scheduler.onPlaybackSnapshot(matchingSnapshot(4000));
        assertEquals("no render after the wipe cleared the scheduler",
                rendersAfterWipe,recorder.renders);
    }

    /**
     * The wipe is serialized AFTER work already queued on the single-thread io executor: a
     * task enqueued before reset() runs first, then the wipe. This mirrors "already-running
     * Cloud work serializes before the wipe" without a live HTTPS request.
     */
    @Test public void wipeRunsAfterPriorQueuedWork() throws Exception {
        CloudMemory cloud=new CloudMemory();SecretStore secrets=new SecretStore.InMemorySecretStore();
        CloudDeviceCredentials identity=credentials(cloud,secrets);
        identity.persistActivation("cloud-uuid","device-token","act-1","secret","123456");
        identity.confirmClaimed();

        RecordingListener recorder=new RecordingListener();
        MediaSyncedTrackScheduler scheduler=new MediaSyncedTrackScheduler(recorder);
        TrackMemory trackMemory=new TrackMemory();
        CloudTrackRepository cache=new CloudTrackRepository(trackMemory);
        assertTrue(cache.install(2,track("active"),scheduler));

        ManualExecutor io=new ManualExecutor();
        CloudControlClient client=new CloudControlClient(io,identity,cache,scheduler);

        final List<String> order=new ArrayList<>();
        // A stand-in for an in-flight/queued cloud request already sitting on io.
        io.execute(()->order.add("prior-request"));

        client.reset(()->order.add("wipe-complete"));

        // Two tasks now queued: the prior request first (FIFO), then the wipe.
        assertEquals(2,io.pending());
        io.runNext(); // prior request
        assertEquals("prior queued work runs before the wipe touches the cache",
                "device-token",identity.deviceToken());
        io.runNext(); // wipe (+ completion callback)
        assertEquals("[prior-request, wipe-complete]",order.toString());
        assertNull("wipe cleared the credential after the prior work",identity.deviceToken());
        assertNull(cache.cachedTrackId());
    }

    /**
     * The optional completion callback fires exactly once, on the io thread, at the end of the
     * wipe. reset() returns before it runs; pumping the executor triggers it.
     */
    @Test public void completionCallbackFiresAfterWipe() throws Exception {
        CloudMemory cloud=new CloudMemory();SecretStore secrets=new SecretStore.InMemorySecretStore();
        CloudDeviceCredentials identity=credentials(cloud,secrets);
        identity.persistActivation("cloud-uuid","device-token","act-1","secret","123456");
        RecordingListener recorder=new RecordingListener();
        MediaSyncedTrackScheduler scheduler=new MediaSyncedTrackScheduler(recorder);
        CloudTrackRepository cache=new CloudTrackRepository(new TrackMemory());

        ManualExecutor io=new ManualExecutor();
        CloudControlClient client=new CloudControlClient(io,identity,cache,scheduler);

        final int[] completions={0};
        client.reset(()->completions[0]++);
        assertEquals("callback must NOT run before the executor pumps the wipe",0,completions[0]);
        io.runAll();
        assertEquals("callback runs exactly once at the end of the wipe",1,completions[0]);
        assertNull(identity.deviceToken());
    }

    /**
     * If the primary io executor is already shut down, reset must STILL never execute the wipe
     * on the caller. The rejected primary submission is handed to a separate fallback executor;
     * this test keeps that fallback manual so the non-blocking boundary is deterministic.
     */
    @Test public void resetUsesAsyncFallbackWhenPrimaryExecutorAlreadyShutDown() throws Exception {
        CloudMemory cloud=new CloudMemory();SecretStore secrets=new SecretStore.InMemorySecretStore();
        CloudDeviceCredentials identity=credentials(cloud,secrets);
        identity.persistActivation("cloud-uuid","device-token","act-1","secret","123456");
        RecordingListener recorder=new RecordingListener();
        MediaSyncedTrackScheduler scheduler=new MediaSyncedTrackScheduler(recorder);
        CloudTrackRepository cache=new CloudTrackRepository(new TrackMemory());
        assertTrue(cache.install(2,track("active"),scheduler));

        ManualExecutor io=new ManualExecutor();
        io.shutdown(); // primary path rejects immediately
        ManualExecutor fallback=new ManualExecutor();
        CloudControlClient client=new CloudControlClient(io,identity,cache,scheduler,fallback);

        final int[] completions={0};
        client.reset(()->completions[0]++);

        // reset() returned while fallback work is still pending: caller was never the worker.
        assertTrue("fallback wipe must be queued asynchronously",fallback.hasPending());
        assertEquals("credential must still exist before fallback worker runs",
                "device-token",identity.deviceToken());
        assertNotNull(cache.cachedTrackId());
        assertEquals(0,completions[0]);

        fallback.runAll();

        assertNull("fallback wipe cleared the credential",identity.deviceToken());
        assertNull(cache.cachedTrackId());
        assertEquals("completion callback fires after asynchronous fallback wipe",1,completions[0]);
    }

    /**
     * Recovery identity rotation is serialized INSIDE the same asynchronous wipe: reset()
     * returns before it runs, then the io worker rotates exactly once before deleting the
     * Cloud credential. This prevents a main-thread write and guarantees the next client is
     * constructed from a fresh installation id.
     */
    @Test public void resetRotatesRecoveryIdentityOnlyWhenAsyncWipeRuns() throws Exception {
        CloudMemory cloud=new CloudMemory();SecretStore secrets=new SecretStore.InMemorySecretStore();
        CloudDeviceCredentials identity=credentials(cloud,secrets);
        identity.persistActivation("cloud-uuid","device-token","act-1","secret","123456");
        MediaSyncedTrackScheduler scheduler=new MediaSyncedTrackScheduler(new RecordingListener());
        CloudTrackRepository cache=new CloudTrackRepository(new TrackMemory());

        ManualExecutor io=new ManualExecutor();
        final int[] rotations={0};
        CloudControlClient client=new CloudControlClient(
                io,identity,cache,scheduler,CloudControlClient::startResetFallbackThread,
                ()->rotations[0]++);

        client.reset();
        assertEquals("rotation must not run on the reset caller",0,rotations[0]);
        assertEquals("credential remains until asynchronous wipe runs",
                "device-token",identity.deviceToken());

        io.runAll();

        assertEquals("installation identity rotates exactly once",1,rotations[0]);
        assertNull("credential is deleted after rotation",identity.deviceToken());
    }

    /**
     * Restartability contract (unchanged by the async fix): after reset the client is spent
     * (io shut down, cannot poll), OverlayService nulls the reference, and the pure guard
     * mirror says a later entry with a configured origin reconstructs a fresh client.
     */
    @Test public void spentClientIsReconstructedOnNextEntry() throws Exception {
        CloudMemory cloud=new CloudMemory();SecretStore secrets=new SecretStore.InMemorySecretStore();
        CloudDeviceCredentials identity=credentials(cloud,secrets);
        identity.persistActivation("cloud-uuid","device-token","act-1","secret","123456");
        MediaSyncedTrackScheduler scheduler=new MediaSyncedTrackScheduler(new RecordingListener());
        CloudTrackRepository cache=new CloudTrackRepository(new TrackMemory());

        ManualExecutor io=new ManualExecutor();
        CloudControlClient client=new CloudControlClient(io,identity,cache,scheduler);
        client.reset();
        io.runAll();

        assertTrue("io must be shut down so the spent client cannot schedule another poll",
                io.isShutdown());
        // OverlayService nulls its reference after reset; the guard then reconstructs a fresh
        // client on the next entry when a real origin is configured.
        boolean cloudClientNull=true, cloudOriginEmpty=false;
        assertTrue("a later entry must reconstruct a fresh client after a spent reset",
                OverlayService.shouldReconstructCloudClient(cloudClientNull,cloudOriginEmpty));
    }
}
