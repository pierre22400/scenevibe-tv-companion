package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Exercise the real HTTP ACK decision for every non-ARMED status, null and an ordinary install exception. */
@RunWith(Parameterized.class)
public final class M4PhaseFNonArmedTest {
    private final InstallationStatus status;
    private final boolean throwing;
    /** Select a bounded generic outcome or one ordinary owner operation failure. */
    public M4PhaseFNonArmedTest(InstallationStatus status,boolean throwing) {this.status=status;this.throwing=throwing;}
    /** Neither pure-stage success nor a null/exception may authorize an ACK HTTP request. */
    @Parameterized.Parameters public static Collection<Object[]> outcomes() {
        Collection<Object[]> values=new ArrayList<>();
        for(InstallationStatus status:InstallationStatus.values())if(status!=InstallationStatus.ARMED)values.add(new Object[]{status,false});
        values.add(new Object[]{null,false});values.add(new Object[]{null,true});return values;
    }
    /** The network layer invokes its generic operation once, awaits it, and fails closed without ACK. */
    @Test public void onlyExactArmedCanReachHttpAck() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(true,14)) {
            h.prior(true,13);h.forceOutcome=true;h.outcome=status;h.throwInstall=throwing;
            M4PhaseFFixtures.refused(h);
            assertEquals(1,h.installCalls);assertEquals(h.ownerThread,h.installThread);assertNotEquals(h.fetchThread,h.installThread);
            assertEquals(0,h.acks);assertEquals(0,h.backend.candidateWrites);assertEquals(0,h.backend.ackWrites);
            assertEquals(13,h.store.read().snapshot().revision());assertEquals(13,h.store.read().acknowledgedRevision());
        }
    }
}
