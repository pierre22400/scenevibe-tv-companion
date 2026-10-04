package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Preserve wire acceptance while observing canonical local capability refusal and bounded Video diagnostics. */
public final class M4PhaseFCapabilityDiagnosticsTest {
    /** The authorized MEDIA/CONTINUE tightening is a local refusal, never malformed transport or mutation. */
    @Test public void wireValidContinueReturnsUnsupportedWithoutCommitRuntimeMutationOrAck() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(true,14)) {
            h.prior(true,13);h.assignment.getJSONObject("overlayManifest").getJSONObject("clock").put("pauseBehavior","continue");
            assertTrue(CloudProtocol.validAssignment(h.assignment,h.assignment.getString("deviceId"),0));
            CloudV1InstallationAdapter.Assignment mapped=CloudV1InstallationAdapter.adapt(h.assignment,h.assignment.getString("deviceId"));
            assertEquals(InstallationStatus.UNSUPPORTED_CAPABILITY,VideoInstallationHandlers.manifested().validate(
                    mapped.request(),com.scenevibe.tvcompanionpoc.installation.TvCapabilities.current()));
            Map<String,String> before=new HashMap<>(h.backend.values);long generation=h.runtime.controller.currentGeneration();h.poll();
            assertTrue(h.backend.trace.contains("result:UNSUPPORTED_CAPABILITY"));assertEquals(before,h.backend.values);
            assertEquals(0,h.backend.candidateWrites);assertEquals(0,h.runtime.loads);assertEquals(0,h.acks);
            assertEquals(generation,h.runtime.controller.currentGeneration());assertEquals(13,h.runtime.active);
            assertEquals(RuntimeDiagnostics.ManifestCode.NONE,DiagnosticsStore.INSTANCE.lastManifestCode());
            assertEquals(RuntimeDiagnostics.CloudErrorCode.NETWORK,DiagnosticsStore.INSTANCE.lastCloudErrorCode());
        }
    }
    /** The frozen v1 media-only structural boundary rejects wall before any generic install. */
    @Test public void wallRemainsWireProtocolRefusalWithNoOwnerInstall() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(true,14)) {
            h.prior(true,13);h.assignment.getJSONObject("overlayManifest").getJSONObject("clock").put("mode","wall");h.poll();
            assertEquals(0,h.installCalls);assertEquals(0,h.acks);assertEquals(0,h.backend.candidateWrites);
            assertEquals(RuntimeDiagnostics.CloudErrorCode.PROTOCOL,DiagnosticsStore.INSTANCE.lastCloudErrorCode());
        }
    }
    /** Wire-valid timing drift remains a handler-owned MANIFEST_INCONSISTENT rather than PROTOCOL. */
    @Test public void semanticMismatchPreservesManifestInconsistentDiagnostic() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(true,14)) {
            org.json.JSONObject scene=h.assignment.getJSONObject("overlayManifest").getJSONArray("scenes").getJSONObject(0);
            scene.put("startMs",scene.getLong("startMs")+1);h.poll();
            assertEquals(1,h.installCalls);assertEquals(0,h.acks);assertEquals(0,h.backend.candidateWrites);
            assertEquals(RuntimeDiagnostics.ManifestCode.MANIFEST_INCONSISTENT,DiagnosticsStore.INSTANCE.lastManifestCode());
            assertEquals(RuntimeDiagnostics.CloudErrorCode.NETWORK,DiagnosticsStore.INSTANCE.lastCloudErrorCode());
        }
    }
    /** Runtime semantic corruption passes its wire shape but fails through the qualified handler diagnostic. */
    @Test public void invalidRuntimePreservesManifestInvalidDiagnostic() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(true,14)) {
            h.assignment.getJSONObject("runtimeTrack").getJSONArray("comments").getJSONObject(0).put("media",org.json.JSONObject.NULL);h.poll();
            assertEquals(0,h.acks);assertEquals(0,h.backend.candidateWrites);
            assertEquals(RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID,DiagnosticsStore.INSTANCE.lastManifestCode());
        }
    }
    /** A later fully confirmed delivery clears prior bounded Cloud and manifest failures. */
    @Test public void successfulAckClearsPreviousBoundedErrors() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(true,14)) {
            DiagnosticsStore.INSTANCE.setLastCloudErrorCode(RuntimeDiagnostics.CloudErrorCode.NETWORK);
            DiagnosticsStore.INSTANCE.setLastManifestCode(RuntimeDiagnostics.ManifestCode.MANIFEST_INCONSISTENT);h.fetch();
            assertEquals(RuntimeDiagnostics.CloudErrorCode.NONE,DiagnosticsStore.INSTANCE.lastCloudErrorCode());
            assertEquals(RuntimeDiagnostics.ManifestCode.NONE,DiagnosticsStore.INSTANCE.lastManifestCode());
            assertEquals(14,DiagnosticsStore.INSTANCE.lastSuccessfulAckRevision());
        }
    }
}
