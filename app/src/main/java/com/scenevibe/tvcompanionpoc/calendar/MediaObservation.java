package com.scenevibe.tvcompanionpoc.calendar;

/**
 * Immutable observation already selected by an external acquisition/identity boundary.
 * Every negative position denotes unavailable position. No position is estimated,
 * normalized or clamped, and this value contains no platform or player object.
 */
public final class MediaObservation {
    private final boolean eligible;
    private final long positionMs;
    private final boolean playing;

    /** Retain the three supplied scalar values exactly, without interpreting time. */
    public MediaObservation(boolean eligible, long positionMs, boolean playing) {
        this.eligible = eligible;
        this.positionMs = positionMs;
        this.playing = playing;
    }

    /** Return the eligibility decision made outside the temporal core. */
    public boolean eligible() {
        return eligible;
    }

    /** Return the exact position, including any negative unavailable sentinel. */
    public long positionMs() {
        return positionMs;
    }

    /** Return the supplied playback classification without querying a player. */
    public boolean playing() {
        return playing;
    }
}
