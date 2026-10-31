package com.scenevibe.accessibilityprobe004;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * API31 emulator-only experiments against the app-owned virtual provider. The critical test
 * compares an event-only campaign with a periodic one, preserving both synthetic proof logs.
 * No Netflix/Disney app, Sony, protected pixels, network or playback command is involved.
 */
@RunWith(AndroidJUnit4.class)
public final class AutonomousProbeTest {
    private Instrumentation instrumentation;
    private ProbeActivity activity;
    private ProbeService service;

    /** Refuse physical hardware before setup and await an idle owned accessibility worker. */
    @Before public void prepare() throws Exception {
        EmulatorServiceControl.requireEmulator();
        instrumentation = InstrumentationRegistry.getInstrumentation();
        activity = (ProbeActivity) instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), ProbeActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        service = EmulatorServiceControl.connect(instrumentation); assertNotNull(service);
        instrumentation.runOnMainSync(() -> service.stop(SafeRow.Reason.OWNER_STOP));
        waitIdle(); service.store().deleteStopped();
    }

    /** Stop only our synthetic collection and close only our own Activity. */
    @After public void cleanup() throws Exception {
        if (service != null) { instrumentation.runOnMainSync(() -> service.stop(SafeRow.Reason.OWNER_STOP)); waitIdle(); }
        if (activity != null) instrumentation.runOnMainSync(activity::finish);
    }

    /** Prove a silent virtual change is missed event-only but freshly observed on a periodic tick. */
    @Test public void silentChangeRequiresAutonomousTick() throws Exception {
        arm(ObservationCadence.Mode.EVENT_ONLY_CONTROL); awaitQuiet(); markAndChange();
        SystemClock.sleep(2600); String eventOnly = read();
        assertEquals("A native callback would invalidate the silent comparator", 0, nativeAfterMark(eventOnly));
        assertFalse("Event-only observer unexpectedly saw the silent change", candidateAfterMark(eventOnly, false));
        stopAndSave("positive-event-only.jsonl");
        instrumentation.runOnMainSync(() -> activity.silentFixture().setSilentPhase(0));
        arm(ObservationCadence.Mode.NORMAL); awaitQuiet(); markAndChange();
        long deadline = SystemClock.uptimeMillis() + 8000;
        while (!candidateAfterMark(read(), true) && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100);
        String periodic = read();
        assertTrue("No fresh periodic observation of the silent provider", candidateAfterMark(periodic, true));
        assertEquals("The mutation must not have triggered an Android callback", 0, nativeAfterMark(periodic));
        assertTrue(periodic.contains("FRESH_OK")); assertTrue(periodic.contains("\"rangeUnit\":\"UNKNOWN\""));
        stopAndSave("positive-periodic.jsonl");
    }

    /** The actual manual-control buttons reset each campaign and mark the delayed silent mutation. */
    @Test public void delayedOwnerControlIsRepeatable() throws Exception {
        instrumentation.runOnMainSync(() -> {
            activity.silentFixture().setSilentPhase(1);
            activity.getWindow().getDecorView().findViewWithTag("Armer le profil sélectionné").performClick();
        });
        assertTrue(service.controller().active()); SystemClock.sleep(700); waitIdle();
        assertTrue("Manual arm did not reset the owned fixture", read().contains("\"rangeCurrent\":10.0"));
        instrumentation.runOnMainSync(() -> activity.getWindow().getDecorView()
            .findViewWithTag("Contrôle silencieux — changer les nœuds dans 5 secondes").performClick());
        long deadline = SystemClock.uptimeMillis() + 9000;
        while (!candidateAfterMark(read(), true) && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100);
        String log = read(); assertTrue(candidateAfterMark(log, true)); assertEquals(0, nativeAfterMark(log));
        stopAndSave("positive-owner-buttons.jsonl");
    }

    /** Missing target roots still produce periodic start/end and explicit unavailable results. */
    @Test public void emptyTargetStillProducesTicks() throws Exception {
        instrumentation.runOnMainSync(() -> assertTrue(service.arm(ProbeProfile.NETFLIX)));
        SystemClock.sleep(4400);
        String log = read(); assertTrue(log.contains("PERIODIC_TICK")); assertTrue(log.contains("TICK_END"));
        assertTrue(log.contains("ROOT_UNAVAILABLE")); assertFalse(log.contains("AD_START"));
        assertTrue(service.controller().active());
    }

    /** OFF cancels future ticks and blocks late worker writes before export becomes available. */
    @Test public void offStopsTicksAndLateWrites() throws Exception {
        arm(ObservationCadence.Mode.NORMAL); SystemClock.sleep(300);
        instrumentation.runOnMainSync(() -> service.stop(SafeRow.Reason.OWNER_STOP));
        long bytes = service.store().bytes(); waitIdle(); SystemClock.sleep(2300);
        assertEquals(bytes, service.store().bytes()); assertFalse(service.controller().active());
        File copy = service.store().exportLocal(new File(activity.getCacheDir(), "autonomous-export"));
        assertEquals(JournalStore.sha256(copy), JournalStore.sha256(service.store().latest()));
    }

    /** A fresh numeric synthetic range is available without assigning milliseconds or reading text. */
    @Test public void syntheticRangeRemainsUnknownUnit() throws Exception {
        arm(ObservationCadence.Mode.NORMAL); SystemClock.sleep(2500);
        String log = read(); assertTrue(log.contains("\"rangeState\":\"VALID\""));
        assertTrue(log.contains("\"rangeCurrent\":10.0")); assertTrue(log.contains("\"rangeUnit\":\"UNKNOWN\""));
        assertFalse(log.contains("Programme synthétique")); assertFalse(log.contains("silent_control"));
    }

    /** Old callbacks remain typed diagnostics, with no freshness claim or event-triggered query. */
    @Test public void staleCallbackDoesNotInterpretSource() throws Exception {
        arm(ObservationCadence.Mode.NORMAL); awaitQuiet();
        instrumentation.runOnMainSync(() -> {
            AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
            event.setPackageName(ProbeProfile.OWN_CONTROL.packageName); event.setEventTime(SystemClock.uptimeMillis() - 6000);
            service.onAccessibilityEvent(event); event.recycle();
        });
        assertTrue(read().contains("STALE_EVENT"));
    }

    /** Dense cadence is selected before arm and cannot be used to mutate the active campaign. */
    @Test public void denseModeIsExplicitAndImmutable() throws Exception {
        arm(ObservationCadence.Mode.DENSE_30_SECONDS); SystemClock.sleep(1400);
        assertTrue(read().contains("\"pollMode\":\"DENSE_30_SECONDS\"")); assertTrue(read().contains("PERIODIC_TICK"));
        instrumentation.runOnMainSync(() -> assertFalse(service.arm(ProbeProfile.DISNEY_PLUS, ObservationCadence.Mode.NORMAL)));
        assertEquals(ProbeProfile.OWN_CONTROL, service.controller().profile());
    }

    /** Arm only the synthetic profile and await the initial traversal outside the UI thread. */
    private void arm(ObservationCadence.Mode mode) throws Exception {
        waitIdle(); instrumentation.runOnMainSync(() -> assertTrue(service.arm(ProbeProfile.OWN_CONTROL, mode)));
        SystemClock.sleep(300); waitIdle();
    }

    /** Wait for target callbacks and the worker to become quiet before a silent mutation. */
    private void awaitQuiet() throws Exception { SystemClock.sleep(1700); waitIdle(); }

    /** Write a synthetic marker and change internal provider state without sending any event. */
    private void markAndChange() {
        instrumentation.runOnMainSync(() -> {
            service.controller().record(new SafeRow(SafeRow.Kind.CONTROL_MARK).number("syntheticPhase", 1));
            activity.silentFixture().setSilentPhase(1);
        });
    }

    /** Await one bounded in-flight operation, never blocking the Activity's Binder-serving thread. */
    private void waitIdle() throws Exception {
        long deadline = SystemClock.uptimeMillis() + 6000;
        while (service.observing() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
        assertFalse("Owned worker did not finish", service.observing());
    }

    /** Read only this test application's synthetic JSONL bytes. */
    private String read() throws Exception {
        String snapshot = new String(Files.readAllBytes(service.store().latest().toPath()), StandardCharsets.UTF_8);
        return snapshot.substring(0, snapshot.lastIndexOf('\n') + 1);
    }

    /** Count native callbacks strictly after the explicit synthetic mutation marker. */
    private int nativeAfterMark(String log) throws Exception {
        boolean marked = false; int count = 0;
        for (String line : log.split("\n")) {
            JSONObject row = new JSONObject(line); if ("CONTROL_MARK".equals(row.getString("kind"))) marked = true;
            else if (marked && row.has("eventType")) count++;
        }
        return count;
    }

    /** Find only freshly projected node candidates, optionally requiring the periodic trigger. */
    private boolean candidateAfterMark(String log, boolean periodic) throws Exception {
        boolean marked = false;
        for (String line : log.split("\n")) {
            JSONObject row = new JSONObject(line); if ("CONTROL_MARK".equals(row.getString("kind"))) marked = true;
            if (marked && "NODE".equals(row.getString("kind")) && "FRESH_OK".equals(row.optString("quality"))
                && "AD_LABEL_CANDIDATE".equals(row.optString("labelCategory"))
                && (!periodic || "PERIODIC_TICK".equals(row.optString("trigger")))) return true;
        }
        return false;
    }

    /** Preserve both stopped positive-control journals for independent artifact review. */
    private void stopAndSave(String fixedName) throws Exception {
        instrumentation.runOnMainSync(() -> service.stop(SafeRow.Reason.OWNER_STOP)); waitIdle();
        Files.write(new File(activity.getFilesDir(), fixedName).toPath(), Files.readAllBytes(service.store().latest().toPath()));
    }
}
