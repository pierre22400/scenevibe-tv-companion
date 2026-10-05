package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Every actual service-port partial refusal/exception leaves startup pending and permits fresh recreation. */
@RunWith(Parameterized.class)
public final class M4PhaseGArmFailureTest {
    private final boolean manifested,generic,throwing;
    private final String stage;
    /** Inject only a runtime boundary failure; handlers, installer and store remain real. */
    public M4PhaseGArmFailureTest(boolean manifested, boolean generic, String stage, boolean throwing) {
        this.manifested=manifested;this.generic=generic;this.stage=stage;this.throwing=throwing;
    }
    /** Both representations exercise every applicable activation operation with false and exception results. */
    @Parameterized.Parameters(name="manifested={0},generic={1},stage={2},throw={3}") public static Collection<Object[]> faults() {
        Collection<Object[]> values=new ArrayList<>();
        for(boolean manifested:new boolean[]{true,false})for(boolean generic:new boolean[]{true,false})
            for(String stage:new String[]{"retire-legacy","retire-manifested","load","manifest","selected"}) {
                if(!manifested&&"manifest".equals(stage))continue;
                for(boolean throwing:new boolean[]{false,true})values.add(new Object[]{manifested,generic,stage,throwing});
            }
        return values;
    }
    /** Failed startup performs only bounded runtime abort; recreated cores restore the exact pending package. */
    @Test public void partialArmFailureRetainsDurableStateAndFreshStartupCanRecover() throws Exception {
        Map<String,String> values=M4PhaseGFixtures.durable(manifested,generic),prior=new HashMap<>(values);
        M4PhaseGFixtures.Startup first=new M4PhaseGFixtures.Startup(values);
        long revision=first.store.read().snapshot().revision(),ack=first.store.read().acknowledgedRevision();
        first.runtime.failAt=stage;first.runtime.throwing=throwing;
        assertEquals(InstallationStatus.ARM_FAILED,first.restore());first.disarmed();first.unchanged(prior);
        assertTrue(first.backend.trace.contains("abort"));assertEquals(ack,first.store.read().acknowledgedRevision());
        M4PhaseGFixtures.Startup second=new M4PhaseGFixtures.Startup(values);
        assertEquals(InstallationStatus.ARMED,second.restore());assertEquals(revision,second.runtime.active);
        assertEquals(ack,second.store.read().acknowledgedRevision());assertEquals(0,second.runtime.shows);
        assertTrue(second.runtime.maxVisible<=1);second.unchanged(prior);
    }
}
