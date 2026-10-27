package com.scenevibe.tvcompanionpoc;

import org.junit.Test;
import static org.junit.Assert.*;

/** Deterministic public-clock sequences prove local policy, never real Prime advertisement detection. */
public final class AdSyncControllerTest {
    private static final Object SESSION=new Object();
    /** Use a fresh PLAYING anchor from the same synthetic, already-eligible session. */
    private AdSyncController.Sample sample(long position,long time){return frame(SESSION,true,true,position,position,time,time,1f,1_000_000);}
    /** Describe all independently variable scalar facts without a hidden fake wall clock. */
    private AdSyncController.Sample frame(Object key,boolean usable,boolean playing,long raw,long selected,long anchor,long now,float speed,long duration){
        return new AdSyncController.Sample(key,usable,playing,raw,selected,anchor,now,speed,duration);}
    /** Opt in before a sequence, retaining the default no-op mode in other callers. */
    private AdSyncController enabled(){AdSyncController c=new AdSyncController();c.setEnabled(true);return c;}
    /** No extra delay or offset is imposed on ordinary continuous playback. */
    @Test public void normalPlaybackRemainsUnchanged(){AdSyncController c=enabled();for(int i=0;i<20;i++){
        AdSyncController.Decision d=c.observe(sample(5000+i*250,100000+i*250));assertTrue(d.allow);assertEquals(5000+i*250,d.contentMs);}}
    /** Default OFF accepts the original selected clock including seeks, without inference. */
    @Test public void disabledKeepsLegacyRoute(){AdSyncController c=new AdSyncController();assertTrue(c.observe(sample(9000,100000)).allow);
        assertTrue(c.observe(sample(100,100250)).allow);assertEquals(AdSyncController.State.DISABLED,c.status().state);}
    /** Manual suspension survives arbitrary subsequent clock movement. */
    @Test public void manualHoldCannotEndItself(){AdSyncController c=enabled();c.observe(sample(1000,100000));c.suspend();
        for(int i=1;i<10;i++)assertFalse(c.observe(sample(1000+i*1000,100000+i*1000)).allow);assertEquals(AdSyncController.State.SUSPENDED,c.status().state);}
    /** An explicit resume still waits for two compatible observations. */
    @Test public void resumeRequiresTwoFreshSamples(){AdSyncController c=enabled();c.observe(sample(1000,100000));c.suspend();c.requestResume();
        assertFalse(c.observe(sample(1000,101000)).allow);assertTrue(c.observe(sample(1250,101250)).allow);}
    /** Duplicate callbacks cannot satisfy the two-observation confirmation. */
    @Test public void duplicateCaptureDoesNotConfirmResume(){AdSyncController c=enabled();c.suspend();c.requestResume();
        assertFalse(c.observe(sample(1000,100000)).allow);assertFalse(c.observe(sample(1000,100000)).allow);}
    /** An old raw state extrapolated by the probe is insufficient for automatic resumption. */
    @Test public void staleAnchorRequiresManualCalibration(){AdSyncController c=enabled();c.suspend();c.requestResume();
        assertFalse(c.observe(frame(SESSION,true,true,1000,6000,95000,100000,1f,1_000_000)).allow);
        assertEquals(AdSyncController.Reason.FRESH_ANCHOR_REQUIRED,c.status().reason);}
    /** Losing a session immediately stops authorization and does not auto-resume when it returns. */
    @Test public void missingSessionRemainsUncertain(){AdSyncController c=enabled();c.observe(sample(1000,100000));c.unavailable();
        assertFalse(c.observe(sample(1250,100250)).allow);assertEquals(AdSyncController.State.UNCERTAIN,c.status().state);}
    /** A different session cannot silently inherit the prior comment lifetime. */
    @Test public void replacementSessionClosesGate(){AdSyncController c=enabled();c.observe(sample(1000,100000));
        assertFalse(c.observe(frame(new Object(),true,true,1250,1250,100250,100250,1f,1_000_000)).allow);
        assertEquals(AdSyncController.Reason.SESSION_CHANGED,c.status().reason);}
    /** Target mismatch cannot be overridden by calibration or a resume request. */
    @Test public void identityFailureRefusesManualOverride(){AdSyncController c=enabled();c.observe(sample(1000,100000));c.requestResume();
        assertFalse(c.observe(frame(SESSION,false,true,1250,1250,100250,100250,1f,1_000_000)).allow);assertFalse(c.calibrate(1250,100250));}
    /** A discontinuous clock is ambiguous even when the platform keeps the content media id. */
    @Test public void forwardJumpRequiresConfirmation(){AdSyncController c=enabled();c.observe(sample(1000,100000));
        assertFalse(c.observe(sample(15000,100250)).allow);assertEquals(AdSyncController.Reason.CLOCK_JUMP,c.status().reason);}
    /** Rewinding is also ambiguous until confirmed; it is not labelled as an advertisement. */
    @Test public void backwardJumpRequiresConfirmation(){AdSyncController c=enabled();c.observe(sample(15000,100000));
        assertFalse(c.observe(sample(1000,100250)).allow);assertEquals(AdSyncController.Reason.CLOCK_JUMP,c.status().reason);}
    /** A freshly updated PLAYING anchor frozen at one position is held before further scheduling. */
    @Test public void freshFrozenPositionClosesGate(){AdSyncController c=enabled();c.observe(sample(1000,100000));
        assertFalse(c.observe(sample(1000,100250)).allow);assertEquals(AdSyncController.Reason.FRESH_POSITION_FROZEN,c.status().reason);}
    /** A normal unchanged raw anchor is not misclassified merely because the probe extrapolates it. */
    @Test public void oldAnchorExtrapolationIsNotAnAdSignal(){AdSyncController c=enabled();c.observe(sample(1000,100000));
        assertTrue(c.observe(frame(SESSION,true,true,1000,1250,100000,100250,1f,1_000_000)).allow);}
    /** An advertisement with an identical advancing public clock is objectively indistinguishable in this model. */
    @Test public void continuousAdClockHasNoFalseDetectionGuarantee(){AdSyncController c=enabled();c.observe(sample(1000,100000));
        AdSyncController.Decision d=c.observe(sample(1250,100250));assertTrue(d.allow);assertEquals(AdSyncController.Reason.CONTINUITY_ONLY,d.reason);}
    /** A paused main programme freezes naturally, without being called a detected ad. */
    @Test public void pausedPositionIsNotFreshPlayingFreeze(){AdSyncController c=enabled();
        assertTrue(c.observe(frame(SESSION,true,false,1000,1000,100000,100000,0f,1_000_000)).allow);
        assertTrue(c.observe(frame(SESSION,true,false,1000,1000,100250,100250,0f,1_000_000)).allow);}
    /** Duration changes are a reason to suspend, even when the platform reuses its media id. */
    @Test public void changedDurationClosesGate(){AdSyncController c=enabled();c.observe(sample(1000,100000));
        assertFalse(c.observe(frame(SESSION,true,true,1250,1250,100250,100250,1f,30000)).allow);assertEquals(AdSyncController.Reason.DURATION_CHANGED,c.status().reason);}
    /** NaN, absent anchors and future update times cannot become content-time proofs. */
    @Test public void malformedTemporalFactsAreRefused(){for(AdSyncController.Sample s:new AdSyncController.Sample[]{
        frame(SESSION,true,true,1,1,100000,100000,Float.NaN,1),frame(SESSION,true,true,1,1,100001,100000,1f,1),
        frame(null,true,true,1,1,100000,100000,1f,1),frame(SESSION,true,true,-1,1,100000,100000,1f,1)})assertFalse(enabled().observe(s).allow);}
    /** A confirmed manual film time changes only the local selected-clock offset. */
    @Test public void manualOffsetUsesOnlyTheObservedClock(){AdSyncController c=enabled();c.observe(sample(90000,100000));c.suspend();
        assertTrue(c.calibrate(60000,100000));AdSyncController.Decision d=c.observe(sample(90250,100250));assertTrue(d.allow);assertEquals(60250,d.contentMs);assertEquals(-30000,d.offsetMs);}
    /** A manual action on an obsolete capture is refused instead of inventing a time anchor. */
    @Test public void staleCalibrationIsRefused(){AdSyncController c=enabled();c.observe(sample(1000,100000));assertFalse(c.calibrate(1000,102501));}
    /** Corrections outside the declared one-hour bound or a negative film time fail closed. */
    @Test public void calibrationBoundsAreEnforced(){AdSyncController c=enabled();c.observe(sample(1000,100000));assertFalse(c.calibrate(-1,100000));assertFalse(c.calibrate(4_000_000,100000));}
    /** Switching OFF removes the offset and restores the qualified selected-clock path. */
    @Test public void disableClearsManualOffset(){AdSyncController c=enabled();c.observe(sample(1000,100000));c.calibrate(2000,100000);c.setEnabled(false);assertEquals(1250,c.observe(sample(1250,100250)).contentMs);}
    /** TV-entered clock values have explicit units and reject malformed or out-of-range input. */
    @Test public void parsesOnlyBoundedFilmTimes(){assertEquals(900000,AdSyncController.parseFilmTime("15:00"));assertEquals(3601000,AdSyncController.parseFilmTime("1:00:01"));
        for(String s:new String[]{"15","1:60","-1:00","1:00:60","999:00","12:00:01","1:1"})try{AdSyncController.parseFilmTime(s);fail(s);}catch(IllegalArgumentException expected){}}
}
