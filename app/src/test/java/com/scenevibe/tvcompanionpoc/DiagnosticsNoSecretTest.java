package com.scenevibe.tvcompanionpoc;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

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
                .lastBlockCode(OverlayService.BLOCK_CODE_MEDIA_IDENTITY)
                .lastAutostartDecision(AutostartPolicy.Decision.AUTOSTART_NOTHING_TO_RESTORE)
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

    /** In-memory 'installation' store so peek can be exercised without an Android runtime. */
    private static final class InstallMemory implements InstallationIdentity.Storage {
        final Map<String,String> values=new HashMap<>();
        int puts;
        @Override public String get(String key){return values.get(key);}
        @Override public void put(String key,String value){puts++;values.put(key,value);}
    }

    /**
     * The bounded new fields (lastBlockCode, lastAutostartDecision) are short codes / a bounded
     * enum name and never a secret. They round-trip through the model as bounded values only.
     */
    @Test public void newBoundedFieldsAreCodesNotSecrets() {
        RuntimeDiagnostics d=new RuntimeDiagnostics.Builder()
                .appVersion("0.8.0-tv-hardening")
                .lastBlockCode(OverlayService.BLOCK_CODE_SESSION_UNAVAILABLE)
                .lastAutostartDecision(AutostartPolicy.Decision.START)
                .build();
        assertEquals(OverlayService.BLOCK_CODE_SESSION_UNAVAILABLE,d.lastBlockCode);
        assertEquals(AutostartPolicy.Decision.START,d.lastAutostartDecision);
        // The block code is one of the small bounded set; never free-form text.
        assertTrue(d.lastBlockCode.equals(OverlayService.BLOCK_CODE_MEDIA_IDENTITY)
                || d.lastBlockCode.equals(OverlayService.BLOCK_CODE_SESSION_UNAVAILABLE));
        // The autostart decision is a bounded enum, never a secret-bearing string field.
        assertTrue(d.lastAutostartDecision instanceof AutostartPolicy.Decision);
    }

    /**
     * capture()'s installationId source must be a READ-ONLY peek: when no id is stored yet and
     * there is no legacy value, peek returns null and MINTS/PERSISTS nothing. This proves that
     * opening Diagnostics never creates an InstallationIdentity.
     */
    @Test public void installationIdPeekIsNonMutatingWhenNoneExists() {
        InstallMemory install=new InstallMemory();
        InstallationIdentity identity=new InstallationIdentity(install,()->null,new SecureRandom());
        assertNull("peek must not mint an id when none exists",identity.peekInstallationId());
        assertEquals("peek must not persist anything",0,install.puts);
        assertTrue("no id should be stored by a peek",install.values.isEmpty());
        // The mutating path DOES mint and persist, confirming peek is genuinely read-only.
        String minted=identity.installationId();
        assertNotNull(minted);
        assertEquals(1,install.puts);
        // After a real mint, peek returns the stored value and still does not write again.
        assertEquals(minted,identity.peekInstallationId());
        assertEquals(1,install.puts);
    }

    /**
     * When no stored id exists but a legacy value is present, peek surfaces the legacy value
     * (so Diagnostics can show a stable abbreviated id) WITHOUT persisting it - a subsequent
     * real read would migrate it, but a snapshot must not.
     */
    @Test public void installationIdPeekSurfacesLegacyWithoutPersisting() {
        InstallMemory install=new InstallMemory();
        InstallationIdentity identity=new InstallationIdentity(install,()->"legacy-device-id",new SecureRandom());
        assertEquals("legacy-device-id",identity.peekInstallationId());
        assertEquals("peek must not persist the legacy value",0,install.puts);
        assertTrue(install.values.isEmpty());
    }

    /** Minimal in-memory cloud_identity store for the peek-migration test. */
    private static final class CloudMemory implements CloudDeviceCredentials.Storage {
        final Map<String,String> values=new HashMap<>();
        final Map<String,Boolean> flags=new HashMap<>();
        int removals;
        @Override public String getString(String key){return values.get(key);}
        @Override public boolean getFlag(String key){return flags.getOrDefault(key,false);}
        @Override public void putFlag(String key,boolean value){flags.put(key,value);}
        @Override public boolean persistActivation(String cloudDeviceId,String deviceToken,
                String activationId,String activationSecret,String userCode){return true;}
        @Override public boolean confirmClaimed(){return true;}
        @Override public void clearActivationTemporaries(){}
        @Override public void disconnect(){}
        @Override public void reset(){values.clear();flags.clear();}
        @Override public void removeLegacyPlaintext(String key){removals++;values.remove(key);}
    }

    /**
     * The read-only peek used by capture() must NOT run the legacy plaintext migration on
     * construction: a snapshot must never encrypt, delete or commit anything. A legacy
     * plaintext deviceToken stays untouched when the peek factory is used, but the normal
     * (mutating) constructor drains it. This proves opening Diagnostics never triggers a
     * credential migration.
     */
    @Test public void credentialsPeekDoesNotRunMigration() {
        CloudMemory peekStore=new CloudMemory();
        peekStore.values.put("deviceToken","legacy-plaintext-token");
        SecretStore peekSecrets=new SecretStore.InMemorySecretStore();
        // migrate=false: the peek path. Nothing is removed and no ciphertext is written.
        new CloudDeviceCredentials(peekStore,peekSecrets,false);
        assertEquals("peek must not delete legacy plaintext",0,peekStore.removals);
        assertEquals("legacy plaintext must remain untouched by a peek",
                "legacy-plaintext-token",peekStore.values.get("deviceToken"));
        assertFalse("peek must not write ciphertext",peekSecrets.contains("deviceToken"));

        // The normal constructor DOES migrate, confirming the peek path is genuinely read-only.
        CloudMemory liveStore=new CloudMemory();
        liveStore.values.put("deviceToken","legacy-plaintext-token");
        SecretStore liveSecrets=new SecretStore.InMemorySecretStore();
        new CloudDeviceCredentials(liveStore,liveSecrets);
        assertEquals("normal construction migrates and deletes the plaintext",1,liveStore.removals);
        assertTrue("normal construction writes ciphertext",liveSecrets.contains("deviceToken"));
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
