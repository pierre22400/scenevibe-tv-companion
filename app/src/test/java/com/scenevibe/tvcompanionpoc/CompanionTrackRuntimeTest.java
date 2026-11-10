package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.audio.LocalAudioPort;
import com.scenevibe.tvcompanionpoc.calendar.MediaObservation;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Reconcile native-panel positions through the principal calendar and both
 * renderers, with recording effect ports and immutable synthetic timestamps.
 */
public final class CompanionTrackRuntimeTest {
    private StudioMultimediaRuntime runtime;
    private final Port audio=new Port();
    private final List<String> texts=new ArrayList<>();
    private long capture=100000;
    /** Use the actual principal replay contract, without a Cloud admission or Android shortcut. */
    @Before public void before() throws Exception {
        runtime=AdSyncTestFixture.runtime(new SceneRuntimeController.SceneSink(){
            /** Accept only the supplied payload-free scenes. */ @Override public boolean preflight(OverlayManifest.Scene scene){return true;}
            /** Record actual TEXT requests. */ @Override public void show(OverlayManifest.Scene scene){texts.add(scene.id);}
            /** No physical window exists in this software fixture. */ @Override public void hide(OverlayManifest.Scene scene){}
            /** Preserve the real regie's teardown calls. */ @Override public void hideAll(){}
        },audio,true);
    }
    /** Retire the one runtime and recording player. */
    @After public void after(){runtime.close();}
    /** Feed an explicit current MEDIA observation, never a generated Track clock. */
    private void at(long position,boolean playing){capture+=250;runtime.observe("synthetic-activation",new MediaObservation(true,position,playing),"owned",capture,playing?1f:0f);}
    /** Forward recalage never catches up all expired comments in a burst. */
    @Test public void forwardRecalageSkipsExpiredEvents(){at(0,true);runtime.suspendComments();runtime.policyChanged();at(13000,true);assertTrue(texts.isEmpty());assertEquals(0,audio.starts);}
    /** Recalage preserves future TEXT and AUDIO in the original calendar. */
    @Test public void futureEventsRemainScheduled(){at(0,true);runtime.suspendComments();runtime.policyChanged();at(750,true);at(1000,true);at(1750,true);at(2000,true);assertEquals(List.of("text1"),texts);assertEquals(1,audio.starts);}
    /** A genuine explicit rewind into AUDIO restarts only the matching native window at its offset. */
    @Test public void backwardRecalageInsideAudioHasOneOffsetPlayer(){at(1750,true);at(2000,true);assertEquals(1,audio.starts);at(2500,true);runtime.suspendComments();runtime.confirmSeek();at(2250,true);assertEquals(2,audio.starts);assertEquals(250,audio.offset);assertEquals(LocalAudioPort.State.STARTED,audio.state());at(2500,true);assertEquals(2,audio.starts);}
    /** A committed paused position emits no speech until an eligible PLAYING observation. */
    @Test public void recalageWithoutResumeDoesNotStartAudio(){at(0,true);runtime.suspendComments();runtime.confirmSeek();at(2250,false);assertEquals(0,audio.starts);at(2250,true);at(2500,true);assertEquals(1,audio.starts);assertEquals(500,audio.offset);}
    /** Eligibility loss retains all replay protections and suppresses both effect types. */
    @Test public void invalidMediaCannotReleaseTheCalendar(){at(0,true);runtime.suspendComments();runtime.observe("synthetic-activation",new MediaObservation(false,2000,true),"owned",100500,1f);assertEquals(0,audio.starts);assertTrue(texts.isEmpty());}
    /** A policy-only forward correction cannot replay an already consumed MP3. */
    @Test public void nonBackwardCorrectionDoesNotReplayConsumedAudio(){at(1750,true);at(2000,true);runtime.suspendComments();runtime.policyChanged();at(2250,true);at(2500,true);assertEquals(1,audio.starts);}
    /** Native-offset recording seam with no audio-focus or video-transport API. */
    private static final class Port implements LocalAudioPort {
        int starts;long offset;State state=State.IDLE;
        /** Prepare silently. */ @Override public void prepare(String id){state=State.PREPARED;offset=0;}
        /** Record one actual renderer start. */ @Override public void start(){starts++;state=State.STARTED;}
        /** Retire the old window before a seek. */ @Override public void stop(){state=State.IDLE;}
        /** Release only this local effect. */ @Override public void release(){state=State.IDLE;}
        /** Keep gain local to the fixture. */ @Override public void setGain(float gain){}
        /** Report the recording lifecycle. */ @Override public State state(){return state;}
        /** Advertise the principal MP3 offset contract. */ @Override public boolean supportsNativeOffsets(){return true;}
        /** Freeze only this local player. */ @Override public void pause(){state=State.PAUSED;}
        /** Resume only this local player. */ @Override public void resume(){state=State.STARTED;}
        /** Record the scheduler's native offset. */ @Override public void seekTo(long offset){this.offset=offset;}
    }
}
