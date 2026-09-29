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
        /**
         * EXCEPTIONAL reset of the cloud identity: removes cloudDeviceId, activationId,
         * userCode and every state flag (connected/offline) so no cloud identity remains
         * here. It NEVER touches the separate 'installation' identity store. The two
         * secrets are wiped through the SecretStore by the caller.
         */
        void reset();
        /** Deletes a single legacy plaintext secret key AFTER its ciphertext is verified. */
        void removeLegacyPlaintext(String key);
    }
    private final Storage storage;
    private final SecretStore secrets;
    private volatile boolean credentialUnavailable;
    /** Uses Android private preferences and excludes OS backup through the existing manifest. */
    CloudDeviceCredentials(Context context) {
        this(context,true);
    }
    /**
     * READ-ONLY peek at the cloud identity for OBSERVATIONAL use (the Diagnostics snapshot).
     * It binds the SAME app-private stores but does NOT run the legacy plaintext migration on
     * construction, so opening Diagnostics never mutates the cloud_identity file or the
     * SecretStore. Reads still fail closed to {@link #credentialUnavailable()} on a
     * keystore/cipher problem, but nothing is encrypted, deleted or committed here.
     */
    static CloudDeviceCredentials peek(Context context) {
        return new CloudDeviceCredentials(context,false);
    }
    private CloudDeviceCredentials(Context context,boolean migrate) {
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
            @Override public void reset() {
                // Wipe the whole cloud_identity file: cloudDeviceId, activationId, userCode,
                // connected/offline flags and any residual legacy plaintext secret keys. The
                // 'installation' identity store is a DIFFERENT file and is never touched here.
                prefs.edit().clear().commit();
            }
            @Override public void removeLegacyPlaintext(String key) {
                prefs.edit().remove(key).commit();
            }
        };
        this.secrets=new SecretStore.AndroidKeyStoreSecretStore(context);
        if(migrate) migrateLegacyPlaintext();
    }
    /** Injectable persistence boundary for deterministic JVM tests (runs the migration). */
    CloudDeviceCredentials(Storage storage,SecretStore secrets) {
        this(storage,secrets,true);
    }
    /**
     * Injectable persistence boundary for deterministic JVM tests, with an explicit choice of
     * whether construction runs the legacy plaintext migration. A read-only peek passes false
     * so a snapshot never mutates a store.
     */
    CloudDeviceCredentials(Storage storage,SecretStore secrets,boolean migrate) {
        this.storage=storage;this.secrets=secrets;
        if(migrate) migrateLegacyPlaintext();
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
            if(!secrets.put(name,plaintext)) {
                // Encrypted write is not durable. Keep the historical plaintext for a later
                // retry but fail closed: the plaintext is NEVER read back as an operational
                // credential, so mark the store unavailable rather than pretending success.
                credentialUnavailable=true;return;
            }
            // Verify the ciphertext decrypts back to the exact original BEFORE deleting.
            if(!plaintext.equals(secrets.get(name))) {
                // Read-back mismatch: the write did not durably round-trip. Same fail-closed
                // treatment as a non-durable put: keep plaintext, never operationalize it.
                credentialUnavailable=true;return;
            }
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
        // Snapshot the FULL previous secret tuple so any failure can restore it verbatim.
        // This is the transaction's rollback log: it lets persistActivation be all-or-nothing
        // even though the SecretStore commits one record at a time. Reading the previous
        // values fails closed on corruption rather than proceeding on a mixed state.
        final String previousDeviceToken;
        final String previousActivationSecret;
        try {
            previousDeviceToken=secrets.get(SECRET_DEVICE_TOKEN);
            previousActivationSecret=secrets.get(SECRET_ACTIVATION_SECRET);
        }catch(SecretStore.Unavailable unavailable) {
            credentialUnavailable=true;return false;
        }
        boolean deviceTokenWritten=false;
        boolean activationSecretWritten=false;
        try {
            // Stage 1: encrypt-and-verify the durable deviceToken.
            if(!secrets.put(SECRET_DEVICE_TOKEN,deviceToken)
                    ||!deviceToken.equals(secrets.get(SECRET_DEVICE_TOKEN))) {
                rollbackSecrets(previousDeviceToken,previousActivationSecret,false,false);
                return false;
            }
            deviceTokenWritten=true;
            // Stage 2: encrypt-and-verify the temporary activationSecret.
            if(!secrets.put(SECRET_ACTIVATION_SECRET,activationSecret)
                    ||!activationSecret.equals(secrets.get(SECRET_ACTIVATION_SECRET))) {
                rollbackSecrets(previousDeviceToken,previousActivationSecret,true,false);
                return false;
            }
            activationSecretWritten=true;
            // Stage 3: durably commit the non-secret batch. The plaintext secret parameters
            // below only erase any legacy plaintext keys. If this commit is not durable the
            // secrets must NOT be left updated in isolation, so roll them both back.
            if(!storage.persistActivation(cloudDeviceId,null,activationId,null,userCode)) {
                rollbackSecrets(previousDeviceToken,previousActivationSecret,true,true);
                return false;
            }
            return true;
        }catch(SecretStore.Unavailable unavailable) {
            // Any keystore/cipher problem mid-transaction: restore the previous tuple and fail closed.
            rollbackSecrets(previousDeviceToken,previousActivationSecret,
                    deviceTokenWritten,activationSecretWritten);
            credentialUnavailable=true;
            return false;
        }
    }
    /**
     * Restores the previous secret tuple after a failed persistActivation stage so the whole
     * previous state stays usable and no old/new mix survives. For each secret that was just
     * written it re-writes the previous value (or removes the record when there was none),
     * best-effort: a rollback that itself cannot commit is surfaced via credentialUnavailable
     * so the mixed state is never reported as a healthy credential.
     */
    private void rollbackSecrets(String previousDeviceToken,String previousActivationSecret,
            boolean deviceTokenWritten,boolean activationSecretWritten) {
        try {
            if(activationSecretWritten) restoreSecret(SECRET_ACTIVATION_SECRET,previousActivationSecret);
            if(deviceTokenWritten) restoreSecret(SECRET_DEVICE_TOKEN,previousDeviceToken);
        }catch(SecretStore.Unavailable unavailable) {
            credentialUnavailable=true;
        }
    }
    private void restoreSecret(String name,String previousValue) {
        if(previousValue==null) {
            if(!secrets.remove(name)) credentialUnavailable=true;
        }else if(!secrets.put(name,previousValue)) {
            credentialUnavailable=true;
        }
    }
    /**
     * Marks the activation claimed: keeps deviceToken+cloudDeviceId, drops the temporaries.
     * The claimed-state commit runs FIRST; the activationSecret is removed ONLY after that
     * commit is durable, so a failed commit leaves the activationSecret intact and the whole
     * operation retryable (the next poll calls confirmClaimed again). A false removal of the
     * critical secret is surfaced via credentialUnavailable rather than silently ignored.
     */
    boolean confirmClaimed() {
        if(!storage.confirmClaimed()) return false; // Retryable: activationSecret still present.
        if(!secrets.remove(SECRET_ACTIVATION_SECRET)) credentialUnavailable=true;
        return true;
    }
    /** Drops ONLY the expired activation temporaries; the durable device credential survives. */
    void clearExpiredActivation() {
        if(!secrets.remove(SECRET_ACTIVATION_SECRET)) credentialUnavailable=true;
        storage.clearActivationTemporaries();
    }
    /**
     * Stops cloud polling locally. Clears activation temporaries and connected flag but KEEPS
     * cloudDeviceId+deviceToken so reconnection can reuse the durable credential; it never
     * touches the FinalTrack cache.
     */
    void disconnect() {
        if(!secrets.remove(SECRET_ACTIVATION_SECRET)) credentialUnavailable=true;
        storage.disconnect();
    }
    /**
     * EXCEPTIONAL "Reset SceneVibe Cloud connection" (user section 14), available ONLY from
     * Diagnostics and never triggered automatically by any network/timeout/401/error path.
     * It deletes BOTH encrypted secrets (deviceToken + activationSecret) from the SecretStore
     * and wipes the non-secret cloud_identity store (cloudDeviceId, activationId, userCode,
     * connected/offline). It DELIBERATELY leaves the separate 'installation' identity store
     * untouched, so the stable local installationId survives a cloud reset. The FinalTrack
     * cache is cleared by the caller (CloudTrackRepository.clear()), not here. After a reset
     * the cloud state reads as disconnected with no credential. A previously observed
     * credential-unavailable flag is cleared because there is no longer any secret to read.
     */
    void reset() {
        // Removal durability is verified: reset must end with NO secret ciphertext present.
        // If either critical removal is not durable we surface it via credentialUnavailable
        // rather than pretending the reset succeeded. Both removals are attempted so a single
        // stuck record does not skip wiping the other secret.
        boolean deviceTokenRemoved=secrets.remove(SECRET_DEVICE_TOKEN);
        boolean activationSecretRemoved=secrets.remove(SECRET_ACTIVATION_SECRET);
        storage.reset();
        // Only clear the fail-closed flag when both secrets are durably gone; a false commit
        // means ciphertext may remain, which must stay observable and never look healthy.
        credentialUnavailable=!(deviceTokenRemoved&&activationSecretRemoved);
    }
}
