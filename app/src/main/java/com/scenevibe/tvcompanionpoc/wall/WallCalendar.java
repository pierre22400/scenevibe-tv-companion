package com.scenevibe.tvcompanionpoc.wall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Finite immutable UTC horizon and windows, independent of any installation or clock. */
public final class WallCalendar {
    public static final int MAX_EVENTS = 256;
    public static final long MAX_HORIZON_MS = 604_800_000L;

    private final long horizonStartEpochMs;
    private final long horizonEndEpochMs;
    private final List<WallEvent> events;

    /** Freeze 1..256 unique windows entirely contained in a positive bounded horizon. */
    public WallCalendar(long horizonStartEpochMs, long horizonEndEpochMs, List<WallEvent> source) {
        WallEvent.requireEpoch(horizonStartEpochMs);
        WallEvent.requireEpoch(horizonEndEpochMs);
        if (horizonStartEpochMs >= horizonEndEpochMs
                || horizonEndEpochMs - horizonStartEpochMs > MAX_HORIZON_MS) {
            throw new IllegalArgumentException("Invalid WALL horizon");
        }
        if (source == null || source.isEmpty() || source.size() > MAX_EVENTS) {
            throw new IllegalArgumentException("Invalid WALL count");
        }
        List<WallEvent> copy = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (WallEvent event : source) {
            if (event == null || copy.size() == MAX_EVENTS || !ids.add(event.eventId())
                    || event.startEpochMs() < horizonStartEpochMs
                    || event.endEpochMs() > horizonEndEpochMs) {
                throw new IllegalArgumentException("Invalid WALL window");
            }
            copy.add(event);
        }
        if (copy.isEmpty()) throw new IllegalArgumentException("Invalid WALL count");
        copy.sort(Comparator.comparingLong(WallEvent::startEpochMs).thenComparing(WallEvent::eventId));
        this.horizonStartEpochMs = horizonStartEpochMs;
        this.horizonEndEpochMs = horizonEndEpochMs;
        this.events = Collections.unmodifiableList(copy);
    }

    /** Return the inclusive UTC horizon start. */
    public long horizonStartEpochMs() { return horizonStartEpochMs; }

    /** Return the exclusive UTC horizon end. */
    public long horizonEndEpochMs() { return horizonEndEpochMs; }

    /** Return canonical start/ASCII-ID order; selection never uses input order. */
    public List<WallEvent> events() { return events; }
}
