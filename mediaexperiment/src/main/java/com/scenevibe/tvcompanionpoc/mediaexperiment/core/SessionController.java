package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/** Minimal controller adapter; Android owns the token and transport implementation. */
public interface SessionController {
    /** Read fresh state, metadata and an opaque MediaSession identity. */
    PlaybackSnapshot snapshot();

    /** Whether PLAY is advertised by this controller. */
    boolean canPlay();

    /** Whether PAUSE is advertised by this controller. */
    boolean canPause();

    /** Whether PLAY_PAUSE is advertised by this controller. */
    boolean canPlayPause();

    /** Dispatch PAUSE using MediaSession transport controls only. */
    void pause();

    /** Dispatch PLAY using MediaSession transport controls only. */
    void play();
}
