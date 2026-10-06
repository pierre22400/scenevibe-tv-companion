package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.calendar.MediaCalendar;
import com.scenevibe.tvcompanionpoc.calendar.SceneEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Test-only projection of an already constructed Video track into temporal values.
 * It reads no JSON, identity, manifest, store or Cloud state. The product payload
 * remains in ScheduledTrack; no production caller can access this test source.
 */
final class M5VideoTestProjection {
    /** Copy only exact temporal fields and the existing pause policy. */
    static MediaCalendar project(ScheduledTrack track) {
        List<SceneEvent> events = new ArrayList<>();
        for (ScheduledTrack.Event event : track.comments) {
            events.add(new SceneEvent(event.id, event.startMs, event.durationMs));
        }
        return new MediaCalendar(events, track.pauseFreezesDisplay);
    }

    /** Prevent construction of this stateless test helper. */
    private M5VideoTestProjection() {}
}
