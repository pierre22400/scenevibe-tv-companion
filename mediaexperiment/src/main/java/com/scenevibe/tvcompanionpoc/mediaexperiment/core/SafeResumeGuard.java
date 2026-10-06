package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/**
 * Fail-closed decision core for whether this POC may issue a PLAY (resume) command.
 *
 * <p>PLAY may be issued ONLY if ALL of the following hold:</p>
 * <ol>
 *   <li>this POC successfully SENT the PAUSE;</li>
 *   <li>PAUSED was subsequently OBSERVED (a sent-but-unconfirmed pause is not enough);</li>
 *   <li>the target session/package is still the same;</li>
 *   <li>the media identity has not clearly changed;</li>
 *   <li>the user has not switched to another media app;</li>
 *   <li>the interlude ended normally OR the POC is explicitly doing emergency cleanup.</li>
 * </ol>
 *
 * <p>Android-free and side-effect-free: the decision is a pure boolean function of
 * its inputs so it is exhaustively unit-testable without a device.</p>
 */
public final class SafeResumeGuard {

    /**
     * @param target the session this POC originally targeted when it paused
     * @param ownership recorded pause-send/confirm + interlude-end facts
     * @param latest the most recent observed snapshot (may be null if none observed)
     * @return true only when every condition above holds; false otherwise
     */
    public boolean mayResume(
            SessionTarget target, PauseOwnership ownership, PlaybackSnapshot latest) {
        if (target == null || ownership == null) return false;

        // (1) + (2) We must have both sent AND observed the pause.
        if (!ownership.pauseSent) return false;
        if (!ownership.pauseConfirmed) return false;

        // (6) Interlude ended normally, or we are explicitly in emergency cleanup.
        if (!ownership.interludeEndedNormally && !ownership.emergencyCleanup) return false;

        // Without a current observation we cannot confirm the target is unchanged.
        if (latest == null) return false;

        // (3) + (5) Same package / user has not switched to a different media app.
        if (target.packageName == null || !target.packageName.equals(latest.packageName)) {
            return false;
        }

        // (4) Media identity must not have clearly changed (fail-closed).
        if (!PocMediaIdentity.sameMedia(target, latest)) return false;

        return true;
    }

    private SafeResumeGuard() {}

    public static SafeResumeGuard create() {
        return new SafeResumeGuard();
    }
}
