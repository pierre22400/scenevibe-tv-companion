package com.scenevibe.accessibilityprobe004;

/**
 * Pure scheduling policy. A voluntary dense window expires into the ordinary cadence; late
 * ticks are never replayed. EVENT_ONLY_CONTROL is restricted to the app-owned comparator.
 */
public final class ObservationCadence {
    public enum Mode { NORMAL, DENSE_30_SECONDS, EVENT_ONLY_CONTROL }
    private final Mode mode;
    private final long startedElapsed;
    private int consecutiveSlow;

    /** Bind one immutable campaign mode and its elapsedRealtime start, without persistence. */
    public ObservationCadence(Mode mode, long startedElapsed) {
        this.mode = mode; this.startedElapsed = startedElapsed;
    }

    /** Return the next delay; zero denotes the explicit synthetic event-only comparator. */
    public long interval(long nowElapsed) {
        if (mode == Mode.EVENT_ONLY_CONTROL) return 0;
        return mode == Mode.DENSE_30_SECONDS && nowElapsed - startedElapsed < ProbeLimits.DENSE_WINDOW_MS
            ? ProbeLimits.DENSE_POLL_MS : ProbeLimits.POLL_MS;
    }

    /** Stop after two consecutive wall-duration overruns; a normal completion resets the streak. */
    public boolean overloaded(long durationMs) {
        consecutiveSlow = durationMs > ProbeLimits.OVERLOAD_MS ? consecutiveSlow + 1 : 0;
        return consecutiveSlow >= 2;
    }
}
