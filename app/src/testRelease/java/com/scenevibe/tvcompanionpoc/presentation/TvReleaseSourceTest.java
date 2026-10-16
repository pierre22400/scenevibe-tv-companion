package com.scenevibe.tvcompanionpoc.presentation;

import org.junit.Test;
import static org.junit.Assert.*;

/** Release's presentation source is closed until an independently authorized Cloud adapter exists. */
public final class TvReleaseSourceTest {
    /** No fixture, scenario or inferred ready state is available in the release source set. */
    @Test public void releaseHasNoDemoOrSelectionCapability() {
        TvPresentationSource source = TvSourceFactory.create(null);
        assertFalse(source.isLocalDemo()); assertTrue(source.offers().isEmpty());
        assertTrue(source.scenarioNames().isEmpty()); assertEquals(-1, source.scenarioIndex());
        assertFalse(source.preparation("missing").readyFor("missing"));
        assertFalse(source.preparation("missing").mediaAdmittedFor("missing"));
        source.cancel("missing"); assertTrue(source.offers().isEmpty());
        try { source.select("missing"); fail("No release selection port"); } catch (IllegalStateException expected) { }
        try { source.chooseScenario(0); fail("No release simulation"); } catch (IllegalStateException expected) { }
    }
}
