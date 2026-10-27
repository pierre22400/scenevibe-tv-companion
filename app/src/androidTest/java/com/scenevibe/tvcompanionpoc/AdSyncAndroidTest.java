package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.content.Intent;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.scenevibe.tvcompanionpoc.audio.AndroidLocalAudioPort;
import com.scenevibe.tvcompanionpoc.audio.LocalAudioPort;
import com.scenevibe.tvcompanionpoc.calendar.MediaObservation;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Native Android TV fixture: own public MediaSessions, actual Settings widgets
 * and the existing local MP3 decoder. No Prime, account, Cloud or ad is observed.
 * Synthetic fixture admission is test-only and never a native Cloud permit.
 */
@RunWith(AndroidJUnit4.class)
public final class AdSyncAndroidTest {
    /** Run Android UI/player effects on the application's actual main Looper. */
    private void main(Runnable work){InstrumentationRegistry.getInstrumentation().runOnMainSync(work);}
    /** Copy public scalar facts from this test's OWN MediaSession into the real guard. */
    private AdSyncController.Sample sample(MediaSession session){PlaybackState p=session.getController().getPlaybackState();long now=SystemClock.elapsedRealtime();
        return new AdSyncController.Sample(session.getSessionToken(),true,p.getState()==PlaybackState.STATE_PLAYING,
                p.getPosition(),p.getPosition(),p.getLastPositionUpdateTime(),now,p.getPlaybackSpeed(),100000);}
    /** Public playback anchors are observable, but carry no verified third-party ad flag. */
    @Test public void ownPublicMediaSessionSupportsManualHold(){var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        main(()->{MediaSession session=new MediaSession(context,"SceneVibe OWN AD-SYNC fixture");
        try{session.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,1000,1f,SystemClock.elapsedRealtime()).build());
            AdSyncController c=new AdSyncController();c.setEnabled(true);assertTrue(c.observe(sample(session)).allow);c.suspend();assertFalse(c.observe(sample(session)).allow);
            assertEquals(AdSyncController.State.SUSPENDED,c.status().state);
        }finally{session.release();}}); }
    /** An actual replacement Android token cannot inherit the accepted clock silently. */
    @Test public void nativeSessionReplacementIsUncertain(){var context=InstrumentationRegistry.getInstrumentation().getTargetContext();main(()->{MediaSession a=new MediaSession(context,"OWN A"),b=new MediaSession(context,"OWN B");
        try{PlaybackState p=new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,1000,1f,SystemClock.elapsedRealtime()).build();a.setPlaybackState(p);b.setPlaybackState(p);
            AdSyncController c=new AdSyncController();c.setEnabled(true);assertTrue(c.observe(sample(a)).allow);assertFalse(c.observe(sample(b)).allow);assertEquals(AdSyncController.Reason.SESSION_CHANGED,c.status().reason);
        }finally{a.release();b.release();}}); }
    /** Find only SceneVibe's own remote-focusable widgets; no third-party tree is examined. */
    private Button button(View root,String prefix){if(root instanceof Button && ((Button)root).getText().toString().startsWith(prefix))return (Button)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){Button b=button(((ViewGroup)root).getChildAt(i),prefix);if(b!=null)return b;}return null;}
    /** Actual Activity navigation and opt-in cannot start comments without existing private admission. */
    @Test public void realSettingsRequireAdmission(){var instrument=InstrumentationRegistry.getInstrumentation();var target=instrument.getTargetContext();
        assertTrue("Native test build must have no Cloud origin",BuildConfig.CLOUD_ORIGIN.isEmpty());
        Activity activity=instrument.startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try{main(()->{TvFirst009TextServiceOwner.setAdSyncEnabled(false);Button settings=button(activity.getWindow().getDecorView(),BuildConfig.TVUX_DEVELOPER_OVERLAY?"Développeur":"Réglages");assertNotNull(settings);settings.performClick();
            Button toggle=button(activity.getWindow().getDecorView(),"AD-SYNC");assertNotNull(toggle);toggle.performClick();assertTrue(TvFirst009TextServiceOwner.adSyncEnabled());
            assertFalse(button(activity.getWindow().getDecorView(),"Suspendre les commentaires").isEnabled());assertFalse(TvFirst009TextServiceOwner.multimediaActive());});
        }finally{main(()->{TvFirst009TextServiceOwner.setAdSyncEnabled(false);activity.finish();});}}
    /** Observe native player state through the main thread until a bounded condition is satisfied. */
    private void awaitState(AndroidLocalAudioPort port,LocalAudioPort.State expected) throws Exception {long end=SystemClock.elapsedRealtime()+10000;boolean[] ready={false};
        while(SystemClock.elapsedRealtime()<end){main(()->ready[0]=port.state()==expected);if(ready[0])return;Thread.sleep(25);}fail("Native local AUDIO state did not become "+expected);}
    /** Existing MP3 bytes decode natively, stop on interruption, and are not replayed by return within the consumed window. */
    @Test public void nativeAudioStopsWithoutReplayAndRetainsFuture() throws Exception {
        var instrument=InstrumentationRegistry.getInstrumentation();var target=instrument.getTargetContext();File file=new File(target.getCacheDir(),"ad-sync-existing-test.mp3");
        try(var input=instrument.getContext().getAssets().open("cloudbridge003e/2f940c78abae4ec0d03f920eb1416ed955b277220c8334dce839a6f4c2e6071e.mp3");
            var output=new FileOutputStream(file)) {
            // InputStream.readAllBytes is not available on the Android TV 31 runtime.
            byte[] buffer=new byte[8192];
            for(int count;(count=input.read(buffer))!=-1;)output.write(buffer,0,count);
        }
        AtomicInteger preparations=new AtomicInteger();AndroidLocalAudioPort[] port={null};StudioMultimediaRuntime[] runtime={null};TextView[] text={null};
        try{main(()->{port[0]=new AndroidLocalAudioPort(id->{preparations.incrementAndGet();return file;});text[0]=new TextView(target);
            try{runtime[0]=AdSyncTestFixture.runtime(new SceneRuntimeController.SceneSink(){
                /** This test draws only into its own Android View, without any overlay permission. */
                @Override public boolean preflight(OverlayManifest.Scene scene){return true;}
                /** Preserve the actual TEXT regie's effect on a native widget. */
                @Override public void show(OverlayManifest.Scene scene){text[0].setText(scene.id);}
                /** Clear the native fixture view on expiration. */
                @Override public void hide(OverlayManifest.Scene scene){text[0].setText("");}
                /** Clear the native fixture view immediately on suspension. */
                @Override public void hideAll(){text[0].setText("");}
            },port[0]);}catch(Exception invalid){throw new IllegalStateException(invalid);}
            runtime[0].observe("synthetic-activation",new MediaObservation(true,1000,true));assertEquals("text1",text[0].getText().toString());
            runtime[0].observe("synthetic-activation",new MediaObservation(true,2000,true));});
            awaitState(port[0],LocalAudioPort.State.STARTED);
            main(()->{runtime[0].suspendComments();assertEquals(LocalAudioPort.State.IDLE,port[0].state());assertEquals("",text[0].getText().toString());
                runtime[0].observe("synthetic-activation",new MediaObservation(true,2250,true));assertEquals(LocalAudioPort.State.IDLE,port[0].state());assertEquals(1,preparations.get());
                runtime[0].observe("synthetic-activation",new MediaObservation(true,5000,true));runtime[0].observe("synthetic-activation",new MediaObservation(true,6000,true));});
            awaitState(port[0],LocalAudioPort.State.STARTED);assertEquals(2,preparations.get());
        }finally{main(()->{if(runtime[0]!=null)runtime[0].close();if(port[0]!=null)port[0].release();});file.delete();}
    }
}
