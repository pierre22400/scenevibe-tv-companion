package com.scenevibe.tvcompanionpoc;

import android.media.session.PlaybackState;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Deterministic JVM coverage for the media-position-driven Case B scene expiry added to
 * {@link MediaSyncedTrackScheduler} (review Issue 1/3). The scheduler is the SOLE temporal
 * authority: it renders a comment at {@code startMs} and fires {@link
 * MediaSyncedTrackScheduler.Listener#onExpire} once the MediaSession position reaches
 * {@code startMs + durationMs}. No wall-clock timer is involved, so a pause (position does not
 * advance) freezes the window and a backward seek before the window re-arms the comment. These
 * tests drive the scheduler with raw {@link MediaSessionProbe.Snapshot}s only.
 */
public final class MediaSyncedTrackSchedulerExpiryTest {

    private static final class Listener implements MediaSyncedTrackScheduler.Listener {
        final List<String> rendered = new ArrayList<>();
        final List<String> expired = new ArrayList<>();
        final List<Boolean> eligibility = new ArrayList<>();
        @Override public void onRender(ScheduledTrack.Event event) { rendered.add(event.id); }
        @Override public void onPlayback(boolean playing, boolean freeze) {}
        @Override public void onEligibility(boolean eligible) { eligibility.add(eligible); }
        @Override public void onExpire(ScheduledTrack.Event event) { expired.add(event.id); }
    }

    private static ScheduledTrack track() {
        List<ScheduledTrack.Event> comments = new ArrayList<>();
        // c1 window [1000, 7000), c2 window [12000, 18000).
        comments.add(new ScheduledTrack.Event("c1", "Hello", 1_000L, 6_000L, null));
        comments.add(new ScheduledTrack.Event("c2", "World", 12_000L, 6_000L, null));
        return new ScheduledTrack(
                "track-1",
                "com.amazon.amazonvideo.livingroom",
                new ScheduledTrack.MediaIdentity(
                        "prime_video",
                        "amzn1.dv.gti.baecadcc-c3ef-42a3-bce0-c241adaa992e",
                        "Columbo — Eaux troubles",
                        5_884_768L),
                comments,
                true);
    }

    private static MediaSessionProbe.Snapshot playing(long position) {
        return snapshot(PlaybackState.STATE_PLAYING, "PLAYING", position);
    }

    private static MediaSessionProbe.Snapshot paused(long position) {
        return snapshot(PlaybackState.STATE_PAUSED, "PAUSED", position);
    }

    private static MediaSessionProbe.Snapshot snapshot(int state, String stateName, long position) {
        return new MediaSessionProbe.Snapshot(
                "com.amazon.amazonvideo.livingroom",
                state,
                stateName,
                position,
                position,
                1f,
                0L,
                "amzn1.dv.gti.baecadcc-c3ef-42a3-bce0-c241adaa992e",
                "Columbo",
                "Eaux troubles",
                5_884_768L);
    }

    /** A scene is expired once media position reaches startMs+durationMs, not before. */
    @Test public void sceneExpiresWhenMediaWindowElapses() {
        Listener listener = new Listener();
        MediaSyncedTrackScheduler scheduler = new MediaSyncedTrackScheduler(listener);
        scheduler.load(track());

        scheduler.onPlaybackSnapshot(playing(1_000L)); // anchor + render c1
        assertEquals(1, listener.rendered.size());
        assertEquals("c1", listener.rendered.get(0));
        assertTrue("not yet elapsed", listener.expired.isEmpty());

        scheduler.onPlaybackSnapshot(playing(5_000L)); // still inside window
        assertTrue("still inside window must not expire", listener.expired.isEmpty());

        scheduler.onPlaybackSnapshot(playing(7_000L)); // window end reached
        assertEquals(1, listener.expired.size());
        assertEquals("c1", listener.expired.get(0));
    }

    /** A pause with position frozen must NOT drain the window; resume keeps it coherent. */
    @Test public void pauseFreezeDoesNotExpireSceneWhilePaused() {
        Listener listener = new Listener();
        MediaSyncedTrackScheduler scheduler = new MediaSyncedTrackScheduler(listener);
        scheduler.load(track());

        scheduler.onPlaybackSnapshot(playing(1_000L)); // render c1
        assertEquals(1, listener.rendered.size());

        // Paused: position does not advance; many frozen snapshots must not expire the scene.
        scheduler.onPlaybackSnapshot(paused(4_000L));
        scheduler.onPlaybackSnapshot(paused(4_000L));
        scheduler.onPlaybackSnapshot(paused(4_000L));
        assertTrue("a frozen (paused) scene must not expire by wall time", listener.expired.isEmpty());

        // Resume still inside the window: still no expiry.
        scheduler.onPlaybackSnapshot(playing(4_000L));
        assertTrue(listener.expired.isEmpty());

        // Advance to the window end after resume: now it expires.
        scheduler.onPlaybackSnapshot(playing(7_000L));
        assertEquals(1, listener.expired.size());
        assertEquals("c1", listener.expired.get(0));
    }

    /** A forward seek past the window end expires the scene just as normal playback would. */
    @Test public void forwardSeekPastWindowEndExpiresScene() {
        Listener listener = new Listener();
        MediaSyncedTrackScheduler scheduler = new MediaSyncedTrackScheduler(listener);
        scheduler.load(track());

        scheduler.onPlaybackSnapshot(playing(1_000L)); // render c1
        assertEquals(1, listener.rendered.size());

        // Jump well past c1's window end (and before c2 start). c1 must expire; c2 not yet due.
        scheduler.onPlaybackSnapshot(playing(9_000L));
        assertTrue(listener.expired.contains("c1"));
        assertFalse(listener.rendered.contains("c2"));
    }

    /** A backward seek before a rendered scene's start re-arms it; it re-renders on replay. */
    @Test public void backwardSeekBeforeStartReArmsScene() {
        Listener listener = new Listener();
        MediaSyncedTrackScheduler scheduler = new MediaSyncedTrackScheduler(listener);
        scheduler.load(track());

        scheduler.onPlaybackSnapshot(playing(1_000L)); // render c1
        assertEquals(1, listener.rendered.size());
        scheduler.onPlaybackSnapshot(playing(4_000L)); // advance inside window
        assertTrue(listener.expired.isEmpty());

        // Seek back before c1 start (delta < -BACKWARD_SEEK_THRESHOLD_MS): the
        // currently shown scene must hide immediately, then its comment re-arms.
        scheduler.onPlaybackSnapshot(playing(0L));
        assertEquals("seek before start hides the old scene", 1, listener.expired.size());
        assertEquals("c1", listener.expired.get(0));

        // Replay forward: c1 renders again and gets a fresh expiry at its window end.
        scheduler.onPlaybackSnapshot(playing(1_000L));
        assertEquals("c1 re-rendered after re-arm", 2, listener.rendered.size());
        scheduler.onPlaybackSnapshot(playing(7_000L));
        assertEquals(2, listener.expired.size());
        assertEquals("c1", listener.expired.get(1));
        assertEquals("c1", listener.expired.get(0));
    }

    /** Eligibility loss (identity mismatch) drops any pending window; no late onExpire fires. */
    @Test public void eligibilityLossDropsPendingWindowWithoutLateExpire() {
        Listener listener = new Listener();
        MediaSyncedTrackScheduler scheduler = new MediaSyncedTrackScheduler(listener);
        scheduler.load(track());

        scheduler.onPlaybackSnapshot(playing(1_000L)); // render c1, window armed
        assertEquals(1, listener.rendered.size());

        // A different platform snapshot makes the identity mismatch => eligibility loss.
        MediaSessionProbe.Snapshot foreign = new MediaSessionProbe.Snapshot(
                "com.netflix.ninja", PlaybackState.STATE_PLAYING, "PLAYING",
                5_000L, 5_000L, 1f, 0L, "", "Columbo", "Eaux troubles", 5_884_768L);
        scheduler.onPlaybackSnapshot(foreign);
        assertEquals(Boolean.FALSE, listener.eligibility.get(listener.eligibility.size() - 1));

        // The eligibility loss already hid the scene; no onExpire must fire afterwards.
        assertTrue("eligibility loss must not also fire a late onExpire", listener.expired.isEmpty());
    }

    /** A non-positive duration comment has no media window and never auto-expires here. */
    @Test public void nonPositiveDurationHasNoWindow() {
        Listener listener = new Listener();
        MediaSyncedTrackScheduler scheduler = new MediaSyncedTrackScheduler(listener);
        List<ScheduledTrack.Event> comments = new ArrayList<>();
        comments.add(new ScheduledTrack.Event("c1", "Hello", 1_000L, 0L, null));
        scheduler.load(new ScheduledTrack(
                "track-1", "com.amazon.amazonvideo.livingroom",
                new ScheduledTrack.MediaIdentity("prime_video",
                        "amzn1.dv.gti.baecadcc-c3ef-42a3-bce0-c241adaa992e",
                        "Columbo — Eaux troubles", 5_884_768L),
                comments, true));

        scheduler.onPlaybackSnapshot(playing(1_000L));
        assertEquals(1, listener.rendered.size());
        scheduler.onPlaybackSnapshot(playing(50_000L));
        assertTrue("zero-duration comment is never auto-expired here", listener.expired.isEmpty());
    }
}
