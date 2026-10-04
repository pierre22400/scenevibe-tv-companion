package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.ExecutionRequirements;
import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.PreparedInstallation;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Lock no-manifest compatibility, exact text bytes and actual parsed pause/count requirements. */
public final class M4PhaseDLegacyHandlerTest {
    private final VideoLegacyInstallationHandler handler=VideoInstallationHandlers.legacy();

    /** Frozen legacy bytes select the exact durable handler/codec identity and comment count. */
    @Test public void frozenLegacyFixturePreparesExactIdentityAndCount() throws Exception {
        InstallRequest request=M4PhaseDHandlerFixtures.request(false);
        assertEquals(InstallationStatus.VALIDATED,handler.validate(request,TvCapabilities.current()));
        PreparedInstallation prepared=handler.prepare(request,TvCapabilities.current());
        VideoPreparedState state=(VideoPreparedState)prepared.preparedState();
        assertEquals(InstallationStore.COMPAT_TRACK_HANDLER_ID,prepared.handlerId());
        assertEquals(TvCapabilities.CODEC_TRACK,prepared.codecId());
        assertEquals(request.revision(),prepared.revision());
        assertEquals(TvCapabilities.LEGACY_CONTRACT,prepared.requirements().renderingContract());
        assertEquals(ExecutionRequirements.ClockMode.MEDIA,prepared.requirements().clock());
        assertEquals(state.track.comments.size(),prepared.requirements().timedSceneCount());
        assertNull(state.manifest);
        assertFalse(prepared.requirements().requiresRemoteAssetAcquisition());
        assertFalse(prepared.requirements().requiresSharedAssetCache());
    }
    /** A legacy package cannot carry a graphical half. */
    @Test public void manifestArtifactIsRejected() throws Exception {
        InstallRequest manifested=M4PhaseDHandlerFixtures.request(true);
        reject(new InstallRequest(1,TvCapabilities.CODEC_TRACK,manifested.artifacts()));
    }
    /** Unknown artifact names are refused even when their count is one. */
    @Test public void unknownArtifactIsRejected() {
        reject(new InstallRequest(1,TvCapabilities.CODEC_TRACK,java.util.Collections.singletonMap("unknown",new byte[]{1})));
    }
    /** The manifested codec cannot be mislabeled as this legacy handler. */
    @Test public void wrongCodecIsRejected() throws Exception {
        reject(new InstallRequest(1,TvCapabilities.CODEC_TRACK_OVERLAY,M4PhaseDHandlerFixtures.request(false).artifacts()));
    }
    /** Malformed UTF-8 is rejected rather than becoming replacement text. */
    @Test public void malformedUtf8IsRejected() throws Exception {
        reject(M4PhaseDHandlerFixtures.withArtifact(M4PhaseDHandlerFixtures.request(false),"runtime",new byte[]{(byte)0xc3,0x28}));
    }
    /** The old runtime UTF-16 ceiling remains inclusive, with no silent truncation. */
    @Test public void runtimeUtf16BoundIsExact() throws Exception {
        InstallRequest request=M4PhaseDHandlerFixtures.request(false);
        String value=new String(request.artifact("runtime"),StandardCharsets.UTF_8);
        String padded=value+" ".repeat(400_000-value.length());
        assertEquals(InstallationStatus.VALIDATED,handler.validate(M4PhaseDHandlerFixtures.withArtifact(request,"runtime",
                padded.getBytes(StandardCharsets.UTF_8)),TvCapabilities.current()));
        reject(M4PhaseDHandlerFixtures.withArtifact(request,"runtime",(padded+" ").getBytes(StandardCharsets.UTF_8)));
    }
    /** Invalid JSON, contract or comment array stays invalid under the same text-only parser. */
    @Test public void malformedRuntimeTypeAndCommentsAreRejected() throws Exception {
        reject(M4PhaseDHandlerFixtures.withArtifact(M4PhaseDHandlerFixtures.request(false),"runtime","[".getBytes(StandardCharsets.UTF_8)));
        JSONObject runtime=M4PhaseDHandlerFixtures.runtime(false);runtime.put("type","unknown");reject(M4PhaseDHandlerFixtures.request(runtime,null));
        for (Object comments:new Object[]{JSONObject.NULL,new JSONArray(),new JSONArray().put(false)}) {
            runtime=M4PhaseDHandlerFixtures.runtime(false);runtime.put("comments",comments);reject(M4PhaseDHandlerFixtures.request(runtime,null));
        }
    }
    /** Presence of a comment media field is refused even when its value is null. */
    @Test public void commentMediaIsRejected() throws Exception {
        JSONObject runtime=M4PhaseDHandlerFixtures.runtime(false);runtime.getJSONArray("comments").getJSONObject(0).put("media",JSONObject.NULL);
        reject(M4PhaseDHandlerFixtures.request(runtime,null));
    }
    /** Legacy identity checks are the existing media parser's checks, without a new business model. */
    @Test public void invalidMediaIdentityIsRejected() throws Exception {
        JSONObject runtime=M4PhaseDHandlerFixtures.runtime(false);runtime.getJSONObject("mediaIdentity").put("platform","other");
        reject(M4PhaseDHandlerFixtures.request(runtime,null));
    }
    /** A true parsed flag advertises only the qualified legacy FREEZE behavior. */
    @Test public void truePauseDerivesFreeze() throws Exception {
        JSONObject runtime=M4PhaseDHandlerFixtures.runtime(false);runtime.put("pauseFreezesDisplay",true);
        assertEquals(ExecutionRequirements.PauseBehavior.FREEZE,handler.prepare(M4PhaseDHandlerFixtures.request(runtime,null),TvCapabilities.current()).requirements().pauseBehavior());
    }
    /** A false parsed flag advertises the existing legacy display countdown, not a new media clock. */
    @Test public void falsePauseDerivesContinue() throws Exception {
        JSONObject runtime=M4PhaseDHandlerFixtures.runtime(false);runtime.put("pauseFreezesDisplay",false);
        assertEquals(ExecutionRequirements.PauseBehavior.CONTINUE,handler.prepare(M4PhaseDHandlerFixtures.request(runtime,null),TvCapabilities.current()).requirements().pauseBehavior());
    }
    /** Absent pause flag retains the TrackParser default false interpretation. */
    @Test public void absentPauseDerivesContinue() throws Exception {
        JSONObject runtime=M4PhaseDHandlerFixtures.runtime(false);runtime.remove("pauseFreezesDisplay");
        assertEquals(ExecutionRequirements.PauseBehavior.CONTINUE,handler.prepare(M4PhaseDHandlerFixtures.request(runtime,null),TvCapabilities.current()).requirements().pauseBehavior());
    }
    /** Maximum comment cardinality becomes the actual bounded timed count, never a guessed scene count. */
    @Test public void actualCommentCountDerivesTimedSceneCount() throws Exception {
        JSONObject runtime=M4PhaseDHandlerFixtures.runtime(false);
        JSONObject original=runtime.getJSONArray("comments").getJSONObject(0);JSONArray comments=new JSONArray();
        for (int i=0;i<256;i++) comments.put(new JSONObject(original.toString()).put("id","comment:"+i));
        runtime.put("comments",comments);
        assertEquals(256,handler.prepare(M4PhaseDHandlerFixtures.request(runtime,null),TvCapabilities.current()).requirements().timedSceneCount());
    }
    /** Cache encoding returns the exact canonical request and preserves every Unicode byte. */
    @Test public void encodeForCachePreservesCanonicalBytes() throws Exception {
        InstallRequest request=M4PhaseDHandlerFixtures.request(false);
        PreparedInstallation prepared=handler.prepare(request,TvCapabilities.current());assertSame(request,handler.encodeForCache(prepared));
        assertTrue("exact legacy bytes",Arrays.equals(request.artifact("runtime"),handler.encodeForCache(prepared).artifact("runtime")));
    }
    /** Durable restore rebuilds trusted parsed state without any manifested parse or runtime effect. */
    @Test public void restoreRevalidatesAndRebuildsTrustedState() throws Exception {
        InstallRequest request=M4PhaseDHandlerFixtures.request(false);
        PreparedInstallation prepared=handler.prepare(request,TvCapabilities.current());
        PreparedInstallation restored=handler.restoreFromCache(request,TvCapabilities.current());
        assertNotSame(prepared.preparedState(),restored.preparedState());assertNull(((VideoPreparedState)restored.preparedState()).manifest);
        assertSame(request,restored.canonical());assertEquals(prepared.requirements().timedSceneCount(),restored.requirements().timedSceneCount());
    }
    /** Pure legacy stages leave cache, live scheduler and visible manifested owner unchanged. */
    @Test public void pureLegacyStagesCannotRetireALiveManifestedRevision() throws Exception {
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(true);
        M4PhaseAFixtures.Runtime live=new M4PhaseAFixtures.Runtime(memory);live.restore();live.due();
        long generation=live.controller.currentGeneration();int loads=live.loads;
        InstallRequest request=M4PhaseDHandlerFixtures.request(false);handler.validate(request,TvCapabilities.current());
        PreparedInstallation prepared=handler.prepare(request,TvCapabilities.current());handler.encodeForCache(prepared);handler.restoreFromCache(request,TvCapabilities.current());
        assertTrue(live.controller.hasVisibleScene());assertEquals(generation,live.controller.currentGeneration());assertEquals(loads,live.loads);
        assertEquals(0,memory.commits);assertEquals(0,memory.ackCommits);assertEquals(0,memory.clears);
    }
    /** Legacy rejection methods carry only a closed invalid-package status. */
    private void reject(InstallRequest request) {M4PhaseDHandlerFixtures.rejected(handler,request,InstallationStatus.INVALID_PACKAGE);}
}
