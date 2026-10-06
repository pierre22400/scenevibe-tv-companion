package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/**
 * Explicit, bounded sequence of states for the local interlude capability spike.
 *
 * <p>Android-free. The sequence models: discover a playing session, request
 * transient audio focus, send a MediaSession pause, wait for an observed PAUSED
 * confirmation, attach a fullscreen overlay, play a local video, then (only when
 * the safe-resume guard permits) send PLAY and wait for an observed PLAYING
 * confirmation. Any failure path converges on {@link #STOPPED}.</p>
 */
public enum InterludeState {
    IDLE,
    SCANNING,
    FOCUS_REQUESTED,
    FOCUS_GRANTED,
    FOCUS_DENIED,
    PAUSE_SENT,
    PAUSE_CONFIRMED,
    PAUSE_TIMEOUT,
    OVERLAY_ATTACHED,
    VIDEO_PLAYING,
    VIDEO_COMPLETED,
    VIDEO_ERROR,
    PLAY_SENT,
    PLAY_CONFIRMED,
    PLAY_TIMEOUT,
    STOPPED
}
