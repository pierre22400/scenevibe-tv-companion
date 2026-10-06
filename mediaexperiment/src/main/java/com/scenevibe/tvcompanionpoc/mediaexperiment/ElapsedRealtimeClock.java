package com.scenevibe.tvcompanionpoc.mediaexperiment;

import android.os.SystemClock;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.Clock;

/**
 * Android {@link Clock} backed by {@code SystemClock.elapsedRealtime()}, the
 * monotonic clock appropriate for bounded timeout deadlines.
 */
final class ElapsedRealtimeClock implements Clock {
    @Override
    public long nowMs() {
        return SystemClock.elapsedRealtime();
    }
}
