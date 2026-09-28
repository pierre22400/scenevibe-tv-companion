package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Rejects forged canonical /api/v1 envelopes and proves backoff is independent of playback timing. */
public final class CloudProtocolTest {
    private static final String UUID_A="01234567-89ab-cdef-0123-456789abcdef";
    private static final String UUID_B="fedcba98-7654-3210-fedc-ba9876543210";

    /** Builds the canonical 201 device-activation body: NO 'type', userCode is six digits. */
    private static JSONObject activation() throws Exception {
        return new JSONObject()
            .put("activationId",UUID_A)
            .put("deviceId",UUID_B)
            .put("userCode","123456")
            .put("activationSecret","A".repeat(43))
            .put("deviceToken","B".repeat(43))
            .put("expiresAt","2026-09-26T12:00:00Z");
    }
    /** Builds the canonical assignment envelope targeting the given cloud device + revision. */
    private static JSONObject assignment(String deviceId,int revision,String trackId) throws Exception {
        return new JSONObject().put("type","scenevibe.cloud.assignment.v1")
            .put("deviceId",deviceId).put("revision",revision)
            .put("finalTrackId","final-"+trackId).put("trackId",trackId)
            .put("runtimeTrack",new JSONObject().put("type","scenevibe.track.v1")
                .put("trackId",trackId).put("targetPackage","com.amazon.amazonvideo.livingroom")
                .put("mediaIdentity",new JSONObject()
                    .put("platform","prime_video")
                    .put("videoId","video-1")
                    .put("title","Columbo — Eaux troubles")
                    .put("durationMs",5_884_768)));
    }

    /** A canonical 201 with userCode, UUID ids, bounded secrets and ISO expiry validates. */
    @Test public void canonicalActivationWithUserCodeValidates() throws Exception {
        assertTrue(CloudProtocol.validActivation(activation()));
    }
    /** The obsolete 'code' field cannot substitute for the required six-digit 'userCode'. */
    @Test public void activationRejectsOldCodeFieldWhenUserCodeMissing() throws Exception {
        JSONObject legacy=activation();legacy.remove("userCode");legacy.put("code","123456");
        assertFalse(CloudProtocol.validActivation(legacy));
    }
    /** A userCode that is not exactly six digits is rejected. */
    @Test public void activationRejectsNonSixDigitUserCode() throws Exception {
        assertFalse(CloudProtocol.validActivation(activation().put("userCode","12345")));
        assertFalse(CloudProtocol.validActivation(activation().put("userCode","1234567")));
        assertFalse(CloudProtocol.validActivation(activation().put("userCode","12a456")));
    }
    /** Malformed UUID deviceId or activationId cannot persist a device credential. */
    @Test public void activationRejectsMalformedUuids() throws Exception {
        assertFalse(CloudProtocol.validActivation(activation().put("deviceId","not-a-uuid")));
        assertFalse(CloudProtocol.validActivation(activation().put("activationId","short")));
    }
    /** A bounded secret or token below the minimum length is rejected. */
    @Test public void activationRejectsUnboundedCredentials() throws Exception {
        assertFalse(CloudProtocol.validActivation(activation().put("deviceToken","short")));
        assertFalse(CloudProtocol.validActivation(activation().put("activationSecret","short")));
    }
    /** An unparseable expiresAt is rejected before any credential is trusted. */
    @Test public void activationRejectsUnparseableExpiry() throws Exception {
        assertFalse(CloudProtocol.validActivation(activation().put("expiresAt","not-a-date")));
    }

    /** The status endpoint accepts only the three canonical lifecycle states. */
    @Test public void activationStatusAcceptsOnlyCanonicalStates() {
        assertTrue(CloudProtocol.validActivationStatus("open"));
        assertTrue(CloudProtocol.validActivationStatus("claimed"));
        assertTrue(CloudProtocol.validActivationStatus("expired"));
        assertFalse(CloudProtocol.validActivationStatus("pending"));
        assertFalse(CloudProtocol.validActivationStatus("connected"));
        assertFalse(CloudProtocol.validActivationStatus(null));
    }

    /** The canonical assignment envelope for this device and a newer revision validates. */
    @Test public void assignmentEnvelopeValidates() throws Exception {
        assertTrue(CloudProtocol.validAssignment(assignment(UUID_B,2,"track-1"),UUID_B,1));
    }
    /** An assignment addressed to another cloud device never replaces local cache. */
    @Test public void assignmentRejectsWrongDevice() throws Exception {
        assertFalse(CloudProtocol.validAssignment(assignment(UUID_A,2,"track-1"),UUID_B,1));
    }
    /** A revision at or below the cached revision is stale and rejected. */
    @Test public void assignmentRejectsStaleRevision() throws Exception {
        assertFalse(CloudProtocol.validAssignment(assignment(UUID_B,2,"track-1"),UUID_B,3));
    }
    /** An empty finalTrackId cannot be acknowledged, so the envelope is rejected. */
    @Test public void assignmentRejectsEmptyFinalTrackId() throws Exception {
        assertFalse(CloudProtocol.validAssignment(assignment(UUID_B,2,"track-1").put("finalTrackId",""),UUID_B,1));
    }
    /** The runtimeTrack.trackId must equal the envelope trackId. */
    @Test public void assignmentRejectsRuntimeTrackIdMismatch() throws Exception {
        JSONObject envelope=assignment(UUID_B,2,"track-1");
        envelope.getJSONObject("runtimeTrack").put("trackId","mismatch");
        assertFalse(CloudProtocol.validAssignment(envelope,UUID_B,1));
    }
    /** A runtimeTrack whose type is not scenevibe.track.v1 is rejected. */
    @Test public void assignmentRejectsWrongRuntimeTrackType() throws Exception {
        JSONObject envelope=assignment(UUID_B,2,"track-1");
        envelope.getJSONObject("runtimeTrack").put("type","scenevibe.other.v1");
        assertFalse(CloudProtocol.validAssignment(envelope,UUID_B,1));
    }

    /** A package-only runtime is unsafe: media identity is mandatory. */
    @Test public void assignmentRejectsMissingMediaIdentity() throws Exception {
        JSONObject envelope=assignment(UUID_B,2,"track-1");
        envelope.getJSONObject("runtimeTrack").remove("mediaIdentity");
        assertFalse(CloudProtocol.validAssignment(envelope,UUID_B,1));
    }

    /** The ACK response confirms the exact device + revision with acknowledged delivery. */
    @Test public void ackAcceptsMatchingDeviceRevisionAndStatus() throws Exception {
        JSONObject ack=new JSONObject().put("deviceId",UUID_B).put("revision",4)
            .put("deliveryStatus","acknowledged");
        assertTrue(CloudProtocol.validAck(ack,UUID_B,4));
    }
    /** An ACK for another device is rejected. */
    @Test public void ackRejectsWrongDevice() throws Exception {
        JSONObject ack=new JSONObject().put("deviceId",UUID_A).put("revision",4)
            .put("deliveryStatus","acknowledged");
        assertFalse(CloudProtocol.validAck(ack,UUID_B,4));
    }
    /** An ACK confirming a different revision than expected is rejected. */
    @Test public void ackRejectsWrongRevision() throws Exception {
        JSONObject ack=new JSONObject().put("deviceId",UUID_B).put("revision",5)
            .put("deliveryStatus","acknowledged");
        assertFalse(CloudProtocol.validAck(ack,UUID_B,4));
    }
    /** A missing or non-acknowledged deliveryStatus is rejected. */
    @Test public void ackRejectsMissingOrOtherDeliveryStatus() throws Exception {
        JSONObject missing=new JSONObject().put("deviceId",UUID_B).put("revision",4);
        assertFalse(CloudProtocol.validAck(missing,UUID_B,4));
        JSONObject assigned=new JSONObject().put("deviceId",UUID_B).put("revision",4)
            .put("deliveryStatus","assigned");
        assertFalse(CloudProtocol.validAck(assigned,UUID_B,4));
    }

    /** Retry is bounded while the scheduler sees only passive MediaSession snapshots. */
    @Test public void backoffCapsAtTwoMinutes() {
        assertEquals(15,CloudProtocol.backoffSeconds(0));
        assertEquals(30,CloudProtocol.backoffSeconds(1));
        assertEquals(120,CloudProtocol.backoffSeconds(100));
    }
}
