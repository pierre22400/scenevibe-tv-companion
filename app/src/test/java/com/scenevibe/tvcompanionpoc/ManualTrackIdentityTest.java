package com.scenevibe.tvcompanionpoc;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Les octets de fixture sortent réellement du nouveau préparateur Studio/Cloud.
 * Ce test JVM ne prétend pas décoder du son ni accorder une admission native.
 */
public final class ManualTrackIdentityTest {
    /** Relit un profil synthétique commis, sans accès au réseau ni au catalogue privé. */
    private byte[] bytes() throws Exception {
        try(InputStream input=getClass().getClassLoader().getResourceAsStream("realtrack001/profile.json")) {
            assertNotNull(input);return input.readAllBytes();
        }
    }
    /** Accepte l'identité explicite de six nouveaux événements, sans les empreintes historiques. */
    @Test public void sixManualEventsHaveAnExplicitProgramReference() throws Exception {
        MultimediaDeliveryProfile profile=new MultimediaDeliveryProfile(bytes());
        assertTrue(ManualTrackIdentity.matches(profile));assertFalse(CloudMultimediaAdmission.mapped(profile));
        JSONObject track=new JSONObject(new String(profile.trackBytes,StandardCharsets.UTF_8));
        assertEquals(6,track.getJSONArray("events").length());assertEquals(3,profile.resources.size());
        assertEquals(3,profile.resources.stream().map(row->row.sha256).distinct().count());
    }
    /** Une empreinte recalculée sur un autre épisode n'accorde pas la référence locale. */
    @Test public void changedEpisodeRemainsRefusedEvenWithFreshContentHash() throws Exception {
        JSONObject root=new JSONObject(new String(bytes(),StandardCharsets.UTF_8));
        JSONObject track=new JSONObject(new String(Base64.getDecoder().decode(root.getString("trackBase64")),StandardCharsets.UTF_8));
        track.getJSONObject("target").getJSONObject("program").put("episode",5);
        byte[] changed=track.toString().getBytes(StandardCharsets.UTF_8);
        root.put("trackBase64",Base64.getEncoder().encodeToString(changed));
        root.getJSONObject("identity").put("snapshotSha256",MultimediaDeliveryProfile.hash(changed));
        assertFalse(ManualTrackIdentity.matches(new MultimediaDeliveryProfile(root.toString().getBytes(StandardCharsets.UTF_8))));
    }
    /** Une édition auto-attestée ne devient pas la référence inconnue confirmable par le propriétaire. */
    @Test public void selfAttestedEditionIsNotAcceptedAsOwnerReference() throws Exception {
        JSONObject root=new JSONObject(new String(bytes(),StandardCharsets.UTF_8));
        JSONObject track=new JSONObject(new String(Base64.getDecoder().decode(root.getString("trackBase64")),StandardCharsets.UTF_8));
        track.getJSONObject("target").getJSONObject("edition").put("status","EVIDENCE_REFERENCED");
        byte[] changed=track.toString().getBytes(StandardCharsets.UTF_8);
        root.put("trackBase64",Base64.getEncoder().encodeToString(changed));
        root.getJSONObject("identity").put("snapshotSha256",MultimediaDeliveryProfile.hash(changed));
        assertFalse(ManualTrackIdentity.matches(new MultimediaDeliveryProfile(root.toString().getBytes(StandardCharsets.UTF_8))));
    }
}
