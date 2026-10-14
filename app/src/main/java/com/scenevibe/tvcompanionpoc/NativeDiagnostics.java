package com.scenevibe.tvcompanionpoc;

import android.util.Log;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Bounded E5 command/observation diagnostics, separate from delivery ACK and presentation state.
 * The last summary is observational only. Tokens, authored text, titles and reusable credentials
 * are never logged; session/identity fingerprints are salted per service lifetime.
 */
final class NativeDiagnostics implements NativeTransportOwner.Diagnostics {
    private static volatile String lastSummary = "E5 local qualification: not requested";
    private final String salt = UUID.randomUUID().toString();
    private final Supplier<String> phase;

    /** Bind the regie's authoritative phase for truthful command/confirmation logs. */
    NativeDiagnostics(Supplier<String> phase) { this.phase = phase; }

    /** Log one bounded event without persisting an identity or granting transport ownership. */
    @Override public void record(String event, long generation, NativeSessionSnapshot sample,
            NativeTransportOwner.Reason reason) {
        String current = phase.get();
        lastSummary = event + " / " + current + " / " + reason.name();
        try {
            JSONObject json = new JSONObject();
            json.put("profile", "E5-NATIVE-035");
            json.put("event", event);
            json.put("phase", current);
            json.put("generation", generation);
            json.put("executionAtMs", android.os.SystemClock.elapsedRealtime());
            json.put("reason", reason.name());
            if (sample != null) {
                json.put("session", fingerprint(Integer.toString(sample.token.hashCode())));
                json.put("identity", fingerprint(sample.mediaId + "|" + sample.title + "|"
                        + sample.subtitle + "|" + sample.durationMs));
                json.put("state", sample.state);
                json.put("positionMs", sample.positionMs);
                json.put("observedAtMs", sample.observedAtMs);
                json.put("providerAgeMs", sample.observedAtMs - sample.updatedAtMs);
            }
            Log.i("SceneVibeE5", json.toString());
        } catch (Exception unavailable) {
            Log.w("SceneVibeE5", "DIAGNOSTIC_ENCODING_FAILED");
        }
    }

    /** Read only a bounded latest code for the explicit Diagnostics screen. */
    static String lastSummary() { return lastSummary; }

    /** Pseudonymize an in-memory observation with an ephemeral salt, never a token string. */
    private String fingerprint(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                (salt + "|" + value).getBytes(StandardCharsets.UTF_8));
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 8; i++) text.append(String.format(java.util.Locale.ROOT, "%02x", digest[i] & 255));
        return text.toString();
    }
}
