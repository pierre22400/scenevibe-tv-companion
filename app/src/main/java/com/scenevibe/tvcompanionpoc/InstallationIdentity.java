package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * The stable local installation id of SceneVibe on this TV. It is random, stable,
 * non-secret and app-private; it is independent of the LAN pairing subsystem, and it is
 * strictly distinct from the cloudDeviceId (the server UUID minted by the 201) and from
 * the durable deviceToken (the cloud secret). It is NEVER derived from IP, MAC, or TV
 * model: the class performs no network or device-fingerprint lookups of any kind.
 *
 * <p>The identity lives in its own 'installation' app-private preferences file so a later
 * "Reset Cloud" can wipe cloud state without touching it. On first read the mandated
 * migration order runs: (1) reuse an already stored InstallationIdentity value; (2) else
 * migrate EXACTLY the legacy PairingPolicy deviceId (key 'deviceId' in the 'pairing'
 * prefs) so an existing 0.7.1 install keeps its id; (3) else mint a strong random id in
 * the same Base64url-of-16-random-bytes format as the legacy value; then persist the
 * resulting identity. No stored value is ever logged.
 */
final class InstallationIdentity {
    static final String KEY_INSTALLATION_ID = "installationId";

    /**
     * Minimal persistence boundary mirroring the other app-private stores so the pure
     * migration logic is testable on the JVM without an Android runtime. The production
     * implementation wraps the app-private 'installation' SharedPreferences and reports
     * commit durability; a successful boolean return means the write is durable.
     */
    interface Storage {
        String get(String key);
        void put(String key, String value);
    }

    /**
     * Read-only access to the legacy PairingPolicy deviceId value so migration can reuse
     * the EXACT existing id. Injectable so JVM tests can supply a legacy value without an
     * Android runtime; returns null when no legacy value exists.
     */
    interface LegacyDeviceId {
        String value();
    }

    private final Storage storage;
    private final LegacyDeviceId legacy;
    private final SecureRandom random;

    /** Uses Android private preferences and excludes OS backup through the existing manifest. */
    InstallationIdentity(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences("installation", Context.MODE_PRIVATE);
        this.storage = new Storage() {
            @Override public String get(String key) { return prefs.getString(key, null); }
            @Override public void put(String key, String value) {
                if (!prefs.edit().putString(key, value).commit())
                    throw new IllegalStateException("Installation identity could not be persisted");
            }
        };
        // Read the legacy value directly from the same 'pairing' prefs/key that
        // PairingPolicy.deviceId() uses; do NOT call into PairingRuntime.
        SharedPreferences pairing = app.getSharedPreferences("pairing", Context.MODE_PRIVATE);
        this.legacy = () -> pairing.getString("deviceId", null);
        this.random = new SecureRandom();
    }

    /** Injectable boundaries for deterministic JVM tests. */
    InstallationIdentity(Storage storage, LegacyDeviceId legacy, SecureRandom random) {
        this.storage = storage;
        this.legacy = legacy;
        this.random = random;
    }

    /**
     * Returns the stable local installationId, running the mandated migration order on
     * first read and persisting the result. Subsequent reads return the same stored value.
     */
    synchronized String installationId() {
        String existing = storage.get(KEY_INSTALLATION_ID);
        if (existing != null) return existing;
        String legacyValue = legacy != null ? legacy.value() : null;
        String id = legacyValue != null ? legacyValue : generate();
        storage.put(KEY_INSTALLATION_ID, id);
        return id;
    }

    /**
     * Read-only peek at the installationId for OBSERVATIONAL use (the Diagnostics snapshot).
     * It returns the already stored id, or the migratable legacy value when no id is stored
     * yet, or null when neither exists - but it NEVER mints a new id and NEVER persists
     * anything (it does not call {@code storage.put}). Opening Diagnostics must not mutate any
     * store, so {@link RuntimeDiagnostics#capture} uses this instead of {@link #installationId()}.
     */
    synchronized String peekInstallationId() {
        String existing = storage.get(KEY_INSTALLATION_ID);
        if (existing != null) return existing;
        return legacy != null ? legacy.value() : null;
    }

    /**
     * Destructive recovery primitive used ONLY by the explicit Diagnostics Cloud reset.
     *
     * <p>A normal installation keeps one stable id forever. Reset is different: once the TV
     * user deliberately deletes the Cloud credential, preserving the old installationId would
     * make the server correctly demand proof of the now-deleted device token and the TV could
     * never pair again. Recovery therefore mints and durably stores a fresh random installation
     * id. The previous server-side device remains account-owned until the user removes it from
     * the account; this method never attempts a silent takeover or weakens device proof.
     *
     * @return the newly persisted non-secret installation id
     */
    synchronized String rotateForCloudReset() {
        String id = generate();
        storage.put(KEY_INSTALLATION_ID, id);
        return id;
    }

    /** Strong random id in the same Base64url-of-16-random-bytes format as the legacy value. */
    private String generate() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
