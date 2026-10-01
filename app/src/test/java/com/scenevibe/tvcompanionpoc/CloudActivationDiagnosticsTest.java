package com.scenevibe.tvcompanionpoc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Locks the bounded activation-failure diagnostics added after the physical 0.9D qualification.
 *
 * <p>The public Cloud contract intentionally collapses a lost-device-proof failure to the same
 * opaque HTTP 401 / UNAUTHORIZED response as any other activation authentication failure.
 * The TV must surface that bounded fact in Diagnostics instead of swallowing the activation
 * error, but it must never infer an internal server reason or expose a raw response body.
 */
public final class CloudActivationDiagnosticsTest {

    /** A public 401 is surfaced as the existing bounded UNAUTHORIZED diagnostic. */
    @Test public void unauthorizedActivationIsVisibleButOpaque() {
        assertEquals(RuntimeDiagnostics.CloudErrorCode.UNAUTHORIZED,
                CloudControlClient.activationErrorCode(401));
    }

    /** Non-auth activation rejection remains a coarse protocol-shaped diagnostic. */
    @Test public void otherActivationRejectionStaysProtocolShaped() {
        assertEquals(RuntimeDiagnostics.CloudErrorCode.PROTOCOL,
                CloudControlClient.activationErrorCode(400));
    }
}
