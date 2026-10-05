package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationSnapshot;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Directly qualify additive generic clear against both generic and frozen historical storage shapes. */
@RunWith(Parameterized.class)
public final class M4PhaseFClearStoreTest {
    private final boolean manifested;
    /** Both supported handler shapes have identical whole-file reset semantics. */
    public M4PhaseFClearStoreTest(boolean manifested) {this.manifested=manifested;}
    /** Exercise runtime-only and runtime-plus-manifest bytes without changing the fixtures. */
    @Parameterized.Parameters(name="manifested={0}") public static Collection<Object[]> profiles() {
        return Arrays.asList(new Object[][]{{true},{false}});
    }
    /** One generic clear removes candidate, separate ACK and physical historical residue atomically. */
    @Test public void clearAllRemovesGenericSnapshotAckAndResidueInOneBatch() throws Exception {
        M4PhaseFFixtures.Backend backend=new M4PhaseFFixtures.Backend(M4PhaseFFixtures.historical(manifested));
        InstallationStore store=new InstallationStore(backend);
        assertEquals(InstallationStore.CommitState.COMMITTED,store.commit(new InstallationSnapshot(
                M4PhaseEVideoFixtures.request(manifested,15),manifested?InstallationStore.COMPAT_OVERLAY_HANDLER_ID:InstallationStore.COMPAT_TRACK_HANDLER_ID)));
        assertTrue(store.markAcknowledged(15));assertTrue(store.clearAll());assertEquals(1,backend.clears);
        assertTrue(backend.values.isEmpty());assertEquals(InstallationStore.ReadState.EMPTY,store.read().state());assertEquals(0,store.read().acknowledgedRevision());
    }
    /** The historical void API delegates to the same exact whole-file primitive and retains its signature. */
    @Test public void historicalApiClearsHistoricalAndGenericStateCompatibly() throws Exception {
        M4PhaseFFixtures.Backend backend=new M4PhaseFFixtures.Backend(M4PhaseFFixtures.historical(manifested));
        InstallationStore store=new InstallationStore(backend);store.clearHistorical();assertEquals(1,backend.clears);assertTrue(backend.values.isEmpty());
        assertEquals(InstallationStore.CommitState.COMMITTED,store.commit(new InstallationSnapshot(
                M4PhaseEVideoFixtures.request(manifested,15),manifested?InstallationStore.COMPAT_OVERLAY_HANDLER_ID:InstallationStore.COMPAT_TRACK_HANDLER_ID)));
        store.clearHistorical();assertTrue(backend.values.isEmpty());assertEquals(2,backend.clears);
    }
    /** A failed atomic clear is observable and preserves the entire prior readable representation/ACK. */
    @Test public void refusedClearPreservesPriorSnapshotAndAcknowledgement() throws Exception {
        M4PhaseFFixtures.Backend backend=new M4PhaseFFixtures.Backend(M4PhaseFFixtures.historical(manifested));
        InstallationStore store=new InstallationStore(backend);Map<String,String> before=new HashMap<>(backend.values);backend.clearWritable=false;
        assertFalse(store.clearAll());assertEquals(before,backend.values);assertEquals(13,store.read().acknowledgedRevision());
    }
    /** Ordinary backend failure returns false without echoing raw exceptions or treating reset as successful. */
    @Test public void thrownClearIsBoundedAndKeepsPreviousState() throws Exception {
        M4PhaseFFixtures.Backend backend=new M4PhaseFFixtures.Backend(M4PhaseFFixtures.historical(manifested));
        InstallationStore store=new InstallationStore(new InstallationStore.Backend() {
            /** Use the same stable preference identity. */
            @Override public Object monitor() {return backend.monitor();}
            /** Preserve the exact frozen readable prior bytes. */
            @Override public String get(String key) {return backend.get(key);}
            /** Throw before publication, modeling an ordinary disk/editor failure. */
            @Override public boolean commit(Map<String,String> puts,Set<String> removed,boolean clear) {throw new IllegalStateException("Injected disk failure");}
        });
        Map<String,String> before=new HashMap<>(backend.values);assertFalse(store.clearAll());assertEquals(before,backend.values);
    }
    /** InstallationStore has no reference to independent identity, pairing or credential preference files. */
    @Test public void clearDoesNotTouchSeparateIdentityOrCredentialStorage() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(manifested,15)) {
            h.fetch();String device=h.credentials.cloudDeviceId(),token=h.credentials.deviceToken();
            assertTrue(h.credentials.connected());assertTrue(h.store.clearAll());assertEquals(InstallationStore.ReadState.EMPTY,h.store.read().state());
            assertEquals(device,h.credentials.cloudDeviceId());assertEquals(token,h.credentials.deviceToken());assertTrue(h.credentials.connected());
            assertEquals(0,h.rotations);
        }
    }
}
