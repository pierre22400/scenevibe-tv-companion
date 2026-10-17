package com.scenevibe.tvcompanionpoc.tvfirst;

import java.util.Collections;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/** Writer lifetimes reject late callbacks rather than retagging them to a newer service. */
public final class ObservationDiagnosticsTest {
    private ObservationDiagnostics.Session session;

    /** Retire the current test writer without touching any runtime or preference. */
    @After public void cleanup() {if (session != null) session.close();}

    /** Construct a synthetic missing-source batch with ordered time coordinates. */
    private MediaEvidence batch(long sequence, long captured) {
        return new MediaEvidence(sequence, captured, MediaEvidence.Availability.UNAVAILABLE,
                false, false, 0, Collections.emptyList(), MediaEvidence.Proof.UNKNOWN,
                MediaEvidence.Proof.UNKNOWN, MediaEvidence.Proof.UNKNOWN, MediaEvidence.Proof.UNKNOWN);
    }

    /** A stopped writer cannot republish a positive-looking old callback. */
    @Test public void closeRejectsLateCallback() {
        session = ObservationDiagnostics.begin(); session.record(batch(1, 10)); session.close();
        session.record(batch(2, 20)); assertNull(ObservationDiagnostics.latest());
    }

    /** Replacement never permits an old callback or destruction to overwrite the new observation. */
    @Test public void replacementRetiresOldWriter() {
        ObservationDiagnostics.Session old = ObservationDiagnostics.begin();
        session = ObservationDiagnostics.begin(); MediaEvidence e = batch(1, 10); session.record(e);
        old.record(batch(999, 999)); old.close(); assertSame(e, ObservationDiagnostics.latest());
    }

    /** Sequence and capture coordinates must both advance coherently. */
    @Test public void duplicateAndReorderedCallbacksIgnored() {
        session = ObservationDiagnostics.begin(); MediaEvidence e = batch(3, 30); session.record(e);
        session.record(batch(3, 40)); session.record(batch(2, 50)); session.record(batch(4, 20));
        assertSame(e, ObservationDiagnostics.latest());
    }

    /** A newer coherent batch replaces the single retained value. */
    @Test public void retainsOnlyLatestBatch() {
        session = ObservationDiagnostics.begin(); session.record(batch(1, 10));
        MediaEvidence e = batch(2, 20); session.record(e); assertSame(e, ObservationDiagnostics.latest());
    }
}
