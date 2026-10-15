package com.scenevibe.tvcompanionpoc;

import org.junit.Test;
import static org.junit.Assert.*;

/** State reconstruction regressions use lifecycle epochs, never silence as positive evidence. */
public final class NativeForegroundHistory042Test {
    private static final String PRIME = NativeQualificationPolicy.PRIME_PACKAGE;

    /** Supply the explicit lifecycle reset that must precede any usable target resume. */
    private NativeForegroundHistory epoch() {
        NativeForegroundHistory history = new NativeForegroundHistory();
        history.event(27, null, null, 1);
        return history;
    }

    /** A RESUMED remains a state until a transition, irrespective of its age. */
    @Test public void continuousPrimeBeyondThirtySeconds() {
        NativeForegroundHistory h = epoch();
        h.event(1, PRIME, "Player", 1000);
        h.event(7, PRIME, "Player", 120000);
        assertTrue(h.primeAlone());
    }

    /** A lone old resume without an epoch cannot certify truncated retention. */
    @Test public void missingEpochRefuses() {
        NativeForegroundHistory h = new NativeForegroundHistory();
        h.event(1, PRIME, "Player", 1000);
        assertFalse(h.primeAlone());
        assertTrue(h.incomplete());
    }

    /** A real departure invalidates Prime even when the session remains alive. */
    @Test public void primePauseRefuses() {
        NativeForegroundHistory h = epoch();
        h.event(1, PRIME, "Player", 2);
        h.event(2, PRIME, "Player", 3);
        assertFalse(h.primeAlone());
    }

    /** HOME or any different application cannot become a substitute target. */
    @Test public void anotherAppRefuses() {
        NativeForegroundHistory h = epoch();
        h.event(1, PRIME, "Player", 2);
        h.event(2, PRIME, "Player", 3);
        h.event(1, "launcher", "Home", 4);
        assertFalse(h.primeAlone());
    }

    /** Multi-resume is ambiguous even if Prime was the last app resumed. */
    @Test public void multiResumeRefuses() {
        NativeForegroundHistory h = epoch();
        h.event(1, "other", "Other", 2);
        h.event(1, PRIME, "Player", 3);
        assertFalse(h.primeAlone());
        assertEquals(2, h.resumedCount());
    }

    /** Separate Prime activities must not cancel one another at package granularity. */
    @Test public void oldActivityStopDoesNotRemoveNewPlayer() {
        NativeForegroundHistory h = epoch();
        h.event(1, PRIME, "Menu", 2);
        h.event(2, PRIME, "Menu", 3);
        h.event(1, PRIME, "Player", 4);
        h.event(23, PRIME, "Menu", 5);
        assertTrue(h.primeAlone());
    }

    /** Two resumed activities in the same package do not prove one top activity. */
    @Test public void samePackageMultiResumeRefuses() {
        NativeForegroundHistory h = epoch();
        h.event(1, PRIME, "Menu", 2);
        h.event(1, PRIME, "Player", 3);
        assertFalse(h.primeAlone());
    }

    /** Missing public activity identity must not collapse distinct instances. */
    @Test public void missingActivityRefuses() {
        NativeForegroundHistory h = epoch();
        h.event(1, PRIME, null, 2);
        assertFalse(h.primeAlone());
        assertTrue(h.incomplete());
    }

    /** An unmatched departure while an app owns the foreground exposes incomplete history. */
    @Test public void incompleteDepartureRefuses() {
        NativeForegroundHistory h = epoch();
        h.event(1, PRIME, "Player", 2);
        h.event(2, "other", "Unknown", 3);
        assertFalse(h.primeAlone());
    }

    /** Repeated same-class resumes cannot safely distinguish public-API activity instances. */
    @Test public void duplicateResumeRefuses() {
        NativeForegroundHistory h = epoch();
        h.event(1, PRIME, "Player", 2);
        h.event(1, PRIME, "Player", 3);
        assertFalse(h.primeAlone());
    }

    /** Reordered wall timestamps remain invalid even after another lifecycle boundary. */
    @Test public void unorderedEventsRefuse() {
        NativeForegroundHistory h = epoch();
        h.event(1, PRIME, "Player", 3);
        h.event(2, PRIME, "Player", 2);
        h.event(27, null, null, 4);
        h.event(1, PRIME, "Player", 5);
        assertFalse(h.primeAlone());
    }

    /** Screen off, keyguard and runtime boundaries all require a new explicit resume. */
    @Test public void invalidatingBoundariesRefuse() {
        for (int type : new int[] {16, 17, 26, 27}) {
            NativeForegroundHistory h = epoch();
            h.event(1, PRIME, "Player", 2);
            h.event(type, null, null, 3);
            assertFalse(h.primeAlone());
            h.event(15, null, null, 4);
            h.event(18, null, null, 5);
            assertFalse(h.primeAlone());
            h.event(1, PRIME, "Player", 6);
            assertTrue(h.primeAlone());
        }
    }

    /** A coherent departure and return creates new foreground state without a cached grant. */
    @Test public void explicitReturnAdmitted() {
        NativeForegroundHistory h = epoch();
        h.event(1, PRIME, "Player", 2);
        h.event(2, PRIME, "Player", 3);
        h.event(1, "other", "Other", 4);
        h.event(2, "other", "Other", 5);
        h.event(1, PRIME, "Player", 6);
        assertTrue(h.primeAlone());
    }

    /** An unrelated event and even a screen-on alone prove no activity ownership. */
    @Test public void emptyAndUnrelatedRefuse() {
        NativeForegroundHistory h = epoch();
        h.event(15, null, null, 2);
        h.event(7, PRIME, "Player", 3);
        assertFalse(h.primeAlone());
    }
    /** A real first-boot missing launcher resume refuses until a new visible lifecycle epoch. */
    @Test public void firstBootGapNeedsNewEpochAndExplicitResume() {
        NativeForegroundHistory h = epoch();
        h.event(1, "setup", "Setup", 2);
        h.event(2, "launcher", "Launcher", 3);
        h.event(2, "setup", "Setup", 4);
        h.event(1, PRIME, "Player", 5);
        h.event(7, PRIME, "Player", 45000);
        assertFalse(h.primeAlone());
        assertTrue(h.incomplete());
        h.event(16, null, null, 45001);
        h.event(15, null, null, 45002);
        assertFalse(h.primeAlone());
        h.event(1, PRIME, "Player", 45003);
        h.event(7, PRIME, "Player", 90000);
        assertTrue(h.primeAlone());
    }

}
