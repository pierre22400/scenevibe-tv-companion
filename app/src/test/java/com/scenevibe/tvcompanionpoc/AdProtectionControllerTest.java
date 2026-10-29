package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.ad.AdEvidenceDetector.Marker;
import com.scenevibe.tvcompanionpoc.ad.AdEvidenceDetector.Sample;
import com.scenevibe.tvcompanionpoc.ad.AdProtectionController;
import com.scenevibe.tvcompanionpoc.ad.AdProtectionController.Edge;
import com.scenevibe.tvcompanionpoc.ad.AdProtectionController.State;
import org.junit.Test;
import static org.junit.Assert.*;

/** Guard positive/negative sequences, without any Prime clock or editorial cue supplied to the machine. */
public final class AdProtectionControllerTest {
    private final AdProtectionController guard=new AdProtectionController();
    private final Object session=new Object();
    /** Compose current observed scalars; no metadata, annotation or clock is invented in the production machine. */
    private Sample frame(long time,long position,Marker marker,boolean playing,boolean identity){return new Sample(session,time,position,playing?1f:0f,true,identity,playing,position>=0,marker);}
    /** Deliver a compatible current query to the independent gate. */
    private AdProtectionController.Decision at(long time,long position,Marker marker){return guard.observe(frame(time,position,marker,true,true),time);}
    /** Enable, observe ordinary absent metadata, then positively confirm an observed appearance. */
    private void ad(){guard.setEnabled(true);at(0,1000,Marker.ABSENT);assertFalse(at(250,1250,Marker.AD).allow);
        assertEquals(Edge.AD_START,at(500,1250,Marker.AD).edge);assertEquals(State.AD_GUARD,guard.status().state);}
    /** Confirm a progressing return without manufacturing a replacement content clock. */
    private AdProtectionController.Decision exit(){assertFalse(at(1000,5000,Marker.ABSENT).allow);assertFalse(at(1500,5500,Marker.ABSENT).allow);return at(2000,6000,Marker.ABSENT);}

    /** No explicit opt-in means exact reference-path eligibility, even with a positive ad flag. */
    @Test public void offNeverGates(){assertTrue(at(0,1000,Marker.AD).allow);assertEquals(State.NORMAL,guard.status().state);}
    /** Enabling during a break cannot allow effects before the initial actual capture. */
    @Test public void optInWaitsForInitialCapture(){guard.setEnabled(true);assertFalse(guard.status().allow);assertTrue(at(0,1000,Marker.ABSENT).allow);}
    /** First positive callback closes effects before a following 250ms scheduling observation. */
    @Test public void callbackImmediatelyHolds(){guard.setEnabled(true);at(0,1000,Marker.ABSENT);
        assertFalse(guard.positive(session,100).allow);assertEquals(State.AD_PENDING,guard.status().state);assertEquals(0,guard.status().adStarts);}
    /** The Sony absent/AD/absent shape has observed appearance and separately qualified end. */
    @Test public void absentAdAbsentWithProgressingFilm(){ad();var end=exit();assertTrue(end.allow);assertEquals(Edge.AD_END,end.edge);assertEquals(1,end.adStarts);assertEquals(1,end.adEnds);}
    /** Starting observation in a break confirms presence, never its unobserved onset. */
    @Test public void enableMidAdDoesNotInventStart(){guard.setEnabled(true);at(0,1000,Marker.AD);
        assertEquals(Edge.AD_PRESENT,at(250,1000,Marker.AD).edge);assertEquals(0,guard.status().adStarts);assertFalse(guard.status().allow);assertTrue(exit().allow);}
    /** One false positive can recover, but never release from a single absent frame. */
    @Test public void isolatedFlagRecoversWithoutConfirmedAdEnd(){guard.setEnabled(true);at(0,1000,Marker.ABSENT);at(250,1000,Marker.AD);
        var end=exit();assertTrue(end.allow);assertEquals(0,end.adEnds);assertEquals("ISOLATED_FLAG_RECOVERED",end.reason);}
    /** Temporary marker loss while the public position remains frozen cannot release protection. */
    @Test public void frozenPlayingAfterMissingMarkerStaysHeld(){ad();for(long time:new long[]{1000,1500,2000,2500,3000})assertFalse(at(time,2420980,Marker.ABSENT).allow);}
    /** A renewed positive during exit verification keeps the same interruption open. */
    @Test public void reappearingAdCancelsExit(){ad();at(1000,5000,Marker.ABSENT);at(1500,5500,Marker.ABSENT);
        assertFalse(at(1750,5500,Marker.AD).allow);at(2000,5500,Marker.AD);assertEquals(1,guard.status().adStarts);assertEquals(0,guard.status().adEnds);}
    /** Absence through ordinary continuous playback does not create an ad or an additional pause. */
    @Test public void prolongedNormalPlaybackIsReferenceCompatible(){guard.setEnabled(true);for(int i=0;i<100;i++)assertTrue(at(i*250,1000+i*250,Marker.ABSENT).allow);assertEquals(0,guard.status().adStarts);}
    /** An ordinary forward/backward seek is not a positive marker. */
    @Test public void ordinarySeeksHaveNoFalsePositive(){guard.setEnabled(true);long time=0;for(long p:new long[]{1000,900000,90000,0,300000})assertTrue(at(time+=250,p,Marker.ABSENT).allow);assertEquals(0,guard.status().adStarts);}
    /** Paused metadata alone is not advertising; the independent MEDIA renderer still controls pause. */
    @Test public void normalPauseDoesNotCreateAd(){guard.setEnabled(true);assertTrue(guard.observe(frame(0,1000,Marker.ABSENT,false,true),0).allow);assertEquals(0,guard.status().adStarts);}
    /** Buffering without a positive marker adds no lasting ad suspicion. */
    @Test public void normalBufferingDoesNotCreateAd(){guard.setEnabled(true);assertTrue(guard.observe(frame(0,-1,Marker.ABSENT,false,true),0).allow);assertTrue(at(250,1000,Marker.ABSENT).allow);}
    /** A paused return after a real positive is not evidence that the film resumed. */
    @Test public void pauseCannotEndConfirmedAd(){ad();for(long t:new long[]{1000,1500,2000})assertFalse(guard.observe(frame(t,5000,Marker.ABSENT,false,true),t).allow);assertEquals(0,guard.status().adEnds);}
    /** Real identity mismatch remains held, without revoking the cache or caller's separate admission. */
    @Test public void wrongFilmCannotEndAd(){ad();for(long t:new long[]{1000,1500,2000})assertFalse(guard.observe(frame(t,t+5000,Marker.ABSENT,true,false),t).allow);}
    /** A malformed marker cannot masquerade as a typed explicit content zero after advertising. */
    @Test public void malformedDuringAdRemainsHeld(){ad();assertFalse(at(1000,5000,Marker.INVALID).allow);assertEquals(State.UNCERTAIN,guard.status().state);}
    /** Malformed input without any known positive is not invented advertising. */
    @Test public void malformedBeforeAnyAdDoesNotInventEpisode(){guard.setEnabled(true);assertTrue(at(0,1000,Marker.INVALID).allow);assertEquals(0,guard.status().adStarts);}
    /** Session destruction is not an advertising-end signal. */
    @Test public void unavailableDoesNotEndAd(){ad();assertFalse(guard.unavailable().allow);assertFalse(guard.watch(10000).allow);assertEquals(0,guard.status().adEnds);}
    /** An ambiguous active publisher batch cannot requalify editorial playback. */
    @Test public void ambiguityAfterAdStaysHeld(){ad();Sample sample=new Sample(session,1000,5000,1f,false,true,true,true,Marker.ABSENT);
        assertFalse(guard.observe(sample,1000).allow);assertEquals(0,guard.status().adEnds);}
    /** Duplicate capture times cannot satisfy the three independent exit observations. */
    @Test public void duplicateCapturesDoNotRequalify(){ad();for(int i=0;i<5;i++)assertFalse(at(1000,5000,Marker.ABSENT).allow);assertEquals(0,guard.status().adEnds);}
    /** Old captures do not prove current playback after marker loss. */
    @Test public void staleReturnDoesNotRelease(){ad();assertFalse(guard.observe(frame(1000,5000,Marker.ABSENT,true,true),10000).allow);}
    /** Large temporal jumps during exit reset the coherent sequence, never create an offset. */
    @Test public void seekDuringExitNeedsNewStableSequence(){ad();at(1000,5000,Marker.ABSENT);assertFalse(at(1500,900000,Marker.ABSENT).allow);
        assertFalse(at(2000,900500,Marker.ABSENT).allow);assertTrue(at(2500,901000,Marker.ABSENT).allow);}
    /** A target session replacement alone does not end advertising; a stable compatible return still can recover. */
    @Test public void replacementNeedsFreshThreeFrameReturn(){ad();Object next=new Object();for(int i=0;i<3;i++){
        var result=guard.observe(new Sample(next,1000+i*500,5000+i*500,1f,true,true,true,true,Marker.ABSENT),1000+i*500);
        assertEquals(i==2,result.allow);}assertEquals(1,guard.status().adEnds);}
    /** The owner can deliberately return to the reference path; the next enable starts empty and closed. */
    @Test public void explicitOffIsControlledEscape(){ad();guard.setEnabled(false);assertTrue(guard.status().allow);guard.setEnabled(true);assertFalse(guard.status().allow);assertTrue(at(10000,5000,Marker.ABSENT).allow);}
    /** Normal recovery after a lost session cannot create a fictitious advertising episode. */
    @Test public void normalSessionLossRecoversWithoutAdEnd(){guard.setEnabled(true);at(0,1000,Marker.ABSENT);guard.unavailable();assertTrue(at(1000,2000,Marker.ABSENT).allow);assertEquals(0,guard.status().adEnds);}
}
