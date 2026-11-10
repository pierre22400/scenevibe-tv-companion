package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * Exercise the reused PRODUCT UX 002 real Views and key dispatch in the main
 * package. Window effects are recorded; no accessibility service is simulated.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=31)
public final class CompanionRecalibrationPanelTest {
    private Activity activity;
    private Host host;
    private CompanionControlPanel panel;
    private Edit edit;
    /** Supply a real Activity and only a recording WindowManager boundary. */
    @Before public void before(){activity=Robolectric.buildActivity(Activity.class).setup().get();host=new Host();edit=new Edit();panel=new CompanionControlPanel(activity,null,host,()->true);}
    /** Retire all owned Views after each independent transaction. */
    @After public void after(){panel.close();activity.finish();}
    /** Dispatch a complete physical key pair, preserving its actual held duration. */
    private void key(int code,long duration){
        long now=android.os.SystemClock.uptimeMillis();host.view.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,code,0));
        host.view.dispatchKeyEvent(new KeyEvent(now,now+duration,KeyEvent.ACTION_UP,code,0));
    }
    /** Find an actual text-bearing widget without inspecting implementation fields. */
    private TextView text(View view,String label){
        if(view instanceof TextView && label.contentEquals(((TextView)view).getText()))return (TextView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView found=text(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}return null;
    }
    /** CURRENT and NEW remain visually distinct and do not report film detection. */
    @Test public void projectsCurrentAndCandidateInMainPackage(){assertTrue(panel.openManual(edit));assertNotNull(text(host.view,"POSITION ACTUELLE DE LA TRACK"));assertNotNull(text(host.view,"NOUVELLE POSITION DE LA TRACK"));assertNotNull(text(host.view,"Règle le temps SceneVibe sur le temps affiché par ton film."));assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,host.params.type);}
    /** Reuse the exact 60/10-second navigation without committing a candidate. */
    @Test public void dpadEditsOnlyTheCandidate(){panel.openManual(edit);key(KeyEvent.KEYCODE_DPAD_UP,50);key(KeyEvent.KEYCODE_DPAD_RIGHT,50);key(KeyEvent.KEYCODE_DPAD_DOWN,50);key(KeyEvent.KEYCODE_DPAD_LEFT,50);assertEquals(615000,edit.candidate);assertEquals(0,edit.commits);}
    /** Long OK toggles one-second steps without submitting the edit. */
    @Test public void longOkChangesFineStepOnly(){panel.openManual(edit);key(KeyEvent.KEYCODE_DPAD_CENTER,800);key(KeyEvent.KEYCODE_DPAD_RIGHT,50);assertEquals(616000,edit.candidate);assertEquals(0,edit.commits);}
    /** A short OK invokes the selected command once and closes its window. */
    @Test public void shortOkCommitsExactlyOnce(){panel.openManual(edit);key(KeyEvent.KEYCODE_DPAD_LEFT,50);key(KeyEvent.KEYCODE_DPAD_CENTER,50);assertEquals(1,edit.commits);assertEquals(0,edit.cancels);assertFalse(panel.isOpen());assertTrue(host.released);}
    /** BACK cancels without applying or resuming the Track. */
    @Test public void backCancelsCandidate(){panel.openManual(edit);key(KeyEvent.KEYCODE_DPAD_UP,50);key(KeyEvent.KEYCODE_BACK,50);assertEquals(0,edit.commits);assertEquals(1,edit.cancels);assertFalse(panel.isOpen());}
    /** The cancel button obeys ordinary OK selection rather than committing globally. */
    @Test public void selectedCancelButtonDoesNotCommit(){panel.openManual(edit);((Button)text(host.view,"Annuler")).requestFocus();key(KeyEvent.KEYCODE_DPAD_CENTER,50);assertEquals(0,edit.commits);assertEquals(1,edit.cancels);}
    /** Losing actual window focus discards the pending candidate. */
    @Test public void focusLossCancelsWithoutPartialCommit(){panel.openManual(edit);host.view.onWindowFocusChanged(true);key(KeyEvent.KEYCODE_DPAD_RIGHT,50);host.view.onWindowFocusChanged(false);assertEquals(0,edit.commits);assertEquals(1,edit.cancels);assertFalse(panel.isOpen());}
    /** No orphan OK release may validate a candidate. */
    @Test public void orphanReleaseDoesNotCommit(){panel.openManual(edit);host.view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));assertEquals(0,edit.commits);assertTrue(panel.isOpen());}
    /** A refused owner commit leaves the panel available for correction or BACK. */
    @Test public void refusedCommitDoesNotCloseOrResume(){edit.accept=false;panel.openManual(edit);key(KeyEvent.KEYCODE_DPAD_CENTER,50);assertTrue(panel.isOpen());assertEquals(1,edit.commits);key(KeyEvent.KEYCODE_BACK,50);assertEquals(1,edit.cancels);}
    /** Permission loss refuses native attachment without asking for new configuration. */
    @Test public void absentExistingPermissionFailsClosed(){var denied=new CompanionControlPanel(activity,null,host,()->false);assertFalse(denied.openManual(edit));assertEquals(0,host.adds);assertEquals(1,edit.cancels);}
    /** Duplicate invocations cannot replace an already edited transaction. */
    @Test public void duplicateOpenCannotCreateASecondWindow(){panel.openManual(edit);assertFalse(panel.openManual(new Edit()));assertEquals(1,host.adds);assertEquals(0,edit.cancels);}
    /** Hours are explicit from 60:00 onward. */
    @Test public void timeFormatIsUnambiguous(){assertEquals("59:59",CloudMultimediaSession.formatTrackTime(3599000));assertEquals("01:00:00",CloudMultimediaSession.formatTrackTime(3600000));assertEquals("01:05:15",CloudMultimediaSession.formatTrackTime(3915000));}
    /** Keep the selected pause policy visible to the viewer. */
    @Test public void withoutResumeIsASeparateExplicitCommand(){edit.resume=false;panel.openManual(edit);assertNotNull(text(host.view,"Recaler sans reprise"));assertNull(text(host.view,"Recaler et reprendre"));}
    /** Record the one system overlay and mandatory synchronous focus release. */
    private static final class Host implements CompanionControlPanel.WindowHost {
        View view;WindowManager.LayoutParams params;int adds;boolean released;
        /** Observe attachment without granting any permission. */
        @Override public void add(View view,WindowManager.LayoutParams params){this.view=view;this.params=params;adds++;}
        /** Record removal of input ownership before window retirement. */
        @Override public void releaseFocus(View view,WindowManager.LayoutParams params){released=true;}
        /** Keep only an inert reference for widget assertions. */
        @Override public void remove(View view){}
        /** Record layout changes on the same native root. */
        @Override public void update(View view,WindowManager.LayoutParams params){this.params=params;}
    }
    /** Record a detached candidate, never a replacement playback clock. */
    private static final class Edit implements CompanionControlPanel.ManualEdit {
        long candidate=615000;int commits,cancels;boolean accept=true,resume=true;
        /** Describe the candidate's policy. */ @Override public String label(){return "Réglage candidat";}
        /** Project a captured current Track position. */ @Override public String currentLabel(){return "10:15";}
        /** Format only the detached candidate. */ @Override public String candidateLabel(){return CloudMultimediaSession.formatTrackTime(candidate);}
        /** Keep the initially selected command. */ @Override public boolean resumeOnCommit(){return resume;}
        /** Modify only the candidate. */ @Override public void adjust(long delta){candidate=Math.max(0,Math.min(7200000,candidate+delta));}
        /** Record an owner-confirmed commit. */ @Override public boolean commit(){commits++;return accept;}
        /** Record cancellation without any playback action. */ @Override public void cancel(){cancels++;}
    }
}
