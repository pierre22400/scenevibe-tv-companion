package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/**
 * POC-local playback state integer constants.
 *
 * <p>These intentionally mirror the numeric values of
 * {@code android.media.session.PlaybackState} so the Android layer (FEAT-003) can
 * forward observed state codes without translation, yet this class imports NO
 * Android types so the core logic runs under plain JUnit with
 * {@code testOptions.unitTests.returnDefaultValues}.</p>
 */
public final class PlaybackStateCodes {
    public static final int STATE_NONE = 0;
    public static final int STATE_STOPPED = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_PLAYING = 3;
    public static final int STATE_FAST_FORWARDING = 4;
    public static final int STATE_REWINDING = 5;
    public static final int STATE_BUFFERING = 6;
    public static final int STATE_ERROR = 7;
    public static final int STATE_CONNECTING = 8;
    public static final int STATE_SKIPPING_TO_PREVIOUS = 9;
    public static final int STATE_SKIPPING_TO_NEXT = 10;
    public static final int STATE_SKIPPING_TO_QUEUE_ITEM = 11;

    public static String name(int state) {
        switch (state) {
            case STATE_NONE: return "NONE";
            case STATE_STOPPED: return "STOPPED";
            case STATE_PAUSED: return "PAUSED";
            case STATE_PLAYING: return "PLAYING";
            case STATE_FAST_FORWARDING: return "FAST_FORWARDING";
            case STATE_REWINDING: return "REWINDING";
            case STATE_BUFFERING: return "BUFFERING";
            case STATE_ERROR: return "ERROR";
            case STATE_CONNECTING: return "CONNECTING";
            case STATE_SKIPPING_TO_PREVIOUS: return "SKIPPING_TO_PREVIOUS";
            case STATE_SKIPPING_TO_NEXT: return "SKIPPING_TO_NEXT";
            case STATE_SKIPPING_TO_QUEUE_ITEM: return "SKIPPING_TO_QUEUE_ITEM";
            default: return "UNKNOWN_" + state;
        }
    }

    private PlaybackStateCodes() {}
}
