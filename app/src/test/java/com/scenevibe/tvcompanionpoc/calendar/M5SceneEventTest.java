package com.scenevibe.tvcompanionpoc.calendar;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Prove the identifier and temporal boundaries rather than inventing a scheduler.
 * Synthetic Unicode identifiers are values, not commentary or rendering payloads.
 */
public final class M5SceneEventTest {
    /** A value has exactly three private final fields and cannot be subclassed. */
    @Test
    public void immutableSurfaceHasOnlyThreeValueFields() {
        assertTrue(Modifier.isFinal(SceneEvent.class.getModifiers()));
        assertEquals(3, SceneEvent.class.getDeclaredFields().length);
        for (Field field : SceneEvent.class.getDeclaredFields()) {
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
            assertTrue(field.getType() == String.class || field.getType() == long.class);
        }
    }

    /** Null and empty identifiers are rejected, with a fixed content-free error. */
    @Test
    public void missingIdentifiersAreRejected() {
        for (String id : new String[]{null, ""}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> new SceneEvent(id, 0, 1000));
            assertEquals("Invalid scene event", error.getMessage());
            assertNull(error.getCause());
        }
    }

    /** The identifier limit is measured in UTF-16 units, not Unicode code points. */
    @Test
    public void identifierCeilingIsInclusiveAndCountsSurrogates() {
        assertEquals(128, new SceneEvent("a".repeat(128), 0, 1).eventId().length());
        assertEquals(128, new SceneEvent("🙂".repeat(64), 0, 1).eventId().length());
        assertThrows(IllegalArgumentException.class, () -> new SceneEvent("a".repeat(129), 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SceneEvent("🙂".repeat(65), 0, 1));
    }

    /** Whitespace, case, accents and supplementary characters survive verbatim. */
    @Test
    public void identifiersAreNeverTrimmedOrNormalized() {
        for (String id : new String[]{" ", " A\t", "é", "e\u0301", "œ", "’", "🙂"}) {
            assertEquals(id, new SceneEvent(id, 0, 1000).eventId());
        }
        assertNotEquals(new SceneEvent("é", 0, 1000).eventId(),
                new SceneEvent("e\u0301", 0, 1000).eventId());
    }

    /** Both legal start endpoints remain usable without extending the domain. */
    @Test
    public void startBoundsAreExact() {
        assertEquals(0, new SceneEvent("zero", 0, 1000).startMs());
        assertEquals(43_200_000L, new SceneEvent("last", 43_200_000L, 60_000).startMs());
        for (long start : new long[]{-1, Long.MIN_VALUE, 43_200_001L, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new SceneEvent("id", start, 1000));
        }
    }

    /** Positive duration accepts the ceiling and rejects overflow without repair. */
    @Test
    public void positiveDurationCeilingIsExact() {
        assertEquals(1, new SceneEvent("tiny", 0, 1).durationMs());
        assertEquals(60_000, new SceneEvent("max", 0, 60_000).durationMs());
        for (long duration : new long[]{60_001, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new SceneEvent("id", 0, duration));
        }
    }

    /** Historical no-window values are retained, including the long minimum. */
    @Test
    public void nonPositiveDurationsAreRepresentableWithoutDefaults() {
        for (long duration : new long[]{0, -1, -60_000, Long.MIN_VALUE}) {
            assertEquals(duration, new SceneEvent("id", 0, duration).durationMs());
        }
    }
}
