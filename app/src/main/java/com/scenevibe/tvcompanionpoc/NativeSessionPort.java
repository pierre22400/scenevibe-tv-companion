package com.scenevibe.tvcompanionpoc;

/**
 * Sole native-session boundary used by the ephemeral transport owner.
 * Implementations must query the identified controller afresh at dispatch, never target by
 * package alone, never retain a stale transport controller and never issue commands autonomously.
 */
interface NativeSessionPort {
    /** Select one unambiguous Prime VOD target after explicit local operator confirmation. */
    NativeSessionSnapshot select(boolean vodConfirmed);

    /** Observe the original token/identity again; return null on loss or competing sessions. */
    NativeSessionSnapshot observe(NativeSessionSnapshot original);

    /** Revalidate freshness/PLAYING/actions at dispatch and issue at most one PAUSE. */
    boolean pause(NativeSessionSnapshot expected, NativeQualificationPolicy policy, long nowMs);

    /** Revalidate the exact PAUSED anchor at dispatch and issue at most one PLAY. */
    boolean play(NativeSessionSnapshot pausedAnchor, NativeQualificationPolicy policy, long nowMs);
}
