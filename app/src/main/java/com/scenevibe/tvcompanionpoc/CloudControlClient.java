package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONObject;
import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationHandler;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.PackageInstaller;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;

/**
 * Outbound HTTPS client; never handles video, subtitles, player controls or browser timecodes.
 * Fetch and ACK run on io. Assignment application runs synchronously on the Android
 * window owner, so scheduler callbacks cannot race an incomplete renderer handoff.
 */
final class CloudControlClient {
    /** One client/poller selects one transport; production remains on the historical Video route. */
    enum TransportMode { VIDEO_V1, PACKAGE_V1 }
    /** Generic local call seam; production binds the one service-owned PackageInstaller. */
    interface InstallationOperation {
        /** Complete one local installation; this operation neither sends nor persists an ACK. */
        InstallationStatus install(InstallRequest request,InstallationHandler.RuntimePorts ports);
    }
    private static final String TAG="SceneVibeCloud";
    private static final int LIMIT=3_000_000;
    /** Bounded observational watchdog never waits on the Android reset caller. */
    private static final long RESET_WATCHDOG_SECONDS=15;
    private final ScheduledExecutorService io;
    /** One-shot asynchronous fallback is used only when the primary executor is spent. */
    private final java.util.concurrent.Executor resetFallback;
    /** Rotate only the separate non-secret installation identity on exceptional reset. */
    private final Runnable resetInstallationIdentity;
    private final CloudDeviceCredentials identity;
    private final InstallationStore store;
    private final InstallationOperation installation;
    private final InstallationHandler.RuntimePorts runtimePorts;
    /** Runtime-only reset supplied by the service; it knows no durable or network state. */
    private final Runnable resetRuntime;
    /** All local runtime mutations complete on the Android owner before HTTP ACK. */
    private final AssignmentMutationGate mutations;
    /** Service identity proof is rechecked after dispatch, in addition to running. */
    private final java.util.function.BooleanSupplier currentClient;
    private final String installationId;
    private final String origin;
    private final TransportMode transportMode;
    private volatile boolean running;
    private int failures;

    /** Bind the service's single generic stack; only this constructor creates Android transport owners. */
    CloudControlClient(Context context,InstallationStore store,PackageInstaller installer,
            InstallationHandler.RuntimePorts runtimePorts,Runnable resetRuntime,
            java.util.function.Supplier<CloudControlClient> currentClient) {
        this.io=Executors.newSingleThreadScheduledExecutor();
        this.resetFallback=CloudControlClient::startResetFallbackThread;
        this.resetInstallationIdentity=()->new InstallationIdentity(context).rotateForCloudReset();
        this.identity=new CloudDeviceCredentials(context);
        this.store=store;this.installation=installer::install;this.runtimePorts=runtimePorts;
        this.resetRuntime=resetRuntime;this.currentClient=()->currentClient.get()==this;
        Handler main=new Handler(Looper.getMainLooper());
        this.mutations=new AssignmentMutationGate(work->{
            if(!main.post(work))throw new java.util.concurrent.RejectedExecutionException(
                    "Assignment owner unavailable");
        },()->Looper.myLooper()==Looper.getMainLooper());
        this.installationId=new InstallationIdentity(context).installationId();
        this.origin=BuildConfig.CLOUD_ORIGIN;
        this.transportMode=TransportMode.VIDEO_V1;
    }

    /** Android-free local seam drives the actual client without starting network polling. */
    CloudControlClient(ScheduledExecutorService io,CloudDeviceCredentials identity,
            InstallationStore store,InstallationOperation installation,
            InstallationHandler.RuntimePorts runtimePorts,Runnable resetRuntime) {
        this(io,identity,store,installation,runtimePorts,resetRuntime,
                CloudControlClient::startResetFallbackThread,()->{},AssignmentMutationGate.direct(),()->true);
    }

    /** Inject owner/lifetime/reset boundaries without accepting a product parser or runtime controller. */
    CloudControlClient(ScheduledExecutorService io,CloudDeviceCredentials identity,
            InstallationStore store,InstallationOperation installation,
            InstallationHandler.RuntimePorts runtimePorts,Runnable resetRuntime,
            java.util.concurrent.Executor resetFallback,Runnable resetInstallationIdentity,
            AssignmentMutationGate mutations,java.util.function.BooleanSupplier currentClient) {
        this(io,identity,store,installation,runtimePorts,resetRuntime,resetFallback,
                resetInstallationIdentity,mutations,currentClient,TransportMode.VIDEO_V1);
    }
    /** Explicit local qualification selects generic transport on this same client, without changing live wiring. */
    CloudControlClient(ScheduledExecutorService io,CloudDeviceCredentials identity,
            InstallationStore store,InstallationOperation installation,
            InstallationHandler.RuntimePorts runtimePorts,Runnable resetRuntime,
            java.util.concurrent.Executor resetFallback,Runnable resetInstallationIdentity,
            AssignmentMutationGate mutations,java.util.function.BooleanSupplier currentClient,
            TransportMode transportMode) {
        if(io==null||identity==null||store==null||installation==null||runtimePorts==null
                ||resetRuntime==null||resetFallback==null||resetInstallationIdentity==null
                ||mutations==null||currentClient==null||transportMode==null)throw new IllegalArgumentException("Missing Cloud dependency");
        this.io=io;this.identity=identity;this.store=store;this.installation=installation;
        this.runtimePorts=runtimePorts;this.resetRuntime=resetRuntime;
        this.resetFallback=resetFallback;this.resetInstallationIdentity=resetInstallationIdentity;
        this.mutations=mutations;this.currentClient=currentClient;
        this.installationId="test-installation";this.origin="";
        this.transportMode=transportMode;
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
                            activationErrorCode(reply.status));
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
    static RuntimeDiagnostics.CloudErrorCode activationErrorCode(int status) {
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
    /** Read generic durable ACK, adapt on io, await owner installation and confirm ACK only after ARMED. */
    private void fetchAssignment() throws Exception {
        String token=identity.deviceToken(),cloudDeviceId=identity.cloudDeviceId();
        if(token==null||cloudDeviceId==null)return;
        InstallationStore.ReadResult durable=store.read();
        if(durable.state()==InstallationStore.ReadState.CORRUPT)
            throw new CloudException(RuntimeDiagnostics.CloudErrorCode.NETWORK,"Local installation cache unavailable");
        if(transportMode==TransportMode.PACKAGE_V1) {
            fetchPackageAssignment(token,cloudDeviceId,durable);return;
        }
        Reply reply=http("GET","devices/"+cloudDeviceId+"/assignment?afterRevision="+durable.acknowledgedRevision(),token,null);
        // 204 and authenticated 404 remain normal empty polling responses.
        if(reply.status==204||reply.status==404)return;
        if(reply.status==401) {
            DiagnosticsStore.INSTANCE.setLastCloudErrorCode(RuntimeDiagnostics.CloudErrorCode.UNAUTHORIZED);
            Log.w(TAG,"Cloud credential rejected");return;
        }
        CloudV1InstallationAdapter.Assignment assignment;
        try {
            if(reply.status!=200)throw new IllegalArgumentException("Invalid assignment status");
            assignment=CloudV1InstallationAdapter.adapt(reply.body,cloudDeviceId);
        } catch(RuntimeException malformed) {
            throw new CloudException(RuntimeDiagnostics.CloudErrorCode.PROTOCOL,"Invalid assignment response");
        }
        InstallRequest request=assignment.request();
        long revision=request.revision();
        DiagnosticsStore.INSTANCE.setLastAssignmentRevisionReceived(revision);
        InstallationStatus installed=applyAssignment(mutations,()->running&&currentClient.getAsBoolean(),
                request,runtimePorts,installation);
        DiagnosticsStore.INSTANCE.setLastManifestCode(CloudV1InstallationAdapter.manifestCode(request,installed));
        if(installed!=InstallationStatus.ARMED)
            throw new CloudException(RuntimeDiagnostics.CloudErrorCode.NETWORK,"Local installation refused");
        if(!running||!currentClient.getAsBoolean())return;
        JSONObject ack=new JSONObject().put("revision",revision).put("finalTrackId",assignment.finalTrackId());
        Reply confirmed=http("POST","devices/"+cloudDeviceId+"/ack",token,ack);
        if(confirmed.status!=200||!CloudProtocol.validAck(confirmed.body,cloudDeviceId,revision))
            throw new IllegalStateException("Cloud ACK rejected");
        if(!running||!currentClient.getAsBoolean())return;
        if(!store.markAcknowledged(revision))throw new IllegalStateException("Cloud ACK state could not be persisted");
        DiagnosticsStore.INSTANCE.setLastSuccessfulAckRevision(revision);
        DiagnosticsStore.INSTANCE.setLastCloudErrorCode(RuntimeDiagnostics.CloudErrorCode.NONE);
        Log.i(TAG,"Cloud track loaded; revision="+revision);
    }
    /** Fetch the exact generic body on the same io poller; proof is rebuilt from readback after owner ARM. */
    private void fetchPackageAssignment(String token,String deviceId,InstallationStore.ReadResult durable) throws Exception {
        Reply reply=http("GET","devices/"+deviceId+"/package-assignment?afterRevision="+durable.acknowledgedRevision(),token,null);
        if(reply.status==204||reply.status==404)return;
        if(reply.status==401) {
            DiagnosticsStore.INSTANCE.setLastCloudErrorCode(RuntimeDiagnostics.CloudErrorCode.UNAUTHORIZED);return;
        }
        InstallRequest request;
        try {
            if(reply.status!=200)throw new IllegalArgumentException("Invalid package status");
            request=CloudPackageInstallationAdapter.adapt(reply.body,deviceId,durable);
        } catch(RuntimeException malformed) {throw new CloudException(RuntimeDiagnostics.CloudErrorCode.PROTOCOL,"Invalid package response");}
        long revision=request.revision();DiagnosticsStore.INSTANCE.setLastAssignmentRevisionReceived(revision);
        JSONObject proof=mutations.call(()->{
            if(!running||!currentClient.getAsBoolean())return null;
            InstallationStatus installed=applyAssignment(mutations,()->running&&currentClient.getAsBoolean(),
                    request,runtimePorts,installation);
            if(installed!=InstallationStatus.ARMED)
                throw new CloudException(RuntimeDiagnostics.CloudErrorCode.NETWORK,"Local package installation refused");
            if(!running||!currentClient.getAsBoolean())return null;
            InstallationStore.ReadResult readback=store.read();
            if(readback.state()!=InstallationStore.ReadState.SNAPSHOT)
                throw new CloudException(RuntimeDiagnostics.CloudErrorCode.NETWORK,"Local package readback refused");
            return CloudPackageInstallationAdapter.proof(readback.snapshot(),revision);
        });
        if(proof==null||!running||!currentClient.getAsBoolean())return;
        Reply confirmed=http("POST","devices/"+deviceId+"/package-ack",token,proof);
        if(confirmed.status!=200||!CloudPackageInstallationAdapter.validAck(confirmed.body,deviceId,revision))
            throw new IllegalStateException("Cloud package ACK rejected");
        boolean acknowledged=mutations.call(()->running&&currentClient.getAsBoolean()&&store.markAcknowledged(revision));
        if(!acknowledged)throw new IllegalStateException("Cloud package ACK persistence refused");
        DiagnosticsStore.INSTANCE.setLastSuccessfulAckRevision(revision);
        DiagnosticsStore.INSTANCE.setLastCloudErrorCode(RuntimeDiagnostics.CloudErrorCode.NONE);
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
    /** Await one generic install on the owner; stopped/replaced queued work cannot mutate or authorize ACK. */
    static InstallationStatus applyAssignment(AssignmentMutationGate gate,
            java.util.function.BooleanSupplier currentClient,InstallRequest request,
            InstallationHandler.RuntimePorts runtimePorts,InstallationOperation installation) throws Exception {
        return gate.call(()->currentClient.getAsBoolean()
                ?installation.install(request,runtimePorts):InstallationStatus.ARM_FAILED);
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
     * runtime track), zeroes the Cloud diagnostics, and deliberately rotates the separate
     * local installation identity. Rotation is the secure recovery boundary: once the durable
     * device credential is deleted, reusing the old installationId would require proof that no
     * longer exists. A fresh random id lets the TV pair as a new device without weakening Cloud
     * proof-of-possession. The historical account device remains until explicitly removed.
     * The autostart pref is left as-is (AutostartPolicy then concludes NOTHING_TO_RESTORE).
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
                // Rotate FIRST: after the durable device credential is deliberately deleted,
                // reusing the old installationId would make the Cloud correctly require proof
                // that no longer exists. A fresh random installation identity lets the user
                // pair as a NEW TV without weakening server-side device proof. The historical
                // account-owned device remains server-side until explicitly removed there.
                resetInstallationIdentity.run();
                identity.reset();
                try {
                    mutations.call(()->{
                        boolean cleared=store.clearAll();
                        resetRuntime.run();
                        if(!cleared)throw new IllegalStateException("Cloud installation reset failed");
                        return null;
                    });
                } catch(Exception ownerFailure) {
                    throw new IllegalStateException("Cloud reset application failed",ownerFailure);
                }
                DiagnosticsStore.INSTANCE.resetCloudObservations();
                Log.i(TAG,"Cloud reset completed with fresh installation identity");
            }catch(RuntimeException resetFailure) {
                DiagnosticsStore.INSTANCE.setLastCloudErrorCode(RuntimeDiagnostics.CloudErrorCode.NETWORK);
                Log.w(TAG,"Cloud reset incomplete");
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
        /** Carry only an existing bounded code and a fixed label, never a raw response/cause. */
        CloudException(RuntimeDiagnostics.CloudErrorCode code,String message){super(message);this.code=code;}
    }
}
