package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.PackageInstaller;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/** Startup empty, owner-unavailable and read-failure edges remain observational and write-free. */
public final class M4PhaseGStartupStateTest {
    /** EMPTY skips all handler/runtime calls and removes a previous process-local startup observation. */
    @Test public void emptyStoreDoesNothingAndCannotReuseOldObservation() {
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(new HashMap<>());
        h.observed.setLastStartupRestoreResult(InstallationStatus.ARMED);
        assertNull(h.restore());assertNull(h.observed.lastStartupRestoreResult());h.disarmed();
        assertTrue(h.backend.trace.isEmpty());h.unchanged(new HashMap<>());
    }
    /** A zero ACK in an otherwise empty file is still clean empty, without a cleanup write. */
    @Test public void emptyWithZeroAckRemainsEmptyAndUntouched() {
        Map<String,String> values=new HashMap<>();values.put("ackRevision","0");
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(values);
        assertNull(h.restore());assertEquals(InstallationStore.ReadState.EMPTY,h.store.read().state());
        h.unchanged(new HashMap<>(values));h.disarmed();
    }
    /** Real service ports reject a non-owner without touching any runtime or durable state. */
    @Test public void offOwnerRestoreDoesNotManufactureActiveSelection() throws Exception {
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(M4PhaseGFixtures.durable(true,true));
        Map<String,String> prior=new HashMap<>(h.backend.values);h.runtime.ownerAllowed=false;
        assertEquals(InstallationStatus.ARM_FAILED,h.restore());h.disarmed();h.unchanged(prior);
        assertEquals(0,h.runtime.loads);assertFalse(h.backend.trace.contains("abort"));
    }
    /** Teardown-unavailable scheduler/controller cannot pretend that the pending snapshot is armed. */
    @Test public void unavailableRuntimePreservesPendingSnapshotAndAck() throws Exception {
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(M4PhaseGFixtures.durable(false,true));
        Map<String,String> prior=new HashMap<>(h.backend.values);h.runtime.available=false;
        assertEquals(InstallationStatus.ARM_FAILED,h.restore());h.disarmed();h.unchanged(prior);
    }
    /** The real store converts ordinary backend read failures into CORRUPT; no raw cause reaches diagnostics. */
    @Test public void storageReadExceptionIsBoundedAndCannotClearOrFallback() throws Exception {
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(M4PhaseGFixtures.durable(true,true));
        InstallationStore store=new InstallationStore(new InstallationStore.Backend() {
            /** Preserve the shared file monitor. */
            @Override public Object monitor() {return h.backend.monitor();}
            /** Model an ordinary malformed typed/disk read before any handler may run. */
            @Override public String get(String key) {throw new IllegalStateException("sensitive backend cause");}
            /** Startup must never use a write to recover an unavailable read. */
            @Override public boolean commit(Map<String,String> puts,Set<String> removed,boolean clear) {throw new AssertionError("No startup write");}
        });
        PackageInstaller installer=new PackageInstaller(store,VideoInstallationHandlers.registry(),TvCapabilities.current());
        assertEquals(InstallationStatus.CACHE_FAILED,OverlayService.restoreInstalledPackage(store,installer,h.runtime.ports,h.observed));
        h.disarmed();assertEquals(0,h.runtime.loads);assertFalse(DiagnosticsActivity.render(
                RuntimeDiagnostics.installationSnapshot(store,h.observed).build()).contains("sensitive"));
    }
    /** Confirmed generic ACK equality is preserved just as pending ACK inequality is preserved. */
    @Test public void alreadyConfirmedSnapshotRestoresWithoutAckWrite() throws Exception {
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(M4PhaseGFixtures.durable(true,true));
        assertTrue(h.store.markAcknowledged(15));h.backend.ackWrites=0;
        Map<String,String> prior=new HashMap<>(h.backend.values);
        assertEquals(InstallationStatus.ARMED,h.restore());assertEquals(15,h.store.read().acknowledgedRevision());h.unchanged(prior);
    }
}
