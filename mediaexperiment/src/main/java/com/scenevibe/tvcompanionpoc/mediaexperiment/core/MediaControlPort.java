package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/**
 * Android-free seam over MediaSession transport controls. These are
 * fire-and-forget: dispatching a command is NOT evidence it took effect. Only an
 * observed PAUSED/PLAYING snapshot confirms the result.
 */
public interface MediaControlPort {
    /** Dispatch a transport pause to the targeted session. */
    void pause();

    /** Dispatch a transport play to the targeted session. */
    void play();
}
