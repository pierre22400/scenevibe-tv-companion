package com.scenevibe.tvcompanionpoc;

import java.util.ArrayList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Inject every real service-port activation refusal/exception after partial mutation through actual Cloud io. */
@RunWith(Parameterized.class)
public final class M4PhaseFRuntimeFailureTest {
    private final boolean manifested,throwing;
    private final String stage;
    /** Select one supported profile and one partial activation stage fault. */
    public M4PhaseFRuntimeFailureTest(boolean manifested,String stage,boolean throwing) {
        this.manifested=manifested;this.stage=stage;this.throwing=throwing;
    }
    /** Both profiles exercise each applicable activation stage with false and ordinary exception outcomes. */
    @Parameterized.Parameters(name="manifested={0},stage={1},throw={2}") public static Collection<Object[]> faults() {
        Collection<Object[]> values=new ArrayList<>();
        for(boolean manifested:new boolean[]{true,false})for(String stage:new String[]{"retire-legacy","retire-manifested","load","manifest","selected"}) {
            if(!manifested&&"manifest".equals(stage))continue;
            for(boolean throwing:new boolean[]{false,true})values.add(new Object[]{manifested,stage,throwing});
        }
        return values;
    }
    /** New revision stays durable, old ACK stays confirmed, runtime aborts and later same-revision retry recovers. */
    @Test public void partialRuntimeFailureNeverAcksOrRollsBackAndRedeliveryRecovers() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(manifested,14)) {
            h.prior(!manifested,13);h.onOwner(()->{h.runtime.due();return null;});h.runtime.failAt=stage;h.runtime.throwing=throwing;
            M4PhaseFFixtures.refused(h);assertEquals(14,h.store.read().snapshot().revision());assertEquals(13,h.store.read().acknowledgedRevision());
            assertEquals(1,h.backend.candidateWrites);assertEquals(0,h.acks+h.backend.ackWrites);assertEquals(0,h.runtime.active);
            assertFalse(h.runtime.legacyVisible);assertFalse(h.runtime.manifestVisible);assertEquals(1,h.runtime.maxVisible);
            h.runtime.failAt=null;h.fetch();assertEquals(1,h.backend.candidateWrites);assertEquals(1,h.acks);
            assertEquals(14,h.store.read().acknowledgedRevision());assertEquals(14,h.runtime.active);
        }
    }
}
