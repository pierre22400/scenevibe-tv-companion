package com.scenevibe.tvcompanionpoc;

import android.app.Service;
import android.content.Intent;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.TextView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSettings;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.*;

/** Exercises the TV launcher and admission owner on simulated Android 12. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public final class NetflixManualLaunchActivityTest {
    private ActivityController<NetflixManualLaunchActivity> activity;
    private ServiceController<NetflixManualSyncService> owner;
    private NetflixManualSyncService service;
    private HoldingExecutor worker;

    /** Prepare the real Activity and control when the simulated worker finishes. */
    @Before public void setUp() {
        ShadowSettings.setCanDrawOverlays(true);
        activity = Robolectric.buildActivity(NetflixManualLaunchActivity.class).setup();
        owner = Robolectric.buildService(NetflixManualSyncService.class).create();
        service = owner.get();
        ReflectionHelpers.<ExecutorService>getField(service, "decoderWorker").shutdownNow();
        worker = new HoldingExecutor();
        ReflectionHelpers.setField(service, "decoderWorker", worker);
    }

    /** End the simulated lifecycle without retaining views or callbacks. */
    @After public void tearDown() {
        if (activity != null) activity.pause().stop().destroy();
        if (owner != null) owner.destroy();
    }

    /** Drive ARM from the visible button and verify background, recreation and repeated ARM. */
    @Test public void dpadPreparesOneSilentSessionAndKeepsItAcrossLauncherLifecycle() {
        Intent arm = clickPrepareWithDpad();
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(arm, 0, 1));
        assertTrue(status().contains("Préparation en cours"));
        assertFalse(prepare().isEnabled());
        service.onStartCommand(arm, 0, 2);
        assertEquals(1, worker.submissions);

        activity.pause().stop().destroy();
        activity = null;
        completeAdmission(NetflixManualLaunchActivityTest::decodeMp3);
        assertEquals(NetflixManualSyncService.LaunchStatus.READY, NetflixManualSyncService.launchStatus());
        NetflixManualClock clock = ReflectionHelpers.getField(service, "clock");
        assertFalse(clock.running());
        assertFalse(clock.anchored());
        assertEquals(32 * 60 * 1000L, clock.horizon());

        activity = Robolectric.buildActivity(NetflixManualLaunchActivity.class).setup();
        assertTrue(status().contains("maintiens MUTE"));
        assertFalse(prepare().isEnabled());
        service.onStartCommand(arm, 0, 3);
        assertSame(clock, ReflectionHelpers.getField(service, "clock"));
        assertEquals(1, worker.submissions);
        NetflixManualSyncService.requestControlsFromShortcut();
        assertNotNull(ReflectionHelpers.getField(service, "controls"));
        service.onStartCommand(new Intent().setAction("CLOSE"), 0, 4);
        assertNull(ReflectionHelpers.getField(service, "controls"));
        assertSame(clock, ReflectionHelpers.getField(service, "clock"));
        assertFalse(clock.running());

        Button stop = ReflectionHelpers.getField(activity.get(), "stop");
        assertTrue(stop.requestFocus());
        pressOk(stop);
        assertTrue(status().contains("Session arrêtée"));
        assertFalse(clock.editable());
        assertTrue(prepare().isEnabled());
    }

    /** A completion posted before STOP must never reactivate a retired owner. */
    @Test public void stoppedPreparationRejectsLateAdmissionAndShortcut() {
        service.onStartCommand(clickPrepareWithDpad(), 0, 1);
        NetflixManualSyncService.requestStopFromLauncher();
        completeAdmission(NetflixManualLaunchActivityTest::decodeMp3);
        assertEquals(NetflixManualSyncService.LaunchStatus.STOPPED, NetflixManualSyncService.launchStatus());
        assertNull(ReflectionHelpers.getField(service, "clock"));
        NetflixManualSyncService.requestControlsFromShortcut();
        assertNull(ReflectionHelpers.getField(service, "controls"));
    }

    /** A rejected asset reaches the visible failure state and permits a new explicit attempt. */
    @Test public void decoderFailureShowsRetryWithoutReadyOrShortcut() {
        service.onStartCommand(clickPrepareWithDpad(), 0, 1);
        completeAdmission(bytes -> { throw new IllegalStateException("synthetic decoder rejection"); });
        assertTrue(status().contains("Échec de préparation"));
        assertTrue(prepare().isEnabled());
        assertEquals("ARM", clickPrepareWithDpad().getAction());
        NetflixManualSyncService.requestControlsFromShortcut();
        assertNull(ReflectionHelpers.getField(service, "controls"));
    }

    /** Missing overlay permission leads to settings and leaves the admission idle. */
    @Test public void permissionIsRequestedWithoutStartingAdmission() {
        ShadowSettings.setCanDrawOverlays(false);
        pressOk(prepare());
        assertNull(Shadows.shadowOf(activity.get()).getNextStartedService());
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Shadows.shadowOf(activity.get()).getNextStartedActivity().getAction());
        assertEquals(0, worker.submissions);
    }

    /** Destroying the owner clears READY, and a null restart cannot restore the old clock. */
    @Test public void destroyedServiceDoesNotRestoreItsSessionFromCache() {
        service.onStartCommand(clickPrepareWithDpad(), 0, 1);
        completeAdmission(NetflixManualLaunchActivityTest::decodeMp3);
        assertEquals(NetflixManualSyncService.LaunchStatus.READY, NetflixManualSyncService.launchStatus());
        owner.destroy();
        owner = Robolectric.buildService(NetflixManualSyncService.class).create();
        assertEquals(Service.START_NOT_STICKY, owner.get().onStartCommand(null, 0, 2));
        assertNull(ReflectionHelpers.getField(owner.get(), "clock"));
        assertTrue(status().contains("Session arrêtée"));
    }

    /** Feed the actual bundled MP3s through a bench decoder and use the production admission path. */
    private void completeAdmission(StudioMultimediaPackage.Decoder decoder) {
        ReflectionHelpers.callInstanceMethod(service, "admitFixture",
                ReflectionHelpers.ClassParameter.from(StudioMultimediaPackage.Decoder.class, decoder));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    /** Read the explicit foreground command emitted by D-pad OK. */
    private Intent clickPrepareWithDpad() {
        assertTrue(prepare().requestFocus());
        pressOk(prepare());
        Intent arm = Shadows.shadowOf(activity.get()).getNextStartedService();
        assertNotNull(arm);
        assertEquals("ARM", arm.getAction());
        assertEquals(NetflixManualSyncService.class.getName(), arm.getComponent().getClassName());
        return arm;
    }

    /** Return the launcher preparation button. */
    private Button prepare() {
        return ReflectionHelpers.getField(activity.get(), "start");
    }

    /** Return the actual status shown by the launcher. */
    private String status() {
        TextView view = ReflectionHelpers.getField(activity.get(), "status");
        return view.getText().toString();
    }

    /** Deliver both halves of an explicit remote OK press. */
    private static void pressOk(Button button) {
        button.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
        button.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    /** Measure the duration of each MP3 with FFmpeg in the simulated Android test. */
    private static long decodeMp3(byte[] bytes) throws Exception {
        Path file = Files.createTempFile("scenevibe-multiplatform-", ".mp3");
        try {
            Files.write(file, bytes);
            Process decoder = new ProcessBuilder("ffmpeg", "-v", "error", "-i", file.toString(),
                    "-f", "s16le", "-ac", "1", "-ar", "44100", "pipe:1")
                    .redirectErrorStream(true).start();
            byte[] pcm = decoder.getInputStream().readAllBytes();
            if (decoder.waitFor() != 0 || pcm.length == 0) {
                throw new IllegalArgumentException("MP3_DECODE_FAILED");
            }
            return Math.round(pcm.length / 88.2);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** Hold worker dispatch so success, failure and stop order can be exercised deterministically. */
    private static final class HoldingExecutor extends ScheduledThreadPoolExecutor {
        private int submissions;

        /** Allocate no executing thread while the worker is held. */
        HoldingExecutor() {
            super(1);
        }

        /** Count a submitted admission without racing the main-thread test. */
        @Override public void execute(Runnable command) {
            submissions++;
        }
    }
}
