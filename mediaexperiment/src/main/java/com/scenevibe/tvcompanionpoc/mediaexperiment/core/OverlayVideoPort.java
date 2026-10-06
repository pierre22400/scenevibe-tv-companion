package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/**
 * Android-free seam over the fullscreen local interlude overlay/video surface.
 * The Android layer implements this with a WindowManager overlay + a video view;
 * tests supply fakes (including ones that throw to exercise failure cleanup).
 */
public interface OverlayVideoPort {
    /** Attach the fullscreen overlay surface. */
    void attach();

    /** Begin playback of the bundled local video. */
    void playLocalVideo();

    /** Remove the overlay surface. Must be safe to call repeatedly. */
    void remove();
}
