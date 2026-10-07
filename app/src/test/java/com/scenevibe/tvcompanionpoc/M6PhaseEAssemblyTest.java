package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Execute the actual common ports through the existing single client and installer.
 * Historical Video restoration never fabricates PACKAGE_V1 proof. A higher Video
 * revision establishes the exact durable proof before the shared client may ACK.
 */
public final class M6PhaseEAssemblyTest {
    /** Replace only the fixture's runtime-port seam with the actual Android common-port class. */
    private static OverlayRuntimePorts common(M6PackageFixtures.Harness h) throws Exception {
        OverlayRuntimePorts ports=new OverlayRuntimePorts(h.ports.video.live,h.ports.banner);
        M4PhaseFFixtures.field(h.client,"runtimePorts",ports);
        return ports;
    }
    /** The historical descriptor stays false; only the named assembled descriptor reports WALL. */
    @Test public void capabilityIsSpecificToPackageQualification() {
        assertFalse(TvCapabilities.current().supportsWallClockExecution());
        assertTrue(TvCapabilities.packageQualification().supportsWallClockExecution());
        assertSame(TvCapabilities.packageQualification(),TvCapabilities.packageQualification());
        assertFalse(TvCapabilities.packageQualification().supportsRemoteAssetAcquisition());
        assertFalse(TvCapabilities.packageQualification().supportsSharedAssetCache());
    }
    /** Real common ports remove the opposing activation at every Video/Banner replacement. */
    @Test public void actualCommonPortsAlternateVideoBannerVideoOnOneClient() throws Exception {
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.video(true,16))) {
            common(h);h.fetch();assertEquals(16,h.store.read().acknowledgedRevision());
            h.assignment=M6PackageFixtures.banner(17).put("deviceId",h.assignment.getString("deviceId"));h.fetch();
            assertNotNull(h.owner.submit(()->h.ports.banner.activeState()).get());
            assertEquals(17,h.store.read().acknowledgedRevision());
            h.assignment=M6PackageFixtures.video(true,18);h.fetch();
            assertNull(h.owner.submit(()->h.ports.banner.activeState()).get());
            assertTrue(h.ports.video.controller.hasActiveManifest());
            assertEquals(18,h.store.read().acknowledgedRevision());assertEquals(3,h.acks);
            assertEquals(3,h.backend.candidateWrites);
        }
    }
    /** An offline same-revision restore runs the real Banner handler without repersisting. */
    @Test public void actualCommonPortsRestoreBannerWithoutNetworkOrSecondWrite() throws Exception {
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(17))) {
            OverlayRuntimePorts ports=common(h);h.fetch();
            InstallRequest exact=h.store.read().snapshot().canonical();int acknowledgements=h.acks;
            h.owner.submit(ports::abortActivation).get();
            assertNull(h.owner.submit(()->h.ports.banner.activeState()).get());
            assertEquals(InstallationStatus.ARMED,h.owner.submit(()->h.installer.install(exact,ports)).get());
            assertNotNull(h.owner.submit(()->h.ports.banner.activeState()).get());
            assertEquals(1,h.backend.candidateWrites);assertEquals(acknowledgements,h.acks);
            assertEquals(17,h.store.read().acknowledgedRevision());
        }
    }
    /** An old Video cache can restore, but cannot attest bytes it did not persist. */
    @Test public void historicalUpgradeRequiresHigherVideoForExactPackageProof() throws Exception {
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.video(true,15))) {
            OverlayRuntimePorts ports=common(h);
            org.json.JSONObject envelope=M4PhaseFFixtures.envelope(true,15);
            InstallRequest historical=CloudV1InstallationAdapter.adapt(envelope,envelope.getString("deviceId")).request();
            assertEquals(InstallationStatus.ARMED,h.owner.submit(()->h.installer.install(historical,ports)).get());
            assertTrue(h.store.markAcknowledged(15));
            M6PackageFixtures.refused(h);assertEquals(0,h.acks);assertEquals(15,h.store.read().acknowledgedRevision());
            h.assignment=M6PackageFixtures.video(true,16);h.fetch();
            assertEquals(16,h.store.read().acknowledgedRevision());assertEquals(1,h.acks);
            assertEquals(2,h.backend.candidateWrites);
        }
    }
    /** Clear retires the real Banner activation and its controller without touching durable identity. */
    @Test public void commonAbortRetiresBothKindsAndPreservesDurableAck() throws Exception {
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(17))) {
            OverlayRuntimePorts ports=common(h);h.fetch();String bytes=h.backend.values.get(com.scenevibe.tvcompanionpoc.installation.InstallationStore.SNAPSHOT_KEY);
            h.owner.submit(ports::abortActivation).get();h.owner.submit(ports::abortActivation).get();
            assertNull(h.owner.submit(()->h.ports.banner.activeState()).get());
            assertFalse(h.ports.video.controller.hasActiveManifest());
            assertEquals(bytes,h.backend.values.get(com.scenevibe.tvcompanionpoc.installation.InstallationStore.SNAPSHOT_KEY));
            assertEquals(17,h.store.read().acknowledgedRevision());
        }
    }
    /** A foreign thread cannot retire an active common owner before dispatch to the actual owner. */
    @Test public void commonRetirementRefusesForeignThread() throws Exception {
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(17))) {
            OverlayRuntimePorts ports=common(h);h.fetch();
            assertFalse(ports.retireLegacyVisualOwner());assertFalse(ports.retireVideoVisualOwner());
            assertNotNull(h.owner.submit(()->h.ports.banner.activeState()).get());
        }
    }
}
