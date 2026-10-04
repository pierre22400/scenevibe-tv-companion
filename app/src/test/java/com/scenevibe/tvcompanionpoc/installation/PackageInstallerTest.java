package com.scenevibe.tvcompanionpoc.installation;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static com.scenevibe.tvcompanionpoc.installation.M4PhaseEInstallerFixtures.*;
import static org.junit.Assert.*;

/**
 * Exercise revision authority, exact durable restoration and confirmation isolation.
 * The store, metadata values and registry are real. Only opaque semantic preparation and
 * runtime activation are substituted; no copied installer or persistence policy is used.
 */
public final class PackageInstallerTest {
    /** Empty storage runs every stage exactly once and arms a freshly restored durable object. */
    @Test public void emptyStoreUsesExactFullOrderAndReturnsOnlyArmed() {
        Harness test=new Harness();
        assertEquals(InstallationStatus.ARMED,test.install(1));
        assertEquals(Arrays.asList("read","validate","prepare","encode","commit","read","restore","arm"),test.trace);
        assertEquals(1,test.memory.commits);assertEquals(0,test.memory.ackWrites);assertEquals(0,test.memory.clearWrites);
        assertFalse(test.memory.values.containsKey("ackRevision"));
        assertNotSame(test.handler.initial,test.handler.armInput);assertSame(test.handler.restored,test.handler.armInput);
        assertNotSame(test.handler.initial.canonical(),test.handler.restoredRequest);
        assertEquals(1,test.store.read().snapshot().revision());assertEquals(0,test.store.read().acknowledgedRevision());
    }

    /** A newer installation retains the prior server-confirmed ACK and has one durable identity. */
    @Test public void newerRevisionCommitsOnceWithoutChangingAcknowledgement() {
        Harness test=new Harness();test.seed(7,7);
        assertEquals(InstallationStatus.ARMED,test.install(8));
        assertEquals(1,test.memory.commits);assertEquals(0,test.memory.ackWrites);
        assertEquals(8,test.store.read().snapshot().revision());assertEquals(7,test.store.read().acknowledgedRevision());
        assertEquals(HANDLER,test.store.read().snapshot().handlerId());
    }

    /** Encoder-approved canonical bytes, rather than incoming/prepared caller bytes, are restored and armed. */
    @Test public void encoderCanonicalBytesAreReadBackAndRestoredBeforeArm() {
        Harness test=new Harness();test.handler.encodedOverride=request(3,TvCapabilities.CODEC_TRACK,"canonical É / e\u0301 / 😀");
        InstallRequest incoming=request(3,TvCapabilities.CODEC_TRACK,"uncanonical incoming");
        assertEquals(InstallationStatus.ARMED,test.installer.install(incoming,PORTS));
        assertTrue("encoded bytes are authoritative",Arrays.equals(test.handler.encodedOverride.artifact("body"),test.handler.restoredRequest.artifact("body")));
        assertFalse("caller bytes never armed",Arrays.equals(incoming.artifact("body"),test.handler.armInput.canonical().artifact("body")));
        assertNotSame(incoming,test.handler.restoredRequest);assertNotSame(test.handler.initial,test.handler.armInput);
        assertEquals(1,test.memory.commits);assertSame(test.handler.restored,test.handler.armInput);
    }

    /** Codec lookup cannot discover a handler for an unregistered bounded id. */
    @Test public void unknownCodecCausesNoSemanticCallWriteOrArm() {
        Harness test=new Harness();
        assertEquals(InstallationStatus.INVALID_PACKAGE,test.installer.install(request(1,"unknown.codec.v1","malformed"),PORTS));
        assertEquals(Collections.singletonList("read"),test.trace);assertEquals(0,test.memory.commits);
        assertEquals(0,test.handler.validations+test.other.validations);assertEquals(0,test.handler.arms+test.other.arms);
    }

    /** A configured but empty static registry grants no installation authority. */
    @Test public void emptyRegistryRejectsBeforePreparationOrPersistence() {
        Harness test=new Harness();PackageInstaller installer=new PackageInstaller(test.store,InstallationHandlerRegistry.empty(),CAPS);
        assertEquals(InstallationStatus.INVALID_PACKAGE,installer.install(request(1,TvCapabilities.CODEC_TRACK,"opaque"),PORTS));
        assertEquals(Collections.singletonList("read"),test.trace);assertEquals(0,test.memory.commits);
    }

    /** A failed physical commit preserves every prior durable byte and confirmed revision. */
    @Test public void commitFalseLeavesPreviousSnapshotAndAckByteIdentical() {
        Harness test=new Harness();test.seed(1,1);Map<String,String> before=new HashMap<>(test.memory.values);test.memory.writable=false;
        assertEquals(InstallationStatus.CACHE_FAILED,test.install(2));
        assertTrue("previous complete file retained",before.equals(test.memory.values));
        assertEquals(1,test.memory.commits);assertEquals(0,test.handler.restores+test.handler.arms);assertEquals(0,test.memory.ackWrites);
    }

    /** Ordinary backend exceptions cannot escape or arm an uncommitted caller copy. */
    @Test public void commitExceptionLeavesPreviousSnapshotAndAckByteIdentical() {
        Harness test=new Harness();test.seed(1,1);Map<String,String> before=new HashMap<>(test.memory.values);test.memory.throwCommit=true;
        assertEquals(InstallationStatus.CACHE_FAILED,test.install(2));
        assertTrue("previous durable file retained",before.equals(test.memory.values));
        assertEquals(1,test.memory.commits);assertEquals(0,test.handler.arms);assertEquals(0,test.memory.ackWrites);
    }

    /** The real store's INVALID_SNAPSHOT guard is mapped to INVALID_PACKAGE without an attempted batch. */
    @Test public void invalidSnapshotCommitOutcomeIsNotStorageOrArmSuccess() {
        Harness test=new Harness();test.seed(1,1);
        // Deterministic concurrent-confirmation fault: the store itself refuses the candidate.
        test.handler.beforeEncode=()->test.memory.values.put("ackRevision","3");
        assertEquals(InstallationStatus.INVALID_PACKAGE,test.install(2));
        assertEquals(0,test.memory.commits);assertEquals(0,test.handler.restores+test.handler.arms);
        assertEquals("3",test.memory.values.get("ackRevision"));assertEquals(0,test.memory.ackWrites);
    }

    /** Stale malformed artifacts and an unknown incoming codec are never semantically inspected. */
    @Test public void staleRevisionReturnsBeforeLookupValidationOrPayloadParsing() {
        Harness test=new Harness();test.seed(9,9);Map<String,String> before=new HashMap<>(test.memory.values);
        InstallRequest malformed=new InstallRequest(8,"unknown.codec.v1",Collections.singletonMap("bad",new byte[]{(byte)255}));
        assertEquals(InstallationStatus.STALE,test.installer.install(malformed,PORTS));
        assertEquals(Collections.singletonList("read"),test.trace);assertTrue(before.equals(test.memory.values));
        assertEquals(0,test.handler.validations+test.other.validations+test.memory.commits+test.handler.arms+test.other.arms);
    }

    /** Even an unknown durable handler is irrelevant to an already-stale revision decision. */
    @Test public void staleRevisionDoesNotNeedDurableHandlerResolution() {
        Harness test=new Harness();test.store.commit(new InstallationSnapshot(request(9,TvCapabilities.CODEC_TRACK,"durable"),"unknown.handler.v1"));
        test.memory.commits=0;test.trace.clear();
        assertEquals(InstallationStatus.STALE,test.install(8));assertEquals(Collections.singletonList("read"),test.trace);
        assertEquals(0,test.memory.commits);
    }

    /** Matching revision ignores entirely different incoming content and performs zero persistence. */
    @Test public void sameRevisionRestoresExactDurableBytesWithoutRevalidationOrCommit() {
        Harness test=new Harness();test.seed(7,7);Map<String,String> before=new HashMap<>(test.memory.values);
        assertEquals(InstallationStatus.ARMED,test.install(7));
        assertEquals(Arrays.asList("read","restore","arm"),test.trace);assertEquals(0,test.memory.commits+test.memory.ackWrites);
        assertTrue("no rewrite",before.equals(test.memory.values));
        assertTrue("durable bytes restored",Arrays.equals(request(7,TvCapabilities.CODEC_TRACK,"durable").artifact("body"),test.handler.restoredRequest.artifact("body")));
        assertSame(test.handler.restored,test.handler.armInput);assertEquals(7,test.store.read().acknowledgedRevision());
    }

    /** Same revision selects the durable handler even when the other registered codec arrives. */
    @Test public void sameRevisionIncomingOtherCodecCannotRebindDurableShape() {
        Harness test=new Harness();test.seed(7,7);
        assertEquals(InstallationStatus.ARMED,test.installer.install(request(7,TvCapabilities.CODEC_TRACK_OVERLAY,"different"),PORTS));
        assertEquals(1,test.handler.restores);assertEquals(1,test.handler.arms);
        assertEquals(0,test.other.validations+test.other.preparations+test.other.restores+test.other.arms);
        assertEquals(0,test.memory.commits);assertEquals(HANDLER,test.store.read().snapshot().handlerId());
    }

    /** Unknown incoming codec and invalid inert bytes have no authority on a matching revision. */
    @Test public void sameRevisionUnknownIncomingCodecIsIgnored() {
        Harness test=new Harness();test.seed(7,7);
        assertEquals(InstallationStatus.ARMED,test.installer.install(request(7,"unknown.codec.v1","not JSON"),PORTS));
        assertEquals(Arrays.asList("read","restore","arm"),test.trace);assertEquals(0,test.memory.commits+test.memory.ackWrites);
    }

    /** Restore failure on redelivery cannot rewrite the known durable snapshot or ACK. */
    @Test public void sameRevisionRestoreExceptionPreservesEntireStoreWithoutArm() {
        Harness test=new Harness();test.seed(7,7);Map<String,String> before=new HashMap<>(test.memory.values);test.handler.fault="restore-throw";
        assertEquals(InstallationStatus.ARM_FAILED,test.install(7));assertEquals(0,test.memory.commits+test.handler.arms);
        assertTrue("redelivery file untouched",before.equals(test.memory.values));assertEquals(0,test.memory.ackWrites);
    }

    /** Runtime failure on redelivery leaves durable authority and prior confirmed ACK unchanged. */
    @Test public void sameRevisionArmFailurePerformsNoWrite() {
        Harness test=new Harness();test.seed(7,7);Map<String,String> before=new HashMap<>(test.memory.values);test.handler.armed=InstallationStatus.ARM_FAILED;
        assertEquals(InstallationStatus.ARM_FAILED,test.install(7));assertEquals(0,test.memory.commits+test.memory.ackWrites);
        assertTrue("redelivery file untouched",before.equals(test.memory.values));
    }

    /** A handler id absent from the build cannot trigger codec guessing or a fallback activation. */
    @Test public void sameRevisionUnknownDurableHandlerFailsClosedWithoutFallback() {
        Harness test=new Harness();test.store.commit(new InstallationSnapshot(request(5,TvCapabilities.CODEC_TRACK,"bytes"),"missing.handler.v1"));
        Map<String,String> before=new HashMap<>(test.memory.values);test.memory.commits=0;test.trace.clear();
        assertEquals(InstallationStatus.CACHE_FAILED,test.install(5));assertEquals(Collections.singletonList("read"),test.trace);
        assertTrue(before.equals(test.memory.values));assertEquals(0,test.memory.commits);
    }

    /** A stored handler/codec cross-binding cannot be repaired from incoming metadata. */
    @Test public void sameRevisionMismatchedDurableBindingFailsClosed() {
        Harness test=new Harness();test.store.commit(new InstallationSnapshot(request(5,TvCapabilities.CODEC_TRACK,"bytes"),OTHER_HANDLER));
        test.memory.commits=0;test.trace.clear();
        assertEquals(InstallationStatus.CACHE_FAILED,test.install(5));assertEquals(Collections.singletonList("read"),test.trace);
        assertEquals(0,test.memory.commits);
    }

    /** Corrupt durable bytes forbid all handler work, clearing and writes even for a newer valid request. */
    @Test public void corruptStoreIsNeverClearedRewrittenValidatedOrArmed() {
        Harness test=new Harness();test.memory.values.put(InstallationStore.SNAPSHOT_KEY,"broken");
        Map<String,String> before=new HashMap<>(test.memory.values);
        assertEquals(InstallationStatus.CACHE_FAILED,test.install(20));assertEquals(Collections.singletonList("read"),test.trace);
        assertTrue(before.equals(test.memory.values));assertEquals(0,test.memory.commits+test.memory.ackWrites+test.memory.clearWrites);
    }

    /** A positive orphan ACK remains corruption at the real store boundary. */
    @Test public void orphanAcknowledgementBlocksIncomingPackageWithoutMutation() {
        Harness test=new Harness();test.memory.values.put("ackRevision","9");
        assertEquals(InstallationStatus.CACHE_FAILED,test.install(20));assertEquals(Collections.singletonList("read"),test.trace);
        assertEquals(0,test.memory.commits);assertEquals("9",test.memory.values.get("ackRevision"));
    }

    /** A future confirmation cannot be hidden by a newer incoming revision or overwritten into validity. */
    @Test public void futureAcknowledgementBlocksIncomingPackageWithoutMutation() {
        Harness test=new Harness();test.seed(7,7);test.memory.values.put("ackRevision","8");
        assertEquals(InstallationStatus.CACHE_FAILED,test.install(20));assertEquals(Collections.singletonList("read"),test.trace);
        assertEquals(0,test.memory.commits);assertEquals("8",test.memory.values.get("ackRevision"));
    }

    /** Typed/backend read failure produces storage failure, never an empty-store overwrite. */
    @Test public void readExceptionFailsClosedWithoutHandlerOrWrite() {
        Harness test=new Harness();test.memory.throwRead=true;
        assertEquals(InstallationStatus.CACHE_FAILED,test.install(20));assertEquals(Collections.singletonList("read"),test.trace);
        assertEquals(0,test.memory.commits);
    }

    /** Process recreation can recover the newer pending revision without rollback or a second commit. */
    @Test public void failedArmCanRecoverFromExactDurableRedeliveryAfterRecreation() {
        Harness test=new Harness();test.seed(1,1);test.handler.fault="arm-throw";
        assertEquals(InstallationStatus.ARM_FAILED,test.install(2));Map<String,String> pending=new HashMap<>(test.memory.values);
        assertEquals(2,test.store.read().snapshot().revision());assertEquals(1,test.store.read().acknowledgedRevision());
        test.handler.fault=null;test.trace.clear();test.memory.commits=0;
        PackageInstaller recreated=new PackageInstaller(new InstallationStore(test.memory),test.registry,CAPS);
        assertEquals(InstallationStatus.ARMED,recreated.install(request(2,"unknown.codec.v1","different incoming"),PORTS));
        assertTrue("pending canonical bytes reused",pending.equals(test.memory.values));assertEquals(0,test.memory.commits+test.memory.ackWrites);
        assertEquals(Arrays.asList("read","restore","arm"),test.trace);assertEquals(1,test.store.read().acknowledgedRevision());
    }

    /** Registry identity and all handler counters stay unchanged after repeated lookup. */
    @Test public void staticLookupConstructsAndInvokesNothing() {
        Harness test=new Harness();
        for (int i=0;i<100;i++) {
            assertSame(test.handler,test.registry.findCodec(TvCapabilities.CODEC_TRACK).handler());
            assertSame(test.registry.findHandler(HANDLER),test.registry.findCodec(TvCapabilities.CODEC_TRACK));
        }
        assertEquals(0,test.handler.validations+test.handler.preparations+test.handler.encodings+test.handler.restores+test.handler.arms);
        assertTrue(test.trace.isEmpty());assertEquals(0,test.memory.commits);
    }

    /** Missing constructor dependencies are rejected with a fixed content-free label. */
    @Test public void missingDependenciesCannotCreatePartialInstaller() {
        Harness test=new Harness();
        assertThrows(IllegalArgumentException.class,()->new PackageInstaller(null,test.registry,CAPS));
        assertThrows(IllegalArgumentException.class,()->new PackageInstaller(test.store,null,CAPS));
        assertThrows(IllegalArgumentException.class,()->new PackageInstaller(test.store,test.registry,null));
    }

    /** Null input cannot cause semantic work or writes in a clean file. */
    @Test public void nullRequestIsBoundedInvalidPackageAfterInitialRead() {
        Harness test=new Harness();assertEquals(InstallationStatus.INVALID_PACKAGE,test.installer.install(null,PORTS));
        assertEquals(Collections.singletonList("read"),test.trace);assertEquals(0,test.memory.commits);
    }

    /** Durable corruption remains the first authority even if the caller's request is absent. */
    @Test public void corruptStoreTakesPrecedenceOverNullRequest() {
        Harness test=new Harness();test.memory.values.put(InstallationStore.SNAPSHOT_KEY,"broken");
        assertEquals(InstallationStatus.CACHE_FAILED,test.installer.install(null,PORTS));assertEquals(0,test.memory.commits);
    }

    /** Null activation ports never grant ARMED; a completed commit remains pending for recovery. */
    @Test public void nullPortsLeaveCommittedCandidatePendingWithoutHandlerArm() {
        Harness test=new Harness();
        assertEquals(InstallationStatus.ARM_FAILED,test.installer.install(request(1,TvCapabilities.CODEC_TRACK,"opaque"),null));
        assertEquals(1,test.memory.commits);assertEquals(0,test.handler.arms);assertEquals(1,test.store.read().snapshot().revision());
        assertEquals(0,test.memory.ackWrites);assertEquals(0,test.store.read().acknowledgedRevision());
    }
}
