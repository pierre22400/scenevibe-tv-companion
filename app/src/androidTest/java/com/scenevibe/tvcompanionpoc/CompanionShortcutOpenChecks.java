package com.scenevibe.tvcompanionpoc;

import android.app.Instrumentation;
import android.content.Intent;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import org.json.JSONObject;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.File;
import static org.junit.Assert.*;

/**
 * P0 end-to-end window reception over an already admitted real private cache.
 * The system action reaches the actual callback while an OWN viewer remains
 * foreground. No notification PendingIntent or direct callback is invoked.
 */
final class CompanionShortcutOpenChecks {
    /** Require actual shortcut admission, one attached window, D-pad input and focus return. */
    static void exercise(Instrumentation instrument,MediaSession media) throws Exception {
        var target=instrument.getTargetContext();
        DeveloperViewerFixtureActivity viewer=(DeveloperViewerFixtureActivity)instrument.startActivitySync(
                new Intent(target,DeveloperViewerFixtureActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try(var bench=new CompanionShortcutBench(instrument)){
            instrument.runOnMainSync(()->{
                TvFirst009TextServiceOwner.setDeveloperMode(target,false);
                media.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED,30000,0f,SystemClock.elapsedRealtime()).build());
            });
            bench.await(viewer::hasWindowFocus,"Owned viewer has no input before shortcut",10000);
            bench.await(()->TvFirst009TextServiceOwner.status().positionMs==30000,"Fresh admitted MEDIA position missing",10000);
            int accepted=CompanionShortcutService.diagnostics().accepted;
            bench.systemShortcut();
            bench.await(TvFirst009TextServiceOwner::developerHasFocus,"Actual shortcut did not attach a focusable principal overlay",10000);
            assertEquals(accepted+1,CompanionShortcutService.diagnostics().accepted);
            Object window=TvFirst009TextServiceOwner.developerWindowIdentity();assertNotNull(window);
            int duplicates=CompanionShortcutService.diagnostics().duplicates;
            bench.systemShortcut();
            assertEquals(duplicates+1,CompanionShortcutService.diagnostics().duplicates);
            assertEquals(window,TvFirst009TextServiceOwner.developerWindowIdentity());
            int keys=TvFirst009TextServiceOwner.developerReceivedKeys();bench.remote.pressDPadDown();
            bench.await(()->TvFirst009TextServiceOwner.developerReceivedKeys()>keys,"Panel did not receive actual D-pad input",5000);
            bench.remote.pressBack();
            bench.await(viewer::hasWindowFocus,"Closing shortcut window did not return input to owned viewer",10000);
            assertFalse(TvFirst009TextServiceOwner.developerHasFocus());
            assertEquals(0,viewer.backCount);assertEquals(0,viewer.transportKeys);
            assertEquals(30000,media.getController().getPlaybackState().getPosition());
            assertEquals(PlaybackState.STATE_PAUSED,media.getController().getPlaybackState().getState());
            JSONObject proof=new JSONObject().put("scope","REAL_ANDROID_SHORTCUT_OWN_VIEWER_NOT_SONY_PRIME")
                    .put("systemGlobalAction",13).put("directCallbackInjection",false).put("notificationUsed",false)
                    .put("actualBinding",true).put("actualCallback",true).put("windowAttached",true).put("inputFocus",true)
                    .put("duplicateWindow",false).put("dpadReceived",true).put("focusReturned",true).put("videoTransportCommands",0);
            Files.write(new File(target.getFilesDir(),"companion-mute002-p0-open.json").toPath(),proof.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("MUTE002_P0_OPEN_PASS; SYSTEM_CALLBACK; ADMITTED_TRACK; SAME_WINDOW; DPAD; FOCUS_RETURN; NOT_SONY_MUTE");
        }finally{instrument.runOnMainSync(()->{TvFirst009TextServiceOwner.closeDeveloperPanel();viewer.finish();});}
    }
    /** This fixture owns no persistent runtime or independent clock. */
    private CompanionShortcutOpenChecks(){}
}
