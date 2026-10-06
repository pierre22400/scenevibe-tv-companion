package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/**
 * Immutable description of the media session this POC decided to target when the
 * interlude sequence began. Plain Java, no Android types.
 */
public final class SessionTarget {
    public final String packageName;
    public final String mediaId;
    public final String title;
    public final String subtitle;
    public final long durationMs;

    public SessionTarget(
            String packageName,
            String mediaId,
            String title,
            String subtitle,
            long durationMs) {
        this.packageName = packageName;
        this.mediaId = mediaId;
        this.title = title;
        this.subtitle = subtitle;
        this.durationMs = durationMs;
    }
}
