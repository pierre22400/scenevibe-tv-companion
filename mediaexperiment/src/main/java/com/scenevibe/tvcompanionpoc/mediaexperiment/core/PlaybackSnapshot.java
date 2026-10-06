package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/**
 * Immutable, Android-free equivalent of the production {@code MediaSessionProbe.Snapshot}
 * value shape. The {@code state} field is a POC-local playback state code
 * (see {@link PlaybackStateCodes}), never an Android constant.
 */
public final class PlaybackSnapshot {
    public final String packageName;
    public final int state;
    public final String mediaId;
    public final String title;
    public final String subtitle;
    public final long durationMs;

    public PlaybackSnapshot(
            String packageName,
            int state,
            String mediaId,
            String title,
            String subtitle,
            long durationMs) {
        this.packageName = packageName;
        this.state = state;
        this.mediaId = mediaId;
        this.title = title;
        this.subtitle = subtitle;
        this.durationMs = durationMs;
    }

    public String stateName() {
        return PlaybackStateCodes.name(state);
    }
}
