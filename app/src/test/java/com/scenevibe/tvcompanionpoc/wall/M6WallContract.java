package com.scenevibe.tvcompanionpoc.wall;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * JDK-only contracts against actual WALL source. Fixed epochs replace any clock.
 * The same cases run standalone and through JUnit; temporary mutants reuse witnesses.
 */
public final class M6WallContract {
    private static final long MAX = 253_402_300_799_999L;
    private static final List<String> CASES = Collections.unmodifiableList(Arrays.asList(
            "eventMinEpoch", "eventMaxEpoch", "eventMinDuration", "eventMaxDuration",
            "eventIdAlphabet", "eventIdMaxLength", "rejectNullId", "rejectEmptyId",
            "rejectLongId", "rejectUnsafeIds", "rejectNegativeStart", "rejectEpochPastMaximum",
            "rejectLongOverflow", "rejectReversedWindow", "rejectEqualEnds", "rejectShortDuration",
            "rejectLongDuration", "calendarOne", "calendar256", "reject257",
            "rejectEmptyCalendar", "rejectNullCalendarEvents", "rejectNullEvent", "rejectDuplicateId",
            "rejectBeforeHorizon", "rejectAfterHorizon", "horizonMin", "horizonMax",
            "rejectHorizonOverflow", "rejectHorizonEqual", "rejectHorizonReversed", "rejectHorizonTooLong",
            "immutableCalendar", "canonicalCalendar", "beforeStart", "startExact",
            "inside", "endExact", "afterEnd", "disjoint", "adjacent", "overlap",
            "fallbackA", "equalStart", "sameStartFallback", "lateLoad", "reload", "noPastReplay",
            "forwardJump", "backwardReselect", "backwardBeforeStart", "backwardReplacement",
            "directAToB", "exitToNone", "repeatedSame", "clear", "clearTwice",
            "eligibilityLossRegain", "initialIneligible", "nextBoundary", "noBoundary",
            "invalidEvaluateKeepsState", "invalidLoadKeepsState", "resultImmutable",
            "permutationSweep", "explicitTimeReplay", "maxEpochSelection", "boundedEffectsSweep"));

    /** No instances; this is test code, independent of JUnit and Android. */
    private M6WallContract() {}

    /** Return the finite shared contract inventory. */
    public static List<String> cases() { return CASES; }

    /** Execute all cases or one named mutation witness without platform dependencies. */
    public static void main(String[] arguments) {
        List<String> names = arguments.length == 0 ? CASES : Collections.singletonList(arguments[0]);
        for (String name : names) {
            try { run(name); }
            catch (AssertionError failure) { throw new AssertionError("WALL contract failed: " + name, failure); }
        }
        System.out.println("WALL contracts PASS=" + names.size() + " FAIL=0 SKIP=0");
    }

    /** Exercise exact numerical boundaries, selection, retirement and repeated explicit time. */
    public static void run(String name) {
        WallCalendarScheduler scheduler = new WallCalendarScheduler();
        switch (name) {
            case "eventMinEpoch": equal(0L, event("A", 0, 250).startEpochMs()); break;
            case "eventMaxEpoch": equal(MAX, event("A", MAX - 250, MAX).endEpochMs()); break;
            case "eventMinDuration": equal(250L, event("A", 10, 260).endEpochMs() - 10); break;
            case "eventMaxDuration": equal(3_600_000L, event("A", 0, 3_600_000).endEpochMs()); break;
            case "eventIdAlphabet": equal("-._:09AZaz", event("-._:09AZaz", 0, 250).eventId()); break;
            case "eventIdMaxLength": equal(repeat('a', 128), event(repeat('a', 128), 0, 250).eventId()); break;
            case "rejectNullId": invalid(() -> event(null, 0, 250)); break;
            case "rejectEmptyId": invalid(() -> event("", 0, 250)); break;
            case "rejectLongId": invalid(() -> event(repeat('a', 129), 0, 250)); break;
            case "rejectUnsafeIds":
                for (String id : Arrays.asList(" A", "A ", "A/B", "A\\B", "A\n", "A\0", "é", "é", "🙂", "A@B"))
                    invalid(() -> event(id, 0, 250));
                break;
            case "rejectNegativeStart": invalid(() -> event("A", -1, 250)); break;
            case "rejectEpochPastMaximum": invalid(() -> event("A", MAX - 250, MAX + 1)); break;
            case "rejectLongOverflow":
                invalid(() -> event("A", Long.MIN_VALUE, Long.MAX_VALUE));
                invalid(() -> event("A", 0, Long.MAX_VALUE));
                invalid(() -> event("A", Long.MAX_VALUE - 250, Long.MAX_VALUE));
                break;
            case "rejectReversedWindow": invalid(() -> event("A", 251, 250)); break;
            case "rejectEqualEnds": invalid(() -> event("A", 250, 250)); break;
            case "rejectShortDuration": invalid(() -> event("A", 1, 250)); break;
            case "rejectLongDuration": invalid(() -> event("A", 0, 3_600_001)); break;
            case "calendarOne": equal(1, calendar(a()).events().size()); break;
            case "calendar256": equal(256, new WallCalendar(0, 250, many(256)).events().size()); break;
            case "reject257": invalid(() -> new WallCalendar(0, 250, many(257))); break;
            case "rejectEmptyCalendar": invalid(() -> new WallCalendar(0, 1000, Collections.emptyList())); break;
            case "rejectNullCalendarEvents": invalid(() -> new WallCalendar(0, 1000, null)); break;
            case "rejectNullEvent": invalid(() -> calendar((WallEvent) null)); break;
            case "rejectDuplicateId": invalid(() -> calendar(a(), event("A", 2500, 3000))); break;
            case "rejectBeforeHorizon": invalid(() -> new WallCalendar(1001, 2000, Arrays.asList(a()))); break;
            case "rejectAfterHorizon": invalid(() -> new WallCalendar(1000, 1999, Arrays.asList(a()))); break;
            case "horizonMin":
                equal(250L, new WallCalendar(0, 250, Arrays.asList(event("A", 0, 250))).horizonEndEpochMs());
                break;
            case "horizonMax":
                equal(604_800_000L, new WallCalendar(0, 604_800_000L, Arrays.asList(a())).horizonEndEpochMs());
                equal(MAX - 604_800_000L, new WallCalendar(MAX - 604_800_000L, MAX,
                        Arrays.asList(event("A", MAX - 250, MAX))).horizonStartEpochMs());
                break;
            case "rejectHorizonOverflow":
                invalid(() -> new WallCalendar(Long.MIN_VALUE, Long.MAX_VALUE, Arrays.asList(a())));
                invalid(() -> new WallCalendar(0, MAX + 1, Arrays.asList(a())));
                break;
            case "rejectHorizonEqual": invalid(() -> new WallCalendar(1000, 1000, Arrays.asList(a()))); break;
            case "rejectHorizonReversed": invalid(() -> new WallCalendar(2000, 1000, Arrays.asList(a()))); break;
            case "rejectHorizonTooLong": invalid(() -> new WallCalendar(0, 604_800_001L, Arrays.asList(a()))); break;
            case "immutableCalendar": {
                List<WallEvent> source = new ArrayList<>(Arrays.asList(a()));
                WallCalendar frozen = new WallCalendar(0, 10000, source);
                source.clear();
                equal(1, frozen.events().size());
                unsupported(() -> frozen.events().clear());
                unsupported(() -> frozen.events().set(0, event("B", 1000, 2000)));
                break;
            }
            case "canonicalCalendar":
                equal("A,B,Z", ids(calendar(event("Z", 1000, 2000), event("B", 1000, 2500), a()).events()));
                break;
            case "beforeStart": check(scheduler.load(calendar(a()), 999, true), null, 1000); break;
            case "startExact": check(scheduler.load(calendar(a()), 1000, true), "A", 2000, "DUE:A"); break;
            case "inside": check(scheduler.load(calendar(a()), 1500, true), "A", 2000, "DUE:A"); break;
            case "endExact":
                scheduler.load(calendar(a()), 1000, true);
                check(scheduler.evaluate(2000, true), null, -1, "EXIT:A:END"); break;
            case "afterEnd": check(scheduler.load(calendar(a()), 2001, true), null, -1); break;
            case "disjoint":
                scheduler.load(calendar(a(), event("B", 3000, 4000)), 1500, true);
                check(scheduler.evaluate(2000, true), null, 3000, "EXIT:A:END");
                check(scheduler.evaluate(3000, true), "B", 4000, "DUE:B"); break;
            case "adjacent":
                scheduler.load(calendar(a(), event("B", 2000, 3000)), 1999, true);
                check(scheduler.evaluate(2000, true), "B", 3000, "EXIT:A:END", "DUE:B"); break;
            case "overlap":
                scheduler.load(overlap(), 1000, true);
                check(scheduler.evaluate(2000, true), "B", 3000, "EXIT:A:SUPERSEDED", "DUE:B"); break;
            case "fallbackA":
                scheduler.load(overlap(), 2000, true);
                check(scheduler.evaluate(3000, true), "A", 5000, "EXIT:B:END", "DUE:A"); break;
            case "equalStart":
                check(scheduler.load(calendar(event("Z", 1000, 2500), a()), 1000, true), "A", 2000, "DUE:A");
                check(scheduler.load(calendar(event("a", 1000, 2500), event(":", 1000, 2000)), 1000, true),
                        ":", 2000, "EXIT:A:CLEAR", "DUE::"); break;
            case "sameStartFallback":
                scheduler.load(calendar(a(), event("Z", 1000, 2500)), 1000, true);
                check(scheduler.evaluate(2000, true), "Z", 2500, "EXIT:A:END", "DUE:Z"); break;
            case "lateLoad": check(scheduler.load(calendar(a()), 1999, true), "A", 2000, "DUE:A"); break;
            case "reload": {
                scheduler.load(calendar(a()), 1500, true);
                WallCalendar replacement = calendar(event("A", 1000, 3000));
                check(scheduler.load(replacement, 1500, true), "A", 3000,
                        "EXIT:A:CLEAR", "DUE:A");
                check(scheduler.evaluate(2000, true), "A", 3000);
                check(scheduler.load(replacement, 2000, true), "A", 3000, "EXIT:A:CLEAR", "DUE:A");
                break;
            }
            case "noPastReplay": check(scheduler.load(overlap(), 6000, true), null, -1); break;
            case "forwardJump":
                scheduler.load(calendar(a(), event("B", 2500, 3000), event("C", 4000, 5000)), 1500, true);
                check(scheduler.evaluate(4500, true), "C", 5000, "EXIT:A:END", "DUE:C"); break;
            case "backwardReselect":
                scheduler.load(calendar(a()), 1000, true);
                check(scheduler.evaluate(2000, true), null, -1, "EXIT:A:END");
                check(scheduler.evaluate(1000, true), "A", 2000, "DUE:A"); break;
            case "backwardBeforeStart":
                scheduler.load(calendar(a()), 1500, true);
                check(scheduler.evaluate(999, true), null, 1000, "EXIT:A:CLOCK_REEVALUATED"); break;
            case "backwardReplacement":
                scheduler.load(overlap(), 2500, true);
                check(scheduler.evaluate(1500, true), "A", 2000, "EXIT:B:CLOCK_REEVALUATED", "DUE:A"); break;
            case "directAToB":
                scheduler.load(calendar(a(), event("B", 3000, 4000)), 1500, true);
                check(scheduler.evaluate(3500, true), "B", 4000, "EXIT:A:END", "DUE:B"); break;
            case "exitToNone":
                scheduler.load(calendar(a()), 1500, true);
                check(scheduler.evaluate(9999, true), null, -1, "EXIT:A:END"); break;
            case "repeatedSame":
                scheduler.load(calendar(a()), 1500, true);
                check(scheduler.evaluate(1500, true), "A", 2000);
                check(scheduler.evaluate(1999, true), "A", 2000); break;
            case "clear":
                scheduler.load(calendar(a()), 1500, true);
                check(scheduler.clear(), null, -1, "EXIT:A:CLEAR");
                check(scheduler.evaluate(1500, true), null, -1); break;
            case "clearTwice": check(scheduler.clear(), null, -1); check(scheduler.clear(), null, -1); break;
            case "eligibilityLossRegain":
                scheduler.load(calendar(a()), 1500, true);
                check(scheduler.evaluate(1500, false), null, 2000, "EXIT:A:CLEAR");
                check(scheduler.evaluate(1500, false), null, 2000);
                check(scheduler.evaluate(1500, true), "A", 2000, "DUE:A");
                scheduler.evaluate(1900, false);
                check(scheduler.evaluate(2000, true), null, -1); break;
            case "initialIneligible":
                check(scheduler.load(calendar(a()), 1500, false), null, 2000);
                check(scheduler.evaluate(1500, true), "A", 2000, "DUE:A"); break;
            case "nextBoundary":
                check(scheduler.load(overlap(), 0, true), null, 1000);
                check(scheduler.evaluate(1000, true), "A", 2000, "DUE:A");
                check(scheduler.evaluate(2000, true), "B", 3000, "EXIT:A:SUPERSEDED", "DUE:B");
                check(scheduler.evaluate(3000, true), "A", 5000, "EXIT:B:END", "DUE:A"); break;
            case "noBoundary":
                check(scheduler.evaluate(0, true), null, -1);
                check(scheduler.load(calendar(a()), MAX, true), null, -1); break;
            case "invalidEvaluateKeepsState":
                scheduler.load(calendar(a()), 1500, true);
                for (long now : new long[]{-1L, MAX + 1, Long.MIN_VALUE, Long.MAX_VALUE})
                    invalid(() -> scheduler.evaluate(now, true));
                check(scheduler.evaluate(1500, true), "A", 2000); break;
            case "invalidLoadKeepsState":
                scheduler.load(calendar(a()), 1500, true);
                invalid(() -> scheduler.load(null, 1500, true));
                invalid(() -> scheduler.load(overlap(), -1, true));
                invalid(() -> scheduler.load(overlap(), MAX + 1, true));
                check(scheduler.evaluate(1500, true), "A", 2000); break;
            case "resultImmutable": {
                WallCalendarScheduler.Result previous = scheduler.load(calendar(a()), 1500, true);
                unsupported(() -> previous.effects().clear());
                unsupported(() -> previous.effects().set(0, previous.effects().get(0)));
                scheduler.clear();
                check(previous, "A", 2000, "DUE:A"); break;
            }
            case "permutationSweep": permutationSweep(); break;
            case "explicitTimeReplay": {
                WallCalendarScheduler other = new WallCalendarScheduler();
                scheduler.load(overlap(), 0, true); other.load(overlap(), 0, true);
                for (long now : new long[]{1000, 2000, 2999, 3000, 5000, 1500, 999, 2500, 2500})
                    equal(snapshot(scheduler.evaluate(now, true)), snapshot(other.evaluate(now, true)));
                break;
            }
            case "maxEpochSelection":
                check(scheduler.load(new WallCalendar(MAX - 250, MAX,
                        Arrays.asList(event("A", MAX - 250, MAX))), MAX - 250, true), "A", MAX, "DUE:A");
                check(scheduler.evaluate(MAX, true), null, -1, "EXIT:A:END"); break;
            case "boundedEffectsSweep": boundedEffectsSweep(); break;
            default: throw new AssertionError("Unknown WALL contract");
        }
    }

    /** Build a valid fixture directly, without a parser, store or adapter. */
    private static WallEvent event(String id, long start, long end) { return new WallEvent(id, start, end); }

    /** Return the shared exact-boundary window. */
    private static WallEvent a() { return event("A", 1000, 2000); }

    /** Construct a calendar with a fixed explicit horizon. */
    private static WallCalendar calendar(WallEvent... events) { return new WallCalendar(0, 10000, Arrays.asList(events)); }

    /** Return an overlap where the older window outlives the newer one. */
    private static WallCalendar overlap() { return calendar(event("A", 1000, 5000), event("B", 2000, 3000)); }

    /** Construct exactly the requested number of unique windows for count boundaries. */
    private static List<WallEvent> many(int count) {
        List<WallEvent> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(event("E" + i, 0, 250));
        return result;
    }

    /** Create bounded literal ID lengths without a newer platform String API. */
    private static String repeat(char character, int count) {
        char[] value = new char[count]; Arrays.fill(value, character); return new String(value);
    }

    /** Compare all selection, ordered-effect and future-boundary fields against fixed expectations. */
    private static void check(WallCalendarScheduler.Result result, String id, long boundary, String... effects) {
        equal(id, result.selected() == null ? null : result.selected().eventId());
        equal(boundary, result.nextBoundaryEpochMs());
        equal(Arrays.asList(effects), effects(result));
        if (result.effects().size() > 2) throw new AssertionError("Unbounded effects");
        for (WallCalendarScheduler.Effect effect : result.effects()) {
            if ((effect.kind() == WallCalendarScheduler.Kind.DUE) != (effect.reason() == null))
                throw new AssertionError("Invalid reason binding");
        }
    }

    /** Preserve effect order in the test journal; no sorting or deduplication is permitted. */
    private static List<String> effects(WallCalendarScheduler.Result result) {
        List<String> values = new ArrayList<>();
        for (WallCalendarScheduler.Effect effect : result.effects())
            values.add(effect.kind() + ":" + effect.eventId() + (effect.reason() == null ? "" : ":" + effect.reason()));
        return values;
    }

    /** Produce a deterministic test snapshot containing every public result field. */
    private static String snapshot(WallCalendarScheduler.Result result) {
        WallEvent selected = result.selected();
        return (selected == null ? "-" : selected.eventId() + "/" + selected.startEpochMs() + "/" + selected.endEpochMs())
                + "/" + result.nextBoundaryEpochMs() + "/" + effects(result);
    }

    /** Record canonical ordering separately from temporal selection. */
    private static String ids(List<WallEvent> events) {
        List<String> values = new ArrayList<>();
        for (WallEvent event : events) values.add(event.eventId());
        return String.join(",", values);
    }

    /** Require the exact validation exception rather than accepting any failure. */
    private static void invalid(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid input accepted");
    }

    /** Require the collection's actual immutability contract. */
    private static void unsupported(Runnable action) {
        try { action.run(); } catch (UnsupportedOperationException expected) { return; }
        throw new AssertionError("Mutable result escaped");
    }

    /** Compare literal expectations without depending on JVM assertion flags. */
    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + "; got " + actual);
    }

    /** Enumerate all 24 input orders against a fixed table at every distinct boundary and interior. */
    private static void permutationSweep() {
        List<WallEvent> windows = new ArrayList<>(Arrays.asList(event("Z", 1000, 4000),
                event("A", 1000, 5000), event("B", 2000, 3000), event("C", 2000, 3500)));
        List<List<WallEvent>> orders = new ArrayList<>(); permutations(windows, 0, orders);
        equal(24, orders.size());
        long[] epochs = {0, 999, 1000, 1999, 2000, 2999, 3000, 3499, 3500, 3999, 4000, 4999, 5000};
        String[] winners = {null, null, "A", "A", "B", "B", "C", "C", "A", "A", "A", "A", null};
        String canonical = null;
        for (List<WallEvent> order : orders) {
            WallCalendar calendar = new WallCalendar(0, 10000, order);
            if (canonical == null) canonical = ids(calendar.events());
            equal(canonical, ids(calendar.events()));
            for (int i = 0; i < epochs.length; i++) {
                WallCalendarScheduler.Result result = new WallCalendarScheduler().load(calendar, epochs[i], true);
                equal(winners[i], result.selected() == null ? null : result.selected().eventId());
                equal(winners[i] == null ? Collections.emptyList() : Arrays.asList("DUE:" + winners[i]), effects(result));
            }
        }
    }

    /** Generate permutations in test code only, with no production order adaptation. */
    private static void permutations(List<WallEvent> source, int index, List<List<WallEvent>> results) {
        if (index == source.size()) { results.add(new ArrayList<>(source)); return; }
        for (int i = index; i < source.size(); i++) {
            Collections.swap(source, index, i); permutations(source, index + 1, results); Collections.swap(source, index, i);
        }
    }

    /** Cover all 81 ordered epoch pairs and explicit eligibility combinations against a fixed winner table. */
    private static void boundedEffectsSweep() {
        long[] epochs = {0, 999, 1000, 1999, 2000, 2999, 3000, 4999, 5000};
        String[] winners = {null, null, "A", "A", "B", "B", "A", "A", null};
        for (int i = 0; i < epochs.length; i++) for (int j = 0; j < epochs.length; j++)
            for (boolean before : new boolean[]{false, true}) for (boolean after : new boolean[]{false, true}) {
                WallCalendarScheduler scheduler = new WallCalendarScheduler();
                scheduler.load(overlap(), epochs[i], before);
                WallCalendarScheduler.Result result = scheduler.evaluate(epochs[j], after);
                String old = before ? winners[i] : null;
                String next = after ? winners[j] : null;
                equal(next, result.selected() == null ? null : result.selected().eventId());
                List<String> expected = new ArrayList<>();
                if (!Objects.equals(old, next)) {
                    if (old != null) {
                        long start = old.equals("A") ? 1000 : 2000;
                        long end = old.equals("A") ? 5000 : 3000;
                        String reason = !after ? "CLEAR" : epochs[j] >= end ? "END"
                                : epochs[j] < start ? "CLOCK_REEVALUATED" : "SUPERSEDED";
                        expected.add("EXIT:" + old + ":" + reason);
                    }
                    if (next != null) expected.add("DUE:" + next);
                }
                equal(expected, effects(result));
                if (result.nextBoundaryEpochMs() != -1 && result.nextBoundaryEpochMs() <= epochs[j])
                    throw new AssertionError("Nonfuture boundary");
            }
    }
}
