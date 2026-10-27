package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Native TV-UX window and D-pad qualification on a disposable Android TV.
 * The real notification service route and real WindowManager are exercised over
 * an OWN foreground fixture. Active command tests explicitly use a recording UI
 * port, not a manufactured Cloud admission. No Prime/Sony invocation is claimed.
 */
@RunWith(AndroidJUnit4.class)
public final class TvUxAndroidTest {
    private Context target;
    private UiDevice remote;
    private TvUxForegroundFixtureActivity foreground;
    private CompanionControlPanel direct;
    /** Launch another owned foreground Activity, leaving MainActivity out of the invocation path. */
    @Before public void before() throws Exception {
        assertTrue(BuildConfig.DEBUG && BuildConfig.TVUX_CONTROL_PANEL);assertTrue(BuildConfig.CLOUD_ORIGIN.isEmpty());
        target=InstrumentationRegistry.getInstrumentation().getTargetContext();remote=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        assertEquals(android.content.res.Configuration.UI_MODE_TYPE_TELEVISION,target.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_TYPE_MASK);
        var display=target.getResources().getDisplayMetrics();assertTrue("TV qualification requires a landscape TV profile",display.widthPixels>display.heightPixels);
        remote.executeShellCommand("appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
        foreground=(TvUxForegroundFixtureActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(
                new Intent(target,TvUxForegroundFixtureActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .putExtra("panel",TvFirst009TextServiceOwner.panelIntent(target)));
        await(()->foreground.hasWindowFocus(),"Owned foreground did not acquire focus");
        remote.pressDPadUp();remote.waitForIdle();focus("Action notification SceneVibe",false);
    }
    /** Close owned windows, stop only SceneVibe's test service and finish its foreground fixture. */
    @After public void after() throws Exception {
        main(()->{if(direct!=null)direct.close();target.stopService(new Intent(target,OverlayService.class));if(foreground!=null)foreground.finish();});
        remote.executeShellCommand("appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
    }
    /** Serialize all view and service operations onto Android's actual owner thread. */
    private void main(Runnable work){InstrumentationRegistry.getInstrumentation().runOnMainSync(work);}
    /** Bound asynchronous native focus/service checks, without fabricating a media clock. */
    private void await(BooleanSupplier check,String message) throws Exception {
        long end=SystemClock.elapsedRealtime()+10000;
        while(SystemClock.elapsedRealtime()<end){AtomicBoolean ready=new AtomicBoolean();main(()->ready.set(check.getAsBoolean()));if(ready.get())return;Thread.sleep(50);}
        fail(message);
    }
    /** Wait for a label exposed by SceneVibe's real visible control window. */
    private void visible(String text){assertNotNull(remote.wait(Until.findObject(By.text(text)),10000));}
    /** Navigate only with D-pad events until the expected visible button receives focus. */
    private void focus(String label,boolean down) {
        for(int i=0;i<25;i++){
            // The covered Activity can retain a focused View while its WINDOW has
            // no focus. Match the actual overlay label, not the first focused node.
            if(remote.hasObject(By.text(label).focused(true)))return;
            if(down)remote.pressDPadDown();else remote.pressDPadUp();remote.waitForIdle();
        }
        fail("D-pad could not focus "+label);
    }
    /** Keep active UI command tests separate from the real, unadmitted service-route test. */
    private Actions activePanel() throws Exception {
        Actions actions=new Actions();main(()->{direct=new CompanionControlPanel(target,actions);assertTrue(direct.open());});
        visible("Commentaires écrits : ON");
        remote.takeScreenshot(new File(target.getFilesDir(),"tvux-panel.png"));return actions;
    }
    /** A system-style PendingIntent opens without MainActivity; Back returns input and preserves the service. */
    @Test public void notificationRouteOverOwnForegroundReturnsFocus() throws Exception {
        remote.pressDPadCenter();visible("Commentaires écrits : ON");
        await(()->!foreground.hasWindowFocus(),"Panel did not own focus");assertTrue(foreground.resumed);
        remote.pressBack();await(()->foreground.hasWindowFocus(),"Back did not return focus");
        assertEquals(0,foreground.backCount);assertEquals(0,foreground.transportKeys);
        var manager=target.getSystemService(NotificationManager.class);assertTrue(manager.getActiveNotifications().length>0);
        assertTrue(target.getSystemService(ActivityManager.class).getRunningServices(100).stream()
                .anyMatch(service->service.service.getClassName().equals(OverlayService.class.getName())));
        assertFalse("UI invocation must not grant a multimedia permit",TvFirst009TextServiceOwner.multimediaActive());
        remote.pressBack();await(()->foreground.backCount==1,"Closed panel still captured Back");
        System.out.println("TVUX_NOTIFICATION_CAPABILITY_PASS; OWN_FOREGROUND_ONLY; SONY_ENTRY_UNPROVEN; SERVICE_PRESERVED_WITHOUT_ADMISSION");
    }
    /** Nested Back closes only the keypad; the main cross then removes all input ownership. */
    @Test public void nestedBackAndMainCrossReleaseWindow() throws Exception {
        activePanel();focus("Recaler sur le temps du film",true);remote.pressDPadCenter();visible("mm:ss ou hh:mm:ss");
        remote.pressBack();visible("Reprendre les commentaires");assertTrue(direct.isOpen());
        focus("× Fermer",false);remote.pressDPadCenter();
        await(()->!direct.isOpen() && foreground.hasWindowFocus(),"Cross left a residual window");assertEquals(0,foreground.backCount);
    }
    /** All requested controls and time entry are usable without touch, an IME or a physical keyboard. */
    @Test public void dpadTogglesSuspendResumeAndTimeEntry() throws Exception {
        Actions actions=activePanel();remote.pressDPadCenter();visible("Commentaires écrits : OFF");assertFalse(actions.text);
        remote.pressDPadDown();remote.pressDPadCenter();visible("Commentaires audio : OFF");assertFalse(actions.audio);
        remote.pressDPadDown();remote.pressDPadCenter();remote.waitForIdle();assertEquals(1,actions.suspends);
        remote.pressDPadDown();remote.pressDPadCenter();remote.waitForIdle();assertEquals(1,actions.resumes);
        remote.pressDPadDown();remote.pressDPadCenter();visible("mm:ss ou hh:mm:ss");
        remote.takeScreenshot(new File(target.getFilesDir(),"tvux-keypad.png"));
        remote.pressDPadCenter(); // 1
        remote.pressDPadDown();remote.pressDPadDown();remote.pressDPadDown();remote.pressDPadCenter(); // :
        remote.pressDPadUp();remote.pressDPadUp();remote.pressDPadUp();remote.pressDPadRight();remote.pressDPadRight();remote.pressDPadCenter(); // 3
        remote.pressDPadDown();remote.pressDPadDown();remote.pressDPadDown();remote.pressDPadLeft();remote.pressDPadCenter(); // 0
        focus("Confirmer le temps",true);remote.pressDPadCenter();
        visible("Recalage accepté ; prochaine observation MEDIA attendue.");assertEquals(90000,actions.calibrated);
        assertFalse(actions.seek);assertFalse(actions.text);assertFalse(actions.audio);
    }
    /** Application modals cancel on Back and on their visible cross, preserving the foreground route. */
    @Test public void applicationModalBackAndCrossAreNonDestructive() throws Exception {
        remote.pressDPadDown();remote.pressDPadCenter();visible("Modale SceneVibe");remote.pressBack();
        await(()->foreground.hasWindowFocus(),"Dialog Back did not restore focus");assertEquals(0,foreground.backCount);assertEquals(0,foreground.accepted);
        remote.pressDPadCenter();visible("Modale SceneVibe");focus("× Fermer",false);remote.pressDPadCenter();
        await(()->foreground.hasWindowFocus(),"Dialog cross did not restore focus");assertEquals(0,foreground.accepted);
    }
    /** Android permission loss removes the focusable window rather than leaving an invisible key sink. */
    @Test public void revokedPermissionRemovesWindow() throws Exception {
        activePanel();remote.executeShellCommand("appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW deny");
        await(()->!direct.isOpen() && foreground.hasWindowFocus(),"Permission loss retained a control window");
    }

    /** Read the actual public probe across modeled session loss/navigation and an open control overlay. */
    @Test public void currentPublicProbeResumesAcrossOwnedNavigationAndOverlay() throws Exception {
        remote.executeShellCommand("cmd notification allow_listener "+NotificationAccess.component(target).flattenToString());
        await(()->NotificationAccess.isGranted(target),"Existing MEDIA listener authorization not granted on the disposable emulator");
        MediaSession[] clock={null};MediaSessionProbe[] probe={null};AdSyncController controller=new AdSyncController();
        AtomicInteger captures=new AtomicInteger();
        try {
            main(()->{
                clock[0]=new MediaSession(target,"SceneVibe owned TV-UX clock");
                clock[0].setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"tvux-owned-clock")
                        .putString(MediaMetadata.METADATA_KEY_TITLE,"SceneVibe OWN timing fixture").putLong(MediaMetadata.METADATA_KEY_DURATION,100000).build());
                clock[0].setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,1000,1f,SystemClock.elapsedRealtime()-10000).build());
                clock[0].setActive(true);controller.setEnabled(true);
                probe[0]=new MediaSessionProbe(target,new MediaSessionProbe.Listener(){
                    /** Consume only this test's own, currently queried public identity and clock. */
                    @Override public void onSnapshot(MediaSessionProbe.Snapshot value){
                        boolean own="tvux-owned-clock".equals(value.mediaId);
                        if(own)captures.incrementAndGet();
                        controller.observe(new AdSyncController.Sample(value.sessionKey,own,value.state==PlaybackState.STATE_PLAYING,
                                value.positionMs,value.estimatedPositionMs,value.updatedAtMs,value.capturedAtMs,value.speed,value.durationMs,own),SystemClock.elapsedRealtime());
                    }
                    /** Model unavailable MEDIA honestly, without reusing its prior position. */
                    @Override public void onUnavailable(){controller.unavailable();}
                },null,250,value->"tvux-owned-clock".equals(value.mediaId));
                probe[0].start();
            });
            await(()->captures.get()>0,"Public probe did not query owned MEDIA");
            main(()->{controller.requestResume();probe[0].captureNow();});
            await(()->controller.status().allow,"Two current public observations did not confirm startup");
            main(()->{controller.suspend();clock[0].setActive(false);probe[0].captureNow();});
            Activity application=InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            try{main(()->{probe[0].captureNow();assertEquals(AdSyncController.Calibration.SESSION_ABSENT,
                    controller.calibrateResult(15000,SystemClock.elapsedRealtime()));});}finally{main(application::finish);}
            await(()->foreground.hasWindowFocus(),"Owned viewer did not return after app navigation");
            int prior=captures.get();main(()->clock[0].setActive(true));await(()->captures.get()>prior,"Owned MEDIA did not return");
            main(()->assertFalse("Returning alone must not release the hold",controller.status().allow));
            activePanel();main(()->{controller.requestResume();probe[0].captureNow();});
            await(()->controller.status().allow,"Fresh public captures did not confirm resume while overlay open");
            main(()->{controller.suspend();probe[0].captureNow();assertEquals(AdSyncController.Calibration.APPLIED,
                    controller.calibrateResult(15000,SystemClock.elapsedRealtime()));direct.close();});
            await(()->foreground.hasWindowFocus(),"Public-clock test left a focused overlay");
            System.out.println("TVUX_OWN_PUBLIC_PROBE_PASS; MODELED_NAVIGATION_LOSS_REFUSED; OVERLAY_RESUME_AND_CALIBRATION; NO_PRIME_CLAIM");
        } finally {
            main(()->{if(probe[0]!=null)probe[0].stop();if(clock[0]!=null)clock[0].release();});
            remote.executeShellCommand("cmd notification disallow_listener "+NotificationAccess.component(target).flattenToString());
        }
    }

    /** Recording command port: native focus tests do not manufacture a verified Cloud activation. */
    private static final class Actions implements CompanionControlPanel.Commands {
        volatile boolean text=true,audio=true,seek;volatile int suspends,resumes;volatile long calibrated=-1;
        /** Return only the owned test state and an explicitly waiting MEDIA status. */
        @Override public CompanionControlPanel.Snapshot snapshot(){return new CompanionControlPanel.Snapshot(true,text,audio,"Confirmation MEDIA attendue");}
        /** Reflect the requested TEXT toggle. */
        @Override public void text(boolean enabled){text=enabled;}
        /** Reflect the requested AUDIO toggle independently. */
        @Override public void audio(boolean enabled){audio=enabled;}
        /** Record suspension intent without touching a platform player. */
        @Override public void suspend(){suspends++;}
        /** Record resumption intent without claiming a real film anchor. */
        @Override public void resume(){resumes++;}
        /** Let the actual D-pad form produce a parsed local time. */
        @Override public AdSyncController.Calibration calibrate(long time,boolean seek){calibrated=time;this.seek=seek;return AdSyncController.Calibration.APPLIED;}
    }
}
