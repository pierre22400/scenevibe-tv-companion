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
    private final ScheduledExecutorService io=Executors.newSingleThreadScheduledExecutor();
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
        this.identity=new CloudDeviceCredentials(context);
        this.cache=cache;
        this.scheduler=scheduler;
        this.installationId=new InstallationIdentity(context).installationId();
        this.origin=BuildConfig.CLOUD_ORIGIN;
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
                if(reply.status!=201||!CloudProtocol.validActivation(data)) {
                    Log.w(TAG,"Activation rejected or malformed");return;
                }
                // A valid 201 durable-persists deviceToken+cloudDeviceId IMMEDIATELY plus the
                // temporary activation state; do NOT wait for a claimed status.
                if(!identity.persistActivation(data.getString("deviceId"),data.getString("deviceToken"),
                        data.getString("activationId"),data.getString("activationSecret"),data.getString("userCode")))
                    throw new IllegalStateException("Private credential write failed");
                Log.i(TAG,"Cloud activation open");
            }catch(Exception error){Log.w(TAG,"Cloud activation unavailable");}
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
            // Observational only: a failed poll is a bounded NETWORK code, never a raw trace.
            DiagnosticsStore.INSTANCE.setLastCloudErrorCode(RuntimeDiagnostics.CloudErrorCode.NETWORK);
            publishCloudState();
            Log.w(TAG,"Cloud offline; cached track remains available");}
        long delay=CloudProtocol.backoffSeconds(failures);
        if(running)io.schedule(this::poll,delay,TimeUnit.SECONDS);
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
            DiagnosticsStore.INSTANCE.setLastCloudErrorCode(RuntimeDiagnostics.CloudErrorCode.PROTOCOL);
            throw new IllegalStateException("Invalid assignment response");
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
                    if(System.nanoTime()>deadline||output.size()+count>LIMIT)
                        throw new IllegalStateException("Cloud response timeout or too large");
                    output.write(buffer,0,count);
                }
                return new Reply(status,new JSONObject(output.toString(StandardCharsets.UTF_8.name())));
            }
        } finally {connection.disconnect();}
    }
    /** Small transport envelope with no credential exposed to logs or activities. */
    private static final class Reply {
        final int status;final JSONObject body;
        /** Captures only HTTP status and bounded parsed JSON. */
        Reply(int status,JSONObject body){this.status=status;this.body=body;}
    }
}
