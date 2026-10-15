package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/** E5-041 diagnostics must stay correlated and private without acquiring transport authority. */
public final class NativeSelectionDiagnostics041Test {
    /** A selection failure survives admission and terminal logging under the same attempt ID. */
    @Test public void rejectionReachesTerminalWithSameAttempt() throws Exception {
        NativeDiagnostics diagnostics = new NativeDiagnostics(() -> "REFUSED");
        NativeSelectionTrace trace = new NativeSelectionTrace(NativeSelectionTrace.Operation.SELECT, 4, 100);
        trace.code = NativeSelectionTrace.Code.FOREGROUND_REFUSED;
        trace.foreground = NativeSelectionTrace.Foreground.NO_RELEVANT_EVENTS;
        trace.finishedAtMs = 102;
        diagnostics.selection(7, trace);
        JSONObject admission = diagnostics.describe("NATIVE_ADMISSION_CHECK", 7, null,
                NativeTransportOwner.Reason.NONE, 103);
        JSONObject terminal = diagnostics.describe("CLEANUP", 7, null,
                NativeTransportOwner.Reason.TARGET, 104);
        assertEquals(admission.getString("attemptId"), terminal.getString("attemptId"));
        assertEquals("TARGET", terminal.getString("reason"));
        assertEquals("FOREGROUND_REFUSED", terminal.getJSONObject("selection").getString("code"));
        assertEquals("NO_RELEVANT_EVENTS", terminal.getJSONObject("selection").getString("foreground"));
        assertEquals(100, terminal.getJSONObject("selection").getLong("selectedAtMs"));
        assertTrue(terminal.getBoolean("terminal"));
    }

    /** Evidence from attempt 7 must never be attached to an unrelated attempt 8. */
    @Test public void observationsDoNotLeakAcrossAttempts() throws Exception {
        NativeDiagnostics diagnostics = new NativeDiagnostics(() -> "PAUSE_REQUESTED");
        diagnostics.selection(7, new NativeSelectionTrace(NativeSelectionTrace.Operation.SELECT, 1, 100));
        assertFalse(diagnostics.describe("NATIVE_ADMISSION_CHECK", 8, null,
                NativeTransportOwner.Reason.NONE, 101).has("selection"));
    }

    /** A new accepted request clears retired query details before any fresh selection occurs. */
    @Test public void newRequestRetiresPreviousSelection() throws Exception {
        NativeDiagnostics diagnostics = new NativeDiagnostics(() -> "PREPARING");
        diagnostics.selection(7, new NativeSelectionTrace(NativeSelectionTrace.Operation.SELECT, 1, 100));
        diagnostics.record("REQUEST", 8, null, NativeTransportOwner.Reason.NONE);
        assertFalse(diagnostics.describe("PREFLIGHT", 8, null,
                NativeTransportOwner.Reason.NONE, 101).has("selection"));
        assertFalse(diagnostics.describe("CLEANUP", 7, null,
                NativeTransportOwner.Reason.TARGET, 102).has("selection"));
    }

    /** The terminal record uses a defensive copy rather than mutable callback-owned diagnostics. */
    @Test public void acceptedTraceIsDefensivelyCopied() throws Exception {
        NativeDiagnostics diagnostics = new NativeDiagnostics(() -> "REFUSED");
        NativeSelectionTrace trace = new NativeSelectionTrace(NativeSelectionTrace.Operation.SELECT, 1, 100);
        trace.code = NativeSelectionTrace.Code.MULTIPLE_PRIME_SESSIONS;
        trace.primeCandidates = 2;
        diagnostics.selection(7, trace);
        trace.code = NativeSelectionTrace.Code.SELECTED;
        trace.primeCandidates = 1;
        JSONObject observed = diagnostics.describe("CLEANUP", 7, null,
                NativeTransportOwner.Reason.TARGET, 103).getJSONObject("selection");
        assertEquals("MULTIPLE_PRIME_SESSIONS", observed.getString("code"));
        assertEquals(2, observed.getInt("primeCandidates"));
    }

    /** Only technical values and salted legacy fingerprints reach structured E5 diagnostics. */
    @Test public void privateNativeContentIsNotExported() throws Exception {
        NativeDiagnostics diagnostics = new NativeDiagnostics(() -> "PAUSE_REQUESTED");
        NativeSelectionTrace trace = new NativeSelectionTrace(NativeSelectionTrace.Operation.SELECT, 1, 100);
        trace.code = NativeSelectionTrace.Code.NATIVE_READ_REFUSED;
        trace.read = NativeSelectionTrace.Read.METADATA_CHANGED;
        diagnostics.selection(7, trace);
        NativeSessionSnapshot sample = new NativeSessionSnapshot(new Object(),
                NativeQualificationPolicy.PRIME_PACKAGE, "secret-media-id", "secret-title", "secret-subtitle",
                -1, 10, 100, 99, 1, 3, true, true, true);
        String encoded = diagnostics.describe("NATIVE_ADMISSION_CHECK", 7, sample,
                NativeTransportOwner.Reason.NONE, 101).toString();
        assertFalse(encoded.contains("secret-"));
        JSONObject selection = new JSONObject(encoded).getJSONObject("selection");
        assertFalse(selection.has("token"));
        assertFalse(selection.has("mediaId"));
        assertFalse(selection.has("packageName"));
        assertEquals("METADATA_CHANGED", selection.getString("read"));
    }

    /** A forged diagnostic SELECTED cannot turn missing session evidence into a PAUSE or PLAY. */
    @Test public void diagnosticCodeCannotAuthorizeTransport() throws Exception {
        E5TestHarness harness = new E5TestHarness();
        harness.session.selectable = false;
        NativeDiagnostics diagnostics = new NativeDiagnostics(() -> "REFUSED");
        NativeSelectionTrace forged = new NativeSelectionTrace(NativeSelectionTrace.Operation.SELECT, 1, 100);
        forged.code = NativeSelectionTrace.Code.SELECTED;
        diagnostics.selection(1, forged);
        NativeTransportOwner owner = new NativeTransportOwner(harness.session, harness.policy,
                diagnostics, harness.clock::nowMs);
        assertFalse(owner.requestPause(1, harness.clock.now, true));
        assertEquals(NativeTransportOwner.Reason.TARGET, owner.reason());
        assertEquals(0, harness.session.pauses);
        assertFalse(owner.requestPlay(1, harness.clock.now, true));
        assertEquals(0, harness.session.plays);
    }
}
