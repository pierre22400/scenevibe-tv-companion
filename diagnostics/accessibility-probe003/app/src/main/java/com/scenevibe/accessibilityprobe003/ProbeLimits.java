package com.scenevibe.accessibilityprobe003;

/** Fixed discovery budgets; these are collection limits, never advertisement thresholds. */
public final class ProbeLimits {
    public static final long DURATION_MS = 600_000L;
    public static final long BYTES = 2_000_000L;
    public static final int NODES = 200;
    public static final int DEPTH = 12;
    public static final int WINDOWS = 8;
    public static final long EXPLORE_INTERVAL_MS = 250L;
    public static final long STALE_EVENT_MS = 5_000L;
    public static final int TEXT_CHARS = 160;
    public static final int TEXT_ITEMS = 16;
    public static final long RETENTION_MS = 86_400_000L;

    /** Prevent construction of the constants container. */
    private ProbeLimits() { }
}
