package com.scenevibe.tvcompanionpoc;

import org.junit.Test;
import static org.junit.Assert.*;

/** No visibility/request is represented as granted keyboard focus. */
public final class DeveloperFocusStateTest {
    /** Only a native callback can confirm a requested interactive window. */
    @Test public void requestIsNotGrant(){var f=new DeveloperFocusState();f.passive();assertEquals(DeveloperFocusState.State.PASSIVE,f.state());f.request();assertEquals(DeveloperFocusState.State.INTERACTIVE_REQUESTED,f.state());f.gained();assertEquals(DeveloperFocusState.State.INTERACTIVE_CONFIRMED,f.state());}
    /** Release is a separate transition, not an immediate fictional CLOSED state. */
    @Test public void releaseRequiresEvidence(){var f=new DeveloperFocusState();f.request();f.gained();f.releasing();assertEquals(DeveloperFocusState.State.RELEASING,f.state());f.released();assertEquals(DeveloperFocusState.State.CLOSED,f.state());}
    /** Unsolicited focus and unexpected loss cannot leave an invisible interactive state. */
    @Test public void passiveDoesNotClaimUnsolicitedGrant(){var f=new DeveloperFocusState();f.passive();f.gained();assertEquals(DeveloperFocusState.State.PASSIVE,f.state());f.request();f.gained();f.lost();assertEquals(DeveloperFocusState.State.PASSIVE,f.state());}
}
