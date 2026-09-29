package com.scenevibe.tvcompanionpoc;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/** JVM tests for the durable credential lifecycle and the three separated identities. */
public final class CloudDeviceCredentialsTest {
    /**
     * In-memory store that faithfully reproduces the SharedPreferences batches: a valid
     * 201 persists the durable credential together with the activation temporaries, claim
     * keeps the durable credential while dropping temporaries, and expiry/disconnect drop
     * only the temporaries. The two SECRETS (deviceToken/activationSecret) never live here;
     * they are routed through the injected {@link SecretStore}. This store holds only the
     * non-secret values, matching the production Storage seam.
     */
    private static final class Memory implements CloudDeviceCredentials.Storage {
        final Map<String,String> values=new HashMap<>();
        final Map<String,Boolean> flags=new HashMap<>();
        @Override public String getString(String key){return values.get(key);}
        @Override public boolean getFlag(String key){return flags.getOrDefault(key,false);}
        @Override public void putFlag(String key,boolean value){flags.put(key,value);}
        @Override public boolean persistActivation(String cloudDeviceId,String deviceToken,
                String activationId,String activationSecret,String userCode) {
            // Secrets are erased here (production removes any legacy plaintext); only
            // non-secret fields persist. deviceToken/activationSecret args are the legacy keys.
            values.put("cloudDeviceId",cloudDeviceId);values.remove("deviceToken");
            values.put("activationId",activationId);values.remove("activationSecret");
            values.put("userCode",userCode);return true;
        }
        @Override public boolean confirmClaimed() {
            values.remove("activationId");values.remove("userCode");
            flags.put("connected",true);return true;
        }
        @Override public void clearActivationTemporaries() {
            values.remove("activationId");values.remove("userCode");
        }
        @Override public void disconnect() {
            values.remove("activationId");values.remove("userCode");
            flags.put("connected",false);
        }
        @Override public void reset() {values.clear();flags.clear();}
        @Override public void removeLegacyPlaintext(String key) {values.remove(key);}
    }

    private static CloudDeviceCredentials credentials(Memory memory) {
        return new CloudDeviceCredentials(memory,new SecretStore.InMemorySecretStore());
    }

    /** A valid 201 durably stores deviceToken + cloudDeviceId BEFORE any claim. */
    @Test public void activationPersistsDurableCredentialBeforeClaim() {
        Memory memory=new Memory();CloudDeviceCredentials identity=credentials(memory);
        assertTrue(identity.persistActivation("cloud-uuid","device-token","act-1","secret","123456"));
        assertEquals("device-token",identity.deviceToken());
        assertEquals("cloud-uuid",identity.cloudDeviceId());
        assertEquals("act-1",identity.activationId());
        assertEquals("secret",identity.activationSecret());
        assertEquals("123456",identity.userCode());
        assertFalse(identity.connected());
    }

    /** Claim confirms connected and clears temporaries without replacing the durable credential. */
    @Test public void claimConnectsWithoutReplacingDurableCredential() {
        Memory memory=new Memory();CloudDeviceCredentials identity=credentials(memory);
        identity.persistActivation("cloud-uuid","device-token","act-1","secret","123456");
        assertTrue(identity.confirmClaimed());
        assertTrue(identity.connected());
        assertEquals("device-token",identity.deviceToken());
        assertEquals("cloud-uuid",identity.cloudDeviceId());
        assertNull(identity.activationId());
        assertNull(identity.activationSecret());
        assertNull(identity.userCode());
    }

    /** Expiry clears only activation temporaries and keeps deviceToken + cloudDeviceId. */
    @Test public void expiryKeepsDurableCredentialAndClearsTemporaries() {
        Memory memory=new Memory();CloudDeviceCredentials identity=credentials(memory);
        identity.persistActivation("cloud-uuid","device-token","act-1","secret","123456");
        identity.clearExpiredActivation();
        assertEquals("device-token",identity.deviceToken());
        assertEquals("cloud-uuid",identity.cloudDeviceId());
        assertNull(identity.activationId());
        assertNull(identity.activationSecret());
        assertNull(identity.userCode());
        assertFalse(identity.connected());
    }

    /** Disconnect keeps the durable credential for reconnection but clears connected + temporaries. */
    @Test public void disconnectKeepsDurableCredentialForReconnection() {
        Memory memory=new Memory();CloudDeviceCredentials identity=credentials(memory);
        identity.persistActivation("cloud-uuid","device-token","act-1","secret","123456");
        identity.confirmClaimed();
        identity.disconnect();
        assertFalse(identity.connected());
        assertEquals("device-token",identity.deviceToken());
        assertEquals("cloud-uuid",identity.cloudDeviceId());
        assertNull(identity.activationId());
    }

    /**
     * The local installationId (PairingPolicy.deviceId) and the cloud deviceId are stored
     * separately: the credentials store never carries the installationId, and its
     * cloudDeviceId is the UUID minted by the 201.
     */
    @Test public void installationIdAndCloudDeviceIdAreStoredSeparately() {
        Memory memory=new Memory();CloudDeviceCredentials identity=credentials(memory);
        identity.persistActivation("cloud-uuid","device-token","act-1","secret","123456");
        assertEquals("cloud-uuid",identity.cloudDeviceId());
        // The credentials store holds cloud identity only; the local installationId lives in
        // the separate 'pairing' prefs via PairingPolicy and never leaks into this store.
        assertNull(memory.values.get("installationId"));
    }
}
