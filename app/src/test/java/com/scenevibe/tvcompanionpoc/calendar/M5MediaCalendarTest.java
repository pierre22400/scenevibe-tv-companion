package com.scenevibe.tvcompanionpoc.calendar;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Verify a bounded owned calendar, including equal-start order and exact identity.
 * These tests never acquire media, restore storage or render a scene.
 */
public final class M5MediaCalendarTest {
    /** A calendar has only its immutable sequence and supplied pause policy. */
    @Test
    public void immutableSurfaceHasOnlyTwoValueFields() {
        assertTrue(Modifier.isFinal(MediaCalendar.class.getModifiers()));
        assertEquals(2, MediaCalendar.class.getDeclaredFields().length);
        for (Field field : MediaCalendar.class.getDeclaredFields()) {
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
            assertTrue(field.getType() == List.class || field.getType() == boolean.class);
        }
    }

    /** Mutation of the caller's collection cannot replace or reorder owned values. */
    @Test
    public void inputListIsDefensivelyCopied() {
        SceneEvent first = new SceneEvent("first", 10, 1000);
        List<SceneEvent> input = new ArrayList<>(Collections.singletonList(first));
        MediaCalendar calendar = new MediaCalendar(input, true);
        input.clear();
        input.add(new SceneEvent("different", 0, 1000));
        assertEquals(1, calendar.events().size());
        assertSame(first, calendar.events().get(0));
    }

    /** All public mutation routes of the owned list remain closed. */
    @Test
    public void outputListAndIteratorCannotMutateCalendar() {
        MediaCalendar calendar = new MediaCalendar(Collections.singletonList(new SceneEvent("id", 0, 1)), true);
        assertThrows(UnsupportedOperationException.class, () -> calendar.events().clear());
        assertThrows(UnsupportedOperationException.class, () -> calendar.events().set(0, new SceneEvent("other", 0, 1)));
        java.util.Iterator<SceneEvent> iterator = calendar.events().iterator();
        iterator.next();
        assertThrows(UnsupportedOperationException.class, iterator::remove);
    }

    /** Equal starts preserve input order rather than sorting opaque identifiers. */
    @Test
    public void stableSortKeepsEqualStartsInTheirOriginalOrder() {
        SceneEvent z = new SceneEvent("z", 20, 1000);
        SceneEvent a = new SceneEvent("a", 20, 1000);
        SceneEvent earlier = new SceneEvent("earlier", 0, 1000);
        MediaCalendar calendar = new MediaCalendar(Arrays.asList(z, earlier, a), false);
        assertEquals(Arrays.asList(earlier, z, a), calendar.events());
    }

    /** Duplicate exact identifiers fail even when their timestamps differ. */
    @Test
    public void duplicateIdentifiersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MediaCalendar(Arrays.asList(
                new SceneEvent("id", 0, 1000), new SceneEvent("id", 100, 1000)), false));
    }

    /** Similar-looking IDs remain distinct when the actual String values differ. */
    @Test
    public void identifierUniquenessDoesNotNormalizeUnicodeCaseOrWhitespace() {
        List<SceneEvent> events = new ArrayList<>();
        for (String id : new String[]{"é", "e\u0301", "id", "ID", " id ", "œ", "’", "🙂"}) {
            events.add(new SceneEvent(id, 0, 1000));
        }
        assertEquals(events, new MediaCalendar(events, true).events());
    }

    /** Null sequences, empty sequences and null elements are not valid calendars. */
    @Test
    public void absentOrIncompleteCalendarsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MediaCalendar(null, false));
        assertThrows(IllegalArgumentException.class, () -> new MediaCalendar(Collections.emptyList(), false));
        assertThrows(IllegalArgumentException.class, () -> new MediaCalendar(Arrays.asList(
                new SceneEvent("id", 0, 1), null), false));
    }

    /** The inclusive cardinality ceiling prevents unbounded runtime collections. */
    @Test
    public void oneAnd256EventsPassBut257Fails() {
        List<SceneEvent> events = new ArrayList<>();
        for (int index = 0; index < 256; index++) {
            events.add(new SceneEvent("id-" + index, index, 1000));
        }
        assertEquals(1, new MediaCalendar(events.subList(0, 1), false).events().size());
        assertEquals(256, new MediaCalendar(events, true).events().size());
        events.add(new SceneEvent("excess", 256, 1000));
        assertThrows(IllegalArgumentException.class, () -> new MediaCalendar(events, false));
    }

    /** Both existing pause policies are retained without a capability or clock. */
    @Test
    public void pausePolicyIsExact() {
        List<SceneEvent> events = Collections.singletonList(new SceneEvent("id", 0, 1000));
        assertTrue(new MediaCalendar(events, true).freezeOnPause());
        assertFalse(new MediaCalendar(events, false).freezeOnPause());
    }
}
