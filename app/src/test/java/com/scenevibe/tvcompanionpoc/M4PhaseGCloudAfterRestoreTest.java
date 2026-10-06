package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Startup followed by the byte-unchanged F client retains one owner authority and exact confirmed-ACK ordering. */
@RunWith(Parameterized.class)
public final class M4PhaseGCloudAfterRestoreTest {
    private final boolean manifested;
    /** Both supported profiles retain the same live client/installer sequence after restoration. */
    public M4PhaseGCloudAfterRestoreTest(boolean manifested) {this.manifested=manifested;}
    /** No separate legacy revision or ACK path may reappear after startup. */
    @Parameterized.Parameters(name="manifested={0}") public static Collection<Object[]> profiles() {
        return Arrays.asList(new Object[][]{{true},{false}});
    }
    /** Same pending revision restores before Cloud, then re-arms without commit and confirms only the server's ACK. */
    @Test public void sameRevisionRedeliveryAfterStartupRearmsWithoutCommitThenConfirmsAck() throws Exception {
        try(M4PhaseFFixtures.Harness h=installed(15)) {
            Map<String,String> prior=new HashMap<>(h.backend.values);restore(h);
            assertEquals(prior,h.backend.values);assertEquals(0,h.gets+h.acks+h.backend.ackWrites+h.backend.candidateWrites);
            h.assignment.put("finalTrackId","separate-final-ACK-binding");
            h.assignment.getJSONObject("runtimeTrack").getJSONArray("comments").getJSONObject(0).put("text","changed incoming bytes");
            h.onAck=()->{assertEquals(15,h.runtime.active);assertEquals(13,h.store.read().acknowledgedRevision());assertEquals(0,h.backend.ackWrites);};
            h.fetch();assertEquals(0,h.backend.candidateWrites+h.backend.clears);assertEquals(1,h.backend.ackWrites);assertEquals(1,h.acks);
            assertEquals(15,h.store.read().acknowledgedRevision());assertTrue(h.getPath.endsWith("afterRevision=13"));
            assertEquals("separate-final-ACK-binding",h.ackBody.getString("finalTrackId"));assertFalse(h.ackBody.has("trackId"));
            assertEquals(M4PhaseAFixtures.UNICODE,h.runtime.loaded.comments.get(0).text);
            assertTrue(h.backend.trace.indexOf("result:ARMED")<h.backend.trace.indexOf("ACK"));
            assertTrue(h.backend.trace.indexOf("ACK")<h.backend.trace.indexOf("ack-persist"));
            assertEquals(prior.get(InstallationStore.SNAPSHOT_KEY),h.backend.values.get(InstallationStore.SNAPSHOT_KEY));
        }
    }
    /** A newer live delivery publishes one candidate after restored visible ownership, then arms before ACK. */
    @Test public void newerDeliveryAfterRestoreCommitsOnceAndRetiresVisibleOwner() throws Exception {
        try(M4PhaseFFixtures.Harness h=installed(16)) {
            restore(h);h.onOwner(()->{h.runtime.due();return null;});
            ScheduledTrack.Event old=h.runtime.loaded.comments.get(0);long generation=h.runtime.controller.currentGeneration();
            h.onAck=()->{assertEquals(16,h.runtime.active);assertEquals(13,h.store.read().acknowledgedRevision());};h.fetch();
            assertEquals(1,h.backend.candidateWrites);assertEquals(1,h.acks);assertEquals(16,h.store.read().acknowledgedRevision());
            assertFalse(h.runtime.manifestVisible);assertFalse(h.runtime.legacyVisible);
            h.onOwner(()->{h.runtime.controller.onEventDue(old.id,generation);h.runtime.due();
                h.runtime.controller.onEventExpired(old.id,generation);return null;});
            assertEquals(manifested,h.runtime.manifestVisible);assertEquals(!manifested,h.runtime.legacyVisible);
            assertEquals(1,h.runtime.maxVisible);
        }
    }
    /** Rejected HTTP ACK after startup keeps the original pending durable revision until valid confirmation. */
    @Test public void pendingStartupRetriesServerConfirmationWithoutRewritingSnapshot() throws Exception {
        try(M4PhaseFFixtures.Harness h=installed(15)) {
            restore(h);Map<String,String> prior=new HashMap<>(h.backend.values);h.badAck=true;
            M4PhaseFFixtures.refused(h);assertEquals(prior,h.backend.values);assertEquals(0,h.backend.ackWrites+h.backend.candidateWrites);
            h.badAck=false;h.fetch();assertEquals(15,h.store.read().acknowledgedRevision());assertEquals(2,h.acks);
            assertEquals(0,h.backend.candidateWrites);assertEquals(1,h.backend.ackWrites);
        }
    }
    /** The same corrupt durable view blocks local restoration and Cloud before any fetch or overwrite. */
    @Test public void corruptStateBlocksBothStartupAndCloudMutation() throws Exception {
        try(M4PhaseFFixtures.Harness h=installed(16)) {
            h.backend.values.put(InstallationStore.SNAPSHOT_KEY,"corrupt");Map<String,String> prior=new HashMap<>(h.backend.values);
            assertEquals(InstallationStatus.CACHE_FAILED,h.onOwner(()->OverlayService.restoreInstalledPackage(
                    h.store,h.installer,h.runtime.ports,DiagnosticsStore.INSTANCE)));
            M4PhaseFFixtures.refused(h);assertEquals(prior,h.backend.values);assertEquals(0,h.runtime.active);
            assertEquals(0,h.gets+h.installCalls+h.acks+h.backend.candidateWrites+h.backend.ackWrites+h.backend.clears);
        }
    }
    /** Manifested MEDIA/CONTINUE remains a local UNSUPPORTED refusal after a restored manifested or legacy owner. */
    @Test public void continueCapabilityTighteningDoesNotMutateRestoredStateOrAck() throws Exception {
        try(M4PhaseFFixtures.Harness h=installed(16)) {
            restore(h);Map<String,String> prior=new HashMap<>(h.backend.values);
            h.assignment=M4PhaseFFixtures.envelope(true,16);
            h.assignment.getJSONObject("overlayManifest").getJSONObject("clock").put("pauseBehavior","continue");
            assertTrue(CloudProtocol.validAssignment(h.assignment,h.assignment.getString("deviceId"),0));h.poll();
            assertTrue(h.backend.trace.contains("result:UNSUPPORTED_CAPABILITY"));assertEquals(prior,h.backend.values);
            assertEquals(15,h.runtime.active);assertEquals(0,h.acks+h.backend.candidateWrites+h.backend.ackWrites);
            assertEquals(RuntimeDiagnostics.CloudErrorCode.NETWORK,DiagnosticsStore.INSTANCE.lastCloudErrorCode());
        }
    }
    /** The unchanged F coordinated reset remains asynchronous and clears restored durable/runtime state. */
    @Test public void runningResetAfterStartupClearsDurableAndRuntimeOnTheirOwners() throws Exception {
        try(M4PhaseFFixtures.Harness h=installed(15)) {
            restore(h);h.onOwner(()->{h.runtime.due();return null;});CountDownLatch done=new CountDownLatch(1);
            h.client.reset(done::countDown);assertTrue(done.await(3,TimeUnit.SECONDS));
            assertEquals(InstallationStore.ReadState.EMPTY,h.store.read().state());assertEquals(0,h.runtime.active);
            assertFalse(h.runtime.manifestVisible);assertFalse(h.runtime.legacyVisible);assertNull(h.credentials.deviceToken());
            assertEquals(h.ownerThread,h.backend.clearThread);assertEquals(h.ownerThread,h.runtime.resetThread);
            assertNull(DiagnosticsStore.INSTANCE.lastStartupRestoreResult());assertEquals(1,h.rotations);
        }
    }
    /** Compose actual F HTTP/owner collaborators only for live-after-startup integration. */
    private M4PhaseFFixtures.Harness installed(long delivery) throws Exception {
        return new M4PhaseFFixtures.Harness(M4PhaseGFixtures.durable(manifested,true),M4PhaseFFixtures.envelope(manifested,delivery));
    }
    /** Invoke the actual startup seam on the same owner as subsequent F client installation, before network work. */
    private static void restore(M4PhaseFFixtures.Harness h) throws Exception {
        assertEquals(InstallationStatus.ARMED,h.onOwner(()->OverlayService.restoreInstalledPackage(
                h.store,h.installer,h.runtime.ports,DiagnosticsStore.INSTANCE)));
        assertEquals(15,h.runtime.active);assertEquals(13,h.store.read().acknowledgedRevision());assertEquals(0,h.runtime.shows);
    }
}
