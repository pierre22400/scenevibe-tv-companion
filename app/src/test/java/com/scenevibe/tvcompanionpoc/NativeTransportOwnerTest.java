package com.scenevibe.tvcompanionpoc;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Independent observations exercise the transport lease and position guards without a TV.
 * Every refusal asserts absence of an accepted PLAY/PAUSE, not just a diagnostic string.
 */
public final class NativeTransportOwnerTest {
    /** Start one lease, then explicitly confirm its stationary pause. */
    private E5TestHarness paused() {
        E5TestHarness h = new E5TestHarness();
        assertTrue(h.owner.requestPause(1, h.clock.now, true));
        h.clock.now += 100;
        h.session.current = h.sample(NativeSessionSnapshot.PAUSED, 300100, h.clock.now, h.clock.now);
        assertEquals(NativeTransportOwner.Result.CONFIRMED, h.owner.confirmPaused(1, h.clock.now));
        return h;
    }

    /** Replace one evidence dimension while retaining every unrelated field. */
    private NativeSessionSnapshot changed(NativeSessionSnapshot s, Object token, String pkg,
            String media, String title, long position, long observed, long updated,
            int state, float speed, boolean pause, boolean play, boolean vod) {
        return new NativeSessionSnapshot(token, pkg, media, title, s.subtitle, s.durationMs,
                position, observed, updated, speed, state, pause, play, vod);
    }

    /** Sending PAUSE does not confirm ownership or authorize PLAY. */
    @Test public void pauseSentIsNotConfirmation() {
        E5TestHarness h = new E5TestHarness();
        assertTrue(h.owner.requestPause(1, h.clock.now, true));
        assertEquals(NativeTransportOwner.Result.WAITING, h.owner.confirmPaused(1, h.clock.now));
        assertFalse(h.owner.requestPlay(1, h.clock.now, true));
        assertEquals(0, h.session.plays);
    }

    /** Already-PAUSED native playback is never appropriated. */
    @Test public void initiallyPausedRefusesWithoutCommand() {
        E5TestHarness h = new E5TestHarness();
        h.session.current = h.sample(NativeSessionSnapshot.PAUSED, 300000, h.clock.now, h.clock.now);
        assertFalse(h.owner.requestPause(1, h.clock.now, true));
        assertEquals(0, h.session.pauses);
    }

    /** Unknown/live content lacks the scoped VOD attestation and is refused. */
    @Test public void liveOrUnconfirmedVodRefuses() {
        E5TestHarness h = new E5TestHarness();
        NativeSessionSnapshot s = h.session.current;
        h.session.current = changed(s, s.token, s.packageName, s.mediaId, s.title, s.positionMs,
                s.observedAtMs, s.updatedAtMs, s.state, s.speed, true, true, false);
        assertFalse(h.owner.requestPause(1, h.clock.now, true));
        assertEquals(0, h.session.pauses);
    }

    /** A duration-zero live/unknown source cannot qualify as VOD even with a checkbox. */
    @Test public void zeroDurationRefuses() {
        E5TestHarness h = new E5TestHarness();
        NativeSessionSnapshot s = h.session.current;
        h.session.current = new NativeSessionSnapshot(s.token, s.packageName, s.mediaId, s.title,
                s.subtitle, 0, 0, s.observedAtMs, s.updatedAtMs, 1, s.state, true, true, true);
        assertFalse(h.owner.requestPause(1, h.clock.now, true));
        assertEquals(0, h.session.pauses);
    }

    /** An ambiguous controller selection is not repaired by picking the first package. */
    @Test public void ambiguousSelectionRefuses() {
        E5TestHarness h = new E5TestHarness();
        h.session.selectable = false;
        assertFalse(h.owner.requestPause(1, h.clock.now, true));
        assertEquals(0, h.session.pauses);
    }

    /** Other native packages are outside this first profile. */
    @Test public void otherAppRefuses() {
        E5TestHarness h = new E5TestHarness();
        NativeSessionSnapshot s = h.session.current;
        h.session.current = changed(s, s.token, "another.app", s.mediaId, s.title, s.positionMs,
                s.observedAtMs, s.updatedAtMs, s.state, s.speed, true, true, true);
        assertFalse(h.owner.requestPause(1, h.clock.now, true));
        assertEquals(0, h.session.pauses);
    }

    /** Missing PAUSE support is refused before dispatch. */
    @Test public void missingActionsRefuses() {
        E5TestHarness h = new E5TestHarness();
        NativeSessionSnapshot s = h.session.current;
        h.session.current = changed(s, s.token, s.packageName, s.mediaId, s.title, s.positionMs,
                s.observedAtMs, s.updatedAtMs, s.state, s.speed, false, false, true);
        assertFalse(h.owner.requestPause(1, h.clock.now, true));
        assertEquals(0, h.session.pauses);
    }

    /** Failed dispatch never becomes an owned lease. */
    @Test public void pauseDispatchRefusalReleasesUnownedLease() {
        E5TestHarness h = new E5TestHarness();
        h.session.pauseAllowed = false;
        assertFalse(h.owner.requestPause(1, h.clock.now, true));
        assertFalse(h.owner.hasLease());
        assertEquals(0, h.session.pauses);
    }

    /** An old provider position is not made fresh merely by a new poll. */
    @Test public void staleProviderRefusesBeforePause() {
        E5TestHarness h = new E5TestHarness();
        h.session.current = h.sample(NativeSessionSnapshot.PLAYING, 300000, h.clock.now,
                h.clock.now - h.policy.providerMaxAgeMs - 1);
        assertFalse(h.owner.requestPause(1, h.clock.now, true));
        assertEquals(0, h.session.pauses);
    }

    /** Future reception timestamps are incoherent evidence. */
    @Test public void futureSampleRefuses() {
        E5TestHarness h = new E5TestHarness();
        h.session.current = h.sample(NativeSessionSnapshot.PLAYING, 300000, h.clock.now + 1,
                h.clock.now);
        assertFalse(h.owner.requestPause(1, h.clock.now, true));
        assertEquals(0, h.session.pauses);
    }

    /** Negative/unavailable positions never grant control. */
    @Test public void unavailablePositionRefuses() {
        E5TestHarness h = new E5TestHarness();
        h.session.current = h.sample(NativeSessionSnapshot.PLAYING, -1, h.clock.now, h.clock.now);
        assertFalse(h.owner.requestPause(1, h.clock.now, true));
        assertEquals(0, h.session.pauses);
    }

    /** The finite confirmation deadline cannot be mistaken for a successful pause. */
    @Test public void pauseNotConfirmedTimesOut() {
        E5TestHarness h = new E5TestHarness();
        assertTrue(h.owner.requestPause(1, h.clock.now, true));
        h.clock.now += h.policy.pauseTimeoutMs;
        h.session.current = h.sample(NativeSessionSnapshot.PLAYING, 303000, h.clock.now, h.clock.now);
        assertEquals(NativeTransportOwner.Result.REFUSED, h.owner.confirmPaused(1, h.clock.now));
        assertEquals(0, h.session.plays);
    }

    /** A replacement token, even in the same package/programme, cannot receive PLAY. */
    @Test public void changedTokenRefusesPlay() {
        E5TestHarness h = paused(); NativeSessionSnapshot s = h.session.current;
        h.session.current = changed(s, new Object(), s.packageName, s.mediaId, s.title, s.positionMs,
                s.observedAtMs, s.updatedAtMs, s.state, s.speed, true, true, true);
        assertFalse(h.owner.requestPlay(1, h.clock.now, true));
        assertEquals(0, h.session.plays);
    }

    /** A different media ID is never repaired through matching titles. */
    @Test public void changedMediaRefusesPlay() {
        E5TestHarness h = paused(); NativeSessionSnapshot s = h.session.current;
        h.session.current = changed(s, s.token, s.packageName, "programme-two", s.title, s.positionMs,
                s.observedAtMs, s.updatedAtMs, s.state, s.speed, true, true, true);
        assertFalse(h.owner.requestPlay(1, h.clock.now, true));
        assertEquals(0, h.session.plays);
    }

    /** Changed episode metadata is refused even if a provider reuses its media ID. */
    @Test public void changedProgrammeMetadataRefuses() {
        E5TestHarness h = paused(); NativeSessionSnapshot s = h.session.current;
        h.session.current = changed(s, s.token, s.packageName, s.mediaId, "Different programme",
                s.positionMs, s.observedAtMs, s.updatedAtMs, s.state, s.speed, true, true, true);
        assertFalse(h.owner.requestPlay(1, h.clock.now, true));
        assertEquals(0, h.session.plays);
    }

    /** The first profile's exact stationary anchor rejects a one-millisecond seek. */
    @Test public void seekOutsideExactAnchorRefusesPlay() {
        E5TestHarness h = paused();
        h.session.current = h.sample(NativeSessionSnapshot.PAUSED, 300101, h.clock.now, h.clock.now);
        assertFalse(h.owner.requestPlay(1, h.clock.now, true));
        assertEquals(0, h.session.plays);
    }

    /** A newer PAUSED update invalidates ownership even when its position returns to the anchor. */
    @Test public void userUpdateWithSamePositionRefuses() {
        E5TestHarness h = paused(); h.clock.now += 1;
        h.session.current = h.sample(NativeSessionSnapshot.PAUSED, 300100, h.clock.now, h.clock.now);
        assertFalse(h.owner.requestPlay(1, h.clock.now, true));
        assertEquals(0, h.session.plays);
    }

    /** Losing the source/foreground evidence forbids commands on a different app. */
    @Test public void appDepartureRefusesPlay() {
        E5TestHarness h = paused(); h.session.observable = false;
        assertFalse(h.owner.requestPlay(1, h.clock.now, true));
        assertEquals(0, h.session.plays);
    }

    /** Stationary data can still become too old to trust after the finite bound. */
    @Test public void pausedProviderEventuallyExpires() {
        E5TestHarness h = paused(); h.clock.now += h.policy.providerMaxAgeMs + 1;
        h.refreshPaused();
        assertFalse(h.owner.requestPlay(1, h.clock.now, true));
        assertEquals(0, h.session.plays);
    }

    /** A second request neither replaces nor duplicates an owned native pause. */
    @Test public void secondLeaseRefused() {
        E5TestHarness h = paused();
        assertFalse(h.owner.requestPause(2, h.clock.now, true));
        assertEquals(1, h.session.pauses);
        assertTrue(h.owner.hasLease());
    }

    /** An error/STOP/timeout is never converted into normal EOF. */
    @Test public void abnormalEndForbidsPlay() {
        E5TestHarness h = paused();
        assertFalse(h.owner.requestPlay(1, h.clock.now, false));
        assertEquals(0, h.session.plays);
    }

    /** A successful guard still requires PLAYING observation after command dispatch. */
    @Test public void nominalPlayRequiresConfirmation() {
        E5TestHarness h = paused();
        assertTrue(h.owner.requestPlay(1, h.clock.now, true));
        assertEquals(1, h.session.plays);
        assertEquals(NativeTransportOwner.Result.WAITING, h.owner.confirmPlaying(1, h.clock.now));
        h.clock.now += 100;
        h.session.current = h.sample(NativeSessionSnapshot.PLAYING, 300200, h.clock.now, h.clock.now);
        assertEquals(NativeTransportOwner.Result.CONFIRMED, h.owner.confirmPlaying(1, h.clock.now));
    }

    /** Duplicate EOF cannot dispatch another native PLAY. */
    @Test public void duplicatePlayRequestRefused() {
        E5TestHarness h = paused();
        assertTrue(h.owner.requestPlay(1, h.clock.now, true));
        assertFalse(h.owner.requestPlay(1, h.clock.now, true));
        assertEquals(1, h.session.plays);
    }

    /** A refused PLAY dispatch remains an error rather than successful resume. */
    @Test public void playDispatchFailureRefuses() {
        E5TestHarness h = paused(); h.session.playAllowed = false;
        assertFalse(h.owner.requestPlay(1, h.clock.now, true));
        assertEquals(0, h.session.plays);
    }

    /** Lack of confirmation remains failure, with no unbounded retry. */
    @Test public void playNotConfirmedRefusesAfterDeadline() {
        E5TestHarness h = paused();
        assertTrue(h.owner.requestPlay(1, h.clock.now, true));
        h.clock.now += h.policy.playingTimeoutMs;
        h.refreshPaused();
        assertEquals(NativeTransportOwner.Result.REFUSED, h.owner.confirmPlaying(1, h.clock.now));
        assertEquals(1, h.session.plays);
    }

    /** Generation-stale calls cannot dispatch to the current lease. */
    @Test public void staleGenerationRefusesPlay() {
        E5TestHarness h = paused();
        assertFalse(h.owner.requestPlay(2, h.clock.now, true));
        assertEquals(0, h.session.plays);
    }

    /** Release is idempotent and never resumes the native player. */
    @Test public void releaseNeverPlaysAndIsIdempotent() {
        E5TestHarness h = paused();
        h.owner.release(1); h.owner.release(1);
        assertFalse(h.owner.hasLease());
        assertEquals(0, h.session.plays);
    }

    /** A newly created owner restores no old lease or command. */
    @Test public void restartRestoresNoLease() {
        E5TestHarness old = paused();
        E5TestHarness restarted = new E5TestHarness();
        assertFalse(restarted.owner.hasLease());
        assertFalse(restarted.owner.requestPlay(1, restarted.clock.now, true));
        assertEquals(0, restarted.session.plays);
        assertTrue(old.owner.hasLease());
    }

    /** A frozen PAUSED update is valid through ten seconds, with newly observed reception. */
    @Test public void frozenProviderAnchorIsAdmissibleForKnownClip() {
        E5TestHarness h = paused(); h.clock.now += 10000; h.refreshPaused();
        assertTrue(h.owner.requestPlay(1, h.clock.now, true));
        assertEquals(1, h.session.plays);
    }

    /** Missing generic titles/IDs are insufficient evidence of an unchanged programme. */
    @Test public void absentIdentityRefuses() {
        E5TestHarness h = new E5TestHarness(); NativeSessionSnapshot s = h.session.current;
        h.session.current = changed(s, s.token, s.packageName, "", "", s.positionMs,
                s.observedAtMs, s.updatedAtMs, s.state, s.speed, true, true, true);
        assertFalse(h.owner.requestPause(1, h.clock.now, true));
        assertEquals(0, h.session.pauses);
    }
}
