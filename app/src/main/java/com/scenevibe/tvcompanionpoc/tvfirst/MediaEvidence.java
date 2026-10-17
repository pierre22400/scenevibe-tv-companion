package com.scenevibe.tvcompanionpoc.tvfirst;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Passive, bounded facts captured during one probe pass. Source facts are not programme
 * admission: identity, edition, foreground and timeline remain UNKNOWN unless separately
 * demonstrated. This model holds no credential, installer, player control or renderer.
 */
public final class MediaEvidence {
    public static final int MAX_SESSIONS = 16;
    public enum Availability { AVAILABLE, ACCESS_REQUIRED, UNAVAILABLE, QUERY_FAILED, TOO_MANY_SESSIONS }
    public enum Proof { UNKNOWN, OBSERVED, CONTRADICTED }
    public final long sequence, capturedAtMs;
    public final Availability availability;
    public final boolean notificationAccess, overlayAccess;
    public final int sessionCount;
    public final List<Session> sessions;
    public final Proof programme, edition, foreground, timeline;

    /** Copy one bounded batch; proof coordinates describe independent facts, never a title match. */
    public MediaEvidence(long sequence, long capturedAtMs, Availability availability,
            boolean notificationAccess, boolean overlayAccess, int sessionCount,
            List<Session> sessions, Proof programme, Proof edition, Proof foreground, Proof timeline) {
        if (sequence < 1 || capturedAtMs < 0 || availability == null || sessions == null
                || sessions.size() > MAX_SESSIONS || sessionCount < sessions.size()
                || programme == null || edition == null || foreground == null || timeline == null)
            throw new IllegalArgumentException("Invalid observation batch");
        this.sequence = sequence;
        this.capturedAtMs = capturedAtMs;
        this.availability = availability;
        this.notificationAccess = notificationAccess;
        this.overlayAccess = overlayAccess;
        this.sessionCount = sessionCount;
        this.sessions = Collections.unmodifiableList(new ArrayList<>(sessions));
        this.programme = programme; this.edition = edition;
        this.foreground = foreground; this.timeline = timeline;
    }

    /** One session's actual scalars; absent or incoherent values are retained, not repaired. */
    public static final class Session {
        public final String key, packageName, mediaId, title, subtitle;
        public final int playbackState;
        public final long positionMs, anchorAtMs, estimatedPositionMs, durationMs;
        public final float speed;
        public final boolean complete;

        /** Bound strings explicitly and record truncation; the key is process-local, not a token. */
        public Session(String key, String packageName, String mediaId, String title, String subtitle,
                int playbackState, long positionMs, long anchorAtMs, float speed,
                long estimatedPositionMs, long durationMs) {
            this.key = bounded(key, 64);
            this.packageName = bounded(packageName, 256);
            this.mediaId = bounded(mediaId, 256);
            this.title = bounded(title, 500);
            this.subtitle = bounded(subtitle, 500);
            this.complete = fits(key, 64) && fits(packageName, 256) && fits(mediaId, 256)
                    && fits(title, 500) && fits(subtitle, 500);
            this.playbackState = playbackState; this.positionMs = positionMs;
            this.anchorAtMs = anchorAtMs; this.speed = speed;
            this.estimatedPositionMs = estimatedPositionMs; this.durationMs = durationMs;
        }

        /** Test the original length so truncation cannot masquerade as a complete identity. */
        private static boolean fits(String text, int maximum) {
            return text == null || text.length() <= maximum;
        }

        /** Keep absent metadata distinct from an empty published string. */
        private static String bounded(String text, int maximum) {
            return text == null ? null : text.substring(0, Math.min(text.length(), maximum));
        }
    }
}
