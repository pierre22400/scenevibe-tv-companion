package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/**
 * Recorded facts about what this POC actually did and observed around the pause.
 * Plain-Java inputs to {@link SafeResumeGuard}. "Sent" is deliberately distinct
 * from "confirmed": a sent-but-unconfirmed pause must never authorize PLAY.
 */
public final class PauseOwnership {
    /** This POC successfully dispatched the PAUSE transport command. */
    public final boolean pauseSent;

    /** A PAUSED snapshot was subsequently OBSERVED (not merely assumed). */
    public final boolean pauseConfirmed;

    /** The interlude ended normally (video completed) rather than erroring/aborting. */
    public final boolean interludeEndedNormally;

    /** The POC is explicitly performing emergency cleanup (allows resume attempt). */
    public final boolean emergencyCleanup;

    public PauseOwnership(
            boolean pauseSent,
            boolean pauseConfirmed,
            boolean interludeEndedNormally,
            boolean emergencyCleanup) {
        this.pauseSent = pauseSent;
        this.pauseConfirmed = pauseConfirmed;
        this.interludeEndedNormally = interludeEndedNormally;
        this.emergencyCleanup = emergencyCleanup;
    }
}
