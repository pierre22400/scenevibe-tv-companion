package com.scenevibe.tvcompanionpoc;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Contract tests for the scoped target, in-memory plan and foreground evidence.
 * They assert privacy/safety outcomes, not the rendering internals or a Sony compatibility claim.
 */
public final class E5LocalPlanAndForegroundTest {
    /** Qualification graphics are parsed by strict v1 and contain no VIDEO primitive. */
    @Test public void graphicalContractRemainsStrictV1() {
        LocalInterludePlan plan = LocalInterludePlan.qualification(true);
        assertEquals(10000, plan.scene.durationMs);
        assertEquals(2, plan.scene.elements.size());
        assertEquals(OverlayManifest.PrimitiveType.RECTANGLE, plan.scene.elements.get(0).type);
        assertEquals(OverlayManifest.PrimitiveType.TEXT, plan.scene.elements.get(1).type);
        assertEquals(5, OverlayManifest.PrimitiveType.values().length);
    }

    /** No arbitrary filesystem/network source is accepted by the local plan. */
    @Test public void fixtureIdentityIsImmutableAndExact() {
        assertEquals("scenevibe_interlude_10s.mp4", LocalInterludePlan.ASSET_NAME);
        assertEquals(2146888, LocalInterludePlan.FILE_BYTES);
        assertEquals("c66ded0b6a2ea41ef34320a289f2c1ec37e2a87d34ef580d9ac45d08815682ec",
                LocalInterludePlan.SHA256);
    }

    /** Integer overflow cannot admit a video rectangle outside the native canvas. */
    @Test(expected = IllegalArgumentException.class)
    public void overflowingFrameRefused() {
        LocalInterludePlan plan = LocalInterludePlan.qualification(true);
        new LocalInterludePlan(plan.scene, new OverlayManifest.Frame(100, 100, Integer.MAX_VALUE, 5),
                0, true, plan.policy);
    }

    /** The physical target remains scoped to Sony Android12, not every Sony/Android device. */
    @Test public void targetSpecificAdmission() {
        assertTrue(NativeQualificationPolicy.supportedTarget("Sony", 31));
        assertFalse(NativeQualificationPolicy.supportedTarget("Samsung", 31));
        assertFalse(NativeQualificationPolicy.supportedTarget("Sony", 35));
        assertFalse(NativeQualificationPolicy.supportedTarget("Sony", 30));
    }

    /** An empty or truncated foreground history never proves Prime visibility. */
    @Test public void missingForegroundEvidenceRefuses() {
        assertFalse(new NativeForegroundProof().primeAlone());
    }

    /** Only a single recently resumed Prime activity is positive evidence. */
    @Test public void solePrimeForegroundAdmitted() {
        NativeForegroundProof proof = new NativeForegroundProof();
        proof.event(1, NativeQualificationPolicy.PRIME_PACKAGE);
        assertTrue(proof.primeAlone());
    }

    /** HOME invalidates the target even if its PAUSED MediaSession survives. */
    @Test public void homeDepartureInvalidatesForegroundProof() {
        NativeForegroundProof proof = new NativeForegroundProof();
        proof.event(1, NativeQualificationPolicy.PRIME_PACKAGE);
        proof.event(2, NativeQualificationPolicy.PRIME_PACKAGE);
        proof.event(1, "launcher");
        assertFalse(proof.primeAlone());
    }

    /** Concurrent resumed apps are ambiguous, not an opportunity for last-package selection. */
    @Test public void concurrentForegroundAppsRefused() {
        NativeForegroundProof proof = new NativeForegroundProof();
        proof.event(1, NativeQualificationPolicy.PRIME_PACKAGE);
        proof.event(1, "other.app");
        assertFalse(proof.primeAlone());
    }

    /** An unknown event cannot create native foreground ownership. */
    @Test public void unrelatedEventDoesNotGrantProof() {
        NativeForegroundProof proof = new NativeForegroundProof();
        proof.event(99, NativeQualificationPolicy.PRIME_PACKAGE);
        assertFalse(proof.primeAlone());
    }

    /** Leaving Diagnostics and resuming Prime permits only the newly observed target. */
    @Test public void explicitReturnToPrimeIsObserved() {
        NativeForegroundProof proof = new NativeForegroundProof();
        proof.event(1, "scenevibe");
        proof.event(2, "scenevibe");
        proof.event(1, NativeQualificationPolicy.PRIME_PACKAGE);
        assertTrue(proof.primeAlone());
    }
}
