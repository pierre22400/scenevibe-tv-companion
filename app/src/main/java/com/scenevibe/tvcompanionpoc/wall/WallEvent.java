package com.scenevibe.tvcompanionpoc.wall;

/** Immutable WALL identity and UTC window. No payload or MEDIA position is carried. */
public final class WallEvent {
    public static final long MAX_EPOCH_MS = 253_402_300_799_999L;
    public static final long MIN_DURATION_MS = 250L;
    public static final long MAX_DURATION_MS = 3_600_000L;

    private final String eventId;
    private final long startEpochMs;
    private final long endEpochMs;

    /** Validate both endpoints before subtracting, retaining the exact safe ASCII ID. */
    public WallEvent(String eventId, long startEpochMs, long endEpochMs) {
        if (eventId == null || eventId.length() > 128
                || !eventId.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("Invalid WALL identifier");
        }
        requireEpoch(startEpochMs);
        requireEpoch(endEpochMs);
        if (startEpochMs >= endEpochMs || endEpochMs - startEpochMs < MIN_DURATION_MS
                || endEpochMs - startEpochMs > MAX_DURATION_MS) {
            throw new IllegalArgumentException("Invalid WALL duration");
        }
        this.eventId = eventId;
        this.startEpochMs = startEpochMs;
        this.endEpochMs = endEpochMs;
    }

    /** Reject out-of-domain time before any arithmetic or scheduler state mutation. */
    static void requireEpoch(long epochMs) {
        if (epochMs < 0L || epochMs > MAX_EPOCH_MS) {
            throw new IllegalArgumentException("Invalid WALL epoch");
        }
    }

    /** Return the unchanged opaque ASCII identifier. */
    public String eventId() { return eventId; }

    /** Return the inclusive UTC start in abstract milliseconds. */
    public long startEpochMs() { return startEpochMs; }

    /** Return the exclusive UTC end in abstract milliseconds. */
    public long endEpochMs() { return endEpochMs; }
}
