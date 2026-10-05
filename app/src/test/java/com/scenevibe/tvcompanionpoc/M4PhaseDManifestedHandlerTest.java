package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.ExecutionRequirements;
import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.PreparedInstallation;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercise the real manifested parser/bridge/profile boundary with exact frozen canonical inputs. */
public final class M4PhaseDManifestedHandlerTest {
    private final VideoManifestInstallationHandler handler=VideoInstallationHandlers.manifested();

    /** Frozen Unicode bytes validate and produce a trusted bounded MEDIA/FREEZE state. */
    @Test public void frozenManifestedFixturePreparesExactRequirements() throws Exception {
        InstallRequest request=M4PhaseDHandlerFixtures.request(true);
        assertEquals(InstallationStatus.VALIDATED,handler.validate(request,TvCapabilities.current()));
        PreparedInstallation prepared=handler.prepare(request,TvCapabilities.current());
        VideoPreparedState state=(VideoPreparedState)prepared.preparedState();
        assertEquals(InstallationStore.COMPAT_OVERLAY_HANDLER_ID,prepared.handlerId());
        assertEquals(TvCapabilities.CODEC_TRACK_OVERLAY,prepared.codecId());
        assertEquals(request.revision(),prepared.revision());
        assertEquals(TvCapabilities.OVERLAY_CONTRACT,prepared.requirements().renderingContract());
        assertEquals(ExecutionRequirements.ClockMode.MEDIA,prepared.requirements().clock());
        assertEquals(ExecutionRequirements.PauseBehavior.FREEZE,prepared.requirements().pauseBehavior());
        assertEquals(state.manifest.scenes.size(),prepared.requirements().timedSceneCount());
        assertFalse(prepared.requirements().requiresRemoteAssetAcquisition());
        assertFalse(prepared.requirements().requiresSharedAssetCache());
        assertEquals(InstallationStatus.PREPARED,prepared.status());
    }
    /** Both artifact names are mandatory, rather than merely a count of two. */
    @Test public void missingRuntimeIsRejected() throws Exception {
        reject(M4PhaseDHandlerFixtures.withArtifact(M4PhaseDHandlerFixtures.request(true),"runtime",null));
    }
    /** A manifested shape cannot silently fall back when its graphical half is absent. */
    @Test public void missingManifestIsRejected() throws Exception {
        reject(M4PhaseDHandlerFixtures.withArtifact(M4PhaseDHandlerFixtures.request(true),"manifest",null));
    }
    /** A same-count package with an unknown key is rejected before parsing. */
    @Test public void unknownArtifactIsRejected() throws Exception {
        InstallRequest request=M4PhaseDHandlerFixtures.request(true);
        Map<String,byte[]> artifacts=new TreeMap<>(request.artifacts());
        artifacts.put("unknown",artifacts.remove("manifest"));
        reject(new InstallRequest(request.revision(),request.codecId(),artifacts));
    }
    /** The existing outer model refuses a third artifact before any handler can retain it. */
    @Test public void extraThirdArtifactIsOuterRejected() throws Exception {
        Map<String,byte[]> artifacts=new TreeMap<>(M4PhaseDHandlerFixtures.request(true).artifacts());
        artifacts.put("extra",new byte[]{1});
        try {new InstallRequest(1,TvCapabilities.CODEC_TRACK_OVERLAY,artifacts);fail("Extra artifact accepted");}
        catch (IllegalArgumentException invalid) {assertTrue("fixed outer failure",invalid.getMessage().startsWith("Invalid "));}
    }
    /** A correct byte set under a different codec cannot select this handler. */
    @Test public void wrongCodecIsRejected() throws Exception {
        reject(new InstallRequest(1,TvCapabilities.CODEC_TRACK,M4PhaseDHandlerFixtures.request(true).artifacts()));
    }
    /** Overlong, surrogate, out-of-range and truncated UTF-8 are never decoded with replacement. */
    @Test public void malformedUtf8InEitherArtifactIsRejected() throws Exception {
        for (String key:new String[]{"runtime","manifest"})
            for (byte[] bytes:new byte[][]{{(byte)0xc0,(byte)0x80},{(byte)0xed,(byte)0xa0,(byte)0x80},
                    {(byte)0xf4,(byte)0x90,(byte)0x80,(byte)0x80},{(byte)0xe2,(byte)0x82}})
                reject(M4PhaseDHandlerFixtures.withArtifact(M4PhaseDHandlerFixtures.request(true),key,bytes));
    }
    /** The exact historical runtime ceiling is accepted; one additional UTF-16 unit is not. */
    @Test public void runtimeUtf16LimitIsExact() throws Exception {
        InstallRequest request=M4PhaseDHandlerFixtures.request(true);
        String value=new String(request.artifact("runtime"),StandardCharsets.UTF_8);
        String padded=value+" ".repeat(400_000-value.length());
        assertEquals(InstallationStatus.VALIDATED,handler.validate(M4PhaseDHandlerFixtures.withArtifact(request,"runtime",
                padded.getBytes(StandardCharsets.UTF_8)),TvCapabilities.current()));
        reject(M4PhaseDHandlerFixtures.withArtifact(request,"runtime",(padded+" ").getBytes(StandardCharsets.UTF_8)));
    }
    /** The manifest's UTF-16 bound is independent of its wider UTF-8 byte ceiling. */
    @Test public void manifestUtf16LimitIsExact() throws Exception {
        InstallRequest request=M4PhaseDHandlerFixtures.request(true);
        String value=new String(request.artifact("manifest"),StandardCharsets.UTF_8);
        String padded=value+" ".repeat(800_000-value.length());
        assertEquals(InstallationStatus.VALIDATED,handler.validate(M4PhaseDHandlerFixtures.withArtifact(request,"manifest",
                padded.getBytes(StandardCharsets.UTF_8)),TvCapabilities.current()));
        reject(M4PhaseDHandlerFixtures.withArtifact(request,"manifest",(padded+" ").getBytes(StandardCharsets.UTF_8)));
    }
    /** A wrong runtime type cannot become a valid graphical-only installation. */
    @Test public void wrongRuntimeTypeIsRejected() throws Exception {
        JSONObject runtime=M4PhaseDHandlerFixtures.runtime(true);runtime.put("type","unknown");
        reject(M4PhaseDHandlerFixtures.request(runtime,M4PhaseDHandlerFixtures.manifest()));
    }
    /** Comments must exist as the same required array used by the qualified text parser. */
    @Test public void missingCommentsIsRejected() throws Exception {
        JSONObject runtime=M4PhaseDHandlerFixtures.runtime(true);runtime.remove("comments");
        reject(M4PhaseDHandlerFixtures.request(runtime,M4PhaseDHandlerFixtures.manifest()));
    }
    /** Scalar/empty/non-object comments fail closed before the shared runtime parser. */
    @Test public void invalidCommentsShapesAreRejected() throws Exception {
        for (Object comments:new Object[]{1,new JSONArray(),new JSONArray().put(1)}) {
            JSONObject runtime=M4PhaseDHandlerFixtures.runtime(true);runtime.put("comments",comments);
            reject(M4PhaseDHandlerFixtures.request(runtime,M4PhaseDHandlerFixtures.manifest()));
        }
    }
    /** Presence of a media field, including JSON null, remains forbidden for Cloud-compatible text. */
    @Test public void everyCommentMediaFieldIsRejected() throws Exception {
        for (Object media:new Object[]{JSONObject.NULL,new JSONObject(),"asset:local"}) {
            JSONObject runtime=M4PhaseDHandlerFixtures.runtime(true);
            runtime.getJSONArray("comments").getJSONObject(0).put("media",media);
            reject(M4PhaseDHandlerFixtures.request(runtime,M4PhaseDHandlerFixtures.manifest()));
        }
    }
    /** The existing exact identity duration/platform rules remain authoritative. */
    @Test public void invalidMediaIdentityIsRejected() throws Exception {
        for (Object duration:new Object[]{0,-1,1.5,"1000"}) {
            JSONObject runtime=M4PhaseDHandlerFixtures.runtime(true);
            runtime.getJSONObject("mediaIdentity").put("durationMs",duration);
            reject(M4PhaseDHandlerFixtures.request(runtime,M4PhaseDHandlerFixtures.manifest()));
        }
        JSONObject runtime=M4PhaseDHandlerFixtures.runtime(true);
        runtime.getJSONObject("mediaIdentity").put("platform","other");
        reject(M4PhaseDHandlerFixtures.request(runtime,M4PhaseDHandlerFixtures.manifest()));
    }
    /** Invalid graphical JSON produces only the closed invalid-package code. */
    @Test public void malformedManifestIsRejectedWithoutContent() throws Exception {
        reject(M4PhaseDHandlerFixtures.withArtifact(M4PhaseDHandlerFixtures.request(true),"manifest",
                "{private-test-sentinel".getBytes(StandardCharsets.UTF_8)));
    }
    /** Parser-valid non-Video source products are rejected by the handler-owned bridge. */
    @Test public void nonVideoProductsAreRejected() throws Exception {
        for (String product:new String[]{"banner","language","other"}) {
            JSONObject manifest=M4PhaseDHandlerFixtures.manifest();manifest.getJSONObject("source").put("product",product);
            reject(M4PhaseDHandlerFixtures.request(M4PhaseDHandlerFixtures.runtime(true),manifest));
        }
    }
    /** Canonical source must be exactly the parsed runtime track id. */
    @Test public void sourceMismatchIsRejected() throws Exception {
        JSONObject manifest=M4PhaseDHandlerFixtures.manifest();manifest.getJSONObject("source").put("sourceId","different-source");
        reject(M4PhaseDHandlerFixtures.request(M4PhaseDHandlerFixtures.runtime(true),manifest));
    }
    /** Missing scenes cannot weaken the one-comment/one-scene relation. */
    @Test public void missingSceneRelationIsRejected() throws Exception {
        JSONObject manifest=M4PhaseDHandlerFixtures.manifest();
        JSONArray scenes=manifest.getJSONArray("scenes");
        if (scenes.length()>1) scenes.remove(scenes.length()-1);else scenes.getJSONObject(0).put("id","missing-comment");
        reject(M4PhaseDHandlerFixtures.request(M4PhaseDHandlerFixtures.runtime(true),manifest));
    }
    /** Additional timed scenes cannot silently execute outside the Video comment stream. */
    @Test public void extraSceneRelationIsRejected() throws Exception {
        JSONObject manifest=M4PhaseDHandlerFixtures.manifest();
        JSONObject extra=new JSONObject(manifest.getJSONArray("scenes").getJSONObject(0).toString());
        extra.put("id","additional-scene");manifest.getJSONArray("scenes").put(extra);
        reject(M4PhaseDHandlerFixtures.request(M4PhaseDHandlerFixtures.runtime(true),manifest));
    }
    /** Duplicate scene ids remain structurally rejected by the existing parser. */
    @Test public void duplicateSceneRelationIsRejected() throws Exception {
        JSONObject manifest=M4PhaseDHandlerFixtures.manifest();
        manifest.getJSONArray("scenes").put(new JSONObject(manifest.getJSONArray("scenes").getJSONObject(0).toString()));
        reject(M4PhaseDHandlerFixtures.request(M4PhaseDHandlerFixtures.runtime(true),manifest));
    }
    /** A same-count scene set with a different id is still inconsistent. */
    @Test public void sceneIdMismatchIsRejected() throws Exception {
        JSONObject manifest=M4PhaseDHandlerFixtures.manifest();manifest.getJSONArray("scenes").getJSONObject(0).put("id","other-comment");
        reject(M4PhaseDHandlerFixtures.request(M4PhaseDHandlerFixtures.runtime(true),manifest));
    }
    /** One millisecond of start or duration drift is rejected with no tolerance. */
    @Test public void exactTimingDriftIsRejected() throws Exception {
        for (String key:new String[]{"startMs","durationMs"}) {
            JSONObject manifest=M4PhaseDHandlerFixtures.manifest();
            JSONObject scene=manifest.getJSONArray("scenes").getJSONObject(0);scene.put(key,scene.getLong(key)+1);
            reject(M4PhaseDHandlerFixtures.request(M4PhaseDHandlerFixtures.runtime(true),manifest));
        }
    }
    /** Parser-valid wall data is unsupported capability rather than an executable clock claim. */
    @Test public void wallClockReturnsUnsupportedCapability() throws Exception {
        JSONObject manifest=M4PhaseDHandlerFixtures.manifest();manifest.getJSONObject("clock").put("mode","wall");
        M4PhaseDHandlerFixtures.rejected(handler,M4PhaseDHandlerFixtures.request(M4PhaseDHandlerFixtures.runtime(true),manifest),
                InstallationStatus.UNSUPPORTED_CAPABILITY);
    }
    /** Generic CONTINUE is refused while the separately tested historical String seam remains compatible. */
    @Test public void manifestedContinueReturnsUnsupportedCapability() throws Exception {
        JSONObject manifest=M4PhaseDHandlerFixtures.manifest();manifest.getJSONObject("clock").put("pauseBehavior","continue");
        M4PhaseDHandlerFixtures.rejected(handler,M4PhaseDHandlerFixtures.request(M4PhaseDHandlerFixtures.runtime(true),manifest),
                InstallationStatus.UNSUPPORTED_CAPABILITY);
    }
    /** Local asset data never advertises a downloader or a shared cache. */
    @Test public void localAssetRefDoesNotClaimRemoteAcquisition() throws Exception {
        JSONObject manifest=M4PhaseDHandlerFixtures.manifest();
        JSONArray elements=manifest.getJSONArray("scenes").getJSONObject(0).getJSONArray("elements");
        JSONObject original=elements.getJSONObject(0);
        JSONObject image=new JSONObject().put("id","local-image").put("type","image")
                .put("frame",original.getJSONObject("frame")).put("zIndex",original.getInt("zIndex"))
                .put("opacity",original.getDouble("opacity")).put("assetRef","asset:local/image.png").put("fit","contain");
        elements.put(0,image);
        PreparedInstallation prepared=handler.prepare(M4PhaseDHandlerFixtures.request(M4PhaseDHandlerFixtures.runtime(true),manifest),TvCapabilities.current());
        assertFalse(prepared.requirements().requiresRemoteAssetAcquisition());
        assertFalse(prepared.requirements().requiresSharedAssetCache());
    }
    /** Exact Unicode, whitespace and canonical bytes survive preparation and cache encoding unchanged. */
    @Test public void cacheEncodingPreservesEveryCanonicalByte() throws Exception {
        InstallRequest request=M4PhaseDHandlerFixtures.request(true);
        PreparedInstallation prepared=handler.prepare(request,TvCapabilities.current());
        assertSame(request,handler.encodeForCache(prepared));
        for (String key:new String[]{"runtime","manifest"})
            assertTrue("canonical bytes unchanged",Arrays.equals(request.artifact(key),handler.encodeForCache(prepared).artifact(key)));
    }
    /** Restore reparses untrusted durable bytes into a fresh trusted state, without activation. */
    @Test public void restoreRebuildsTrustedPreparedState() throws Exception {
        InstallRequest request=M4PhaseDHandlerFixtures.request(true);
        PreparedInstallation first=handler.prepare(request,TvCapabilities.current());
        PreparedInstallation restored=handler.restoreFromCache(handler.encodeForCache(first),TvCapabilities.current());
        assertNotSame(first.preparedState(),restored.preparedState());
        assertEquals(first.revision(),restored.revision());
        assertEquals(first.requirements().timedSceneCount(),restored.requirements().timedSceneCount());
        assertSame(request,restored.canonical());
    }
    /** Pure stages cannot perturb a genuinely visible qualified runtime or its durable cache. */
    @Test public void pureStagesLeaveQualifiedLiveRuntimeUntouched() throws Exception {
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(true);
        M4PhaseAFixtures.Runtime live=new M4PhaseAFixtures.Runtime(memory);assertEquals(13,live.restore());live.due();
        assertTrue(live.controller.hasVisibleScene());
        long generation=live.controller.currentGeneration();int loads=live.loads,shows=live.sink.shows;
        InstallRequest request=M4PhaseDHandlerFixtures.request(true);
        handler.validate(request,TvCapabilities.current());
        PreparedInstallation prepared=handler.prepare(request,TvCapabilities.current());
        handler.encodeForCache(prepared);handler.restoreFromCache(request,TvCapabilities.current());
        assertEquals(0,memory.commits);assertEquals(0,memory.ackCommits);assertEquals(0,memory.clears);
        assertEquals(loads,live.loads);assertEquals(shows,live.sink.shows);assertEquals(generation,live.controller.currentGeneration());
        assertTrue(live.controller.hasVisibleScene());assertEquals(13,live.cache.acknowledged());
    }
    /** Missing capability descriptor never creates a trusted executable preparation. */
    @Test public void nullCapabilitiesFailClosed() throws Exception {
        assertEquals(InstallationStatus.INVALID_PACKAGE,handler.validate(M4PhaseDHandlerFixtures.request(true),null));
    }
    /** Parsed state exposes immutable collections and contains no media Bitmap. */
    @Test public void trustedStateIsImmutableAndTextOnly() throws Exception {
        VideoPreparedState state=(VideoPreparedState)handler.prepare(M4PhaseDHandlerFixtures.request(true),TvCapabilities.current()).preparedState();
        for (ScheduledTrack.Event event:state.track.comments) assertNull(event.mediaBitmap);
        try {state.track.comments.clear();fail("Mutable track");} catch (UnsupportedOperationException expected) { }
        try {state.manifest.scenes.clear();fail("Mutable manifest");} catch (UnsupportedOperationException expected) { }
    }
    /** Rejections report only closed protocol vocabulary in all pure handler entry points. */
    private void reject(InstallRequest request) {M4PhaseDHandlerFixtures.rejected(handler,request,InstallationStatus.INVALID_PACKAGE);}
}
