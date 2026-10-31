package com.scenevibe.accessibilityprobe004;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
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
 * API31 emulator tests of our own Activity/service only. Synthetic labels and deliberate
 * private-looking strings are never observations from Netflix, Disney+ or a physical device.
 */
@RunWith(AndroidJUnit4.class)
public final class AndroidProbeTest {
    private Instrumentation instrumentation;
    private ProbeService service;
    private ProbeActivity activity;

    /** Keep accessibility services unsuppressed, await the owned service, and reset only its files. */
    @Before public void prepare() throws Exception {
        EmulatorServiceControl.requireEmulator();
        instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        Intent intent = new Intent(instrumentation.getTargetContext(), ProbeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = (ProbeActivity) instrumentation.startActivitySync(intent);
        service = EmulatorServiceControl.connect(instrumentation);
        assertNotNull("Owned service did not connect", service);
        instrumentation.runOnMainSync(new Reset(service));
        long idleDeadline = SystemClock.uptimeMillis() + 6000;
        while (service.observing() && SystemClock.uptimeMillis() < idleDeadline) SystemClock.sleep(50);
        assertFalse("Previous worker did not finish", service.observing());
    }

    /** Stop diagnostic collection and close the owned Activity after each software test. */
    @After public void cleanup() {
        if (service != null) instrumentation.runOnMainSync(new Stop(service));
        if (activity != null) instrumentation.runOnMainSync(activity::finish);
    }

    /** Inspect actual package permissions, protected service and absence of Companion identity. */
    @Test public void manifestBoundaries() throws Exception {
        PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), PackageManager.GET_PERMISSIONS | PackageManager.GET_SERVICES);
        assertEquals("com.scenevibe.accessibilityprobe004", info.packageName);
        assertTrue(info.requestedPermissions == null || info.requestedPermissions.length == 0);
        assertEquals(1, info.services.length);
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE", info.services[0].permission);
    }

    /** Read the actual service mask/package list after arm and OFF, not only XML constants. */
    @Test public void effectiveConfiguration() {
        instrumentation.runOnMainSync(new Arm(service, ProbeProfile.NETFLIX));
        AccessibilityServiceInfo info = service.getServiceInfo();
        assertEquals(AccessibilityEvent.TYPES_ALL_MASK, info.eventTypes);
        assertArrayEquals(new String[] { "com.netflix.ninja" }, info.packageNames);
        assertEquals(0, info.notificationTimeout);
        assertEquals(AccessibilityServiceInfo.FEEDBACK_GENERIC, info.feedbackType);
        assertTrue((info.flags & AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS) != 0);
        assertTrue((info.getCapabilities() & AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT) != 0);
        assertEquals(0, info.getCapabilities() & AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES);
        instrumentation.runOnMainSync(new Stop(service));
        assertEquals(0, service.getServiceInfo().eventTypes);
    }

    /** Produce real own-Activity events and verify semantic projection plus accessible root reads. */
    @Test public void positiveControlEventsAndRoots() throws Exception {
        instrumentation.runOnMainSync(new Arm(service, ProbeProfile.OWN_CONTROL));
        SystemClock.sleep(400);
        instrumentation.runOnMainSync(new FixtureClick(activity));
        long deadline = SystemClock.uptimeMillis() + 8_000;
        while (SystemClock.uptimeMillis() < deadline && !read().contains("AD_LABEL_CANDIDATE")) SystemClock.sleep(100);
        String log = read();
        assertTrue("No own-control event received", log.contains("AD_LABEL_CANDIDATE"));
        assertTrue(log.contains("OWN_CONTROL"));
        assertTrue(log.contains("eventTimeUptimeMs"));
        boolean nodes = false;
        for (String line : log.split("\n")) {
            JSONObject row = new JSONObject(line);
            nodes |= row.optInt("targetNodeCount") > 0;
        }
        assertTrue("No target root read in own fixture", nodes);
        assertFalse(log.contains("\"text\":"));
    }

    /** Project a synthetic secret-looking own View text without retaining it in the log. */
    @Test public void syntheticPrivacyAndNullCallback() throws Exception {
        instrumentation.runOnMainSync(new Arm(service, ProbeProfile.OWN_CONTROL));
        instrumentation.runOnMainSync(new PrivateFixture(activity));
        instrumentation.runOnMainSync(new NullCallback(service));
        SystemClock.sleep(500);
        String log = read(); assertTrue(log.contains("CALLBACK_NULL"));
        assertFalse(log.contains("PRIVATE_ACCOUNT_SYNTHETIC_123"));
        assertFalse(log.contains("private@example.test"));
    }

    /** A large owned tree stays below the traversal budget and reports truncation. */
    @Test public void nodeBudgetAndTruncation() throws Exception {
        instrumentation.runOnMainSync(new LargeFixture(activity));
        SystemClock.sleep(500);
        instrumentation.runOnMainSync(new Arm(service, ProbeProfile.OWN_CONTROL));
        SystemClock.sleep(400);
        instrumentation.runOnMainSync(new FixtureClick(activity));
        SystemClock.sleep(1200);
        boolean truncated = false;
        for (String line : read().split("\n")) {
            JSONObject row = new JSONObject(line);
            if (row.has("nodeReadCount")) {
                assertTrue(row.getInt("nodeReadCount") <= 200);
                truncated |= row.optBoolean("truncated");
            }
        }
        assertTrue("Large fixture did not exercise the node limit", truncated);
    }

    /** A narrow but deep owned tree must report its depth limit without requiring 200 nodes. */
    @Test public void depthBudgetAndTruncation() throws Exception {
        instrumentation.runOnMainSync(new DeepFixture(activity));
        SystemClock.sleep(500);
        instrumentation.runOnMainSync(new Arm(service, ProbeProfile.OWN_CONTROL));
        instrumentation.runOnMainSync(new RootSurvey(service));
        assertTrue(read().contains("\"truncated\":true"));
    }

    /** Reconnect the Android adapter and confirm it resets the live mask and volatile campaign. */
    @Test public void reconnectDisarms() {
        instrumentation.runOnMainSync(new Arm(service, ProbeProfile.OWN_CONTROL));
        instrumentation.runOnMainSync(service::onServiceConnected);
        assertFalse(service.controller().active()); assertEquals(0, service.getServiceInfo().eventTypes);
    }

    /** Verify stopped export bytes and that OFF refuses further direct null callbacks. */
    @Test public void stoppedExportAndOff() throws Exception {
        instrumentation.runOnMainSync(new Arm(service, ProbeProfile.OWN_CONTROL));
        instrumentation.runOnMainSync(new Stop(service));
        long bytes = service.store().bytes();
        instrumentation.runOnMainSync(new NullCallback(service)); assertEquals(bytes, service.store().bytes());
        File destination = new File(activity.getCacheDir(), "software-export");
        File copy = service.store().exportLocal(destination);
        assertEquals(JournalStore.sha256(service.store().latest()), JournalStore.sha256(copy));
    }

    /** Read only the owned synthetic journal for assertions; it is not a platform capture. */
    private String read() throws Exception {
        String snapshot = new String(Files.readAllBytes(service.store().latest().toPath()), StandardCharsets.UTF_8);
        return snapshot.substring(0, snapshot.lastIndexOf('\n') + 1);
    }

    /** Main-thread setup action limited to the test application's private journal. */
    private static final class Reset implements Runnable {
        private final ProbeService service;
        /** Store the owned service reference. */
        Reset(ProbeService service) { this.service = service; }
        /** Stop and remove only synthetic test journals. */
        @Override public void run() { service.stop(SafeRow.Reason.OWNER_STOP); service.store().deleteStopped(); }
    }
    /** Main-thread explicit profile arming for an owned emulator service. */
    private static final class Arm implements Runnable {
        private final ProbeService service; private final ProbeProfile profile;
        /** Store the fixed test profile. */
        Arm(ProbeService service, ProbeProfile profile) { this.service = service; this.profile = profile; }
        /** Require successful arming; no player transport is invoked. */
        @Override public void run() {
            assertSame("Test retained a stale service instance", ProbeService.current(), service);
            assertTrue("Arm refused: connected=" + service.controller().connected() + " active=" + service.controller().active()
                + " busy=" + service.observing(), service.arm(profile));
        }
    }
    /** Main-thread explicit stop action. */
    private static final class Stop implements Runnable {
        private final ProbeService service;
        /** Store the service being tested. */
        Stop(ProbeService service) { this.service = service; }
        /** Close only this diagnostic collection. */
        @Override public void run() { service.stop(SafeRow.Reason.OWNER_STOP); }
    }
    /** Main-thread click on our own synthetic control, never on a platform app. */
    private static final class FixtureClick implements Runnable {
        private final ProbeActivity activity;
        /** Store the owned Activity. */
        FixtureClick(ProbeActivity activity) { this.activity = activity; }
        /** Trigger this application's test button only. */
        @Override public void run() { activity.findViewById(R.id.fixture_button).performClick(); }
    }
    /** Synthetic private-looking data exercises expurgation without real personal data. */
    private static final class PrivateFixture implements Runnable {
        private final ProbeActivity activity;
        /** Store the owned Activity. */
        PrivateFixture(ProbeActivity activity) { this.activity = activity; }
        /** Modify only the owned fixture View. */
        @Override public void run() {
            TextView text = activity.findViewById(R.id.fixture_label);
            text.setText("PRIVATE_ACCOUNT_SYNTHETIC_123"); text.setContentDescription("private@example.test");
        }
    }
    /** Null-source callback robustness test, explicitly synthetic. */
    private static final class NullCallback implements Runnable {
        private final ProbeService service;
        /** Store the owned service. */
        NullCallback(ProbeService service) { this.service = service; }
        /** Exercise the documented nullable diagnostic path. */
        @Override public void run() { service.onAccessibilityEvent(null); }
    }
    /** Own-app wide hierarchy for the traversal ceiling. */
    private static final class LargeFixture implements Runnable {
        private final ProbeActivity activity;
        /** Store the owned Activity. */
        LargeFixture(ProbeActivity activity) { this.activity = activity; }
        /** Add only deterministic synthetic child nodes to the local fixture. */
        @Override public void run() {
            LinearLayout parent = (LinearLayout) activity.findViewById(R.id.fixture_label).getParent();
            for (int i = 0; i < 230; i++) {
                TextView child = new TextView(activity); child.setText("SYNTHETIC CONTROL");
                child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES); parent.addView(child);
            }
        }
    }

    /** Own-app deep hierarchy for the depth ceiling. */
    private static final class DeepFixture implements Runnable {
        private final ProbeActivity activity;
        /** Store only the owned Activity. */
        DeepFixture(ProbeActivity activity) { this.activity = activity; }
        /** Add deterministic visible-accessibility containers, without touching a platform app. */
        @Override public void run() {
            LinearLayout parent = (LinearLayout) activity.findViewById(R.id.fixture_label).getParent();
            for (int i = 0; i < 20; i++) {
                LinearLayout child = new LinearLayout(activity);
                child.setOrientation(LinearLayout.VERTICAL);
                child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                parent.addView(child); parent = child;
            }
            TextView leaf = new TextView(activity); leaf.setText("SYNTHETIC DEEP CONTROL"); parent.addView(leaf);
        }
    }

    /** Root-only traversal of the actual owned accessibility hierarchy, not a fabricated NodeInfo. */
    private static final class RootSurvey implements Runnable {
        private final ProbeService service;
        /** Store the owned service. */
        RootSurvey(ProbeService service) { this.service = service; }
        /** Read the active own root and emit bounded results for assertions. */
        @Override public void run() {
            NodeSurvey survey = new NodeSurvey(service.controller(), new TextProjection());
            survey.root(service.getRootInActiveWindow(), SafeRow.Origin.ACTIVE_ROOT); survey.finish();
        }
    }
}
