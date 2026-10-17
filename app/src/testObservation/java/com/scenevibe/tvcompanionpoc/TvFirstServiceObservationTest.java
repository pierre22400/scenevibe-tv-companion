package com.scenevibe.tvcompanionpoc;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.WindowManager;
import com.scenevibe.tvcompanionpoc.tvfirst.MediaEvidence;
import com.scenevibe.tvcompanionpoc.tvfirst.MediaEvidencePolicy;
import com.scenevibe.tvcompanionpoc.tvfirst.ObservationDiagnostics;
import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationSnapshot;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Actual service lifecycle and render sinks, with real preference snapshots and framework windows. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 35})
@LooperMode(LooperMode.Mode.PAUSED)
public final class TvFirstServiceObservationTest {
    private ServiceController<OverlayService> controller;
    private OverlayService service;
    private Map<String, Map<String, ?>> before;
    private final String[] preferences = {"cloud_track", "overlay", "cloud_identity", "cloud_secret", "installation", "pairing"};

    /** Seed a valid-looking pending or foreign package as inert bytes before starting the actual service. */
    @Before public void setup() throws Exception {
        assertTrue(BuildConfig.TVFIRST_OBSERVATION_ONLY);
        assertFalse(BuildConfig.ENABLE_LAN_DEV); assertEquals("", BuildConfig.CLOUD_ORIGIN);
        controller = Robolectric.buildService(OverlayService.class); service = controller.get();
        for (String name : preferences) service.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                .clear().putString("test-sentinel", name).commit();
        org.json.JSONObject envelope = M4PhaseFFixtures.envelope(true, 99);
        InstallRequest request = CloudV1InstallationAdapter.adapt(envelope, envelope.getString("deviceId")).request();
        InstallationStore store = new InstallationStore(new AndroidInstallationBackend(service));
        assertEquals(InstallationStore.CommitState.COMMITTED, store.commit(new InstallationSnapshot(
                request, VideoInstallationHandlers.registry().findCodec(request.codecId()).handlerId())));
        service.getSharedPreferences("cloud_track", Context.MODE_PRIVATE).edit().putString("ackRevision", "98").commit();
        assertEquals(InstallationStore.ReadState.SNAPSHOT, store.read().state());
        before = snapshot();
        controller.create();
        assertTrue(ReflectionHelpers.<Boolean>getField(service, "foregroundReady"));
    }

    /** Destruction must also leave every seeded preference and installation byte unchanged. */
    @After public void cleanup() {
        if (controller != null) controller.destroy();
        assertEquals(before, snapshot());
    }

    /** Read current actual preference maps; do not replace the installation code by a fake writer. */
    private Map<String, Map<String, ?>> snapshot() {
        Map<String, Map<String, ?>> result = new HashMap<>();
        for (String name : preferences) result.put(name,
                new HashMap<>(service.getSharedPreferences(name, Context.MODE_PRIVATE).getAll()));
        return result;
    }

    /** All historical mutation/runtime owners must remain unconstructed in observation mode. */
    private void assertInert() {
        assertEquals(before, snapshot());
        for (String field : new String[] {"cloudClient", "installationStore", "packageInstaller",
                "videoRuntimePorts", "trackScheduler", "commentaryServer", "sceneController",
                "renderer", "sceneRenderer"}) assertNull(field, ReflectionHelpers.getField(service, field));
        assertEquals(0L, (long) ReflectionHelpers.<Long>getField(service, "activeRevision"));
        WindowManager windows = service.getSystemService(WindowManager.class);
        org.robolectric.shadows.ShadowWindowManagerImpl windowShadow = org.robolectric.shadow.api.Shadow.extract(windows);
        assertTrue(windowShadow.getViews().isEmpty());
    }

    /** Use the actual start method, not a mirror of its routing condition. */
    private void start(String action) {
        assertEquals(Service.START_STICKY, service.onStartCommand(
                action == null ? null : new Intent(service, OverlayService.class).setAction(action), 0, 1));
        assertNotNull(ReflectionHelpers.getField(service, "mediaSessionProbe"));
        assertInert();
    }

    /** Every action, including connect/reset and system restart, reaches the inert branch first. */
    @Test public void allActualEntryActionsAreInert() {
        for (String action : new String[] {OverlayService.ACTION_TOP, OverlayService.ACTION_BOTTOM,
                OverlayService.ACTION_BOOT_PREPARE, OverlayService.ACTION_CLOUD_CONNECT,
                OverlayService.ACTION_CLOUD_RESET, null}) start(action);
    }

    /** Restore is bypassed even when durable bytes advertise an old pending foreign revision. */
    @Test public void pendingAndCorruptRestoreCannotArmOrWrite() {
        start(OverlayService.ACTION_BOOT_PREPARE);
        assertEquals("98", service.getSharedPreferences("cloud_track", Context.MODE_PRIVATE).getString("ackRevision", ""));
        assertNotNull(ObservationDiagnostics.latest()); assertInert();
    }

    /** An actual malformed generic snapshot is neither parsed, repaired nor removed by the service. */
    @Test public void corruptedGenericSnapshotIsNotTouched() {
        service.getSharedPreferences("cloud_track", Context.MODE_PRIVATE).edit()
                .putString(InstallationStore.SNAPSHOT_KEY, "corrupt-generic-test-value").commit();
        before = snapshot(); start(OverlayService.ACTION_BOOT_PREPARE); assertInert();
    }

    /** An unavailable source and missing overlay permission remain observable without entering legacy work. */
    @Test public void absentPermissionsProduceObservationOnly() {
        Settings.Secure.putString(service.getContentResolver(), "enabled_notification_listeners", "");
        start(OverlayService.ACTION_TOP);
        assertEquals(MediaEvidence.Availability.ACCESS_REQUIRED, ObservationDiagnostics.latest().availability);
        assertEquals(MediaEvidence.Proof.UNKNOWN, ObservationDiagnostics.latest().edition); assertInert();
    }

    /** Repeated startup reuses exactly one probe and never starts a second owner or Cloud client. */
    @Test public void repeatedEntryReusesOneProbe() {
        start(OverlayService.ACTION_TOP); Object probe = ReflectionHelpers.getField(service, "mediaSessionProbe");
        start(OverlayService.ACTION_CLOUD_CONNECT);
        assertSame(probe, ReflectionHelpers.getField(service, "mediaSessionProbe")); assertInert();
    }

    /** A real periodic probe callback remains inert during prolonged foreground-service operation. */
    @Test public void periodicSamplingCannotMutateInstallation() {
        start(OverlayService.ACTION_TOP); long first = ObservationDiagnostics.latest().sequence;
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(4, TimeUnit.SECONDS);
        assertTrue(ObservationDiagnostics.latest().sequence > first); assertInert();
    }

    /** Actual native scene sink refuses preflight/show even when called directly by a late DUE path. */
    @Test public void realSceneSinkCannotCreateWindow() {
        start(OverlayService.ACTION_TOP);
        SceneRuntimeController.SceneSink sink = ReflectionHelpers.getField(service, "sceneSink");
        assertFalse(sink.preflight(null)); sink.show(null); sink.hide(null); sink.hideAll();
        injectDueThroughRealController(sink); assertInert();
    }

    /** The actual legacy lazy-render entry is inert even outside normal scheduler dispatch. */
    @Test public void realLegacyRenderEntryCannotCreateWindow() {
        start(OverlayService.ACTION_TOP);
        ReflectionHelpers.callInstanceMethod(service, "showRenderer"); assertInert();
    }

    /** Capture the real listener closure, then prove service destruction invalidates it before any later callback. */
    @Test public void latePositiveCallbackAfterDestructionIsDiscarded() {
        start(OverlayService.ACTION_TOP);
        Object probe = ReflectionHelpers.getField(service, "mediaSessionProbe");
        MediaSessionProbe.Observer observer = ReflectionHelpers.getField(probe, "observer");
        controller.destroy(); controller = null;
        observer.onObservation(positive(100)); assertNull(ObservationDiagnostics.latest());
        ReflectionHelpers.callInstanceMethod(service, "showRenderer");
        SceneRuntimeController.SceneSink sink = ReflectionHelpers.getField(service, "sceneSink");
        sink.show(null); injectDueThroughRealController(sink); assertInert();
    }

    /** Even a complete positive injected batch reaches only the actual diagnostic callback. */
    @Test public void positiveCallbackCannotUnlockRealService() {
        start(OverlayService.ACTION_TOP);
        MediaSessionProbe.Observer observer = ReflectionHelpers.getField(
                ReflectionHelpers.getField(service, "mediaSessionProbe"), "observer");
        observer.onObservation(positive(100));
        assertTrue(MediaEvidencePolicy.evaluate(ObservationDiagnostics.latest(), SystemClock.elapsedRealtime()).conditionsComplete());
        assertFalse(MediaEvidencePolicy.evaluate(ObservationDiagnostics.latest(), SystemClock.elapsedRealtime()).renderingAllowed());
        SceneRuntimeController.SceneSink sink = ReflectionHelpers.getField(service, "sceneSink");
        sink.show(null); ReflectionHelpers.callInstanceMethod(service, "showRenderer"); assertInert();
    }

    /** A stopped-service reset entry in the actual Diagnostics Activity cannot clear the seeded store. */
    @Test public void diagnosticsResetIsClosedEvenWhenServiceStopped() {
        start(OverlayService.ACTION_TOP); controller.destroy(); controller = null;
        org.robolectric.android.controller.ActivityController<DiagnosticsActivity> activity =
                Robolectric.buildActivity(DiagnosticsActivity.class).create().start().resume();
        ReflectionHelpers.callInstanceMethod(activity.get(), "resetCloud");
        activity.pause().stop().destroy(); assertInert();
    }

    /** Returning to the unchanged main screen cannot migrate seeded credentials when the profile has no Cloud origin. */
    @Test public void mainScreenReturnPreservesActualStores() {
        service.getSharedPreferences("cloud_identity", Context.MODE_PRIVATE).edit()
                .putString("deviceToken", "synthetic-legacy-value-not-a-real-token").commit();
        before = snapshot(); start(OverlayService.ACTION_TOP);
        org.robolectric.android.controller.ActivityController<MainActivity> activity =
                Robolectric.buildActivity(MainActivity.class).create().start().resume();
        activity.pause().resume().pause().stop().destroy(); assertInert();
    }

    /** The real timed probe observes PLAYING/pause/resume and permission loss without treating an owner package as foreground. */
    @Test public void actualProbeTransitionsRemainInert() {
        Settings.Secure.putString(service.getContentResolver(), "enabled_notification_listeners",
                NotificationAccess.component(service).flattenToString());
        android.media.session.MediaSession session = new android.media.session.MediaSession(service, "synthetic-transition");
        android.media.session.MediaController media = new android.media.session.MediaController(service, session.getSessionToken());
        android.media.session.MediaSessionManager manager = service.getSystemService(android.media.session.MediaSessionManager.class);
        Shadows.shadowOf(manager).addController(media);
        Shadows.shadowOf(media).setPackageName("synthetic.prime.owner");
        try {
            start(OverlayService.ACTION_TOP);
            for (int state : new int[] {3, 2, 3}) {
                Shadows.shadowOf(media).setPlaybackState(new android.media.session.PlaybackState.Builder()
                        .setState(state, 123, 1, SystemClock.elapsedRealtime()).build());
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);
                assertEquals(state, ObservationDiagnostics.latest().sessions.get(0).playbackState);
                assertEquals(MediaEvidence.Proof.UNKNOWN, ObservationDiagnostics.latest().foreground);
                assertInert();
            }
            Settings.Secure.putString(service.getContentResolver(), "enabled_notification_listeners", "");
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);
            assertEquals(MediaEvidence.Availability.ACCESS_REQUIRED, ObservationDiagnostics.latest().availability);
            assertTrue(ObservationDiagnostics.latest().sessions.isEmpty()); assertInert();
        } finally {
            session.release(); Shadows.shadowOf(manager).clearControllers();
        }
    }

    /** Build positive synthetic coordinates solely to exercise the real listener's lack of execution authority. */
    private MediaEvidence positive(long sequence) {
        long now = SystemClock.elapsedRealtime();
        MediaEvidence.Session s = new MediaEvidence.Session("synthetic", "test.package", "synthetic-id",
                "synthetic-title", null, 3, 10, Math.max(1, now), 1, 10, -1);
        return new MediaEvidence(sequence, now, MediaEvidence.Availability.AVAILABLE, true, true, 1,
                Collections.singletonList(s), MediaEvidence.Proof.OBSERVED, MediaEvidence.Proof.OBSERVED,
                MediaEvidence.Proof.OBSERVED, MediaEvidence.Proof.OBSERVED);
    }

    /** Drive the unmodified controller with a known synthetic scene into the service's actual sink. */
    private void injectDueThroughRealController(SceneRuntimeController.SceneSink sink) {
        SceneRuntimeController runtime = new SceneRuntimeController(sink);
        OverlayManifest.Scene scene = new OverlayManifest.Scene("synthetic-due", 0, 1000, Collections.emptyList());
        runtime.loadManifest(1, new OverlayManifest("video:synthetic", "video", "synthetic", "media", "freeze",
                Collections.singletonList(scene)));
        runtime.onEventDue("synthetic-due");
        assertFalse(runtime.hasVisibleScene());
    }
}
