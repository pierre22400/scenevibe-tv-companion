package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Real shared widgets with recorded window operations; no media admission is manufactured. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=31)
public final class DeveloperPanelTest {
    private Activity activity;
    private Host host;
    private Actions actions;
    private CompanionControlPanel panel;
    /** Supply only a deterministic native-window seam and a shared command-state recorder. */
    @Before public void before(){activity=Robolectric.buildActivity(Activity.class).setup().get();host=new Host();actions=new Actions();panel=new CompanionControlPanel(activity,actions,host,()->true);}
    /** Release only this fixture's UI resources. */
    @After public void after(){panel.pauseEmbedded();panel.close();activity.finish();}
    /** Find real remote buttons independently of private implementation fields. */
    private Button button(View root,String text){
        if(root instanceof Button && ((Button)root).getText().toString().startsWith(text))return (Button)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){Button result=button(((ViewGroup)root).getChildAt(i),text);if(result!=null)return result;}return null;
    }
    /** Preparation must leave all remote input with the ordinary foreground Activity. */
    @Test public void prepareIsNotFocusableOrTouchable(){assertTrue(panel.prepare());assertTrue(panel.isPrepared());assertTrue((host.params.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)!=0);assertTrue((host.params.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)!=0);}
    /** The same root is promoted after handoff rather than removed and re-created. */
    @Test public void promotionRetainsTheWindow(){panel.prepare();View root=host.view;assertTrue(panel.activatePrepared());assertSame(root,host.view);assertEquals(1,host.adds);assertEquals(1,host.updates);assertEquals(0,host.removes);assertEquals(0,host.params.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);}
    /** A second preparation parks the same window and releases input. */
    @Test public void parkingRetainsRootAndSwitches(){panel.prepare();panel.activatePrepared();View root=host.view;panel.standby();assertSame(root,host.view);assertTrue(panel.isPrepared());assertTrue(actions.text);assertTrue(actions.audio);assertEquals(0,host.removes);}
    /** Updating the window can fail without leaving an invisible input owner. */
    @Test public void failedPromotionReleasesWindow(){panel.prepare();host.failUpdate=true;assertFalse(panel.activatePrepared());assertFalse(panel.isOpen());assertTrue(host.released);assertEquals(1,host.removes);}
    /** An Activity embedding uses the identical widgets with no WindowManager attachment. */
    @Test public void embeddedControlsDoNotBecomeAnOverlay(){View view=panel.embeddedView(()->{});activity.setContentView(view);assertEquals(0,host.adds);assertEquals(0,host.updates);assertNotNull(button(view,"AD-SYNC"));}
    /** The two surfaces read the same command state and do not create secondary preferences. */
    @Test public void activityAndOverlayUseIdenticalCommands(){
        var embedded=new CompanionControlPanel(activity,actions,new Host(),()->true);
        View app=embedded.embeddedView(()->{});button(app,"Commentaires écrits").performClick();
        panel.prepare();panel.activatePrepared();assertEquals("Commentaires écrits : OFF",button(host.view,"Commentaires écrits").getText().toString());
        button(host.view,"AD-SYNC").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
        assertTrue(actions.adSync);assertEquals("AD-SYNC : ON",button(app,"AD-SYNC").getText().toString());embedded.pauseEmbedded();
    }
    /** Back first cancels the numeric form; another Back closes the window only. */
    @Test public void nestedBackAndClosePreserveCommands(){panel.prepare();panel.activatePrepared();button(host.view,"Recaler").performClick();panel.back();assertTrue(panel.isOpen());panel.back();assertFalse(panel.isOpen());assertEquals(0,actions.suspends);assertTrue(actions.text);}
    /** A close cross does not disable the separate developer mode state. */
    @Test public void closeCallbackDoesNotDisableMode(){var state=new DeveloperOverlayState();state.enable();panel.onLifecycle(state::closed,state::prepared);panel.prepare();state.prepared();panel.activatePrepared();state.shown();button(host.view,"× Fermer").performClick();assertEquals(DeveloperOverlayState.Stage.ENABLED,state.stage());}
    /** The normal application remains on ordinary application window types. */
    @Test public void mainActivityContainsDeveloperTabAsNormalViews(){
        var app=Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNotNull(button(app.getWindow().getDecorView(),"Développeur"));
        int type=app.getWindow().getAttributes().type;assertTrue(type>=WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW && type<=WindowManager.LayoutParams.LAST_APPLICATION_WINDOW);app.finish();
    }
    /** Entering the tab does not activate a mode, service, track or overlay. */
    @Test public void developerNavigationDoesNotActivateAnything(){
        var app=Robolectric.buildActivity(MainActivity.class).setup().get();button(app.getWindow().getDecorView(),"Développeur").performClick();
        assertNotNull(button(app.getWindow().getDecorView(),"Afficher les commandes en overlay"));
        assertEquals(DeveloperOverlayState.Stage.OFF,TvFirst009TextServiceOwner.developerStage());assertFalse(TvFirst009TextServiceOwner.multimediaActive());app.finish();
    }
    /** Public player readiness is separate from Activity handoff, never a timed auto-show. */
    @Test public void activeTrackCanWaitForThePlayer(){
        var state=new DeveloperOverlayState();state.enable();state.prepared();String token=state.begin(100);
        assertTrue(state.waitForPlayer(token,200));assertTrue(state.awaitingPlayer(5000));assertEquals(DeveloperOverlayState.Stage.WAITING_PLAYER,state.stage());
        state.shown();assertEquals(DeveloperOverlayState.Stage.INTERACTIVE,state.stage());
    }
    /** Waiting for an unknown/absent player expires to a passive state only. */
    @Test public void absentPlayerCannotTriggerLateFocus(){
        var state=new DeveloperOverlayState();state.enable();state.prepared();String token=state.begin(100);state.waitForPlayer(token,200);
        assertFalse(state.awaitingPlayer(300200));assertEquals(DeveloperOverlayState.Stage.PREPARED,state.stage());
    }
    /** A capture from before the application handoff cannot steal the destination's navigation. */
    @Test public void readinessRequiresANewCompatibleCapture(){
        var sample=new AdSyncController.Sample(new Object(),true,true,1000,1000,1000,2000,1f,100000);
        assertFalse(CloudMultimediaSession.developerMediaReady(sample,2200,2100));
        assertTrue(CloudMultimediaSession.developerMediaReady(sample,2200,1900));
    }
    /** A stale/future capture, paused playback or absent identity leaves the badge passive. */
    @Test public void stalePausedAndInvalidReadinessAreRefused(){
        var playing=new AdSyncController.Sample(new Object(),true,true,1000,1000,1000,2000,1f,100000);
        assertFalse(CloudMultimediaSession.developerMediaReady(playing,4501,1900));
        assertFalse(CloudMultimediaSession.developerMediaReady(playing,1999,1900));
        assertFalse(CloudMultimediaSession.developerMediaReady(new AdSyncController.Sample(new Object(),true,false,1000,1000,1000,2000,0f,100000),2100,1900));
        assertFalse(CloudMultimediaSession.developerMediaReady(new AdSyncController.Sample(null,false,true,1000,1000,1000,2000,1f,100000),2100,1900));
    }
    /** The wait starts at Activity stop and is discarded when ordinary app navigation resumes. */
    @Test public void mediaWaitOriginIsBoundToThisLaunch(){
        var state=new DeveloperOverlayState();state.enable();state.prepared();String token=state.begin(100);state.waitForPlayer(token,200);
        assertEquals(200,state.playerWaitStarted());state.prepared();assertEquals(Long.MAX_VALUE,state.playerWaitStarted());
    }

    /** Record the same root while native WindowManager behavior is tested separately. */
    private static final class Host implements CompanionControlPanel.WindowHost {
        View view;WindowManager.LayoutParams params;int adds,updates,removes;boolean released,failUpdate;
        /** Retain a single window root. */
        @Override public void add(View view,WindowManager.LayoutParams params){this.view=view;this.params=params;adds++;}
        /** Track every input-policy update. */
        @Override public void update(View view,WindowManager.LayoutParams params){updates++;this.params=params;if(failUpdate)throw new IllegalStateException("update fixture");}
        /** Record release before removal. */
        @Override public void releaseFocus(View view,WindowManager.LayoutParams params){released=true;}
        /** Count synchronous removals. */
        @Override public void remove(View view){removes++;}
    }
    /** Only a shared UI-state recorder; no Cloud credentials or native playback permit exists. */
    private static final class Actions implements CompanionControlPanel.Commands {
        boolean text=true,audio=true,adSync;int suspends;
        /** Return one state for both actual widget trees. */
        @Override public CompanionControlPanel.Snapshot snapshot(){return new CompanionControlPanel.Snapshot(true,text,audio,adSync,true,"Confirmation MEDIA attendue","brut 1000 ms · local 1000 ms");}
        /** Change TEXT only. */
        @Override public void text(boolean value){text=value;}
        /** Change AUDIO only. */
        @Override public void audio(boolean value){audio=value;}
        /** Change AD-SYNC independently. */
        @Override public void adSync(boolean value){adSync=value;}
        /** Record explicit local suspension. */
        @Override public void suspend(){suspends++;}
        /** Keep resume intent distinct from observation. */
        @Override public void resume(){}
        /** Test form parsing separately from authority. */
        @Override public AdSyncController.Calibration calibrate(long time,boolean seek){return AdSyncController.Calibration.APPLIED;}
    }
}
