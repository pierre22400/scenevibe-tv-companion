package com.scenevibe.tvcompanionpoc.tvfirst;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Explain incomplete observations without issuing an admission. Even a completely positive
 * synthetic batch grants no rendering permission. The 2500 ms display freshness budget is
 * a diagnostic policy only, not a Sony-qualified extrapolation or comment clock.
 */
public final class MediaEvidencePolicy {
    public static final long DISPLAY_FRESHNESS_MS = 2500L;
    public enum Reason { SOURCE_UNAVAILABLE, CAPTURE_NOT_CURRENT, PERMISSION_MISSING,
        SESSION_AMBIGUOUS, METADATA_INCOMPLETE, POSITION_UNKNOWN, ANCHOR_INCOHERENT,
        SPEED_UNSUPPORTED, NOT_PLAYING, PROGRAMME_UNKNOWN, EDITION_UNKNOWN,
        FOREGROUND_UNKNOWN, TIMELINE_UNKNOWN, OBSERVATION_ONLY }

    /** Immutable diagnostic result; it deliberately exposes no execution capability. */
    public static final class Result {
        public final List<Reason> missing;

        /** Copy bounded reasons rather than retaining a mutable caller list. */
        private Result(List<Reason> missing) {
            this.missing = Collections.unmodifiableList(new ArrayList<>(missing));
        }

        /** A useful positive control for tests, without a corresponding permission to display. */
        public boolean conditionsComplete() { return missing.isEmpty(); }

        /** This profile cannot authorize an overlay under any combination of inputs. */
        public boolean renderingAllowed() { return false; }
    }

    /** Evaluate raw facts, keeping absent edition/foreground/timeline as explicit unknowns. */
    public static Result evaluate(MediaEvidence batch, long nowMs) {
        List<Reason> reasons = new ArrayList<>();
        if (batch == null) {
            reasons.add(Reason.SOURCE_UNAVAILABLE);
            return new Result(reasons);
        }
        if (batch.availability != MediaEvidence.Availability.AVAILABLE)
            reasons.add(Reason.SOURCE_UNAVAILABLE);
        if (nowMs < batch.capturedAtMs || nowMs - batch.capturedAtMs > DISPLAY_FRESHNESS_MS)
            reasons.add(Reason.CAPTURE_NOT_CURRENT);
        if (!batch.notificationAccess || !batch.overlayAccess) reasons.add(Reason.PERMISSION_MISSING);
        if (batch.sessionCount != 1 || batch.sessions.size() != 1) reasons.add(Reason.SESSION_AMBIGUOUS);
        else {
            MediaEvidence.Session s = batch.sessions.get(0);
            if (s == null || !s.complete || empty(s.key) || empty(s.packageName) || empty(s.mediaId))
                reasons.add(Reason.METADATA_INCOMPLETE);
            if (s != null) {
                if (s.positionMs < 0) reasons.add(Reason.POSITION_UNKNOWN);
                if (s.anchorAtMs <= 0 || s.anchorAtMs > batch.capturedAtMs)
                    reasons.add(Reason.ANCHOR_INCOHERENT);
                if (!Float.isFinite(s.speed) || s.speed != 1f) reasons.add(Reason.SPEED_UNSUPPORTED);
                if (s.playbackState != 3) reasons.add(Reason.NOT_PLAYING);
            }
        }
        if (batch.programme != MediaEvidence.Proof.OBSERVED) reasons.add(Reason.PROGRAMME_UNKNOWN);
        if (batch.edition != MediaEvidence.Proof.OBSERVED) reasons.add(Reason.EDITION_UNKNOWN);
        if (batch.foreground != MediaEvidence.Proof.OBSERVED) reasons.add(Reason.FOREGROUND_UNKNOWN);
        if (batch.timeline != MediaEvidence.Proof.OBSERVED) reasons.add(Reason.TIMELINE_UNKNOWN);
        return new Result(reasons);
    }

    /** Format a bounded read-only diagnostic; do not call historical credential/store capture. */
    public static String describe(MediaEvidence batch, long nowMs) {
        StringBuilder text = new StringBuilder("TV-FIRST 006 — OBSERVATION ONLY\nRendering: FORBIDDEN\nConfigured probe interval ms: 1000\n");
        if (batch == null) return text.append("Source: UNKNOWN / stopped\n").toString();
        text.append("Source: ").append(batch.availability).append("\nSequence: ").append(batch.sequence)
                .append("\nCaptured elapsed ms: ").append(batch.capturedAtMs)
                .append("\nSessions: ").append(batch.sessionCount)
                .append("\nProgramme: ").append(batch.programme).append("\nEdition: ").append(batch.edition)
                .append("\nForeground: ").append(batch.foreground).append("\nTimeline: ").append(batch.timeline)
                .append("\nMissing: ").append(evaluate(batch, nowMs).missing).append('\n');
        for (MediaEvidence.Session s : batch.sessions) {
            if (s == null) continue;
            text.append("Session ").append(safe(s.key)).append(" package=").append(safe(s.packageName))
                    .append(" mediaId=").append(safe(s.mediaId)).append(" state=").append(s.playbackState)
                    .append(" positionMs=").append(s.positionMs).append(" anchorMs=").append(s.anchorAtMs)
                    .append(" estimatedMs=").append(s.estimatedPositionMs).append(" speed=").append(s.speed)
                    .append(" durationMs=").append(s.durationMs).append(" complete=").append(s.complete).append('\n');
        }
        return text.toString();
    }

    /** Distinguish absent/empty metadata without treating a human title as an identity. */
    private static boolean empty(String text) { return text == null || text.isEmpty(); }

    /** Prevent control characters in the bounded diagnostic; retain no raw session token. */
    private static String safe(String text) { return text == null ? "UNKNOWN" : text.replaceAll("[\\p{Cntrl}]", " "); }

    /** Static evaluation owns no mutable acquisition or scheduling state. */
    private MediaEvidencePolicy() { }
}
