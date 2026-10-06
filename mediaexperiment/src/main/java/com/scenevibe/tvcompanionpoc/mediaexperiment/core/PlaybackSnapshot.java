package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/**
 * Immutable, Android-free equivalent of the production {@code MediaSessionProbe.Snapshot}
 * value shape. The {@code state} field is a POC-local playback state code
 * (see {@link PlaybackStateCodes}), never an Android constant.
 */
public final class PlaybackSnapshot {
    /** Opaque identity, kept separate from package and media metadata. Never log it. */
    public final Object sessionIdentity;
    public final String packageName;
    public final int state;
    public final String mediaId;
    public final String title;
    public final String subtitle;
    public final long durationMs;

    /** Store one observation without manufacturing an identity from metadata. */
    public PlaybackSnapshot(
            String packageName,
            int state,
            String mediaId,
            String title,
            String subtitle,
            long durationMs,
            Object sessionIdentity) {
        this.sessionIdentity = sessionIdentity;
        this.packageName = packageName;
        this.state = state;
        this.mediaId = mediaId;
        this.title = title;
        this.subtitle = subtitle;
        this.durationMs = durationMs;
    }

    /** Return a bounded state label suitable for diagnostics. */
    public String stateName() {
        return PlaybackStateCodes.name(state);
    }
}
