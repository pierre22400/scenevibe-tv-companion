package com.scenevibe.tvcompanionpoc.mediaexperiment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.scenevibe.tvcompanionpoc.mediaexperiment.TestFakes.MutableSessions;
import com.scenevibe.tvcompanionpoc.mediaexperiment.TestFakes.RecordingController;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.DeniedReason;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.PlaybackStateCodes;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SessionCatalog;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SessionTarget;

import org.junit.Test;

/** Exercise selection and live revalidation through the real scanner's catalog seam. */
public class SessionCatalogTest {
    private final MutableSessions sessions = new MutableSessions();
    private final RecordingController original = new RecordingController();
    private final SessionCatalog catalog = new SessionCatalog(sessions);

    /** Register an active controller and capture the exact selected session. */
    private SessionTarget select() {
        sessions.active.add(original);
        return SessionTarget.from(catalog.scan());
    }

    /** A token may be equal across different adapter instances returned by a fresh query. */
    @Test public void equalTokenDifferentControllerInstance_revalidates() {
        SessionTarget target = select();
        RecordingController freshAdapter = new RecordingController();
        freshAdapter.token = new String("original-token");
        sessions.active.clear();
        sessions.active.add(freshAdapter);
        assertTrue(catalog.revalidate(target).valid());
    }

    /** Missing token evidence fails closed, even when package and media match. */
    @Test public void missingToken_neverSelectsOrRevalidates() {
        original.token = null;
        sessions.active.add(original);
        assertNull(catalog.scan());
        assertFalse(catalog.revalidate(SessionTarget.from(original.snapshot())).valid());
    }

    /** Two simultaneous PLAYING sessions are ambiguous rather than arbitrarily selected. */
    @Test public void multiplePlayingSessions_noSelection() {
        sessions.active.add(original);
        RecordingController other = new RecordingController();
        other.token = "other-token";
        sessions.active.add(other);
        assertNull(catalog.scan());
    }

    /** The same token under a changed package still fails the original identity check. */
    @Test public void sameTokenPackageChanged_denied() {
        SessionTarget target = select();
        original.packageName = "com.netflix.ninja";
        assertEquals(DeniedReason.PACKAGE_CHANGED, catalog.revalidate(target).reason);
    }

    /** Duplicate token entries make the active inventory ambiguous. */
    @Test public void duplicatedToken_denied() {
        SessionTarget target = select();
        sessions.active.add(new RecordingController());
        assertEquals(DeniedReason.AMBIGUOUS_SESSION, catalog.revalidate(target).reason);
    }

    /** BUFFERING on another app can represent a user switch before PLAYING arrives. */
    @Test public void otherBufferingSession_denied() {
        SessionTarget target = select();
        RecordingController other = new RecordingController();
        other.token = "other-token";
        other.packageName = "com.netflix.ninja";
        other.state = PlaybackStateCodes.STATE_BUFFERING;
        sessions.active.add(other);
        assertEquals(DeniedReason.OTHER_MEDIA_APP_RELEVANT, catalog.revalidate(target).reason);
    }

    /** A lower-priority paused app alone does not replace the original relevant owner. */
    @Test public void lowerPriorityInactiveSession_originalRemainsValid() {
        SessionTarget target = select();
        RecordingController other = new RecordingController();
        other.token = "other-token";
        other.state = PlaybackStateCodes.STATE_PAUSED;
        sessions.active.add(other);
        assertTrue(catalog.revalidate(target).valid());
    }

    /** Actual dispatch refuses PAUSE to an already-paused original session. */
    @Test(expected = IllegalStateException.class)
    public void directPauseOnPausedSession_throwsBeforeCommand() {
        select();
        original.state = PlaybackStateCodes.STATE_PAUSED;
        try {
            catalog.pause();
        } finally {
            assertEquals(0, original.pauseCalls);
        }
    }

    /** Actual dispatch refuses PLAY when the latest state is already PLAYING. */
    @Test(expected = IllegalStateException.class)
    public void directPlayOnPlayingSession_throwsBeforeCommand() {
        select();
        try {
            catalog.play();
        } finally {
            assertEquals(0, original.playCalls);
        }
    }

    /** Once the original disappears, transport cannot use its stale controller object. */
    @Test(expected = IllegalStateException.class)
    public void missingOriginalAtDispatch_neverUsesOldController() {
        select();
        sessions.active.clear();
        try {
            catalog.play();
        } finally {
            assertEquals(0, original.playCalls);
        }
    }
}
