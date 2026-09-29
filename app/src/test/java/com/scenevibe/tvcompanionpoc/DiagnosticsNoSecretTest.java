package com.scenevibe.tvcompanionpoc;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.Assert.*;

/**
 * JVM test proving the diagnostics read model never returns a secret (user section 11 /
 * 18.D). It verifies structurally that {@link RuntimeDiagnostics} declares no field named
 * after a secret, that the two identities are exposed ONLY abbreviated, and that a full
 * identity value passed into the builder never appears verbatim in any field value.
 */
public final class DiagnosticsNoSecretTest {

    private static final String FULL_INSTALLATION_ID = "installationId-0123456789abcdef-FULL";
    private static final String FULL_CLOUD_DEVICE_ID = "cloud-uuid-0123456789abcdef-FULL";
    // Secret-shaped values that must NEVER be accepted or surfaced by the model.
    private static final String DEVICE_TOKEN = "device-token-SECRET-do-not-leak";
    private static final String ACTIVATION_SECRET = "activation-secret-SECRET-do-not-leak";

    /** The model exposes NO field whose name matches a banned secret. */
    @Test public void modelDeclaresNoSecretField() {
        String[] banned={"devicetoken","activationsecret","lantoken","pepper",
                "databaseurl","vercel","password","token","secret","finaltrack","commenttext"};
        for (Field field : RuntimeDiagnostics.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            String name=field.getName().toLowerCase();
            for (String bad : banned) {
                assertFalse("RuntimeDiagnostics must not expose a '"+field.getName()+"' field",
                        name.contains(bad));
            }
        }
    }

    /** installationId and cloudDeviceId are surfaced ONLY abbreviated, never in full. */
    @Test public void identitiesAreAbbreviatedNeverFull() {
        RuntimeDiagnostics d=new RuntimeDiagnostics.Builder()
                .appVersion("0.8.0-tv-hardening")
                .installationId(FULL_INSTALLATION_ID)
                .cloudDeviceId(FULL_CLOUD_DEVICE_ID)
                .build();

        assertNotNull(d.installationIdAbbreviated);
        assertNotNull(d.cloudDeviceIdAbbreviated);
        // The abbreviated value is a strict prefix (+ ellipsis) and never the whole id.
        assertNotEquals(FULL_INSTALLATION_ID,d.installationIdAbbreviated);
        assertNotEquals(FULL_CLOUD_DEVICE_ID,d.cloudDeviceIdAbbreviated);
        assertTrue(FULL_INSTALLATION_ID.startsWith(
                d.installationIdAbbreviated.substring(0,RuntimeDiagnostics.ABBREVIATION_LENGTH)));
        assertTrue(d.installationIdAbbreviated.length()
                <= RuntimeDiagnostics.ABBREVIATION_LENGTH+1);
        assertTrue(d.cloudDeviceIdAbbreviated.length()
                <= RuntimeDiagnostics.ABBREVIATION_LENGTH+1);
    }

    /**
     * No field value of a fully populated snapshot ever contains a full identity or a
     * secret-shaped value, and the two identity fields hold only the abbreviated form.
     */
    @Test public void noFieldValueContainsAFullIdOrSecret() throws Exception {
        RuntimeDiagnostics d=new RuntimeDiagnostics.Builder()
                .appVersion("0.8.0-tv-hardening")
                .serviceRunning(true)
                .autostartEnabled(true)
                .cloudState(RuntimeDiagnostics.CloudState.CONNECTED)
                .installationId(FULL_INSTALLATION_ID)
                .cloudDeviceId(FULL_CLOUD_DEVICE_ID)
                .cachedTrackPresent(true)
                .cachedTrackId("track-42")
                .cachedRevision(7)
                .lastAcknowledgedRevision(7)
                .mediaSessionPermissionGranted(true)
                .lastObservedMediaApp("com.amazon.amazonvideo.livingroom")
                .mediaIdentityState(RuntimeDiagnostics.MediaIdentityState.ELIGIBLE)
                .lastBlockCode(null)
                .hadSuccessfulCloudConnection(true)
                .lastAssignmentRevisionReceived(7)
                .lastSuccessfulAckRevision(7)
                .lastCloudErrorCode(RuntimeDiagnostics.CloudErrorCode.NONE)
                .build();

        for (Field field : RuntimeDiagnostics.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object value=field.get(d);
            if (!(value instanceof String)) continue;
            String text=(String) value;
            assertFalse("A field leaked the full installationId",text.contains(FULL_INSTALLATION_ID));
            assertFalse("A field leaked the full cloudDeviceId",text.contains(FULL_CLOUD_DEVICE_ID));
            assertFalse("A field leaked a deviceToken",text.contains(DEVICE_TOKEN));
            assertFalse("A field leaked an activationSecret",text.contains(ACTIVATION_SECRET));
        }
        // The identity fields hold exactly the abbreviated form, nothing more.
        assertEquals(RuntimeDiagnostics.abbreviate(FULL_INSTALLATION_ID),d.installationIdAbbreviated);
        assertEquals(RuntimeDiagnostics.abbreviate(FULL_CLOUD_DEVICE_ID),d.cloudDeviceIdAbbreviated);
    }

    /** abbreviate() never returns the whole value of an id longer than the abbreviation length. */
    @Test public void abbreviateNeverReturnsFullLongValue() {
        assertNull(RuntimeDiagnostics.abbreviate(null));
        assertNull(RuntimeDiagnostics.abbreviate(""));
        // A short value (<= length) is returned as-is; it is not a secret and cannot be shortened.
        assertEquals("short",RuntimeDiagnostics.abbreviate("short"));
        String abbreviated=RuntimeDiagnostics.abbreviate(FULL_INSTALLATION_ID);
        assertNotEquals(FULL_INSTALLATION_ID,abbreviated);
        assertTrue(abbreviated.length()<FULL_INSTALLATION_ID.length());
    }
}
