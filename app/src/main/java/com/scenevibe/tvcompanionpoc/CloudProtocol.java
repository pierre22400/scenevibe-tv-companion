package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;
import java.time.Instant;

/** Pure envelope checks shared by the outbound client and deterministic JVM tests. */
final class CloudProtocol {
    /**
     * A forged or malformed 201 cannot cause any credential to be stored. The canonical
     * response has NO 'type' field; it carries activationId + deviceId (both UUIDs), a
     * six-digit userCode, and length-bounded activationSecret + deviceToken plus a parseable
     * ISO expiresAt.
     */
    static boolean validActivation(JSONObject data) {
        if(data==null)return false;
        try {Instant.parse(data.optString("expiresAt"));}
        catch(Exception invalid) {return false;}
        return data.optString("activationId").matches("[0-9a-f-]{36}")
            &&data.optString("deviceId").matches("[0-9a-f-]{36}")
            &&data.optString("userCode").matches("[0-9]{6}")
            &&data.optString("activationSecret").matches("[A-Za-z0-9_-]{20,200}")
            &&data.optString("deviceToken").matches("[A-Za-z0-9_-]{20,200}");
    }
    /** The activation status endpoint returns only these three lifecycle states. */
    static boolean validActivationStatus(String status) {
        return "open".equals(status)||"claimed".equals(status)||"expired".equals(status);
    }
    /**
     * An assignment must be the canonical envelope, target this cloud device and advance
     * (or retry) the cached revision, and carry a text-only runtime track for Prime.
     */
    static boolean validAssignment(JSONObject data,String cloudDeviceId,long cached) {
        if(data==null||!"scenevibe.cloud.assignment.v1".equals(data.optString("type"))
            ||!cloudDeviceId.equals(data.optString("deviceId")))return false;
        Object rawRevision=data.opt("revision");
        if(!(rawRevision instanceof Number))return false;
        long revision=((Number)rawRevision).longValue();
        JSONObject runtime=data.optJSONObject("runtimeTrack");
        String finalTrackId=data.optString("finalTrackId","");
        String trackId=data.optString("trackId","");
        return revision>=1&&revision>=cached&&runtime!=null
            &&!finalTrackId.isEmpty()&&!trackId.isEmpty()
            &&"scenevibe.track.v1".equals(runtime.optString("type"))
            &&trackId.equals(runtime.optString("trackId"))
            &&"com.amazon.amazonvideo.livingroom".equals(runtime.optString("targetPackage"));
    }
    /**
     * The ACK response confirms the exact device + revision and reports acknowledged
     * delivery; it has NO 'type' and no 'status' field.
     */
    static boolean validAck(JSONObject data,String cloudDeviceId,long expectedRevision) {
        if(data==null||!cloudDeviceId.equals(data.optString("deviceId")))return false;
        Object rawRevision=data.opt("revision");
        if(!(rawRevision instanceof Number))return false;
        return ((Number)rawRevision).longValue()==expectedRevision
            &&"acknowledged".equals(data.optString("deliveryStatus"));
    }
    /** Bounded exponential retry leaves the MediaSession scheduler untouched. */
    static long backoffSeconds(int failures) {return Math.min(120L,15L*(1L<<Math.min(4,Math.max(0,failures))));}
    /** Utility class has no mutable state. */
    private CloudProtocol() {}
}
