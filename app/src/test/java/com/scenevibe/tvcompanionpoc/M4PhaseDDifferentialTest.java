package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Compare handler delegation with the code-exact frozen Phase C repository, not reconstructed expectations. */
public final class M4PhaseDDifferentialTest {
    /** Adapt only the persistence seam so both independent repositories observe equivalent new memories. */
    private static final class ReferenceMemory implements M4PhaseDHistoricalRepository.Storage {
        final M4PhaseAFixtures.Memory memory=new M4PhaseAFixtures.Memory();
        /** Read the same qualified historical keys. */
        @Override public String get(String key) {return memory.get(key);}
        /** Publish a reference legacy tuple atomically. */
        @Override public boolean save(long revision,String runtime) {return memory.save(revision,runtime);}
        /** Publish a reference manifested tuple atomically. */
        @Override public boolean save(long revision,String runtime,String manifest) {return memory.save(revision,runtime,manifest);}
        /** Record a reference acknowledgement without changing its package. */
        @Override public boolean saveAck(long revision) {return memory.saveAck(revision);}
        /** Apply the reference corruption/reset clear only. */
        @Override public void clear() {memory.clear();}
    }
    /** Observe real scheduler loading without any renderer, Android window or new clock. */
    private static final class Loads implements MediaSyncedTrackScheduler.Listener {
        int count;
        /** This matrix does not drive playback or display comment content. */
        @Override public void onRender(ScheduledTrack.Event event) {}
        /** This matrix observes load ordering only. */
        @Override public void onPlayback(boolean playing,boolean freeze) {}
        /** Every load emits the existing false eligibility transition. */
        @Override public void onEligibility(boolean eligible) {if (!eligible) count++;}
    }
    /** Compare current/historical acceptance, diagnostics, cache bytes, restoration and actual load counts. */
    private static void compare(String runtime,String manifest,InstallationStatus capabilityDifference) {
        ReferenceMemory oldMemory=new ReferenceMemory();M4PhaseAFixtures.Memory currentMemory=new M4PhaseAFixtures.Memory();
        M4PhaseDHistoricalRepository old=new M4PhaseDHistoricalRepository(oldMemory);
        CloudTrackRepository current=new CloudTrackRepository(currentMemory);
        Loads oldLoads=new Loads(),currentLoads=new Loads();
        MediaSyncedTrackScheduler oldScheduler=new MediaSyncedTrackScheduler(oldLoads),currentScheduler=new MediaSyncedTrackScheduler(currentLoads);
        boolean accepted;
        if (manifest!=null) {
            M4PhaseDHistoricalRepository.InstallResult original=old.install(21,runtime,manifest,oldScheduler);
            CloudTrackRepository.InstallResult delegated=current.install(21,runtime,manifest,currentScheduler);
            assertEquals(original.ok,delegated.ok);assertEquals(original.code,delegated.code);accepted=original.ok;
            M4PhaseDHistoricalRepository.RestoreResult originalRestore=old.restoreWithManifest(oldScheduler);
            CloudTrackRepository.RestoreResult delegatedRestore=current.restoreWithManifest(currentScheduler);
            assertEquals(originalRestore.ok,delegatedRestore.ok);assertEquals(originalRestore.revision,delegatedRestore.revision);
        } else {
            accepted=old.install(21,runtime,oldScheduler);assertEquals(accepted,current.install(21,runtime,currentScheduler));
            assertEquals(old.restore(oldScheduler),current.restore(currentScheduler));
        }
        assertTrue("byte-identical differential cache",oldMemory.memory.values.equals(currentMemory.values));
        assertEquals(oldMemory.memory.commits,currentMemory.commits);assertEquals(oldMemory.memory.clears,currentMemory.clears);
        assertEquals(oldLoads.count,currentLoads.count);assertEquals(old.revision(),current.revision());assertEquals(old.acknowledged(),current.acknowledged());
        Map<String,byte[]> artifacts=new TreeMap<>();artifacts.put("runtime",runtime.getBytes(StandardCharsets.UTF_8));
        if (manifest!=null) artifacts.put("manifest",manifest.getBytes(StandardCharsets.UTF_8));
        InstallRequest request=new InstallRequest(21,manifest==null?TvCapabilities.CODEC_TRACK:TvCapabilities.CODEC_TRACK_OVERLAY,artifacts);
        InstallationStatus expected=capabilityDifference!=null?capabilityDifference:
                accepted?InstallationStatus.VALIDATED:InstallationStatus.INVALID_PACKAGE;
        assertEquals(expected,(manifest==null?VideoInstallationHandlers.legacy():VideoInstallationHandlers.manifested()).validate(request,TvCapabilities.current()));
    }
    /** Frozen manifested cache bytes retain the exact supported semantic decision. */
    @Test public void frozenManifestedCacheMatchesHistoricalBehavior() throws Exception {
        M4PhaseAFixtures.Memory cache=M4PhaseAFixtures.cacheFixture(true);compare(cache.values.get("runtime"),cache.values.get("manifest"),null);
    }
    /** Frozen no-manifest cache bytes retain the exact legacy install and restore decision. */
    @Test public void frozenLegacyCacheMatchesHistoricalBehavior() throws Exception {
        compare(M4PhaseAFixtures.cacheFixture(false).values.get("runtime"),null,null);
    }
    /** All seven actual producer-envelope projections have equivalent decisions; no independent recopy. */
    @Test public void allFrozenM1EnvelopesMatchHistoricalDecisions() throws Exception {
        for (int i=0;i<7;i++) {
            JSONObject envelope=M4PhaseAFixtures.envelope(i,21);
            compare(envelope.getJSONObject("runtimeTrack").toString(),envelope.has("overlayManifest")?envelope.getJSONObject("overlayManifest").toString():null,null);
        }
    }
    /** Malformed runtime cannot mutate either durable cache or scheduler. */
    @Test public void malformedRuntimeMatchesHistoricalRefusal() throws Exception {compare("{invalid",M4PhaseDHandlerFixtures.manifest().toString(),null);}
    /** Cloud text-only media presence remains exactly rejected. */
    @Test public void commentMediaMatchesHistoricalRefusal() throws Exception {
        JSONObject runtime=M4PhaseDHandlerFixtures.runtime(true);runtime.getJSONArray("comments").getJSONObject(0).put("media",JSONObject.NULL);
        compare(runtime.toString(),M4PhaseDHandlerFixtures.manifest().toString(),null);
    }
    /** Malformed graphical JSON retains its bounded invalid outcome. */
    @Test public void malformedManifestMatchesHistoricalRefusal() throws Exception {compare(M4PhaseDHandlerFixtures.runtime(true).toString(),"{invalid",null);}
    /** Source mismatch remains MANIFEST_INVALID rather than a new diagnostic vocabulary. */
    @Test public void sourceMismatchMatchesHistoricalRefusal() throws Exception {
        JSONObject manifest=M4PhaseDHandlerFixtures.manifest();manifest.getJSONObject("source").put("sourceId","different");
        compare(M4PhaseDHandlerFixtures.runtime(true).toString(),manifest.toString(),null);
    }
    /** A same-count changed scene relation remains MANIFEST_INCONSISTENT. */
    @Test public void sceneSetMismatchMatchesHistoricalRefusal() throws Exception {
        JSONObject manifest=M4PhaseDHandlerFixtures.manifest();manifest.getJSONArray("scenes").getJSONObject(0).put("id","different");
        compare(M4PhaseDHandlerFixtures.runtime(true).toString(),manifest.toString(),null);
    }
    /** Exact one-millisecond drift retains the historical inconsistency decision. */
    @Test public void timingMismatchMatchesHistoricalRefusal() throws Exception {
        JSONObject manifest=M4PhaseDHandlerFixtures.manifest();JSONObject scene=manifest.getJSONArray("scenes").getJSONObject(0);
        scene.put("startMs",scene.getLong("startMs")+1);compare(M4PhaseDHandlerFixtures.runtime(true).toString(),manifest.toString(),null);
    }
    /** MEDIA/CONTINUE stays accepted on current production APIs but unsupported generically. */
    @Test public void continueTighteningDoesNotChangeCurrentProduction() throws Exception {
        JSONObject manifest=M4PhaseDHandlerFixtures.manifest();manifest.getJSONObject("clock").put("pauseBehavior","continue");
        compare(M4PhaseDHandlerFixtures.runtime(true).toString(),manifest.toString(),InstallationStatus.UNSUPPORTED_CAPABILITY);
    }
    /** WALL stays rejected historically; generic rejection explicitly reports capability, not execution. */
    @Test public void wallRejectionKeepsHistoricalDiagnosticAndGenericCapabilityTruth() throws Exception {
        JSONObject manifest=M4PhaseDHandlerFixtures.manifest();manifest.getJSONObject("clock").put("mode","wall");
        compare(M4PhaseDHandlerFixtures.runtime(true).toString(),manifest.toString(),InstallationStatus.UNSUPPORTED_CAPABILITY);
    }
    /** Track pause flags do not create an additional bridge rule absent from the qualified baseline. */
    @Test public void runtimePauseFalseWithManifestFreezeMatchesHistoricalAcceptance() throws Exception {
        JSONObject runtime=M4PhaseDHandlerFixtures.runtime(true);runtime.put("pauseFreezesDisplay",false);
        compare(runtime.toString(),M4PhaseDHandlerFixtures.manifest().toString(),null);
    }
    /** Preserve the current runtime parser's integer coercion at this internal boundary. */
    @Test public void runtimeTimingCoercionMatchesHistoricalParserBehavior() throws Exception {
        JSONObject runtime=M4PhaseDHandlerFixtures.runtime(true);JSONObject comment=runtime.getJSONArray("comments").getJSONObject(0);
        comment.put("startMs",comment.getLong("startMs")+0.5);compare(runtime.toString(),M4PhaseDHandlerFixtures.manifest().toString(),null);
    }
    /** Legacy malformed comment/media cases retain the no-manifest refusal behavior. */
    @Test public void legacyInvalidTextOnlyRuntimeMatchesHistoricalRefusal() throws Exception {
        JSONObject runtime=M4PhaseDHandlerFixtures.runtime(false);runtime.getJSONArray("comments").getJSONObject(0).put("media",new JSONObject());
        compare(runtime.toString(),null,null);
    }
}
