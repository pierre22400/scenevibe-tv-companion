package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.provider.Settings;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.scenevibe.tvcompanionpoc.audio.AndroidLocalAudioPort;
import com.scenevibe.tvcompanionpoc.audio.LocalAudioPort;
import com.scenevibe.tvcompanionpoc.calendar.MediaObservation;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Restitution logicielle sur Android TV avec le cache réellement téléchargé en
 * HTTPS : vrai SceneRenderer, vrais widgets overlay et vrai MediaPlayer MP3.
 * Les observations de timeline sont synthétiques et injectées au calendrier M5
 * existant ; elles ne prouvent aucune session Prime ou synchronisation Sony.
 */
@RunWith(AndroidJUnit4.class)
public final class CloudAndroid001PlaybackTest {
    private MultimediaPrivateCache.Prepared prepared;
    private StudioMultimediaRuntime runtime;
    private SceneRenderer renderer;
    private CountingAudio audio;
    private final Set<String> scenes=new HashSet<>();
    private volatile String pendingScene;
    private final String activation="android-software-qualification-"+java.util.UUID.randomUUID();

    /** Reopen the real target cache and compose the unchanged SceneVibe rendering ports on Android's UI thread. */
    @Before public void before() throws Exception {
        Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();assertTrue(Settings.canDrawOverlays(target));
        TvFirst008Source source=new TvFirst008Source(target);
        try{prepared=MultimediaAndroidBridge.restoreCache(target,source);}finally{source.close();}
        assertNotNull("Run HTTPS source qualification first",prepared);assertEquals(19,prepared.track.calendar.events().size());
        main(()->{
            renderer=new SceneRenderer(target,id->null,()->{throw new AssertionError("OVERLAY_PERMISSION_LOST");});
            audio=new CountingAudio(new AndroidLocalAudioPort(id->{try{return prepared.resource(id);}catch(Exception error){throw new java.io.IOException("VERIFIED_RESOURCE_UNAVAILABLE",error);}}));
            runtime=new StudioMultimediaRuntime(new SceneRuntimeController.SceneSink() {
                /** Keep the genuine Android renderer's exact preflight. */ @Override public boolean preflight(OverlayManifest.Scene scene){return renderer.preflight(scene);}
                /** Render original TEXT; attachment is checked after the main Looper processes WindowManager's traversal. */ @Override public void show(OverlayManifest.Scene scene){renderer.render(scene);pendingScene=scene.id;}
                /** Use the existing synchronous dismissal on event expiration. */ @Override public void hide(OverlayManifest.Scene scene){renderer.dismissNow();}
                /** Eligibility loss removes every Android window immediately. */ @Override public void hideAll(){renderer.dismissNow();}
            },audio);
            runtime.prepareFromVerifiedCloudCache(prepared);
            runtime.loadForSoftwareQualification(prepared.track,activation);
        });
    }

    /** Revoke the runtime before releasing the actual Android window and native player. */
    @After public void after(){main(()->{if(runtime!=null)runtime.close();if(renderer!=null)renderer.dismissNow();if(audio!=null)audio.release();});}

    /** Execute the real main-Looper ownership boundary rather than a Robolectric shadow. */
    private static void main(Runnable work){InstrumentationRegistry.getInstrumentation().runOnMainSync(work);}

    /** Supply one explicitly synthetic selected content-time observation to the single M5 calendar. */
    private void observe(long position,boolean playing){
        main(()->runtime.observe(activation,new MediaObservation(true,position,playing)));
        if(pendingScene!=null)awaitWindow();
    }

    /** Prove actual attachment after Android's asynchronous addView traversal, without blocking its UI thread. */
    private void awaitWindow(){
        String scene=pendingScene;long end=android.os.SystemClock.elapsedRealtime()+30000;
        while(android.os.SystemClock.elapsedRealtime()<end){
            java.util.concurrent.atomic.AtomicBoolean attached=new java.util.concurrent.atomic.AtomicBoolean();
            main(()->{
                try{var field=SceneRenderer.class.getDeclaredField("root");field.setAccessible(true);var view=(android.view.View)field.get(renderer);attached.set(view!=null && view.isAttachedToWindow());}
                catch(ReflectiveOperationException error){throw new AssertionError(error);}
            });
            if(attached.get()){scenes.add(scene);pendingScene=null;return;}
            android.os.SystemClock.sleep(20);
        }
        fail("Native TEXT window never attached");
    }

    /** Model continuous playback without turning a sparse fixture jump into M5's deliberate forward-SEEK policy. */
    private void advance(long from,long to){
        long position=from;
        while(to-position>5000){position+=5000;observe(position,true);}
        observe(to,true);
    }

    /** Await asynchronous native seek completion and start without blocking Android's main Looper. */
    private void state(LocalAudioPort.State expected) throws Exception {
        long end=android.os.SystemClock.elapsedRealtime()+30000;
        while(android.os.SystemClock.elapsedRealtime()<end){var actual=new java.util.concurrent.atomic.AtomicReference<LocalAudioPort.State>();main(()->actual.set(audio.state()));if(actual.get()==expected)return;Thread.sleep(20);}
        fail("Native player did not reach "+expected);
    }

    /** Dispatch all nineteen original events; each MP3 reaches Android STARTED and every TEXT window is attached. */
    @Test public void rendersSixteenTextsAndStartsThreeNativeMp3() throws Exception {
        observe(0,true);
        long previous=0;
        for(var event:prepared.track.calendar.events()) {
            advance(previous,event.startMs());previous=event.startMs();
            if(prepared.track.audio.containsKey(event.eventId())){state(LocalAudioPort.State.STARTED);observe(event.startMs(),true);}
        }
        assertEquals(16,scenes.size());assertEquals(3,audio.starts);assertEquals(3,audio.resources.size());
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(250);
        android.graphics.Bitmap screenshot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try(java.io.FileOutputStream image=new java.io.FileOutputStream(new File(context.getFilesDir(),"cloudandroid001-text-render.png"))) {
            assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,image));
        }finally{screenshot.recycle();}
        java.nio.file.Files.write(new File(context.getFilesDir(),"cloudandroid001-playback-proof.json").toPath(),new JSONObject()
                .put("textWindowsAttached",scenes.size()).put("mp3Starts",audio.starts).put("logicalAudioResources",audio.resources.size())
                .put("timelineObservations","SYNTHETIC").put("nativeMediaPlayer",true).put("nativeSceneRenderer",true).put("primeTested",false)
                .toString().getBytes(StandardCharsets.UTF_8));
    }

    /** PAUSE freezes the current native MP3 and RESUME keeps its resource without a second start request. */
    @Test public void pauseResumeKeepsOneNativeResource() throws Exception {
        observe(90000,true);state(LocalAudioPort.State.STARTED);observe(90500,false);state(LocalAudioPort.State.PAUSED);
        observe(90500,true);state(LocalAudioPort.State.STARTED);assertEquals(1,audio.starts);assertEquals(1,audio.prepares);assertEquals(1,audio.resumes);
    }

    /** A forward seek out of an audio window stops its native player immediately. */
    @Test public void forwardSeekStopsExpiredAudio() throws Exception {
        observe(90000,true);state(LocalAudioPort.State.STARTED);observe(120000,true);state(LocalAudioPort.State.IDLE);assertEquals(1,audio.starts);
    }

    /** A backward seek before the cue rearms the same logical audio exactly once. */
    @Test public void backwardSeekRearmsWithoutDuplicateSnapshots() throws Exception {
        observe(90000,true);state(LocalAudioPort.State.STARTED);observe(91000,true);observe(88000,true);observe(89000,true);observe(90000,true);
        state(LocalAudioPort.State.STARTED);observe(90000,true);assertEquals(2,audio.starts);assertEquals(2,audio.prepares);
    }

    /** TEXT OFF suppresses visual windows while the same calendar still starts AUDIO. */
    @Test public void textOffKeepsAudioIndependent() throws Exception {
        main(()->runtime.setTextEnabled(false));observe(32988,true);observe(90000,true);state(LocalAudioPort.State.STARTED);assertEquals(0,scenes.size());assertEquals(1,audio.starts);
    }

    /** AUDIO OFF releases native sound immediately while subsequent TEXT windows remain renderable. */
    @Test public void audioOffKeepsTextIndependent() throws Exception {
        observe(90000,true);state(LocalAudioPort.State.STARTED);main(()->runtime.setAudioEnabled(false));state(LocalAudioPort.State.IDLE);
        advance(90000,105532);assertEquals(1,scenes.size());assertEquals(1,audio.starts);
    }

    /** Loss of a selected session removes sound and display; the former activation cannot leak into a replacement. */
    @Test public void interruptionAndStaleActivationStopNativePlayback() throws Exception {
        observe(90000,true);state(LocalAudioPort.State.STARTED);main(()->runtime.unavailable());state(LocalAudioPort.State.IDLE);
        main(()->runtime.loadForSoftwareQualification(prepared.track,"new-activation"));observe(300000,true);state(LocalAudioPort.State.IDLE);assertEquals(1,audio.starts);
    }

    /** The transport's cache receipt never changes the UNKNOWN edition/mapping admission result. */
    @Test public void downloadDoesNotInventPrimeAdmission(){
        main(()->{boolean refused=false;try{runtime.requireNativeIdentityEvidence();}catch(IllegalStateException expected){refused=true;}assertTrue(refused);});
    }

    /** Count orchestration requests while delegating every real operation to Android's existing MediaPlayer port. */
    private static final class CountingAudio implements LocalAudioPort {
        final AndroidLocalAudioPort delegate;final Set<String> resources=new HashSet<>();int starts,prepares,resumes;
        /** Own only a wrapper for test observations, never a second player. */ CountingAudio(AndroidLocalAudioPort delegate){this.delegate=delegate;}
        /** Decode and prepare the actual verified file. */ @Override public void prepare(String id){prepares++;resources.add(id);delegate.prepare(id);assertNotEquals(State.ERROR,delegate.state());}
        /** Forward native start, recording its request separately from STARTED evidence. */ @Override public void start(){starts++;delegate.start();}
        /** Stop the single local native player. */ @Override public void stop(){delegate.stop();}
        /** Release that player idempotently. */ @Override public void release(){delegate.release();}
        /** Preserve local gain without changing system volume. */ @Override public void setGain(float gain){delegate.setGain(gain);}
        /** Return the true Android adapter state. */ @Override public State state(){return delegate.state();}
        /** Preserve native seek/freeze capability. */ @Override public boolean supportsNativeOffsets(){return delegate.supportsNativeOffsets();}
        /** Pause SceneVibe only. */ @Override public void pause(){delegate.pause();}
        /** Resume the same native player. */ @Override public void resume(){resumes++;delegate.resume();}
        /** Apply the calendar's exact content-time offset. */ @Override public void seekTo(long offset){delegate.seekTo(offset);}
    }
}
