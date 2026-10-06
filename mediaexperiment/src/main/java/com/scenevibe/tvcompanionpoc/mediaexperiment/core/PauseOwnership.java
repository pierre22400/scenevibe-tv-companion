package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/**
 * Recorded facts about what this POC actually did and observed around the pause.
 * Plain-Java inputs to {@link SafeResumeGuard}. "Sent" is deliberately distinct
 * from "confirmed": a sent-but-unconfirmed pause must never authorize PLAY.
 */
public final class PauseOwnership {
    /** State observed immediately before this POC dispatched PAUSE. */
    public final int initialPlaybackState;
    /** This POC successfully dispatched the PAUSE transport command. */
    public final boolean pauseSent;

    /** A PAUSED snapshot was subsequently OBSERVED (not merely assumed). */
    public final boolean pauseConfirmed;

    /** The interlude ended normally (video completed) rather than erroring/aborting. */
    public final boolean interludeEndedNormally;

    /** Record the observed PLAYING -> sent PAUSE -> observed PAUSED chain. */
    public PauseOwnership(
            int initialPlaybackState,
            boolean pauseSent,
            boolean pauseConfirmed,
            boolean interludeEndedNormally) {
        this.initialPlaybackState = initialPlaybackState;
        this.pauseSent = pauseSent;
        this.pauseConfirmed = pauseConfirmed;
        this.interludeEndedNormally = interludeEndedNormally;
    }

    /** Ownership cannot arise from a pre-existing pause or a sent-only command. */
    public boolean ownsPause() {
        return initialPlaybackState == PlaybackStateCodes.STATE_PLAYING
                && pauseSent && pauseConfirmed;
    }
}
