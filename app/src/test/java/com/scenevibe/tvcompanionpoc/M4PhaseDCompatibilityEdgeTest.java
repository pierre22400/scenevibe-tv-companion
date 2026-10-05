package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Characterize historical capability edges before extracting any Video semantics. */
public final class M4PhaseDCompatibilityEdgeTest {
    /** Current media/continue acceptance must survive the generic capability tightening. */
    @Test public void productionContinueProfileInstallsAndRestoresWithoutRewritingBytes() throws Exception {
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(true);
        String runtime=memory.values.get("runtime");
        JSONObject manifest=new JSONObject(memory.values.get("manifest"));
        manifest.getJSONObject("clock").put("pauseBehavior","continue");
        String canonical=manifest.toString();
        M4PhaseAFixtures.Runtime live=new M4PhaseAFixtures.Runtime(memory);
        CloudTrackRepository.InstallResult result=live.cache.install(14,runtime,canonical,live.scheduler);
        assertTrue(result.ok);
        assertEquals(RuntimeDiagnostics.ManifestCode.NONE,result.code);
        assertEquals(canonical,memory.values.get("manifest"));
        assertEquals(14,live.restore());
        assertEquals(1,memory.commits);
        assertEquals(13,live.cache.acknowledged());
    }

    /** Parser-valid wall data remains structurally refused by the current Video bridge. */
    @Test public void productionWallProfileRemainsManifestInvalidWithoutMutation() throws Exception {
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(true);
        JSONObject manifest=new JSONObject(memory.values.get("manifest"));
        manifest.getJSONObject("clock").put("mode","wall");
        M4PhaseAFixtures.Runtime live=new M4PhaseAFixtures.Runtime(memory);
        CloudTrackRepository.InstallResult result=live.cache.install(14,memory.values.get("runtime"),
                manifest.toString(),live.scheduler);
        assertFalse(result.ok);
        assertEquals(RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID,result.code);
        assertEquals(13,live.cache.revision());
        assertEquals(0,memory.commits);
        assertEquals(0,live.loads);
    }

    /** Legacy absent pause flag retains the parser's historical false/continue interpretation. */
    @Test public void productionLegacyAbsentPauseFlagRemainsAccepted() throws Exception {
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(false);
        JSONObject runtime=new JSONObject(memory.values.get("runtime"));
        runtime.remove("pauseFreezesDisplay");
        M4PhaseAFixtures.Runtime live=new M4PhaseAFixtures.Runtime(memory);
        assertTrue(live.cache.install(15,runtime.toString(),live.scheduler));
        assertEquals(15,live.restore());
        assertFalse(TrackParser.parse(new JSONObject(memory.values.get("runtime")),asset->null).pauseFreezesDisplay);
        assertNull(memory.values.get("manifest"));
    }
}
