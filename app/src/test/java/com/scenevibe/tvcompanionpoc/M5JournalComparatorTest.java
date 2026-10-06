package com.scenevibe.tvcompanionpoc;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Test-only sensitivity controls for exact comparison; no fake candidate scheduler. */
public final class M5JournalComparatorTest {
    /** Build a raw trace with a repeated PLAYBACK and an explicit empty input. */
    private List<M5TemporalJournal.Frame> trace() {
        M5TemporalJournal journal = new M5TemporalJournal();
        journal.begin("opaque-old");
        journal.append(M5TemporalJournal.Kind.ELIGIBLE, null, false, false);
        journal.append(M5TemporalJournal.Kind.PLAYBACK, null, true, true);
        journal.append(M5TemporalJournal.Kind.PLAYBACK, null, true, true);
        journal.append(M5TemporalJournal.Kind.DUE, "é", false, false);
        journal.end();
        journal.begin("opaque-new");
        journal.end();
        return journal.frames();
    }

    /** Replace the first frame defensively while retaining the empty second frame. */
    private List<M5TemporalJournal.Frame> changed(List<M5TemporalJournal.Effect> effects, String token) {
        List<M5TemporalJournal.Frame> result = new ArrayList<>(trace());
        result.set(0, new M5TemporalJournal.Frame(0, token, effects));
        return result;
    }

    /** Positive control checks full JSON round-trip without lost repeated effects. */
    @Test
    public void identicalOrderedFramesPass() throws Exception {
        assertTrue(M5TemporalJournal.exactlyEqual(trace(), trace()));
        M5TemporalJournal journal = new M5TemporalJournal();
        journal.begin(null);
        journal.end();
        assertTrue(M5TemporalJournal.exactlyEqual(journal.frames(), M5TemporalJournal.fromJson(journal.json())));
    }

    /** Removing an actual DUE must fail the strict comparator. */
    @Test
    public void missingDueFails() {
        List<M5TemporalJournal.Effect> effects = new ArrayList<>(trace().get(0).effects);
        effects.remove(3);
        assertFalse(M5TemporalJournal.exactlyEqual(trace(), changed(effects, "opaque-old")));
    }

    /** Reordering distinct callback types must fail without any sorting. */
    @Test
    public void reorderedEffectsFail() {
        List<M5TemporalJournal.Effect> effects = new ArrayList<>(trace().get(0).effects);
        java.util.Collections.swap(effects, 0, 3);
        assertFalse(M5TemporalJournal.exactlyEqual(trace(), changed(effects, "opaque-old")));
    }

    /** Composed and decomposed IDs must differ exactly. */
    @Test
    public void changedIdentifierFails() {
        List<M5TemporalJournal.Effect> effects = new ArrayList<>(trace().get(0).effects);
        effects.set(3, new M5TemporalJournal.Effect(M5TemporalJournal.Kind.DUE, "e\u0301", false, false));
        assertFalse(M5TemporalJournal.exactlyEqual(trace(), changed(effects, "opaque-old")));
    }

    /** A playing change must fail even if event IDs and order still match. */
    @Test
    public void changedPlayingFails() {
        List<M5TemporalJournal.Effect> effects = new ArrayList<>(trace().get(0).effects);
        effects.set(1, new M5TemporalJournal.Effect(M5TemporalJournal.Kind.PLAYBACK, null, false, true));
        assertFalse(M5TemporalJournal.exactlyEqual(trace(), changed(effects, "opaque-old")));
    }

    /** A pause-policy change must fail independently of the playing bit. */
    @Test
    public void changedFreezeFails() {
        List<M5TemporalJournal.Effect> effects = new ArrayList<>(trace().get(0).effects);
        effects.set(1, new M5TemporalJournal.Effect(M5TemporalJournal.Kind.PLAYBACK, null, true, false));
        assertFalse(M5TemporalJournal.exactlyEqual(trace(), changed(effects, "opaque-old")));
    }

    /** An extra repeated effect cannot be silently deduplicated. */
    @Test
    public void extraEffectFails() {
        List<M5TemporalJournal.Effect> effects = new ArrayList<>(trace().get(0).effects);
        effects.add(effects.get(1));
        assertFalse(M5TemporalJournal.exactlyEqual(trace(), changed(effects, "opaque-old")));
    }

    /** Even a redundant PLAYBACK is significant and cannot be removed. */
    @Test
    public void removedRepeatedPlaybackFails() {
        List<M5TemporalJournal.Effect> effects = new ArrayList<>(trace().get(0).effects);
        effects.remove(2);
        assertFalse(M5TemporalJournal.exactlyEqual(trace(), changed(effects, "opaque-old")));
    }

    /** Opaque external tokens compare exactly without installation authority. */
    @Test
    public void changedTokenFails() {
        assertFalse(M5TemporalJournal.exactlyEqual(trace(), changed(trace().get(0).effects, "opaque-new")));
    }

    /** A missing no-op input must fail; absence is part of the trace. */
    @Test
    public void removedEmptyInputFails() {
        assertFalse(M5TemporalJournal.exactlyEqual(trace(), trace().subList(0, 1)));
    }

    /** An incorrect input index cannot be hidden by identical effect sequences. */
    @Test
    public void changedInputIndexFails() {
        List<M5TemporalJournal.Frame> result = new ArrayList<>(trace());
        result.set(1, new M5TemporalJournal.Frame(7, "opaque-new", List.of()));
        assertFalse(M5TemporalJournal.exactlyEqual(trace(), result));
    }
}
