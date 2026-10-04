package com.scenevibe.tvcompanionpoc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

/** Execute actual io/owner queue races and ensure a posted or obsolete mutation cannot authorize ACK. */
public final class M4PhaseFOwnerGateTest {
    /** The Cloud worker must await actual owner completion rather than ACK merely because work was posted. */
    @Test public void ackWaitsForActualOwnerCompletion() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(true,14)) {
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            h.beforeInstall=()->{entered.countDown();waitFor(release);};
            Future<?> fetch=h.fetchAsync("fetchAssignment");assertTrue(entered.await(3,TimeUnit.SECONDS));
            try {assertFalse(fetch.isDone());assertEquals(0,h.acks);assertEquals(0,h.backend.candidateWrites);}
            finally {release.countDown();}
            M4PhaseFFixtures.Harness.await(fetch);assertEquals(1,h.acks);assertEquals(1,h.backend.candidateWrites);
        }
    }
    /** The lifetime predicate is checked after dispatch so a synchronously stopped queued client does nothing. */
    @Test public void stoppedBeforeQueuedOwnerMutationCannotInstallOrAck() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(true,14)) {
            CountDownLatch release=blockOwner(h);Future<?> fetch=h.fetchAsync("fetchAssignment");
            assertTrue(h.dispatched.await(3,TimeUnit.SECONDS));M4PhaseFFixtures.field(h.client,"running",false);release.countDown();
            boolean failed=false;try {M4PhaseFFixtures.Harness.await(fetch);}catch(Exception expected){failed=true;}
            assertTrue(failed);assertEquals(0,h.installCalls+h.acks+h.backend.candidateWrites);
        }
    }
    /** A service replacement invalidates the old instance even if its running flag has not changed yet. */
    @Test public void replacedCurrentInstanceCannotApplyQueuedAssignment() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(true,14)) {
            CountDownLatch release=blockOwner(h);Future<?> fetch=h.fetchAsync("fetchAssignment");
            assertTrue(h.dispatched.await(3,TimeUnit.SECONDS));h.current.set(false);release.countDown();
            boolean failed=false;try {M4PhaseFFixtures.Harness.await(fetch);}catch(Exception expected){failed=true;}
            assertTrue(failed);assertEquals(0,h.installCalls+h.acks+h.backend.candidateWrites);
        }
    }
    /** Interrupting the real io wait cancels the queued FutureTask; releasing the owner cannot install later. */
    @Test public void interruptedWaitCancelsLateOwnerInstallation() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(true,14)) {
            CountDownLatch release=blockOwner(h);Future<?> fetch=h.fetchAsync("fetchAssignment");
            assertTrue(h.dispatched.await(3,TimeUnit.SECONDS));h.client.stop();release.countDown();
            try {M4PhaseFFixtures.Harness.await(fetch);fail("interrupted owner wait must fail");}
            catch(InterruptedException expected) { /* Original interrupted-wait discipline is preserved. */ }
            h.onOwner(()->null);assertEquals(0,h.installCalls+h.acks+h.backend.candidateWrites);
        }
    }
    /** Loss of current-client identity after successful ARM prevents the subsequent HTTP request. */
    @Test public void replacementAfterArmPreventsAck() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(true,14)) {
            h.afterInstall=()->h.current.set(false);h.fetch();assertEquals(1,h.backend.candidateWrites);
            assertEquals(0,h.acks);assertEquals(0,h.store.read().acknowledgedRevision());
        }
    }
    /** A response from an obsolete instance cannot persist confirmation into the shared store. */
    @Test public void replacementDuringAckPreventsLocalConfirmation() throws Exception {
        try(M4PhaseFFixtures.Harness h=new M4PhaseFFixtures.Harness(true,14)) {
            h.onAck=()->h.current.set(false);h.fetch();assertEquals(1,h.acks);assertEquals(0,h.backend.ackWrites);
            assertEquals(0,h.store.read().acknowledgedRevision());
        }
    }
    /** Hold the existing owner executor before the client's gate dispatch without blocking the test caller. */
    private static CountDownLatch blockOwner(M4PhaseFFixtures.Harness h) throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        h.owner.submit(()->{entered.countDown();waitFor(release);});assertTrue(entered.await(3,TimeUnit.SECONDS));return release;
    }
    /** Bound fixture waiting and preserve interrupt status; no test waits indefinitely. */
    private static void waitFor(CountDownLatch latch) {
        try {assertTrue(latch.await(3,TimeUnit.SECONDS));}
        catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw new IllegalStateException("Fixture owner interrupted");}
    }
}
