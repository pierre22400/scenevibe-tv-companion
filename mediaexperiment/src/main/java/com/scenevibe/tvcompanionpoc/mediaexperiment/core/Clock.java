package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/**
 * Android-free monotonic time seam (milliseconds). The Android layer implements
 * this with {@code SystemClock.elapsedRealtime()}; tests supply a mutable fake so
 * timeout logic is fully deterministic.
 */
public interface Clock {
    long nowMs();
}
