package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Rejects forged cloud envelopes and proves backoff is independent of playback timing. */
public final class CloudProtocolTest {
    /** A malformed activation cannot persist a candidate device credential. */
    @Test public void activationRequiresCompleteTypedCredentials() throws Exception {
        String raw="{\"type\":\"scenevibe.cloud.activation.v1\",\"code\":\"123456\",\"expiresAt\":\"2026-09-26T12:00:00Z\",\"activationId\":\"01234567-89ab-cdef-0123-456789abcdef\",\"activationSecret\":\""+"A".repeat(43)+"\",\"deviceToken\":\""+"B".repeat(43)+"\"}";
        JSONObject valid=new JSONObject(raw);assertTrue(CloudProtocol.validActivation(valid));
        valid.put("deviceToken","short");assertFalse(CloudProtocol.validActivation(valid));
        valid.put("deviceToken","B".repeat(43));valid.put("code","wrong");
        assertFalse(CloudProtocol.validActivation(valid));
    }
    /** Wrong device and old revision never replace local cache. */
    @Test public void assignmentRequiresMatchingDeviceAndCurrentRevision() throws Exception {
        JSONObject valid=new JSONObject().put("type","scenevibe.cloud.assignment.v1")
            .put("deviceId","tv-1").put("revision",2).put("trackId","track-1")
            .put("runtimeTrack",new JSONObject().put("trackId","track-1").put("targetPackage","com.amazon.amazonvideo.livingroom"));
        assertTrue(CloudProtocol.validAssignment(valid,"tv-1",1));
        assertFalse(CloudProtocol.validAssignment(valid,"tv-2",1));
        assertFalse(CloudProtocol.validAssignment(valid,"tv-1",3));
        valid.getJSONObject("runtimeTrack").put("trackId","mismatch");
        assertFalse(CloudProtocol.validAssignment(valid,"tv-1",1));
    }
    /** Retry is bounded while the scheduler sees only passive MediaSession snapshots. */
    @Test public void backoffCapsAtTwoMinutes() {
        assertEquals(15,CloudProtocol.backoffSeconds(0));
        assertEquals(30,CloudProtocol.backoffSeconds(1));
        assertEquals(120,CloudProtocol.backoffSeconds(100));
    }
}
