package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationSnapshot;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Startup reuses exact durable same-revision semantics for both generic and frozen historical shapes. */
@RunWith(Parameterized.class)
public final class M4PhaseGStartupTest {
    private final boolean manifested,generic;
    /** Select representation independently of native Video ownership. */
    public M4PhaseGStartupTest(boolean manifested, boolean generic) {this.manifested=manifested;this.generic=generic;}
    /** No profile may need a Cloud delivery to become locally armed. */
    @Parameterized.Parameters(name="manifested={0},generic={1}") public static Collection<Object[]> profiles() {
        return M4PhaseGFixtures.profiles();
    }
    /** Restore exact revision/handler/bytes, leaving pending ACK and old residue untouched. */
    @Test public void restoresExactDurablePackageWithZeroWriteAndNoCloudCollaborator() throws Exception {
        Map<String,String> values=M4PhaseGFixtures.durable(manifested,generic),prior=new HashMap<>(values);
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(values);
        InstallationSnapshot durable=h.store.read().snapshot();long ack=h.store.read().acknowledgedRevision();
        assertEquals(InstallationStatus.ARMED,h.restore());assertEquals(durable.revision(),h.runtime.active);
        assertEquals(manifested,h.runtime.controller.hasActiveManifest());assertEquals(1,h.runtime.loads);
        assertEquals(manifested?InstallationStore.COMPAT_OVERLAY_HANDLER_ID:InstallationStore.COMPAT_TRACK_HANDLER_ID,durable.handlerId());
        M4PhaseGFixtures.exact(durable,h.store.read().snapshot());assertEquals(ack,h.store.read().acknowledgedRevision());
        h.unchanged(prior);assertEquals(generic,values.containsKey(InstallationStore.SNAPSHOT_KEY));
        assertEquals(InstallationStatus.ARMED,h.observed.lastStartupRestoreResult());
    }
    /** Startup alone creates no card/badge; only the unchanged scheduler's eligible due event may show. */
    @Test public void armIsInvisibleUntilRealMediaDueEventWithOneVisualOwner() throws Exception {
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(M4PhaseGFixtures.durable(manifested,generic));
        assertEquals(InstallationStatus.ARMED,h.restore());assertEquals(0,h.runtime.shows);
        assertFalse(h.runtime.legacyVisible);assertFalse(h.runtime.manifestVisible);
        h.runtime.due();assertEquals(1,h.runtime.shows);assertEquals(1,h.runtime.maxVisible);
        assertEquals(manifested,h.runtime.manifestVisible);assertEquals(!manifested,h.runtime.legacyVisible);
        assertFalse(OverlayService.shouldShowOnEntry(OverlayService.ACTION_BOOT_PREPARE,false));
        assertFalse(OverlayService.shouldShowOnEntry(OverlayService.ACTION_BOOT_PREPARE,true));
    }
    /** Reconstructed store/installer/media cores restore the same bytes without a new Send or migration. */
    @Test public void processLocalRecreationRestoresPendingOrConfirmedRevision() throws Exception {
        Map<String,String> values=M4PhaseGFixtures.durable(manifested,generic),prior=new HashMap<>(values);
        M4PhaseGFixtures.Startup first=new M4PhaseGFixtures.Startup(values);
        assertEquals(InstallationStatus.ARMED,first.restore());first.runtime.due();first.runtime.live.abortActivation();
        M4PhaseGFixtures.Startup second=new M4PhaseGFixtures.Startup(values);
        assertEquals(InstallationStatus.ARMED,second.restore());assertNotSame(first.runtime.loaded,second.runtime.loaded);
        assertEquals(first.runtime.loaded.trackId,second.runtime.loaded.trackId);assertEquals(0,second.runtime.shows);
        first.unchanged(prior);second.unchanged(prior);
    }
    /** Complete French Unicode survives strict durable decoding and both real handler restoration routes. */
    @Test public void exactUnicodeBytesAndParsedTextSurviveStartup() throws Exception {
        Map<String,String> values=M4PhaseGFixtures.durable(manifested,generic),prior=new HashMap<>(values);
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(values);
        assertEquals(InstallationStatus.ARMED,h.restore());assertEquals(M4PhaseAFixtures.UNICODE,h.runtime.loaded.comments.get(0).text);
        assertArrayEquals(prior.get("runtime").getBytes(StandardCharsets.UTF_8),h.store.read().snapshot().canonical().artifact("runtime"));
        if(manifested)assertArrayEquals(prior.get("manifest").getBytes(StandardCharsets.UTF_8),h.store.read().snapshot().canonical().artifact("manifest"));
        h.unchanged(prior);
    }
    /** A replacement after startup retires the restored owner and cannot accept callbacks from its old generation. */
    @Test public void laterReplacementRetiresRestoredOwnerAndInvalidatesOldCallbacks() throws Exception {
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(M4PhaseGFixtures.durable(manifested,generic));
        assertEquals(InstallationStatus.ARMED,h.restore());h.runtime.due();
        ScheduledTrack.Event old=h.runtime.loaded.comments.get(0);long generation=h.runtime.controller.currentGeneration();
        assertEquals(InstallationStatus.ARMED,h.installer.install(M4PhaseEVideoFixtures.request(!manifested,16),h.runtime.ports));
        assertFalse(h.runtime.manifestVisible);assertFalse(h.runtime.legacyVisible);
        h.runtime.controller.onCommentDue(old,generation);assertFalse(h.runtime.manifestVisible);
        h.runtime.due();h.runtime.controller.onCommentExpired(old,generation);
        assertEquals(16,h.runtime.active);assertEquals(!manifested,h.runtime.manifestVisible);
        assertEquals(manifested,h.runtime.legacyVisible);assertEquals(1,h.runtime.maxVisible);
        assertEquals(1,h.backend.candidateWrites);assertEquals(0,h.backend.ackWrites+h.backend.clears);
    }
}
