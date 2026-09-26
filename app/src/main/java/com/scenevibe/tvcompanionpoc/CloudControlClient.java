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
    private final String deviceId;
    private final String origin;
    private volatile boolean running;
    private int failures;
    /** Links the outbound client to an already created passive MediaSession scheduler. */
    CloudControlClient(Context context, CloudTrackRepository cache,MediaSyncedTrackScheduler scheduler) {
        this.identity=new CloudDeviceCredentials(context);
        this.cache=cache;
        this.scheduler=scheduler;
        this.deviceId=PairingRuntime.get(context).deviceId();
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
                JSONObject request=new JSONObject().put("deviceId",deviceId);
                Reply reply=http("POST","activations",identity.deviceToken(),request);
                JSONObject data=reply.body;
                if(reply.status!=201||!CloudProtocol.validActivation(data)) {
                    Log.w(TAG,"Activation rejected or malformed");return;
                }
                if(!identity.pending(data.getString("activationId"),data.getString("activationSecret"),
                        data.getString("deviceToken"),data.getString("code")))throw new IllegalStateException("Private credential write failed");
                Log.i(TAG,"Cloud activation open");
            }catch(Exception error){Log.w(TAG,"Cloud activation unavailable");}
        });
    }
    /** Clears only local cloud credentials; the durable cached track remains available offline. */
    void disconnect() {identity.disconnect();Log.i(TAG,"Cloud identity disconnected locally");}
    /** Stops network work when the foreground service stops; MediaSession remains independent. */
    void stop() {running=false;io.shutdownNow();}
    /** Performs one conditional fetch and schedules a bounded, backoff-aware subsequent poll. */
    private void poll() {
        if(!running)return;
        try {
            if(identity.activationId()!=null)checkActivation();
            if(identity.connected())fetchAssignment();
            failures=0;identity.setOffline(false);
        }catch(Exception error){failures=Math.min(4,failures+1);identity.setOffline(true);
            Log.w(TAG,"Cloud offline; cached track remains available");}
        long delay=CloudProtocol.backoffSeconds(failures);
        if(running)io.schedule(this::poll,delay,TimeUnit.SECONDS);
    }
    /** Promotes the pending token only after the activation secret reports claimed. */
    private void checkActivation() throws Exception {
        String activationId=identity.activationId(), temporary=identity.activationSecret();
        if(activationId==null||temporary==null)return;
        Reply reply=http("GET","activations/"+activationId,temporary,null);
        if(reply.status!=200||reply.body==null||!"scenevibe.cloud.activation.status.v1".equals(reply.body.optString("type")))
            throw new IllegalStateException("Invalid activation status");
        String status=reply.body.optString("status");
        if("claimed".equals(status)) {
            if(!identity.confirm())throw new IllegalStateException("Credential commit failed");
            Log.i(TAG,"Cloud connected");
        }else if("expired".equals(status)) {
            identity.clearPending();Log.i(TAG,"Cloud activation expired");
        }else if(!"open".equals(status))throw new IllegalStateException("Unknown activation status");
    }
    /** Downloads only revisions beyond the last ACK; retries an unacknowledged cached revision. */
    private void fetchAssignment() throws Exception {
        String token=identity.deviceToken();if(token==null)return;
        Reply reply=http("GET","devices/"+deviceId+"/assignment?afterRevision="+cache.acknowledged(),token,null);
        if(reply.status==204)return;
        JSONObject data=reply.body;
        if(reply.status!=200||data==null||!"scenevibe.cloud.assignment.v1".equals(data.optString("type")))
            throw new IllegalStateException("Invalid assignment response");
        long revision=data.optLong("revision",-1), cached=cache.revision();
        JSONObject runtime=data.optJSONObject("runtimeTrack");
        String trackId=data.optString("trackId","");
        if(!CloudProtocol.validAssignment(data,deviceId,cached))
            throw new IllegalStateException("Invalid assignment revision");
        if(revision>cached && !cache.install(revision,runtime.toString(),scheduler))
            throw new IllegalStateException("Invalid or non-durable runtime track");
        // A cached, unacknowledged revision was already restored when service started.
        JSONObject ack=new JSONObject().put("revision",revision).put("trackId",trackId);
        Reply confirmed=http("POST","devices/"+deviceId+"/ack",token,ack);
        if(confirmed.status!=200||confirmed.body==null
            ||!"scenevibe.cloud.ack.v1".equals(confirmed.body.optString("type"))
            ||confirmed.body.optLong("revision",-1)!=revision)throw new IllegalStateException("Cloud ACK rejected");
        if(!cache.markAcknowledged(revision))throw new IllegalStateException("Cloud ACK state could not be persisted");
        Log.i(TAG,"Cloud track loaded; revision="+revision);
    }
    /** Reads at most three megabytes from an explicitly configured HTTPS endpoint. */
    private Reply http(String method,String path,String token,JSONObject body) throws Exception {
        if(!running)throw new IllegalStateException("Cloud client stopped");
        HttpsURLConnection connection=(HttpsURLConnection)new URL(origin+"/api/tv/v1/"+path).openConnection();
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
                while((count=input.read(buffer))!=-1){if(output.size()+count>LIMIT)throw new IllegalStateException("Cloud response too large");output.write(buffer,0,count);}
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
