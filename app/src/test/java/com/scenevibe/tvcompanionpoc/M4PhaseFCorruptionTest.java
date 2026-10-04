package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** A corrupt generic/compatibility view can never become afterRevision=0 or permit network overwrite. */
@RunWith(Parameterized.class)
public final class M4PhaseFCorruptionTest {
    private final String fault;
    /** Select a durable representation or acknowledged-revision corruption. */
    public M4PhaseFCorruptionTest(String fault) {this.fault=fault;}
    /** Historical residue must not rescue a corrupt present generic marker. */
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> faults() {
        return Arrays.asList(new Object[][]{{"generic"},{"empty-marker"},{"future-ack"},{"malformed-ack"},{"orphan-ack"},{"partial-historical"}});
    }
    /** The actual client fails before GET, adapter, owner dispatch, installation or ACK. */
    @Test public void corruptStorePreventsFetchAndEveryMutation() throws Exception {
        Map<String,String> values=M4PhaseFFixtures.historical(true);
        switch(fault) {
            case "generic":values.put(InstallationStore.SNAPSHOT_KEY,"corrupt");break;
            case "empty-marker":values.put(InstallationStore.SNAPSHOT_KEY,"");break;
            case "future-ack":values.put("ackRevision","14");break;
            case "malformed-ack":values.put("ackRevision","bad");break;
            case "orphan-ack":values.clear();values.put("ackRevision","13");break;
            case "partial-historical":values.remove("runtime");break;
            default:throw new AssertionError("Unknown durable fault");
        }
        Map<String,String> prior=new HashMap<>(values);
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(values,M4PhaseFFixtures.envelope(true,14))) {
            M4PhaseFFixtures.refused(h);assertEquals(InstallationStore.ReadState.CORRUPT,h.store.read().state());
            assertEquals(prior,h.backend.values);assertEquals(0,h.gets+h.installCalls+h.acks);
            assertEquals(0,h.backend.candidateWrites+h.backend.ackWrites+h.backend.clears);
        }
    }
}
