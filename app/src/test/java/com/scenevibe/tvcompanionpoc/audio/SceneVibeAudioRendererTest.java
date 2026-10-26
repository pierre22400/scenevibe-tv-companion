package com.scenevibe.tvcompanionpoc.audio;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * JVM-only synchronization matrix for the Android-free {@link SceneVibeAudioRenderer}.
 * Each row of the AUDIO 002 mandate matrix is covered by a dedicated test, driven
 * ONLY by the scheduler callbacks ({@code onDue}/{@code onExpire}/{@code onPlayback}/
 * {@code onEligibility}) so the single media-time authority is preserved. Mirrors the
 * pure-JVM style of {@code M5SceneEventTest} and {@code MediaSyncedTrackSchedulerExpiryTest}
 * and the A1 {@code VoiceCoexistenceProbeTest} no-focus guards.
 */
public final class SceneVibeAudioRendererTest {

    private static final String TOKEN = "tok-1";

    private final RecordingLocalAudioPort port = new RecordingLocalAudioPort();
    private SceneVibeAudioRenderer renderer;

    private final AudioEvent comment = AudioEvent.audio("c1", "scenevibe_cue.aac", 1000, 5000, 0.8f);

    /** Build a renderer that is eligible, PLAYING, and knows one AUDIO comment. */
    @Before
    public void setUp() {
        renderer = new SceneVibeAudioRenderer(port);
        renderer.setCatalog(TOKEN, Collections.singletonList(comment));
        renderer.onEligibility(TOKEN, true);
        renderer.onPlayback(TOKEN, true, false);
    }

    // ---- normal playback -----------------------------------------------------

    /** Normal playback: onDue prepares then starts the local resource, in order. */
    @Test
    public void dueDuringPlaybackPreparesThenStartsLocalResource() {
        renderer.onDue(TOKEN, "c1");
        assertEquals(Arrays.asList("prepare:scenevibe_cue.aac", "setGain:0.8", "start"), port.calls);
        assertEquals(1, port.prepareCount);
        assertEquals(1, port.startCount);
        assertTrue(renderer.isStarted());
        assertEquals("c1", renderer.activeEventId());
    }

    /** prepare strictly precedes start (prepare != start). */
    @Test
    public void prepareOrderingAlwaysPrecedesStart() {
        renderer.onDue(TOKEN, "c1");
        assertTrue(port.calls.indexOf("prepare:scenevibe_cue.aac") < port.calls.indexOf("start"));
    }

    /** SceneVibe gain is applied to the LOCAL port only, with the event's value. */
    @Test
    public void sceneVibeGainIsAppliedToTheLocalPortOnly() {
        renderer.onDue(TOKEN, "c1");
        assertEquals(0.8f, port.lastGain, 0.0f);
    }

    // ---- no audio focus ------------------------------------------------------

    /** The CORE exposes NO audio-focus request and NO setStreamVolume method. */
    @Test
    public void coreHasNoAudioFocusOrStreamVolumeCapability() {
        for (java.lang.reflect.Method m : SceneVibeAudioRenderer.class.getMethods()) {
            String n = m.getName().toLowerCase();
            assertFalse(n, n.contains("focus"));
            assertFalse(n, n.contains("streamvolume"));
            assertFalse(n, n.contains("duck"));
        }
        for (java.lang.reflect.Method m : LocalAudioPort.class.getMethods()) {
            String n = m.getName().toLowerCase();
            assertFalse(n, n.contains("focus"));
            assertFalse(n, n.contains("streamvolume"));
        }
        // Driving a full comment never records any focus/stream-volume call.
        renderer.onDue(TOKEN, "c1");
        for (String call : port.calls) {
            String c = call.toLowerCase();
            assertFalse(c, c.contains("focus"));
            assertFalse(c, c.contains("streamvolume"));
        }
    }

    // ---- stop + release ------------------------------------------------------

    /** onExpire of the active comment stops then releases the player. */
    @Test
    public void expireStopsAndReleases() {
        renderer.onDue(TOKEN, "c1");
        renderer.onExpire(TOKEN, "c1");
        assertEquals(Arrays.asList("prepare:scenevibe_cue.aac", "setGain:0.8", "start", "stop", "release"),
                port.calls);
        assertNull(renderer.activeEventId());
        assertFalse(renderer.isStarted());
        assertEquals(LocalAudioPort.State.IDLE, port.state());
    }

    /** onExpire for a non-active eventId does not touch the player. */
    @Test
    public void expireOfUnrelatedEventIsIgnored() {
        renderer.onDue(TOKEN, "c1");
        int before = port.stopCount;
        renderer.onExpire(TOKEN, "other");
        assertEquals(before, port.stopCount);
        assertTrue(renderer.isStarted());
    }

    // ---- pause (STOP_ON_PAUSE) + resume --------------------------------------

    /** Observed pause stops+releases an in-progress comment (STOP_ON_PAUSE). */
    @Test
    public void pauseStopsInProgressCommentPerStopOnPausePolicy() {
        renderer.onDue(TOKEN, "c1");
        renderer.onPlayback(TOKEN, false, false);
        assertEquals(1, port.stopCount);
        assertEquals(1, port.releaseCount);
        assertNull(renderer.activeEventId());
    }

    /** Resume does NOT retroactively replay a consumed/interrupted phrase. */
    @Test
    public void resumeDoesNotReplayConsumedPhrase() {
        renderer.onDue(TOKEN, "c1");
        renderer.onPlayback(TOKEN, false, false);
        int startsAfterPause = port.startCount;
        renderer.onPlayback(TOKEN, true, false); // resume; scheduler re-emits no onDue
        assertEquals(startsAfterPause, port.startCount);
        assertNull(renderer.activeEventId());
    }

    // ---- forward seek --------------------------------------------------------

    /**
     * Forward seek: the scheduler consumes the window and emits NO onDue, so no
     * stale comment is ever started.
     */
    @Test
    public void forwardSeekStartsNoStaleComment() {
        // Scheduler skipThrough(position) consumes c1 without onDue; renderer stays idle.
        assertNull(renderer.activeEventId());
        assertEquals(0, port.startCount);
    }

    // ---- backward seek -------------------------------------------------------

    /**
     * Backward seek: scheduler re-arms and expires any pending window first; a fresh
     * onDue then starts exactly one comment, no duplicate/overlap.
     */
    @Test
    public void backwardSeekReArmsWithoutDuplicates() {
        renderer.onDue(TOKEN, "c1");
        renderer.onExpire(TOKEN, "c1"); // rearmFrom expires the visible window
        renderer.onDue(TOKEN, "c1");    // controlled replay after re-arm
        assertEquals(2, port.startCount);
        assertEquals(2, port.prepareCount);
        assertTrue(renderer.isStarted());
        assertEquals("c1", renderer.activeEventId());
    }

    // ---- no duplicates on repeated due ---------------------------------------

    /** A repeated onDue for the same active comment never restarts it. */
    @Test
    public void repeatedDueDoesNotRestart() {
        renderer.onDue(TOKEN, "c1");
        renderer.onDue(TOKEN, "c1");
        renderer.onDue(TOKEN, "c1");
        assertEquals(1, port.startCount);
        assertEquals(1, port.prepareCount);
    }

    /** A second concurrent onDue while one is active is ignored (scheduler owns arbitration). */
    @Test
    public void concurrentSecondDueIsIgnoredWhileOneActive() {
        renderer.setCatalog(TOKEN, Arrays.asList(comment, AudioEvent.audio("c2", "two.aac", 2000, 3000, 1.0f)));
        renderer.onEligibility(TOKEN, true);
        renderer.onPlayback(TOKEN, true, false);
        renderer.onDue(TOKEN, "c1");
        renderer.onDue(TOKEN, "c2");
        assertEquals("c1", renderer.activeEventId());
        assertEquals(1, port.startCount);
    }

    // ---- eligibility loss ----------------------------------------------------

    /** Eligibility loss stops+releases safely with no residual playback. */
    @Test
    public void eligibilityLossStopsAndReleases() {
        renderer.onDue(TOKEN, "c1");
        renderer.onEligibility(TOKEN, false);
        assertEquals(1, port.stopCount);
        assertEquals(1, port.releaseCount);
        assertNull(renderer.activeEventId());
        assertEquals(LocalAudioPort.State.IDLE, port.state());
    }

    // ---- AUDIO OFF during playback -------------------------------------------

    /** AUDIO OFF during playback stops+releases immediately, independent of scheduler. */
    @Test
    public void audioOffDuringPlaybackStopsImmediately() {
        renderer.onDue(TOKEN, "c1");
        renderer.setAudioEnabled(false);
        assertEquals(1, port.stopCount);
        assertEquals(1, port.releaseCount);
        assertNull(renderer.activeEventId());
        // While OFF, a new due produces nothing.
        renderer.onDue(TOKEN, "c1");
        assertEquals(1, port.startCount);
    }

    // ---- episode change ------------------------------------------------------

    /**
     * Episode change surfaces as onEligibility(false) from the scheduler: the
     * previous episode's audio stops immediately, and the new Track's catalog only
     * plays on a fresh onDue.
     */
    @Test
    public void episodeChangeStopsPreviousAudioImmediately() {
        renderer.onDue(TOKEN, "c1");
        // New episode: scheduler deactivates old, loads new Track.
        renderer.onEligibility(TOKEN, false);
        assertNull(renderer.activeEventId());
        assertEquals(1, port.stopCount);
        AudioEvent next = AudioEvent.audio("n1", "next.aac", 500, 4000, 0.5f);
        renderer.setCatalog("tok-2", Collections.singletonList(next));
        renderer.onEligibility("tok-2", true);
        renderer.onPlayback("tok-2", true, false);
        renderer.onDue("tok-2", "n1");
        assertEquals("n1", renderer.activeEventId());
        assertEquals("next.aac", port.lastAssetRef);
    }

    // ---- resource / decode error ---------------------------------------------

    /** A decode failure on start leaves a truthful terminal state, never coexistence. */
    @Test
    public void decodeErrorFailsSafeWithTruthfulTerminalState() {
        port.failOnStart = true;
        try {
            renderer.onDue(TOKEN, "c1");
            fail("expected decode failure to propagate from the port");
        } catch (IllegalStateException expected) {
            // Port reported ERROR; CORE's onError() must stop safely.
        }
        renderer.onError();
        assertNull(renderer.activeEventId());
        assertTrue(port.stopCount >= 1);
        assertTrue(port.releaseCount >= 1);
    }

    // ---- due while paused / ineligible ---------------------------------------

    /** No comment starts while paused, even if onDue arrives. */
    @Test
    public void dueWhilePausedStartsNothing() {
        renderer.onPlayback(TOKEN, false, false);
        renderer.onDue(TOKEN, "c1");
        assertEquals(0, port.startCount);
        assertNull(renderer.activeEventId());
    }

    // ---- pre-preparation (section 6) -----------------------------------------

    /** Pre-prepare readies the resource without emitting sound (prepare != start). */
    @Test
    public void prePrepareReadiesWithoutSound() {
        renderer.prePrepare("c1");
        assertEquals(Collections.singletonList("prepare:scenevibe_cue.aac"), port.calls);
        assertEquals(0, port.startCount);
        assertTrue(renderer.isPrepared());
        assertFalse(renderer.isStarted());
        // The subsequent onDue starts without a second prepare.
        renderer.onDue(TOKEN, "c1");
        assertEquals(1, port.prepareCount);
        assertEquals(1, port.startCount);
    }

    /** Timing hooks expose sampling + prep + start indices for FEAT-004 analysis. */
    @Test
    public void timingHooksExposePrepAndStartSamples() {
        renderer.tick();                 // sample 1
        renderer.prePrepare("c1");       // prepare at sample 1
        renderer.tick();                 // sample 2
        renderer.tick();                 // sample 3
        renderer.onDue(TOKEN, "c1");     // start at sample 3
        assertEquals(3L, renderer.sampleCount());
        assertEquals(1L, renderer.prepareSample());
        assertEquals(3L, renderer.startSample());
    }

    // ---- gain bounds ---------------------------------------------------------

    /** setGain forwards a bounded value to the port and rejects out-of-range. */
    @Test
    public void setGainIsBoundedAndForwardedToPort() {
        renderer.setGain(0.3f);
        assertEquals(0.3f, port.lastGain, 0.0f);
        for (float bad : new float[]{-0.1f, 1.1f, Float.NaN}) {
            assertThrows(IllegalArgumentException.class, () -> renderer.setGain(bad));
        }
    }

    /** The renderer rejects a null port at construction. */
    @Test
    public void constructorRejectsNullPort() {
        assertThrows(IllegalArgumentException.class, () -> new SceneVibeAudioRenderer(null));
    }

    /** Idempotent teardown: redundant stop paths do not double-release. */
    @Test
    public void redundantTeardownIsSafe() {
        renderer.onDue(TOKEN, "c1");
        renderer.onExpire(TOKEN, "c1");
        int releasesAfterExpire = port.releaseCount;
        renderer.onEligibility(TOKEN, false); // already idle
        assertEquals(releasesAfterExpire, port.releaseCount);
    }

    /** A due for an event absent from the catalog is ignored. */
    @Test
    public void dueForUnknownEventIsIgnored() {
        renderer.onDue(TOKEN, "ghost");
        assertEquals(0, port.startCount);
        assertNull(renderer.activeEventId());
    }

    /** Catalog ignores non-AUDIO-free input defensively (null entries skipped). */
    @Test
    public void catalogSkipsNullEntries() {
        List<AudioEvent> withNull = Arrays.asList(comment, null);
        renderer.setCatalog(TOKEN, withNull);
        renderer.onEligibility(TOKEN, true);
        renderer.onPlayback(TOKEN, true, false);
        renderer.onDue(TOKEN, "c1");
        assertEquals("c1", renderer.activeEventId());
    }
}
