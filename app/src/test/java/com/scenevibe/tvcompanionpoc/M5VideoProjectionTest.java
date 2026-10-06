package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.calendar.MediaCalendar;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Prove test-only projection and isolated oracle ownership, without live installation. */
public final class M5VideoProjectionTest {
    /** Make an already constructed Video state; no transport or parser is consulted. */
    private ScheduledTrack track(boolean freeze) {
        return new ScheduledTrack("synthetic", "com.amazon.amazonvideo.livingroom",
                new ScheduledTrack.MediaIdentity("prime_video", "fixture", "Columbo", 100_000),
                Arrays.asList(new ScheduledTrack.Event("later", "payload stays outside", 3000, 60000, null),
                        new ScheduledTrack.Event("é", "outside", 1000, 0, null),
                        new ScheduledTrack.Event("e\u0301", "outside", 1000, -1, null)), freeze);
    }

    /** Preserve every exact temporal field and the legacy stable order at equality. */
    @Test
    public void temporalProjectionIsExact() {
        ScheduledTrack input = track(true);
        MediaCalendar actual = M5VideoTestProjection.project(input);
        assertEquals(input.comments.size(), actual.events().size());
        for (int i = 0; i < input.comments.size(); i++) {
            assertEquals(input.comments.get(i).id, actual.events().get(i).eventId());
            assertEquals(input.comments.get(i).startMs, actual.events().get(i).startMs());
            assertEquals(input.comments.get(i).durationMs, actual.events().get(i).durationMs());
        }
        assertEquals("é", actual.events().get(0).eventId());
        assertEquals("e\u0301", actual.events().get(1).eventId());
    }

    /** Map both qualified legacy pause policies without inventing a clock. */
    @Test
    public void pausePolicyIsRetained() {
        assertTrue(M5VideoTestProjection.project(track(true)).freezeOnPause());
        assertFalse(M5VideoTestProjection.project(track(false)).freezeOnPause());
    }

    /** Projection creates independent pure elements and does not mutate Video state. */
    @Test
    public void projectionOwnsOnlyPureValues() {
        ScheduledTrack input = track(true);
        MediaCalendar first = M5VideoTestProjection.project(input);
        MediaCalendar second = M5VideoTestProjection.project(input);
        assertNotSame(first.events().get(0), second.events().get(0));
        assertEquals("outside", input.comments.get(0).text);
        assertEquals("é", input.comments.get(0).id);
    }

    /** All six oracle source classes must load separately from production classes. */
    @Test
    public void oracleCannotFallBackToLiveProductionClasses() throws Exception {
        assertTrue(M5FrozenLegacyOracle.isIsolated());
    }
}
