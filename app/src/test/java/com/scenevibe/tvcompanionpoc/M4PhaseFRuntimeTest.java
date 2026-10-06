package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.PackageInstaller;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercise the actual nested service ports with real Video handlers, scheduler and controller. */
public final class M4PhaseFRuntimeTest {
    /** Visible manifested-to-legacy replacement retires the former owner before the next comment can show. */
    @Test public void manifestedToLegacyReplacementKeepsOneVisualOwner() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(false,14)) {
            h.prior(true,13);h.onOwner(()->{h.runtime.due();return null;});assertTrue(h.runtime.manifestVisible);
            h.fetch();assertFalse(h.runtime.manifestVisible);assertFalse(h.runtime.controller.hasActiveManifest());
            h.onOwner(()->{h.runtime.due();return null;});assertTrue(h.runtime.legacyVisible);assertEquals(1,h.runtime.maxVisible);
        }
    }
    /** Visible legacy-to-manifested replacement retires the old card synchronously before scene activation. */
    @Test public void legacyToManifestedReplacementKeepsOneVisualOwner() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(true,14)) {
            h.prior(false,13);h.onOwner(()->{h.runtime.due();return null;});assertTrue(h.runtime.legacyVisible);
            h.fetch();assertFalse(h.runtime.legacyVisible);assertFalse(h.runtime.manifestVisible);
            h.onOwner(()->{h.runtime.due();return null;});assertTrue(h.runtime.manifestVisible);assertEquals(1,h.runtime.maxVisible);
        }
    }
    /** Old generation callbacks cannot resurrect or hide a scene after a new manifested revision is selected. */
    @Test public void replacementInvalidatesPreviousGenerationCallbacks() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(true,14)) {
            h.prior(true,13);ScheduledTrack.Event old=h.runtime.loaded.comments.get(0);long generation=h.runtime.controller.currentGeneration();
            h.onOwner(()->{h.runtime.due();return null;});h.fetch();
            h.onOwner(()->{h.runtime.controller.onEventDue(old.id,generation);return null;});assertFalse(h.runtime.manifestVisible);
            h.onOwner(()->{h.runtime.due();h.runtime.controller.onEventExpired(old.id,generation);return null;});
            assertTrue(h.runtime.manifestVisible);assertEquals(14,h.runtime.active);assertEquals(1,h.runtime.maxVisible);
        }
    }
    /** A direct off-owner generic ARM leaves the committed package pending without any runtime mutation. */
    @Test public void offOwnerDirectArmIsRejectedByActualServicePorts() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(true,14)) {
            assertEquals(InstallationStatus.ARM_FAILED,h.installer.install(CloudV1InstallationAdapter.adapt(
                    h.assignment,h.assignment.getString("deviceId")).request(),h.runtime.live));
            assertEquals(0,h.runtime.active);assertEquals(0,h.runtime.loads);assertFalse(h.runtime.controller.hasActiveManifest());
            assertEquals(14,h.store.read().snapshot().revision());assertEquals(0,h.store.read().acknowledgedRevision());
        }
    }
    /** Service ports independently reject every direct operation from the wrong owner, including abort. */
    @Test public void everyDirectMutationRejectsOffOwner() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(true,14)) {
            assertFalse(h.runtime.live.retireLegacyVisualOwner());assertFalse(h.runtime.live.retireManifestedVisualOwner());
            assertFalse(h.runtime.live.loadPreparedVideo(null));assertFalse(h.runtime.live.armPreparedManifest(14,null));
            assertFalse(h.runtime.live.selectActiveRevision(14,true));h.runtime.live.abortActivation();assertTrue(h.backend.trace.isEmpty());
        }
    }
    /** Selection refuses an unarmed/wrong manifested revision and refuses legacy while a manifest is armed. */
    @Test public void selectionRequiresExactControllerOwnership() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(true,14)) {
            assertFalse(h.onOwner(()->h.runtime.live.selectActiveRevision(14,true)));h.prior(true,13);
            assertFalse(h.onOwner(()->h.runtime.live.selectActiveRevision(14,true)));
            assertFalse(h.onOwner(()->h.runtime.live.selectActiveRevision(14,false)));assertEquals(13,h.runtime.active);
        }
    }
    /** Teardown-unavailable runtime fails activation and does not manufacture active selection or ACK. */
    @Test public void unavailableRuntimeKeepsPendingSnapshotUnacknowledged() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(true,14)) {
            h.runtime.available=false;M4PhaseFFixtures.refused(h);assertEquals(0,h.acks);assertEquals(0,h.runtime.active);
            assertEquals(14,h.store.read().snapshot().revision());assertEquals(0,h.store.read().acknowledgedRevision());
        }
    }
    /** Repeated owner abort clears visual/scheduler/selection state without touching durable installation or ACK. */
    @Test public void abortIsIdempotentAndRuntimeOnly() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(true,14)) {
            h.fetch();String encoded=h.backend.values.get(InstallationStore.SNAPSHOT_KEY);int commits=h.backend.candidateWrites,acks=h.backend.ackWrites;
            h.onOwner(()->{h.runtime.due();h.runtime.live.abortActivation();h.runtime.live.abortActivation();return null;});
            assertEquals(0,h.runtime.active);assertFalse(h.runtime.controller.hasActiveManifest());assertFalse(h.runtime.manifestVisible);
            assertEquals(encoded,h.backend.values.get(InstallationStore.SNAPSHOT_KEY));assertEquals(commits,h.backend.candidateWrites);assertEquals(acks,h.backend.ackWrites);
        }
    }
    /** Handler-restored prepared objects are handed to the existing scheduler/controller without reparse in ports. */
    @Test public void actualPortsKeepExactPreparedObjectIdentity() throws Exception {
        M4PhaseFFixtures.Runtime runtime=new M4PhaseFFixtures.Runtime(new ArrayList<>(),()->true);
        com.scenevibe.tvcompanionpoc.installation.PreparedInstallation prepared=VideoInstallationHandlers.manifested().prepare(
                M4PhaseDHandlerFixtures.request(true),TvCapabilities.current());
        VideoPreparedState trusted=(VideoPreparedState)prepared.preparedState();
        assertEquals(InstallationStatus.ARMED,VideoInstallationHandlers.manifested().arm(prepared,runtime.ports));
        assertSame(trusted.track,runtime.loaded);assertSame(trusted.manifest,runtime.armed);
        assertEquals(prepared.revision(),runtime.active);
    }
}
