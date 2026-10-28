package com.scenevibe.tvcompanionpoc;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Reproduce v18's blocked return without pretending to observe Prime.
 * A capture is current when the public controller is queried now, even if its
 * last state-change anchor is old. Two current, compatible observations remain
 * mandatory; stale captures, incompatible identities and invalid clocks stay closed.
 */
public final class AdSyncFreshCaptureTest {
    private final Object session=new Object();
    /** Model a continuously advancing selected position from an old, valid published anchor. */
    private AdSyncController.Sample frame(long time) {
        return new AdSyncController.Sample(session,true,true,1000,1000+time-100000,100000,time,1f,1_000_000);
    }
    /** Suspend one explicitly enabled guard without changing editorial time or a native permit. */
    private AdSyncController held() {
        AdSyncController controller=new AdSyncController();controller.setEnabled(true);
        controller.observe(frame(100000),100000);controller.suspend();return controller;
    }
    /** v18 stayed closed forever when a live player did not publish a new state anchor. */
    @Test public void resumeAfterOldAnchorRequiresFreshCapturesNotNewStateChanges() {
        AdSyncController controller=held();controller.requestResume();
        assertFalse(controller.observe(frame(130000),130000).allow);
        assertTrue(controller.observe(frame(130250),130250).allow);
    }
    /** A paused current session also need not create artificial new update anchors. */
    @Test public void pausedFreshQueriesCanConfirmWithoutDispatchingPlaying() {
        AdSyncController controller=held();controller.requestResume();
        assertFalse(controller.observe(new AdSyncController.Sample(session,true,false,1000,1000,100000,130000,0f,1_000_000),130000).allow);
        assertTrue(controller.observe(new AdSyncController.Sample(session,true,false,1000,1000,100000,130250,0f,1_000_000),130250).allow);
    }
    /** Navigation away followed by a queued old observation is not proof about the current film. */
    @Test public void staleCaptureCannotConfirmReturn() {
        AdSyncController controller=held();controller.requestResume();
        assertFalse(controller.observe(frame(100000),130000).allow);
        assertEquals(AdSyncController.Reason.OBSERVATION_STALE,controller.status().reason);
    }
    /** Manual calibration uses a current query while allowing its raw state anchor to be old. */
    @Test public void currentCaptureWithOldAnchorCanBeCalibrated() {
        AdSyncController controller=held();controller.observe(frame(130000),130000);
        assertEquals(AdSyncController.Calibration.APPLIED,controller.calibrateResult(15000,130000));
        assertEquals(15250,controller.observe(frame(130250),130250).contentMs);
    }
    /** Disappearing MEDIA invalidates the last sample instead of silently reusing it. */
    @Test public void absentSessionHasItsOwnRefusal() {
        AdSyncController controller=held();controller.unavailable();
        assertEquals(AdSyncController.Calibration.SESSION_ABSENT,controller.calibrateResult(1000,130000));
    }
    /** An unrelated application's current session cannot be overridden by entering a film time. */
    @Test public void incompatibleIdentityHasItsOwnRefusal() {
        AdSyncController controller=held();
        controller.observe(new AdSyncController.Sample(session,false,true,1000,1000,130000,130000,1f,1_000_000,false),130000);
        assertEquals(AdSyncController.Calibration.IDENTITY_INCOMPATIBLE,controller.calibrateResult(1000,130000));
    }
    /** Clock validity is diagnosed separately from metadata identity. */
    @Test public void invalidObservationHasItsOwnRefusal() {
        AdSyncController controller=held();
        controller.observe(new AdSyncController.Sample(session,false,true,-1,-1,130000,130000,1f,1_000_000,true),130000);
        assertEquals(AdSyncController.Calibration.INVALID_OBSERVATION,controller.calibrateResult(1000,130000));
    }
    /** A stale capture cannot become a manual anchor, even if its identity still matches. */
    @Test public void staleObservationHasItsOwnRefusal() {
        AdSyncController controller=held();
        assertEquals(AdSyncController.Calibration.OBSERVATION_STALE,controller.calibrateResult(1000,130000));
    }
    /** Keep the bounded correction and leave the same activation available for a corrected input. */
    @Test public void offsetRefusalDoesNotRequireReactivation() {
        AdSyncController controller=held();
        assertEquals(AdSyncController.Calibration.OFFSET_LIMIT,controller.calibrateResult(4_000_000,100000));
        assertEquals(AdSyncController.Calibration.APPLIED,controller.calibrateResult(1000,100000));
    }
    /** Missing observations never end a suspension without another explicit action. */
    @Test public void navigatingBackToTheFilmDoesNotAutoResume() {
        AdSyncController controller=held();controller.unavailable();
        assertFalse(controller.observe(frame(130000),130000).allow);
        controller.requestResume();assertFalse(controller.observe(frame(130250),130250).allow);
        assertTrue(controller.observe(frame(130500),130500).allow);
    }
}
