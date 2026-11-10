package com.scenevibe.tvcompanionpoc;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Qualify explicit native recalage on the principal MEDIA projection. These
 * tests never create a second timer, Android permission or video transport.
 */
public final class CompanionTrackClockTest {
    private final Object session=new Object();
    /** Supply fresh scalar evidence from an owned, advancing MEDIA session. */
    private AdSyncController.Sample sample(long position,long capture){
        return new AdSyncController.Sample(session,true,true,position,position,capture,capture,1f,7_200_000L);
    }
    /** Keep the historical advisory switch OFF, as on a normal existing Companion. */
    private AdSyncController clock(long position){
        AdSyncController clock=new AdSyncController(true);clock.observe(sample(position,100000),100000);return clock;
    }
    /** A 10:15 to 05:15 correction changes the existing projection and keeps progressing. */
    @Test public void recalageWorksWithoutEnablingAnotherSetting(){
        var clock=clock(615000);assertFalse(clock.enabled());
        assertEquals(AdSyncController.Calibration.APPLIED,clock.recalibrateTrack(315000,100000,true));
        var next=clock.observe(sample(615250,100250),100250);
        assertTrue(next.allow);assertEquals(315250,next.contentMs);assertEquals(315250,clock.trackPosition());assertFalse(clock.enabled());
    }
    /** Recalage without resumption stays fixed while the film continues advancing. */
    @Test public void pausedRecalageDoesNotDriftOrResume(){
        var clock=clock(2500000);assertEquals(AdSyncController.Calibration.APPLIED,clock.recalibrateTrack(2550000,100000,false));
        for(int i=1;i<=8;i++){assertFalse(clock.observe(sample(2500000+i*250,100000+i*250),100000+i*250).allow);assertEquals(2550000,clock.trackPosition());}
        assertTrue(clock.manuallySuspended());
    }
    /** Explicit later resumption retains the paused Track position and requires two fresh samples. */
    @Test public void pausedTrackResumesAtItsOwnAnchor(){
        var clock=clock(10000);clock.recalibrateTrack(20000,100000,false);clock.observe(sample(11000,101000),101000);clock.requestResume();
        assertFalse(clock.observe(sample(11250,101250),101250).allow);
        var next=clock.observe(sample(11500,101500),101500);assertTrue(next.allow);assertEquals(20250,next.contentMs);
    }
    /** Identity loss cannot be overridden by an explicit time entered in the panel. */
    @Test public void incompatibleIdentityRefusesRecalage(){
        var clock=clock(10000);clock.observe(new AdSyncController.Sample(session,false,true,10000,10000,100000,100000,1f,7200000,false),100000);
        assertEquals(AdSyncController.Calibration.IDENTITY_INCOMPATIBLE,clock.recalibrateTrack(20000,100000,true));assertFalse(clock.status().allow);
    }
    /** Session disappearance refuses the edit and keeps native effects closed. */
    @Test public void missingMediaRefusesRecalage(){
        var clock=clock(10000);clock.unavailable();assertEquals(AdSyncController.Calibration.SESSION_ABSENT,clock.recalibrateTrack(20000,100000,true));assertFalse(clock.status().allow);
    }
    /** An old public capture cannot grant permission to seek the Track. */
    @Test public void staleCaptureRefusesRecalage(){
        var clock=clock(10000);assertEquals(AdSyncController.Calibration.OBSERVATION_STALE,clock.recalibrateTrack(20000,102501,true));assertEquals(10000,clock.trackPosition());
    }
    /** Negative, excessive and overflowing input leaves the original position unchanged. */
    @Test public void invalidTimeRefusesWithoutPartialCommit(){
        var clock=clock(10000);for(long candidate:new long[]{-1,43200001,Long.MAX_VALUE}){
            assertEquals(AdSyncController.Calibration.FORMAT_INVALID,clock.recalibrateTrack(candidate,100000,true));assertEquals(10000,clock.trackPosition());}
    }
    /** Preserve the already qualified offset boundary rather than silently widening admission. */
    @Test public void historicalOffsetLimitIsPreserved(){
        var clock=clock(10000);assertEquals(AdSyncController.Calibration.OFFSET_LIMIT,clock.recalibrateTrack(3610001,100000,true));assertEquals(10000,clock.trackPosition());
    }
    /** MEDIA pauses are still pauses after requesting a local resume. */
    @Test public void manualResumeNeverCommandsOrFakesFilmPlayback(){
        var clock=clock(10000);clock.observe(new AdSyncController.Sample(session,true,false,10000,10000,100100,100100,0f,7200000),100100);
        assertEquals(AdSyncController.Calibration.APPLIED,clock.recalibrateTrack(20000,100100,true));
        assertEquals(20000,clock.observe(new AdSyncController.Sample(session,true,false,10000,10000,100350,100350,0f,7200000),100350).contentMs);
    }
    /** Historical callers retain their explicit guard opt-in contract. */
    @Test public void historicalCalibrationDoesNotAcquireNewAuthority(){
        var clock=clock(10000);assertEquals(AdSyncController.Calibration.SYNC_DISABLED,clock.calibrateResult(20000,100000));assertEquals(10000,clock.trackPosition());
    }
}
