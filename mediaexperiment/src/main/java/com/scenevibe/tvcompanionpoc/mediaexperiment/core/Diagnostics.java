package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/**
 * Bounded diagnostics holder for a single interlude attempt.
 *
 * <p>Captures ONLY coarse mechanism facts: selected package, playback state name,
 * a bounded set of PlaybackState action flags, media-id presence, focus result,
 * command-sent/confirmed/timeout booleans, overlay/video progress. It deliberately
 * stores NO content text, NO credentials, and NO titles beyond the internal
 * identity check (which is never recorded here).</p>
 *
 * <p>These are logic diagnostics only. Nothing here claims real audio ducking or a
 * real streaming-app pause occurred; that is a physical, on-device observation.</p>
 */
public final class Diagnostics {
    public String selectedPackage;
    public String playbackStateName = PlaybackStateCodes.name(PlaybackStateCodes.STATE_NONE);

    // Bounded set of transport action flags advertised by the session.
    public boolean actionPlayAvailable;
    public boolean actionPauseAvailable;
    public boolean actionPlayPauseAvailable;

    public boolean mediaIdPresent;

    public AudioFocusPort.Result audioFocusResult;

    public boolean pauseCommandSent;
    public boolean pauseConfirmed;
    public boolean pauseTimedOut;

    public boolean overlayAttached;

    public boolean localVideoStarted;
    public boolean localVideoCompleted;
    public boolean localVideoError;

    public boolean playCommandSent;
    public boolean resumeConfirmed;
    public boolean resumeTimedOut;
}
