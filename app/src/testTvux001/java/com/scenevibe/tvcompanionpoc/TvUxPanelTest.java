package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.app.AlertDialog;
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

/**
 * Real Android views with explicit window effects under Robolectric.
 * These tests assert lifecycle, shared preferences and non-destructive dismissal;
 * they are not a Sony notification-menu or Prime compatibility qualification.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=31)
public final class TvUxPanelTest {
    private Activity activity;
    private Host host;
    private Actions commands;
    private CompanionControlPanel panel;
    private boolean permission=true;
    /** Attach the real panel to a bounded host without granting any Cloud/native playback admission. */
    @Before public void before() {
        activity=Robolectric.buildActivity(Activity.class).setup().get();host=new Host();commands=new Actions();
        panel=new CompanionControlPanel(activity,commands,host,()->permission);
    }
    /** Remove test views and reset the same process-owned switch state used by Settings. */
    @After public void after() {
        panel.close();activity.finish();
        TvFirst009TextServiceOwner.setMultimediaTextEnabled(true);TvFirst009TextServiceOwner.setMultimediaAudioEnabled(true);
    }
    /** Find a real button by its displayed text, including nested calibration controls. */
    private Button find(View root,String prefix) {
        if(root instanceof Button && ((Button)root).getText().toString().startsWith(prefix))return (Button)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){Button result=find(((ViewGroup)root).getChildAt(i),prefix);if(result!=null)return result;}
        return null;
    }
    /** Exercise the panel's actual Back dispatcher, consuming both event halves. */
    private void back() {
        assertTrue(host.view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK)));
        assertTrue(host.view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK)));
    }
    /** Only an explicitly opened, interactive window receives input; opening twice cannot duplicate it. */
    @Test public void openIsSingleAndFocusable() {
        assertTrue(panel.open());assertTrue(panel.open());assertEquals(1,host.adds);
        assertEquals(0,host.params.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,host.params.type);
    }
    /** Main Back removes the window and never calls a comment mutation. */
    @Test public void backReleasesOnlyThePanel() {
        panel.open();back();assertFalse(panel.isOpen());assertEquals(1,host.removes);assertTrue(host.released);assertEquals(0,commands.changes);
    }
    /** The visible main cross is exactly the same non-destructive close operation. */
    @Test public void crossReleasesOnlyThePanel() {
        panel.open();find(host.view,"× Fermer").performClick();
        assertFalse(panel.isOpen());assertTrue(commands.text);assertTrue(commands.audio);assertEquals(0,commands.changes);
    }
    /** Back first cancels the local form; another Back removes the panel, without calibration or replay. */
    @Test public void nestedBackPreservesState() {
        panel.open();find(host.view,"Recaler").performClick();assertNotNull(find(host.view,"Confirmer le temps"));
        back();assertTrue(panel.isOpen());assertNull(find(host.view,"Confirmer le temps"));assertEquals(0,commands.changes);
        back();assertFalse(panel.isOpen());
    }
    /** The form's cross also returns one level instead of destroying the underlying control surface. */
    @Test public void nestedCrossReturnsToPanel() {
        panel.open();find(host.view,"Recaler").performClick();find(host.view,"× Fermer").performClick();
        assertTrue(panel.isOpen());assertNotNull(find(host.view,"Reprendre"));assertEquals(0,commands.changes);
    }
    /** Toggle labels and actions come from fresh state snapshots, not cached UI booleans. */
    @Test public void textAndAudioAreIndependentAndLive() {
        panel.open();find(host.view,"Commentaires écrits").performClick();assertFalse(commands.text);assertTrue(commands.audio);
        commands.audio=false;Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
        assertEquals("Commentaires audio : OFF",find(host.view,"Commentaires audio").getText().toString());
    }
    /** An inactive service cannot be activated by the panel and cannot accept a local calibration. */
    @Test public void absentAuthorityStaysClosed() {
        commands.active=false;panel.open();assertFalse(find(host.view,"Reprendre").isEnabled());
        assertEquals(AdSyncController.Calibration.COMPANION_INACTIVE,TvFirst009TextServiceOwner.calibrateMultimediaResult(1000,false));
        assertFalse(TvFirst009TextServiceOwner.multimediaActive());
    }
    /** A partial add failure still releases input and removes any view retained by WindowManager. */
    @Test public void addFailureHasNoResidualInputOwner() {
        host.failAdd=true;assertFalse(panel.open());assertFalse(panel.isOpen());assertTrue(host.released);assertEquals(1,host.removes);
    }
    /** A failed remove cannot leave an invisible focusable view. */
    @Test public void removalFailureReleasesFocusFirst() {
        panel.open();host.failRemove=true;panel.close();assertFalse(panel.isOpen());assertTrue(host.released);assertEquals(View.GONE,host.view.getVisibility());
    }
    /** Revoking overlay permission closes the same window without a comment mutation. */
    @Test public void permissionLossClosesThePanel() {
        panel.open();permission=false;Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
        assertFalse(panel.isOpen());assertTrue(host.released);assertEquals(0,commands.changes);
    }
    /** Invalid input remains editable in place; a corrected D-pad entry can use the same activation. */
    @Test public void invalidTimeDoesNotDismissOrRearm() {
        panel.open();find(host.view,"Recaler").performClick();find(host.view,"Confirmer le temps").performClick();
        assertNotNull(find(host.view,"Confirmer le temps"));assertEquals(0,commands.changes);
        find(host.view,"1").performClick();find(host.view,":").performClick();find(host.view,"3").performClick();find(host.view,"0").performClick();
        find(host.view,"Confirmer le temps").performClick();assertEquals(90000,commands.calibrated);assertFalse(commands.seek);assertNull(find(host.view,"Confirmer le temps"));
    }
    /** Application dialogs preserve positive-action safety, cancellation and dismissal callbacks. */
    @Test public void applicationModalCrossIsNonDestructive() {
        int[] accepted={0},dismissed={0};
        AlertDialog dialog=TvCompanionDialogs.builder(activity).setTitle("Test SceneVibe")
                .setPositiveButton("Valider",(d,w)->accepted[0]++).setOnDismissListener(d->dismissed[0]++).show();
        find(dialog.getWindow().getDecorView(),"× Fermer").performClick();
        assertFalse(dialog.isShowing());assertEquals(0,accepted[0]);assertEquals(1,dismissed[0]);
    }
    /** Reading owner preferences after an overlay change keeps the main application's switches truthful. */
    @Test public void settingsUseTheSameOwnerPreferences() {
        TvFirst009TextServiceOwner.setMultimediaTextEnabled(false);TvFirst009TextServiceOwner.setMultimediaAudioEnabled(false);
        CloudMultimediaControls settings=new CloudMultimediaControls(activity,()->null);
        assertEquals("Commentaires écrits : OFF",find(settings.view(),"Commentaires écrits").getText().toString());
        assertEquals("Commentaires audio : OFF",find(settings.view(),"Commentaires audio").getText().toString());
    }

    /** Explicit bounded host; it never stands in for real native focus qualification. */
    private static final class Host implements CompanionControlPanel.WindowHost {
        View view;WindowManager.LayoutParams params;int adds,removes;boolean released,failAdd,failRemove;
        /** Model even a partially attached view before an add error. */
        @Override public void add(View view,WindowManager.LayoutParams params){this.view=view;this.params=params;adds++;if(failAdd)throw new IllegalStateException("test add");}
        /** Record that input was made unavailable before any remove attempt. */
        @Override public void releaseFocus(View view,WindowManager.LayoutParams params){released=true;params.flags|=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;}
        /** Keep failure visible to the lifecycle test without retaining panel ownership. */
        @Override public void remove(View view){removes++;if(failRemove)throw new IllegalStateException("test remove");}
    }
    /** Only a UI recording port: no account, cache, metadata or native admission is fabricated. */
    private static final class Actions implements CompanionControlPanel.Commands {
        boolean active=true,text=true,audio=true,seek;int changes;long calibrated=-1;
        /** Distinguish a requested resume from an acquired synchronization state. */
        @Override public CompanionControlPanel.Snapshot snapshot(){return new CompanionControlPanel.Snapshot(active,text,audio,"Confirmation MEDIA attendue");}
        /** Record an independent TEXT action. */
        @Override public void text(boolean enabled){text=enabled;changes++;}
        /** Record an independent AUDIO action. */
        @Override public void audio(boolean enabled){audio=enabled;changes++;}
        /** Record the explicit hold command. */
        @Override public void suspend(){changes++;}
        /** Record only intent, without inventing a successful MEDIA observation. */
        @Override public void resume(){changes++;}
        /** Return a bounded synthetic result for the real keypad's parsing/lifecycle test. */
        @Override public AdSyncController.Calibration calibrate(long time,boolean seek){calibrated=time;this.seek=seek;changes++;return AdSyncController.Calibration.APPLIED;}
    }
}
