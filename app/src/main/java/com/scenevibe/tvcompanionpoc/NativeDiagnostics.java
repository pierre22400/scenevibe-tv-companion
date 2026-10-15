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
    private volatile SelectionObservation selection;

    /** Atomically pair one attempt generation with a private primitive-only trace copy. */
    private static final class SelectionObservation {
        final long generation;
        final NativeSelectionTrace trace;

        /** Diagnostics retain no native object and never supply admission data back to the owner. */
        SelectionObservation(long generation, NativeSelectionTrace trace) {
            this.generation = generation;
            this.trace = trace.copy();
        }
    }

    /** Bind the regie's authoritative phase for truthful command/confirmation logs. */
    NativeDiagnostics(Supplier<String> phase) { this.phase = phase; }

    /** Accept memory-only selection evidence; the existing owner/terminal event emits it later. */
    void selection(long generation, NativeSelectionTrace trace) {
        selection = new SelectionObservation(generation, trace);
    }

    /** Log one bounded event without persisting an identity or granting transport ownership. */
    @Override public void record(String event, long generation, NativeSessionSnapshot sample,
            NativeTransportOwner.Reason reason) {
        if ("REQUEST".equals(event)) selection = null;
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
        SelectionObservation observed = selection;
        if (observed != null && observed.generation == generation) {
            json.put("selection", describeSelection(observed.trace));
        }
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

    /** Encode only stable codes, counts and public numeric read fields, never native identities. */
    private static JSONObject describeSelection(NativeSelectionTrace trace) throws Exception {
        JSONObject json = new JSONObject();
        json.put("code", trace.code.name());
        json.put("operation", trace.operation.name());
        json.put("query", trace.query);
        json.put("selectedAtMs", trace.selectedAtMs);
        json.put("finishedAtMs", trace.finishedAtMs);
        json.put("stage", trace.stage.name());
        json.put("detailsComplete", trace.detailsComplete);
        json.put("foreground", trace.foreground.name());
        json.put("foregroundWindowMs", trace.foregroundWindowMs);
        json.put("eventsExamined", trace.eventsExamined);
        json.put("relevantEvents", trace.relevantEvents);
        json.put("resumedApps", trace.resumedApps);
        json.put("lastRelevantEventAgeMs", trace.lastRelevantEventAgeMs);
        json.put("sessionCount", trace.sessionCount);
        json.put("visitedSessions", trace.visitedSessions);
        json.put("ignoredSessions", trace.ignoredSessions);
        json.put("primeCandidates", trace.primeCandidates);
        json.put("competingSessions", trace.competingSessions);
        json.put("readControllerIndex", trace.readControllerIndex);
        json.put("readWasPrime", trace.readWasPrime);
        json.put("read", trace.read.name());
        json.put("metadataDifference", trace.metadataDifference.name());
        json.put("readStep", trace.readStep.name());
        json.put("firstMetadataPresent", trace.firstMetadataPresent);
        json.put("secondMetadataPresent", trace.secondMetadataPresent);
        json.put("firstStatePresent", trace.firstStatePresent);
        json.put("secondStatePresent", trace.secondStatePresent);
        json.put("firstState", trace.firstState);
        json.put("secondState", trace.secondState);
        json.put("firstPositionMs", trace.firstPositionMs);
        json.put("secondPositionMs", trace.secondPositionMs);
        json.put("firstUpdatedAtMs", trace.firstUpdatedAtMs);
        json.put("secondUpdatedAtMs", trace.secondUpdatedAtMs);
        json.put("firstSpeed", Float.toString(trace.firstSpeed));
        json.put("secondSpeed", Float.toString(trace.secondSpeed));
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
