package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.ad.AdEvidenceDetector.Marker;
import com.scenevibe.tvcompanionpoc.ad.MultiPlatformObservationLog;
import com.scenevibe.tvcompanionpoc.ad.MultiPlatformObservationLog.Frame;
import com.scenevibe.tvcompanionpoc.ad.MultiPlatformObservationLog.Platform;
import com.scenevibe.tvcompanionpoc.ad.MultiPlatformObservationLog.ValueType;
import com.scenevibe.tvcompanionpoc.ad.MultiPlatformTransitionFacts;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic diagnostics prove arithmetic and absence of authority, never real Netflix/Disney ad detection. */
public final class MultiPlatformTransitionTest {
    /** Supply explicit synthetic capture and anchor facts, with no protected media or private identity. */
    private Frame frame(int session,int state,long time,long position,float speed,long age){
        return new Frame(session,state,time,position,position,speed,age,1000000,Marker.ABSENT,ValueType.ABSENT);
    }
    /** Stable published two-times playback and small jitter retain numeric residuals without an ad conclusion. */
    @Test public void speedAndJitterAreMeasured() throws Exception {
        Frame old=frame(1,3,1000,10000,2f,0),next=frame(1,3,2000,12020,2f,0);
        JSONObject facts=MultiPlatformTransitionFacts.compare(next,old,2000);
        assertTrue(facts.getBoolean("comparable"));assertEquals(2000,facts.getDouble("expectedPositionDeltaMs"),0);
        assertEquals(20,facts.getDouble("positionResidualMs"),0);assertFalse(facts.getBoolean("discontinuity"));
    }
    /** Forward and backward seeks are numeric discontinuities only, including an explicit reset near zero. */
    @Test public void seeksAreNotAdvertisements() throws Exception {
        Frame old=frame(1,3,1000,50000,1f,0);
        for(long position:new long[]{100000,0}){
            Frame next=frame(1,3,2000,position,1f,0);JSONObject facts=MultiPlatformTransitionFacts.compare(next,old,2000);
            assertTrue(facts.getBoolean("discontinuity"));String changes=MultiPlatformTransitionFacts.changes(next,old,2000).toString();
            assertTrue(changes.contains("POSITION_JUMP"));assertFalse(changes.contains("AD_"));
            if(position==0)assertTrue(changes.contains("POSITION_RESET"));
        }
    }
    /** PAUSE and BUFFERING transitions expose resumption but cannot establish comparable editorial time or an ad. */
    @Test public void pauseBufferAndResumeAreFacts() throws Exception {
        for(int state:new int[]{2,6}){Frame old=frame(1,state,1000,10000,0f,0),next=frame(1,3,2000,11000,1f,0);
            assertEquals("STATE_CHANGED",MultiPlatformTransitionFacts.compare(next,old,2000).getString("reason"));
            assertTrue(MultiPlatformTransitionFacts.changes(next,old,2000).toString().contains("PLAYBACK_RESUMED"));}
        assertEquals(0,MultiPlatformTransitionFacts.compare(frame(1,2,2000,10000,0f,0),frame(1,2,1000,10000,0f,0),2000).getDouble("expectedPositionDeltaMs"),0);
    }
    /** Different sessions, missing positions, old anchors and capture gaps never fabricate a residual. */
    @Test public void unavailablePairsAreExplicit() throws Exception {
        Frame old=frame(1,3,1000,10000,1f,0);
        Frame[] invalid={frame(2,3,2000,11000,1f,0),frame(1,3,2000,-1,1f,0),frame(1,3,2000,11000,1f,120001),
                frame(1,3,7000,16000,1f,0),frame(1,3,1000,10000,1f,0),frame(1,3,2000,11000,Float.NaN,0),frame(1,3,2000,11000,1f,-1)};
        for(Frame next:invalid){JSONObject facts=MultiPlatformTransitionFacts.compare(next,old,next.capturedMs);
            assertFalse(facts.getBoolean("comparable"));assertTrue(facts.isNull("positionResidualMs"));}
    }
    /** A stale capture, changed identity, duration or speed is not silently compared across a boundary. */
    @Test public void changedFactsBreakComparison() throws Exception {
        Frame old=frame(1,3,1000,10000,1f,0),next=frame(1,3,2000,11000,1f,0);
        assertEquals("STALE_CAPTURE",MultiPlatformTransitionFacts.compare(next,old,5000).getString("reason"));
        next.identityChanged=true;assertEquals("IDENTITY_CHANGED",MultiPlatformTransitionFacts.compare(next,old,2000).getString("reason"));
        assertEquals("SPEED_CHANGED",MultiPlatformTransitionFacts.compare(frame(1,3,2000,11000,2f,0),old,2000).getString("reason"));
        Frame episode=new Frame(1,3,2000,11000,11000,1f,0,2000000,Marker.ABSENT,ValueType.ABSENT);
        assertEquals("DURATION_CHANGED",MultiPlatformTransitionFacts.compare(episode,old,2000).getString("reason"));
    }
    /** Session disappearance/replacement is auditable; no progression joins two ephemeral ordinals. */
    @Test public void sessionLifecycleDoesNotJoinClocks() throws Exception {
        var log=new MultiPlatformObservationLog(Platform.NETFLIX,100);
        log.capture(List.of(frame(1,3,1000,10000,1f,0)),1,1,1000,100000);
        String replaced=log.capture(List.of(frame(2,3,2000,200,1f,0)),1,1,2000,101000).toString();
        assertTrue(replaced.contains("SESSION_DISAPPEARED"));assertTrue(replaced.contains("SESSION_REPLACED"));
        assertEquals("FIRST_SESSION_SAMPLE",log.status(2000).getJSONArray("samples").getJSONObject(0).getJSONObject("comparison").getString("reason"));
        assertTrue(log.capture(List.of(),0,0,3000,102000).toString().contains("SESSION_DISAPPEARED"));
    }
    /** Stable polling stays bounded to the existing one-second journal; read-only status does not renew it. */
    @Test public void unchangedPollingIsBounded() {
        var log=new MultiPlatformObservationLog(Platform.DISNEY_PLUS);
        log.capture(List.of(frame(1,3,1000,10000,1f,0)),1,1,1000,100000);
        for(int i=1;i<4;i++)assertTrue(log.capture(List.of(frame(1,3,1000+i*250,10000+i*250,1f,0)),1,1,1000+i*250,100000+i*250).isEmpty());
        assertEquals(1,log.capture(List.of(frame(1,3,2000,11000,1f,0)),1,1,2000,101000).size());
    }
    /** Present zero, absent key and unavailable metadata remain separate without changing the legacy marker field. */
    @Test public void markerAvailabilityIsAdditive() throws Exception {
        var log=new MultiPlatformObservationLog(Platform.NETFLIX);Frame missing=frame(1,3,1000,1,1f,0);
        log.capture(List.of(missing),1,1,1000,100000);assertEquals("UNAVAILABLE",log.status(1000).getJSONArray("samples").getJSONObject(0).getString("markerStatus"));
        Frame absent=frame(1,3,2000,1001,1f,0);absent.metadataPresent=true;
        log.capture(List.of(absent),1,1,2000,101000);assertEquals("ABSENT",log.status(2000).getJSONArray("samples").getJSONObject(0).getString("markerStatus"));
    }
    /** Genuine callback envelopes carry only public numbers and are disabled with their campaign. */
    @Test public void callbacksAndAnnotationsRemainScoped() throws Exception {
        var log=new MultiPlatformObservationLog(Platform.NETFLIX,100);
        JSONObject callback=log.playbackCallback(1,3,10000,1f,1000,1000,100000);
        assertEquals("PLAYBACK_CALLBACK",callback.getString("event"));assertFalse(callback.toString().contains("AD_START"));
        JSONObject label=log.annotate("NORMAL_SEEK",1010,100010);
        assertEquals(100,label.getLong("campaignStartedMs"));assertEquals("OWNER_LABEL_NOT_AUTOMATIC_SIGNAL",label.getString("origin"));
        log.close(1100,100100);assertNull(log.playbackCallback(1,2,10000,0f,1100,1100,100100));
    }
}
