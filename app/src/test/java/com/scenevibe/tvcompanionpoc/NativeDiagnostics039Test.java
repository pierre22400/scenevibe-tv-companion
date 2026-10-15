package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/** Verify the structured ADB evidence independently from Android logging and native transport. */
public final class NativeDiagnostics039Test {
    /** Observe fresh and genuinely old positions without printing metadata or a reusable token. */
    @Test public void diagnosticCarriesEvidenceAndProtectsContent() throws Exception {
        NativeDiagnostics diagnostics = new NativeDiagnostics(() -> "PAUSE_REQUESTED");
        NativeSessionSnapshot sample = new NativeSessionSnapshot(new Object(),
                NativeQualificationPolicy.PRIME_PACKAGE, "private-content-id", "private-title", "private-subtitle",
                -1, 300000, 100010, 100004, 1f, 3, true, true, true);
        JSONObject fresh = diagnostics.describe("NATIVE_ADMISSION_CHECK", 7, sample,
                NativeTransportOwner.Reason.NONE, 100020);
        assertEquals("SUFFICIENT", fresh.getString("guard"));
        assertEquals(10, fresh.getLong("sampleAgeMs"));
        assertEquals(16, fresh.getLong("providerAgeMs"));
        assertEquals(300016, fresh.getLong("estimatedPositionMs"));
        assertEquals("elapsedRealtimeMs", fresh.getString("clockBasis"));
        assertFalse(fresh.getBoolean("terminal"));
        assertFalse(fresh.getBoolean("durationKnown"));
        assertFalse(fresh.toString().contains("private-"));
        JSONObject old = diagnostics.describe("NATIVE_OBSERVATION", 7, sample,
                NativeTransportOwner.Reason.STALE_POSITION, 120021);
        assertEquals("SAMPLE_STALE", old.getString("guard"));
        assertEquals(fresh.getString("attemptId"), old.getString("attemptId"));
    }

    /** Terminal state is explicit and attempts do not collide across service instances. */
    @Test public void terminalAndAttemptIdentityAreUnambiguous() throws Exception {
        NativeDiagnostics first = new NativeDiagnostics(() -> "REFUSED");
        NativeDiagnostics second = new NativeDiagnostics(() -> "SUCCESS");
        JSONObject denied = first.describe("CLEANUP", 1, null, NativeTransportOwner.Reason.TARGET, 10);
        JSONObject success = second.describe("CLEANUP", 1, null, NativeTransportOwner.Reason.NONE, 10);
        assertTrue(denied.getBoolean("terminal"));
        assertEquals("REFUSED", denied.getString("phase"));
        assertEquals("NO_OBSERVATION", denied.getString("guard"));
        assertNotEquals(denied.getString("attemptId"), success.getString("attemptId"));
        assertEquals("SUCCESS", success.getString("phase"));
    }
}
