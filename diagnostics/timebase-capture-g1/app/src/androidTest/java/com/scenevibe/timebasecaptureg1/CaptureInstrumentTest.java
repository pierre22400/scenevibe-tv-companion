package com.scenevibe.timebasecaptureg1;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;
import androidx.test.uiautomator.UiObject2;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import static org.junit.Assert.*;
import java.util.regex.Pattern;

/**
 * Real owned-emulator MediaProjection tests. UI automation operates only our controls
 * and the system consent, never a third-party player. No screenshot API substitutes for capture.
 */
@RunWith(AndroidJUnit4.class)
public final class CaptureInstrumentTest {
    private Context context;
    private UiDevice device;

    /** Construct an isolated Android-managed test harness. */
    public CaptureInstrumentTest() { }

    /** Reject physical hardware before any launch, installation-side action or consent. */
    @Before public void prepare() throws Exception {
        assertTrue("EMULATOR ONLY", "ranchu".equals(Build.HARDWARE)||"goldfish".equals(Build.HARDWARE));
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        CaptureService.cancel(context); device.pressHome();
        for(int i=0;i<100&&CaptureService.active;i++)Thread.sleep(20);
        assertFalse("Previous owned attempt not closed",CaptureService.active);
        CaptureService.lastProof=null;CaptureService.lastResult="DISARMED";
    }

    /** Open only the owned preparation screen, scroll to and click its consent control. */
    private void open(CapturePolicy.Mode mode) throws Exception { open(mode,false); }

    /** Configure an intentionally wrong ROI only to keep an owned projection open for revocation. */
    private void open(CapturePolicy.Mode mode,boolean negativeRoi) throws Exception {
        context.startActivity(new Intent(context,ControlActivity.class).putExtra("mode",mode.name()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        UiObject2 button=device.wait(Until.findObject(By.text("CONSENTEMENT ET CONTRÔLE SYNTHÉTIQUE")),4000);
        if(button==null)button=device.findObject(By.text("Consentement et contrôle synthétique"));
        if(button==null) {
            device.findObject(By.clazz("android.widget.ScrollView")).scroll(androidx.test.uiautomator.Direction.DOWN,1f);
            button=device.wait(Until.findObject(By.text(Pattern.compile("(?i)Consentement et contrôle synthétique"))),2000);
        }
        assertNotNull("Owned consent button",button);
        if(negativeRoi) {
            UiObject2 x=device.findObject(By.desc("X normalisé"));
            if(x==null) {
                device.findObject(By.clazz("android.widget.ScrollView")).scroll(androidx.test.uiautomator.Direction.UP,1f);
                x=device.findObject(By.desc("X normalisé"));
            }
            x.setText("0");device.findObject(By.desc("Y normalisé")).setText("0");
            device.findObject(By.desc("Largeur normalisée")).setText("0.1");device.findObject(By.desc("Hauteur normalisée")).setText("0.1");
            device.findObject(By.clazz("android.widget.ScrollView")).scroll(androidx.test.uiautomator.Direction.DOWN,1f);
            button=device.findObject(By.text(Pattern.compile("(?i)Consentement et contrôle synthétique")));
            assertNotNull(button);
        }
        button.click();
        Thread.sleep(800);
    }

    /** Accept the real system projection dialog; no appops, shell grant or injected consent token. */
    private void accept() throws Exception {
        UiObject2 yes=device.wait(Until.findObject(By.res("com.android.systemui:id/start")),3000);
        if(yes==null)yes=device.findObject(By.text(Pattern.compile("(?i)(start now|start|allow|commencer|démarrer|autoriser|share screen)")));
        if(yes==null)yes=device.findObject(By.res("android:id/button1"));
        assertNotNull("System consent unavailable: G1 NOT QUALIFIED",yes); yes.click();
    }

    /** Wait for terminal worker evidence rather than fabricating a capture success from UI state. */
    private JSONObject terminal() throws Exception {
        for(int i=0;i<180;i++) { if(CaptureService.lastProof!=null&&!CaptureService.active)return CaptureService.lastProof; Thread.sleep(100); }
        fail("No terminal evidence: "+CaptureService.lastResult);return null;
    }

    /** Public owned pixels must actually arrive through ImageReader with all four markers. */
    @Test public void publicPixels() throws Exception {
        open(CapturePolicy.Mode.PUBLIC);accept();JSONObject j=terminal();
        assertEquals(j.toString(),"ROI_VISIBLE",j.getString("outcome"));assertEquals(4,j.getInt("markerMatches"));
        assertTrue(j.getBoolean("middleMagenta"));assertClosed(j);
    }

    /** Mixed composition preserves ordinary UI while the separate secure surface is not readable. */
    @Test public void mixedComposition() throws Exception {
        open(CapturePolicy.Mode.MIXED);accept();JSONObject j=terminal();
        assertEquals(j.toString(),"ROI_VISIBLE",j.getString("outcome"));assertEquals(4,j.getInt("markerMatches"));
        assertFalse(j.toString(),j.getBoolean("middleMagenta"));assertClosed(j);
    }

    /** A protected owned window yields no useful protected pattern, without alternate acquisition. */
    @Test public void secureWindow() throws Exception {
        open(CapturePolicy.Mode.SECURE);accept();JSONObject j=terminal();
        assertEquals(j.toString(),"ROI_NOT_VISIBLE",j.getString("outcome"));assertEquals(0,j.getInt("markerMatches"));assertClosed(j);
    }

    /** Declining actual system consent never arms or consumes a projection. */
    @Test public void deniedConsent() throws Exception {
        open(CapturePolicy.Mode.PUBLIC);device.pressBack();Thread.sleep(500);
        assertFalse(CaptureService.active);assertEquals("CONSENT_DENIED",CaptureService.lastResult);
    }

    /** Stopping an armed attempt closes it before an image and never retries. */
    @Test public void stopBeforeImage() throws Exception {
        open(CapturePolicy.Mode.PUBLIC);accept();Thread.sleep(300);CaptureService.cancel(context);
        JSONObject j=terminal();assertEquals("OWNER_STOP",j.getString("outcome"));assertClosed(j);
        Thread.sleep(3300);assertFalse(CaptureService.active);assertEquals(0,j.getInt("received"));
    }

    /** Leaving the owned screen cancels rather than following another foreground. */
    @Test public void leaveFixture() throws Exception {
        open(CapturePolicy.Mode.PUBLIC);accept();Thread.sleep(400);device.pressHome();
        JSONObject j=terminal();assertEquals("OWNER_STOP",j.getString("outcome"));assertClosed(j);
    }

    /** Stop our real public MediaProjection API; reflection reads only this instrument's private field. */
    @Test public void projectionRevoked() throws Exception {
        open(CapturePolicy.Mode.PUBLIC,true);accept();
        java.lang.reflect.Field live=CaptureService.class.getDeclaredField("live"); live.setAccessible(true);
        java.lang.reflect.Field projection=CaptureService.class.getDeclaredField("projection"); projection.setAccessible(true);
        android.media.projection.MediaProjection own=null;
        for(int i=0;i<100;i++) {
            Object service=live.get(null);
            if(service!=null)own=(android.media.projection.MediaProjection)projection.get(service);
            if(own!=null)break;Thread.sleep(50);
        }
        assertNotNull("Owned projection did not start",own);own.stop();
        JSONObject j=terminal();assertEquals(j.toString(),"SESSION_REVOKED",j.getString("outcome"));assertClosed(j);
    }

    /** Verify image accounting, terminal ownership and absence of persistent screenshot files. */
    private void assertClosed(JSONObject j) throws Exception {
        assertTrue(j.toString(),j.getBoolean("resourcesClosed"));assertEquals(0,j.getInt("releaseErrors"));
        assertEquals(j.getInt("received"),j.getInt("imagesClosed"));assertFalse(j.getBoolean("imagePersisted"));
        for(String name:context.getFilesDir().list())assertFalse(name.endsWith(".png")||name.endsWith(".jpg")||name.endsWith(".webp"));
    }
}
