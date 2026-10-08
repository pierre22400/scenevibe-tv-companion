package com.scenevibe.tvcompanionpoc.mediaexperiment;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.OperatorAction;
import org.junit.Test;
import static org.junit.Assert.*;

/** Verify ADB dispatch is opt-in, explicit, bounded, and default fail-closed. */
public final class OperatorActionTest {
    @Test public void missingOrUnknownNeverStartsMedia() {
        assertEquals(OperatorAction.NONE, OperatorAction.parse(null));
        assertEquals(OperatorAction.NONE, OperatorAction.parse(""));
        assertEquals(OperatorAction.NONE, OperatorAction.parse("play"));
        assertEquals(OperatorAction.NONE, OperatorAction.parse("voice "));
        assertEquals(OperatorAction.NONE, OperatorAction.parse("VOICE"));
        assertEquals(OperatorAction.NONE, OperatorAction.parse("resume"));
        assertEquals(OperatorAction.NONE, OperatorAction.parse("stop;play"));
    }

    @Test public void exactCommandsResolveIndividually() {
        for (OperatorAction action : OperatorAction.values()) {
            if (action == OperatorAction.NONE) continue;
            assertSame(action, OperatorAction.parse(action.wireName()));
        }
    }

    @Test public void onlyStopAndHideAreEmergency() {
        assertTrue(OperatorAction.STOP.isEmergency());
        assertTrue(OperatorAction.HIDE.isEmergency());
        for (OperatorAction action : OperatorAction.values()) {
            if (action != OperatorAction.STOP && action != OperatorAction.HIDE) {
                assertFalse(action.isEmergency());
            }
        }
    }
}
