package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;

/** Outbound HTTPS client; never handles video, subtitles, player controls or browser timecodes. */
final class CloudControlClient {
    private static final String TAG="SceneVibeCloud";
    private static final int LIMIT=3_000_000;
    /**
     * Bounded watchdog for the asynchronous reset: if the queued wipe has not completed within
     * this window a bounded diagnostic is surfaced. This never blocks the calling (Android
     * main) thread; it runs on a throwaway daemon thread and only emits an observational code.
     */
    private static final long RESET_WATCHDOG_SECONDS=15;
    private final ScheduledExecutorService io;
    /**
     * Separate one-shot fallback execution boundary used only if the primary Cloud io executor
     * is already shut down when Reset is requested. Production always dispatches this work to
     * a daemon thread so the Android caller is never used as the reset worker.
     */
    private final java.util.concurrent.Executor resetFallback;
    private final CloudDeviceCredentials identity;
    private final CloudTrackRepository cache;
    private final MediaSyncedTrackScheduler scheduler;
    /** Local stable id from InstallationIdentity; used ONLY as the activation installationId. */
    private final String installationId;
    private final String origin;
    private volatile boolean running;
    private int failures;
    /** Links the outbound client to an already created passive MediaSession scheduler. */
    CloudControlClient(Context context, CloudTrackRepository cache,MediaSyncedTrackScheduler scheduler) {
        this.io=Executors.newSingleThreadScheduledExecutor();
        this.resetFallback=CloudControlClient::startResetFallbackThread;
        this.identity=new CloudDeviceCredentials(context);
        this.cache=cache;
        this.scheduler=scheduler;
        this.installationId=new InstallationIdentity(context).installationId();
        this.origin=BuildConfig.CLOUD_ORIGIN;
    }
    /**
     * Injectable seam for deterministic JVM tests of the ASYNCHRONOUS reset. It takes the
     * already-built cloud collaborators plus the single-thread scheduled executor so a test can
     * drive {@link #reset(Runnable)} without an Android {@link Context} or a live HTTPS stack.
     * The origin is left empty (so {@link #start()} is a no-op and no network is ever touched)
     * and the installationId is a fixed non-secret placeholder; only the reset lifecycle -
     * synchronous running-flip, wipe queued onto io, executor shutdown, async completion - is
     * exercised. Production code always uses the {@link Context} constructor above.
     */
    CloudControlClient(ScheduledExecutorService io,CloudDeviceCredentials identity,
            CloudTrackRepository cache,MediaSyncedTrackScheduler scheduler) {
        this(io,identity,cache,scheduler,CloudControlClient::startResetFallbackThread);
    }
    /**
     * Extended injectable seam used only by reset tests so the rejected-primary-executor path
     * can be driven deterministically without ever running fallback work on the test caller.
     */
    CloudControlClient(ScheduledExecutorService io,CloudDeviceCredentials identity,
            CloudTrackRepository cache,MediaSyncedTrackScheduler scheduler,
            java.util.concurrent.Executor resetFallback) {
        this.io=io;
        this.resetFallback=resetFallback;
        this.identity=identity;
        this.cache=cache;
        this.scheduler=scheduler;
        this.installationId="test-installation";
        this.origin="";
    }
    /** Restores cached media in OverlayService before starting the first network fetch. */
    void start() {
        if(origin.isEmpty()||running)return;
        if(!origin.matches("https://[^/:?#]+")) {Log.w(TAG,"Invalid HTTPS cloud configuration");return;}
        running=true;failures=0;
        io.execute(this::poll);
    }
    /** Opens an activation only from an explicit TV button; never blocks the TV UI. */
    void activate() {
        if(!running)return;
        io.execute(()->{
            try {
                JSONObject request=new JSONObject().put("installationId",installationId);
                String existing=identity.deviceToken();
                if(existing!=null)request.put("deviceToken",existing);
                Reply reply=http("POST","device-activations",existing,request);
                JSONObject data=reply.body;
                if(reply.status!=201) {
                    // Observational only: preserve the server's bounded device-proof refusal
                    // instead of swallowing every activation failure behind a generic Logcat
                    // line. Never surface a raw body, token, installationId or account detail.
                    DiagnosticsStore.INSTANCE.setLastCloudErrorCode(
                            activationErrorCode(reply.status,data));
                    publishCloudState();
                    Log.w(TAG,"Activation rejected");return;
                }
                if(!CloudProtocol.validActivation(data)) {
                    DiagnosticsStore.INSTANCE.setLastCloudErrorCode(
                            RuntimeDiagnostics.CloudErrorCode.PROTOCOL);
                    publishCloudState();
                    Log.w(TAG,"Activation response malformed");return;
                }
                // A valid 201 durable-persists deviceToken+cloudDeviceId IMMEDIATELY plus the
                // temporary activation state; do NOT wait for a claimed status.
                if(!identity.persistActivation(data.getString("deviceId"),data.getString("deviceToken"),
                        data.getString("activationId"),data.getString("activationSecret"),data.getString("userCode")))
                    throw new IllegalStateException("Private credential write failed");
                DiagnosticsStore.INSTANCE.setLastCloudErrorCode(RuntimeDiagnostics.CloudErrorCode.NONE);
                publishCloudState();
                Log.i(TAG,"Cloud activation open");
            }catch(Exception error){
                DiagnosticsStore.INSTANCE.setLastCloudErrorCode(classify(error));
                publishCloudState();
                Log.w(TAG,"Cloud activation unavailable");
            }
        });
    }
    /** Clears only local cloud credentials; the durable cached track remains available offline. */
    void disconnect() {
        identity.disconnect();
        DiagnosticsStore.INSTANCE.setCloudState(RuntimeDiagnostics.CloudState.DISCONNECTED);
        Log.i(TAG,"Cloud identity disconnected locally");
    }
    /** Stops network work when the foreground service stops; MediaSession remains independent. */
    void stop() {running=false;io.shutdownNow();}
    /** Performs one conditional fetch and schedules a bounded, backoff-aware subsequent poll. */
    private void poll() {
        if(!running)return;
        try {
            if(identity.activationId()!=null)checkActivation();
            if(identity.connected())fetchAssignment();
            failures=0;identity.setOffline(false);
            // Observational only: reflect the coarse cloud state after a clean poll.
            publishCloudState();
        }catch(Exception error){failures=Math.min(4,failures+1);identity.setOffline(true);
            // Observational only: map the failure to a bounded code, never a raw trace. A more
            // specific code carried by the failure (PROTOCOL from a malformed assignment,
            // TIMEOUT from a genuine transport timeout) is preserved instead of being blindly
            // clobbered with NETWORK; only a truly generic failure falls back to NETWORK.
            DiagnosticsStore.INSTANCE.setLastCloudErrorCode(classify(error));
            publishCloudState();
            Log.w(TAG,"Cloud offline; cached track remains available");}
        long delay=CloudProtocol.backoffSeconds(failures);
        if(running)io.schedule(this::poll,delay,TimeUnit.SECONDS);
    }
    /**
     * Observational only: maps a caught poll failure to the bounded diagnostics code. A
     * {@link CloudException} carries the intended code (e.g. PROTOCOL for a malformed
     * assignment) so it is preserved rather than clobbered by NETWORK; a genuine transport
     * timeout maps to TIMEOUT; every other failure is the generic NETWORK code. This is
     * observational only and never gates behavior.
     */
    private static RuntimeDiagnostics.CloudErrorCode classify(Throwable error) {
        if(error instanceof CloudException) return ((CloudException)error).code;
        if(error instanceof java.net.SocketTimeoutException) return RuntimeDiagnostics.CloudErrorCode.TIMEOUT;
        return RuntimeDiagnostics.CloudErrorCode.NETWORK;
    }
    /**
     * Observational-only activation error mapping. The Cloud deliberately collapses device-proof
     * failures and other activation authentication failures to the SAME public 401/UNAUTHORIZED
     * contract, so Diagnostics preserves that opaque boundary rather than trying to infer or
     * expose the server's internal reason. No raw response body is surfaced.
     */
    static RuntimeDiagnostics.CloudErrorCode activationErrorCode(int status,JSONObject body) {
        if(status==401)return RuntimeDiagnostics.CloudErrorCode.UNAUTHORIZED;
        return RuntimeDiagnostics.CloudErrorCode.PROTOCOL;
    }
    /**
     * Observational only: maps the current credential state to the bounded diagnostics cloud
     * state. Never influences a decision; the client's behavior is unchanged by this call.
     */
    private void publishCloudState() {
        RuntimeDiagnostics.CloudState state=identity.credentialUnavailable()
                ? RuntimeDiagnostics.CloudState.DISCONNECTED
                : identity.userCode()!=null?RuntimeDiagnostics.CloudState.ACTIVATION_PENDING
                : identity.connected()?(identity.offline()?RuntimeDiagnostics.CloudState.OFFLINE
                        :RuntimeDiagnostics.CloudState.CONNECTED)
                : RuntimeDiagnostics.CloudState.DISCONNECTED;
        DiagnosticsStore.INSTANCE.setCloudState(state);
        if(identity.credentialUnavailable())
            DiagnosticsStore.INSTANCE.setLastCloudErrorCode(
                    RuntimeDiagnostics.CloudErrorCode.CREDENTIAL_UNAVAILABLE);
    }
    /** Reads the activation lifecycle; claimed connects, expired drops only the temporaries. */
    private void checkActivation() throws Exception {
        String activationId=identity.activationId(), temporary=identity.activationSecret();
        if(activationId==null||temporary==null)return;
        Reply reply=http("GET","device-activations/"+activationId,temporary,null);
        if(reply.status!=200||reply.body==null)throw new IllegalStateException("Invalid activation status");
        String status=reply.body.optString("status");
        if(!CloudProtocol.validActivationStatus(status))throw new IllegalStateException("Unknown activation status");
        if("claimed".equals(status)) {
            if(!identity.confirmClaimed())throw new IllegalStateException("Credential commit failed");
            Log.i(TAG,"Cloud connected");
        }else if("expired".equals(status)) {
            identity.clearExpiredActivation();Log.i(TAG,"Cloud activation expired");
        }
    }
    /** Downloads only revisions beyond the last ACK; retries an unacknowledged cached revision. */
    private void fetchAssignment() throws Exception {
        String token=identity.deviceToken(), cloudDeviceId=identity.cloudDeviceId();
        if(token==null||cloudDeviceId==null)return;
        Reply reply=http("GET","devices/"+cloudDeviceId+"/assignment?afterRevision="+cache.acknowledged(),token,null);
        // 204 = nothing newer, 404 = authenticated but no current assignment: both NORMAL, not offline.
        if(reply.status==204||reply.status==404)return;
        if(reply.status==401) {
            // Observational only: a bounded UNAUTHORIZED code. This never auto-resets the
            // identity - Reset Cloud is a manual Diagnostics-only action.
            DiagnosticsStore.INSTANCE.setLastCloudErrorCode(RuntimeDiagnostics.CloudErrorCode.UNAUTHORIZED);
            Log.w(TAG,"Cloud credential rejected");return;
        }
        JSONObject data=reply.body;
        if(reply.status!=200||data==null||!CloudProtocol.validAssignment(data,cloudDeviceId,cache.revision())) {
            // A malformed/rejected assignment is a PROTOCOL failure. Carry that code on the
            // exception so poll's catch reports PROTOCOL and never clobbers it with NETWORK.
            throw new CloudException(RuntimeDiagnostics.CloudErrorCode.PROTOCOL,"Invalid assignment response");
        }
        long revision=data.optLong("revision",-1), cached=cache.revision();
        // Observational only: record the highest assignment revision received.
        DiagnosticsStore.INSTANCE.setLastAssignmentRevisionReceived(revision);
        JSONObject runtime=data.optJSONObject("runtimeTrack");
        String finalTrackId=data.optString("finalTrackId","");
        // Re-run the FULL runtimeTrack JSON through the shared TrackParser before persistence.
        if(revision>cached && !cache.install(revision,runtime.toString(),scheduler))
            throw new IllegalStateException("Invalid or non-durable runtime track");
        // A cached, unacknowledged revision was already restored when service started.
        // ACK sends finalTrackId (NOT trackId) and only after the scheduler load succeeded.
        JSONObject ack=new JSONObject().put("revision",revision).put("finalTrackId",finalTrackId);
        Reply confirmed=http("POST","devices/"+cloudDeviceId+"/ack",token,ack);
        if(confirmed.status!=200||!CloudProtocol.validAck(confirmed.body,cloudDeviceId,revision))
            throw new IllegalStateException("Cloud ACK rejected");
        if(!cache.markAcknowledged(revision))throw new IllegalStateException("Cloud ACK state could not be persisted");
        // Observational only: record the last revision the cloud successfully acknowledged
        // and clear any prior bounded error code after a fully successful cycle.
        DiagnosticsStore.INSTANCE.setLastSuccessfulAckRevision(revision);
        DiagnosticsStore.INSTANCE.setLastCloudErrorCode(RuntimeDiagnostics.CloudErrorCode.NONE);
        Log.i(TAG,"Cloud track loaded; revision="+revision);
    }
    /** Reads at most three megabytes from an explicitly configured HTTPS endpoint. */
    private Reply http(String method,String path,String token,JSONObject body) throws Exception {
        if(!running)throw new IllegalStateException("Cloud client stopped");
        HttpsURLConnection connection=(HttpsURLConnection)new URL(origin+"/api/v1/"+path).openConnection();
        try {
            connection.setRequestMethod(method);connection.setConnectTimeout(8000);connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);
            connection.setRequestProperty("Accept","application/json");
            if(token!=null)connection.setRequestProperty("Authorization","Bearer "+token);
            if(body!=null) {
                byte[] payload=body.toString().getBytes(StandardCharsets.UTF_8);
                if(payload.length>LIMIT)throw new IllegalArgumentException("Cloud payload too large");
                connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");
                connection.setFixedLengthStreamingMode(payload.length);
                try(OutputStream output=connection.getOutputStream()){output.write(payload);}
            }
            int status=connection.getResponseCode();
            if(status==204)return new Reply(status,null);
            if(status>=300&&status<400)throw new IllegalStateException("Cloud redirect rejected");
            try(InputStream input=status<400?connection.getInputStream():connection.getErrorStream()) {
                if(input==null)return new Reply(status,null);
                ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                while((count=input.read(buffer))!=-1){
                    // Distinguish the two manual-limit causes so diagnostics is honest: a
                    // blown 10s deadline is a genuine TIMEOUT; exceeding the byte cap is a
                    // PROTOCOL-shaped oversized response. Both still fail closed identically.
                    if(System.nanoTime()>deadline)
                        throw new CloudException(RuntimeDiagnostics.CloudErrorCode.TIMEOUT,"Cloud response timeout");
                    if(output.size()+count>LIMIT)
                        throw new CloudException(RuntimeDiagnostics.CloudErrorCode.PROTOCOL,"Cloud response too large");
                    output.write(buffer,0,count);
                }
                return new Reply(status,new JSONObject(output.toString(StandardCharsets.UTF_8.name())));
            }
        } finally {connection.disconnect();}
    }
    /** Convenience overload: the coordinated reset with no completion callback. */
    void reset() {
        reset(null);
    }
    /**
     * EXCEPTIONAL "Reset SceneVibe Cloud connection" coordinated with the runtime (user
     * section 14 / Correction 3), corrected so it NEVER blocks the calling thread. Because
     * {@link OverlayService#onStartCommand} invokes this on the Android MAIN thread, reset()
     * must return immediately and complete the wipe asynchronously.
     *
     * <p>It flips {@code running=false} FIRST and synchronously. That prevents new Cloud work
     * from entering and prevents later polls from being scheduled. A request that had already
     * passed {@code http()}'s leading running check may still finish its current executor task;
     * this is safe because the wipe is queued onto the SAME single-thread io executor and thus
     * runs strictly AFTER that task, removing any state it may have written before reset
     * completion. The method then calls {@code io.shutdown()} (NOT shutdownNow, so the already-
     * queued wipe still runs). It does NOT call {@code awaitTermination} on the calling thread -
     * there is no main-thread wait.
     *
     * <p>The wipe deletes the cloud identity (deviceToken/activationSecret/cloudDeviceId/
     * activationId/userCode) and the cached track, clears the scheduler (dropping any in-memory
     * runtime track), and zeroes the Cloud diagnostics. It NEVER touches the separate
     * 'installation' identity store, so the stable local installationId survives. The autostart
     * pref is left as-is (AutostartPolicy then concludes NOTHING_TO_RESTORE).
     *
     * <p>Completion is asynchronous: an optional {@code onComplete} callback runs at the end of
     * the wipe runnable (on the io thread). A bounded daemon watchdog surfaces a bounded
     * diagnostic if the wipe does not complete within {@link #RESET_WATCHDOG_SECONDS}; it never
     * blocks the UI thread. This instance is spent after reset: the io executor is shut down and
     * a {@link ScheduledExecutorService} cannot be reused, so {@link OverlayService} discards
     * this client and reconstructs a fresh one on the next entry.
     *
     * @param onComplete optional callback invoked on the io thread once the wipe finishes; may
     *     be {@code null}. It never receives a secret and must not block.
     */
    void reset(Runnable onComplete) {
        // Stop first. This synchronously prevents NEW Cloud work and future poll scheduling.
        // A request already inside http() may still finish its current single-thread executor
        // task; the queued wipe below runs after it and removes any state written before reset
        // completion. This assignment is the only reset work performed on the Android caller.
        running=false;
        // A single latch that both the completion callback and the watchdog observe, so the
        // watchdog only fires when the wipe genuinely did not finish in time.
        final java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
        Runnable wipe=()->{
            try {
                identity.reset();
                cache.clear();
                scheduler.clear();
                DiagnosticsStore.INSTANCE.resetCloudObservations();
                Log.i(TAG,"Cloud reset completed on io executor");
            }finally {
                done.countDown();
                if(onComplete!=null) {
                    try {onComplete.run();}
                    catch(RuntimeException ignored){Log.w(TAG,"Cloud reset completion callback failed");}
                }
            }
        };
        // Queue the wipe so it serializes after any request already executing on io, then shut
        // io down WITHOUT waiting: shutdown() lets the queued wipe run but rejects any future
        // scheduled poll. If io is already shut down, NEVER fall back to wipe.run() on the
        // caller: dispatch the wipe to the dedicated asynchronous fallback boundary instead.
        try {
            io.execute(wipe);
            io.shutdown();
            startResetWatchdog(done);
        }catch(java.util.concurrent.RejectedExecutionException stopped) {
            try {
                resetFallback.execute(wipe);
                startResetWatchdog(done);
            }catch(RuntimeException fallbackFailure) {
                DiagnosticsStore.INSTANCE.setLastCloudErrorCode(
                        RuntimeDiagnostics.CloudErrorCode.TIMEOUT);
                Log.w(TAG,"Cloud reset fallback could not be scheduled");
            }
        }
    }
    /** Production fallback: one daemon worker, never the Android caller thread. */
    private static void startResetFallbackThread(Runnable wipe) {
        Thread worker=new Thread(wipe,"scenevibe-cloud-reset-fallback");
        worker.setDaemon(true);
        worker.start();
    }
    /**
     * Bounded, non-blocking watchdog for the asynchronous reset. It waits on a throwaway daemon
     * thread (never the caller) up to {@link #RESET_WATCHDOG_SECONDS}; if the wipe has not
     * completed by then it surfaces a bounded observational diagnostic. It never carries a
     * secret and never blocks the UI thread.
     */
    private void startResetWatchdog(java.util.concurrent.CountDownLatch done) {
        Thread watchdog=new Thread(()->{
            try {
                if(!done.await(RESET_WATCHDOG_SECONDS,TimeUnit.SECONDS)) {
                    DiagnosticsStore.INSTANCE.setLastCloudErrorCode(
                            RuntimeDiagnostics.CloudErrorCode.TIMEOUT);
                    Log.w(TAG,"Cloud reset did not complete within the bounded window");
                }
            }catch(InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        },"scenevibe-cloud-reset-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }
    /** Small transport envelope with no credential exposed to logs or activities. */
    private static final class Reply {
        final int status;final JSONObject body;
        /** Captures only HTTP status and bounded parsed JSON. */
        Reply(int status,JSONObject body){this.status=status;this.body=body;}
    }
    /**
     * Internal transport failure that carries the intended bounded {@link
     * RuntimeDiagnostics.CloudErrorCode} so poll's catch reports the specific code (PROTOCOL,
     * TIMEOUT) instead of clobbering it with NETWORK. It never carries a secret or raw body.
     */
    private static final class CloudException extends Exception {
        final RuntimeDiagnostics.CloudErrorCode code;
        CloudException(RuntimeDiagnostics.CloudErrorCode code,String message){super(message);this.code=code;}
    }
}
