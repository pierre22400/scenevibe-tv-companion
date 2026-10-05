package com.scenevibe.tvcompanionpoc.installation;

import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static com.scenevibe.tvcompanionpoc.installation.M4PhaseEInstallerFixtures.*;
import static org.junit.Assert.*;

/**
 * A claimed successful commit grants no activation authority until exact readback is proven.
 * Each backend fault is an independently counted case. Matching metadata with changed
 * bytes is also refused, so the initial in-memory preparation can never mask disk failure.
 */
@RunWith(Parameterized.class)
public final class PackageInstallerReadbackTest {
    private final String fault;
    /** Keep only a bounded injected readback-fault id. */
    public PackageInstallerReadbackTest(String fault) {this.fault=fault;}
    /** Exercise missing/corrupt representation, every binding mismatch and exact artifact-byte identity. */
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{{"corrupt"},{"empty"},{"read-throw"},{"revision"},{"handler"},
                {"unknown-handler"},{"codec"},{"binding"},{"bytes"},{"artifact-id"}});
    }
    /** Stop after readback, without restore/ARM, ACK, clearing or another persistence attempt. */
    @Test public void claimedCommitCannotArmUntilExactDurableRepresentationIsVerified() {
        Harness test=new Harness();test.memory.readbackFault=fault;
        assertEquals(InstallationStatus.CACHE_FAILED,test.install(2));
        assertEquals(Arrays.asList("read","validate","prepare","encode","commit","read"),test.trace);
        assertEquals(1,test.memory.commits);assertEquals(0,test.memory.ackWrites+test.memory.clearWrites);
        assertEquals(0,test.handler.restores+test.handler.arms+test.other.restores+test.other.arms);
    }
}
