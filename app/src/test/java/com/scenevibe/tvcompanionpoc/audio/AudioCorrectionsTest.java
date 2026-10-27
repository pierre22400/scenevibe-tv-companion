package com.scenevibe.tvcompanionpoc.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.scenevibe.tvcompanionpoc.calendar.MediaCalendar;
import com.scenevibe.tvcompanionpoc.calendar.SceneEvent;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AUDIO 002B targeted-correction unit proofs for the Android-free CORE:
 * <ul>
 *   <li>C1 mapping: {@link DemoAudioTrack#catalogForCalendar(MediaCalendar)} keys AUDIO
 *       cues to the EXACT real event ids and never invents/mutates a TEXT id.</li>
 *   <li>C2 pre-preparation: a pre-prepared (not started) asset is invalidated on Track
 *       change ({@code setCatalog}), eligibility loss, and AUDIO disable.</li>
 *   <li>C3 token scoping of {@code onDue}/{@code onExpire} at the renderer boundary.</li>
 *   <li>C5 guard: no new focus/stream-volume capability reachable from the mapping.</li>
 * </ul>
 */
public final class AudioCorrectionsTest {

    private static final String TOKEN = "video-activation-7";

    // ---- C1 explicit, testable eventId mapping --------------------------------

    /** The mapping produces one AUDIO cue per TEXT event, keyed to the SAME eventId. */
    @Test public void mappingKeysAudioCuesToRealEventIds() {
        List<SceneEvent> source = new ArrayList<>();
        source.add(new SceneEvent("real-1", 5_000L, 6_000L));
        source.add(new SceneEvent("real-2", 45_000L, 0L));        // no window -> default
        source.add(new SceneEvent("real-3", 120_000L, 60_000L));  // max window kept
        MediaCalendar calendar = new MediaCalendar(source, false);

        List<AudioEvent> mapped = DemoAudioTrack.catalogForCalendar(calendar);
        assertEquals(3, mapped.size());
        for (AudioEvent e : mapped) {
            assertEquals(AudioEvent.Type.AUDIO, e.type());
            assertEquals(DemoAudioTrack.CUE_ASSET_REF, e.assetRef());
            assertEquals(DemoAudioTrack.DEMO_GAIN, e.gain(), 0.0f);
        }
        // Keyed to the EXACT real ids (calendar sorts by startMs).
        assertEquals("real-1", mapped.get(0).eventId());
        assertEquals("real-2", mapped.get(1).eventId());
        assertEquals("real-3", mapped.get(2).eventId());
        // startMs preserved verbatim so an onDue at that media instant resolves.
        assertEquals(5_000L, mapped.get(0).startMs());
        // No-window TEXT event mapped to a bounded audible window.
        assertTrue(mapped.get(1).durationMs() > 0L);
    }

    /** The C1 mapping, fed into a renderer, makes a real-id onDue actually start. */
    @Test public void mappedCatalogResolvesRealIdDue() {
        MediaCalendar calendar = new MediaCalendar(
                Collections.singletonList(new SceneEvent("real-9", 5_000L, 6_000L)), false);
        RecordingLocalAudioPort port = new RecordingLocalAudioPort();
        SceneVibeAudioRenderer renderer = new SceneVibeAudioRenderer(port);
        renderer.setCatalog(TOKEN, DemoAudioTrack.catalogForCalendar(calendar));
        renderer.onEligibility(TOKEN, true);
        renderer.onPlayback(TOKEN, true, false);

        renderer.onDue(TOKEN, "real-9");
        assertEquals(1, port.startCount);
        assertEquals("real-9", renderer.activeEventId());
    }

    /** Null/empty calendars map to an empty catalog without throwing. */
    @Test public void mappingHandlesNullAndEmptyDefensively() {
        assertTrue(DemoAudioTrack.catalogForCalendar(null).isEmpty());
        assertTrue(DemoAudioTrack.catalogForEvents(null).isEmpty());
    }

    // ---- C2 pre-preparation invalidation --------------------------------------

    private SceneVibeAudioRenderer prePreparedRenderer(RecordingLocalAudioPort port) {
        SceneVibeAudioRenderer renderer = new SceneVibeAudioRenderer(port);
        renderer.setCatalog(TOKEN, Collections.singletonList(
                AudioEvent.audio("pp-1", "scenevibe_cue", 1_000L, 6_000L, 0.8f)));
        renderer.onEligibility(TOKEN, true);
        renderer.prePrepare("pp-1");
        assertTrue(renderer.isPrepared());
        assertFalse(renderer.isStarted());
        assertEquals(1, port.prepareCount);
        return renderer;
    }

    /** C2: a Track change (new setCatalog) releases a pre-prepared, not-started asset. */
    @Test public void prePrepareInvalidatedOnTrackChange() {
        RecordingLocalAudioPort port = new RecordingLocalAudioPort();
        SceneVibeAudioRenderer renderer = prePreparedRenderer(port);

        renderer.setCatalog("video-activation-8", Collections.singletonList(
                AudioEvent.audio("other", "scenevibe_cue", 2_000L, 6_000L, 0.8f)));

        assertNull(renderer.activeEventId());
        assertFalse(renderer.isPrepared());
        assertTrue(port.stopCount >= 1);
        assertTrue(port.releaseCount >= 1);
    }

    /** C2: eligibility loss releases a pre-prepared, not-started asset. */
    @Test public void prePrepareInvalidatedOnEligibilityLoss() {
        RecordingLocalAudioPort port = new RecordingLocalAudioPort();
        SceneVibeAudioRenderer renderer = prePreparedRenderer(port);

        renderer.onEligibility(TOKEN, false);

        assertNull(renderer.activeEventId());
        assertFalse(renderer.isPrepared());
        assertTrue(port.releaseCount >= 1);
    }

    /** C2: AUDIO disable releases a pre-prepared, not-started asset. */
    @Test public void prePrepareInvalidatedOnAudioDisable() {
        RecordingLocalAudioPort port = new RecordingLocalAudioPort();
        SceneVibeAudioRenderer renderer = prePreparedRenderer(port);

        renderer.setAudioEnabled(false);

        assertNull(renderer.activeEventId());
        assertFalse(renderer.isPrepared());
        assertTrue(port.releaseCount >= 1);
    }

    // ---- C3 token scoping at the renderer boundary ----------------------------

    /** C3: a due with a token != catalogToken is ignored; the matching token works. */
    @Test public void onDueIgnoresStaleTokenButHonorsCurrent() {
        RecordingLocalAudioPort port = new RecordingLocalAudioPort();
        SceneVibeAudioRenderer renderer = new SceneVibeAudioRenderer(port);
        renderer.setCatalog(TOKEN, Collections.singletonList(
                AudioEvent.audio("c1", "scenevibe_cue", 1_000L, 6_000L, 0.8f)));
        renderer.onEligibility(TOKEN, true);
        renderer.onPlayback(TOKEN, true, false);

        renderer.onDue("stale-token", "c1");
        assertEquals(0, port.startCount);
        assertNull(renderer.activeEventId());

        renderer.onDue(TOKEN, "c1");
        assertEquals(1, port.startCount);
        assertEquals("c1", renderer.activeEventId());
    }

    /** C3: a stale-token expire cannot interrupt a comment active under the live token. */
    @Test public void onExpireIgnoresStaleTokenButHonorsCurrent() {
        RecordingLocalAudioPort port = new RecordingLocalAudioPort();
        SceneVibeAudioRenderer renderer = new SceneVibeAudioRenderer(port);
        renderer.setCatalog(TOKEN, Collections.singletonList(
                AudioEvent.audio("c1", "scenevibe_cue", 1_000L, 6_000L, 0.8f)));
        renderer.onEligibility(TOKEN, true);
        renderer.onPlayback(TOKEN, true, false);
        renderer.onDue(TOKEN, "c1");
        assertTrue(renderer.isStarted());

        renderer.onExpire("stale-token", "c1");
        assertTrue(renderer.isStarted());
        assertEquals("c1", renderer.activeEventId());

        renderer.onExpire(TOKEN, "c1");
        assertFalse(renderer.isStarted());
        assertNull(renderer.activeEventId());
    }

    // ---- C5 non-regression: no new focus/volume capability --------------------

    /** The C1 mapping helper introduces no focus/stream-volume/transport capability. */
    @Test public void demoCatalogHelperHasNoFocusOrStreamVolumeCapability() {
        for (Method m : DemoAudioTrack.class.getMethods()) {
            String n = m.getName().toLowerCase();
            assertFalse(n, n.contains("focus"));
            assertFalse(n, n.contains("streamvolume"));
            assertFalse(n, n.contains("duck"));
            assertFalse(n, n.contains("transport"));
        }
    }
}
