package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationSnapshot;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** The actual capture projection and screen share coherent bounded generic/historical metadata. */
@RunWith(Parameterized.class)
public final class M4PhaseGDiagnosticsTest {
    private final boolean manifested,generic;
    /** Read representation metadata independently of Video shape. */
    public M4PhaseGDiagnosticsTest(boolean manifested, boolean generic) {this.manifested=manifested;this.generic=generic;}
    /** All four profiles must use generic truth and leave aliases coherent. */
    @Parameterized.Parameters(name="manifested={0},generic={1}") public static Collection<Object[]> profiles() {
        return M4PhaseGFixtures.profiles();
    }
    /** Durable revision/ACK and binding are exact; compatibility presence/revisions are aliases, never raw cache truth. */
    @Test public void genericMetadataAndCompatibilityAliasesDescribeOneCoherentRead() throws Exception {
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(M4PhaseGFixtures.durable(manifested,generic));
        Map<String,String> prior=new HashMap<>(h.backend.values);InstallationSnapshot snapshot=h.store.read().snapshot();
        RuntimeDiagnostics d=h.diagnostics();
        assertEquals(RuntimeDiagnostics.InstallationState.READY,d.installationState);assertTrue(d.installationPresent);
        assertEquals(snapshot.revision(),d.installedRevision);assertEquals(13,d.acknowledgedRevision);
        assertEquals(snapshot.codecId(),d.packageCodecId);assertEquals(snapshot.handlerId(),d.packageHandlerId);
        assertEquals(d.installationPresent,d.cachedTrackPresent);assertEquals(d.installedRevision,d.cachedRevision);
        assertEquals(d.acknowledgedRevision,d.lastAcknowledgedRevision);assertNull(d.cachedTrackId);
        h.unchanged(prior);h.disarmed();
    }
    /** Repeated screen refresh neither parses content nor modifies durable storage or local ARM/Cloud counters. */
    @Test public void refreshRendersGenericTruthBeforeCompatibilityWithoutPayloadOrWrites() throws Exception {
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(M4PhaseGFixtures.durable(manifested,generic));
        Map<String,String> prior=new HashMap<>(h.backend.values);
        assertEquals(InstallationStatus.ARMED,h.restore());
        for(int i=0;i<3;i++) {
            RuntimeDiagnostics d=h.diagnostics();String rendered=DiagnosticsActivity.render(d);
            assertTrue(rendered.contains("Installed package: yes"));
            assertTrue(rendered.contains("Package codec: "+d.packageCodecId));assertTrue(rendered.contains("Package handler: "+d.packageHandlerId));
            assertTrue(rendered.contains("Installed revision: "+d.installedRevision));assertTrue(rendered.contains("Acknowledged revision: 13"));
            assertTrue(rendered.contains("Last startup restore: ARMED"));
            assertTrue(rendered.indexOf("Installed revision:")<rendered.indexOf("Cached revision (compatibility):"));
            assertFalse(rendered.contains(M4PhaseAFixtures.UNICODE));assertFalse(rendered.contains(prior.get("runtime")));
            if(manifested)assertFalse(rendered.contains(prior.get("manifest")));
        }
        assertEquals(1,h.runtime.loads);assertEquals(0,h.runtime.shows);h.unchanged(prior);
    }
    /** Consumer offline wording depends on snapshot presence, with no installation metadata exposed in normal UI. */
    @Test public void offlineNormalStatusUsesSavedContentAndRetainsConnectionPriority() throws Exception {
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(M4PhaseGFixtures.durable(manifested,generic));
        Map<String,String> prior=new HashMap<>(h.backend.values);
        String offline=MainActivity.cloudStatus(null,true,true,h.store.read());
        assertEquals("Offline (using saved SceneVibe content)",offline);
        assertFalse(offline.contains(h.store.read().snapshot().codecId()));assertFalse(offline.contains("revision"));
        assertEquals("Code: 123456\nWaiting for connection...",MainActivity.cloudStatus("123456",true,true,h.store.read()));
        assertEquals("Connected",MainActivity.cloudStatus(null,false,true,h.store.read()));
        assertEquals("Not connected",MainActivity.cloudStatus(null,false,false,h.store.read()));h.unchanged(prior);
    }
}
