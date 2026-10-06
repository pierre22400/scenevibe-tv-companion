package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/**
 * Fail-closed decision core for whether this POC may issue a PLAY (resume) command.
 *
 * <p>PLAY may be issued ONLY if ALL of the following hold:</p>
 * <ol>
 *   <li>PLAYING was observed immediately before this POC SENT the PAUSE;</li>
 *   <li>PAUSED was subsequently OBSERVED (a sent-but-unconfirmed pause is not enough);</li>
 *   <li>the target session/package is still the same;</li>
 *   <li>the media identity has not clearly changed;</li>
 *   <li>the user has not switched to another media app;</li>
 *   <li>the interlude ended normally, active sessions were revalidated and the
 *       original session is still live and PAUSED.</li>
 * </ol>
 *
 * <p>Android-free and side-effect-free: the decision is a pure boolean function of
 * its inputs so it is exhaustively unit-testable without a device.</p>
 */
public final class SafeResumeGuard {

    /**
     * @param target the session this POC originally targeted when it paused
     * @param ownership recorded pause-send/confirm + interlude-end facts
     * @param live the result of a fresh active-session revalidation
     * @return true only when every condition above holds; false otherwise
     */
    public boolean mayResume(
            SessionTarget target, PauseOwnership ownership, SessionRevalidation live) {
        return denialReason(target, ownership, live) == DeniedReason.NONE;
    }

    /** Explain a failed guard with a bounded enum, never identity or content text. */
    public DeniedReason denialReason(
            SessionTarget target, PauseOwnership ownership, SessionRevalidation live) {
        if (target == null || ownership == null || !ownership.ownsPause()) {
            return DeniedReason.PAUSE_NOT_OWNED;
        }
        if (!ownership.interludeEndedNormally) return DeniedReason.INTERLUDE_NOT_COMPLETED;
        if (live == null) return DeniedReason.SESSION_QUERY_FAILED;
        if (!live.valid()) {
            return live.reason == DeniedReason.NONE ? DeniedReason.AMBIGUOUS_SESSION : live.reason;
        }
        PlaybackSnapshot latest = live.latest;
        if (target.sessionIdentity == null
                || !target.sessionIdentity.equals(latest.sessionIdentity)) {
            return DeniedReason.SESSION_TOKEN_CHANGED;
        }
        if (target.packageName == null || !target.packageName.equals(latest.packageName)) {
            return DeniedReason.PACKAGE_CHANGED;
        }
        if (!PocMediaIdentity.sameMedia(target, latest)) return DeniedReason.MEDIA_CHANGED;
        if (latest.state != PlaybackStateCodes.STATE_PAUSED) return DeniedReason.LIVE_STATE_NOT_PAUSED;
        return DeniedReason.NONE;
    }

    /** Construct through the explicit factory to keep the guard stateless. */
    private SafeResumeGuard() {}

    /** Create a stateless fail-closed guard. */
    public static SafeResumeGuard create() {
        return new SafeResumeGuard();
    }
}
