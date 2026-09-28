package com.scenevibe.tvcompanionpoc;

import android.media.session.PlaybackState;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Locks the physical Sony failure: a FinalTrack must never follow another episode or app. */
public final class MediaSyncedTrackSchedulerIdentityTest {
    private static final class Listener implements MediaSyncedTrackScheduler.Listener {
        int renders;
        final List<Boolean> eligibility = new ArrayList<>();
        @Override public void onRender(ScheduledTrack.Event event) { renders++; }
        @Override public void onPlayback(boolean playing, boolean freeze) {}
        @Override public void onEligibility(boolean eligible) { eligibility.add(eligible); }
    }

    private static ScheduledTrack track() {
        List<ScheduledTrack.Event> comments = new ArrayList<>();
        comments.add(new ScheduledTrack.Event("c1","Hello",1_000L,6_000L,null));
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

    private static MediaSessionProbe.Snapshot snapshot(
            String pkg, String mediaId, String title, String subtitle, long duration, long position) {
        return new MediaSessionProbe.Snapshot(
                pkg,
                PlaybackState.STATE_PLAYING,
                "PLAYING",
                position,
                position,
                1f,
                0L,
                mediaId,
                title,
                subtitle,
                duration);
    }

    @Test public void wrongPrimeEpisodeNeverRendersButCorrectEpisodeDoes() {
        Listener listener = new Listener();
        MediaSyncedTrackScheduler scheduler = new MediaSyncedTrackScheduler(listener);
        scheduler.load(track());

        scheduler.onPlaybackSnapshot(snapshot(
                "com.amazon.amazonvideo.livingroom","",
                "Columbo","Le Chant du cygne",5_900_000L,1_000L));
        assertEquals(0,listener.renders);

        scheduler.onPlaybackSnapshot(snapshot(
                "com.amazon.amazonvideo.livingroom","",
                "Columbo","Eaux troubles",5_884_768L,1_000L));
        assertEquals(1,listener.renders);
        assertTrue(listener.eligibility.contains(Boolean.TRUE));
    }

    @Test public void switchingPlatformClearsEligibilityAndCannotKeepFrozenCardAlive() {
        Listener listener = new Listener();
        MediaSyncedTrackScheduler scheduler = new MediaSyncedTrackScheduler(listener);
        scheduler.load(track());
        scheduler.onPlaybackSnapshot(snapshot(
                "com.amazon.amazonvideo.livingroom","",
                "Columbo","Eaux troubles",5_884_768L,1_000L));
        assertEquals(1,listener.renders);

        scheduler.onPlaybackSnapshot(snapshot(
                "com.netflix.ninja","",
                "Columbo","Eaux troubles",5_884_768L,1_500L));
        assertEquals(Boolean.FALSE,listener.eligibility.get(listener.eligibility.size()-1));

        scheduler.onPlaybackUnavailable();
        assertEquals(1,listener.renders);
    }

    @Test public void genericSeriesTitleAloneFailsClosed() {
        assertFalse(MediaIdentityMatcher.matches(track(), snapshot(
                "com.amazon.amazonvideo.livingroom","",
                "Columbo","",5_884_768L,1_000L)));
    }

    @Test public void exactPublishedMediaIdIsStrongestIdentity() {
        assertTrue(MediaIdentityMatcher.matches(track(), snapshot(
                "com.amazon.amazonvideo.livingroom",
                "amzn1.dv.gti.baecadcc-c3ef-42a3-bce0-c241adaa992e",
                "","",-1L,1_000L)));
    }

    @Test public void titleMatchWithWildlyDifferentDurationFailsClosed() {
        assertFalse(MediaIdentityMatcher.matches(track(), snapshot(
                "com.amazon.amazonvideo.livingroom","",
                "Columbo","Eaux troubles",5_000_000L,1_000L)));
    }
}
