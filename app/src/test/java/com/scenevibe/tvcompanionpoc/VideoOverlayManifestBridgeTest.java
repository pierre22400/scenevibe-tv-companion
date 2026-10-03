package com.scenevibe.tvcompanionpoc;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Locks the user section-5 cross-contract Video coherence rules (user section 20-B,
 * cases 4-10) with no Android runtime dependency: product/clock/source binding, a
 * comment.id &lt;-&gt; scene.id bijection and EXACT integer-ms timing. A reject always yields a
 * bounded {@link VideoOverlayManifestBridge.Result} failure and the bridge never ACKs.
 */
public final class VideoOverlayManifestBridgeTest {
    /** Parse one text-only runtime track through the shared parser (no media). */
    private static ScheduledTrack track(String trackId,String runtimeJson) throws Exception {
        return TrackParser.parse(new JSONObject(runtimeJson),media->null);
    }

    /**
     * A runtimeTrack bound to {@code trackId} with two comments whose ids/timings line up
     * with the manifest fixture below (c1 @1000/6000, c2 @12000/6000).
     */
    private static String runtimeJson(String trackId) {
        return "{\"type\":\"scenevibe.track.v1\",\"trackId\":\""+trackId+"\","
                +"\"targetPackage\":\"com.amazon.amazonvideo.livingroom\","
                +"\"mediaIdentity\":{\"platform\":\"prime_video\",\"videoId\":\"video-1\","
                +"\"title\":\"Columbo\",\"durationMs\":5884768},\"pauseFreezesDisplay\":true,"
                +"\"comments\":["
                +"{\"id\":\"c1\",\"text\":\"Hello\",\"startMs\":1000,\"durationMs\":6000},"
                +"{\"id\":\"c2\",\"text\":\"World\",\"startMs\":12000,\"durationMs\":6000}]}";
    }

    /** A minimal valid text scene bound to one comment id with explicit timing. */
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

    /** A valid Video manifest whose scenes mirror the runtime comments by default. */
    private static OverlayManifest videoManifest(String sourceId,String clockMode,
            String product,JSONArray scenes) throws Exception {
        JSONObject json=new JSONObject()
                .put("type","scenevibe.overlay-manifest.v1")
                .put("schemaVersion","1.0.0")
                .put("manifestId","video:"+sourceId)
                .put("source",new JSONObject().put("product",product).put("sourceId",sourceId))
                .put("canvas",new JSONObject().put("width",1920).put("height",1080))
                .put("clock",new JSONObject().put("mode",clockMode).put("pauseBehavior","freeze"))
                .put("scenes",scenes);
        return OverlayManifestParser.parse(json);
    }

    /** The default two-scene manifest that matches runtimeJson("track-1") exactly. */
    private static OverlayManifest matchingManifest(String sourceId) throws Exception {
        JSONArray scenes=new JSONArray().put(scene("c1",1000,6000)).put(scene("c2",12000,6000));
        return videoManifest(sourceId,"media","video",scenes);
    }

    /** Case 4: trackId == sourceId AND the comment/scene bijection with exact timing passes. */
    @Test public void matchingTrackAndManifestPass() throws Exception {
        ScheduledTrack runtime=track("track-1",runtimeJson("track-1"));
        VideoOverlayManifestBridge.Result result=
                VideoOverlayManifestBridge.validate(runtime,matchingManifest("track-1"));
        assertTrue(result.ok);
        assertNull(result.code);
    }

    /** Case 5: sourceId that does not equal the runtime trackId is rejected. */
    @Test public void sourceIdMismatchRejected() throws Exception {
        ScheduledTrack runtime=track("track-1",runtimeJson("track-1"));
        VideoOverlayManifestBridge.Result result=
                VideoOverlayManifestBridge.validate(runtime,matchingManifest("other-track"));
        assertFalse(result.ok);
        assertEquals(RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID,result.code);
    }

    /** Case 6: a comment with no matching scene id (missing id) is rejected as inconsistent. */
    @Test public void missingSceneIdRejected() throws Exception {
        ScheduledTrack runtime=track("track-1",runtimeJson("track-1"));
        // Replace c2's scene with a differently-named scene: same cardinality, broken bijection.
        JSONArray scenes=new JSONArray().put(scene("c1",1000,6000)).put(scene("c3",12000,6000));
        OverlayManifest manifest=videoManifest("track-1","media","video",scenes);
        VideoOverlayManifestBridge.Result result=
                VideoOverlayManifestBridge.validate(runtime,manifest);
        assertFalse(result.ok);
        assertEquals(RuntimeDiagnostics.ManifestCode.MANIFEST_INCONSISTENT,result.code);
    }

    /** Case 7: an extra Video scene (more scenes than comments) is rejected. */
    @Test public void extraSceneRejected() throws Exception {
        ScheduledTrack runtime=track("track-1",runtimeJson("track-1"));
        JSONArray scenes=new JSONArray()
                .put(scene("c1",1000,6000)).put(scene("c2",12000,6000)).put(scene("c3",20000,6000));
        OverlayManifest manifest=videoManifest("track-1","media","video",scenes);
        VideoOverlayManifestBridge.Result result=
                VideoOverlayManifestBridge.validate(runtime,manifest);
        assertFalse(result.ok);
        assertEquals(RuntimeDiagnostics.ManifestCode.MANIFEST_INCONSISTENT,result.code);
    }

    /** Case 8: a startMs that differs by a single millisecond is rejected (exact long ==). */
    @Test public void startMsMismatchRejected() throws Exception {
        ScheduledTrack runtime=track("track-1",runtimeJson("track-1"));
        JSONArray scenes=new JSONArray().put(scene("c1",1001,6000)).put(scene("c2",12000,6000));
        OverlayManifest manifest=videoManifest("track-1","media","video",scenes);
        VideoOverlayManifestBridge.Result result=
                VideoOverlayManifestBridge.validate(runtime,manifest);
        assertFalse(result.ok);
        assertEquals(RuntimeDiagnostics.ManifestCode.MANIFEST_INCONSISTENT,result.code);
    }

    /** Case 9: a durationMs that differs by a single millisecond is rejected (exact long ==). */
    @Test public void durationMsMismatchRejected() throws Exception {
        ScheduledTrack runtime=track("track-1",runtimeJson("track-1"));
        JSONArray scenes=new JSONArray().put(scene("c1",1000,5999)).put(scene("c2",12000,6000));
        OverlayManifest manifest=videoManifest("track-1","media","video",scenes);
        VideoOverlayManifestBridge.Result result=
                VideoOverlayManifestBridge.validate(runtime,manifest);
        assertFalse(result.ok);
        assertEquals(RuntimeDiagnostics.ManifestCode.MANIFEST_INCONSISTENT,result.code);
    }

    /** Case 10: a product or clock-mode mismatch is rejected with a bounded failure (no ACK). */
    @Test public void productOrClockMismatchRejected() throws Exception {
        ScheduledTrack runtime=track("track-1",runtimeJson("track-1"));

        JSONArray wallScenes=new JSONArray().put(scene("c1",1000,6000)).put(scene("c2",12000,6000));
        OverlayManifest wallClock=videoManifest("track-1","wall","video",wallScenes);
        VideoOverlayManifestBridge.Result wallResult=
                VideoOverlayManifestBridge.validate(runtime,wallClock);
        assertFalse(wallResult.ok);
        assertEquals(RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID,wallResult.code);

        JSONArray bannerScenes=new JSONArray().put(scene("c1",1000,6000)).put(scene("c2",12000,6000));
        OverlayManifest banner=videoManifest("track-1","media","banner",bannerScenes);
        VideoOverlayManifestBridge.Result bannerResult=
                VideoOverlayManifestBridge.validate(runtime,banner);
        assertFalse(bannerResult.ok);
        assertEquals(RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID,bannerResult.code);
        // The bridge has NO ACK responsibility: a reject is only a bounded failed Result;
        // its public surface carries no ACK, so a rejection can never produce one here.
    }
}
