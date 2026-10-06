package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/**
 * Android-free seam over transient audio focus. The Android layer (FEAT-003)
 * implements this with AudioManager; tests supply trivial fakes.
 */
public interface AudioFocusPort {
    /** @return GRANTED or DENIED for a transient-may-duck focus request. */
    Result requestTransientMayDuck();

    /** Release any focus previously granted. Must be safe to call repeatedly. */
    void abandon();

    enum Result {
        GRANTED,
        DENIED
    }
}
