package com.scenevibe.tvcompanionpoc;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Replays the Sony E5-038 observation: a recent PLAYING position with duration -1.
 * Commands never synthesize confirmations; PAUSED, EOF and resumed PLAYING are supplied apart.
 * The same admission test is run unchanged against E5-038 and the corrective candidate.
 */
public final class SonyStalePosition039Test {
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
        assertEquals("Fresh Sony evidence must not be rejected as stale", 1, h.session.pauses);
        assertEquals(SceneRuntimeController.LocalPhase.PAUSE_REQUESTED, h.controller.localPhase());
        assertEquals(0, h.player.starts);
        assertEquals(0, h.session.plays);
    }

    /** Exercise the full real controller; each native confirmation is independent of dispatch. */
    @Test public void unknownDurationCompletesOnlyAfterBothConfirmations() {
        E5TestHarness h = new E5TestHarness();
        h.session.current = sample(h, 3, 300000, h.clock.now, h.clock.now);
        h.requestAndReady();
        h.controller.tickLocal();
        assertEquals(0, h.player.starts);
        h.clock.now += 100;
        long pausedAt = h.clock.now;
        h.session.current = sample(h, 2, 300100, h.clock.now, pausedAt);
        h.controller.tickLocal();
        assertEquals(SceneRuntimeController.LocalPhase.PLAYER_PLAYING, h.controller.localPhase());
        h.clock.now += 10000;
        h.session.current = sample(h, 2, 300100, h.clock.now, pausedAt);
        h.player.state = LocalVideoPort.State.STOPPED;
        h.player.callbacks.eof(h.player.generation);
        assertEquals(1, h.session.plays);
        assertEquals(SceneRuntimeController.LocalPhase.RESUME_REQUESTED, h.controller.localPhase());
        h.clock.now += 100;
        h.session.current = sample(h, 3, 300200, h.clock.now, h.clock.now);
        h.controller.tickLocal();
        assertEquals(SceneRuntimeController.LocalPhase.SUCCESS, h.controller.localPhase());
        assertFalse(h.owner.hasLease());
    }

    /** Fail a proposed initial observation and prove neither transport command was accepted. */
    private void refused(E5TestHarness h, NativeSessionSnapshot s) {
        h.session.current = s;
        h.requestAndReady();
        assertEquals(SceneRuntimeController.LocalPhase.REFUSED, h.controller.localPhase());
        assertEquals(0, h.session.pauses);
        assertEquals(0, h.session.plays);
        assertFalse(h.owner.hasLease());
    }

    /** New polling never rejuvenates a genuinely old provider timestamp. */
    @Test public void oldProviderStillRefused() {
        E5TestHarness h = new E5TestHarness();
        refused(h, sample(h, 3, 300000, h.clock.now, h.clock.now - 20001));
        assertEquals(NativeTransportOwner.Reason.STALE_POSITION, h.owner.reason());
    }

    /** The independent 1000ms observation budget is retained. */
    @Test public void oldObservationStillRefused() {
        E5TestHarness h = new E5TestHarness();
        refused(h, sample(h, 3, 300000, h.clock.now - 1001, h.clock.now - 1001));
    }

    /** A missing raw position is never synthesized from timestamps. */
    @Test public void absentPositionStillRefused() {
        E5TestHarness h = new E5TestHarness();
        refused(h, sample(h, 3, -1, h.clock.now, h.clock.now));
    }

    /** Wall-clock milliseconds, nanoseconds and seconds cannot be silently converted to uptime. */
    @Test public void incompatibleClockBasesStillRefused() {
        for (long updated : new long[] {1792010976860L, 18329909000000L, 18, 0, 100001}) {
            E5TestHarness h = new E5TestHarness();
            refused(h, sample(h, 3, 300000, h.clock.now, updated));
        }
    }

    /** Future observations remain incoherent, even with a recent provider update. */
    @Test public void futureObservationStillRefused() {
        E5TestHarness h = new E5TestHarness();
        refused(h, sample(h, 3, 300000, h.clock.now + 1, h.clock.now));
    }

    /** Unknown duration needs an exact native ID; title-only and empty identities do not qualify. */
    @Test public void unknownDurationWithoutNativeIdRefused() {
        E5TestHarness h = new E5TestHarness();
        NativeSessionSnapshot s = sample(h, 3, 300000, h.clock.now, h.clock.now);
        refused(h, new NativeSessionSnapshot(s.token, s.packageName, "", "Specific title", "",
                -1, s.positionMs, s.observedAtMs, s.updatedAtMs, 1, 3, true, true, true));
    }

    /** Zero/missing and other invalid negative durations retain conservative rejection. */
    @Test public void invalidDurationsStillRefused() {
        for (long duration : new long[] {0, -2}) {
            E5TestHarness h = new E5TestHarness();
            NativeSessionSnapshot s = sample(h, 3, 300000, h.clock.now, h.clock.now);
            refused(h, new NativeSessionSnapshot(s.token, s.packageName, s.mediaId, "", "",
                    duration, s.positionMs, s.observedAtMs, s.updatedAtMs, 1, 3, true, true, true));
        }
    }

    /** No unknown-duration admission is possible without explicit VOD attestation or a token. */
    @Test public void insufficientTargetEvidenceRefused() {
        for (boolean vod : new boolean[] {false, true}) {
            E5TestHarness h = new E5TestHarness();
            NativeSessionSnapshot s = sample(h, 3, 300000, h.clock.now, h.clock.now);
            refused(h, new NativeSessionSnapshot(vod ? null : s.token, s.packageName, s.mediaId,
                    "", "", -1, s.positionMs, s.observedAtMs, s.updatedAtMs, 1, 3, true, true, vod));
        }
    }

    /** A session replacement between PAUSE dispatch and observation never starts the interlude. */
    @Test public void changedSessionDuringPauseRefused() {
        E5TestHarness h = new E5TestHarness();
        h.session.current = sample(h, 3, 300000, h.clock.now, h.clock.now);
        h.requestAndReady();
        h.clock.now += 100;
        NativeSessionSnapshot s = sample(h, 2, 300100, h.clock.now, h.clock.now);
        h.session.current = new NativeSessionSnapshot(new Object(), s.packageName, s.mediaId,
                "", "", -1, s.positionMs, s.observedAtMs, s.updatedAtMs, 0, 2, true, true, true);
        h.controller.tickLocal();
        assertEquals(SceneRuntimeController.LocalPhase.RECOVERY_REQUIRED, h.controller.localPhase());
        assertEquals(0, h.player.starts);
        assertEquals(0, h.session.plays);
    }

    /** The raw PAUSED anchor and provider update remain exact throughout an unknown-duration lease. */
    @Test public void unknownDurationSeekOrManualUpdateForbidsPlay() {
        for (int change = 0; change < 3; change++) {
            E5TestHarness h = new E5TestHarness();
            h.session.current = sample(h, 3, 300000, h.clock.now, h.clock.now);
            assertTrue(h.owner.requestPause(1, h.clock.now, true));
            h.clock.now += 100;
            long pausedAt = h.clock.now;
            h.session.current = sample(h, 2, 300100, h.clock.now, pausedAt);
            assertEquals(NativeTransportOwner.Result.CONFIRMED, h.owner.confirmPaused(1, h.clock.now));
            h.clock.now += change == 2 ? 20001 : 10000;
            h.session.current = sample(h, 2, change == 0 ? 300101 : 300100,
                    h.clock.now, change == 1 ? pausedAt + 1 : pausedAt);
            assertFalse(h.owner.requestPlay(1, h.clock.now, true));
            assertEquals(0, h.session.plays);
        }
    }

    /** Known-duration end-of-media and arithmetic overflow still refuse before PAUSE. */
    @Test public void positionBoundsRetained() {
        E5TestHarness h = new E5TestHarness();
        refused(h, h.sample(3, 3000000, h.clock.now, h.clock.now));
        h = new E5TestHarness();
        refused(h, sample(h, 3, Long.MAX_VALUE, h.clock.now, h.clock.now));
    }

    /** A previous denial is not reported as the reason of a newly prepared attempt. */
    @Test public void newAttemptResetsRetiredReasonOnly() {
        E5TestHarness h = new E5TestHarness();
        refused(h, sample(h, 3, -1, h.clock.now, h.clock.now));
        assertEquals(NativeTransportOwner.Reason.STALE_POSITION, h.owner.reason());
        h.session.current = sample(h, 3, 300000, h.clock.now, h.clock.now);
        assertTrue(h.controller.requestLocal(LocalInterludePlan.qualification(true)));
        assertEquals(NativeTransportOwner.Reason.NONE, h.owner.reason());
        h.player.ready();
        h.owner.beginAttempt();
        assertTrue(h.owner.hasLease());
        assertEquals(1, h.session.pauses);
    }
}
