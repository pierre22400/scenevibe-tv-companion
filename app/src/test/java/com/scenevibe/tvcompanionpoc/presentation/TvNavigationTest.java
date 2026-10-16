package com.scenevibe.tvcompanionpoc.presentation;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Exercises visit identity, Back and focus/scroll recovery independently of a device. */
public final class TvNavigationTest {
    /** A launcher visit has no implicit Track and lets Android exit on Back. */
    @Test public void rootDoesNotInventAnOfferOrTrapBack() {
        TvNavigation n = new TvNavigation();
        assertEquals(TvNavigation.Screen.HOME, n.current().screen);
        assertNull(n.current().offerId); assertFalse(n.back());
    }

    /** Returning from details retains the exact second card and the prior scroll offset. */
    @Test public void detailBackRestoresCardAndScroll() {
        TvNavigation n = new TvNavigation(); n.section(TvNavigation.Screen.TRACKS);
        n.remember("card-second", 712); n.push(TvNavigation.Screen.TRACK, "second");
        n.remember("track-primary", 55); n.push(TvNavigation.Screen.PREPARE, "second");
        assertTrue(n.back()); assertEquals("track-primary", n.current().focus);
        assertTrue(n.back()); assertEquals("card-second", n.current().focus); assertEquals(712, n.current().scroll);
    }

    /** A fresh push does not reuse an old visit's excerpt or focus. */
    @Test public void reopeningRouteStartsFresh() {
        TvNavigation n = new TvNavigation(); n.push(TvNavigation.Screen.TRACK, "a");
        n.remember("preview-detail", 400); n.current().preview = "detail"; n.back();
        n.push(TvNavigation.Screen.TRACK, "a");
        assertNull(n.current().focus); assertNull(n.current().preview); assertEquals(0, n.current().scroll);
    }

    /** Home receives the menu entry actually used to open Diagnostics. */
    @Test public void diagnosticEntryMemoryIsDistinctFromSelectedSection() {
        TvNavigation n = new TvNavigation(); n.section(TvNavigation.Screen.DIAGNOSTICS);
        assertEquals(TvNavigation.Screen.DIAGNOSTICS, n.activeSection()); n.back();
        assertEquals("nav-DIAGNOSTICS", n.current().focus); assertEquals(TvNavigation.Screen.HOME, n.activeSection());
    }

    /** Explicit return to Tracks preserves its scroll while targeting the consulted offer. */
    @Test public void returnToTracksRetainsItsActualVisit() {
        TvNavigation n = new TvNavigation(); n.section(TvNavigation.Screen.TRACKS); n.remember("card-b", 800);
        n.push(TvNavigation.Screen.TRACK, "b"); n.tracks("b");
        assertEquals(800, n.current().scroll); assertEquals("card-b", n.current().focus); assertEquals(2, n.visits().size());
    }

    /** A home shortcut retains the actual entry section rather than inventing a Tracks visit. */
    @Test public void homeShortcutRetainsTheHomeSection() {
        TvNavigation n = new TvNavigation(); n.push(TvNavigation.Screen.TRACK, "a");
        assertEquals(TvNavigation.Screen.HOME, n.activeSection());
    }

    /** Missing references cannot select the first Track by accident. */
    @Test(expected = IllegalArgumentException.class) public void missingTrackIsRejected() {
        new TvNavigation().push(TvNavigation.Screen.TRACK, null);
    }

    /** Preparation also requires an explicit reference. */
    @Test(expected = IllegalArgumentException.class) public void emptyPreparationReferenceIsRejected() {
        new TvNavigation().push(TvNavigation.Screen.PREPARE, " ");
    }

    /** Activity recreation accepts only a stack rooted at the launcher home surface. */
    @Test public void malformedRestoreCannotReplaceRoot() {
        TvNavigation n = new TvNavigation();
        n.restore(Arrays.asList(new TvNavigation.Visit(TvNavigation.Screen.SETTINGS, null)));
        assertEquals(TvNavigation.Screen.HOME, n.current().screen);
        n.remember("nav-TRACKS", -300); assertEquals(0, n.current().scroll);
    }
}
