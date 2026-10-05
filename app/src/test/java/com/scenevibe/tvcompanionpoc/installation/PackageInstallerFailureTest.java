package com.scenevibe.tvcompanionpoc.installation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static com.scenevibe.tvcompanionpoc.installation.M4PhaseEInstallerFixtures.*;
import static org.junit.Assert.*;

/**
 * Count each deterministic handler-contract failure separately in the executed XML.
 * Pre-commit failures preserve every prior byte; post-commit failures keep the newer
 * revision and old confirmation without another commit, rollback or success claim.
 */
@RunWith(Parameterized.class)
public final class PackageInstallerFailureTest {
    private final String fault;
    private final InstallationStatus validation,armed,expected;
    private final boolean postCommit;

    /** Retain only one fixed fault descriptor and its closed expected lifecycle outcome. */
    public PackageInstallerFailureTest(String fault,InstallationStatus validation,InstallationStatus armed,
            boolean postCommit,InstallationStatus expected) {
        this.fault=fault;this.validation=validation;this.armed=armed;this.postCommit=postCommit;this.expected=expected;
    }

    /** Enumerate null/exception/foreign metadata and every premature stage/success result. */
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> cases() {
        List<Object[]> rows=new ArrayList<>();
        for (InstallationStatus status:InstallationStatus.values()) if (status!=InstallationStatus.VALIDATED) {
            rows.add(new Object[]{"validate-"+status,status,InstallationStatus.ARMED,false,
                    status==InstallationStatus.PREPARED||status==InstallationStatus.ARMED?InstallationStatus.INVALID_PACKAGE:status});
        }
        rows.add(new Object[]{"validate-null",null,InstallationStatus.ARMED,false,InstallationStatus.INVALID_PACKAGE});
        for (String fault:Arrays.asList("validate-throw","prepare-throw","prepare-null","prepare-revision",
                "prepare-codec","prepare-handler","prepare-capability","encode-throw","encode-null",
                "encode-revision","encode-codec","encode-shape"))
            rows.add(new Object[]{fault,InstallationStatus.VALIDATED,InstallationStatus.ARMED,false,InstallationStatus.INVALID_PACKAGE});
        for (String fault:Arrays.asList("restore-throw","restore-null","restore-revision","restore-codec",
                "restore-handler","restore-capability","restore-bytes","arm-throw"))
            rows.add(new Object[]{fault,InstallationStatus.VALIDATED,InstallationStatus.ARMED,true,InstallationStatus.ARM_FAILED});
        for (InstallationStatus status:InstallationStatus.values()) if (status!=InstallationStatus.ARMED)
            rows.add(new Object[]{"arm-"+status,InstallationStatus.VALIDATED,status,true,InstallationStatus.ARM_FAILED});
        rows.add(new Object[]{"arm-null",InstallationStatus.VALIDATED,null,true,InstallationStatus.ARM_FAILED});
        return rows;
    }

    /** Inject one failure and verify exact stage prefix, no premature ARM and unchanged confirmation. */
    @Test public void failureIsClosedAtItsStageWithoutConfirmationOrRollback() {
        Harness test=new Harness();test.seed(1,1);Map<String,String> before=new HashMap<>(test.memory.values);
        test.handler.fault=fault;test.handler.validation=validation;test.handler.armed=armed;
        assertEquals(expected,test.install(2));assertEquals(0,test.memory.ackWrites+test.memory.clearWrites);
        List<String> prefix=new ArrayList<>(Arrays.asList("read","validate"));
        if (!fault.startsWith("validate-")) prefix.add("prepare");
        if (!fault.startsWith("validate-")&&!fault.startsWith("prepare-")) prefix.add("encode");
        if (postCommit) {
            prefix.addAll(Arrays.asList("commit","read","restore"));
            if (fault.startsWith("arm-")) prefix.add("arm");
        }
        assertEquals(prefix,test.trace);assertEquals(postCommit?1:0,test.memory.commits);
        assertEquals(fault.startsWith("arm-")?1:0,test.handler.arms);
        InstallationStore.ReadResult durable=test.store.read();
        assertEquals(postCommit?2:1,durable.snapshot().revision());assertEquals(1,durable.acknowledgedRevision());
        if (!postCommit) assertTrue("prior complete file untouched",before.equals(test.memory.values));
        else assertTrue("new committed bytes retained",Arrays.equals(request(2,TvCapabilities.CODEC_TRACK,"incoming").artifact("body"),durable.snapshot().canonical().artifact("body")));
    }
}
