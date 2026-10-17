package com.scenevibe.tvcompanionpoc.tvfirst;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic evidence tests qualify data handling only; none is a Sony observation. */
public final class MediaEvidenceTest {
    /** Build a deliberately synthetic positive session, including a genuine raw anchor coordinate. */
    private MediaEvidence.Session session(String id, long position, long anchor, float speed) {
        return new MediaEvidence.Session("test-session", "test.package", id, "same title", null,
                3, position, anchor, speed, position, -1);
    }

    /** Inject proof coordinates explicitly; production acquisition always supplies UNKNOWN. */
    private MediaEvidence batch(MediaEvidence.Session session, MediaEvidence.Proof proof) {
        return new MediaEvidence(1, 10000, MediaEvidence.Availability.AVAILABLE, true, true, 1,
                Collections.singletonList(session), proof, proof, proof, proof);
    }

    /** Even a complete synthetic case never grants rendering. */
    @Test public void allPositiveEvidenceStillCannotRender() {
        MediaEvidencePolicy.Result result = MediaEvidencePolicy.evaluate(
                batch(session("synthetic-id", 12, 9000, 1), MediaEvidence.Proof.OBSERVED), 10000);
        assertTrue(result.conditionsComplete()); assertFalse(result.renderingAllowed());
    }

    /** Published metadata is not a demonstrated programme/edition/context/timeline binding. */
    @Test public void rawIdentityDoesNotPromoteUnknownProofs() {
        MediaEvidencePolicy.Result result = MediaEvidencePolicy.evaluate(
                batch(session("synthetic-id", 12, 9000, 1), MediaEvidence.Proof.UNKNOWN), 10000);
        for (MediaEvidencePolicy.Reason reason : new MediaEvidencePolicy.Reason[] {
                MediaEvidencePolicy.Reason.PROGRAMME_UNKNOWN, MediaEvidencePolicy.Reason.EDITION_UNKNOWN,
                MediaEvidencePolicy.Reason.FOREGROUND_UNKNOWN, MediaEvidencePolicy.Reason.TIMELINE_UNKNOWN})
            assertTrue(result.missing.contains(reason));
        assertFalse(result.conditionsComplete()); assertFalse(result.renderingAllowed());
    }

    /** A title cannot replace an absent native identity. */
    @Test public void titleDoesNotRepairMissingId() {
        assertTrue(MediaEvidencePolicy.evaluate(batch(session(null, 12, 9000, 1),
                MediaEvidence.Proof.UNKNOWN), 10000).missing.contains(MediaEvidencePolicy.Reason.METADATA_INCOMPLETE));
    }

    /** Conflicting explicit proof remains unusable. */
    @Test public void contradictedFactsNeverComplete() {
        assertFalse(MediaEvidencePolicy.evaluate(batch(session("wrong", 12, 9000, 1),
                MediaEvidence.Proof.CONTRADICTED), 10000).conditionsComplete());
    }

    /** A future raw timestamp is not clamped into a valid anchor. */
    @Test public void futureAnchorRemainsIncoherent() {
        MediaEvidence evidence = batch(session("id", 12, 11000, 1), MediaEvidence.Proof.OBSERVED);
        assertEquals(11000, evidence.sessions.get(0).anchorAtMs);
        assertTrue(MediaEvidencePolicy.evaluate(evidence, 10000).missing.contains(MediaEvidencePolicy.Reason.ANCHOR_INCOHERENT));
    }

    /** Missing position and anchor remain explicitly unavailable. */
    @Test public void unknownPositionIsNotAClock() {
        MediaEvidencePolicy.Result r = MediaEvidencePolicy.evaluate(
                batch(session("id", -1, -1, 1), MediaEvidence.Proof.OBSERVED), 10000);
        assertTrue(r.missing.contains(MediaEvidencePolicy.Reason.POSITION_UNKNOWN));
        assertTrue(r.missing.contains(MediaEvidencePolicy.Reason.ANCHOR_INCOHERENT));
    }

    /** Nonfinite and non-unit speeds do not complete the diagnostic pilot conditions. */
    @Test public void invalidSpeedsRemainUnsupported() {
        for (float speed : new float[] {Float.NaN, Float.POSITIVE_INFINITY, -1, 0, 2})
            assertTrue(MediaEvidencePolicy.evaluate(batch(session("id", 12, 9000, speed),
                    MediaEvidence.Proof.OBSERVED), 10000).missing.contains(MediaEvidencePolicy.Reason.SPEED_UNSUPPORTED));
    }

    /** A fresh capture and an old anchor are distinct; no universal stale-anchor threshold is invented. */
    @Test public void oldAnchorIsRetainedWithoutTimelineProof() {
        MediaEvidence evidence = batch(session("id", 12, 1, 1), MediaEvidence.Proof.UNKNOWN);
        assertEquals(1, evidence.sessions.get(0).anchorAtMs);
        assertFalse(MediaEvidencePolicy.evaluate(evidence, 10000).missing.contains(MediaEvidencePolicy.Reason.CAPTURE_NOT_CURRENT));
        assertTrue(MediaEvidencePolicy.evaluate(evidence, 10000).missing.contains(MediaEvidencePolicy.Reason.TIMELINE_UNKNOWN));
    }

    /** Capture expiry and time reversal are observable even without a new callback. */
    @Test public void captureExpiresAndRejectsFutureCapture() {
        MediaEvidence e = batch(session("id", 12, 9000, 1), MediaEvidence.Proof.OBSERVED);
        assertTrue(MediaEvidencePolicy.evaluate(e, 12501).missing.contains(MediaEvidencePolicy.Reason.CAPTURE_NOT_CURRENT));
        assertTrue(MediaEvidencePolicy.evaluate(e, 9999).missing.contains(MediaEvidencePolicy.Reason.CAPTURE_NOT_CURRENT));
        assertFalse(MediaEvidencePolicy.evaluate(e, 12500).missing.contains(MediaEvidencePolicy.Reason.CAPTURE_NOT_CURRENT));
    }

    /** Multiple sessions cannot be treated as an exact selected context. */
    @Test public void ambiguityIsExplicit() {
        MediaEvidence e = new MediaEvidence(1, 10000, MediaEvidence.Availability.AVAILABLE,
                true, true, 2, Collections.emptyList(), MediaEvidence.Proof.UNKNOWN,
                MediaEvidence.Proof.UNKNOWN, MediaEvidence.Proof.UNKNOWN, MediaEvidence.Proof.UNKNOWN);
        assertTrue(MediaEvidencePolicy.evaluate(e, 10000).missing.contains(MediaEvidencePolicy.Reason.SESSION_AMBIGUOUS));
    }

    /** Missing batch does not become a positive result. */
    @Test public void absentSourceNeverRenders() {
        assertFalse(MediaEvidencePolicy.evaluate(null, 1).renderingAllowed());
        assertFalse(MediaEvidencePolicy.evaluate(null, 1).conditionsComplete());
    }

    /** Bounds are visible, so truncation cannot become a complete programme identifier. */
    @Test public void metadataTruncationIsMarked() {
        MediaEvidence.Session s = session("x".repeat(300), 12, 9000, 1);
        assertEquals(256, s.mediaId.length()); assertFalse(s.complete);
        assertTrue(MediaEvidencePolicy.evaluate(batch(s, MediaEvidence.Proof.OBSERVED),
                10000).missing.contains(MediaEvidencePolicy.Reason.METADATA_INCOMPLETE));
    }

    /** Caller mutation cannot change a published batch. */
    @Test public void sessionsAreDefensiveAndImmutable() {
        List<MediaEvidence.Session> source = new ArrayList<>(); source.add(session("id", 12, 9000, 1));
        MediaEvidence e = new MediaEvidence(1, 10000, MediaEvidence.Availability.AVAILABLE,
                true, true, 1, source, MediaEvidence.Proof.UNKNOWN, MediaEvidence.Proof.UNKNOWN,
                MediaEvidence.Proof.UNKNOWN, MediaEvidence.Proof.UNKNOWN);
        source.clear(); assertEquals(1, e.sessions.size());
        try {e.sessions.clear(); fail();} catch (UnsupportedOperationException expected) {assertEquals(1, e.sessions.size());}
    }

    /** Excess session arrays are refused before retention. */
    @Test(expected = IllegalArgumentException.class) public void oversizedBatchRejected() {
        new MediaEvidence(1, 10000, MediaEvidence.Availability.AVAILABLE, true, true, 17,
                Collections.nCopies(17, session("id", 12, 9000, 1)), MediaEvidence.Proof.UNKNOWN,
                MediaEvidence.Proof.UNKNOWN, MediaEvidence.Proof.UNKNOWN, MediaEvidence.Proof.UNKNOWN);
    }

    /** Read-only diagnostics advertise unknown proofs and forbidden rendering. */
    @Test public void diagnosticDoesNotClaimAdmission() {
        String text = MediaEvidencePolicy.describe(batch(session("id\ncontrol", 12, 9000, 1),
                MediaEvidence.Proof.UNKNOWN), 10000);
        assertTrue(text.contains("Rendering: FORBIDDEN")); assertTrue(text.contains("Edition: UNKNOWN"));
        assertFalse(text.contains("id\ncontrol"));
    }
}
