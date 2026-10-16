package com.scenevibe.tvcompanionpoc.wall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure synchronous WALL state machine. All time and eligibility are explicit inputs.
 * Returned effects describe attempted selection, not successful physical presentation.
 * One caller serializes operations; there are no callbacks, waits or consumed history.
 */
public final class WallCalendarScheduler {
    public static final long NO_BOUNDARY = -1L;

    /** Closed effect vocabulary; every replacement emits EXIT before DUE. */
    public enum Kind { EXIT, DUE }

    /** Closed reasons: completed, still-active priority loss, backwards withdrawal, explicit removal. */
    public enum ExitReason { END, SUPERSEDED, CLOCK_REEVALUATED, CLEAR }

    /** Immutable identity-only effect. A DUE has no exit reason. */
    public static final class Effect {
        private final Kind kind;
        private final String eventId;
        private final ExitReason reason;

        /** Construct an effect internally so invalid kind/reason combinations cannot escape. */
        private Effect(Kind kind, String eventId, ExitReason reason) {
            this.kind = kind;
            this.eventId = eventId;
            this.reason = reason;
        }

        /** Construct an identity-only entry into the current window. */
        private static Effect due(String eventId) { return new Effect(Kind.DUE, eventId, null); }

        /** Construct an identity-only retirement with a closed semantic reason. */
        private static Effect exit(String eventId, ExitReason reason) {
            return new Effect(Kind.EXIT, eventId, reason);
        }

        /** Return EXIT or DUE. */
        public Kind kind() { return kind; }

        /** Return the exact opaque identity. */
        public String eventId() { return eventId; }

        /** Return the EXIT reason, or null for DUE. */
        public ExitReason reason() { return reason; }
    }

    /** Immutable selected window, ordered effects and next strictly future absolute boundary. */
    public static final class Result {
        private final WallEvent selected;
        private final List<Effect> effects;
        private final long nextBoundaryEpochMs;

        /** Freeze the bounded effects independently of future evaluations. */
        private Result(WallEvent selected, List<Effect> effects, long nextBoundaryEpochMs) {
            this.selected = selected;
            this.effects = Collections.unmodifiableList(new ArrayList<>(effects));
            this.nextBoundaryEpochMs = nextBoundaryEpochMs;
        }

        /** Return the eligible selected window, or null. */
        public WallEvent selected() { return selected; }

        /** Return at most EXIT then DUE, with no deferred callback. */
        public List<Effect> effects() { return effects; }

        /** Return an abstract future epoch, or NO_BOUNDARY; nothing is programmed. */
        public long nextBoundaryEpochMs() { return nextBoundaryEpochMs; }
    }

    private WallCalendar calendar;
    private WallEvent selected;

    /** Start empty; every usable calendar and epoch must be supplied explicitly. */
    public WallCalendarScheduler() {}

    /**
     * Replace the calendar explicitly, retiring any previous attempt with CLEAR.
     * Even an identical reload starts a fresh selection. Invalid input leaves state intact.
     */
    public Result load(WallCalendar nextCalendar, long nowEpochMs, boolean eligible) {
        if (nextCalendar == null) throw new IllegalArgumentException("Missing WALL calendar");
        WallEvent.requireEpoch(nowEpochMs);
        List<Effect> effects = new ArrayList<>(2);
        if (selected != null) effects.add(Effect.exit(selected.eventId(), ExitReason.CLEAR));
        calendar = nextCalendar;
        selected = null;
        return transition(nowEpochMs, eligible, effects);
    }

    /** Recompute current state from an explicit epoch; repeated selection is idempotent. */
    public Result evaluate(long nowEpochMs, boolean eligible) {
        WallEvent.requireEpoch(nowEpochMs);
        return transition(nowEpochMs, eligible, new ArrayList<>(2));
    }

    /** Explicitly withdraw the calendar and selected attempt without reading a clock. */
    public Result clear() {
        List<Effect> effects = new ArrayList<>(1);
        if (selected != null) effects.add(Effect.exit(selected.eventId(), ExitReason.CLEAR));
        calendar = null;
        selected = null;
        return new Result(null, effects, NO_BOUNDARY);
    }

    /** Select solely from current input, emitting at most one retirement then one entry. */
    private Result transition(long nowEpochMs, boolean eligible, List<Effect> effects) {
        WallEvent next = eligible ? select(calendar, nowEpochMs) : null;
        if (selected != next) {
            if (selected != null) {
                ExitReason reason = !eligible ? ExitReason.CLEAR
                        : nowEpochMs >= selected.endEpochMs() ? ExitReason.END
                        : nowEpochMs < selected.startEpochMs() ? ExitReason.CLOCK_REEVALUATED
                        : ExitReason.SUPERSEDED;
                effects.add(Effect.exit(selected.eventId(), reason));
            }
            if (next != null) effects.add(Effect.due(next.eventId()));
            selected = next;
        }
        return new Result(selected, effects, nextBoundary(calendar, nowEpochMs));
    }

    /** Scan at most 256 half-open windows using newest start, then smallest safe ASCII ID. */
    private static WallEvent select(WallCalendar calendar, long nowEpochMs) {
        WallEvent best = null;
        if (calendar == null) return null;
        for (WallEvent event : calendar.events()) {
            if (event.startEpochMs() <= nowEpochMs && nowEpochMs < event.endEpochMs()
                    && (best == null || event.startEpochMs() > best.startEpochMs()
                    || (event.startEpochMs() == best.startEpochMs()
                    && event.eventId().compareTo(best.eventId()) < 0))) {
                best = event;
            }
        }
        return best;
    }

    /** Find the earliest future window edge, conservatively including edges of overlap losers. */
    private static long nextBoundary(WallCalendar calendar, long nowEpochMs) {
        long next = NO_BOUNDARY;
        if (calendar == null) return next;
        for (WallEvent event : calendar.events()) {
            if (event.startEpochMs() > nowEpochMs
                    && (next == NO_BOUNDARY || event.startEpochMs() < next)) next = event.startEpochMs();
            if (event.endEpochMs() > nowEpochMs
                    && (next == NO_BOUNDARY || event.endEpochMs() < next)) next = event.endEpochMs();
        }
        return next;
    }
}
