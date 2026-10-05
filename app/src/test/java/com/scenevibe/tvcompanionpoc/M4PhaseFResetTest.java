package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Qualify actual exceptional reset against generic/historical state and real owner-thread runtime clear. */
@RunWith(Parameterized.class)
public final class M4PhaseFResetTest {
    private final boolean manifested,generic;
    /** Select historical versus generic authority independently of the supported Video profile. */
    public M4PhaseFResetTest(boolean manifested,boolean generic) {this.manifested=manifested;this.generic=generic;}
    /** Every installed representation must be erased by the same bounded asynchronous reset. */
    @Parameterized.Parameters(name="manifested={0},generic={1}") public static Collection<Object[]> profiles() {
        Collection<Object[]> values=new ArrayList<>();
        for(boolean manifested:new boolean[]{true,false})for(boolean generic:new boolean[]{true,false})values.add(new Object[]{manifested,generic});
        return values;
    }
    /** Create the selected storage authority through the real Cloud pipeline, never a mocked clear result. */
    private M4PhaseFFixtures.Harness installed() throws Exception {
        M4PhaseFFixtures.Harness h=generic?new M4PhaseFFixtures.Harness(manifested,15)
                :new M4PhaseFFixtures.Harness(M4PhaseFFixtures.historical(manifested),M4PhaseFFixtures.envelope(manifested,manifested?13:14));
        h.fetch();return h;
    }
    /** Reset returns while prior io is paused, then rotates/wipes on io and clears actual runtime on its owner. */
    @Test public void resetRemainsAsyncAndClearsWholeInstallationOnOwner() throws Exception {
        try(M4PhaseFFixtures.Harness h=installed()) {
            h.onOwner(()->{h.runtime.due();return null;});CountDownLatch paused=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(1);
            h.io.execute(()->{paused.countDown();await(release);});assertTrue(paused.await(3,TimeUnit.SECONDS));
            h.client.reset(done::countDown);
            try {assertEquals(1,done.getCount());assertEquals(0,h.rotations);assertNotNull(h.credentials.deviceToken());
                assertEquals(InstallationStore.ReadState.SNAPSHOT,h.store.read().state());assertTrue(h.io.isShutdown());}
            finally {release.countDown();}
            assertTrue(done.await(3,TimeUnit.SECONDS));assertEquals(1,h.rotations);assertNull(h.credentials.deviceToken());assertNull(h.credentials.cloudDeviceId());
            assertTrue(h.backend.values.isEmpty());assertEquals(0,h.store.read().acknowledgedRevision());assertEquals(0,h.runtime.active);
            assertEquals(h.ownerThread,h.backend.clearThread);assertEquals(h.ownerThread,h.runtime.resetThread);
            assertFalse(h.runtime.legacyVisible);assertFalse(h.runtime.manifestVisible);assertFalse(h.runtime.controller.hasActiveManifest());
            assertTrue(h.backend.trace.indexOf("rotate")<h.backend.trace.indexOf("clear"));
        }
    }
    /** A failed durable wipe is bounded, leaves the candidate/ACK readable, and still clears runtime/credentials. */
    @Test public void failedResetClearIsReportedWithoutManufacturedEmptyState() throws Exception {
        try(M4PhaseFFixtures.Harness h=installed()) {
            h.backend.clearWritable=false;CountDownLatch done=new CountDownLatch(1);h.client.reset(done::countDown);assertTrue(done.await(3,TimeUnit.SECONDS));
            assertEquals(InstallationStore.ReadState.SNAPSHOT,h.store.read().state());assertTrue(h.store.read().acknowledgedRevision()>0);
            assertEquals(0,h.runtime.active);assertNull(h.credentials.deviceToken());assertEquals(1,h.rotations);
            assertEquals(RuntimeDiagnostics.CloudErrorCode.NETWORK,DiagnosticsStore.INSTANCE.lastCloudErrorCode());
        }
    }
    /** A completed reset spends the old executor but allows a fresh generic client over the service's same stack. */
    @Test public void completedResetAllowsFreshClientConstruction() throws Exception {
        try(M4PhaseFFixtures.Harness h=installed()) {
            CountDownLatch done=new CountDownLatch(1);h.client.reset(done::countDown);assertTrue(done.await(3,TimeUnit.SECONDS));
            assertTrue(h.io.isShutdown());assertTrue(OverlayService.shouldReconstructCloudClient(true,false));
            ScheduledExecutorService freshIo=Executors.newSingleThreadScheduledExecutor();
            CloudControlClient fresh=new CloudControlClient(freshIo,h.credentials,h.store,h.installer::install,h.runtime.ports,h.runtime::reset);
            try {assertFalse(freshIo.isShutdown());assertEquals(InstallationStore.ReadState.EMPTY,h.store.read().state());}
            finally {fresh.stop();}
        }
    }
    /** Keep deterministic fixture blocking bounded and interruption-aware. */
    private static void await(CountDownLatch latch) {
        try {assertTrue(latch.await(3,TimeUnit.SECONDS));}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("Fixture io interrupted");}
    }
}
