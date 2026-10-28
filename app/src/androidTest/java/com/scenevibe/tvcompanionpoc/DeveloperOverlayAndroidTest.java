package com.scenevibe.tvcompanionpoc;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.view.KeyEvent;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Native Android TV focus/lifecycle qualification on OWN Activities, no Cloud or
 * Prime. Real tab, real OverlayService and real WindowManager are used. An active
 * recording command port is explicitly limited to keypad/UI action tests; it
 * cannot issue a Cloud admission or prove Sony/Prime behavior.
 */
@RunWith(AndroidJUnit4.class)
public final class DeveloperOverlayAndroidTest {
    private Context target;
    private UiDevice remote;
    private MainActivity application;
    private DeveloperViewerFixtureActivity viewer;
    private CompanionControlPanel direct;
    /** Start the real application on an isolated landscape TV, with no provider origin. */
    @Before public void before() throws Exception {
        assertTrue(BuildConfig.DEBUG && BuildConfig.TVUX_DEVELOPER_OVERLAY);assertTrue(BuildConfig.CLOUD_ORIGIN.isEmpty());
        target=InstrumentationRegistry.getInstrumentation().getTargetContext();remote=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        assertEquals(android.content.res.Configuration.UI_MODE_TYPE_TELEVISION,target.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_TYPE_MASK);
        var display=target.getResources().getDisplayMetrics();assertTrue(display.widthPixels>display.heightPixels);
        remote.executeShellCommand("appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
        main(()->{TvFirst009TextServiceOwner.setDeveloperMode(target,false);TvFirst009TextServiceOwner.setAdSyncEnabled(false);TvFirst009TextServiceOwner.setMultimediaTextEnabled(true);TvFirst009TextServiceOwner.setMultimediaAudioEnabled(true);DeveloperViewerFixtureActivity.current=null;});
        application=(MainActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->application.hasWindowFocus(),"Normal app did not acquire input");
    }
    /** Stop only the fixture service and its UI, without a Cloud or physical-device operation. */
    @After public void after() throws Exception {
        main(()->{if(direct!=null)direct.close();TvFirst009TextServiceOwner.setDeveloperMode(target,false);
            TvFirst009TextServiceOwner.setAdSyncEnabled(false);target.stopService(new Intent(target,OverlayService.class));
            if(viewer!=null)viewer.finish();if(application!=null)application.finish();});
        remote.executeShellCommand("appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
    }
    /** Serialize native View/service observations on their actual owner thread. */
    private void main(Runnable work){InstrumentationRegistry.getInstrumentation().runOnMainSync(work);}
    /** Bound asynchronous lifecycle/focus waits without creating a media clock. */
    private void await(BooleanSupplier check,String message) throws Exception {
        long end=SystemClock.elapsedRealtime()+10000;
        while(SystemClock.elapsedRealtime()<end){AtomicBoolean ready=new AtomicBoolean();main(()->ready.set(check.getAsBoolean()));if(ready.get())return;Thread.sleep(50);}fail(message);
    }
    /** Wait for actual SceneVibe UI, never a platform third-party tree. */
    private void visible(String label){assertNotNull(label,remote.wait(Until.findObject(By.text(label)),10000));}
    /** Drive only the D-pad and match the particular visible node's focus. */
    private void focus(String label,boolean down) {
        for(int i=0;i<30;i++){if(remote.hasObject(By.text(label).focused(true)))return;
            if(down)remote.pressDPadDown();else remote.pressDPadUp();remote.waitForIdle();}fail("D-pad could not focus "+label);
    }
    /** Enter the sidebar and the real Developer page with the remote. */
    private void developerTab() {
        // Finish the first layout/focus restoration before sending directional
        // input. Normalize to the sidebar's first item instead of assuming which
        // home-page button Android chose on this emulator frame.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();remote.waitForIdle();visible("Accueil");
        remote.pressDPadLeft();remote.waitForIdle();remote.pressDPadLeft();remote.waitForIdle();
        focus("Accueil",false);focus("Développeur",true);remote.pressDPadCenter();visible("Mode Développeur : OFF");
    }
    /** Separate mode activation from preparation, checking both states in the actual service. */
    private void prepare() throws Exception {
        developerTab();focus("Mode Développeur : OFF",false);remote.pressDPadCenter();visible("Mode Développeur : ON");
        await(()->TvFirst009TextServiceOwner.developerStage()==DeveloperOverlayState.Stage.ENABLED,"Mode did not enable separately");
        focus("Afficher les commandes en overlay",true);remote.pressDPadCenter();
        await(()->TvFirst009TextServiceOwner.developerStage()==DeveloperOverlayState.Stage.PREPARED && TvFirst009TextServiceOwner.developerWindowIdentity()!=null,"Prepared window not attached");
    }
    /** Substitute only the public launch destination; onStop and service promotion remain real. */
    private void handoff() throws Exception {
        main(()->assertTrue(application.launchDeveloperDestination(new Intent(application,DeveloperViewerFixtureActivity.class),true)));
        await(()->DeveloperViewerFixtureActivity.current!=null && DeveloperViewerFixtureActivity.current.resumed,"Own viewer did not resume");
        viewer=DeveloperViewerFixtureActivity.current;
        await(()->TvFirst009TextServiceOwner.developerHasFocus(),"Service panel did not acquire input after app stopped");
    }
    /** Locate the real running service independently of its UI projection. */
    private boolean serviceRunning(){return target.getSystemService(ActivityManager.class).getRunningServices(100).stream().anyMatch(value->value.service.getClassName().equals(OverlayService.class.getName()));}
    /** Pure mode activation/preparation is not a track activation or a focus-taking timer. */
    @Test public void realDeveloperTabPreparesWithoutTakingInput() throws Exception {
        prepare();assertTrue(application.hasWindowFocus());assertFalse(TvFirst009TextServiceOwner.developerHasFocus());
        assertFalse(TvFirst009TextServiceOwner.multimediaActive());assertTrue(serviceRunning());
        remote.pressDPadUp();remote.waitForIdle();assertTrue(application.hasWindowFocus());
        remote.takeScreenshot(new File(target.getFilesDir(),"tvux002-prepared.png"));
    }
    /** A normal Activity launch promotes the SAME service window and survives source destruction. */
    @Test public void handoffSurvivesActivityAndBackReturnsPlayerKeys() throws Exception {
        prepare();Object window=TvFirst009TextServiceOwner.developerWindowIdentity();handoff();
        assertEquals(window,TvFirst009TextServiceOwner.developerWindowIdentity());assertTrue(viewer.resumed);assertFalse(application.hasWindowFocus());
        main(application::finish);await(()->application.isDestroyed(),"Source did not finish");assertTrue(TvFirst009TextServiceOwner.developerHasFocus());
        remote.takeScreenshot(new File(target.getFilesDir(),"tvux002-panel.png"));remote.pressBack();
        await(()->viewer.hasWindowFocus(),"Back did not return viewer input");assertEquals(0,viewer.backCount);
        assertEquals(DeveloperOverlayState.Stage.ENABLED,TvFirst009TextServiceOwner.developerStage());assertTrue(serviceRunning());
        assertFalse(TvFirst009TextServiceOwner.multimediaActive());remote.pressDPadDown();remote.pressBack();
        await(()->viewer.backCount==1 && viewer.remoteKeys>0,"Closed panel retained remote input");assertEquals(0,viewer.transportKeys);
        System.out.println("TVUX002_REAL_TAB_HANDOFF_PASS; SAME_SERVICE_WINDOW; SOURCE_DESTROYED; OWN_VIEWER_ONLY; NO_ADMISSION");
    }
    /** Both tab and service panel read/write one real AD-SYNC owner, with inactive effects still refused. */
    @Test public void tabAndOverlayShareTheExistingBusinessOwner() throws Exception {
        prepare();focus("AD-SYNC : OFF",true);remote.pressDPadCenter();visible("AD-SYNC : ON");assertTrue(TvFirst009TextServiceOwner.adSyncEnabled());
        handoff();visible("AD-SYNC : ON");focus("AD-SYNC : ON",true);remote.pressDPadCenter();visible("AD-SYNC : OFF");
        assertFalse(TvFirst009TextServiceOwner.adSyncEnabled());assertFalse(TvFirst009TextServiceOwner.multimediaActive());
        remote.pressBack();await(()->viewer.hasWindowFocus(),"Panel failed to close");main(viewer::finish);
        await(()->application.hasWindowFocus(),"Normal app did not return");visible("AD-SYNC : OFF");
    }
    /** A user choosing HOME/another app without the explicit launch never gets a delayed key trap. */
    @Test public void unrelatedActivityLaunchLeavesPreparationPassive() throws Exception {
        prepare();main(()->application.startActivity(new Intent(application,DeveloperViewerFixtureActivity.class)));
        await(()->DeveloperViewerFixtureActivity.current!=null && DeveloperViewerFixtureActivity.current.hasWindowFocus(),"Passive window blocked viewer navigation");
        viewer=DeveloperViewerFixtureActivity.current;assertEquals(DeveloperOverlayState.Stage.PREPARED,TvFirst009TextServiceOwner.developerStage());
        assertFalse(TvFirst009TextServiceOwner.developerHasFocus());remote.pressBack();await(()->viewer.backCount==1,"Passive window captured Back");
    }
    /** Failed/absent destinations leave the already-attached passive window and app input intact. */
    @Test public void failedLaunchDoesNotStealFocusLater() throws Exception {
        prepare();Object window=TvFirst009TextServiceOwner.developerWindowIdentity();
        main(()->assertFalse(application.launchDeveloperDestination(new Intent().setClassName(target.getPackageName(),target.getPackageName()+".AbsentOwnedDestination"))));
        assertEquals(DeveloperOverlayState.Stage.PREPARED,TvFirst009TextServiceOwner.developerStage());assertEquals(window,TvFirst009TextServiceOwner.developerWindowIdentity());assertTrue(application.hasWindowFocus());
    }
    /** Losing the existing overlay permission removes passive windows too, without disabling business preferences. */
    @Test public void revokedPermissionRemovesPreparedWindow() throws Exception {
        prepare();remote.executeShellCommand("appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW deny");
        await(()->TvFirst009TextServiceOwner.developerWindowIdentity()==null,"Permission loss retained a window");assertTrue(application.hasWindowFocus());
        assertEquals(DeveloperOverlayState.Stage.ENABLED,TvFirst009TextServiceOwner.developerStage());assertFalse(TvFirst009TextServiceOwner.multimediaActive());
    }
    /** Create active widgets only through a declared recorder, never through a counterfeit Cloud permit. */
    private Actions recordingPanel() throws Exception {
        main(()->application.startActivity(new Intent(application,DeveloperViewerFixtureActivity.class)));
        await(()->DeveloperViewerFixtureActivity.current!=null && DeveloperViewerFixtureActivity.current.hasWindowFocus(),"Own recording viewer missing");viewer=DeveloperViewerFixtureActivity.current;
        Actions actions=new Actions();main(()->{direct=new CompanionControlPanel(target,actions);assertTrue(direct.open());});visible("Commentaires écrits : ON");return actions;
    }
    /** Nested Back/cross and full cross closure are usable remotely and leave the viewer/service alone. */
    @Test public void nestedBackAndCrossCloseOnlyTheirOwnUi() throws Exception {
        Actions actions=recordingPanel();focus("Recaler sur le temps du film",true);remote.pressDPadCenter();visible("mm:ss ou hh:mm:ss");
        remote.pressBack();visible("Reprendre les commentaires");assertTrue(direct.isOpen());
        focus("Recaler sur le temps du film",true);remote.pressDPadCenter();visible("mm:ss ou hh:mm:ss");focus("× Fermer",false);remote.pressDPadCenter();visible("Reprendre les commentaires");assertTrue(direct.isOpen());
        focus("× Fermer",false);remote.pressDPadCenter();await(()->!direct.isOpen() && viewer.hasWindowFocus(),"Cross retained overlay input");
        assertTrue(actions.text && actions.audio);assertEquals(0,actions.suspends);assertEquals(0,viewer.backCount);
    }
    /** Every shared command and the numeric form work with only a TV remote; runtime semantics are qualified separately. */
    @Test public void remoteControlsAndKeypadReachTheSharedPort() throws Exception {
        Actions actions=recordingPanel();remote.takeScreenshot(new File(target.getFilesDir(),"tvux002-on-focus.png"));remote.pressDPadCenter();visible("Commentaires écrits : OFF");assertFalse(actions.text);
        remote.takeScreenshot(new File(target.getFilesDir(),"tvux002-off-focus.png"));
        remote.pressDPadDown();remote.pressDPadCenter();visible("Commentaires audio : OFF");assertFalse(actions.audio);
        remote.pressDPadDown();remote.pressDPadCenter();visible("AD-SYNC : ON");assertTrue(actions.adSync);
        remote.pressDPadDown();remote.pressDPadCenter();remote.waitForIdle();assertEquals(1,actions.suspends);
        remote.pressDPadDown();remote.pressDPadCenter();remote.waitForIdle();assertEquals(1,actions.resumes);
        remote.pressDPadDown();remote.pressDPadCenter();visible("mm:ss ou hh:mm:ss");remote.takeScreenshot(new File(target.getFilesDir(),"tvux002-keypad.png"));
        remote.pressDPadCenter();remote.pressDPadDown();remote.pressDPadDown();remote.pressDPadDown();remote.pressDPadCenter(); // 1:
        remote.pressDPadUp();remote.pressDPadUp();remote.pressDPadUp();remote.pressDPadRight();remote.pressDPadRight();remote.pressDPadCenter(); // 3
        remote.pressDPadDown();remote.pressDPadDown();remote.pressDPadDown();remote.pressDPadLeft();remote.pressDPadCenter(); // 0
        focus("Confirmer le temps",true);remote.pressDPadCenter();visible("Recalage accepté ; prochaine observation MEDIA attendue.");assertEquals(90000,actions.calibrated);assertFalse(actions.seek);
    }
    /** The real source onStop now expands passively, leaving the destination's window input intact. */
    @Test public void normalLaunchNeverTakesTheViewersFocus() throws Exception {
        prepare();Object window=TvFirst009TextServiceOwner.developerWindowIdentity();
        main(()->assertTrue(application.launchDeveloperDestination(new Intent(application,DeveloperViewerFixtureActivity.class))));
        await(()->DeveloperViewerFixtureActivity.current!=null && DeveloperViewerFixtureActivity.current.hasWindowFocus()
                && TvFirst009TextServiceOwner.developerStage()==DeveloperOverlayState.Stage.MONITORING,"Normal launch did not preserve viewer input");
        viewer=DeveloperViewerFixtureActivity.current;assertEquals(window,TvFirst009TextServiceOwner.developerWindowIdentity());assertFalse(TvFirst009TextServiceOwner.developerHasFocus());assertTrue(serviceRunning());
        main(()->viewer.simulatePlayback(true));long start=viewer.simulatedPosition();await(()->viewer.simulatedPosition()>start+300,"Owned playback stopped under passive monitor");
        assertEquals(0,viewer.focusLosses);assertFalse(viewer.episodeDetails);assertEquals(0,viewer.backCount);assertEquals(0,viewer.transportKeys);
        remote.takeScreenshot(new File(target.getFilesDir(),"tvux002-monitor.png"));main(TvFirst009TextServiceOwner::closeDeveloperPanel);
        await(()->viewer.hasWindowFocus(),"Passive close disturbed playback");assertTrue(viewer.simulatedPlaying);
    }
    /** Put a sensitive OWN playback model behind the already-prepared real service window. */
    private void playingViewerBehindBadge() throws Exception {
        prepare();main(()->application.startActivity(new Intent(application,DeveloperViewerFixtureActivity.class)));
        await(()->DeveloperViewerFixtureActivity.current!=null && DeveloperViewerFixtureActivity.current.hasWindowFocus(),"Owned playback viewer missing");
        viewer=DeveloperViewerFixtureActivity.current;main(()->viewer.simulatePlayback(true));
    }
    /** Model a ready MEDIA callback while the viewer is already playing; visibility must not steal focus. */
    @Test public void passiveExpansionPreservesASensitivePlaybackModel() throws Exception {
        playingViewerBehindBadge();Object window=TvFirst009TextServiceOwner.developerWindowIdentity();
        main(()->{String ticket=TvFirst009TextServiceOwner.beginDeveloperHandoff();assertNotNull(ticket);assertTrue(TvFirst009TextServiceOwner.completeDeveloperHandoff(ticket));});
        await(()->TvFirst009TextServiceOwner.developerStage()==DeveloperOverlayState.Stage.MONITORING,"Passive monitor not expanded");
        assertEquals(window,TvFirst009TextServiceOwner.developerWindowIdentity());assertTrue(viewer.hasWindowFocus());assertTrue(viewer.simulatedPlaying);
        assertFalse(viewer.episodeDetails);assertEquals(0,viewer.focusLosses);assertEquals(0,viewer.lifecyclePauses);assertEquals(0,viewer.backCount);assertEquals(0,viewer.transportKeys);
        System.out.println("TVUX002A_PASSIVE_EXPANSION_PASS; OWN_SENSITIVE_PLAYBACK_CONTINUES; NO_WINDOW_FOCUS_LOSS; NO_BACK_OR_TRANSPORT; NO_CLOUD_ADMISSION");
    }
    /** Characterization only: focus acquisition can disturb a sensitive viewer without any outgoing key. */
    @Test public void explicitFocusCharacterizesTheRemainingCompatibilityRisk() throws Exception {
        playingViewerBehindBadge();main(()->{String ticket=TvFirst009TextServiceOwner.beginDeveloperHandoff(true);assertNotNull(ticket);assertTrue(TvFirst009TextServiceOwner.completeDeveloperHandoff(ticket));});
        await(()->TvFirst009TextServiceOwner.developerHasFocus() && viewer.episodeDetails,"Sensitive focus model did not observe the explicit experiment");
        assertTrue(viewer.resumed);assertEquals(0,viewer.lifecyclePauses);assertEquals(1,viewer.focusLosses);assertEquals(0,viewer.backCount);assertEquals(0,viewer.transportKeys);assertTrue(serviceRunning());
        remote.pressBack();await(()->viewer.hasWindowFocus(),"Explicit experiment did not release input");assertEquals(0,viewer.backCount);assertTrue(viewer.episodeDetails);
        System.out.println("TVUX002A_EXPLICIT_FOCUS_RISK_CHARACTERIZED; OWN_MODEL_ONLY_NOT_PRIME_CAUSALITY; NO_BACK_TRANSPORT_OR_ACTIVITY_PAUSE");
    }
    /** Physical media-key events on the emulator must not fall through this interactive control window. */
    @Test public void mediaKeysAreConsumedWithoutCallingTheViewerSession() throws Exception {
        recordingPanel();AtomicInteger callbacks=new AtomicInteger();MediaSession[] clock={null};
        try{main(()->{clock[0]=new MediaSession(target,"SceneVibe OWN input fallback fixture");
            clock[0].setCallback(new MediaSession.Callback(){
                /** Count an unintended fallback PLAY into the owned session. */
                @Override public void onPlay(){callbacks.incrementAndGet();}
                /** Count an unintended fallback PAUSE into the owned session. */
                @Override public void onPause(){callbacks.incrementAndGet();}
                /** Count an unintended fallback SEEK into the owned session. */
                @Override public void onSeekTo(long position){callbacks.incrementAndGet();}
                /** Count an unintended fallback fast-forward. */
                @Override public void onFastForward(){callbacks.incrementAndGet();}
                /** Count an unintended fallback rewind. */
                @Override public void onRewind(){callbacks.incrementAndGet();}
            });clock[0].setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_FAST_FORWARD|PlaybackState.ACTION_REWIND|PlaybackState.ACTION_SEEK_TO).setState(PlaybackState.STATE_PLAYING,1000,1f,SystemClock.elapsedRealtime()).build());clock[0].setActive(true);});
            for(int key:new int[]{KeyEvent.KEYCODE_MEDIA_PLAY,KeyEvent.KEYCODE_MEDIA_PAUSE,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,KeyEvent.KEYCODE_MEDIA_REWIND,KeyEvent.KEYCODE_MEDIA_FAST_FORWARD})remote.executeShellCommand("input keyevent "+key);
            remote.waitForIdle();assertEquals(0,callbacks.get());assertEquals(0,viewer.transportKeys);assertEquals(0,viewer.backCount);assertTrue(direct.isOpen());
        }finally{main(()->{if(clock[0]!=null)clock[0].release();});}
    }
    /** Actual Android routing disproves a passive-window 000 shortcut; no counter is installed in the product. */
    @Test public void numericZerosBelongToTheViewerNotThePassivePanel() throws Exception {
        playingViewerBehindBadge();int before=TvFirst009TextServiceOwner.developerReceivedKeys();
        long start=SystemClock.elapsedRealtime();for(int i=0;i<3;i++)remote.pressKeyCode(KeyEvent.KEYCODE_0);
        long elapsed=SystemClock.elapsedRealtime()-start;await(()->viewer.numericZeros==3,"Viewer did not receive actual KEYCODE_0 events");
        remote.pressKeyCode(KeyEvent.KEYCODE_0);Thread.sleep(1100);remote.pressKeyCode(KeyEvent.KEYCODE_0);Thread.sleep(1100);remote.pressKeyCode(KeyEvent.KEYCODE_0);
        for(int i=0;i<3;i++)remote.pressKeyCode(KeyEvent.KEYCODE_NUMPAD_0);
        await(()->viewer.numericZeros==6 && viewer.numpadZeros==3,"Numeric variants not observed in OWN receiver");
        assertEquals(before,TvFirst009TextServiceOwner.developerReceivedKeys());assertFalse(TvFirst009TextServiceOwner.developerHasFocus());assertTrue(viewer.simulatedPlaying);
        System.out.println("NUMERIC_ROUTING_PROOF fastBatchMs="+elapsed+" viewerKeycode0=6 viewerNumpad0=3 passivePanelEvents=0; SONY_HARDWARE_CODES_UNTESTED");
    }
    /** An immutable notification capability requests focus without launching MainActivity; Sony menu reachability remains unproved. */
    @Test public void notificationRequestsAndConfirmsActualWindowFocus() throws Exception {
        prepare();main(()->application.startActivity(new Intent(application,DeveloperViewerFixtureActivity.class)));
        await(()->DeveloperViewerFixtureActivity.current!=null && DeveloperViewerFixtureActivity.current.hasWindowFocus(),"Owned viewer missing");viewer=DeveloperViewerFixtureActivity.current;
        main(()->{try{TvFirst009TextServiceOwner.panelIntent(target).send();}catch(Exception refused){throw new IllegalStateException(refused);}});
        await(()->"INTERACTIVE_CONFIRMED".equals(TvFirst009TextServiceOwner.developerFocusStatus()),"Visible/requested window was not confirmed by Android");
        assertFalse(application.hasWindowFocus());assertTrue(viewer.resumed);remote.pressBack();await(()->viewer.hasWindowFocus() && "CLOSED".equals(TvFirst009TextServiceOwner.developerFocusStatus()),"Focus was not released");assertEquals(0,viewer.backCount);
    }
    /** Real TCP commands use the same service state while a focus-sensitive OWN viewer keeps playing. */
    @Test public void pairedLocalCommandsDoNotStealTheViewersFocus() throws Exception {
        playingViewerBehindBadge();main(()->assertTrue(TvFirst009TextServiceOwner.setLocalControlEnabled(true)));
        String info=TvFirst009TextServiceOwner.localControlInfo();var address=java.util.regex.Pattern.compile("http://[^:]+:([0-9]+)").matcher(info);assertTrue(address.find());int port=Integer.parseInt(address.group(1));
        assertEquals(403,LocalControlTestClient.send(port,"/status",null,new org.json.JSONObject(),null).status);
        String token=LocalControlTestClient.send(port,"/pair",null,new org.json.JSONObject().put("code",LocalControlTestClient.code(info)),null).json().getString("token");
        var reply=LocalControlTestClient.send(port,"/command",token,new org.json.JSONObject().put("seq",1).put("command","text").put("value","false"),null);assertEquals(200,reply.status);assertFalse(reply.json().getBoolean("text"));
        assertFalse(TvFirst009TextServiceOwner.multimediaTextEnabled());assertTrue(TvFirst009TextServiceOwner.multimediaAudioEnabled());
        assertEquals(409,LocalControlTestClient.send(port,"/command",token,new org.json.JSONObject().put("seq",1).put("command","text").put("value","false"),null).status);
        assertEquals(200,LocalControlTestClient.send(port,"/command",token,new org.json.JSONObject().put("seq",2).put("command","panel").put("value","show"),null).status);
        assertTrue(viewer.hasWindowFocus());assertTrue(viewer.simulatedPlaying);assertEquals(0,viewer.focusLosses);
        assertEquals(200,LocalControlTestClient.send(port,"/command",token,new org.json.JSONObject().put("seq",3).put("command","panel").put("value","close"),null).status);
        assertTrue(viewer.hasWindowFocus());assertEquals(0,viewer.backCount);assertEquals(0,viewer.transportKeys);assertTrue(serviceRunning());assertFalse(TvFirst009TextServiceOwner.multimediaActive());
        main(()->{TvFirst009TextServiceOwner.setLocalControlEnabled(false);TvFirst009TextServiceOwner.setMultimediaTextEnabled(true);});assertFalse(TvFirst009TextServiceOwner.localControlEnabled());
    }
    /** Active command recorder has no MediaSession, provider identity or native playback authority. */
    private static final class Actions implements CompanionControlPanel.Commands {
        volatile boolean text=true,audio=true,adSync,seek;volatile int suspends,resumes;volatile long calibrated=-1;
        /** Project the single owned recording state for actual window widgets. */
        @Override public CompanionControlPanel.Snapshot snapshot(){return new CompanionControlPanel.Snapshot(true,text,audio,adSync,true,"Confirmation MEDIA attendue","brut 1000 ms · local 1000 ms");}
        /** Toggle only the recorder's TEXT preference. */
        @Override public void text(boolean value){text=value;}
        /** Toggle only its independent AUDIO preference. */
        @Override public void audio(boolean value){audio=value;}
        /** Exercise the real AD-SYNC widget's routing. */
        @Override public void adSync(boolean value){adSync=value;}
        /** Record the explicit user action without a playback effect. */
        @Override public void suspend(){suspends++;}
        /** Record resume intent without pretending to observe a film. */
        @Override public void resume(){resumes++;}
        /** Return a parsed value to prove keypad reachability; actual guard refusals are tested separately. */
        @Override public AdSyncController.Calibration calibrate(long value,boolean seek){calibrated=value;this.seek=seek;return AdSyncController.Calibration.APPLIED;}
    }
}
