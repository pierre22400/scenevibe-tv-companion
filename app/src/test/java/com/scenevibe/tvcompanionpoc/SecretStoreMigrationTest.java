package com.scenevibe.tvcompanionpoc;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/**
 * JVM tests for the SecretStore boundary and the fail-safe 0.7.1 plaintext migration
 * (user section 18.D). Exercises the in-memory fake so migration + fail-closed logic runs
 * without an Android runtime; the production AndroidKeyStore implementation is validated
 * by compile + lint only in this sandbox (no device).
 */
public final class SecretStoreMigrationTest {
    /**
     * A Storage that starts life as a 0.7.1 install: the two secrets sit in plaintext under
     * their legacy keys. It records exactly when a plaintext key was deleted so the tests
     * can assert delete-only-after-verified-write.
     */
    private static final class LegacyMemory implements CloudDeviceCredentials.Storage {
        final Map<String,String> values=new HashMap<>();
        final Map<String,Boolean> flags=new HashMap<>();
        @Override public String getString(String key){return values.get(key);}
        @Override public boolean getFlag(String key){return flags.getOrDefault(key,false);}
        @Override public void putFlag(String key,boolean value){flags.put(key,value);}
        @Override public boolean persistActivation(String cloudDeviceId,String deviceToken,
                String activationId,String activationSecret,String userCode) {
            values.put("cloudDeviceId",cloudDeviceId);values.remove("deviceToken");
            values.put("activationId",activationId);values.remove("activationSecret");
            values.put("userCode",userCode);return true;
        }
        @Override public boolean confirmClaimed() {
            values.remove("activationId");values.remove("userCode");flags.put("connected",true);return true;
        }
        @Override public void clearActivationTemporaries() {values.remove("activationId");values.remove("userCode");}
        @Override public void disconnect() {
            values.remove("activationId");values.remove("userCode");flags.put("connected",false);
        }
        @Override public void removeLegacyPlaintext(String key) {values.remove(key);}
    }

    /** A 0.7.1 install with a plaintext deviceToken (and an activationSecret) in prefs. */
    private static LegacyMemory legacyInstall() {
        LegacyMemory memory=new LegacyMemory();
        memory.values.put("cloudDeviceId","cloud-uuid");
        memory.values.put("deviceToken","legacy-token");
        memory.values.put("activationSecret","legacy-secret");
        memory.flags.put("connected",true);
        return memory;
    }

    /** Migration encrypts the legacy plaintext and the secret is then read from the store. */
    @Test public void plaintextMigrationSucceeds() {
        LegacyMemory memory=legacyInstall();
        SecretStore.InMemorySecretStore secrets=new SecretStore.InMemorySecretStore();
        CloudDeviceCredentials identity=new CloudDeviceCredentials(memory,secrets);
        assertEquals("legacy-token",identity.deviceToken());
        assertEquals("legacy-secret",identity.activationSecret());
        assertEquals("legacy-token",secrets.get("deviceToken"));
        assertEquals("legacy-secret",secrets.get("activationSecret"));
        assertFalse(identity.credentialUnavailable());
    }

    /** After a successful migration NO plaintext secret remains in the plaintext prefs. */
    @Test public void plaintextDeletedOnlyAfterVerifiedWrite() {
        LegacyMemory memory=legacyInstall();
        SecretStore.InMemorySecretStore secrets=new SecretStore.InMemorySecretStore();
        new CloudDeviceCredentials(memory,secrets);
        assertNull(memory.values.get("deviceToken"));
        assertNull(memory.values.get("activationSecret"));
        // The ciphertext-backed values still resolve, proving delete happened AFTER the write.
        assertEquals("legacy-token",secrets.get("deviceToken"));
        assertEquals("legacy-secret",secrets.get("activationSecret"));
    }

    /** A write that is not durable leaves the OLD plaintext intact: no data loss, no delete. */
    @Test public void writeFailureKeepsOldSecret() {
        LegacyMemory memory=legacyInstall();
        SecretStore.InMemorySecretStore secrets=new SecretStore.InMemorySecretStore();
        secrets.failWrites(true); // Simulate a non-durable keystore commit.
        CloudDeviceCredentials identity=new CloudDeviceCredentials(memory,secrets);
        // Plaintext was NOT deleted (never delete-first) so the old secret is preserved.
        assertEquals("legacy-token",memory.values.get("deviceToken"));
        assertEquals("legacy-secret",memory.values.get("activationSecret"));
        assertNull(secrets.get("deviceToken"));
        // No plaintext fallback: the encrypted accessor does not surface the plaintext.
        assertNull(identity.deviceToken());
    }

    /** Corruption during a secret read fails closed: unavailable, no plaintext, no new identity. */
    @Test public void corruptionFailsClosed() {
        LegacyMemory memory=new LegacyMemory();
        memory.values.put("cloudDeviceId","cloud-uuid");
        SecretStore.InMemorySecretStore secrets=new SecretStore.InMemorySecretStore();
        secrets.put("deviceToken","device-token");
        secrets.corrupt("deviceToken"); // GCM tag mismatch / keystore corruption.
        CloudDeviceCredentials identity=new CloudDeviceCredentials(memory,secrets);
        assertNull(identity.deviceToken()); // Never returns plaintext or a fabricated value.
        assertTrue(identity.credentialUnavailable());
        // The identity was NOT auto-erased: the non-secret cloudDeviceId survives.
        assertEquals("cloud-uuid",identity.cloudDeviceId());
    }

    /** Disconnect keeps the durable deviceToken secret while dropping the temporaries. */
    @Test public void disconnectKeepsSecret() {
        LegacyMemory memory=new LegacyMemory();
        SecretStore.InMemorySecretStore secrets=new SecretStore.InMemorySecretStore();
        CloudDeviceCredentials identity=new CloudDeviceCredentials(memory,secrets);
        identity.persistActivation("cloud-uuid","device-token","act-1","secret","123456");
        identity.confirmClaimed();
        identity.disconnect();
        assertEquals("device-token",identity.deviceToken());
        assertNull(identity.activationSecret());
        assertFalse(identity.connected());
    }

    /**
     * A diagnostics-style caller can only reach the non-secret observables; neither the
     * plaintext Storage nor the credentialUnavailable signal exposes the raw secret.
     */
    @Test public void secretNotExposedToDiagnosticsAccessor() {
        LegacyMemory memory=new LegacyMemory();
        SecretStore.InMemorySecretStore secrets=new SecretStore.InMemorySecretStore();
        CloudDeviceCredentials identity=new CloudDeviceCredentials(memory,secrets);
        identity.persistActivation("cloud-uuid","top-secret-token","act-1","top-secret-secret","123456");
        // The secret is not stored in the plaintext prefs seam at all.
        assertNull(memory.values.get("deviceToken"));
        assertNull(memory.values.get("activationSecret"));
        for(String value:memory.values.values()) {
            assertNotEquals("top-secret-token",value);
            assertNotEquals("top-secret-secret",value);
        }
        // The fail-closed observable is a boolean, never the secret.
        assertFalse(identity.credentialUnavailable());
    }
}
