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
    @Test public void commitIsOneUse(){var state=prepared();String token=state.begin(100);assertTrue(state.commit(token,200));assertEquals(DeveloperOverlayState.Stage.INTERACTIVE,state.stage());assertFalse(state.commit(token,201));}
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
    /** Construct only a prepared UI state, never an Android/service authority. */
    private DeveloperOverlayState prepared(){var state=new DeveloperOverlayState();state.enable();state.prepared();return state;}
}
