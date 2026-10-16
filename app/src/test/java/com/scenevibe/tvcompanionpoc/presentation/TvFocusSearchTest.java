package com.scenevibe.tvcompanionpoc.presentation;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

/** The frozen spatial rules are tested with two-column and disabled-target examples. */
public final class TvFocusSearchTest {
    /** Constructs a stable geometric target in one logical coordinate space. */
    private TvFocusSearch.Target t(String id, float x, float y, boolean enabled) {
        return new TvFocusSearch.Target(id, x, y, x + 100, y + 50, enabled);
    }

    /** Left honors a same-row neighbor before returning to the active sidebar entry. */
    @Test public void sameRowActionWinsBeforeMenu() {
        TvFocusSearch.Target current = t("cancel", 500, 300, true), left = t("return", 350, 300, true), menu = t("nav-TRACKS", 0, 100, true);
        assertEquals("return", TvFocusSearch.next(current, Arrays.asList(current, left, menu), TvFocusSearch.Direction.LEFT, "nav-TRACKS"));
    }

    /** Left from a content column targets the active section rather than the nearest wrong menu. */
    @Test public void contentLeftReturnsToActiveSection() {
        TvFocusSearch.Target current = t("card", 350, 300, true), active = t("nav-TRACKS", 0, 100, true), other = t("nav-SETTINGS", 0, 300, true);
        assertEquals("nav-TRACKS", TvFocusSearch.next(current, Arrays.asList(current, active, other), TvFocusSearch.Direction.LEFT, "nav-TRACKS"));
    }

    /** Disabled controls cannot receive focus or intercept the route to a working action. */
    @Test public void disabledCandidateIsIgnored() {
        TvFocusSearch.Target current = t("first", 350, 100, true), disabled = t("disabled", 350, 160, false), next = t("next", 350, 250, true);
        assertEquals("next", TvFocusSearch.next(current, Arrays.asList(current, disabled, next), TvFocusSearch.Direction.DOWN, "nav-TRACKS"));
    }

    /** A spatial move with no candidate does not activate a button or invent a destination. */
    @Test public void absentDirectionalTargetLeavesFocusInPlace() {
        TvFocusSearch.Target current = t("first", 350, 100, true);
        assertNull(TvFocusSearch.next(current, Arrays.asList(current), TvFocusSearch.Direction.UP, "nav-HOME"));
    }

    /** A vertically aligned target wins over a nearby item in the other column. */
    @Test public void transverseGapHasTheReferencePenalty() {
        TvFocusSearch.Target current = t("first", 350, 100, true), below = t("below", 350, 240, true), diagonal = t("diagonal", 700, 170, true);
        assertEquals("below", TvFocusSearch.next(current, Arrays.asList(current, below, diagonal), TvFocusSearch.Direction.DOWN, "nav-TRACKS"));
    }
}
