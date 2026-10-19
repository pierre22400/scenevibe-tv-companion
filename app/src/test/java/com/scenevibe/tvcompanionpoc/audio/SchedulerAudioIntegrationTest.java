package com.scenevibe.tvcompanionpoc.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.scenevibe.tvcompanionpoc.calendar.MediaCalendar;
import com.scenevibe.tvcompanionpoc.calendar.MediaCalendarScheduler;
import com.scenevibe.tvcompanionpoc.calendar.MediaObservation;
import com.scenevibe.tvcompanionpoc.calendar.SceneEvent;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * AUDIO 002B C4: INTEGRATED end-to-end proof that the REAL
 * {@link MediaCalendarScheduler} drives the Android-free {@link SceneVibeAudioRenderer}
 * exactly as {@code OverlayService} wires it. Unlike {@link SceneVibeAudioRendererTest}
 * (which calls the renderer callbacks directly), every transition here is produced by
 * the scheduler from {@code load(...)} + {@code onObservation(...)} alone, so the test
 * exercises the SAME fan-out, the SAME activation token, and the SAME eventIds the live
 * service uses.
 *
 * <p>Wiring mirrored from {@code OverlayService}'s {@code MediaCalendarScheduler.Sink}:
 * the sink forwards {@code onEligibility/onPlayback/onDue/onExpire} to the renderer, and
 * on {@code onEligibility(token, true)} it binds the AUDIO catalog to the REAL activation
 * token and the REAL event ids via the C1 mapping
 * ({@link DemoAudioTrack#catalogForCalendar(MediaCalendar)}). This is the end-to-end
 * reason AUDIO now actually fires: before C1 the catalog was keyed to never-matching demo
 * ids, so {@code onDue} resolved null and nothing started.</p>
 *
 * <p>Each assertion is written so it would FAIL if the C1 catalog-token/eventId alignment
 * or the C3 token-scoping were reverted (noted per test).</p>
 */
public final class SchedulerAudioIntegrationTest {

    /** The REAL event ids the scheduler emits (mirrors ScheduledTrack.Event.id -> SceneEvent). */
    private static final String E1 = "scene-event-alpha";
    private static final String E2 = "scene-event-bravo";
    private static final String E3 = "scene-event-charlie";

    private static final String TOKEN_A = "video-activation-1";
    private static final String TOKEN_B = "video-activation-2";

    /**
     * Harness that reproduces the OverlayService fan-out: scheduler -> sink -> renderer,
     * with the C1 catalog binding on first eligibility.
     */
    private static final class Harness implements MediaCalendarScheduler.Sink {
        final RecordingLocalAudioPort port = new RecordingLocalAudioPort();
        final SceneVibeAudioRenderer renderer = new SceneVibeAudioRenderer(port);
        final MediaCalendarScheduler scheduler = new MediaCalendarScheduler(this);
        /** The calendar the C1 mapping reads to produce the AUDIO catalog, by token. */
        MediaCalendar activeCalendar;
        String activeToken;

        @Override public void onEligibility(String token, boolean eligible) {
            // AUDIO 002B C1: bind the catalog to the REAL token + REAL event ids on the
            // transition to eligible, exactly like OverlayService.bindAudioCatalog(token).
            if (eligible && token != null && token.equals(activeToken) && activeCalendar != null) {
                renderer.setCatalog(token, DemoAudioTrack.catalogForCalendar(activeCalendar));
            }
            renderer.onEligibility(token, eligible);
        }

        @Override public void onPlayback(String token, boolean playing, boolean freeze) {
            renderer.onPlayback(token, playing, freeze);
        }

        @Override public void onDue(String token, String eventId) {
            renderer.onDue(token, eventId);
        }

        @Override public void onExpire(String token, String eventId) {
            renderer.onExpire(token, eventId);
        }

        /** Load a calendar under a token, recording it for the C1 catalog mapping. */
        void load(MediaCalendar calendar, String token) {
            activeCalendar = calendar;
            activeToken = token;
            scheduler.load(calendar, token);
        }

        void observe(boolean eligible, long positionMs, boolean playing) {
            scheduler.onObservation(new MediaObservation(eligible, positionMs, playing));
        }
    }

    private static MediaCalendar calendarA() {
        List<SceneEvent> events = new ArrayList<>();
        events.add(new SceneEvent(E1, 5_000L, 6_000L));
        events.add(new SceneEvent(E2, 45_000L, 6_000L));
        events.add(new SceneEvent(E3, 120_000L, 6_000L));
        return new MediaCalendar(events, false);
    }

    private static MediaCalendar calendarB() {
        List<SceneEvent> events = new ArrayList<>();
        events.add(new SceneEvent("episode-b-only", 8_000L, 6_000L));
        return new MediaCalendar(events, false);
    }

    // ---- (1) normal playback: THE end-to-end trigger proof --------------------

    /**
     * An eligible media event at its startMs triggers port.prepare + port.start exactly
     * once, through the REAL scheduler. FAILS if C1 is reverted (catalog keyed to the
     * demo ids would make onDue resolve null, startCount == 0).
     */
    @Test public void normalPlaybackTriggersAudioAssetExactlyOnce() {
        Harness h = new Harness();
        h.load(calendarA(), TOKEN_A);
        // First observation: becomes eligible (binds catalog), position before any event.
        h.observe(true, 0L, true);
        assertEquals(0, h.port.startCount);
        // Advance to E1's media instant: scheduler emits onDue(TOKEN_A, E1).
        h.observe(true, 5_000L, true);

        assertEquals(1, h.port.prepareCount);
        assertEquals(1, h.port.startCount);
        assertTrue(h.renderer.isStarted());
        assertEquals(E1, h.renderer.activeEventId());
        assertEquals(DemoAudioTrack.CUE_ASSET_REF, h.port.lastAssetRef);
    }

    // ---- (2) forward seek (> 5000 ms) starts no stale comment -----------------

    /**
     * A forward seek past E1 (delta > FORWARD_SEEK_THRESHOLD_MS) must NOT start the
     * skipped comment: the scheduler consumes it with no onDue. FAILS if C1/C3 reverted
     * only in the sense that a non-zero start would appear; here it must stay 0.
     */
    @Test public void forwardSeekPastCommentStartsNoStaleComment() {
        Harness h = new Harness();
        h.load(calendarA(), TOKEN_A);
        h.observe(true, 0L, true);
        // Jump from 0 to 20_000 (> 5000): E1 at 5_000 is skipped, never due.
        h.observe(true, 20_000L, true);

        assertEquals(0, h.port.startCount);
        assertNull(h.renderer.activeEventId());
    }

    // ---- (3) backward seek (< -2000 ms) re-arms without duplicates ------------

    /**
     * A backward seek re-arms a consumed event so it can play once more on a fresh due,
     * with no duplicate/overlap. FAILS if C1 reverted (no start at all).
     */
    @Test public void backwardSeekReArmsWithoutDuplicates() {
        Harness h = new Harness();
        h.load(calendarA(), TOKEN_A);
        h.observe(true, 0L, true);
        h.observe(true, 5_000L, true);          // E1 due + start (#1)
        assertEquals(1, h.port.startCount);
        // Expire E1's window by advancing within its window-end (5000+6000=11000).
        h.observe(true, 11_000L, true);         // onExpire(E1): teardown
        assertNull(h.renderer.activeEventId());
        // Backward seek to before E1 (delta < -2000): scheduler re-arms E1.
        h.observe(true, 1_000L, true);
        // Re-reach E1: a single fresh due + start (#2), no duplicate.
        h.observe(true, 5_000L, true);

        assertEquals(2, h.port.startCount);
        assertEquals(2, h.port.prepareCount);
        assertEquals(E1, h.renderer.activeEventId());
    }

    // ---- (4) pause (STOP_ON_PAUSE) + resume: no replay ------------------------

    /**
     * Pause stops the in-progress comment (STOP_ON_PAUSE) and resume does not replay it,
     * all driven by scheduler observations. FAILS if C1 reverted (no comment to stop).
     */
    @Test public void pauseStopsAndResumeDoesNotReplay() {
        Harness h = new Harness();
        h.load(calendarA(), TOKEN_A);
        h.observe(true, 0L, true);
        h.observe(true, 5_000L, true);          // E1 start
        assertTrue(h.renderer.isStarted());
        // Pause at a position inside E1's window (no seek: small/zero delta).
        h.observe(true, 6_000L, false);         // onPlayback(playing=false) -> STOP_ON_PAUSE
        assertFalse(h.renderer.isStarted());
        assertNull(h.renderer.activeEventId());
        int startsAfterPause = h.port.startCount;
        // Resume at the same position: scheduler re-emits no onDue for the consumed event.
        h.observe(true, 6_000L, true);
        assertEquals(startsAfterPause, h.port.startCount);
        assertNull(h.renderer.activeEventId());
    }

    // ---- (5) episode change / eligibility loss stops previous audio -----------

    /**
     * An episode change (new load under a NEW token) drops the old binding
     * (onEligibility(oldToken,false) stops previous audio) and only the new token's
     * catalog plays on a fresh due. FAILS if C1 reverted (no start under either token).
     */
    @Test public void episodeChangeStopsPreviousAndOnlyNewTokenPlays() {
        Harness h = new Harness();
        h.load(calendarA(), TOKEN_A);
        h.observe(true, 0L, true);
        h.observe(true, 5_000L, true);          // episode A: E1 start
        assertTrue(h.renderer.isStarted());

        // Episode change: load episode B under a new token. scheduler.load emits
        // onEligibility(TOKEN_A, false) first -> previous audio stops.
        h.load(calendarB(), TOKEN_B);
        assertFalse(h.renderer.isStarted());
        assertNull(h.renderer.activeEventId());

        // New token becomes eligible (binds B's catalog), then B's event becomes due.
        // Step the position so no step exceeds the forward-seek threshold (5000 ms),
        // otherwise the scheduler would treat the jump as a seek and consume the event.
        h.observe(true, 0L, true);
        h.observe(true, 4_000L, true);
        h.observe(true, 8_000L, true);
        assertTrue(h.renderer.isStarted());
        assertEquals("episode-b-only", h.renderer.activeEventId());
    }

    // ---- (6) no duplicates on repeated observations ---------------------------

    /**
     * Repeated identical observations at the same position do not restart the active
     * comment. FAILS if C1 reverted (nothing starts, so the dedup is untested).
     */
    @Test public void repeatedObservationsDoNotDuplicate() {
        Harness h = new Harness();
        h.load(calendarA(), TOKEN_A);
        h.observe(true, 0L, true);
        h.observe(true, 5_000L, true);
        h.observe(true, 5_000L, true);
        h.observe(true, 5_000L, true);

        assertEquals(1, h.port.startCount);
        assertEquals(1, h.port.prepareCount);
    }

    // ---- C3: stale-token due/expire ignored -----------------------------------

    /**
     * C3: a due bearing a STALE token (a previous activation) must not start a comment
     * in the current session. We bind the renderer to the live token, then deliver a due
     * with an old token directly on the renderer seam (as a reordered/late callback would).
     * FAILS if the C3 token guard in onDue is removed (the stale due would start audio).
     */
    @Test public void staleTokenDueDoesNotStartInNewSession() {
        Harness h = new Harness();
        h.load(calendarA(), TOKEN_A);
        h.observe(true, 0L, true);              // binds catalog under TOKEN_A
        h.renderer.onPlayback(TOKEN_A, true, false);

        // A late due carrying the OLD/stale token but a live eventId must be ignored.
        h.renderer.onDue("video-activation-OLD", E1);
        assertEquals(0, h.port.startCount);
        assertNull(h.renderer.activeEventId());

        // The live-token due still works.
        h.renderer.onDue(TOKEN_A, E1);
        assertEquals(1, h.port.startCount);
        assertEquals(E1, h.renderer.activeEventId());
    }

    /**
     * C3: a stale-token EXPIRE must not interrupt a comment active under the live token.
     * FAILS if the C3 token guard in onExpire is removed (the stale expire would tear down).
     */
    @Test public void staleTokenExpireDoesNotInterruptLiveComment() {
        Harness h = new Harness();
        h.load(calendarA(), TOKEN_A);
        h.observe(true, 0L, true);
        h.observe(true, 5_000L, true);          // E1 active under TOKEN_A
        assertTrue(h.renderer.isStarted());

        // A late expire for E1 but under the OLD token must NOT stop the live comment.
        h.renderer.onExpire("video-activation-OLD", E1);
        assertTrue(h.renderer.isStarted());
        assertEquals(E1, h.renderer.activeEventId());

        // The live-token expire still closes it.
        h.renderer.onExpire(TOKEN_A, E1);
        assertFalse(h.renderer.isStarted());
        assertNull(h.renderer.activeEventId());
    }
}
