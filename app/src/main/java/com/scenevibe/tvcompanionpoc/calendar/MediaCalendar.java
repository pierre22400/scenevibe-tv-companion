package com.scenevibe.tvcompanionpoc.calendar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bounded immutable calendar derived from an already validated installation.
 * Stable sorting preserves the supplied order at equal starts. This value owns
 * no consumed state, active windows, media identity, revision or persistence.
 */
public final class MediaCalendar {
    private final List<SceneEvent> events;
    private final boolean freezeOnPause;

    /** Copy and validate the event list before a stable ascending temporal sort. */
    public MediaCalendar(List<SceneEvent> events, boolean freezeOnPause) {
        if (events == null || events.isEmpty() || events.size() > 256) {
            throw new IllegalArgumentException("Invalid media calendar");
        }
        ArrayList<SceneEvent> owned = new ArrayList<>(events);
        if (owned.isEmpty() || owned.size() > 256) {
            throw new IllegalArgumentException("Invalid media calendar");
        }
        Set<String> identifiers = new HashSet<>();
        for (SceneEvent event : owned) {
            if (event == null || !identifiers.add(event.eventId())) {
                throw new IllegalArgumentException("Invalid media calendar");
            }
        }
        owned.sort(Comparator.comparingLong(SceneEvent::startMs));
        this.events = Collections.unmodifiableList(owned);
        this.freezeOnPause = freezeOnPause;
    }

    /** Expose the owned read-only sequence; SceneEvent elements are immutable too. */
    public List<SceneEvent> events() {
        return events;
    }

    /** Return the supplied pause policy without starting a timer or scheduling work. */
    public boolean freezeOnPause() {
        return freezeOnPause;
    }
}
