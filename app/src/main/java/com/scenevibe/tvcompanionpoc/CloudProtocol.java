package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;
import java.time.Instant;

/** Pure envelope checks shared by the outbound client and deterministic JVM tests. */
final class CloudProtocol {
    /** A forged or malformed activation cannot cause any token to be stored. */
    static boolean validActivation(JSONObject data) {
        if(data==null)return false;
        try {Instant.parse(data.optString("expiresAt"));}
        catch(Exception invalid) {return false;}
        return "scenevibe.cloud.activation.v1".equals(data.optString("type"))
            &&data.optString("code").matches("[0-9]{6}")
            &&data.optString("activationId").matches("[0-9a-f-]{36}")
            &&data.optString("activationSecret").matches("[A-Za-z0-9_-]{43}")
            &&data.optString("deviceToken").matches("[A-Za-z0-9_-]{43}");
    }
    /** An assignment must target this TV and advance (or retry) the cached revision. */
    static boolean validAssignment(JSONObject data,String deviceId,long cached) {
        if(data==null||!"scenevibe.cloud.assignment.v1".equals(data.optString("type"))
            ||!deviceId.equals(data.optString("deviceId")))return false;
        Object rawRevision=data.opt("revision");
        if(!(rawRevision instanceof Number))return false;
        long revision=((Number)rawRevision).longValue();
        JSONObject runtime=data.optJSONObject("runtimeTrack");
        String trackId=data.optString("trackId","");
        return revision>=1&&revision>=cached&&runtime!=null&&!trackId.isEmpty()
            &&"com.amazon.amazonvideo.livingroom".equals(runtime.optString("targetPackage"))
            &&trackId.equals(runtime.optString("trackId"));
    }
    /** Bounded exponential retry leaves the MediaSession scheduler untouched. */
    static long backoffSeconds(int failures) {return Math.min(120L,15L*(1L<<Math.min(4,Math.max(0,failures))));}
    /** Utility class has no mutable state. */
    private CloudProtocol() {}
}
