package com.scenevibe.tvcompanionpoc.calendar;

/**
 * Immutable temporal identity, without a rendering payload or a clock.
 * The identifier is retained verbatim. Non-positive durations represent the
 * historical no-window cases; accepting them here does not change Video ingress.
 */
public final class SceneEvent {
    private final String eventId;
    private final long startMs;
    private final long durationMs;

    /** Validate the bounded temporal value without normalizing its identifier. */
    public SceneEvent(String eventId, long startMs, long durationMs) {
        if (eventId == null || eventId.isEmpty() || eventId.length() > 128
                || startMs < 0 || startMs > 43_200_000L || durationMs > 60_000L) {
            throw new IllegalArgumentException("Invalid scene event");
        }
        this.eventId = eventId;
        this.startMs = startMs;
        this.durationMs = durationMs;
    }

    /** Return the exact opaque identifier, including whitespace and Unicode. */
    public String eventId() {
        return eventId;
    }

    /** Return the validated media start position in milliseconds. */
    public long startMs() {
        return startMs;
    }

    /** Return the exact duration; non-positive values do not describe a window. */
    public long durationMs() {
        return durationMs;
    }
}
