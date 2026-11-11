package com.scenevibe.tvcompanionpoc;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;
import static org.junit.Assert.*;

/**
 * Exercise the real principal Track clock, calendar, overlay and MP3 renderer
 * through framework-delivered shortcut callbacks and native D-pad input.
 * Only the emulator, owned MEDIA session and synthetic private/public packages
 * participate. These observations are not physical Sony MUTE or Prime evidence.
 */
final class CompanionMuteNativeChecks {
    /** Qualify cancellation, both commit policies, TEXT and native AUDIO after actual shortcut editing. */
    static void exercise(Instrumentation instrument,MediaSession media,String scope) throws Exception {
        var target=instrument.getTargetContext();
        DeveloperViewerFixtureActivity viewer=(DeveloperViewerFixtureActivity)instrument.startActivitySync(
                new Intent(target,DeveloperViewerFixtureActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try(var bench=new CompanionShortcutBench(instrument)){
            instrument.runOnMainSync(()->{
                TvFirst009TextServiceOwner.setDeveloperMode(target,false);
                TvFirst009TextServiceOwner.setAdSyncEnabled(false);
                TvFirst009TextServiceOwner.setMultimediaTextEnabled(true);
                TvFirst009TextServiceOwner.setMultimediaAudioEnabled(true);
                playback(media,PlaybackState.STATE_PAUSED);
            });
            bench.await(viewer::hasWindowFocus,"Owned viewer did not gain initial focus",10000);
            bench.await(()->TvFirst009TextServiceOwner.multimediaTrackPosition()==30000,"Initial real clock missing",10000);
            int accepted=CompanionShortcutService.diagnostics().accepted;
            open(bench);bench.remote.pressDPadDown();bench.remote.pressDPadCenter();
            bench.remote.pressDPadLeft();assertEquals(30000,position(instrument));
            bench.remote.pressDPadCenter();closed(bench,viewer);
            assertEquals(20000,position(instrument));Thread.sleep(500);assertEquals(20000,position(instrument));
            assertTrue(TvFirst009TextServiceOwner.multimediaManuallySuspended());

            open(bench);bench.remote.pressDPadCenter();bench.remote.pressDPadRight();
            assertEquals(20000,position(instrument));bench.remote.pressBack();closed(bench,viewer);
            assertEquals(20000,position(instrument));assertTrue(TvFirst009TextServiceOwner.multimediaManuallySuspended());

            open(bench);bench.remote.pressDPadCenter();bench.remote.pressDPadUp();
            assertEquals(20000,position(instrument));bench.remote.pressDPadCenter();closed(bench,viewer);
            assertEquals(80000,position(instrument));assertFalse(TvFirst009TextServiceOwner.multimediaManuallySuspended());
            assertEquals(PlaybackState.STATE_PAUSED,media.getController().getPlaybackState().getState());
            assertEquals(30000,media.getController().getPlaybackState().getPosition());

            // Actual native MP3 playback after a shortcut-driven backward recalage.
            open(bench);bench.remote.pressDPadCenter();bench.remote.pressDPadDown();bench.remote.pressDPadRight();
            longOk(instrument);bench.remote.pressDPadRight();assertEquals(80000,position(instrument));
            int audioBefore=TvFirst009TextServiceOwner.multimediaAudioPlaybackStarts();
            bench.remote.pressDPadCenter();closed(bench,viewer);assertEquals(31000,position(instrument));
            instrument.runOnMainSync(()->playback(media,PlaybackState.STATE_PLAYING));
            bench.await(()->TvFirst009TextServiceOwner.multimediaAudioPlaybackStarts()>audioBefore,"No native AUDIO after shortcut recalage",12000);
            Thread.sleep(300);assertEquals(audioBefore+1,TvFirst009TextServiceOwner.multimediaAudioPlaybackStarts());

            // The owned viewer pauses itself; the Companion never sends any transport command.
            instrument.runOnMainSync(()->playback(media,PlaybackState.STATE_PAUSED));
            bench.await(()->TvFirst009TextServiceOwner.multimediaTrackPosition()==31000,"Paused projection was not refreshed",10000);
            open(bench);bench.remote.pressDPadCenter();bench.remote.pressDPadLeft();
            assertEquals(31000,position(instrument));bench.remote.pressDPadCenter();closed(bench,viewer);
            assertEquals(21000,position(instrument));
            instrument.runOnMainSync(()->playback(media,PlaybackState.STATE_PLAYING));
            bench.await(TvFirst009TextServiceOwner::multimediaTextAttached,"No native TEXT window after shortcut recalage",12000);
            assertEquals(accepted+5,CompanionShortcutService.diagnostics().accepted);
            assertEquals(0,viewer.backCount);assertEquals(0,viewer.transportKeys);
            assertFalse(TvFirst009TextServiceOwner.adSyncEnabled());
            JSONObject proof=new JSONObject().put("scope",scope).put("systemTrigger","ANDROID_TV_BACK_DOWN_CHORD")
                    .put("frameworkCallbacks",5).put("directCallbackInjection",false).put("notificationUsed",false)
                    .put("realCompanionClock",true).put("candidateDetached",true).put("cancelPreservesClock",true)
                    .put("withoutResumePositionMs",20000).put("withResumePositionMs",80000)
                    .put("nativeAudioAfterRecalage",true).put("nativeTextAfterRecalage",true)
                    .put("videoTransportCommands",0).put("focusReturned",true).put("sonyMute","NOT_EXECUTED");
            Files.write(new File(target.getFilesDir(),"companion-mute002-"+scope+"-proof.json").toPath(),proof.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("MUTE002_RECALAGE_PASS; "+scope+"; SYSTEM_CALLBACK; REAL_CLOCK; TEXT_AUDIO; NOT_SONY_MUTE");
        }finally{instrument.runOnMainSync(()->{TvFirst009TextServiceOwner.closeDeveloperPanel();viewer.finish();});}
    }

    /** Read the real shared projection on its owning main Looper. */
    private static long position(Instrumentation instrument){
        long[] value={-1};instrument.runOnMainSync(()->value[0]=TvFirst009TextServiceOwner.multimediaTrackPosition());return value[0];
    }
    /** Ask Android policy for the configured callback and require actual window focus. */
    private static void open(CompanionShortcutBench bench) throws Exception {
        bench.systemShortcut();bench.await(TvFirst009TextServiceOwner::developerHasFocus,"Shortcut recalage window has no focus",10000);
    }
    /** Require removal and input restoration after either commit or cancellation. */
    private static void closed(CompanionShortcutBench bench,DeveloperViewerFixtureActivity viewer) throws Exception {
        bench.await(viewer::hasWindowFocus,"Recalage did not return focus",10000);assertFalse(TvFirst009TextServiceOwner.developerHasFocus());
    }
    /** Supply an actual held CENTER pair to the native window, rather than calling a View handler. */
    private static void longOk(Instrumentation instrument) throws Exception {
        UiAutomation automation=instrument.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        long down=SystemClock.uptimeMillis();
        assertTrue(automation.injectInputEvent(key(down,KeyEvent.ACTION_DOWN),true));
        Thread.sleep(800);assertTrue(automation.injectInputEvent(key(down,KeyEvent.ACTION_UP),true));
    }
    /** Use only public emulator input injection; production never synthesizes keys. */
    private static KeyEvent key(long down,int action){
        return new KeyEvent(down,SystemClock.uptimeMillis(),action,KeyEvent.KEYCODE_DPAD_CENTER,0,0,KeyCharacterMap.VIRTUAL_KEYBOARD,0,0,InputDevice.SOURCE_DPAD);
    }
    /** Publish facts from the owned fixture player without issuing controller transport commands. */
    private static void playback(MediaSession media,int state){
        media.setPlaybackState(new PlaybackState.Builder().setState(state,30000,state==PlaybackState.STATE_PLAYING?1f:0f,SystemClock.elapsedRealtime()).build());
    }
    /** Prevent an independent runtime or clock from being created by the test helper. */
    private CompanionMuteNativeChecks(){}
}
