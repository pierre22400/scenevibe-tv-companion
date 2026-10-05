package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.ExecutionRequirements;
import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationHandlerRegistry;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.PreparedInstallation;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/**
 * Compose the new pure values beside the actual qualified legacy and manifested cores.
 * The models are never handed to those cores. Observed cache, ACK, generation, scheduler
 * and visual-owner state must stay unchanged, including when a capability is rejected.
 */
public final class M4PhaseBSideEffectTest {
    /** Snapshot existing durable bytes into a product-neutral immutable artifact map. */
    private static InstallRequest request(M4PhaseAFixtures.Memory memory,boolean manifested) {
        Map<String,byte[]> artifacts=new HashMap<>();
        artifacts.put("runtime",memory.values.get("runtime").getBytes(StandardCharsets.UTF_8));
        if (manifested) artifacts.put("manifest",memory.values.get("manifest").getBytes(StandardCharsets.UTF_8));
        return new InstallRequest(20,manifested?TvCapabilities.CODEC_TRACK_OVERLAY:TvCapabilities.CODEC_TRACK,artifacts);
    }

    /** Derive only the currently supported local execution profile; this does not install it. */
    private static ExecutionRequirements requirements(boolean manifested,ExecutionRequirements.ClockMode clock) {
        return new ExecutionRequirements(manifested?TvCapabilities.OVERLAY_CONTRACT:TvCapabilities.LEGACY_CONTRACT,
                clock,ExecutionRequirements.PauseBehavior.FREEZE,1,false,false);
    }

    /** Inert preparation neither changes an armed cache nor selects a new visual owner. */
    @Test public void pureModelsLeaveBothQualifiedInstallationPathsUntouched() throws Exception {
        for (boolean manifested:new boolean[]{true,false}) {
            M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(manifested);
            M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(memory);
            runtime.restore();
            Map<String,String> before=new HashMap<>(memory.values);
            int loads=runtime.loads;long generation=runtime.controller.currentGeneration();
            InstallRequest request=request(memory,manifested);
            PreparedInstallation prepared=new PreparedInstallation(request,"local-test",Map.of(),
                    requirements(manifested,ExecutionRequirements.ClockMode.MEDIA),TvCapabilities.current());
            assertEquals(InstallationStatus.PREPARED,prepared.status());
            assertEquals(20,prepared.revision());
            assertNull(InstallationHandlerRegistry.empty().findCodec(request.codecId()));
            assertTrue("durable tuple unchanged",before.equals(memory.values));
            assertEquals(0,memory.commits);assertEquals(0,memory.ackCommits);assertEquals(0,memory.clears);
            assertEquals(13,runtime.cache.acknowledged());
            assertEquals(manifested?13:14,runtime.cache.revision());
            assertEquals(loads,runtime.loads);assertEquals(generation,runtime.controller.currentGeneration());
            assertEquals(0,runtime.sink.shows);assertEquals(0,runtime.sink.hides);
            assertEquals(manifested,runtime.controller.hasActiveManifest());
        }
    }

    /** Rejecting an unexecutable wall profile cannot retire or ACK a currently visible scene. */
    @Test public void unsupportedCapabilityCannotAffectCurrentVisibleRevision() throws Exception {
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(true);
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(memory);
        runtime.restore();runtime.due();
        Map<String,String> before=new HashMap<>(memory.values);
        int loads=runtime.loads;long generation=runtime.controller.currentGeneration();
        InstallRequest request=request(memory,true);
        ExecutionRequirements wall=requirements(true,ExecutionRequirements.ClockMode.WALL);
        assertEquals(InstallationStatus.UNSUPPORTED_CAPABILITY,TvCapabilities.current().validate(request,wall));
        assertThrows(IllegalArgumentException.class,()->new PreparedInstallation(
                request,"local-test",Map.of(),wall,TvCapabilities.current()));
        assertTrue("durable tuple unchanged",before.equals(memory.values));
        assertEquals(0,memory.commits);assertEquals(0,memory.ackCommits);
        assertEquals(loads,runtime.loads);assertEquals(generation,runtime.controller.currentGeneration());
        assertEquals(13,runtime.cache.revision());assertEquals(13,runtime.cache.acknowledged());
        assertTrue(runtime.controller.hasVisibleScene());assertEquals(1,runtime.sink.shows);
        assertEquals(0,runtime.sink.hides);assertEquals(0,runtime.legacyRenders);
    }
}
