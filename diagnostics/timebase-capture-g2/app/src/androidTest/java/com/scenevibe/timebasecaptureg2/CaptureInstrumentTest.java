package com.scenevibe.timebasecaptureg2;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Direction;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import static org.junit.Assert.*;
import java.util.regex.Pattern;

/**
 * Real MediaProjection on disposable owned emulators with normal system consent.
 * UI automation never opens Netflix or a third-party player and never captures via ADB.
 * Pixel assertions inspect only manufactured ROI data after Android resources close.
 */
@RunWith(AndroidJUnit4.class)
public final class CaptureInstrumentTest {
    private Context context;
    private UiDevice device;

    /** No persistent test permissions or images are initialized. */
    public CaptureInstrumentTest() { }

    /** Refuse physical hardware before any Activity launch or device control. */
    @Before public void prepare() throws Exception {
        assertTrue("EMULATOR ONLY","ranchu".equals(Build.HARDWARE)||"goldfish".equals(Build.HARDWARE));
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        SessionMemory.cancel(context);device.pressHome();
        for(int i=0;i<100&&CaptureService.active;i++)Thread.sleep(20);
        assertFalse(CaptureService.active);CaptureService.lastProof=null;CaptureService.lastResult="DISARMED";SessionMemory.synthetic=true;
    }

    /** Find an owned control with bounded scrolling rather than relying on display dimensions. */
    private UiObject2 button(String name) throws Exception {
        Pattern regex=Pattern.compile("(?i)"+Pattern.quote(name));
        for(int i=0;i<6;i++) {
            UiObject2 b=device.findObject(By.text(regex));if(b!=null)return b;
            UiObject2 scroll=device.findObject(By.clazz("android.widget.ScrollView"));if(scroll==null)break;scroll.scroll(Direction.DOWN,.8f);
        }
        fail("Owned button missing: "+name);return null;
    }

    /** Launch only preparation and configure the closed synthetic mode before real consent. */
    private void open(CapturePolicy.Mode mode) throws Exception {
        context.startActivity(new Intent(context,ControlActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        assertNotNull(device.wait(Until.findObject(By.desc("X normalisé")),4000));
        if(mode!=CapturePolicy.Mode.PUBLIC)button("Choisir "+mode).click();
        // A fresh launch avoids returning upward through a soft keyboard on a TV.
        UiObject2 scroll=device.findObject(By.clazz("android.widget.ScrollView"));if(scroll!=null)scroll.scroll(Direction.UP,1f);
        button("Consentement et armement unique").click();Thread.sleep(500);
    }

    /** Accept actual system consent; there is no appops grant or fabricated token. */
    private void accept() throws Exception {
        UiObject2 yes=device.wait(Until.findObject(By.res("com.android.systemui:id/start")),3000);
        if(yes==null)yes=device.findObject(By.text(Pattern.compile("(?i)(start now|start|allow|commencer|démarrer|autoriser|share screen)")));
        if(yes==null)yes=device.findObject(By.res("android:id/button1"));
        assertNotNull("System consent unavailable",yes);yes.click();
        for(int i=0;i<60&&!"WAITING".equals(CaptureService.lastResult);i++)Thread.sleep(20);
        assertEquals("WAITING",CaptureService.lastResult);
    }

    /** Voluntarily leave preparation for another owned Activity using its ordinary UI button. */
    private void depart() throws Exception {button("Afficher notre faux lecteur").click();}

    /** Wait for terminal observed evidence, not a mock service response. */
    private JSONObject terminal() throws Exception {
        for(int i=0;i<180;i++){if(CaptureService.lastProof!=null&&!CaptureService.active)return CaptureService.lastProof;Thread.sleep(100);}
        fail("No terminal proof: "+CaptureService.lastResult);return null;
    }

    /** Read one still-live owned crop after closure and validate our geometric control only. */
    private int matches() {
        synchronized(SessionMemory.roi) {
            int[] pixels=SessionMemory.roi.get(SessionMemory.policy.generation,SystemClock.elapsedRealtime());assertNotNull(pixels);
            return PixelProbe.matches(pixels,SessionMemory.roi.width,SessionMemory.roi.height);
        }
    }

    /** Verify native resource accounting and absence of persistent images. */
    private void closed(JSONObject j) throws Exception {
        assertTrue(j.toString(),j.getBoolean("resourcesClosed"));assertEquals(0,j.getInt("releaseErrors"));
        assertEquals(j.getInt("received"),j.getInt("imagesClosed"));assertFalse(CaptureService.active);
        for(String name:context.getFilesDir().list())assertFalse(name.endsWith(".png")||name.endsWith(".jpg")||name.endsWith(".webp"));
    }

    /** Capture an actually immobile owned Activity via MediaProjection, accepting the first received frame only. */
    @Test public void staticFirstFrame() throws Exception {
        open(CapturePolicy.Mode.PUBLIC);accept();
        context.startActivity(new Intent(context,FixtureActivity.class).putExtra("mode","PUBLIC").putExtra("static",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        JSONObject j=terminal();closed(j);assertFalse(FixtureActivity.animationEnabled);assertTrue(FixtureActivity.drawCount>0);
        assertEquals(j.toString(),"FRAME_READY_NOT_CLASSIFIED",j.getString("outcome"));assertEquals(4,matches());
        assertEquals(1,j.getInt("received"));assertEquals(0,j.getInt("rejected"));assertEquals(1,j.getInt("usefulFrames"));
        assertEquals(SessionMemory.policy.generation,j.getLong("readerGeneration"));assertTrue(j.getInt("virtualDisplayId")>=0);
        assertTrue(j.getLong("readerCreatedElapsedMs")-j.getLong("triggerElapsedMs")>=250);
        assertTrue(j.getLong("closedElapsedMs")-j.getLong("triggerElapsedMs")<10000);
        assertEquals("ACCEPTED",j.getJSONArray("bufferDecisions").getJSONObject(0).getString("decision"));
        assertEquals(0,j.getJSONObject("rejectionCounts").length());assertEquals(0,j.getInt("decisionLogErrors"));
    }

    /** Positive cross-Activity capture, unique useful frame and human-only simulated label, then erase. */
    @Test public void publicCrossAndInspection() throws Exception {
        open(CapturePolicy.Mode.PUBLIC);accept();depart();JSONObject j=terminal();closed(j);
        assertEquals(j.toString(),"FRAME_READY_NOT_CLASSIFIED",j.getString("outcome"));assertEquals(1,j.getInt("usefulFrames"));assertEquals(4,matches());
        assertTrue(j.getLong("triggerElapsedMs")-SessionMemory.policy.waitAt>=3000);
        context.startActivity(new Intent(context,ResultActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Thread.sleep(300);assertNotNull(button("Effacer et fermer"));
        assertTrue(SessionMemory.label(context,"A",true));
        String journal=new String(java.nio.file.Files.readAllBytes(new java.io.File(context.getFilesDir(),"g2.jsonl").toPath()),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(journal.contains("SIMULATED_A"));button("Effacer et fermer").click();Thread.sleep(300);assertNull(SessionMemory.roi.get(SessionMemory.policy.generation,SystemClock.elapsedRealtime()));
    }

    /** Ordinary UI remains visible above our separately protected synthetic surface. */
    @Test public void mixedComposition() throws Exception {open(CapturePolicy.Mode.MIXED);accept();depart();JSONObject j=terminal();closed(j);assertEquals(4,matches());}

    /** A whole secure owned window supplies no useful geometric clock pattern. */
    @Test public void secureWindow() throws Exception {open(CapturePolicy.Mode.SECURE);accept();depart();JSONObject j=terminal();closed(j);assertEquals(0,matches());}

    /** Real consent refusal never starts the service. */
    @Test public void deniedConsent() throws Exception {open(CapturePolicy.Mode.PUBLIC);device.pressBack();Thread.sleep(500);assertFalse(CaptureService.active);assertEquals("CONSENT_DENIED",CaptureService.lastResult);assertFalse(SessionMemory.policy.active());assertTrue(SessionMemory.label(context,"C",false));assertFalse(SessionMemory.label(context,"A",false));}

    /** Owner stop while waiting terminates before buffers and never restarts. */
    @Test public void stopBeforeImage() throws Exception {open(CapturePolicy.Mode.PUBLIC);accept();SessionMemory.cancel(context);JSONObject j=terminal();closed(j);assertEquals("OWNER_STOP",j.getString("outcome"));assertEquals(0,j.getInt("received"));Thread.sleep(3200);assertFalse(CaptureService.active);}

    /** Real projection revocation while waiting closes even though no virtual display exists yet. */
    @Test public void revokeWaiting() throws Exception {
        open(CapturePolicy.Mode.PUBLIC);accept();
        java.lang.reflect.Field live=CaptureService.class.getDeclaredField("live");live.setAccessible(true);
        java.lang.reflect.Field f=CaptureService.class.getDeclaredField("projection");f.setAccessible(true);
        android.media.projection.MediaProjection projection=(android.media.projection.MediaProjection)f.get(live.get(null));assertNotNull(projection);projection.stop();
        JSONObject j=terminal();closed(j);assertEquals(j.toString(),"SESSION_REVOKED",j.getString("outcome"));assertEquals(0,j.getInt("received"));
    }

    /** Staying on preparation cannot accidentally capture its UI at the deadline. */
    @Test public void noDeparture() throws Exception {open(CapturePolicy.Mode.PUBLIC);accept();JSONObject j=terminal();closed(j);assertEquals("ARM_EXPIRED_OR_PREPARATION_VISIBLE",j.getString("outcome"));assertEquals(0,j.getInt("received"));}

    /** Returning to preparation while waiting invalidates the generation. */
    @Test public void returnPreparation() throws Exception {open(CapturePolicy.Mode.PUBLIC);accept();depart();Thread.sleep(250);device.pressBack();JSONObject j=terminal();closed(j);assertEquals("OWNER_STOP",j.getString("outcome"));}

    /** Actual thirty-second memory expiration wipes the backing pixels without a new acquisition. */
    @Test public void expiry() throws Exception {
        open(CapturePolicy.Mode.PUBLIC);accept();depart();JSONObject j=terminal();closed(j);assertEquals(4,matches());
        int[] held=SessionMemory.roi.get(SessionMemory.policy.generation,SystemClock.elapsedRealtime());assertNotNull(held);
        long remaining=SessionMemory.roi.expiresAt-SystemClock.elapsedRealtime();Thread.sleep(Math.max(0,remaining)+150);
        assertNull(SessionMemory.roi.get(SessionMemory.policy.generation,SystemClock.elapsedRealtime()));for(int pixel:held)assertEquals(0,pixel);assertEquals(1,j.getInt("usefulFrames"));
    }
}
