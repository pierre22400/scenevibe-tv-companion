package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationSnapshot;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Iterator;
import org.json.JSONObject;

/**
 * Closed internal transport adapter. Only a newer revision may supply body/kind.
 * Same/stale revisions carry the durable codec/bytes to the sole PackageInstaller
 * authority. Exact logical UTF-8 is persisted as one artifact and never normalized.
 */
final class CloudPackageInstallationAdapter {
    static final String VERSION="scenevibe.cloud.package-assignment.v1";
    static final String VIDEO_CODEC="scenevibe.cloud.assignment.v1";
    static final String CODEC_VERSION="1.0.0";
    static final String VIDEO_ARTIFACT="cloud-video";
    private static final long MAX_SAFE_REVISION=9_007_199_254_740_991L;
    static final int MAX_BODY_BYTES=1_048_576;

    /** Validate closed outer identity/revision, then defer revision policy to the existing installer. */
    static InstallRequest adapt(JSONObject envelope,String deviceId,InstallationStore.ReadResult durable) {
        try {
            closed(envelope,"version","deviceId","revision","kind","codecId","codecVersion","body","deliveryDigest");
            if(!VERSION.equals(envelope.get("version"))||deviceId==null||!deviceId.equals(envelope.get("deviceId")))throw invalid();
            long revision=revision(envelope.get("revision"));
            if(durable==null||durable.state()==InstallationStore.ReadState.CORRUPT)throw invalid();
            if(durable.state()==InstallationStore.ReadState.SNAPSHOT&&revision<=durable.snapshot().revision()) {
                InstallRequest saved=durable.snapshot().canonical();
                return new InstallRequest(revision,saved.codecId(),saved.artifacts());
            }
            String kind=string(envelope,"kind"),codec=string(envelope,"codecId"),version=string(envelope,"codecVersion");
            String body=string(envelope,"body");byte[] bytes=bodyBytes(body);
            if(!CODEC_VERSION.equals(version)||!digest(codec,version,bytes).equals(envelope.get("deliveryDigest")))throw invalid();
            if("banner".equals(kind)&&TvCapabilities.CODEC_BANNER_WALL_OVERLAY.equals(codec))
                return new InstallRequest(revision,codec,Collections.singletonMap(BannerInstallationHandler.ARTIFACT,bytes));
            if("video".equals(kind)&&VIDEO_CODEC.equals(codec)) {
                CloudV1InstallationAdapter.Assignment video=CloudV1InstallationAdapter.adapt(new JSONObject(body),deviceId);
                if(video.request().revision()!=revision)throw invalid();
                return new InstallRequest(revision,video.request().codecId(),Collections.singletonMap(VIDEO_ARTIFACT,bytes));
            }
            throw invalid();
        } catch(Exception rejected) {throw invalid();}
    }

    /** Derive proof only from the exact readback snapshot whose restore/ARM just succeeded on the owner. */
    static JSONObject proof(InstallationSnapshot snapshot,long expectedRevision) {
        try {
            if(snapshot==null||snapshot.revision()!=expectedRevision)throw invalid();
            InstallRequest request=snapshot.canonical();String codec;byte[] bytes;
            if(TvCapabilities.CODEC_BANNER_WALL_OVERLAY.equals(request.codecId())&&BannerInstallationHandler.HANDLER_ID.equals(snapshot.handlerId())) {
                codec=TvCapabilities.CODEC_BANNER_WALL_OVERLAY;bytes=request.artifact(BannerInstallationHandler.ARTIFACT);
            } else if((TvCapabilities.CODEC_TRACK.equals(request.codecId())&&InstallationStore.COMPAT_TRACK_HANDLER_ID.equals(snapshot.handlerId()))
                    ||(TvCapabilities.CODEC_TRACK_OVERLAY.equals(request.codecId())&&InstallationStore.COMPAT_OVERLAY_HANDLER_ID.equals(snapshot.handlerId()))) {
                codec=VIDEO_CODEC;bytes=request.artifact(VIDEO_ARTIFACT);
            } else throw invalid();
            if(request.artifactCount()!=1||bytes==null)throw invalid();
            bodyBytes(VideoRuntimePreparation.utf8(bytes,800_000));
            return new JSONObject().put("revision",expectedRevision).put("codecId",codec)
                    .put("codecVersion",CODEC_VERSION).put("deliveryDigest",digest(codec,CODEC_VERSION,bytes));
        } catch(Exception rejected) {throw invalid();}
    }
    /** Require one exact closed ACK response before the separate local confirmed revision can advance. */
    static boolean validAck(JSONObject body,String deviceId,long revision) {
        try {
            closed(body,"deviceId","revision","status");
            return deviceId.equals(body.get("deviceId"))&&revision(body.get("revision"))==revision
                    &&"acknowledged".equals(body.get("status"));
        } catch(Exception malformed) {return false;}
    }
    /** Reject missing/unknown fields without interpreting opaque same-revision descendants. */
    private static void closed(JSONObject object,String... fields) {
        if(object==null||object.length()!=fields.length)throw invalid();
        for(String field:fields)if(!object.has(field))throw invalid();
        Iterator<String> keys=object.keys();while(keys.hasNext()) {
            String key=keys.next();boolean known=false;for(String field:fields)known|=field.equals(key);if(!known)throw invalid();
        }
    }
    /** Numbers never coerce strings, fractions, unsafe JSON integers or invalid positive revisions. */
    private static long revision(Object value) {
        if(!(value instanceof Number))throw invalid();double number=((Number)value).doubleValue();
        if(!Double.isFinite(number)||number<1||number>MAX_SAFE_REVISION||number!=Math.rint(number))throw invalid();
        return ((Number)value).longValue();
    }
    /** Read a required string without JSONObject's scalar coercions. */
    private static String string(JSONObject object,String key) throws Exception {
        Object value=object.get(key);if(!(value instanceof String))throw invalid();return (String)value;
    }
    /** Preserve well-formed Unicode and enforce the Cloud/TV common logical body ceiling. */
    private static byte[] bodyBytes(String body) {
        if(body.length()>800_000)throw invalid();
        for(int i=0;i<body.length();i++) {
            char unit=body.charAt(i);
            if(Character.isHighSurrogate(unit)) {if(++i>=body.length()||!Character.isLowSurrogate(body.charAt(i)))throw invalid();}
            else if(Character.isLowSurrogate(unit))throw invalid();
        }
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BODY_BYTES)throw invalid();return bytes;
    }
    /** Match Cloud's SHA-256(codec UTF-8 + NUL + version UTF-8 + NUL + exact logical body UTF-8). */
    private static String digest(String codec,String version,byte[] bytes) throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");hash.update(codec.getBytes(StandardCharsets.UTF_8));hash.update((byte)0);
        hash.update(version.getBytes(StandardCharsets.UTF_8));hash.update((byte)0);hash.update(bytes);
        StringBuilder hex=new StringBuilder(64);for(byte value:hash.digest())hex.append(Character.forDigit((value&255)>>>4,16)).append(Character.forDigit(value&15,16));
        return hex.toString();
    }
    /** Fixed failure retains neither parser cause, author text nor device identity. */
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Invalid Cloud package");}
    /** Static transport code owns no poller, store, runtime or credential. */
    private CloudPackageInstallationAdapter() {}
}
