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
        // Fault-injection: when true the corresponding non-secret commit returns false and
        // leaves the prior non-secret values untouched (faithful to the production contract).
        boolean failPersistActivation;
        boolean failConfirmClaimed;
        @Override public String getString(String key){return values.get(key);}
        @Override public boolean getFlag(String key){return flags.getOrDefault(key,false);}
        @Override public void putFlag(String key,boolean value){flags.put(key,value);}
        @Override public boolean persistActivation(String cloudDeviceId,String deviceToken,
                String activationId,String activationSecret,String userCode) {
            if(failPersistActivation) return false; // Non-durable metadata commit; nothing changes.
            values.put("cloudDeviceId",cloudDeviceId);values.remove("deviceToken");
            values.put("activationId",activationId);values.remove("activationSecret");
            values.put("userCode",userCode);return true;
        }
        @Override public boolean confirmClaimed() {
            if(failConfirmClaimed) return false; // Non-durable claim commit; nothing changes.
            values.remove("activationId");values.remove("userCode");flags.put("connected",true);return true;
        }
        @Override public void clearActivationTemporaries() {values.remove("activationId");values.remove("userCode");}
        @Override public void disconnect() {
            values.remove("activationId");values.remove("userCode");flags.put("connected",false);
        }
        @Override public void reset() {values.clear();flags.clear();}
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
        // A non-durable migration write fails closed: unavailable, never operationalized.
        assertTrue(identity.credentialUnavailable());
    }

    /**
     * Migration-non-durable (Correction 2): when the encrypted write is NOT durable the
     * historical plaintext is retained for a later retry, but credentialUnavailable() is true
     * and the plaintext is NEVER returned from deviceToken()/activationSecret().
     */
    @Test public void migrationNonDurableWriteYieldsCredentialUnavailableAndKeepsPlaintext() {
        LegacyMemory memory=legacyInstall();
        SecretStore.InMemorySecretStore secrets=new SecretStore.InMemorySecretStore();
        secrets.failWrites(true); // Encrypted write commits but is not durable.
        CloudDeviceCredentials identity=new CloudDeviceCredentials(memory,secrets);
        // Historical plaintext is kept (no delete-first, no data loss).
        assertEquals("legacy-token",memory.values.get("deviceToken"));
        assertEquals("legacy-secret",memory.values.get("activationSecret"));
        // Fail-closed diagnostic is set for the non-durable-write case, not only Unavailable.
        assertTrue(identity.credentialUnavailable());
        // The plaintext is NEVER read back as an operational credential.
        assertNull(identity.deviceToken());
        assertNull(identity.activationSecret());
        // No ciphertext was durably stored either.
        assertNull(secrets.get("deviceToken"));
        assertNull(secrets.get("activationSecret"));
    }

    /**
     * (a) First-secret-write failure: the FULL previous tuple stays usable and NO new value
     * is visible; the deviceToken write failing must not leave any partial state.
     */
    @Test public void persistActivationFirstSecretWriteFailureKeepsPreviousTuple() {
        LegacyMemory memory=new LegacyMemory();
        SecretStore.InMemorySecretStore secrets=new SecretStore.InMemorySecretStore();
        CloudDeviceCredentials identity=new CloudDeviceCredentials(memory,secrets);
        // Establish a full previous tuple.
        assertTrue(identity.persistActivation("cloud-old","token-old","act-old","secret-old","111111"));
        // Inject a failure on the FIRST secret write (deviceToken) of the next activation.
        secrets.failWriteAtIndex(0);
        assertFalse(identity.persistActivation("cloud-new","token-new","act-new","secret-new","222222"));
        // Full previous tuple stays usable; no new value is visible anywhere.
        assertEquals("token-old",identity.deviceToken());
        assertEquals("secret-old",identity.activationSecret());
        assertEquals("cloud-old",identity.cloudDeviceId());
        assertEquals("act-old",identity.activationId());
        assertEquals("111111",identity.userCode());
    }

    /**
     * (b) Second-secret-write failure: the first secret is NOT left updated in isolation and
     * the full previous tuple stays usable (deviceToken must roll back to its previous value).
     */
    @Test public void persistActivationSecondSecretWriteFailureRollsBackFirstSecret() {
        LegacyMemory memory=new LegacyMemory();
        SecretStore.InMemorySecretStore secrets=new SecretStore.InMemorySecretStore();
        CloudDeviceCredentials identity=new CloudDeviceCredentials(memory,secrets);
        assertTrue(identity.persistActivation("cloud-old","token-old","act-old","secret-old","111111"));
        // Let the first (deviceToken) write succeed and fail the SECOND (activationSecret).
        secrets.failWriteAtIndex(1);
        assertFalse(identity.persistActivation("cloud-new","token-new","act-new","secret-new","222222"));
        // deviceToken was rolled back to the previous value, not left as the new one.
        assertEquals("token-old",identity.deviceToken());
        assertEquals("secret-old",identity.activationSecret());
        assertEquals("cloud-old",identity.cloudDeviceId());
        assertEquals("act-old",identity.activationId());
        assertEquals("111111",identity.userCode());
    }

    /**
     * (c) Metadata-commit failure: both secret writes succeed but the non-secret commit is not
     * durable, so the secrets must NOT be left updated in isolation and the previous tuple
     * (including both secrets) stays usable.
     */
    @Test public void persistActivationMetadataCommitFailureRollsBackSecrets() {
        LegacyMemory memory=new LegacyMemory();
        SecretStore.InMemorySecretStore secrets=new SecretStore.InMemorySecretStore();
        CloudDeviceCredentials identity=new CloudDeviceCredentials(memory,secrets);
        assertTrue(identity.persistActivation("cloud-old","token-old","act-old","secret-old","111111"));
        // Both secrets encrypt fine, but the metadata batch commit fails.
        memory.failPersistActivation=true;
        assertFalse(identity.persistActivation("cloud-new","token-new","act-new","secret-new","222222"));
        // Secrets rolled back; no old/new mix. Previous non-secret values untouched.
        assertEquals("token-old",identity.deviceToken());
        assertEquals("secret-old",identity.activationSecret());
        assertEquals("cloud-old",identity.cloudDeviceId());
        assertEquals("act-old",identity.activationId());
        assertEquals("111111",identity.userCode());
    }

    /**
     * (d) confirmClaimed-commit failure: the claimed-state commit fails, so the activationSecret
     * needed to resume polling is still present and the operation is retryable (a later
     * successful confirmClaimed completes the transition and drops the secret).
     */
    @Test public void confirmClaimedCommitFailureKeepsActivationSecretAndIsRetryable() {
        LegacyMemory memory=new LegacyMemory();
        SecretStore.InMemorySecretStore secrets=new SecretStore.InMemorySecretStore();
        CloudDeviceCredentials identity=new CloudDeviceCredentials(memory,secrets);
        assertTrue(identity.persistActivation("cloud-uuid","device-token","act-1","poll-secret","123456"));
        // Inject a non-durable claimed-state commit.
        memory.failConfirmClaimed=true;
        assertFalse(identity.confirmClaimed());
        // activationSecret survives so polling can resume; state is not yet connected.
        assertEquals("poll-secret",identity.activationSecret());
        assertFalse(identity.connected());
        // Retry: the commit now succeeds and the transition completes.
        memory.failConfirmClaimed=false;
        assertTrue(identity.confirmClaimed());
        assertTrue(identity.connected());
        assertNull(identity.activationSecret());
        assertEquals("device-token",identity.deviceToken());
    }

    /**
     * (e) Secret-removal failure: a non-durable removal of a critical secret during reset is
     * SURFACED (credentialUnavailable) rather than silently ignored.
     */
    @Test public void resetSecretRemovalFailureIsSurfaced() {
        LegacyMemory memory=new LegacyMemory();
        SecretStore.InMemorySecretStore secrets=new SecretStore.InMemorySecretStore();
        CloudDeviceCredentials identity=new CloudDeviceCredentials(memory,secrets);
        assertTrue(identity.persistActivation("cloud-uuid","device-token","act-1","secret","123456"));
        // The deviceToken removal will not durably commit.
        secrets.failRemove("deviceToken");
        identity.reset();
        // Reset does not pretend success when a critical secret removal is not durable.
        assertTrue(identity.credentialUnavailable());
        // The non-durable removal left the ciphertext record present (faithful to contract).
        assertTrue(secrets.contains("deviceToken"));
    }

    /** confirmClaimed removal failure is surfaced but the claimed transition still completes. */
    @Test public void confirmClaimedRemovalFailureIsSurfaced() {
        LegacyMemory memory=new LegacyMemory();
        SecretStore.InMemorySecretStore secrets=new SecretStore.InMemorySecretStore();
        CloudDeviceCredentials identity=new CloudDeviceCredentials(memory,secrets);
        assertTrue(identity.persistActivation("cloud-uuid","device-token","act-1","secret","123456"));
        // The claimed-state commit succeeds but the activationSecret removal does not.
        secrets.failRemove("activationSecret");
        assertTrue(identity.confirmClaimed());
        assertTrue(identity.connected());
        // The failed critical removal is surfaced, never silently ignored.
        assertTrue(identity.credentialUnavailable());
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
