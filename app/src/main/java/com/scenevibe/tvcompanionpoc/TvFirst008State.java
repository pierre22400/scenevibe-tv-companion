package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.json.JSONObject;

/**
 * 008 identity is an explicit encrypted handshake, independent of legacy credentials.
 * Ambiguous network retries keep the same persisted identifiers. No constructor migrates,
 * clears or regenerates an existing identity. Failed durability never grants authority.
 */
final class TvFirst008State {
    private final SharedPreferences prefs;
    private final SecretStore secrets;
    private boolean failed;
    private static final java.util.Map<SharedPreferences, Boolean> FAILED_VIEWS = new java.util.WeakHashMap<>();
    /** Bind dedicated metadata and the existing encrypted secret boundary without reading secrets. */
    TvFirst008State(Context context) {
        this(context.getSharedPreferences("tvfirst008_identity", Context.MODE_PRIVATE), new SecretStore.AndroidKeyStoreSecretStore(context));
    }
    /** Inject persistence boundaries for failure and restart qualification only. */
    TvFirst008State(SharedPreferences prefs, SecretStore secrets) { this.prefs = prefs; this.secrets = secrets; }
    /** Read one metadata record; absent is different from corrupt or unavailable. */
    synchronized JSONObject read(String key) {
        if (failed || blocked()) throw new SecretStore.Unavailable("LOCAL_DURABILITY_UNAVAILABLE");
        String raw = prefs.getString(key, null);
        try { return raw == null ? null : new JSONObject(raw); }
        catch (Exception invalid) { throw new SecretStore.Unavailable("LOCAL_METADATA_UNAVAILABLE"); }
    }
    /** Publish atomically and verify the readback; false commit masks this process's mutable view. */
    synchronized void write(String key, JSONObject value) {
        String raw = value == null ? null : value.toString();
        boolean saved = raw == null ? prefs.edit().remove(key).commit() : prefs.edit().putString(key, raw).commit();
        if (!saved || !java.util.Objects.equals(raw, prefs.getString(key, null))) {
            failed = true; synchronized (FAILED_VIEWS) { FAILED_VIEWS.put(prefs, true); } throw new SecretStore.Unavailable("LOCAL_DURABILITY_UNAVAILABLE");
        }
    }
    /** Share failed-commit refusal across recreated sources in this same Android process. */
    private boolean blocked() { synchronized (FAILED_VIEWS) { return FAILED_VIEWS.containsKey(prefs); } }
    /** Generate only after an explicit pairing choice; preserve an existing pending challenge. */
    synchronized JSONObject challenge(String mode) {
        JSONObject old = read("challenge");
        if (old != null) {
            if (!mode.equals(old.optString("mode"))) throw new IllegalStateException("PENDING_CHALLENGE");
            challengeSecret(old); return old;
        }
        try {
            String id = UUID.randomUUID().toString();
            byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
            String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            if (!secrets.put("tvfirst008.challenge." + id, secret) || !secret.equals(secrets.get("tvfirst008.challenge." + id)))
                throw new SecretStore.Unavailable("CHALLENGE_NOT_DURABLE");
            JSONObject record = new JSONObject().put("challengeId", id).put("mode", mode)
                    .put("installationId", "tv008_" + UUID.randomUUID().toString().replace("-", ""));
            write("challenge", record); return record;
        } catch (org.json.JSONException invalid) { throw TvFirst008Wire.invalid(); }
    }
    /** Return a secret only to the authenticated transport, never the presentation model. */
    synchronized String challengeSecret(JSONObject challenge) { return requiredSecret("tvfirst008.challenge." + TvFirst008Wire.uuid(challenge.optString("challengeId"))); }
    /** Store a claimed credential in a generation-specific slot before publishing the identity. */
    synchronized void claimed(JSONObject response) {
        String device = TvFirst008Wire.uuid(response.optString("deviceId"));
        String generation = TvFirst008Wire.uuid(response.optString("generation"));
        String token = response.optString("deviceToken");
        if (!TvFirst008Wire.PROFILE.equals(response.optString("profile")) || !token.matches("[A-Za-z0-9_-]{43,86}")) throw TvFirst008Wire.invalid();
        try {
            if (!secrets.put("tvfirst008.token." + generation, token) || !token.equals(secrets.get("tvfirst008.token." + generation)))
                throw new SecretStore.Unavailable("CREDENTIAL_NOT_DURABLE");
            write("identity", new JSONObject().put("deviceId", device).put("generation", generation));
            write("challenge", null);
            write("pending", null); write("binding", null); write("receipt", null);
        } catch (org.json.JSONException invalid) { throw TvFirst008Wire.invalid(); }
    }
    /** Read only 008's current encrypted credential; never consult a legacy plaintext fallback. */
    synchronized String token(JSONObject identity) { return requiredSecret("tvfirst008.token." + TvFirst008Wire.uuid(identity.optString("generation"))); }
    /** Refuse missing/corrupt ciphertext while retaining all identity and cache metadata. */
    private String requiredSecret(String key) {
        String value = secrets.get(key);
        if (value == null || !value.matches("[A-Za-z0-9_-]{43,86}")) throw new SecretStore.Unavailable("CREDENTIAL_UNAVAILABLE");
        return value;
    }
    /** Persist one retry-safe intention before issuing its HTTP request. */
    synchronized JSONObject pending(String track, long revision, String generation) {
        JSONObject old = read("pending");
        if (old != null) {
            if (!track.equals(old.optString("finalTrackId")) || !generation.equals(old.optString("generation")))
                throw new IllegalStateException("PENDING_INTENTION");
            return old;
        }
        try {
            JSONObject command = new JSONObject().put("profile", TvFirst008Wire.PROFILE).put("intentionId", UUID.randomUUID().toString())
                    .put("generation", generation).put("finalTrackId", track).put("confirmed", true).put("expectedRevision", revision);
            write("pending", command); return command;
        } catch (org.json.JSONException invalid) { throw TvFirst008Wire.invalid(); }
    }
}
