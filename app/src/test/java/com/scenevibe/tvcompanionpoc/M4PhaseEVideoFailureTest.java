package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static com.scenevibe.tvcompanionpoc.M4PhaseEVideoFixtures.*;
import static org.junit.Assert.*;

/** Count real-Video post-commit false/exception faults individually without a fabricated rollback policy. */
@RunWith(Parameterized.class)
public final class M4PhaseEVideoFailureTest {
    private final boolean manifested,throwing;
    private final String stage;
    /** Retain one fixed handler-profile/stage fault, without any transport or content diagnostic. */
    public M4PhaseEVideoFailureTest(boolean manifested,String stage,boolean throwing) {
        this.manifested=manifested;this.stage=stage;this.throwing=throwing;
    }
    /** Inject every eligible-owner activation refusal and exception for both real registered handlers. */
    @Parameterized.Parameters(name="manifested={0}, stage={1}, throwing={2}") public static Collection<Object[]> cases() {
        List<Object[]> rows=new ArrayList<>();
        for (boolean manifested:new boolean[]{false,true})
            for (String stage:manifested?new String[]{"retire-legacy","retire-manifested","load","manifest","select"}
                    :new String[]{"retire-manifested","retire-legacy","load","select"})
                for (boolean throwing:new boolean[]{false,true}) rows.add(new Object[]{manifested,stage,throwing});
        return rows;
    }
    /** Keep the newer snapshot, exact old ACK and at most one owner; never invent another write to recover ARM. */
    @Test public void failedArmLeavesNewRevisionDurableAndPreviousAcknowledgementIntact() throws Exception {
        Harness test=new Harness();assertEquals(InstallationStatus.ARMED,test.install(!manifested,13));
        test.ports.due();test.confirmPrior(13);test.ports.failAt=stage;test.ports.throwing=throwing;
        assertEquals(InstallationStatus.ARM_FAILED,test.install(manifested,14));
        exact(request(manifested,14),test.store.read().snapshot());assertEquals(13,test.store.read().acknowledgedRevision());
        assertEquals(1,test.backend.writes);assertEquals(0,test.backend.ackWrites+test.backend.clears);
        assertFalse(test.ports.legacyVisible);assertFalse(test.ports.manifestVisible);assertEquals(0,test.ports.activeRevision);
        assertEquals(1,test.ports.maxVisible);assertTrue(test.backend.trace.contains("abort"));
    }
}
