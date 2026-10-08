package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/**
 * Spike 2.0 voice-fixture output, independent of Android and native transport.
 *
 * <p>Only locally supplied speech is played; implementations never request
 * AudioFocus or send native PAUSE/PLAY transport commands.</p>
 */
public interface SpeechFixturePort {
    /** Start the exact externally provisioned ten-second MP3 fixture. */
    void start();

    /** Stop and release speech playback, including partial preparation, idempotently. */
    void stop();
}
