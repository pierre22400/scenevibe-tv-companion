package com.scenevibe.tvcompanionpoc.audio;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pure-JVM boundary test for the ADDITIVE AUDIO value, in the style of
 * {@code M5SceneEventTest}. Proves the mandated fields and bounded validation
 * without inventing a scheduler.
 */
public final class AudioEventTest {

    /** The value is final, immutable, and carries exactly the mandated fields. */
    @Test
    public void immutableSurfaceHasExactlyTheMandatedFields() {
        assertTrue(Modifier.isFinal(AudioEvent.class.getModifiers()));
        assertEquals(7, AudioEvent.class.getDeclaredFields().length);
        for (Field field : AudioEvent.class.getDeclaredFields()) {
            assertTrue(field.getName(), Modifier.isPrivate(field.getModifiers()));
            assertTrue(field.getName(), Modifier.isFinal(field.getModifiers()));
        }
    }

    /** A valid event round-trips every field verbatim. */
    @Test
    public void validEventExposesEveryFieldVerbatim() {
        AudioEvent event = new AudioEvent("e1", AudioEvent.Type.AUDIO, "scenevibe_cue.aac",
                1000, 5000, 0.75f, AudioEvent.PausePolicy.STOP_ON_PAUSE);
        assertEquals("e1", event.eventId());
        assertEquals(AudioEvent.Type.AUDIO, event.type());
        assertEquals("scenevibe_cue.aac", event.assetRef());
        assertEquals(1000, event.startMs());
        assertEquals(5000, event.durationMs());
        assertEquals(0.75f, event.gain(), 0.0f);
        assertEquals(AudioEvent.PausePolicy.STOP_ON_PAUSE, event.pausePolicy());
    }

    /** The convenience factory defaults to AUDIO + STOP_ON_PAUSE. */
    @Test
    public void factoryDefaultsToAudioTypeAndStopOnPause() {
        AudioEvent event = AudioEvent.audio("e1", "cue.aac", 0, 1000, 1.0f);
        assertEquals(AudioEvent.Type.AUDIO, event.type());
        assertEquals(AudioEvent.PausePolicy.STOP_ON_PAUSE, event.pausePolicy());
    }

    /** Null/empty/oversized identifiers are rejected with a content-free error. */
    @Test
    public void identifiersAreValidatedLikeSceneEvent() {
        for (String id : new String[]{null, "", "a".repeat(129)}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> AudioEvent.audio(id, "cue.aac", 0, 1000, 1.0f));
            assertEquals("Invalid audio event", error.getMessage());
        }
        assertEquals(128, AudioEvent.audio("a".repeat(128), "cue.aac", 0, 1, 1.0f)
                .eventId().length());
    }

    /** The asset reference must be a non-empty, bounded, opaque local id. */
    @Test
    public void assetReferenceIsBoundedAndRequired() {
        for (String ref : new String[]{null, "", "a".repeat(257)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> AudioEvent.audio("e1", ref, 0, 1000, 1.0f));
        }
        assertEquals(256, AudioEvent.audio("e1", "a".repeat(256), 0, 1, 1.0f)
                .assetRef().length());
    }

    /** Start/duration bounds match SceneEvent exactly. */
    @Test
    public void temporalBoundsMirrorSceneEvent() {
        assertEquals(0, AudioEvent.audio("e", "r", 0, 1000, 1.0f).startMs());
        assertEquals(43_200_000L, AudioEvent.audio("e", "r", 43_200_000L, 60_000, 1.0f).startMs());
        for (long start : new long[]{-1, 43_200_001L, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> AudioEvent.audio("e", "r", start, 1000, 1.0f));
        }
        for (long duration : new long[]{60_001, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> AudioEvent.audio("e", "r", 0, duration, 1.0f));
        }
    }

    /** Gain is bounded to [0.0, 1.0] and rejects NaN. */
    @Test
    public void gainIsBoundedUnitIntervalAndRejectsNaN() {
        assertEquals(0.0f, AudioEvent.audio("e", "r", 0, 1, 0.0f).gain(), 0.0f);
        assertEquals(1.0f, AudioEvent.audio("e", "r", 0, 1, 1.0f).gain(), 0.0f);
        for (float gain : new float[]{-0.01f, 1.01f, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> AudioEvent.audio("e", "r", 0, 1, gain));
        }
    }

    /** Null type or pause policy is rejected. */
    @Test
    public void nullTypeOrPolicyIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new AudioEvent("e", null, "r", 0, 1, 1.0f, AudioEvent.PausePolicy.STOP_ON_PAUSE));
        assertThrows(IllegalArgumentException.class,
                () -> new AudioEvent("e", AudioEvent.Type.AUDIO, "r", 0, 1, 1.0f, null));
    }
}
