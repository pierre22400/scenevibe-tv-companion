package com.scenevibe.tvcompanionpoc.presentation;

import org.junit.Test;
import static org.junit.Assert.*;
import com.scenevibe.tvcompanionpoc.presentation.TvPreparation.Binding;
import com.scenevibe.tvcompanionpoc.presentation.TvPreparation.Evidence;

/** Negative authority cases: selection and display status never stand in for delivery/admission. */
public final class TvPreparationTest {
    /** Builds an explicit binding, without reusing an editorial revision as an assignment. */
    private Binding binding(String offer, String intention, String device, String executable, String digest, long revision) {
        return new Binding(offer, intention, device, executable, digest, revision);
    }

    /** Returns a fixed named fixture for pure equality tests, not runtime data. */
    private Binding good() { return binding("offer", "intention", "tv", "finaltrack", "digest", 19); }

    /** Builds a READY assertion whose evidence still has to pass the complete gate. */
    private TvPreparation ready(Evidence evidence) {
        return new TvPreparation(TvPreparation.Stage.READY, null, false, evidence);
    }

    /** Even a READY display state is refused when it carries no verified binding. */
    @Test public void statusAndSelectionAloneCannotProveReadiness() {
        assertFalse(ready(null).readyFor("offer"));
        assertFalse(new TvPreparation(TvPreparation.Stage.REQUESTED, null, false, null).mediaAdmittedFor("offer"));
    }

    /** Demonstration state can never masquerade as runtime proof, even with a full tuple. */
    @Test public void demonstrationCannotBecomeRuntimeEvidence() {
        Binding b = good(); Evidence e = new Evidence(b, b, b, b, b);
        TvPreparation demo = new TvPreparation(TvPreparation.Stage.READY, null, true, e);
        assertFalse(demo.readyFor("offer")); assertFalse(demo.mediaAdmittedFor("offer"));
    }

    /** Installation ACK and native admission remain separate observable milestones. */
    @Test public void ackDoesNotAdmitTheProgram() {
        Binding b = good(); TvPreparation p = ready(new Evidence(b, b, b, b, null));
        assertTrue(p.readyFor("offer")); assertFalse(p.mediaAdmittedFor("offer"));
        assertTrue(ready(new Evidence(b, b, b, b, b)).mediaAdmittedFor("offer"));
    }

    /** Every absent stage fails closed, including a missing Account confirmation. */
    @Test public void missingStagesCannotBecomeReady() {
        Binding b = good();
        for (Evidence e : new Evidence[] {new Evidence(null, b, b, b, b), new Evidence(b, null, b, b, b),
                new Evidence(b, b, null, b, b), new Evidence(b, b, b, null, b)}) assertFalse(ready(e).readyFor("offer"));
    }

    /** Wrong offer, intention, device, executable, digest or revision invalidates a stage. */
    @Test public void eachBindingCoordinateIsRequired() {
        Binding b = good(); Binding[] bad = {
                binding("other", "intention", "tv", "finaltrack", "digest", 19),
                binding("offer", "other", "tv", "finaltrack", "digest", 19),
                binding("offer", "intention", "other", "finaltrack", "digest", 19),
                binding("offer", "intention", "tv", "other", "digest", 19),
                binding("offer", "intention", "tv", "finaltrack", "other", 19),
                binding("offer", "intention", "tv", "finaltrack", "digest", 18)};
        for (Binding mismatch : bad) {
            assertFalse(ready(new Evidence(mismatch, b, b, b, b)).readyFor("offer"));
            assertFalse(ready(new Evidence(b, b, mismatch, b, b)).readyFor("offer"));
            assertFalse(ready(new Evidence(b, b, b, mismatch, b)).readyFor("offer"));
            assertFalse(ready(new Evidence(b, b, b, b, mismatch)).mediaAdmittedFor("offer"));
        }
        assertFalse(ready(new Evidence(b, b, b, b, b)).readyFor("another-offer"));
    }

    /** Lost or stale media evidence revokes admission while retaining delivered-content status. */
    @Test public void lostAdmissionDoesNotEraseDelivery() {
        Binding b = good(); TvPreparation lost = ready(new Evidence(b, b, b, b, null));
        assertTrue(lost.readyFor("offer")); assertFalse(lost.mediaAdmittedFor("offer"));
    }

    /** An absent reference cannot be repaired by a default id. */
    @Test(expected = IllegalArgumentException.class) public void missingIdentityIsRejected() {
        binding(null, "intention", "tv", "track", "digest", 1);
    }

    /** No revision is fabricated before the actual allocator returns a positive revision. */
    @Test(expected = IllegalArgumentException.class) public void unallocatedRevisionIsRejected() {
        binding("offer", "intention", "tv", "track", "digest", 0);
    }
}
