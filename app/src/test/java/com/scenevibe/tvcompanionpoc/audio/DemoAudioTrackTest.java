package com.scenevibe.tvcompanionpoc.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-JVM boundary tests for the LOCAL demonstration AUDIO track fixture. The demo
 * is a dev fixture only (NOT a Cloud contract), so this mirrors the value-type test
 * style of {@code AudioEventTest} and runs with a plain JDK + JUnit even when the
 * Android SDK is unavailable.
 */
public final class DemoAudioTrackTest {

    @Test public void defaultEpisodeHasThreeBundledCueEventsAcrossTimeline() {
        DemoAudioTrack track = DemoAudioTrack.defaultEpisode();
        List<AudioEvent> events = track.events();
        assertEquals(3, events.size());
        long previous = -1L;
        for (AudioEvent event : events) {
            assertEquals(AudioEvent.Type.AUDIO, event.type());
            // Every demo asset references the single bundled ADTS/AAC cue.
            assertEquals(DemoAudioTrack.CUE_ASSET_REF, event.assetRef());
            // Spread across the timeline (strictly increasing start positions).
            assertTrue(event.startMs() > previous);
            previous = event.startMs();
            // Gain stays a LOCAL value in range.
            assertTrue(event.gain() >= 0.0f && event.gain() <= 1.0f);
        }
    }

    @Test public void twoDistinctDemoTokensAllowEpisodeChange() {
        assertNotEquals(DemoAudioTrack.DEMO_TOKEN, DemoAudioTrack.DEMO_TOKEN_EPISODE_B);
    }

    @Test public void eventsListIsImmutableCopy() {
        List<AudioEvent> source = new ArrayList<>();
        source.add(AudioEvent.audio("e1", DemoAudioTrack.CUE_ASSET_REF, 1_000L, 2_000L, 0.5f));
        DemoAudioTrack track = new DemoAudioTrack("demo-local", "episode-a", 60_000L, source);
        // Mutating the source after construction must not change the track.
        source.clear();
        assertEquals(1, track.events().size());
        assertThrows(UnsupportedOperationException.class,
                () -> track.events().add(track.events().get(0)));
    }

    @Test public void rejectsInvalidConstruction() {
        List<AudioEvent> ok = new ArrayList<>();
        assertThrows(IllegalArgumentException.class,
                () -> new DemoAudioTrack("", "v", 1L, ok));
        assertThrows(IllegalArgumentException.class,
                () -> new DemoAudioTrack("p", "", 1L, ok));
        assertThrows(IllegalArgumentException.class,
                () -> new DemoAudioTrack("p", "v", 0L, ok));
        assertThrows(IllegalArgumentException.class,
                () -> new DemoAudioTrack("p", "v", 1L, null));
    }

    /**
     * The demo catalog loaded into the renderer drives real due/start transitions,
     * demonstrating the fixture exercises the CORE end to end without any Cloud path.
     */
    @Test public void demoCatalogDrivesRendererDueAndStart() {
        RecordingLocalAudioPort port = new RecordingLocalAudioPort();
        SceneVibeAudioRenderer renderer = new SceneVibeAudioRenderer(port);
        DemoAudioTrack track = DemoAudioTrack.defaultEpisode();
        renderer.setCatalog(DemoAudioTrack.DEMO_TOKEN, track.events());

        renderer.onEligibility(DemoAudioTrack.DEMO_TOKEN, true);
        renderer.onPlayback(DemoAudioTrack.DEMO_TOKEN, true, false);
        AudioEvent first = track.events().get(0);
        renderer.onDue(DemoAudioTrack.DEMO_TOKEN, first.eventId());

        assertTrue(renderer.isStarted());
        assertSame(first.eventId(), renderer.activeEventId());
        assertEquals(1, port.startCount);
        // prepare precedes start.
        assertEquals("prepare:" + DemoAudioTrack.CUE_ASSET_REF, port.calls.get(0));

        // Episode change: eligibility loss on the old token stops the previous audio.
        renderer.onEligibility(DemoAudioTrack.DEMO_TOKEN, false);
        assertFalse(renderer.isStarted());
        assertTrue(port.stopCount >= 1 && port.releaseCount >= 1);
    }
}
