package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/**
 * Android-free seam over a short local audio clip played during the interlude.
 * The Android layer implements this with a local player; tests supply fakes.
 */
public interface LocalAudioPort {
    /** Play the short bundled audio clip. */
    void playShortClip();

    /** Stop the clip. Must be safe to call repeatedly. */
    void stop();
}
