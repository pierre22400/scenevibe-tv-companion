package com.scenevibe.tvcompanionpoc;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Locks the user section-6 durable-cache invariant and user section 20-C (cases 11-15):
 * runtimeTrack + OverlayManifest + revision are persisted together atomically, restored
 * together for the SAME revision, and an invalid/corrupt manifest can never leave a mixed
 * revision. Uses a Memory {@link CloudTrackRepository.Storage} double that mirrors the real
 * SharedPreferences single-commit semantics.
 */
public final class CloudManifestCacheTest {
    /**
     * Store boundary substitute whose manifest-aware save commits revision + runtime +
     * manifest in one atomic step (or clears the manifest key when null), exactly like the
     * real SharedPreferences editor. A single {@code writable} flag models a commit failure.
     */
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
            // Atomic: either all three keys change together or none do.
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

    /** Creates a scheduler with no renderer or MediaSession side effects. */
    private static MediaSyncedTrackScheduler scheduler(){return new MediaSyncedTrackScheduler(new MediaSyncedTrackScheduler.Listener(){
        @Override public void onRender(ScheduledTrack.Event event){}
        @Override public void onPlayback(boolean playing,boolean freeze){}
    });}

    /** A runtimeTrack bound to {@code trackId} with two comments c1/c2 and known timing. */
    private static String runtimeJson(String trackId) {
        return "{\"type\":\"scenevibe.track.v1\",\"trackId\":\""+trackId+"\","
                +"\"targetPackage\":\"com.amazon.amazonvideo.livingroom\","
                +"\"mediaIdentity\":{\"platform\":\"prime_video\",\"videoId\":\"video-1\","
                +"\"title\":\"Columbo\",\"durationMs\":5884768},\"pauseFreezesDisplay\":true,"
                +"\"comments\":["
                +"{\"id\":\"c1\",\"text\":\"Hello\",\"startMs\":1000,\"durationMs\":6000},"
                +"{\"id\":\"c2\",\"text\":\"World\",\"startMs\":12000,\"durationMs\":6000}]}";
    }

    /** A text scene bound to one comment id with explicit timing. */
    private static JSONObject scene(String id,long startMs,long durationMs) throws Exception {
        JSONObject style=new JSONObject()
                .put("color","#FFFFFF").put("backgroundColor","#00000000")
                .put("fontSize",42).put("fontWeight","normal").put("textAlign","center")
                .put("padding",0).put("cornerRadius",0);
        JSONObject text=new JSONObject()
                .put("id",id+":text").put("type","text")
                .put("frame",new JSONObject().put("x",0).put("y",0).put("width",800).put("height",120))
                .put("zIndex",2).put("opacity",1.0).put("text","Comment card.").put("style",style);
        return new JSONObject()
                .put("id",id).put("startMs",startMs).put("durationMs",durationMs)
                .put("elements",new JSONArray().put(text));
    }

    /** A valid Video manifest JSON matching runtimeJson({@code sourceId}) exactly. */
    private static String manifestJson(String sourceId) throws Exception {
        JSONArray scenes=new JSONArray().put(scene("c1",1000,6000)).put(scene("c2",12000,6000));
        return new JSONObject()
                .put("type","scenevibe.overlay-manifest.v1")
                .put("schemaVersion","1.0.0")
                .put("manifestId","video:"+sourceId)
                .put("source",new JSONObject().put("product","video").put("sourceId",sourceId))
                .put("canvas",new JSONObject().put("width",1920).put("height",1080))
                .put("clock",new JSONObject().put("mode","media").put("pauseBehavior","freeze"))
                .put("scenes",scenes).toString();
    }

    /** Case 11: runtimeTrack + manifest + revision are persisted together at one revision. */
    @Test public void manifestedRevisionPersistsAtomically() throws Exception {
        Memory memory=new Memory();
        CloudTrackRepository repository=new CloudTrackRepository(memory);
        CloudTrackRepository.InstallResult result=
                repository.install(5,runtimeJson("track-1"),manifestJson("track-1"),scheduler());
        assertTrue(result.ok);
        assertEquals("5",memory.values.get("revision"));
        assertEquals("track-1",new JSONObject(memory.values.get("runtime")).optString("trackId"));
        assertNotNull(memory.values.get("manifest"));
        assertEquals(5,repository.revision());
    }

    /** Case 12: a fresh repository instance restores the same revision with both halves. */
    @Test public void restoreReturnsSameRevisionWithRuntimeAndManifest() throws Exception {
        Memory memory=new Memory();
        assertTrue(new CloudTrackRepository(memory)
                .install(7,runtimeJson("track-1"),manifestJson("track-1"),scheduler()).ok);
        CloudTrackRepository recreated=new CloudTrackRepository(memory);
        CloudTrackRepository.RestoreResult restored=recreated.restoreWithManifest(scheduler());
        assertTrue(restored.ok);
        assertEquals(7,restored.revision);
        assertNotNull(restored.manifest);
        assertEquals("track-1",restored.manifest.sourceId);
        assertEquals("video",restored.manifest.product);
        assertEquals(2,restored.manifest.scenes.size());
    }

    /** Case 13: a corrupt stored manifest fails closed; the whole revision is discarded. */
    @Test public void corruptManifestFailsClosedAndDiscardsRevision() throws Exception {
        Memory memory=new Memory();
        assertTrue(new CloudTrackRepository(memory)
                .install(3,runtimeJson("track-1"),manifestJson("track-1"),scheduler()).ok);
        // Corrupt only the manifest half in storage.
        memory.values.put("manifest","{not valid json");
        CloudTrackRepository recreated=new CloudTrackRepository(memory);
        CloudTrackRepository.RestoreResult restored=recreated.restoreWithManifest(scheduler());
        assertFalse(restored.ok);
        assertEquals(0,restored.revision);
        assertNull(restored.manifest);
        // Fail-closed discards the whole revision so no half-restore can reach the regie.
        assertTrue(memory.values.isEmpty());
    }

    /** Case 14: a new INVALID revision does not replace a prior valid revision. */
    @Test public void invalidNewRevisionDoesNotReplacePriorValidRevision() throws Exception {
        Memory memory=new Memory();
        CloudTrackRepository repository=new CloudTrackRepository(memory);
        assertTrue(repository.install(4,runtimeJson("track-1"),manifestJson("track-1"),scheduler()).ok);

        // A manifest whose sourceId does not match the runtime trackId is cross-contract invalid.
        CloudTrackRepository.InstallResult badBinding=
                repository.install(5,runtimeJson("track-1"),manifestJson("other-track"),scheduler());
        assertFalse(badBinding.ok);
        assertEquals(RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID,badBinding.code);

        // A structurally malformed manifest JSON is rejected before any write.
        CloudTrackRepository.InstallResult malformed=
                repository.install(5,runtimeJson("track-1"),"{broken",scheduler());
        assertFalse(malformed.ok);
        assertEquals(RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID,malformed.code);

        // The prior valid revision is still the only durable state.
        assertEquals(4,repository.revision());
        assertEquals("track-1",new JSONObject(memory.values.get("runtime")).optString("trackId"));
    }

    /**
     * Case 15: a bad manifest install leaves BOTH the runtimeTrack and the manifest at the
     * prior revision - there is never a mixed revision (runtime rev N + manifest rev N-1).
     */
    @Test public void badManifestLeavesNoMixedRevision() throws Exception {
        Memory memory=new Memory();
        CloudTrackRepository repository=new CloudTrackRepository(memory);
        assertTrue(repository.install(10,runtimeJson("track-1"),manifestJson("track-1"),scheduler()).ok);
        String priorRuntime=memory.values.get("runtime");
        String priorManifest=memory.values.get("manifest");
        String priorRevision=memory.values.get("revision");

        // A newer revision whose manifest timing is inconsistent must change nothing.
        JSONArray driftedScenes=new JSONArray()
                .put(scene("c1",1000,6000)).put(scene("c2",12001,6000));
        String driftedManifest=new JSONObject()
                .put("type","scenevibe.overlay-manifest.v1")
                .put("schemaVersion","1.0.0")
                .put("manifestId","video:track-1")
                .put("source",new JSONObject().put("product","video").put("sourceId","track-1"))
                .put("canvas",new JSONObject().put("width",1920).put("height",1080))
                .put("clock",new JSONObject().put("mode","media").put("pauseBehavior","freeze"))
                .put("scenes",driftedScenes).toString();
        CloudTrackRepository.InstallResult inconsistent=
                repository.install(11,runtimeJson("track-1"),driftedManifest,scheduler());
        assertFalse(inconsistent.ok);
        assertEquals(RuntimeDiagnostics.ManifestCode.MANIFEST_INCONSISTENT,inconsistent.code);

        // Runtime, manifest and revision are all exactly the prior durable triple.
        assertEquals(priorRevision,memory.values.get("revision"));
        assertEquals(priorRuntime,memory.values.get("runtime"));
        assertEquals(priorManifest,memory.values.get("manifest"));
        assertEquals(10,repository.revision());

        // Restoring confirms the single coherent revision 10, never a mix.
        CloudTrackRepository.RestoreResult restored=
                new CloudTrackRepository(memory).restoreWithManifest(scheduler());
        assertTrue(restored.ok);
        assertEquals(10,restored.revision);
        assertEquals("track-1",restored.manifest.sourceId);
    }

    /** A cache commit failure during a manifested install reports MANIFEST_CACHE_FAILED. */
    @Test public void nonDurableCommitReportsCacheFailure() throws Exception {
        Memory memory=new Memory();memory.writable=false;
        CloudTrackRepository repository=new CloudTrackRepository(memory);
        CloudTrackRepository.InstallResult result=
                repository.install(1,runtimeJson("track-1"),manifestJson("track-1"),scheduler());
        assertFalse(result.ok);
        assertEquals(RuntimeDiagnostics.ManifestCode.MANIFEST_CACHE_FAILED,result.code);
        assertEquals(0,repository.revision());
    }

    /** Legacy Case A (no manifest) persistence stores no manifest and restores as before. */
    @Test public void legacyNoManifestInstallStoresNoManifest() throws Exception {
        Memory memory=new Memory();
        CloudTrackRepository repository=new CloudTrackRepository(memory);
        assertTrue(repository.install(2,runtimeJson("track-1"),scheduler()));
        assertNull(memory.values.get("manifest"));
        // The manifested restore path refuses a non-manifested revision without clearing it.
        CloudTrackRepository.RestoreResult restored=
                new CloudTrackRepository(memory).restoreWithManifest(scheduler());
        assertFalse(restored.ok);
        assertEquals(2,repository.revision());
        // The legacy restore still works byte-for-byte for Case A.
        assertEquals(2,new CloudTrackRepository(memory).restore(scheduler()));
    }
}
