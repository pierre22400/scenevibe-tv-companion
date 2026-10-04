package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Execute frozen historical caches through actual live Cloud redelivery without migration or residue deletion. */
@RunWith(Parameterized.class)
public final class M4PhaseFHistoricalRedeliveryTest {
    private final boolean manifested;
    /** Select the exact manifested 13/13 or legacy 14/13 historical cache. */
    public M4PhaseFHistoricalRedeliveryTest(boolean manifested) {this.manifested=manifested;}
    /** Both unambiguous historical shapes retain the same compatibility view. */
    @Parameterized.Parameters(name="manifested={0}") public static Collection<Object[]> profiles() {
        return Arrays.asList(new Object[][]{{true},{false}});
    }
    /** Redelivery restores bytes, arms before ACK, and leaves every historical artifact and generic marker exact. */
    @Test public void historicalSameRevisionArmsWithoutMigrationThenPersistsOnlyConfirmedAck() throws Exception {
        Map<String,String> values=M4PhaseFFixtures.historical(manifested),before=new HashMap<>(values);
        long revision=manifested?13:14;
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(values,M4PhaseFFixtures.envelope(manifested,revision))) {
            h.onAck=()->{
                assertEquals(0,h.backend.candidateWrites+h.backend.ackWrites);assertEquals(before,h.backend.values);
                assertEquals(revision,h.runtime.active);assertNull(h.backend.values.get(InstallationStore.SNAPSHOT_KEY));
            };
            h.fetch();assertEquals(0,h.backend.candidateWrites);assertEquals(1,h.backend.ackWrites);assertEquals(1,h.acks);
            assertEquals(before.get("runtime"),h.backend.values.get("runtime"));assertEquals(before.get("manifest"),h.backend.values.get("manifest"));
            assertEquals(before.get("revision"),h.backend.values.get("revision"));assertNull(h.backend.values.get(InstallationStore.SNAPSHOT_KEY));
            assertEquals(revision,h.store.read().acknowledgedRevision());assertEquals(M4PhaseAFixtures.UNICODE,h.runtime.loaded.comments.get(0).text);
            assertArrayEquals(before.get("runtime").getBytes(StandardCharsets.UTF_8),h.store.read().snapshot().canonical().artifact("runtime"));
        }
    }
    /** A rejected ACK leaves even the historical confirmed value and marker unchanged for a later retry. */
    @Test public void historicalRejectedAckRetainsEveryOriginalKey() throws Exception {
        Map<String,String> values=M4PhaseFFixtures.historical(manifested),before=new HashMap<>(values);
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(values,M4PhaseFFixtures.envelope(manifested,manifested?13:14))) {
            h.ackStatus=500;M4PhaseFFixtures.refused(h);assertEquals(before,h.backend.values);
            assertEquals(0,h.backend.candidateWrites+h.backend.ackWrites);assertEquals(1,h.acks);
        }
    }
    /** A newer installation publishes one generic authority while physically preserving the old residue. */
    @Test public void newerOverHistoricalStateCommitsGenericAuthorityAndKeepsResidue() throws Exception {
        Map<String,String> values=M4PhaseFFixtures.historical(manifested),before=new HashMap<>(values);
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(values,M4PhaseFFixtures.envelope(!manifested,15))) {
            h.onAck=()->assertEquals(13,h.store.read().acknowledgedRevision());h.fetch();
            assertEquals(1,h.backend.candidateWrites);assertNotNull(h.backend.values.get(InstallationStore.SNAPSHOT_KEY));
            for(String key:new String[]{"revision","runtime","manifest"})assertEquals(before.get(key),h.backend.values.get(key));
            assertEquals(15,h.store.read().snapshot().revision());assertEquals(15,h.store.read().acknowledgedRevision());
            assertEquals(!manifested,h.runtime.controller.hasActiveManifest());
            h.onAck=()->{};h.fetch();assertEquals(1,h.backend.candidateWrites);assertEquals(2,h.acks);assertTrue(h.getPath.endsWith("?afterRevision=15"));
        }
    }
}
