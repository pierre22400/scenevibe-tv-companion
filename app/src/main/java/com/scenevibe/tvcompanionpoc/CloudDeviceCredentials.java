package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * App-private cloud identity. Keeps three distinct values: the local installationId
 * (sourced from PairingPolicy.deviceId(), used ONLY as installationId at activation
 * start), the cloudDeviceId (UUID minted by the 201 response, used in assignment/ack
 * URLs), and the durable cloud deviceToken (the credential returned by the 201). It is
 * never the LAN pairing token or a controller token. No stored value is ever logged.
 *
 * <p>The two SECRETS - the durable deviceToken and the temporary activationSecret - are
 * routed through an injectable {@link SecretStore} so they are encrypted at rest with a
 * key that never leaves the platform keystore. The non-secret values (cloudDeviceId,
 * activationId, userCode, flags, revisions) stay in an app-private SharedPreferences file
 * behind the {@link Storage} seam. On first read a fail-safe migration promotes any
 * legacy 0.7.1 plaintext secret into the SecretStore (encrypt, persist, verify, and only
 * THEN delete the plaintext); on any keystore/cipher problem the store fails closed and
 * reports {@link #credentialUnavailable()} instead of falling back to plaintext.
 */
final class CloudDeviceCredentials {
    /** SecretStore record names for the two encrypted secrets. */
    private static final String SECRET_DEVICE_TOKEN = "deviceToken";
    private static final String SECRET_ACTIVATION_SECRET = "activationSecret";
    /**
     * Minimal persistence boundary mirroring CloudTrackRepository.Storage so the pure
     * lifecycle transitions are testable on the JVM without an Android runtime. The
     * production implementation wraps app-private SharedPreferences and reports commit
     * durability; a successful boolean return means the batch is durable. This seam holds
     * ONLY non-secret values (cloudDeviceId/activationId/userCode/flags); the two secrets
     * live in the {@link SecretStore}. Legacy 0.7.1 installs may still have plaintext
     * secrets here under the same keys, which the migration drains and deletes.
     */
    interface Storage {
        String getString(String key);
        boolean getFlag(String key);
        void putFlag(String key,boolean value);
        /**
         * Persists the non-secret activation fields atomically and reports durability. The
         * deviceToken/activationSecret parameters are the LEGACY plaintext keys: production
         * writes null to erase any 0.7.1 plaintext while the tests may keep them in memory.
         */
        boolean persistActivation(String cloudDeviceId,String deviceToken,
                String activationId,String activationSecret,String userCode);
        boolean confirmClaimed();
        void clearActivationTemporaries();
        void disconnect();
        /** Deletes a single legacy plaintext secret key AFTER its ciphertext is verified. */
        void removeLegacyPlaintext(String key);
    }
    private final Storage storage;
    private final SecretStore secrets;
    private volatile boolean credentialUnavailable;
    /** Uses Android private preferences and excludes OS backup through the existing manifest. */
    CloudDeviceCredentials(Context context) {
        SharedPreferences prefs=context.getApplicationContext()
                .getSharedPreferences("cloud_identity",Context.MODE_PRIVATE);
        storage=new Storage() {
            @Override public String getString(String key) {return prefs.getString(key,null);}
            @Override public boolean getFlag(String key) {return prefs.getBoolean(key,false);}
            @Override public void putFlag(String key,boolean value) {prefs.edit().putBoolean(key,value).apply();}
            @Override public boolean persistActivation(String cloudDeviceId,String deviceToken,
                    String activationId,String activationSecret,String userCode) {
                // Secrets never touch these prefs; remove any legacy plaintext keys in the batch.
                return prefs.edit()
                        .putString("cloudDeviceId",cloudDeviceId)
                        .remove("deviceToken")
                        .putString("activationId",activationId)
                        .remove("activationSecret")
                        .putString("userCode",userCode)
                        .commit();
            }
            @Override public boolean confirmClaimed() {
                return prefs.edit().remove("activationId").remove("userCode")
                        .putBoolean("connected",true).commit();
            }
            @Override public void clearActivationTemporaries() {
                prefs.edit().remove("activationId").remove("userCode").commit();
            }
            @Override public void disconnect() {
                prefs.edit().remove("activationId").remove("userCode")
                        .putBoolean("connected",false).commit();
            }
            @Override public void removeLegacyPlaintext(String key) {
                prefs.edit().remove(key).commit();
            }
        };
        this.secrets=new SecretStore.AndroidKeyStoreSecretStore(context);
        migrateLegacyPlaintext();
    }
    /** Injectable persistence boundary for deterministic JVM tests. */
    CloudDeviceCredentials(Storage storage,SecretStore secrets) {
        this.storage=storage;this.secrets=secrets;
        migrateLegacyPlaintext();
    }
    /**
     * Fail-safe migration of a 0.7.1 install (user section 13). For each secret still held
     * as plaintext in Storage: encrypt it via the SecretStore, persist ciphertext+IV,
     * VERIFY the write by reading it back, and ONLY THEN delete the plaintext. Never
     * delete-first. On any keystore/cipher problem the plaintext is kept intact and the
     * store fails closed via {@link #credentialUnavailable()} - never plaintext fallback,
     * never a new identity. Idempotent: does nothing once the secret is encrypted.
     */
    private void migrateLegacyPlaintext() {
        migrateSecret(SECRET_DEVICE_TOKEN);
        migrateSecret(SECRET_ACTIVATION_SECRET);
    }
    private void migrateSecret(String name) {
        String plaintext=storage.getString(name);
        if(plaintext==null)return; // No legacy plaintext to migrate.
        try {
            if(!secrets.put(name,plaintext))return; // Write not durable: keep plaintext, retry later.
            // Verify the ciphertext decrypts back to the exact original BEFORE deleting.
            if(!plaintext.equals(secrets.get(name)))return; // Verification failed: keep plaintext.
            storage.removeLegacyPlaintext(name); // Only now is the plaintext safe to remove.
        }catch(SecretStore.Unavailable unavailable) {
            // Fail closed: keep the plaintext, do not fall back, mark unavailable.
            credentialUnavailable=true;
        }
    }
    /** Reads an encrypted secret, failing closed to null + credentialUnavailable on corruption. */
    private String readSecret(String name) {
        try {
            return secrets.get(name);
        }catch(SecretStore.Unavailable unavailable) {
            credentialUnavailable=true;return null;
        }
    }
    /** The durable cloud device credential returned by the 201; independent of LAN v0.6. */
    String deviceToken() {return readSecret(SECRET_DEVICE_TOKEN);}
    /** The cloud-minted device UUID used in assignment and ACK URLs. */
    String cloudDeviceId() {return storage.getString("cloudDeviceId");}
    /** Returns the temporary activation secret only for TV-side status polling. */
    String activationSecret() {return readSecret(SECRET_ACTIVATION_SECRET);}
    /** Returns the current temporary activation ID. */
    String activationId() {return storage.getString("activationId");}
    /** Displays only the temporary human pairing code on TV, never the credential. */
    String userCode() {return storage.getString("userCode");}
    /** Exposes state without private values. */
    boolean connected() {return storage.getFlag("connected");}
    /** Last transport outcome is display-only and never gates cached scheduling. */
    boolean offline() {return storage.getFlag("offline");}
    /**
     * True once a keystore/cipher failure prevented a secret read/write. This is the sole
     * diagnostics-safe observable for the fail-closed state; it maps to the
     * CREDENTIAL_UNAVAILABLE diagnostic code and NEVER carries a secret value. It never
     * triggers plaintext fallback, minting a new TV, or auto-erasing the identity.
     */
    boolean credentialUnavailable() {return credentialUnavailable;}
    /** Updates UI observability without storing any server payload. */
    void setOffline(boolean value) {storage.putFlag("offline",value);}
    /**
     * On any valid 201 this atomically persists the durable cloudDeviceId + deviceToken
     * immediately (before any claim) and records activationId/activationSecret/userCode as
     * temporary activation state. The two secrets are encrypted through the SecretStore and
     * verified before the non-secret batch is committed; a successful return means every
     * field is durable and no plaintext secret remains.
     */
    boolean persistActivation(String cloudDeviceId,String deviceToken,
            String activationId,String activationSecret,String userCode) {
        try {
            // Encrypt-and-verify both secrets first; abort without partial state on failure.
            if(!secrets.put(SECRET_DEVICE_TOKEN,deviceToken)||!deviceToken.equals(secrets.get(SECRET_DEVICE_TOKEN)))
                return false;
            if(!secrets.put(SECRET_ACTIVATION_SECRET,activationSecret)
                    ||!activationSecret.equals(secrets.get(SECRET_ACTIVATION_SECRET)))
                return false;
        }catch(SecretStore.Unavailable unavailable) {
            credentialUnavailable=true;return false;
        }
        // The plaintext secret parameters below only erase any legacy plaintext keys.
        return storage.persistActivation(cloudDeviceId,null,activationId,null,userCode);
    }
    /** Marks the activation claimed: keeps deviceToken+cloudDeviceId, drops the temporaries. */
    boolean confirmClaimed() {secrets.remove(SECRET_ACTIVATION_SECRET);return storage.confirmClaimed();}
    /** Drops ONLY the expired activation temporaries; the durable device credential survives. */
    void clearExpiredActivation() {secrets.remove(SECRET_ACTIVATION_SECRET);storage.clearActivationTemporaries();}
    /**
     * Stops cloud polling locally. Clears activation temporaries and connected flag but KEEPS
     * cloudDeviceId+deviceToken so reconnection can reuse the durable credential; it never
     * touches the FinalTrack cache.
     */
    void disconnect() {secrets.remove(SECRET_ACTIVATION_SECRET);storage.disconnect();}
}
