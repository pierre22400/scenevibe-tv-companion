package com.scenevibe.tvcompanionpoc;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Replays the Sony E5-038 observation: a recent PLAYING position with duration -1.
 * Commands never synthesize confirmations; PAUSED, EOF and resumed PLAYING are supplied apart.
 * The same admission test is run unchanged against E5-038 and the corrective candidate.
 */
public final class SonyAdmissionReplay {
    /** Build a content-free snapshot using the Sony timeline and an ephemeral test token. */
    private NativeSessionSnapshot sample(E5TestHarness h, int state, long position,
            long observed, long updated) {
        return new NativeSessionSnapshot(h.session.token, NativeQualificationPolicy.PRIME_PACKAGE,
                "synthetic-vod-id", "", "", -1, position, observed, updated,
                state == NativeSessionSnapshot.PLAYING ? 1f : 0f, state, true, true, true);
    }

    /** A fresh identified VOD with Android's unknown-duration sentinel must reach PAUSE dispatch. */
    @Test public void sonyFreshUnknownDurationReachesPauseDispatch() {
        E5TestHarness h = new E5TestHarness();
        h.clock.now = 18329925;
        h.session.current = sample(h, NativeSessionSnapshot.PLAYING, 2475551,
                18329915, 18329909);
        assertTrue(h.controller.requestLocal(LocalInterludePlan.qualification(true)));
        h.player.ready();
        System.out.println("REPLAY phase=" + h.controller.localPhase() + " reason=" + h.owner.reason()
                + " pauses=" + h.session.pauses + " events=" + h.events);
        assertEquals("Fresh Sony evidence must not be rejected as stale", 1, h.session.pauses);
        assertEquals(SceneRuntimeController.LocalPhase.PAUSE_REQUESTED, h.controller.localPhase());
        assertEquals(0, h.player.starts);
        assertEquals(0, h.session.plays);
    }

}
