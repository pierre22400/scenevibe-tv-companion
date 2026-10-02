package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Locks the 0.10A fail-closed renderer-routing boundary without Android runtime dependencies. */
public final class OverlayManifestRouterTest {
    /** Builds the already-qualified sibling runtimeTrack payload. */
    private static JSONObject runtime(String trackId) throws Exception {
        return new JSONObject()
                .put("type","scenevibe.track.v1")
                .put("trackId",trackId)
                .put("targetPackage","com.amazon.amazonvideo.livingroom")
                .put("mediaIdentity",new JSONObject()
                        .put("platform","prime_video")
                        .put("videoId","video-1")
                        .put("title","Fixture")
                        .put("durationMs",120_000));
    }

    /** Builds a canonical assignment carrying the new additive manifest. */
    private static JSONObject assignmentWithManifest(String trackId) throws Exception {
        JSONObject assignment=new JSONObject().put("runtimeTrack",runtime(trackId));
        assignment.put("overlayManifest",new JSONObject()
                .put("type",OverlayManifestRouter.TYPE)
                .put("surface",OverlayManifestRouter.SURFACE)
                .put("renderer",new JSONObject().put("id",OverlayManifestRouter.COMMENTARY_RENDERER))
                .put("payload",new JSONObject()
                        .put("contract",OverlayManifestRouter.RUNTIME_TRACK_CONTRACT)
                        .put("ref",OverlayManifestRouter.RUNTIME_TRACK_REF)
                        .put("id",trackId)));
        return assignment;
    }

    /** Pre-manifest cloud assignments keep the explicit legacy commentary route. */
    @Test public void missingManifestUsesLegacyCommentaryRoute() throws Exception {
        JSONObject assignment=new JSONObject().put("runtimeTrack",runtime("track-1"));
        OverlayManifestRouter.Resolved resolved=OverlayManifestRouter.resolve(assignment);
        assertEquals(OverlayManifestRouter.Kind.LEGACY_COMMENTARY,resolved.kind);
        assertSame(assignment.getJSONObject("runtimeTrack"),resolved.payload);
    }

    /** Canonical v1 metadata selects the commentary renderer and sibling runtimeTrack. */
    @Test public void canonicalManifestRoutesRuntimeTrack() throws Exception {
        JSONObject assignment=assignmentWithManifest("track-1");
        OverlayManifestRouter.Resolved resolved=OverlayManifestRouter.resolve(assignment);
        assertEquals(OverlayManifestRouter.Kind.COMMENTARY,resolved.kind);
        assertSame(assignment.getJSONObject("runtimeTrack"),resolved.payload);
    }

    /** A manifest can never redirect the TV to an arbitrary renderer. */
    @Test public void unknownRendererFailsClosed() throws Exception {
        JSONObject assignment=assignmentWithManifest("track-1");
        assignment.getJSONObject("overlayManifest").getJSONObject("renderer")
                .put("id","evil.renderer");
        assertInvalid(assignment);
    }

    /** A manifest can reference only the already-present sibling runtimeTrack in v1. */
    @Test public void arbitraryPayloadReferenceFailsClosed() throws Exception {
        JSONObject assignment=assignmentWithManifest("track-1");
        assignment.getJSONObject("overlayManifest").getJSONObject("payload")
                .put("ref","https://example.invalid/payload");
        assertInvalid(assignment);
    }

    /** The routing id must bind to the same runtimeTrack the envelope already validated. */
    @Test public void payloadIdMismatchFailsClosed() throws Exception {
        JSONObject assignment=assignmentWithManifest("track-1");
        assignment.getJSONObject("overlayManifest").getJSONObject("payload")
                .put("id","track-2");
        assertInvalid(assignment);
    }

    /** A present-but-malformed manifest is never treated as if it were absent. */
    @Test public void malformedManifestFailsClosed() throws Exception {
        JSONObject assignment=new JSONObject().put("runtimeTrack",runtime("track-1"))
                .put("overlayManifest","not-an-object");
        assertInvalid(assignment);
    }

    /** Helper that proves the bounded protocol exception is raised. */
    private static void assertInvalid(JSONObject assignment) throws Exception {
        try {
            OverlayManifestRouter.resolve(assignment);
            fail("Expected invalid overlay manifest");
        } catch(OverlayManifestRouter.Invalid expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
