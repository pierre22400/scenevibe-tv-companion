package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.audio.LocalAudioPort;
import com.scenevibe.tvcompanionpoc.calendar.MediaObservation;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual calendar, TEXT regie and AUDIO renderer integration; drawing/audio ports are explicit recording fakes. */
public final class AdSyncRuntimeTest {
    private StudioMultimediaRuntime runtime;
    private final Port port=new Port();
    private final List<String> shown=new ArrayList<>();
    private boolean visible;
    /** Compose the existing software runtime without Cloud, network or native MEDIA authority. */
    @Before public void prepare() throws Exception {runtime=AdSyncTestFixture.runtime(new SceneRuntimeController.SceneSink(){
        /** Accept only synthetic, payload-free test scenes. */
        @Override public boolean preflight(OverlayManifest.Scene s){return true;}
        /** Record the actual TEXT effect selected by the regie. */
        @Override public void show(OverlayManifest.Scene s){shown.add(s.id);visible=true;}
        /** Record local window expiration. */
        @Override public void hide(OverlayManifest.Scene s){visible=false;}
        /** Prove interruption removes SceneVibe visuals immediately. */
        @Override public void hideAll(){visible=false;}
    },port);}
    /** Revoke the test activation and release its local effects. */
    @After public void close(){if(runtime!=null)runtime.close();}
    /** Deliver a selected media-time position, with no second timer or inferred platform transport. */
    private void at(long position){runtime.observe("synthetic-activation",new MediaObservation(true,position,true));}
    /** Original continuous playback still routes both event types through the one calendar. */
    @Test public void noInterruptionPreservesAllEvents(){for(int p=0;p<=12500;p+=250)at(p);assertEquals(List.of("text1","text2"),shown);assertEquals(3,port.starts);}
    /** Holding before a due event preserves it until content time reaches it after return. */
    @Test public void unconsumedFutureAudioSurvivesSuspension(){at(1750);runtime.suspendComments();assertEquals(0,port.starts);
        at(1750);at(2000);assertEquals(1,port.starts);}
    /** Interrupted speech is stopped, and returning within its window does not replay its consumed identity. */
    @Test public void activeAudioStopsAndIsNotRestartedOnReturn(){at(2000);assertEquals(1,port.starts);runtime.suspendComments();assertEquals(LocalAudioPort.State.IDLE,port.state());
        at(2250);at(2500);assertEquals(1,port.starts);}
    /** A return before an already-consumed start cannot silently rearm that phrase. */
    @Test public void backwardReturnDoesNotRearmConsumedAudio(){at(2000);runtime.suspendComments();at(1750);at(2000);assertEquals(1,port.starts);}
    /** Resuming after all windows expired cannot produce a burst of old TEXT or AUDIO. */
    @Test public void expiredResumeEmitsNoLateEvents(){at(0);runtime.suspendComments();at(13000);assertTrue(shown.isEmpty());assertEquals(0,port.starts);}
    /** After confirmed resumption, a genuine later backward seek retains the qualified replay policy. */
    @Test public void subsequentRealSeekCanRearmAudio(){at(2000);runtime.suspendComments();at(2000);at(8000);at(0);at(1000);at(2000);assertEquals(3,port.starts);}
    /** Repeated suspension remains idempotent and keeps the remaining calendar. */
    @Test public void repeatedHoldDoesNotConsumeFuture(){at(1750);for(int i=0;i<10;i++)runtime.suspendComments();at(1750);at(2000);assertEquals(1,port.starts);}
    /** Visual effects disappear immediately instead of freezing over a third-party interruption. */
    @Test public void suspensionHidesTextImmediately(){at(1000);assertTrue(visible);runtime.suspendComments();assertFalse(visible);}
    /** TEXT OFF survives suspension and does not suppress future AUDIO. */
    @Test public void independentTextSwitchSurvivesReturn(){runtime.setTextEnabled(false);at(1000);runtime.suspendComments();at(1750);at(2000);assertTrue(shown.isEmpty());assertEquals(1,port.starts);}
    /** AUDIO OFF survives return and never re-enables itself from the interruption gate. */
    @Test public void independentAudioSwitchSurvivesReturn(){runtime.setAudioEnabled(false);at(1000);runtime.suspendComments();at(1750);at(2000);assertEquals(0,port.starts);assertEquals(List.of("text1"),shown);}
    /** An obsolete activation cannot release a held scheduler or render new content. */
    @Test public void staleActivationCannotResume(){at(1750);runtime.suspendComments();runtime.observe("wrong-activation",new MediaObservation(true,2000,true));assertEquals(0,port.starts);at(2000);assertEquals(1,port.starts);}
    /** Eligibility loss during a hold cannot erase its replay protection. */
    @Test public void unavailableWhileHeldPreservesConsumed(){at(2000);runtime.suspendComments();runtime.unavailable();at(2250);assertEquals(1,port.starts);}
    /** Explicit owner-confirmed SEEK, unlike an interruption return, authorizes replay at the new landing. */
    @Test public void confirmedSeekCanReplayInsideConsumedWindow(){at(2000);runtime.suspendComments();at(2250);assertEquals(1,port.starts);
        runtime.confirmSeek();at(2250);assertEquals(2,port.starts);}
    /** Local ports have no networking capability, so the held calendar remains usable offline. */
    @Test public void localResumeHasNoNetworkDependency(){at(1750);runtime.suspendComments();at(1750);at(2000);at(6000);at(6250);assertEquals(2,port.starts);}
    /** Paused confirmation anchors the same calendar without prematurely dispatching due comments. */
    @Test public void pausedResumeWaitsForPlaying(){at(1750);runtime.suspendComments();runtime.observe("synthetic-activation",new MediaObservation(true,2000,false));assertEquals(0,port.starts);at(2000);assertEquals(1,port.starts);}

    /** v22 automatic confirmation preserves consumed MP3 identities and still admits a future original event. */
    @Test public void automaticRecoveryDoesNotReplayConsumedAudio(){
        var guard=new AdSyncController(true);guard.setEnabled(true);
        assertTrue(guard.observe(new AdSyncController.Sample("own",true,true,2000,2000,1000,1000,1f,100000)).allow);at(2000);assertEquals(1,port.starts);
        guard.unavailable();runtime.suspendComments();
        assertFalse(guard.observe(new AdSyncController.Sample("own",true,true,1750,1750,2000,2000,1f,100000)).allow);
        var returned=guard.observe(new AdSyncController.Sample("own",true,true,2000,2000,2250,2250,1f,100000));assertTrue(returned.allow);at(returned.contentMs);assertEquals(1,port.starts);
        for(long position=2250;position<=6250;position+=250){long capture=position+250;var future=guard.observe(new AdSyncController.Sample("own",true,true,position,position,capture,capture,1f,100000));assertTrue(future.allow);at(future.contentMs);}
        assertEquals(2,port.starts);
    }

    /** A recording LOCAL audio port; there is deliberately no interface to a platform player or network. */
    private static final class Port implements LocalAudioPort {
        int starts;State state=State.IDLE;
        /** Record preparation without generating or fetching audio bytes. */
        @Override public void prepare(String id){state=State.PREPARED;}
        /** Count only a SceneVibe renderer start. */
        @Override public void start(){starts++;state=State.STARTED;}
        /** Remove only the current local effect. */
        @Override public void stop(){state=State.IDLE;}
        /** Release the recording local player. */
        @Override public void release(){state=State.IDLE;}
        /** Ignore gain in this deterministic effect-count test. */
        @Override public void setGain(float gain){}
        /** Expose recorded lifecycle facts. */
        @Override public State state(){return state;}
    }
}
