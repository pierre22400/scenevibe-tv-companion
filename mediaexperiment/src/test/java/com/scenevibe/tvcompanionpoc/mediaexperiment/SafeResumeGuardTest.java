package com.scenevibe.tvcompanionpoc.mediaexperiment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.DeniedReason;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SessionRevalidation;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.PauseOwnership;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.PlaybackSnapshot;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.PlaybackStateCodes;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SafeResumeGuard;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SessionTarget;

import org.junit.Test;

/** Direct coverage of ownership, live token, PAUSED and normal-completion requirements. */
public class SafeResumeGuardTest {

    private static final String PKG = "com.amazon.amazonvideo.livingroom";
    private final SafeResumeGuard guard = SafeResumeGuard.create();

    /** Capture token and media identity independently. */
    private SessionTarget target() {
        return new SessionTarget(PKG, "mid-1", "The Expanse", "Ep 1", 3_600_000L, "original-token");
    }

    /** Build a live observation with the original token. */
    private PlaybackSnapshot sameMedia(int state) {
        return new PlaybackSnapshot(PKG, state, "mid-1", "The Expanse", "Ep 1", 3_600_000L, "original-token");
    }

    /** Record an observed PLAYING-to-PAUSED ownership chain. */
    private PauseOwnership owned() {
        return new PauseOwnership(PlaybackStateCodes.STATE_PLAYING, true, true, true);
    }

    /** Supply successful live-query facts for isolated guard-condition tests. */
    private SessionRevalidation live(PlaybackSnapshot snapshot) {
        return new SessionRevalidation(snapshot, true, false, DeniedReason.NONE);
    }

    /** Verify allConditionsHold true. */
    @Test public void allConditionsHold_true() {
        assertTrue(guard.mayResume(target(), owned(),
                live(sameMedia(PlaybackStateCodes.STATE_PAUSED))));
    }

    /** Verify pauseNotSent false. */
    @Test public void pauseNotSent_false() {
        assertFalse(guard.mayResume(target(),
                new PauseOwnership(PlaybackStateCodes.STATE_PLAYING, false, true, true),
                live(sameMedia(PlaybackStateCodes.STATE_PAUSED))));
    }

    /** Verify pauseSentButNotConfirmed false. */
    @Test public void pauseSentButNotConfirmed_false() {
        assertFalse("sent is not confirmed",
                guard.mayResume(target(),
                        new PauseOwnership(PlaybackStateCodes.STATE_PLAYING, true, false, true),
                        live(sameMedia(PlaybackStateCodes.STATE_PAUSED))));
    }

    /** Verify interludeNotEndedNormally false. */
    @Test public void interludeNotEndedNormally_false() {
        assertFalse(guard.mayResume(target(),
                new PauseOwnership(PlaybackStateCodes.STATE_PLAYING, true, true, false),
                live(sameMedia(PlaybackStateCodes.STATE_PAUSED))));
    }

    /** A pre-existing pause cannot be owned, even with fabricated sent/confirmed flags. */
    @Test public void initialPaused_neverOwnsPause() {
        assertFalse(guard.mayResume(target(),
                new PauseOwnership(PlaybackStateCodes.STATE_PAUSED, true, true, true),
                live(sameMedia(PlaybackStateCodes.STATE_PAUSED))));
    }

    /** Verify appSwitchedToDifferentPackage false. */
    @Test public void appSwitchedToDifferentPackage_false() {
        PlaybackSnapshot other = new PlaybackSnapshot(
                "com.netflix.ninja", PlaybackStateCodes.STATE_PLAYING,
                "nf-9", "Other", "x", 1_000L, "other-token");
        assertFalse(guard.mayResume(target(), owned(), live(other)));
    }

    /** Verify mediaIdentityChanged false. */
    @Test public void mediaIdentityChanged_false() {
        PlaybackSnapshot changed = new PlaybackSnapshot(
                PKG, PlaybackStateCodes.STATE_PLAYING, "mid-999", "A Different Show",
                "y", 3_600_000L, "original-token");
        assertFalse(guard.mayResume(target(), owned(), live(changed)));
    }

    /** Verify noObservationAvailable false. */
    @Test public void noObservationAvailable_false() {
        assertFalse(guard.mayResume(target(), owned(), null));
    }

    /** Matching package/media with a replacement token still fails the guard. */
    @Test public void samePackageSameMediaChangedToken_false() {
        PlaybackSnapshot replacement = new PlaybackSnapshot(PKG, PlaybackStateCodes.STATE_PAUSED,
                "mid-1", "The Expanse", "Ep 1", 3_600_000L, "replacement-token");
        assertFalse(guard.mayResume(target(), owned(), live(replacement)));
    }

    /** Resume requires the latest live state to remain PAUSED, for every other state. */
    @Test public void latestNotPaused_false() {
        for (int state = 0; state <= 12; state++) {
            if (state == PlaybackStateCodes.STATE_PAUSED) continue;
            assertFalse(guard.mayResume(target(), owned(), live(sameMedia(state))));
        }
    }

    /** The guard refuses an original controller that is no longer actively listed. */
    @Test public void originalNotRevalidated_false() {
        assertFalse(guard.mayResume(target(), owned(), new SessionRevalidation(
                sameMedia(PlaybackStateCodes.STATE_PAUSED), false, false, DeniedReason.NONE)));
    }

    /** Relevant-app changes deny PLAY even while the original token remains live. */
    @Test public void relevantPackageChanged_false() {
        assertFalse(guard.mayResume(target(), owned(), new SessionRevalidation(
                sameMedia(PlaybackStateCodes.STATE_PAUSED), true, true, DeniedReason.NONE)));
    }
}
