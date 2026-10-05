package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.ExecutionRequirements;
import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationHandler;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.PreparedInstallation;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Stress owner-thread activation, prepared identity and every partial failure without Android or ACK. */
public final class M4PhaseDArmTest {
    /** Deterministic ports deliberately make activation visible to expose any dual-owner ordering error. */
    private static final class Ports implements VideoInstallationRuntimePorts {
        final List<String> trace=new ArrayList<>();
        boolean owner=true,legacyVisible,manifestVisible,throwFailure,throwOwner,throwCleanup;
        String failAt;
        long selectedRevision=9,manifestRevision;
        int maxVisible;
        ScheduledTrack loaded;
        OverlayManifest armedManifest;

        /** Start with exactly one prior owner, never a fabricated already-invalid dual state. */
        Ports(boolean manifested) {manifestVisible=manifested;legacyVisible=!manifested;observe();}
        /** Record only bounded stage names and inspect the one-owner invariant at each mutation. */
        private boolean step(String name,Runnable mutation) {
            trace.add(name);mutation.run();observe();
            if (name.equals(failAt)) {
                if (throwFailure) throw new IllegalStateException("Injected activation failure");
                return false;
            }
            return true;
        }
        /** A second visual owner is forbidden even transiently during a failing replacement. */
        private void observe() {
            int visible=(legacyVisible?1:0)+(manifestVisible?1:0);maxVisible=Math.max(maxVisible,visible);
            assertTrue("one visual owner throughout activation",visible<=1);
        }
        /** Thread checks are read-only and can fail without granting mutation authority. */
        @Override public boolean isOwnerThread() {
            trace.add("owner");if (throwOwner) throw new IllegalStateException("Injected owner failure");return owner;
        }
        /** Retirement immediately removes the prior legacy visual. */
        @Override public boolean retireLegacyVisualOwner() {return step("retire-legacy",()->legacyVisible=false);}
        /** Retirement immediately removes prior manifested visual and controller ownership. */
        @Override public boolean retireManifestedVisualOwner() {
            return step("retire-manifested",()->{manifestVisible=false;armedManifest=null;manifestRevision=0;});
        }
        /** Retain the exact prepared object to detect any reparse or independent interpretation. */
        @Override public boolean loadPreparedTrack(ScheduledTrack track) {return step("load",()->loaded=track);}
        /** Stress a partial activation that has become visible before returning a failure. */
        @Override public boolean armPreparedManifest(long revision,OverlayManifest manifest) {
            return step("manifest",()->{manifestRevision=revision;armedManifest=manifest;manifestVisible=true;});
        }
        /** Only this final operation selects the revision and legacy owner. */
        @Override public boolean selectActiveRevision(long revision,boolean manifested) {
            return step("select",()->{selectedRevision=revision;if (!manifested) legacyVisible=true;});
        }
        /** Cleanup can itself throw; the handler must still expose only ARM_FAILED, never ACK or raw errors. */
        @Override public void abortActivation() {
            trace.add("abort");if (throwCleanup) throw new IllegalStateException("Injected cleanup failure");
            legacyVisible=false;manifestVisible=false;selectedRevision=0;loaded=null;armedManifest=null;manifestRevision=0;observe();
        }
    }

    /** Build only the real handler's validated memory state from frozen canonical artifacts. */
    private static PreparedInstallation prepared(boolean manifested) throws Exception {
        return (manifested?VideoInstallationHandlers.manifested():VideoInstallationHandlers.legacy())
                .prepare(M4PhaseDHandlerFixtures.request(manifested),TvCapabilities.current());
    }
    /** Manifested ARM retires the opposite owner before loading/arming/selecting exact prepared values. */
    @Test public void manifestedArmOrdersRetirementAndUsesPreparedObjects() throws Exception {
        PreparedInstallation prepared=prepared(true);VideoPreparedState state=(VideoPreparedState)prepared.preparedState();Ports ports=new Ports(false);
        assertEquals(InstallationStatus.ARMED,VideoInstallationHandlers.manifested().arm(prepared,ports));
        assertEquals(Arrays.asList("owner","retire-legacy","retire-manifested","load","manifest","select"),ports.trace);
        assertSame(state.track,ports.loaded);assertSame(state.manifest,ports.armedManifest);
        assertEquals(prepared.revision(),ports.manifestRevision);assertEquals(prepared.revision(),ports.selectedRevision);
        assertFalse(ports.legacyVisible);assertTrue(ports.manifestVisible);assertEquals(1,ports.maxVisible);
    }
    /** Legacy takeover retires/disarms manifested ownership before scheduler acceptance and final selection. */
    @Test public void legacyArmRetiresManifestedOwnershipBeforeSelectingLegacy() throws Exception {
        PreparedInstallation prepared=prepared(false);Ports ports=new Ports(true);
        assertEquals(InstallationStatus.ARMED,VideoInstallationHandlers.legacy().arm(prepared,ports));
        assertEquals(Arrays.asList("owner","retire-manifested","retire-legacy","load","select"),ports.trace);
        assertSame(((VideoPreparedState)prepared.preparedState()).track,ports.loaded);assertNull(ports.armedManifest);
        assertEquals(prepared.revision(),ports.selectedRevision);assertFalse(ports.manifestVisible);assertTrue(ports.legacyVisible);
    }
    /** A foreign generic port cannot accidentally gain Video mutation access. */
    @Test public void wrongRuntimePortsTypeFailsClosed() throws Exception {
        assertEquals(InstallationStatus.ARM_FAILED,VideoInstallationHandlers.manifested().arm(prepared(true),new InstallationHandler.RuntimePorts(){}));
        assertEquals(InstallationStatus.ARM_FAILED,VideoInstallationHandlers.legacy().arm(prepared(false),new InstallationHandler.RuntimePorts(){}));
    }
    /** Different handler/shape states are refused before even checking the owner thread. */
    @Test public void oppositeHandlerPreparedPairingIsRejectedBeforeMutation() throws Exception {
        Ports ports=new Ports(true);
        assertEquals(InstallationStatus.ARM_FAILED,VideoInstallationHandlers.manifested().arm(prepared(false),ports));
        assertEquals(InstallationStatus.ARM_FAILED,VideoInstallationHandlers.legacy().arm(prepared(true),ports));assertTrue(ports.trace.isEmpty());
    }
    /** Existing scalar-only Phase B construction remains valid but cannot masquerade as executable state. */
    @Test public void originalConstructorIsInertAndNotArmable() throws Exception {
        PreparedInstallation trusted=prepared(true);
        PreparedInstallation inert=new PreparedInstallation(trusted.canonical(),trusted.handlerId(),Collections.emptyMap(),trusted.requirements(),TvCapabilities.current());
        assertNull(inert.preparedState());assertEquals(InstallationStatus.PREPARED,inert.status());
        assertEquals(InstallationStatus.ARM_FAILED,VideoInstallationHandlers.manifested().arm(inert,new Ports(false)));
        rejectsEncoding(VideoInstallationHandlers.manifested(),inert);
    }
    /** A marker of the wrong concrete type is never interpreted as parsed Video state. */
    @Test public void wrongPreparedStateTypeFailsClosed() throws Exception {
        PreparedInstallation trusted=prepared(false);
        PreparedInstallation forged=new PreparedInstallation(trusted.canonical(),trusted.handlerId(),Collections.emptyMap(),trusted.requirements(),TvCapabilities.current(),new InstallationHandler.PreparedState(){});
        Ports ports=new Ports(true);assertEquals(InstallationStatus.ARM_FAILED,VideoInstallationHandlers.legacy().arm(forged,ports));assertTrue(ports.trace.isEmpty());
        rejectsEncoding(VideoInstallationHandlers.legacy(),forged);
    }
    /** Borrowing parsed state for another canonical revision is rejected without reparsing. */
    @Test public void reboundCanonicalCandidateCannotReuseTrustedState() throws Exception {
        PreparedInstallation trusted=prepared(true);
        InstallRequest other=new InstallRequest(trusted.revision()+1,trusted.codecId(),trusted.canonical().artifacts());
        PreparedInstallation rebound=new PreparedInstallation(other,trusted.handlerId(),Collections.emptyMap(),trusted.requirements(),TvCapabilities.current(),trusted.preparedState());
        Ports ports=new Ports(false);assertEquals(InstallationStatus.ARM_FAILED,VideoInstallationHandlers.manifested().arm(rebound,ports));assertTrue(ports.trace.isEmpty());
        rejectsEncoding(VideoInstallationHandlers.manifested(),rebound);
    }
    /** Independently supplied profile metadata cannot replace the handler-derived bound requirements. */
    @Test public void reboundRequirementsCannotReuseTrustedState() throws Exception {
        PreparedInstallation trusted=prepared(false);
        ExecutionRequirements other=new ExecutionRequirements(TvCapabilities.LEGACY_CONTRACT,ExecutionRequirements.ClockMode.MEDIA,ExecutionRequirements.PauseBehavior.FREEZE,1,false,false);
        PreparedInstallation rebound=new PreparedInstallation(trusted.canonical(),trusted.handlerId(),Collections.emptyMap(),other,TvCapabilities.current(),trusted.preparedState());
        Ports ports=new Ports(true);assertEquals(InstallationStatus.ARM_FAILED,VideoInstallationHandlers.legacy().arm(rebound,ports));assertTrue(ports.trace.isEmpty());
        rejectsEncoding(VideoInstallationHandlers.legacy(),rebound);
    }
    /** Non-owner-thread calls cannot retire windows, load schedulers or invoke cleanup. */
    @Test public void nonOwnerThreadPerformsNoMutation() throws Exception {
        Ports ports=new Ports(false);ports.owner=false;
        assertEquals(InstallationStatus.ARM_FAILED,VideoInstallationHandlers.manifested().arm(prepared(true),ports));
        assertEquals(Collections.singletonList("owner"),ports.trace);assertTrue(ports.legacyVisible);assertEquals(9,ports.selectedRevision);
    }
    /** Failed eligibility checks do not authorize cleanup on an unknown thread. */
    @Test public void throwingOwnerCheckPerformsNoMutation() throws Exception {
        Ports ports=new Ports(true);ports.throwOwner=true;
        assertEquals(InstallationStatus.ARM_FAILED,VideoInstallationHandlers.legacy().arm(prepared(false),ports));
        assertEquals(Collections.singletonList("owner"),ports.trace);assertTrue(ports.manifestVisible);
    }
    /** Every false-return stage remains unarmed and cannot leave two visible owners. */
    @Test public void everyManifestedActivationFailureIsUnarmedAndCleaned() throws Exception {
        for (String stage:new String[]{"retire-legacy","retire-manifested","load","manifest","select"}) {
            Ports ports=new Ports(false);ports.failAt=stage;
            assertEquals(InstallationStatus.ARM_FAILED,VideoInstallationHandlers.manifested().arm(prepared(true),ports));
            assertEquals("abort",ports.trace.get(ports.trace.size()-1));assertEquals(0,ports.selectedRevision);
            assertFalse(ports.legacyVisible);assertFalse(ports.manifestVisible);assertEquals(1,ports.maxVisible);
            if (!stage.equals("select")) assertFalse(ports.trace.contains("select"));
        }
    }
    /** Exceptions after partial manifested activation are bounded failures with no active revision claim. */
    @Test public void everyThrowingManifestedActivationFailureIsBounded() throws Exception {
        for (String stage:new String[]{"retire-legacy","retire-manifested","load","manifest","select"}) {
            Ports ports=new Ports(true);ports.failAt=stage;ports.throwFailure=true;
            assertEquals(InstallationStatus.ARM_FAILED,VideoInstallationHandlers.manifested().arm(prepared(true),ports));
            assertEquals(0,ports.selectedRevision);assertTrue(ports.maxVisible<=1);assertEquals("abort",ports.trace.get(ports.trace.size()-1));
        }
    }
    /** Legacy failures after retiring a visible scene never report successful takeover. */
    @Test public void everyLegacyActivationFailureIsUnarmedAndCleaned() throws Exception {
        for (String stage:new String[]{"retire-manifested","retire-legacy","load","select"})
            for (boolean throwing:new boolean[]{false,true}) {
                Ports ports=new Ports(true);ports.failAt=stage;ports.throwFailure=throwing;
                assertEquals(InstallationStatus.ARM_FAILED,VideoInstallationHandlers.legacy().arm(prepared(false),ports));
                assertEquals(0,ports.selectedRevision);assertFalse(ports.manifestVisible);assertFalse(ports.legacyVisible);assertTrue(ports.maxVisible<=1);
                if (!stage.equals("select")) assertFalse(ports.trace.contains("select"));
            }
    }
    /** Failed cleanup remains ARM_FAILED and the prior synchronous retirement still prevents dual ownership. */
    @Test public void cleanupExceptionCannotReportArmedOrCreateASecondOwner() throws Exception {
        Ports ports=new Ports(false);ports.failAt="manifest";ports.throwCleanup=true;
        assertEquals(InstallationStatus.ARM_FAILED,VideoInstallationHandlers.manifested().arm(prepared(true),ports));
        assertFalse(ports.legacyVisible);assertTrue(ports.maxVisible<=1);assertEquals(9,ports.selectedRevision);
    }
    /** Null state/ports are rejected rather than becoming a partially executable installation. */
    @Test public void nullStateAndPortsFailClosed() throws Exception {
        assertEquals(InstallationStatus.ARM_FAILED,VideoInstallationHandlers.manifested().arm(null,new Ports(false)));
        assertEquals(InstallationStatus.ARM_FAILED,VideoInstallationHandlers.legacy().arm(prepared(false),null));
    }
    /** A foreign trusted candidate also cannot be persisted under the opposite handler identity. */
    @Test public void encodeRejectsOppositeHandlerPreparedState() throws Exception {
        rejectsEncoding(VideoInstallationHandlers.manifested(),prepared(false));rejectsEncoding(VideoInstallationHandlers.legacy(),prepared(true));
    }
    /** Cache encoding errors expose only the closed invalid-package label and no cause/content. */
    private static void rejectsEncoding(InstallationHandler handler,PreparedInstallation prepared) {
        try {handler.encodeForCache(prepared);fail("Foreign prepared state encoded");}
        catch (IllegalArgumentException invalid) {assertEquals(InstallationStatus.INVALID_PACKAGE.name(),invalid.getMessage());assertNull(invalid.getCause());}
    }
}
