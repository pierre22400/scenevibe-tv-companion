package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/**
 * Immutable description of the media session this POC decided to target when the
 * interlude sequence began. Plain Java, no Android types.
 */
public final class SessionTarget {
    /** Opaque equality identity; Android supplies MediaSession.Token. Never log it. */
    public final Object sessionIdentity;
    public final String packageName;
    public final String mediaId;
    public final String title;
    public final String subtitle;
    public final long durationMs;

    /** Capture media facts and the original session identity together. */
    public SessionTarget(
            String packageName,
            String mediaId,
            String title,
            String subtitle,
            long durationMs,
            Object sessionIdentity) {
        this.sessionIdentity = sessionIdentity;
        this.packageName = packageName;
        this.mediaId = mediaId;
        this.title = title;
        this.subtitle = subtitle;
        this.durationMs = durationMs;
    }

    /** Capture the exact session and media observed immediately before PAUSE. */
    public static SessionTarget from(PlaybackSnapshot snapshot) {
        if (snapshot == null) return null;
        return new SessionTarget(snapshot.packageName, snapshot.mediaId, snapshot.title,
                snapshot.subtitle, snapshot.durationMs, snapshot.sessionIdentity);
    }
}
