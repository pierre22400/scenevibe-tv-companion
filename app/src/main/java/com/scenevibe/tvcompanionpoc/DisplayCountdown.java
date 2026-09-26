package com.scenevibe.tvcompanionpoc;

/** Counts visible time only during playback for an opted-in track card. */
final class DisplayCountdown {
    private long remainingMs;
    private long runningSinceMs = -1L;

    void start(long durationMs, long nowMs, boolean playing) {
        remainingMs = durationMs;
        runningSinceMs = playing ? nowMs : -1L;
    }

    long update(boolean playing, long nowMs) {
        if (runningSinceMs >= 0L) {
            remainingMs = Math.max(0L, remainingMs - Math.max(0L, nowMs - runningSinceMs));
        }
        runningSinceMs = playing && remainingMs > 0L ? nowMs : -1L;
        return remainingMs;
    }

    void clear() {
        remainingMs = 0L;
        runningSinceMs = -1L;
    }
}
