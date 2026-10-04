package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Drive both actual Cloud v1 codec routes through the same generic client and ACK state machine. */
@RunWith(Parameterized.class)
public final class M4PhaseFCloudClientTest {
    private final boolean manifested;
    /** Choose only the adapter's supported frozen manifested or historical legacy projection. */
    public M4PhaseFCloudClientTest(boolean manifested) {this.manifested=manifested;}
    /** Both routes must share one install and one confirmation policy. */
    @Parameterized.Parameters(name="manifested={0}") public static Collection<Object[]> profiles() {
        return Arrays.asList(new Object[][]{{true},{false}});
    }
    /** Actual io adaptation and owner installation complete before HTTP ACK and separate local confirmation. */
    @Test public void realPipelineOrdersOneOwnerInstallBeforeExactAckAndConfirmation() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(manifested,14)) {
            h.fetch();List<String> trace=h.backend.trace;
            assertEquals(1,h.installCalls);assertEquals(h.ownerThread,h.installThread);assertNotEquals(h.fetchThread,h.installThread);
            assertEquals(1,h.backend.candidateWrites);assertEquals(1,h.acks);assertEquals(1,h.backend.ackWrites);
            assertTrue(trace.indexOf("GET")<trace.indexOf("install"));assertTrue(trace.indexOf("commit")<trace.indexOf("load"));
            assertTrue(trace.indexOf("selected")<trace.indexOf("result:ARMED"));
            assertTrue(trace.indexOf("result:ARMED")<trace.indexOf("ACK"));assertTrue(trace.indexOf("ACK")<trace.indexOf("ack-persist"));
            assertEquals(2,h.ackBody.length());assertEquals(14,h.ackBody.getLong("revision"));
            assertEquals(h.assignment.getString("finalTrackId"),h.ackBody.getString("finalTrackId"));
            assertFalse(h.ackBody.has("trackId"));assertEquals(14,h.store.read().acknowledgedRevision());
            assertEquals(manifested,h.runtime.controller.hasActiveManifest());assertFalse(h.runtime.manifestVisible);assertFalse(h.runtime.legacyVisible);
        }
    }
    /** finalTrackId remains exactly the envelope's ACK binding even when distinct from the runtime track id. */
    @Test public void ackUsesOriginalFinalTrackIdNeverRuntimeTrackId() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(manifested,14)) {
            h.assignment.put("finalTrackId","original-Été-ACK-binding");h.fetch();
            assertEquals("original-Été-ACK-binding",h.ackBody.getString("finalTrackId"));
            assertNotEquals(h.assignment.getString("trackId"),h.ackBody.getString("finalTrackId"));
        }
    }
    /** Generic snapshot/ACK win over any stale or semantically corrupt historical residue. */
    @Test public void pollingReadsExactGenericAcknowledgedRevision() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(manifested,15)) {
            h.prior(manifested,14);h.backend.values.put("revision","99");h.backend.values.put("runtime","invalid residue");
            h.fetch();assertTrue(h.getPath.endsWith("?afterRevision=14"));
            assertEquals(15,h.store.read().snapshot().revision());assertEquals(15,h.store.read().acknowledgedRevision());
            assertEquals("99",h.backend.values.get("revision"));assertEquals("invalid residue",h.backend.values.get("runtime"));
        }
    }
    /** An actual failed candidate commit preserves the prior tuple, does not load and sends zero ACK. */
    @Test public void commitRefusalKeepsPriorDurableAndRuntimeRevision() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(manifested,14)) {
            h.prior(manifested,13);Map<String,String> prior=new HashMap<>(h.backend.values);h.backend.candidateWritable=false;
            M4PhaseFFixtures.refused(h);assertEquals(prior,h.backend.values);assertEquals(0,h.runtime.loads);assertEquals(0,h.acks);
            assertEquals(13,h.runtime.active);assertEquals(13,h.store.read().acknowledgedRevision());
            assertEquals(manifested?RuntimeDiagnostics.ManifestCode.MANIFEST_CACHE_FAILED:RuntimeDiagnostics.ManifestCode.NONE,
                    DiagnosticsStore.INSTANCE.lastManifestCode());
        }
    }
    /** ARM failure keeps a pending new revision; same-revision redelivery recovers it without another commit. */
    @Test public void failedArmPendingRevisionRecoversAndThenAcknowledges() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(manifested,14)) {
            h.prior(manifested,13);h.runtime.failAt="selected";M4PhaseFFixtures.refused(h);
            assertEquals(14,h.store.read().snapshot().revision());assertEquals(13,h.store.read().acknowledgedRevision());
            assertEquals(1,h.backend.candidateWrites);assertEquals(0,h.acks);assertEquals(0,h.runtime.active);
            h.runtime.failAt=null;h.fetch();assertEquals(1,h.backend.candidateWrites);assertEquals(1,h.acks);
            assertEquals(14,h.store.read().acknowledgedRevision());assertEquals(14,h.runtime.active);
        }
    }
    /** A server failure cannot convert local durable/armed state into a confirmed ACK. */
    @Test public void rejectedHttpAckCanRetryFromExactPendingSnapshot() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(manifested,14)) {
            h.prior(manifested,13);h.ackStatus=500;M4PhaseFFixtures.refused(h);
            assertEquals(14,h.store.read().snapshot().revision());assertEquals(13,h.store.read().acknowledgedRevision());
            assertEquals(0,h.backend.ackWrites);String bytes=h.backend.values.get(InstallationStore.SNAPSHOT_KEY);
            h.ackStatus=200;h.fetch();assertEquals(bytes,h.backend.values.get(InstallationStore.SNAPSHOT_KEY));
            assertEquals(1,h.backend.candidateWrites);assertEquals(2,h.acks);assertEquals(14,h.store.read().acknowledgedRevision());
            assertTrue(h.getPath.endsWith("?afterRevision=13"));
        }
    }
    /** Existing ACK response validation rejects a wrong confirmed revision before local persistence. */
    @Test public void malformedAckConfirmationNeverPersistsAcknowledgedRevision() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(manifested,14)) {
            h.prior(manifested,13);h.badAck=true;M4PhaseFFixtures.refused(h);
            assertEquals(1,h.acks);assertEquals(0,h.backend.ackWrites);assertEquals(13,h.store.read().acknowledgedRevision());
        }
    }
    /** A server confirmation followed by failed disk ACK remains locally pending and safely retryable. */
    @Test public void failedLocalAckPersistenceDoesNotManufactureConfirmation() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(manifested,14)) {
            h.prior(manifested,13);h.backend.ackWritable=false;M4PhaseFFixtures.refused(h);
            assertEquals(1,h.acks);assertEquals(13,h.store.read().acknowledgedRevision());
            h.backend.ackWritable=true;h.fetch();assertEquals(1,h.backend.candidateWrites);assertEquals(2,h.acks);
            assertEquals(14,h.store.read().acknowledgedRevision());
        }
    }
    /** Same revision restores exact persisted bytes rather than parsing changed incoming semantic content. */
    @Test public void sameRevisionRestoresDurableBytesWithZeroInstallCommit() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(manifested,13)) {
            h.prior(manifested,13);byte[] durable=h.store.read().snapshot().canonical().artifact("runtime");
            h.assignment.getJSONObject("runtimeTrack").getJSONArray("comments").getJSONObject(0).put("text","different incoming");
            h.fetch();assertEquals(0,h.backend.candidateWrites);assertEquals(1,h.runtime.loads);assertEquals(1,h.acks);
            assertArrayEquals(durable,h.store.read().snapshot().canonical().artifact("runtime"));
            assertNotEquals("different incoming",h.runtime.loaded.comments.get(0).text);
        }
    }
    /** The durable codec wins even when a same-revision incoming envelope has the opposite Video shape. */
    @Test public void sameRevisionUsesDurableHandlerRatherThanIncomingCodec() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(!manifested,13)) {
            h.prior(manifested,13);h.fetch();assertEquals(0,h.backend.candidateWrites);assertEquals(1,h.acks);
            assertEquals(manifested,h.runtime.controller.hasActiveManifest());assertEquals(13,h.runtime.active);
        }
    }
    /** Wire-valid stale responses reach the sole generic revision authority and cannot mutate or ACK. */
    @Test public void staleResponseCannotOverwriteOrAcknowledge() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(manifested,13)) {
            h.prior(manifested,14);Map<String,String> prior=new HashMap<>(h.backend.values);M4PhaseFFixtures.refused(h);
            assertTrue(h.backend.trace.contains("result:STALE"));assertEquals(prior,h.backend.values);
            assertEquals(0,h.acks);assertEquals(0,h.runtime.loads);assertEquals(14,h.runtime.active);
        }
    }
    /** Both ordinary no-assignment responses leave live/durable state unchanged. */
    @Test public void emptyPollingResponsesRemainNormalAndDoNotInstall() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(manifested,14)) {
            h.assignmentStatus=204;h.fetch();h.assignmentStatus=404;h.fetch();
            assertEquals(2,h.gets);assertEquals(0,h.installCalls);assertEquals(0,h.acks);
            assertEquals(InstallationStore.ReadState.EMPTY,h.store.read().state());
        }
    }
    /** Authentication refusal remains bounded and cannot auto-reset credentials or installed state. */
    @Test public void unauthorizedPollingPreservesCredentialAndCache() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(manifested,14)) {
            h.prior(manifested,13);h.assignmentStatus=401;h.fetch();
            assertEquals(RuntimeDiagnostics.CloudErrorCode.UNAUTHORIZED,DiagnosticsStore.INSTANCE.lastCloudErrorCode());
            assertNotNull(h.credentials.deviceToken());assertEquals(13,h.store.read().snapshot().revision());assertEquals(0,h.installCalls);
        }
    }
    /** Normal disconnect keeps the installed package and passive runtime available offline. */
    @Test public void disconnectPreservesGenericSnapshotAndRuntime() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(manifested,14)) {
            h.prior(manifested,13);Map<String,String> before=new HashMap<>(h.backend.values);h.client.disconnect();h.poll();
            assertEquals(before,h.backend.values);assertEquals(13,h.runtime.active);assertEquals(0,h.gets);assertNotNull(h.credentials.deviceToken());
        }
    }
    /** Exact canonical bytes survive actual HTTP decode, commit/readback and real handler restoration. */
    @Test public void unicodeSurvivesCloudAndDurableHandlerRestore() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(manifested,14)) {
            Map<String,String> fixture=M4PhaseFFixtures.historical(manifested);
            h.assignment.put("runtimeTrack",new org.json.JSONObject(fixture.get("runtime")));
            if(manifested)h.assignment.put("overlayManifest",new org.json.JSONObject(fixture.get("manifest")));
            byte[] expected=h.assignment.getJSONObject("runtimeTrack").toString().getBytes(StandardCharsets.UTF_8);h.fetch();
            assertArrayEquals(expected,h.store.read().snapshot().canonical().artifact("runtime"));
            assertEquals(M4PhaseAFixtures.UNICODE,h.runtime.loaded.comments.get(0).text);
            assertEquals(InstallationStatus.ARMED,h.onOwner(()->new com.scenevibe.tvcompanionpoc.installation.PackageInstaller(
                    h.store,VideoInstallationHandlers.registry(),com.scenevibe.tvcompanionpoc.installation.TvCapabilities.current())
                    .install(CloudV1InstallationAdapter.adapt(h.assignment,h.assignment.getString("deviceId")).request(),h.runtime.ports)));
            assertEquals(1,h.backend.candidateWrites);assertEquals(M4PhaseAFixtures.UNICODE,h.runtime.loaded.comments.get(0).text);
        }
    }
}
