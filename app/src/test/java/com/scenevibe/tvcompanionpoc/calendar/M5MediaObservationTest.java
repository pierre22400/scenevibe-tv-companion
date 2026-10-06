package com.scenevibe.tvcompanionpoc.calendar;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Verify exact scalar observations without importing Android playback state.
 * Negative positions and large positive positions remain untouched values.
 */
public final class M5MediaObservationTest {
    /** An observation contains exactly three immutable scalar fields. */
    @Test
    public void immutableSurfaceHasOnlyThreeScalarFields() {
        assertTrue(Modifier.isFinal(MediaObservation.class.getModifiers()));
        assertEquals(3, MediaObservation.class.getDeclaredFields().length);
        for (Field field : MediaObservation.class.getDeclaredFields()) {
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
            assertTrue(field.getType() == boolean.class || field.getType() == long.class);
        }
    }

    /** No boolean combination is rewritten by an identity or player heuristic. */
    @Test
    public void eligibilityAndPlaybackFlagsAreRetainedExactly() {
        for (boolean eligible : new boolean[]{false, true}) {
            for (boolean playing : new boolean[]{false, true}) {
                MediaObservation observation = new MediaObservation(eligible, 12, playing);
                assertEquals(eligible, observation.eligible());
                assertEquals(playing, observation.playing());
            }
        }
    }

    /** All negative sentinels survive without collapsing them to minus one. */
    @Test
    public void negativePositionsRemainExact() {
        for (long position : new long[]{-1, -2000, Long.MIN_VALUE}) {
            assertEquals(position, new MediaObservation(true, position, true).positionMs());
        }
    }

    /** The model neither clamps to a media duration nor estimates elapsed time. */
    @Test
    public void nonNegativePositionsHaveNoClampOrEstimate() {
        for (long position : new long[]{0, 43_200_001L, Long.MAX_VALUE}) {
            assertEquals(position, new MediaObservation(false, position, false).positionMs());
        }
    }
}
