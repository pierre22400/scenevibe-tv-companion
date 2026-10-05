package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.PackageInstaller;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static com.scenevibe.tvcompanionpoc.M4PhaseEVideoFixtures.*;
import static org.junit.Assert.*;

/** Consume both exact Phase A historical caches without migration, key changes or synthesized confirmation. */
@RunWith(Parameterized.class)
public final class M4PhaseEHistoricalInstallerTest {
    private final boolean manifested;
    /** Keep only the frozen fixture selector; no historical bytes are reconstructed. */
    public M4PhaseEHistoricalInstallerTest(boolean manifested) {this.manifested=manifested;}
    /** Individually count manifested 13/13 and legacy 14/13 cases in the actual JVM reports. */
    @Parameterized.Parameters(name="manifested={0}") public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{{true},{false}});
    }

    /** A same-revision unknown incoming shape arms exactly the historical durable handler with zero writes. */
    @Test public void sameRevisionConsumesHistoricalCacheWithoutMigrationOrRewrite() throws Exception {
        Map<String,String> values=M4PhaseAFixtures.cacheFixture(manifested).values;
        Map<String,String> before=new HashMap<>(values);Harness test=new Harness(values);long revision=manifested?13:14;
        InstallRequest ignored=new InstallRequest(revision,"unknown.codec.v1",Collections.singletonMap("body",new byte[]{(byte)255}));
        assertEquals(InstallationStatus.ARMED,test.installer.install(ignored,test.ports));
        exact(request(manifested,revision),test.store.read().snapshot());assertEquals(13,test.store.read().acknowledgedRevision());
        assertEquals(manifested?InstallationStore.COMPAT_OVERLAY_HANDLER_ID:InstallationStore.COMPAT_TRACK_HANDLER_ID,test.store.read().snapshot().handlerId());
        assertTrue("all legacy keys byte-identical",before.equals(values));assertFalse(values.containsKey(InstallationStore.SNAPSHOT_KEY));
        assertEquals(0,test.backend.writes+test.backend.ackWrites+test.backend.clears);test.ports.due();
        assertEquals(manifested,test.ports.manifestVisible);assertEquals(!manifested,test.ports.legacyVisible);
    }

    /** Process-local recreation re-arms the historical representation again without an eager generic write. */
    @Test public void recreatedInstallerStillPreservesHistoricalRevisionAckAndExactKeys() throws Exception {
        Map<String,String> values=M4PhaseAFixtures.cacheFixture(manifested).values;Harness test=new Harness(values);
        Map<String,String> before=new HashMap<>(values);long revision=manifested?13:14;
        assertEquals(InstallationStatus.ARMED,test.install(manifested,revision));
        PackageInstaller recreated=new PackageInstaller(new InstallationStore(test.backend),VideoInstallationHandlers.registry(),TvCapabilities.current());
        Ports ports=new Ports(test.backend.trace);assertEquals(InstallationStatus.ARMED,recreated.install(request(!manifested,revision),ports));
        assertTrue("historical tuple unchanged across recreation",before.equals(values));assertEquals(0,test.backend.writes+test.backend.ackWrites);
        assertEquals(revision,ports.activeRevision);assertEquals(13,test.store.read().acknowledgedRevision());
    }

    /** Failed ARM does not rewrite/delete legacy keys or advance their confirmed revision. */
    @Test public void failedHistoricalArmKeepsExactLegacyTupleAndAcknowledgement() throws Exception {
        Map<String,String> values=M4PhaseAFixtures.cacheFixture(manifested).values;Map<String,String> before=new HashMap<>(values);
        Harness test=new Harness(values);test.ports.failAt="load";long revision=manifested?13:14;
        assertEquals(InstallationStatus.ARM_FAILED,test.install(manifested,revision));
        assertTrue("historical bytes retained after failure",before.equals(values));assertEquals(0,test.backend.writes+test.backend.ackWrites+test.backend.clears);
        assertEquals(revision,test.store.read().snapshot().revision());assertEquals(13,test.store.read().acknowledgedRevision());
    }

    /** Historical capability/semantic failure remains pending rather than being migrated or guessed into validity. */
    @Test public void corruptHistoricalArtifactIsRejectedWithoutMigrationOrIdentityAccess() throws Exception {
        Map<String,String> values=M4PhaseAFixtures.cacheFixture(manifested).values;values.put("runtime","not JSON");
        Map<String,String> before=new HashMap<>(values);Harness test=new Harness(values);long revision=manifested?13:14;
        assertEquals(InstallationStatus.ARM_FAILED,test.install(manifested,revision));assertTrue(before.equals(values));
        assertEquals(0,test.backend.writes+test.backend.ackWrites+test.ports.loads);
        assertEquals(13,test.store.read().acknowledgedRevision());
    }
}
