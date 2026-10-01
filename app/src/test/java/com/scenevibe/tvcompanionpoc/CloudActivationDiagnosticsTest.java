package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;
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
        JSONObject body=new JSONObject()
                .put("type","scenevibe.cloud.error.v1")
                .put("code","UNAUTHORIZED");
        assertEquals(RuntimeDiagnostics.CloudErrorCode.UNAUTHORIZED,
                CloudControlClient.activationErrorCode(401,body));
    }

    /** Non-auth activation rejection remains a coarse protocol-shaped diagnostic. */
    @Test public void otherActivationRejectionStaysProtocolShaped() {
        JSONObject body=new JSONObject()
                .put("type","scenevibe.cloud.error.v1")
                .put("code","BAD_REQUEST");
        assertEquals(RuntimeDiagnostics.CloudErrorCode.PROTOCOL,
                CloudControlClient.activationErrorCode(400,body));
    }

    /** No body is required to classify the HTTP authentication boundary. */
    @Test public void unauthorizedClassificationNeverDependsOnRawBody() {
        assertEquals(RuntimeDiagnostics.CloudErrorCode.UNAUTHORIZED,
                CloudControlClient.activationErrorCode(401,null));
    }
}
