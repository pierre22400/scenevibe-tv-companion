package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.PackageInstaller;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Test;
import static com.scenevibe.tvcompanionpoc.M4PhaseEVideoFixtures.*;
import static org.junit.Assert.*;

/** Exercise the real Phase D semantics through Phase E's local durable and revision authority. */
public final class M4PhaseEVideoInstallerTest {
    /** Frozen manifested bytes install once, restore into actual cores, and arm without displaying or confirming. */
    @Test public void frozenManifestedPackageArmsFromOneDurableCommit() throws Exception {
        Harness test=new Harness();assertEquals(InstallationStatus.ARMED,test.install(true,13));
        exact(request(true,13),test.store.read().snapshot());assertEquals(InstallationStore.COMPAT_OVERLAY_HANDLER_ID,test.store.read().snapshot().handlerId());
        assertEquals(1,test.backend.writes);assertEquals(0,test.backend.ackWrites);assertEquals(0,test.store.read().acknowledgedRevision());
        assertEquals(13,test.ports.controller.activeRevision());assertEquals(13,test.ports.activeRevision);
        assertFalse(test.ports.manifestVisible);assertFalse(test.ports.legacyVisible);
        assertEquals(1,test.ports.loads);test.ports.due();assertTrue(test.ports.manifestVisible);assertFalse(test.ports.legacyVisible);
    }

    /** Frozen legacy bytes select only the actual media scheduler's legacy visual path. */
    @Test public void frozenLegacyPackageArmsFromOneDurableCommit() throws Exception {
        Harness test=new Harness();assertEquals(InstallationStatus.ARMED,test.install(false,14));
        exact(request(false,14),test.store.read().snapshot());assertEquals(InstallationStore.COMPAT_TRACK_HANDLER_ID,test.store.read().snapshot().handlerId());
        assertEquals(1,test.backend.writes);assertEquals(0,test.backend.ackWrites);assertNull(test.ports.armed);
        assertFalse(test.ports.controller.hasActiveManifest());assertEquals(14,test.ports.activeRevision);
        test.ports.due();assertTrue(test.ports.legacyVisible);assertFalse(test.ports.manifestVisible);
    }

    /** Manifested-to-legacy replacement synchronously retires the old scene and neutralizes old callbacks. */
    @Test public void manifestedToLegacyReplacementHasOneDurableHandlerAndVisualOwner() throws Exception {
        Harness test=new Harness();test.install(true,13);test.ports.due();test.confirmPrior(13);
        ScheduledTrack.Event old=test.ports.loaded.comments.get(0);long generation=test.ports.controller.currentGeneration();
        assertEquals(InstallationStatus.ARMED,test.install(false,14));
        assertEquals(InstallationStore.COMPAT_TRACK_HANDLER_ID,test.store.read().snapshot().handlerId());
        assertNull(test.store.read().snapshot().canonical().artifact("manifest"));assertEquals(13,test.store.read().acknowledgedRevision());
        assertFalse(test.ports.manifestVisible);assertFalse(test.ports.controller.hasActiveManifest());test.ports.due();
        test.ports.controller.onEventDue(old.id,generation);test.ports.controller.onEventExpired(old.id,generation);
        assertTrue(test.ports.legacyVisible);assertFalse(test.ports.manifestVisible);assertEquals(1,test.ports.maxVisible);
        assertEquals(1,test.backend.writes);assertEquals(0,test.backend.ackWrites);
    }

    /** Legacy-to-manifested replacement retires the old card before native scene ownership can be selected. */
    @Test public void legacyToManifestedReplacementHasOneDurableHandlerAndVisualOwner() throws Exception {
        Harness test=new Harness();test.install(false,14);test.ports.due();test.confirmPrior(14);
        assertEquals(InstallationStatus.ARMED,test.install(true,15));
        assertEquals(InstallationStore.COMPAT_OVERLAY_HANDLER_ID,test.store.read().snapshot().handlerId());
        assertFalse(test.ports.legacyVisible);test.ports.due();assertTrue(test.ports.manifestVisible);assertFalse(test.ports.legacyVisible);
        assertEquals(1,test.ports.maxVisible);assertEquals(14,test.store.read().acknowledgedRevision());
        assertEquals(1,test.backend.writes);assertEquals(0,test.backend.ackWrites);
    }

    /** Replacing a visible manifested revision invalidates old due/expiry generations under the actual controller. */
    @Test public void newerManifestedRevisionSuppressesOldGenerationCallbacks() throws Exception {
        Harness test=new Harness();test.install(true,13);test.ports.due();
        ScheduledTrack.Event old=test.ports.loaded.comments.get(0);long generation=test.ports.controller.currentGeneration();
        test.resetObservation();assertEquals(InstallationStatus.ARMED,test.install(true,14));
        int shows=test.ports.shows;test.ports.controller.onEventDue(old.id,generation);assertEquals(shows,test.ports.shows);
        test.ports.due();test.ports.controller.onEventExpired(old.id,generation);assertTrue(test.ports.manifestVisible);
        assertEquals(14,test.ports.controller.activeRevision());assertEquals(1,test.ports.maxVisible);assertEquals(1,test.backend.writes);
    }

    /** Parser-valid manifested MEDIA/CONTINUE is refused as an unsupported executable profile before storage. */
    @Test public void manifestedContinueProfileIsRejectedBeforeCommitAndLiveMutation() throws Exception {
        Harness test=new Harness();JSONObject manifest=M4PhaseDHandlerFixtures.manifest();
        manifest.getJSONObject("clock").put("pauseBehavior","continue");OverlayManifestParser.parse(manifest);
        assertEquals(InstallationStatus.UNSUPPORTED_CAPABILITY,test.installer.install(M4PhaseDHandlerFixtures.request(M4PhaseDHandlerFixtures.runtime(true),manifest),test.ports));
        assertEquals(0,test.backend.writes+test.ports.loads+test.ports.shows);assertEquals(0,test.ports.activeRevision);
    }

    /** Wall parser acceptance never advertises wall-clock execution or removes a current qualified scene. */
    @Test public void wallProfileIsRejectedBeforeCommitWithoutRetiringCurrentScene() throws Exception {
        Harness test=new Harness();test.install(true,13);test.ports.due();test.confirmPrior(13);
        JSONObject manifest=M4PhaseDHandlerFixtures.manifest();manifest.getJSONObject("clock").put("mode","wall");OverlayManifestParser.parse(manifest);
        Map<String,String> before=new HashMap<>(test.backend.values);long generation=test.ports.controller.currentGeneration();int loads=test.ports.loads;
        assertEquals(InstallationStatus.UNSUPPORTED_CAPABILITY,test.installer.install(M4PhaseDHandlerFixtures.request(M4PhaseDHandlerFixtures.runtime(true),manifest),test.ports));
        assertTrue(before.equals(test.backend.values));assertEquals(0,test.backend.writes);assertEquals(loads,test.ports.loads);
        assertEquals(generation,test.ports.controller.currentGeneration());assertTrue(test.ports.manifestVisible);
    }

    /** A supported executable profile still needs exact Video cross-contract timing coherence. */
    @Test public void invalidCrossContractTimingIsInvalidBeforeCommit() throws Exception {
        Harness test=new Harness();JSONObject manifest=M4PhaseDHandlerFixtures.manifest();
        manifest.getJSONArray("scenes").getJSONObject(0).put("startMs",12001);OverlayManifestParser.parse(manifest);
        assertEquals(InstallationStatus.INVALID_PACKAGE,test.installer.install(M4PhaseDHandlerFixtures.request(M4PhaseDHandlerFixtures.runtime(true),manifest),test.ports));
        assertEquals(0,test.backend.writes+test.ports.loads+test.ports.shows);
    }

    /** A mismatched Video source cannot commit merely because its outer generic model is bounded. */
    @Test public void invalidCrossContractSourceIsInvalidBeforeCommit() throws Exception {
        Harness test=new Harness();JSONObject manifest=M4PhaseDHandlerFixtures.manifest();manifest.getJSONObject("source").put("sourceId","foreign-source");
        assertEquals(InstallationStatus.INVALID_PACKAGE,test.installer.install(M4PhaseDHandlerFixtures.request(M4PhaseDHandlerFixtures.runtime(true),manifest),test.ports));
        assertEquals(0,test.backend.writes+test.ports.loads);
    }

    /** Same-revision manifested redelivery cannot replace durable commentary with changed incoming text. */
    @Test public void sameRevisionManifestedChangedBytesRestoreDurableCopyWithoutRewrite() throws Exception {
        sameRevision(true,13);
    }

    /** Same-revision legacy redelivery cannot replace the durable text track. */
    @Test public void sameRevisionLegacyChangedBytesRestoreDurableCopyWithoutRewrite() throws Exception {
        sameRevision(false,14);
    }

    /** Construct genuinely different bounded incoming bytes, then prove the real durable handler is restored. */
    private static void sameRevision(boolean manifested,long revision) throws Exception {
        Harness test=new Harness();test.install(manifested,revision);test.confirmPrior(revision);
        Map<String,String> before=new HashMap<>(test.backend.values);ScheduledTrack previous=test.ports.loaded;
        InstallRequest incoming=M4PhaseDHandlerFixtures.withArtifact(request(manifested,revision),"runtime","not valid incoming JSON".getBytes(StandardCharsets.UTF_8));
        assertEquals(InstallationStatus.ARMED,test.installer.install(incoming,test.ports));
        assertTrue("no rewrite",before.equals(test.backend.values));assertEquals(0,test.backend.writes+test.backend.ackWrites);
        assertNotSame(previous,test.ports.loaded);assertTrue("durable Unicode text restored",M4PhaseAFixtures.UNICODE.equals(test.ports.loaded.comments.get(0).text));
        exact(request(manifested,revision),test.store.read().snapshot());assertEquals(revision,test.store.read().acknowledgedRevision());
    }

    /** An opposite incoming codec cannot replace a known manifested durable identity at the same revision. */
    @Test public void sameRevisionOtherVideoCodecStillArmsDurableManifestedHandler() throws Exception {
        Harness test=new Harness();test.install(true,13);test.confirmPrior(13);
        assertEquals(InstallationStatus.ARMED,test.installer.install(request(false,13),test.ports));
        assertEquals(InstallationStore.COMPAT_OVERLAY_HANDLER_ID,test.store.read().snapshot().handlerId());
        assertEquals(13,test.ports.controller.activeRevision());assertEquals(0,test.backend.writes);
    }

    /** Exact accented/decomposed/supplementary Unicode survives canonical commit, readback and both parsed cores. */
    @Test public void exactUnicodeBytesAndParsedTextSurviveCommitReadbackRestore() throws Exception {
        Harness test=new Harness();InstallRequest canonical=request(true,13);
        assertEquals(InstallationStatus.ARMED,test.installer.install(canonical,test.ports));exact(canonical,test.store.read().snapshot());
        assertTrue("runtime Unicode unchanged",M4PhaseAFixtures.UNICODE.equals(test.ports.loaded.comments.get(0).text));
        String text=test.ports.armed.scenes.get(0).elements.get(0).children.get(1).text;
        assertTrue("manifest Unicode unchanged",M4PhaseAFixtures.UNICODE.equals(text));
        assertTrue(Arrays.equals(canonical.artifact("runtime"),test.store.read().snapshot().canonical().artifact("runtime")));
    }

    /** After a genuine Video ARM failure a recreated generic installer recovers the durable new revision. */
    @Test public void failedVideoArmRecoversOnSameRevisionWithoutSecondCommit() throws Exception {
        Harness test=new Harness();test.install(true,13);test.confirmPrior(13);test.ports.failAt="manifest";
        assertEquals(InstallationStatus.ARM_FAILED,test.install(true,14));assertEquals(14,test.store.read().snapshot().revision());
        assertEquals(13,test.store.read().acknowledgedRevision());Map<String,String> pending=new HashMap<>(test.backend.values);
        test.resetObservation();Ports recreatedPorts=new Ports(test.backend.trace);
        PackageInstaller recreated=new PackageInstaller(new InstallationStore(test.backend),VideoInstallationHandlers.registry(),TvCapabilities.current());
        InstallRequest ignored=new InstallRequest(14,"unknown.codec.v1",Collections.singletonMap("body",new byte[]{(byte)255}));
        assertEquals(InstallationStatus.ARMED,recreated.install(ignored,recreatedPorts));
        assertEquals(14,recreatedPorts.activeRevision);assertEquals(0,test.backend.writes+test.backend.ackWrites);
        assertTrue("pending bytes reused",pending.equals(test.backend.values));assertEquals(13,test.store.read().acknowledgedRevision());
    }

    /** Corrupt stale bytes do not reach the Video parser or change a visible current revision. */
    @Test public void staleMalformedVideoPayloadDoesNotParseWriteOrRetireScene() throws Exception {
        Harness test=new Harness();test.install(true,13);test.ports.due();test.confirmPrior(13);
        int loads=test.ports.loads;long generation=test.ports.controller.currentGeneration();
        InstallRequest malformed=M4PhaseDHandlerFixtures.withArtifact(request(true,12),"runtime",new byte[]{(byte)255});
        assertEquals(InstallationStatus.STALE,test.installer.install(malformed,test.ports));
        assertEquals(0,test.backend.writes);assertEquals(loads,test.ports.loads);assertTrue(test.ports.manifestVisible);
        assertEquals(generation,test.ports.controller.currentGeneration());
    }

    /** Missing owner authority keeps a valid committed Video candidate pending and does not mutate live cores. */
    @Test public void nonOwnerVideoArmFailsWithoutRuntimeMutationOrConfirmation() throws Exception {
        Harness test=new Harness();test.ports.owner=false;
        assertEquals(InstallationStatus.ARM_FAILED,test.install(true,13));assertEquals(1,test.backend.writes);
        assertEquals(0,test.ports.loads+test.ports.shows);assertEquals(0,test.ports.activeRevision);assertEquals(0,test.backend.ackWrites);
        assertEquals(13,test.store.read().snapshot().revision());
    }
}
