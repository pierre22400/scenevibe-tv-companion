package com.scenevibe.tvcompanionpoc.presentation;

import java.util.Objects;

/**
 * UI state, deliberately separate from runtime admission. A selected card is not a proof.
 * A future trusted adapter must supply the exact binding at each stage after verifying it.
 * This class neither changes the M4/M5 admission policy nor activates an overlay.
 */
public final class TvPreparation {
    public enum Stage { NONE, REQUESTED, AWAITING_CONFIRMATION, PREPARING, READY, FAILED }
    public enum Failure { CONNECTION_UNAVAILABLE, REQUEST_EXPIRED, CONFIRMATION_REFUSED,
        TRACK_UNAVAILABLE, PREPARATION_FAILED }
    public final Stage stage;
    public final Failure failure;
    public final boolean demonstration;
    public final Evidence evidence;

    /** Carries a bounded presentation status, never an executable package. */
    public TvPreparation(Stage stage, Failure failure, boolean demonstration, Evidence evidence) {
        this.stage = Objects.requireNonNull(stage);
        this.failure = failure;
        this.demonstration = demonstration;
        this.evidence = evidence;
    }

    /** Rejects a READY assertion unless account, install and ACK agree with the assignment. */
    public boolean readyFor(String offerId) {
        return stage == Stage.READY && !demonstration && evidence != null
                && evidence.deliveryReady(offerId);
    }

    /** Admission is an independent observation even when delivery has been acknowledged. */
    public boolean mediaAdmittedFor(String offerId) {
        return readyFor(offerId) && evidence.assignment.equals(evidence.mediaAdmission);
    }

    /** Exact opaque identity tuple. Revision is the assignment revision, never editorial. */
    public static final class Binding {
        public final String offerId, intentionId, deviceId, executableId, digest;
        public final long assignmentRevision;

        /** Requires explicit identities and a positive allocated revision. */
        public Binding(String offerId, String intentionId, String deviceId, String executableId,
                String digest, long assignmentRevision) {
            for (String id : new String[] {offerId, intentionId, deviceId, executableId, digest}) {
                if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Incomplete binding");
            }
            if (assignmentRevision <= 0) throw new IllegalArgumentException("Missing allocated revision");
            this.offerId = offerId;
            this.intentionId = intentionId;
            this.deviceId = deviceId;
            this.executableId = executableId;
            this.digest = digest;
            this.assignmentRevision = assignmentRevision;
        }

        /** Compares all authority-bearing coordinates rather than titles or list indices. */
        @Override public boolean equals(Object other) {
            if (!(other instanceof Binding)) return false;
            Binding b = (Binding) other;
            return assignmentRevision == b.assignmentRevision && offerId.equals(b.offerId)
                    && intentionId.equals(b.intentionId) && deviceId.equals(b.deviceId)
                    && executableId.equals(b.executableId) && digest.equals(b.digest);
        }

        /** Matches the complete identity equality contract. */
        @Override public int hashCode() {
            return Objects.hash(offerId, intentionId, deviceId, executableId, digest, assignmentRevision);
        }
    }

    /** Separate facts supplied only by a future runtime adapter, with no inferred transitions. */
    public static final class Evidence {
        public final Binding accountConfirmation, assignment, installation, acknowledged, mediaAdmission;

        /** Null means absent, stale or unverified, and therefore cannot prove readiness. */
        public Evidence(Binding accountConfirmation, Binding assignment, Binding installation,
                Binding acknowledged, Binding mediaAdmission) {
            this.accountConfirmation = accountConfirmation;
            this.assignment = assignment;
            this.installation = installation;
            this.acknowledged = acknowledged;
            this.mediaAdmission = mediaAdmission;
        }

        /** Requires the same offer and the exact confirmed, installed and acknowledged binding. */
        public boolean deliveryReady(String offerId) {
            return assignment != null && assignment.offerId.equals(offerId)
                    && assignment.equals(accountConfirmation) && assignment.equals(installation)
                    && assignment.equals(acknowledged);
        }
    }
}
