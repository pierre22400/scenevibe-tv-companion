package com.scenevibe.tvcompanionpoc.mediaexperiment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.PauseOwnership;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.PlaybackSnapshot;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.PlaybackStateCodes;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SafeResumeGuard;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SessionTarget;

import org.junit.Test;

/** Direct, exhaustive coverage of the six-condition safe-resume rule. */
public class SafeResumeGuardTest {

    private static final String PKG = "com.amazon.amazonvideo.livingroom";
    private final SafeResumeGuard guard = SafeResumeGuard.create();

    private SessionTarget target() {
        return new SessionTarget(PKG, "mid-1", "The Expanse", "Ep 1", 3_600_000L);
    }

    private PlaybackSnapshot sameMedia(int state) {
        return new PlaybackSnapshot(PKG, state, "mid-1", "The Expanse", "Ep 1", 3_600_000L);
    }

    private PauseOwnership owned() {
        return new PauseOwnership(true, true, true, false);
    }

    @Test public void allConditionsHold_true() {
        assertTrue(guard.mayResume(target(), owned(),
                sameMedia(PlaybackStateCodes.STATE_PAUSED)));
    }

    @Test public void pauseNotSent_false() {
        assertFalse(guard.mayResume(target(),
                new PauseOwnership(false, true, true, false),
                sameMedia(PlaybackStateCodes.STATE_PAUSED)));
    }

    @Test public void pauseSentButNotConfirmed_false() {
        assertFalse("sent is not confirmed",
                guard.mayResume(target(),
                        new PauseOwnership(true, false, true, false),
                        sameMedia(PlaybackStateCodes.STATE_PAUSED)));
    }

    @Test public void interludeNotEndedNormallyAndNotEmergency_false() {
        assertFalse(guard.mayResume(target(),
                new PauseOwnership(true, true, false, false),
                sameMedia(PlaybackStateCodes.STATE_PAUSED)));
    }

    @Test public void emergencyCleanupAllowsResumeWhenOtherwiseOwned_true() {
        assertTrue(guard.mayResume(target(),
                new PauseOwnership(true, true, false, true),
                sameMedia(PlaybackStateCodes.STATE_PAUSED)));
    }

    @Test public void appSwitchedToDifferentPackage_false() {
        PlaybackSnapshot other = new PlaybackSnapshot(
                "com.netflix.ninja", PlaybackStateCodes.STATE_PLAYING,
                "nf-9", "Other", "x", 1_000L);
        assertFalse(guard.mayResume(target(), owned(), other));
    }

    @Test public void mediaIdentityChanged_false() {
        PlaybackSnapshot changed = new PlaybackSnapshot(
                PKG, PlaybackStateCodes.STATE_PLAYING, "mid-999", "A Different Show",
                "y", 3_600_000L);
        assertFalse(guard.mayResume(target(), owned(), changed));
    }

    @Test public void noObservationAvailable_false() {
        assertFalse(guard.mayResume(target(), owned(), null));
    }
}
