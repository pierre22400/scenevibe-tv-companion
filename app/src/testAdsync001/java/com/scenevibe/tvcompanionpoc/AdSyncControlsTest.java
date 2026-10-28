package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Actual Android widget actions under Robolectric; none grants cache/Cloud or native playback admission. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=31)
public final class AdSyncControlsTest {
    private Activity activity;
    private CloudMultimediaControls controls;
    /** Build the actual existing Settings subtree with its real closed/no-account state. */
    @Before public void before(){TvFirst009TextServiceOwner.setAdSyncEnabled(false);activity=Robolectric.buildActivity(Activity.class).setup().get();
        controls=new CloudMultimediaControls(activity,()->null);activity.setContentView(controls.view());}
    /** Discard only test process preferences and the temporary Activity. */
    @After public void after(){TvFirst009TextServiceOwner.setAdSyncEnabled(false);activity.finish();}
    /** Find a real button by its visible label, without a private implementation field. */
    private Button button(View root,String prefix){if(root instanceof Button && ((Button)root).getText().toString().startsWith(prefix))return (Button)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){Button found=button(((ViewGroup)root).getChildAt(i),prefix);if(found!=null)return found;}return null;}
    /** The opt-in is OFF initially, while the two qualified independent controls remain present. */
    @Test public void optInStartsOffAndPreservesBothSwitches(){assertTrue(BuildConfig.ADSYNC_PROTOTYPE);assertFalse(TvFirst009TextServiceOwner.adSyncEnabled());
        assertNotNull(button(controls.view(),"Commentaires écrits"));assertNotNull(button(controls.view(),"Commentaires audio"));}
    /** Toggling the actual remote-focusable widget cannot start the service or manufacture a permit. */
    @Test public void toggleDoesNotActivateComments(){assertTrue(button(controls.view(),"AD-SYNC").performClick());assertTrue(TvFirst009TextServiceOwner.adSyncEnabled());assertFalse(TvFirst009TextServiceOwner.multimediaActive());}
    /** With no admitted lifetime, Suspend/Resume/Calibration remain visibly disabled. */
    @Test public void manualActionsRequireExistingAdmittedService(){button(controls.view(),"AD-SYNC").performClick();
        for(String prefix:new String[]{"Suspendre les commentaires","Reprendre les commentaires","Recaler sur le temps"})assertFalse(button(controls.view(),prefix).isEnabled());}
    /** A guard toggle cannot change independent TEXT or AUDIO preferences. */
    @Test public void independentSwitchesSurviveGuardToggle(){button(controls.view(),"Commentaires écrits").performClick();button(controls.view(),"AD-SYNC").performClick();
        assertTrue(button(controls.view(),"Commentaires écrits").getText().toString().endsWith("OFF"));assertTrue(button(controls.view(),"Commentaires audio").getText().toString().endsWith("ON"));}
    /** The direct local calibration entry also refuses absent service authority. */
    @Test public void noServiceMeansNoCalibration(){assertFalse(TvFirst009TextServiceOwner.calibrateMultimediaComments(90000));}
}
