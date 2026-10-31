package com.scenevibe.accessibilityprobe004;

import android.os.SystemClock;

/** Android clock adapter; no estimated playback or clock offset is constructed. */
public final class AndroidProbeClock implements ProbeClock {
    /** Return monotonic elapsed time including sleep for admission/deadline checks. */
    @Override public long elapsed() { return SystemClock.elapsedRealtime(); }
    /** Return uptime excluding deep sleep, matching AccessibilityEvent eventTime. */
    @Override public long uptime() { return SystemClock.uptimeMillis(); }
    /** Return wall time for approximate offline correlation only. */
    @Override public long wall() { return System.currentTimeMillis(); }
}
