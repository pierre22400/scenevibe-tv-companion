package com.scenevibe.tvcompanionpoc;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Section 7/8 ACK-ordering coverage plus section 20-A (cases 1-3). These tests exercise the
 * Android-free {@link M4PhaseGHistoricalService#installManifestedRevision} helper, which is the exact
 * core the production {@link M4PhaseFHistoricalCloudClient.ManifestInstaller} delegates to: it is the
 * decision that gates the ACK. A manifested revision is "ACK-able" (helper returns a positive
 * revision) ONLY after runtimeTrack valid + manifest valid + cross-contract valid + atomic
 * durable persist + scheduler accept + regie accept. Any failure returns 0 => NO ACK, prior
 * cache intact, bounded diagnostic. Case A (no manifest) keeps the legacy renderer; when a
 * newer legacy revision follows a manifested revision, the regie is engaged only to DISARM
 * the prior manifest before ACK so visual ownership returns deterministically to Case A.
 */
public final class ManifestInstallAckDecisionTest {

    /** Atomic in-memory store mirroring the real SharedPreferences single-commit semantics. */
    private static final class Memory implements CloudTrackRepository.Storage {
        final Map<String,String> values=new HashMap<>();boolean writable=true;
        @Override public String get(String key){return values.get(key);}
        @Override public boolean save(long revision,String json) {
            if(!writable)return false;
            values.put("revision",String.valueOf(revision));values.put("runtime",json);
            values.remove("manifest");return true;
        }
        @Override public boolean save(long revision,String runtimeJson,String manifestJson) {
            if(!writable)return false;
            Map<String,String> staged=new HashMap<>(values);
            staged.put("revision",String.valueOf(revision));
            staged.put("runtime",runtimeJson);
            if(manifestJson==null)staged.remove("manifest");
            else staged.put("manifest",manifestJson);
            values.clear();values.putAll(staged);return true;
        }
        @Override public boolean saveAck(long revision){values.put("ackRevision",String.valueOf(revision));return true;}
        @Override public void clear(){values.clear();}
    }

    /** Fake SceneSink recording regie shows so we can assert whether Case B engaged. */
    private static final class RecordingSink implements SceneRuntimeController.SceneSink {
        final List<String> shown=new ArrayList<>();
        @Override public boolean preflight(OverlayManifest.Scene scene){return true;}
        @Override public void show(OverlayManifest.Scene scene){shown.add(scene.id);}
        @Override public void hide(OverlayManifest.Scene scene){}
        @Override public void hideAll(){}
    }

    private static MediaSyncedTrackScheduler scheduler() {
        return new MediaSyncedTrackScheduler(new MediaSyncedTrackScheduler.Listener(){
            @Override public void onRender(ScheduledTrack.Event event){}
            @Override public void onPlayback(boolean playing,boolean freeze){}
        });
    }

    private static String runtimeJson(String trackId) {
        return "{\"type\":\"scenevibe.track.v1\",\"trackId\":\""+trackId+"\","
                +"\"targetPackage\":\"com.amazon.amazonvideo.livingroom\","
                +"\"mediaIdentity\":{\"platform\":\"prime_video\",\"videoId\":\"video-1\","
                +"\"title\":\"Columbo\",\"durationMs\":5884768},\"pauseFreezesDisplay\":true,"
                +"\"comments\":["
                +"{\"id\":\"c1\",\"text\":\"Hello\",\"startMs\":1000,\"durationMs\":6000},"
                +"{\"id\":\"c2\",\"text\":\"World\",\"startMs\":12000,\"durationMs\":6000}]}";
    }

    private static JSONObject scene(String id,long startMs,long durationMs) throws Exception {
        JSONObject style=new JSONObject().put("color","#FFFFFF").put("backgroundColor","#00000000")
                .put("fontSize",42).put("fontWeight","normal").put("textAlign","center")
                .put("padding",0).put("cornerRadius",0);
        JSONObject text=new JSONObject().put("id",id+":text").put("type","text")
                .put("frame",new JSONObject().put("x",0).put("y",0).put("width",800).put("height",120))
                .put("zIndex",2).put("opacity",1.0).put("text","Comment card.").put("style",style);
        return new JSONObject().put("id",id).put("startMs",startMs).put("durationMs",durationMs)
                .put("elements",new JSONArray().put(text));
    }

    private static String manifestJson(String sourceId) throws Exception {
        JSONArray scenes=new JSONArray().put(scene("c1",1000,6000)).put(scene("c2",12000,6000));
        return new JSONObject()
                .put("type","scenevibe.overlay-manifest.v1").put("schemaVersion","1.0.0")
                .put("manifestId","video:"+sourceId)
                .put("source",new JSONObject().put("product","video").put("sourceId",sourceId))
                .put("canvas",new JSONObject().put("width",1920).put("height",1080))
                .put("clock",new JSONObject().put("mode","media").put("pauseBehavior","freeze"))
                .put("scenes",scenes).toString();
    }

    // ---- section 7: ACK only after full durable install + scheduler + regie accept ------

    /** A fully coherent manifested revision installs, arms the regie, and is ACK-able. */
    @Test public void coherentManifestInstallsArmsRegieAndIsAckable() throws Exception {
        Memory memory=new Memory();
        CloudTrackRepository repository=new CloudTrackRepository(memory);
        RecordingSink sink=new RecordingSink();
        SceneRuntimeController regie=new SceneRuntimeController(sink);
        DiagnosticsStore diagnostics=new DiagnosticsStore();

        long armed=M4PhaseGHistoricalService.installManifestedRevision(repository,scheduler(),regie,
                diagnostics,5,runtimeJson("track-1"),manifestJson("track-1"));

        assertEquals("ACK-able only after full success",5,armed);
        assertEquals(5,repository.revision());
        assertNotNull(memory.values.get("manifest"));
        assertEquals(RuntimeDiagnostics.ManifestCode.NONE,diagnostics.lastManifestCode());
        // Regie accepted/restored the manifest for the installed revision => Case B is selected.
        assertTrue(regie.hasActiveManifest());
        assertTrue(regie.isSceneRendererActiveFor(5));
    }

    /** A cross-contract-inconsistent manifest is NOT ACK-able and leaves the prior cache intact. */
    @Test public void inconsistentManifestIsNotAckableAndKeepsPriorCache() throws Exception {
        Memory memory=new Memory();
        CloudTrackRepository repository=new CloudTrackRepository(memory);
        // A prior valid revision 4 exists.
        assertEquals(4,M4PhaseGHistoricalService.installManifestedRevision(repository,scheduler(),
                new SceneRuntimeController(new RecordingSink()),new DiagnosticsStore(),
                4,runtimeJson("track-1"),manifestJson("track-1")));
        String priorRuntime=memory.values.get("runtime");
        String priorManifest=memory.values.get("manifest");

        // A newer revision whose manifest timing drifts is cross-contract inconsistent.
        JSONArray drifted=new JSONArray().put(scene("c1",1000,6000)).put(scene("c2",12001,6000));
        String driftedManifest=new JSONObject()
                .put("type","scenevibe.overlay-manifest.v1").put("schemaVersion","1.0.0")
                .put("manifestId","video:track-1")
                .put("source",new JSONObject().put("product","video").put("sourceId","track-1"))
                .put("canvas",new JSONObject().put("width",1920).put("height",1080))
                .put("clock",new JSONObject().put("mode","media").put("pauseBehavior","freeze"))
                .put("scenes",drifted).toString();

        RecordingSink sink=new RecordingSink();
        SceneRuntimeController regie=new SceneRuntimeController(sink);
        DiagnosticsStore diagnostics=new DiagnosticsStore();
        long armed=M4PhaseGHistoricalService.installManifestedRevision(repository,scheduler(),regie,
                diagnostics,5,runtimeJson("track-1"),driftedManifest);

        assertEquals("inconsistent manifest => NO ACK",0,armed);
        assertEquals(RuntimeDiagnostics.ManifestCode.MANIFEST_INCONSISTENT,diagnostics.lastManifestCode());
        // Prior durable revision 4 is entirely intact: no mixed revision.
        assertEquals(4,repository.revision());
        assertEquals(priorRuntime,memory.values.get("runtime"));
        assertEquals(priorManifest,memory.values.get("manifest"));
        // The regie was never armed for the rejected revision.
        assertFalse(regie.hasActiveManifest());
    }

    /** A structurally invalid manifest (wrong bound sourceId) is not ACK-able. */
    @Test public void invalidContractManifestIsNotAckable() throws Exception {
        Memory memory=new Memory();
        CloudTrackRepository repository=new CloudTrackRepository(memory);
        DiagnosticsStore diagnostics=new DiagnosticsStore();
        SceneRuntimeController regie=new SceneRuntimeController(new RecordingSink());

        long armed=M4PhaseGHistoricalService.installManifestedRevision(repository,scheduler(),regie,
                diagnostics,1,runtimeJson("track-1"),manifestJson("other-track"));

        assertEquals(0,armed);
        assertEquals(RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID,diagnostics.lastManifestCode());
        assertEquals(0,repository.revision());
        assertFalse(regie.hasActiveManifest());
    }

    /** A non-durable commit fails closed as MANIFEST_CACHE_FAILED and is not ACK-able. */
    @Test public void nonDurableCommitIsNotAckable() throws Exception {
        Memory memory=new Memory();memory.writable=false;
        CloudTrackRepository repository=new CloudTrackRepository(memory);
        DiagnosticsStore diagnostics=new DiagnosticsStore();
        SceneRuntimeController regie=new SceneRuntimeController(new RecordingSink());

        long armed=M4PhaseGHistoricalService.installManifestedRevision(repository,scheduler(),regie,
                diagnostics,1,runtimeJson("track-1"),manifestJson("track-1"));

        assertEquals(0,armed);
        assertEquals(RuntimeDiagnostics.ManifestCode.MANIFEST_CACHE_FAILED,diagnostics.lastManifestCode());
        assertEquals(0,repository.revision());
        assertFalse(regie.hasActiveManifest());
    }

    // ---- review Issue 2: re-delivered manifested revision (revision <= cached) re-arm -----

    /**
     * A re-delivered manifested revision whose regie is ALREADY armed for the cached revision
     * is a benign idempotent re-ACK: confirmManifestedRevisionArmed returns the revision without
     * re-persisting anything.
     */
    @Test public void reDeliveredManifestConfirmsAlreadyArmedRegie() throws Exception {
        Memory memory=new Memory();
        CloudTrackRepository repository=new CloudTrackRepository(memory);
        RecordingSink sink=new RecordingSink();
        SceneRuntimeController regie=new SceneRuntimeController(sink);

        // Install + arm revision 5 (installer path arms the regie).
        assertEquals(5,M4PhaseGHistoricalService.installManifestedRevision(repository,scheduler(),regie,
                new DiagnosticsStore(),5,runtimeJson("track-1"),manifestJson("track-1")));
        assertTrue(regie.hasActiveManifest());
        String runtimeAfterInstall=memory.values.get("runtime");
        String manifestAfterInstall=memory.values.get("manifest");

        // Re-delivery at revision <= cached: confirm the regie is armed, re-persist nothing.
        long armed=M4PhaseGHistoricalService.confirmManifestedRevisionArmed(repository,scheduler(),regie,5);
        assertEquals(5,armed);
        assertTrue(regie.isSceneRendererActiveFor(5));
        assertEquals("re-delivery must not re-persist runtime",runtimeAfterInstall,memory.values.get("runtime"));
        assertEquals("re-delivery must not re-persist manifest",manifestAfterInstall,memory.values.get("manifest"));
    }

    /**
     * A re-delivered manifested revision whose regie is NOT yet armed (e.g. a cold restart that
     * only loaded the durable cache) is re-armed defensively from the durable manifested copy,
     * so the re-ACK is safe. No new persistence occurs.
     */
    @Test public void reDeliveredManifestReArmsUnarmedRegieFromDurableCache() throws Exception {
        Memory memory=new Memory();
        // Durable manifested revision 7 exists from a prior cycle.
        assertEquals(7,M4PhaseGHistoricalService.installManifestedRevision(new CloudTrackRepository(memory),
                scheduler(),new SceneRuntimeController(new RecordingSink()),new DiagnosticsStore(),
                7,runtimeJson("track-1"),manifestJson("track-1")));

        // A brand new controller (unarmed) stands in for a restart that has not armed yet.
        CloudTrackRepository repository=new CloudTrackRepository(memory);
        SceneRuntimeController regie=new SceneRuntimeController(new RecordingSink());
        assertFalse(regie.hasActiveManifest());

        long armed=M4PhaseGHistoricalService.confirmManifestedRevisionArmed(repository,scheduler(),regie,7);
        assertEquals("defensive re-arm from durable manifested cache",7,armed);
        assertTrue(regie.hasActiveManifest());
        assertTrue(regie.isSceneRendererActiveFor(7));
    }

    /**
     * A re-delivered MANIFESTED revision whose cache has NO durable manifest (a legacy Case A
     * revision) is not armable: confirm returns 0 so the manifested re-delivery fails closed
     * (no ACK) rather than ACK a scene the regie cannot drive.
     */
    @Test public void reDeliveredManifestFailsClosedWhenNoDurableManifest() throws Exception {
        Memory memory=new Memory();
        CloudTrackRepository repository=new CloudTrackRepository(memory);
        // Legacy Case A install: runtime only, no manifest.
        assertTrue(repository.install(4,runtimeJson("track-1"),scheduler()));
        assertNull(memory.values.get("manifest"));

        SceneRuntimeController regie=new SceneRuntimeController(new RecordingSink());
        long armed=M4PhaseGHistoricalService.confirmManifestedRevisionArmed(repository,scheduler(),regie,4);
        assertEquals("no durable manifest => not armable => fail closed",0,armed);
        assertFalse(regie.hasActiveManifest());
    }

    // ---- manifested -> legacy transition regression (Work audit P1) --------------------

    /**
     * A newer legacy revision following a manifested revision must atomically replace the
     * durable runtime/cache, then disarm the manifested regie BEFORE the assignment is ACK-able.
     * This is the exact transition that previously left activeRevision/manifest ownership stale
     * and could route the new legacy track into SceneRenderer.
     */
    @Test public void manifestedThenLegacyRevisionReturnsVisualOwnershipToCaseA() throws Exception {
        Memory memory=new Memory();
        CloudTrackRepository repository=new CloudTrackRepository(memory);
        MediaSyncedTrackScheduler scheduler=scheduler();
        RecordingSink sink=new RecordingSink();
        SceneRuntimeController regie=new SceneRuntimeController(sink);

        assertEquals(4,M4PhaseGHistoricalService.installManifestedRevision(repository,scheduler,regie,
                new DiagnosticsStore(),4,runtimeJson("track-1"),manifestJson("track-1")));
        assertTrue(regie.hasActiveManifest());
        assertTrue(regie.isSceneRendererActiveFor(4));

        // Prove there is an actually visible Case-B scene to remove, not only armed metadata.
        regie.onEventDue(new ScheduledTrack.Event("c1","Hello",1000,6000,null).id);
        assertTrue(regie.hasVisibleScene());

        M4PhaseFHistoricalCloudClient.ManifestInstaller transition=new M4PhaseFHistoricalCloudClient.ManifestInstaller() {
            @Override public boolean install(long revision,String runtime,String manifest) {
                return false;
            }
            @Override public boolean confirmArmed(long revision) {
                return false;
            }
            @Override public boolean activateLegacy(long revision) {
                return M4PhaseGHistoricalService.activateLegacyRevision(regie,revision)==revision;
            }
        };

        boolean ackable=M4PhaseFHistoricalCloudClient.installLegacyRevision(
                5,4,runtimeJson("track-2"),repository,scheduler,transition);

        assertTrue("legacy revision is ACK-able only after Case-B disarm",ackable);
        assertEquals(5,repository.revision());
        assertNull("legacy revision removes prior durable manifest",memory.values.get("manifest"));
        assertFalse("regie manifest must be unloaded",regie.hasActiveManifest());
        assertFalse("old manifested scene must be hidden",regie.hasVisibleScene());
        assertFalse("new revision must select legacy path",regie.isSceneRendererActiveFor(5));
    }

    // ---- section 20-A (cases 1-3): no manifest => legacy path, regie not engaged --------

    /** Case 1: a legacy no-manifest install persists runtime, stores no manifest. */
    @Test public void noManifestAssignmentUsesLegacyInstall() throws Exception {
        Memory memory=new Memory();
        CloudTrackRepository repository=new CloudTrackRepository(memory);
        assertTrue(repository.install(2,runtimeJson("track-1"),scheduler()));
        assertNull("no manifest stored for Case A",memory.values.get("manifest"));
        assertEquals(2,repository.revision());
    }

    /** Case 2: without a loaded manifest the regie never selects Case B for the active revision. */
    @Test public void caseAPredicateChoosesLegacyAndRegieIsNotEngaged() {
        RecordingSink sink=new RecordingSink();
        SceneRuntimeController regie=new SceneRuntimeController(sink);
        // No manifest loaded for the active revision => Case A.
        assertFalse(regie.hasActiveManifest());
        assertFalse(regie.isSceneRendererActiveFor(2));
        assertFalse(SceneRuntimeController.shouldUseSceneRenderer(0,2));

        // A due comment under Case A must not drive the SceneRenderer at all.
        regie.onEventDue(new ScheduledTrack.Event("c1","Hello",1000,6000,null).id);
        assertTrue("regie must not show anything in Case A",sink.shown.isEmpty());
        assertFalse(regie.hasVisibleScene());
    }

    /** Case 3: the manifested restore refuses a non-manifested revision without clearing it. */
    @Test public void manifestRestoreRefusesNonManifestedRevisionWithoutClearing() throws Exception {
        Memory memory=new Memory();
        CloudTrackRepository repository=new CloudTrackRepository(memory);
        assertTrue(repository.install(2,runtimeJson("track-1"),scheduler()));

        CloudTrackRepository.RestoreResult manifested=
                new CloudTrackRepository(memory).restoreWithManifest(scheduler());
        assertFalse("Case A revision is not a manifested restore",manifested.ok);
        assertEquals(2,repository.revision());
        // Legacy restore still authoritative for Case A, byte-for-byte.
        assertEquals(2,new CloudTrackRepository(memory).restore(scheduler()));
    }
}
