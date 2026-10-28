package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** Real shared widgets with recorded window operations; no media admission is manufactured. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=31)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
        state.shown();assertEquals(DeveloperOverlayState.Stage.MONITORING,state.stage());
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
    /** Visibility alone must keep both no-input flags and the exact original root. */
    @Test public void passiveExpansionDoesNotRequestInput(){
        panel.prepare();View root=host.view;assertTrue(panel.presentPrepared(false));assertTrue(panel.isMonitoring());assertSame(root,host.view);
        assertNotEquals(0,host.params.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        assertNotEquals(0,host.params.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        assertTrue("Preference availability is independent of passive key routing",button(root,"AD-SYNC").isEnabled());assertTrue(actions.text && actions.audio);
    }
    /** Returning to the Activity parks an expanded monitor without replacing its window. */
    @Test public void monitorCanParkWithoutChangingTheTrack(){panel.prepare();panel.presentPrepared(false);View root=host.view;panel.standby();assertTrue(panel.isPrepared());assertFalse(panel.isMonitoring());assertSame(root,host.view);assertTrue(actions.text && actions.audio);assertEquals(0,actions.suspends);}
    /** An UP belonging to the old window cannot close this panel or click its first switch. */
    @Test public void orphanReleasesDoNotCloseOrToggle(){panel.prepare();panel.activatePrepared();host.view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));host.view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));assertTrue(panel.isOpen());assertTrue(actions.text);}
    /** Platform transport fallback is blocked while interacting with SceneVibe, for both event halves. */
    @Test public void transportKeysAreConsumedByThePanel(){panel.prepare();panel.activatePrepared();for(int code:new int[]{KeyEvent.KEYCODE_MEDIA_PLAY,KeyEvent.KEYCODE_MEDIA_PAUSE,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,KeyEvent.KEYCODE_MEDIA_REWIND,KeyEvent.KEYCODE_MEDIA_FAST_FORWARD})for(int action:new int[]{KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP})assertTrue(host.view.dispatchKeyEvent(new KeyEvent(action,code)));assertTrue(actions.text && actions.audio);assertTrue(panel.isOpen());}
    /** Render the actual drawable independently of text rasterization to inspect visible activation/focus marks. */
    private Bitmap appearance(Button button){Bitmap result=Bitmap.createBitmap(512,128,Bitmap.Config.ARGB_8888);button.getBackground().setBounds(0,0,512,128);button.getBackground().draw(new Canvas(result));return result;}
    /** Count exact border/fill pixels rather than asserting the implementation's drawable hierarchy. */
    private int pixels(Bitmap bitmap,int color){int[] values=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(values,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());int count=0;for(int value:values)if(value==color)count++;return count;}
    /** Each TEXT/AUDIO/AD-SYNC switch advertises and draws its actual ON/OFF state. */
    @Test public void threeSwitchesExposeCheckedAndVisibleRedOnOnly(){
        panel.prepare();panel.activatePrepared();
        for(String title:new String[]{"Commentaires écrits","Commentaires audio","AD-SYNC"}){
            DeveloperButton item=(DeveloperButton)button(host.view,title);boolean initial=item.isChecked();
            if(!initial)item.performClick();assertTrue(title+" checked",item.isChecked());assertTrue(title+" visible red ON border",pixels(appearance(item),DeveloperButton.ON_BORDER)>0);
            var info=item.createAccessibilityNodeInfo();assertTrue(info.isCheckable());assertTrue(info.isChecked());info.recycle();
            item.performClick();assertFalse(item.isChecked());assertEquals(0,pixels(appearance(item),DeveloperButton.ON_BORDER));assertTrue(item.getText().toString().contains("OFF"));
        }
    }
    /** D-pad focus adds a separate black outline and never changes the switch value/red border. */
    @Test public void focusIsIndependentFromActivation(){
        DeveloperButton item=new DeveloperButton(activity);item.setText("TEXT : ON");item.switchState(true);item.setFocusableInTouchMode(true);
        DeveloperButton other=new DeveloperButton(activity);other.setText("Autre action");other.setFocusableInTouchMode(true);
        var content=new android.widget.LinearLayout(activity);content.setOrientation(android.widget.LinearLayout.VERTICAL);content.addView(item);content.addView(other);activity.setContentView(content);
        content.measure(View.MeasureSpec.makeMeasureSpec(512,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(256,View.MeasureSpec.EXACTLY));content.layout(0,0,512,256);
        assertTrue("Establish another focused view before measuring ON without focus",other.requestFocus());assertFalse(item.isFocused());Bitmap on=appearance(item);
        assertTrue("Acquire actual local view focus before measuring its outline",item.requestFocus());assertTrue(item.isFocused());Bitmap focused=appearance(item);
        assertTrue(item.isChecked());assertTrue("Unfocused ON remains red",pixels(on,DeveloperButton.ON_BORDER)>0);assertTrue("Focused ON remains red",pixels(focused,DeveloperButton.ON_BORDER)>0);assertTrue("Focus has a separate black ring",pixels(focused,Color.BLACK)>0);
        item.switchState(false);assertTrue(item.isFocused());assertEquals(0,pixels(appearance(item),DeveloperButton.ON_BORDER));assertTrue(pixels(appearance(item),Color.BLACK)>0);
    }
    /** Unavailable ON is distinct from available OFF and cannot invoke its click handler remotely. */
    @Test public void unavailableIsNeitherOffNorActionable(){
        DeveloperButton item=new DeveloperButton(activity);item.switchState(true);item.setEnabled(false);
        assertTrue(item.isChecked());assertEquals(0,pixels(appearance(item),DeveloperButton.ON_BORDER));assertTrue("Disabled fill is distinct",pixels(appearance(item),DeveloperButton.UNAVAILABLE_FILL)>0);assertTrue(item.getStateDescription().toString().contains("indisponible"));assertFalse(item.isEnabled());
        item.setEnabled(true);item.switchState(false);assertTrue(pixels(appearance(item),DeveloperButton.OFF_FILL)>0);assertEquals("OFF",item.getStateDescription());
    }
    /** Suspend/resume/calibrate/close remain actions, without an invented persistent ON border. */
    @Test public void commandsAreNotAccessibleSwitches(){panel.prepare();panel.activatePrepared();for(String title:new String[]{"Suspendre","Reprendre","Recaler","× Fermer"}){Button item=button(host.view,title);var info=item.createAccessibilityNodeInfo();assertFalse(info.isCheckable());info.recycle();assertEquals(0,pixels(appearance(item),DeveloperButton.ON_BORDER));}}
    /** Both surfaces draw the same checked projection, regardless of the window that contains them. */
    @Test public void embeddedAndOverlayDrawTheSameSwitchState(){
        var embedded=new CompanionControlPanel(activity,actions,new Host(),()->true);View app=embedded.embeddedView(()->{});panel.prepare();panel.activatePrepared();
        assertTrue(appearance(button(app,"Commentaires écrits")).sameAs(appearance(button(host.view,"Commentaires écrits"))));embedded.pauseEmbedded();
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
