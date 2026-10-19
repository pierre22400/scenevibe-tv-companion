package com.scenevibe.tvcompanionpoc.audio;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.Test;

import com.scenevibe.tvcompanionpoc.calendar.SceneEvent;

import static org.junit.Assert.*;

/**
 * Guard that the ADDITIVE AUDIO model did not alter the TEXT event contract.
 * The pure-JVM {@code SceneEvent} must keep its exact three-field value surface and
 * identical validation; it must NOT gain any AUDIO field (type, asset, gain,
 * policy). The full byte-for-byte preservation of {@code SceneEvent.java} and
 * {@code ScheduledTrack.java} is additionally enforced by the feature's
 * {@code git diff --stat fdc5f6d} verification step.
 */
public final class TextEventPreservationTest {

    /** SceneEvent still exposes exactly {eventId, startMs, durationMs}. */
    @Test
    public void sceneEventStillHasExactlyThreeFieldsAndNoAudioFields() {
        Field[] fields = SceneEvent.class.getDeclaredFields();
        assertEquals(3, fields.length);
        for (Field field : fields) {
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
            assertTrue(field.getType() == String.class || field.getType() == long.class);
            String n = field.getName().toLowerCase();
            assertFalse(n, n.contains("gain"));
            assertFalse(n, n.contains("asset"));
            assertFalse(n, n.contains("type"));
            assertFalse(n, n.contains("policy"));
        }
    }

    /** SceneEvent validation is unchanged: same bounds and same error message. */
    @Test
    public void sceneEventValidationIsUnchanged() {
        assertEquals("ok", new SceneEvent("ok", 0, 1000).eventId());
        assertEquals(43_200_000L, new SceneEvent("last", 43_200_000L, 60_000).startMs());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new SceneEvent("", 0, 1000));
        assertEquals("Invalid scene event", error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new SceneEvent("id", 43_200_001L, 1));
        assertThrows(IllegalArgumentException.class, () -> new SceneEvent("id", 0, 60_001));
    }

    /** The AUDIO value is a SEPARATE type; it is not assignable to/from SceneEvent. */
    @Test
    public void audioEventIsADistinctAdditiveType() {
        assertNotEquals(SceneEvent.class, AudioEvent.class);
        assertFalse(SceneEvent.class.isAssignableFrom(AudioEvent.class));
        assertFalse(AudioEvent.class.isAssignableFrom(SceneEvent.class));
    }
}
