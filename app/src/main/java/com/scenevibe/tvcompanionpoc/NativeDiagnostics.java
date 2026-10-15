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
        long nowMs = android.os.SystemClock.elapsedRealtime();
        evaluated(event, generation, sample, reason, nowMs,
                NativeQualificationPolicy.local035().evidence(sample, nowMs));
    }

    /** Log the owner's actual guard result/time rather than recomputing it after logging delay. */
    @Override public void evaluated(String event, long generation, NativeSessionSnapshot sample,
            NativeTransportOwner.Reason reason, long evaluatedAtMs,
            NativeQualificationPolicy.Evidence evidence) {
        String current = phase.get();
        lastSummary = event + " / " + current + " / " + reason.name();
        try {
            JSONObject json = describe(event, generation, sample, reason, evaluatedAtMs);
            if (sample != null) json.put("guard", evidence.name());
            Log.i("SceneVibeE5", json.toString());
        } catch (Exception unavailable) {
            Log.w("SceneVibeE5", "DIAGNOSTIC_ENCODING_FAILED");
        }
    }

    /** Encode inspectable, content-free evidence with an explicit monotone clock for tests. */
    JSONObject describe(String event, long generation, NativeSessionSnapshot sample,
            NativeTransportOwner.Reason reason, long nowMs) throws Exception {
        String current = phase.get();
        JSONObject json = new JSONObject();
        json.put("profile", "E5-NATIVE-035");
        json.put("event", event);
        json.put("phase", current);
        json.put("generation", generation);
        json.put("attemptId", salt.substring(0, 8) + "-" + generation);
        json.put("executionAtMs", nowMs);
        json.put("clockBasis", "elapsedRealtimeMs");
        json.put("terminal", current.equals("SUCCESS") || current.equals("REFUSED")
                || current.equals("RECOVERY_REQUIRED"));
        json.put("guard", sample == null ? "NO_OBSERVATION"
                : NativeQualificationPolicy.local035().evidence(sample, nowMs).name());
        json.put("reason", reason.name());
        if (sample != null) {
            json.put("session", sample.token == null ? "unavailable" : fingerprint(Integer.toString(sample.token.hashCode())));
            json.put("identity", fingerprint(sample.mediaId + "|" + sample.title + "|"
                    + sample.subtitle + "|" + sample.durationMs));
            json.put("state", sample.state);
            json.put("positionMs", sample.positionMs);
            json.put("estimatedPositionMs", sample.estimatedPosition(nowMs));
            json.put("durationMs", sample.durationMs);
            json.put("durationKnown", sample.durationMs > 0);
            json.put("speed", Float.toString(sample.speed));
            json.put("observedAtMs", sample.observedAtMs);
            json.put("updatedAtMs", sample.updatedAtMs);
            json.put("sampleAgeMs", nowMs - sample.observedAtMs);
            json.put("providerAgeMs", nowMs - sample.updatedAtMs);
        }
        return json;
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
