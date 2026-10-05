package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pin CloudProtocol's wire decisions, exact serialized artifact bytes and immutable ACK isolation. */
public final class M4PhaseFAdapterTest {
    /** All seven producer-frozen envelopes retain the actual wire validator's acceptance decision. */
    @Test public void sevenFrozenEnvelopesPreserveWireTruthAndExactMapping() throws Exception {
        JSONArray cases=M4PhaseAFixtures.resource("/m1/cloud-envelopes.json").getJSONArray("cases");
        assertEquals(7,cases.length());
        for(int i=0;i<cases.length();i++) {
            JSONObject envelope=cases.getJSONObject(i).getJSONObject("envelope");
            String device=envelope.getString("deviceId");
            boolean expected=CloudProtocol.validAssignment(envelope,device,0),accepted=false;
            try {
                CloudV1InstallationAdapter.Assignment mapped=CloudV1InstallationAdapter.adapt(envelope,device);accepted=true;
                assertEquals(envelope.optLong("revision"),mapped.request().revision());
                assertEquals(envelope.optString("finalTrackId"),mapped.finalTrackId());
                assertEquals(envelope.has("overlayManifest")?TvCapabilities.CODEC_TRACK_OVERLAY:TvCapabilities.CODEC_TRACK,mapped.request().codecId());
                assertArrayEquals(envelope.getJSONObject("runtimeTrack").toString().getBytes(StandardCharsets.UTF_8),mapped.request().artifact("runtime"));
                if(envelope.has("overlayManifest"))assertArrayEquals(envelope.getJSONObject("overlayManifest").toString().getBytes(StandardCharsets.UTF_8),mapped.request().artifact("manifest"));
                else assertNull(mapped.request().artifact("manifest"));
                assertEquals(envelope.getString("trackId"),new JSONObject(new String(mapped.request().artifact("runtime"),StandardCharsets.UTF_8)).getString("trackId"));
            } catch(IllegalArgumentException rejected) { /* Compare the bounded wire decision only. */ }
            assertEquals("frozen envelope acceptance",expected,accepted);
        }
    }
    /** Missing additive fields remain the legacy runtime-only codec. */
    @Test public void legacyMapsOneArtifactWithoutInventingManifest() throws Exception {
        JSONObject envelope=M4PhaseFFixtures.envelope(false,14);
        CloudV1InstallationAdapter.Assignment mapped=CloudV1InstallationAdapter.adapt(envelope,envelope.getString("deviceId"));
        assertEquals(TvCapabilities.CODEC_TRACK,mapped.request().codecId());assertEquals(1,mapped.request().artifactCount());
        assertNull(mapped.request().artifact("manifest"));assertEquals(14,mapped.request().revision());
    }
    /** Adaptation freezes the serialized bytes and ACK id before queued runtime mutation. */
    @Test public void laterEnvelopeAndReturnedByteMutationsCannotRebindAssignment() throws Exception {
        JSONObject envelope=M4PhaseFFixtures.envelope(true,13);
        CloudV1InstallationAdapter.Assignment mapped=CloudV1InstallationAdapter.adapt(envelope,envelope.getString("deviceId"));
        byte[] expected=mapped.request().artifact("runtime");String ack=mapped.finalTrackId();
        envelope.put("finalTrackId","other");envelope.getJSONObject("runtimeTrack").put("trackId","other");
        mapped.request().artifact("runtime")[0]=0;
        assertArrayEquals(expected,mapped.request().artifact("runtime"));assertEquals(ack,mapped.finalTrackId());
    }
    /** JSONObject coercions already allowed by CloudProtocol must not become a new adapter wire rejection. */
    @Test public void existingAckIdentifierAndRevisionCoercionsRemainExact() throws Exception {
        JSONObject envelope=M4PhaseFFixtures.envelope(false,13).put("finalTrackId",123).put("revision",13.9);
        assertTrue(CloudProtocol.validAssignment(envelope,envelope.getString("deviceId"),0));
        CloudV1InstallationAdapter.Assignment mapped=CloudV1InstallationAdapter.adapt(envelope,envelope.getString("deviceId"));
        assertEquals("123",mapped.finalTrackId());assertEquals(13,mapped.request().revision());
    }
    /** Exact UTF-8 serialization preserves composed/decomposed French, ligature, apostrophe and emoji. */
    @Test public void exactHistoricalJsonSerializationPreservesUnicode() throws Exception {
        JSONObject envelope=M4PhaseFFixtures.envelope(true,13);
        envelope.getJSONObject("runtimeTrack").getJSONArray("comments").getJSONObject(0).put("text",M4PhaseAFixtures.UNICODE);
        InstallRequest request=CloudV1InstallationAdapter.adapt(envelope,envelope.getString("deviceId")).request();
        assertArrayEquals(envelope.getJSONObject("runtimeTrack").toString().getBytes(StandardCharsets.UTF_8),request.artifact("runtime"));
        assertEquals(M4PhaseAFixtures.UNICODE,new JSONObject(new String(request.artifact("runtime"),StandardCharsets.UTF_8))
                .getJSONArray("comments").getJSONObject(0).getString("text"));
    }
    /** Static fields/API contain no secret/live dependency or transport ACK metadata inside the core request. */
    @Test public void handoffRetainsOnlyRequestAndFinalTrackId() {
        assertEquals(2,CloudV1InstallationAdapter.Assignment.class.getDeclaredFields().length);
        for(java.lang.reflect.Field field:CloudV1InstallationAdapter.Assignment.class.getDeclaredFields()) {
            assertTrue(java.lang.reflect.Modifier.isFinal(field.getModifiers()));
            assertTrue(field.getType()==InstallRequest.class||field.getType()==String.class);
        }
        for(java.lang.reflect.Field field:InstallRequest.class.getDeclaredFields())assertFalse(field.getName().contains("finalTrack"));
    }
    /** Adapter failure exposes a fixed label with no submitted content or parser cause. */
    @Test public void malformedEnvelopeProducesOnlyBoundedRejection() {
        try {CloudV1InstallationAdapter.adapt(null,null);fail("must reject missing envelope");}
        catch(IllegalArgumentException refused) {assertEquals("Invalid Cloud assignment",refused.getMessage());assertNull(refused.getCause());}
    }
}
