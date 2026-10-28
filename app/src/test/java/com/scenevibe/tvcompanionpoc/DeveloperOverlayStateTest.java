package com.scenevibe.tvcompanionpoc;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pure UI handoff transitions: none grants a media permit or advances a track. */
public final class DeveloperOverlayStateTest {
    /** Mode activation alone cannot make a window interactive. */
    @Test public void enableDoesNotOpen(){var state=new DeveloperOverlayState();assertEquals(DeveloperOverlayState.Stage.OFF,state.stage());state.enable();assertEquals(DeveloperOverlayState.Stage.ENABLED,state.stage());assertNull(state.begin(100));}
    /** Passive preparation is an explicit separate stage. */
    @Test public void preparedCanBeginOnlyOneLaunch(){var state=prepared();String token=state.begin(100);assertNotNull(token);assertNull(state.begin(101));assertEquals(DeveloperOverlayState.Stage.HANDOFF,state.stage());}
    /** A successful window update can consume its ticket once. */
    @Test public void commitIsOneUse(){var state=prepared();String token=state.begin(100);assertTrue(state.commit(token,200));assertEquals(DeveloperOverlayState.Stage.MONITORING,state.stage());assertFalse(state.commit(token,201));}
    /** A different or null ticket never promotes input. */
    @Test public void unrelatedTicketCannotShow(){var state=prepared();state.begin(100);assertFalse(state.commit("wrong",200));assertFalse(state.commit(null,200));}
    /** Expiry only returns to passive; it never schedules an automatic show. */
    @Test public void timeoutNeverTakesFocus(){var state=prepared();String token=state.begin(100);assertFalse(state.commit(token,15100));assertEquals(DeveloperOverlayState.Stage.PREPARED,state.stage());}
    /** Failure leaves the same prepared capability available for another explicit action. */
    @Test public void canceledLaunchCanRetry(){var state=prepared();String first=state.begin(100);state.cancel(first);String next=state.begin(200);assertNotEquals(first,next);assertFalse(state.commit(first,201));assertTrue(state.commit(next,202));}
    /** Back/cross closes the UI while keeping the mode enabled. */
    @Test public void closeIsNotDisable(){var state=prepared();state.closed();assertTrue(state.enabled());assertEquals(DeveloperOverlayState.Stage.ENABLED,state.stage());}
    /** Disabling revokes a launch that is still in progress. */
    @Test public void disableRevokesPendingHandoff(){var state=prepared();String token=state.begin(100);state.disable();assertFalse(state.commit(token,101));assertFalse(state.enabled());}
    /** Clock rollback cannot revive a stale handoff. */
    @Test public void invalidMonotonicTimeIsRefused(){var state=prepared();assertNull(state.begin(-1));assertNull(state.begin(Long.MAX_VALUE));String token=state.begin(100);assertFalse(state.commit(token,99));}
    /** Parking keeps the mode and future explicit preparation, not a global reopening shortcut. */
    @Test public void parkingCancelsAnEarlierHandoff(){var state=prepared();String token=state.begin(100);state.prepared();assertFalse(state.commit(token,101));assertEquals(DeveloperOverlayState.Stage.PREPARED,state.stage());}
    /** The safe normal path cannot silently inherit the explicit interactive experiment. */
    @Test public void interactionRequiresItsOwnExplicitLaunch(){var state=prepared();String first=state.begin(100,true);assertTrue(state.focusRequested());assertTrue(state.commit(first,200));assertEquals(DeveloperOverlayState.Stage.INTERACTIVE,state.stage());state.closed();state.prepared();String second=state.begin(300);assertFalse(state.focusRequested());assertTrue(state.commit(second,400));assertEquals(DeveloperOverlayState.Stage.MONITORING,state.stage());}
    /** Cancelled/expired focus choices are revoked rather than persisted as a preference. */
    @Test public void cancellationAndExpiryRevokeFocus(){var state=prepared();String token=state.begin(100,true);state.cancel(token);assertFalse(state.focusRequested());state.begin(200,true);state.expire(15200);assertFalse(state.focusRequested());assertEquals(DeveloperOverlayState.Stage.PREPARED,state.stage());}
    /** A compatible player may make controls visible, without acquiring any input authority by default. */
    @Test public void playerReadinessDoesNotImplyInteractivity(){var state=prepared();String token=state.begin(100);state.waitForPlayer(token,200);assertTrue(state.awaitingPlayer(300));assertFalse(state.focusRequested());state.shown();assertEquals(DeveloperOverlayState.Stage.MONITORING,state.stage());}
    /** Explicit interactive intent is bounded to this player wait and consumed at presentation. */
    @Test public void explicitPlayerExperimentRemainsOneUse(){var state=prepared();String token=state.begin(100,true);state.waitForPlayer(token,200);assertTrue(state.focusRequested());state.shown();assertEquals(DeveloperOverlayState.Stage.INTERACTIVE,state.stage());assertFalse(state.focusRequested());state.closed();assertEquals(DeveloperOverlayState.Stage.ENABLED,state.stage());}
    /** Construct only a prepared UI state, never an Android/service authority. */
    private DeveloperOverlayState prepared(){var state=new DeveloperOverlayState();state.enable();state.prepared();return state;}
}
