package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.ad.AdEvidenceDetector;
import com.scenevibe.tvcompanionpoc.ad.AdEvidenceDetector.Decision;
import com.scenevibe.tvcompanionpoc.ad.AdEvidenceDetector.Edge;
import com.scenevibe.tvcompanionpoc.ad.AdEvidenceDetector.Marker;
import com.scenevibe.tvcompanionpoc.ad.AdEvidenceDetector.Sample;
import com.scenevibe.tvcompanionpoc.ad.AdEvidenceDetector.State;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Positive publisher-contract fixtures and adversarial normal playback sequences.
 * These tests establish classifier behavior, never the existence or correctness
 * of a public advertising marker in Prime Video or a physical Sony observation.
 */
public final class AdEvidenceDetectorTest {
    private final Object session=new Object();
    private final AdEvidenceDetector detector=new AdEvidenceDetector();

    /** Capture explicit facts with no timeline cue, title or editorial identity supplied to the detector. */
    private Sample sample(Object token,long captured,long position,Marker marker,boolean playing,boolean identity,boolean clock){
        return new Sample(token,captured,position,playing?1f:0f,true,identity,playing,clock,marker);
    }
    /** Read one current compatible controller frame; time is a test capture clock, not a film clock replacement. */
    private Decision frame(long time,long position,Marker marker){return detector.observe(sample(session,time,position,marker,true,true,true),time);}
    /** Confirm one synthetic publisher AD flag with two independent, separated captures. */
    private void start(){assertEquals(Edge.NONE,frame(1000,900000,Marker.AD).edge);assertEquals(Edge.AD_START,frame(1250,900250,Marker.AD).edge);}

    /** A fresh compatible MEDIA clock without an explicit flag cannot establish content or advertising. */
    @Test public void ordinaryPlayingWithoutMarkerRemainsUncertain(){for(int i=0;i<20;i++)assertEquals(State.UNCERTAIN,frame(1000+i*250,80000+i*250,Marker.ABSENT).state);
        assertEquals(0,detector.status().adStarts);assertEquals(0,detector.status().adEnds);}
    /** Ordinary forward and backward seeks are negative controls, not advertising evidence. */
    @Test public void seeksWithoutMarkersHaveNoAdEdges(){long now=1000;for(long position:new long[]{1000,1500,900000,900250,90000,90250,50000,50250}){
        assertEquals(Edge.NONE,frame(now,position,Marker.ABSENT).edge);now+=250;}assertEquals(0,detector.status().adStarts);}
    /** Pause, buffering and resumed playback alone cannot turn unknown advertising into a positive. */
    @Test public void pauseAndResumeWithoutMarkersHaveNoAdEdges(){frame(1000,90000,Marker.ABSENT);
        detector.observe(sample(session,1250,90250,Marker.ABSENT,false,true,true),1250);
        detector.observe(sample(session,1500,-1,Marker.ABSENT,false,true,false),1500);
        assertEquals(State.UNCERTAIN,frame(1750,90250,Marker.ABSENT).state);assertEquals(0,detector.status().adStarts);}
    /** Replay the scalar shape around the recorded 15:00 incident without inventing missing ad metadata. */
    @Test public void sonyIncidentCompatibleThenStoppedThenSeekDoesNotInventBoundaries(){
        long now=1000;for(long position:new long[]{900123,900429,928469,993076,-1,993121,999001,999033,-1,988353}){
            detector.observe(sample(session,now,position,Marker.ABSENT,position>=0,true,position>=0),now);now+=250;}
        assertEquals(State.UNCERTAIN,detector.status().state);assertEquals(0,detector.status().adStarts);assertEquals(0,detector.status().adEnds);}
    /** Explicit nonzero metadata is debounced; repeated status calls do not duplicate its edge. */
    @Test public void explicitAdRequiresTwoCapturesAndStartsOnce(){start();assertEquals(State.AD_CONFIRMED,detector.status().state);
        assertEquals(Edge.NONE,detector.status().edge);assertEquals(Edge.NONE,frame(1500,0,Marker.AD).edge);assertEquals(1,detector.status().adStarts);}
    /** A present explicit zero plus compatible coherent content clock confirms only candidate content. */
    @Test public void explicitContentDoesNotInventAnAdEndAtStartup(){assertEquals(State.UNCERTAIN,frame(1000,1000,Marker.CONTENT).state);
        assertEquals(State.CONTENT_CONFIRMED,frame(1250,1250,Marker.CONTENT).state);assertEquals(0,detector.status().adEnds);}
    /** The end is a separate same-session explicit zero transition with two coherent playing samples. */
    @Test public void explicitStartAndEndAreDistinct(){start();assertEquals(Edge.NONE,frame(1500,800000,Marker.CONTENT).edge);
        assertEquals(Edge.AD_END,frame(1750,800250,Marker.CONTENT).edge);assertEquals(1,detector.status().adEnds);
        assertEquals(Edge.NONE,frame(2000,800500,Marker.CONTENT).edge);}
    /** Absent metadata while an ad is open is uncertainty and cannot manufacture AD_END. */
    @Test public void missingFlagIsNotAnEnd(){start();assertEquals(State.UNCERTAIN,frame(1500,920000,Marker.ABSENT).state);
        assertEquals(0,detector.status().adEnds);frame(1750,920250,Marker.AD);assertEquals(Edge.NONE,frame(2000,920500,Marker.AD).edge);assertEquals(1,detector.status().adStarts);}
    /** A disappearing session does not imply that the film returned on a replacement token. */
    @Test public void replacementCannotCloseOldAd(){start();Object replacement=new Object();
        detector.observe(sample(replacement,1500,1000,Marker.CONTENT,true,true,true),1500);
        assertEquals(State.CONTENT_CONFIRMED,detector.observe(sample(replacement,1750,1250,Marker.CONTENT,true,true,true),1750).state);
        assertEquals(0,detector.status().adEnds);}
    /** Multiple active target sessions are ambiguous even when one advertises a positive flag. */
    @Test public void ambiguousTargetIsNeverConfirmed(){start();Sample conflict=new Sample(session,1500,1000,1f,false,true,true,true,Marker.AD);
        assertEquals(State.UNCERTAIN,detector.observe(conflict,1500).state);assertEquals(1,detector.status().adStarts);assertEquals(0,detector.status().adEnds);}
    /** Reusing the same capture twice is not independent evidence. */
    @Test public void duplicateCaptureDoesNotConfirm(){frame(1000,0,Marker.AD);assertEquals(State.UNCERTAIN,frame(1000,0,Marker.AD).state);
        assertEquals(0,detector.status().adStarts);}
    /** Very close callbacks cannot satisfy the sampling confirmation interval. */
    @Test public void rapidCallbacksDoNotConfirm(){frame(1000,0,Marker.AD);assertEquals(State.UNCERTAIN,frame(1050,0,Marker.AD).state);
        assertEquals(Edge.AD_START,frame(1250,0,Marker.AD).edge);}
    /** A stale or future-dated observation cannot confirm a current break. */
    @Test public void staleAndFutureSamplesAreRejected(){frame(1000,0,Marker.AD);
        assertEquals(State.UNCERTAIN,detector.observe(sample(session,1250,0,Marker.AD,true,true,true),5000).state);
        assertEquals(State.UNCERTAIN,detector.observe(sample(session,6000,0,Marker.AD,true,true,true),5000).state);assertEquals(0,detector.status().adStarts);}
    /** Unknown/malformed metadata cannot be coerced into numeric false. */
    @Test public void invalidMarkerDoesNotBecomeContent(){start();assertEquals(State.UNCERTAIN,frame(1500,0,Marker.INVALID).state);assertEquals(0,detector.status().adEnds);}
    /** An explicit zero cannot authorize a return to a different editorial identity. */
    @Test public void incompatibleIdentityBlocksContentConfirmation(){start();for(long time:new long[]{1500,1750})
        assertEquals(State.UNCERTAIN,detector.observe(sample(session,time,1000,Marker.CONTENT,true,false,true),time).state);assertEquals(0,detector.status().adEnds);}
    /** Paused or invalid-clock samples do not establish resumed film playback even with an explicit zero. */
    @Test public void pausedOrInvalidClockCannotConfirmEnd(){start();
        detector.observe(sample(session,1500,1000,Marker.CONTENT,false,true,true),1500);
        assertEquals(State.UNCERTAIN,detector.observe(sample(session,1750,1000,Marker.CONTENT,true,true,false),1750).state);assertEquals(0,detector.status().adEnds);}
    /** A seek coinciding with a zero resets content confirmation until two new coherent observations exist. */
    @Test public void contentSeekNeedsNewCoherentPair(){frame(1000,1000,Marker.CONTENT);assertEquals(State.UNCERTAIN,frame(1250,900000,Marker.CONTENT).state);
        assertEquals(State.CONTENT_CONFIRMED,frame(1500,900250,Marker.CONTENT).state);assertEquals(0,detector.status().adStarts);}
    /** A metadata gap is never counted as an additional independent flag observation. */
    @Test public void gapRestartsConsecutiveEvidence(){frame(1000,0,Marker.AD);frame(1250,0,Marker.ABSENT);
        assertEquals(State.UNCERTAIN,frame(1500,0,Marker.AD).state);assertEquals(Edge.AD_START,frame(1750,0,Marker.AD).edge);}
    /** Timeouts do not silently convert stale evidence into an advertising end. */
    @Test public void timeoutDoesNotConfirmEnd(){start();assertEquals(State.UNCERTAIN,detector.unavailable().state);assertEquals(0,detector.status().adEnds);}
    /** Turning the independent module off and on starts empty without a retained advertising classification. */
    @Test public void resetOnlyClearsThisDetectorsInference(){start();detector.reset();assertEquals(State.UNCERTAIN,detector.status().state);
        assertEquals(0,detector.status().adStarts);assertEquals(0,detector.status().adEnds);}
}
