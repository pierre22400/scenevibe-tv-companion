package com.scenevibe.tvcompanionpoc;

import android.content.ComponentName;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import com.scenevibe.tvcompanionpoc.audio.LocalAudioPort;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSettings;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.*;

/** Run the Android window and service over the real cached calendar, with recording render ports. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public final class NetflixManualPanelTest {
    private ServiceController<NetflixManualSyncService> owner;
    private NetflixManualSyncService service;
    private CompanionControlPanel panel;
    private NetflixManualClock clock;
    private NetflixLocalAdmission.Selection selected;
    private StudioMultimediaRuntime runtime;
    private final long[] elapsed = {50000};
    private final Ports effects = new Ports();

    /** Load the bundled fixture through the actual private cache before testing window input. */
    @Before public void setup() throws Exception {
        ShadowSettings.setCanDrawOverlays(true);
        owner = Robolectric.buildService(NetflixManualSyncService.class).create();
        service = owner.get();
        loadFixture("netflix003a");
    }

    /** Persist and reopen the selected package before attaching the manual clock. */
    private void loadFixture(String fixture) throws Exception {
        if (clock != null) clock.revoke();
        byte[] manifest;
        byte[] track;
        try (var stream = service.getAssets().open(fixture + "/selection.json")) {
            manifest = stream.readAllBytes();
        }
        try (var stream = service.getAssets().open(fixture + "/track.zip")) {
            track = stream.readAllBytes();
        }
        JSONObject reference = new JSONObject(new String(manifest, StandardCharsets.UTF_8));
        selected = new NetflixLocalAdmission.Selection(reference.getString("trackId"),
                reference.getString("programId"), reference.getLong("revision"), reference.getString("sha256"));
        StudioMultimediaPackage.Decoder decoder = "netflix003c".equals(fixture)
                ? NetflixManualPanelTest::decodeMp3 : bytes -> 500;
        NetflixLocalAdmission admission = NetflixLocalAdmission.open(service, selected, decoder);
        admission.admit(track);
        admission = NetflixLocalAdmission.open(service, selected, decoder);
        runtime = new StudioMultimediaRuntime(effects, effects, false, true, null);
        clock = admission.activateManual((verified, permitted) -> runtime, () -> elapsed[0]);
        ReflectionHelpers.setField(service, "clock", clock);
        ReflectionHelpers.setField(service, "selection", selected);
        panel = ReflectionHelpers.getField(service, "panel");
    }

    /** Simulate the framework callback without assuming Sony sends it. */
    @Test public void shortcutRequiresClickAndRejectsRetiredConnections() {
        var bridgeOwner = Robolectric.buildService(NetflixShortcutService.class).create();
        try {
            NetflixShortcutService bridge = bridgeOwner.get();
            bridge.onServiceConnected();
            android.accessibilityservice.AccessibilityButtonController source =
                    ReflectionHelpers.getField(bridge, "controller");
            android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback first =
                    ReflectionHelpers.getField(bridge, "callback");
            assertNull(ReflectionHelpers.getField(service, "controls"));
            first.onClicked(source);
            assertNotNull(ReflectionHelpers.getField(service, "controls"));
            command("CLOSE");
            bridge.onServiceConnected();
            first.onClicked(source);
            assertNull(ReflectionHelpers.getField(service, "controls"));
            android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback current =
                    ReflectionHelpers.getField(bridge, "callback");
            source = ReflectionHelpers.getField(bridge, "controller");
            current.onClicked(source);
            assertNotNull(ReflectionHelpers.getField(service, "controls"));
            command("STOP");
            current.onClicked(source);
            assertNull(ReflectionHelpers.getField(service, "controls"));
            bridge.onUnbind(new Intent());
            assertNull(ReflectionHelpers.getField(bridge, "callback"));
        } finally {
            bridgeOwner.destroy();
        }
    }

    /** Verify distinct diagnostic markers for a refused callback and a shown menu. */
    @Test public void shortcutTracesReadinessAndDisplaySeparately() {
        var bridgeOwner = Robolectric.buildService(NetflixShortcutService.class).create();
        try {
            NetflixShortcutService bridge = bridgeOwner.get();
            bridge.onServiceConnected();
            android.accessibilityservice.AccessibilityButtonController source =
                    ReflectionHelpers.getField(bridge, "controller");
            android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback callback =
                    ReflectionHelpers.getField(bridge, "callback");
            ReflectionHelpers.setField(service, "clock", null);
            callback.onClicked(source);
            ReflectionHelpers.setField(service, "clock", clock);
            callback.onClicked(source);
            callback.onClicked(source);
            bridge.onUnbind(new Intent());
            assertEquals(List.of("SHORTCUT_CONNECTED", "SHORTCUT_CALLBACK_RECEIVED",
                    "SHORTCUT_OPEN_REFUSED_NOT_READY", "SHORTCUT_CALLBACK_RECEIVED",
                    "SHORTCUT_OPEN_ACCEPTED", "SHORTCUT_PANEL_OPENED",
                    "SHORTCUT_CALLBACK_RECEIVED", "SHORTCUT_OPEN_REFUSED_ALREADY_OPEN",
                    "SHORTCUT_DISCONNECTED"),
                    org.robolectric.shadows.ShadowLog.getLogsForTag("SceneVibeShortcut")
                            .stream().map(item -> item.msg).toList());
        } finally {
            ReflectionHelpers.setField(service, "clock", clock);
            bridgeOwner.destroy();
        }
    }

    /** Inspect actual MP3 bytes with FFmpeg on the software bench. */
    private static long decodeMp3(byte[] bytes) throws Exception {
        java.nio.file.Path file = java.nio.file.Files.createTempFile("scenevibe-003c-", ".mp3");
        try {
            java.nio.file.Files.write(file, bytes);
            Process decoder = new ProcessBuilder("ffmpeg", "-v", "error", "-i", file.toString(),
                    "-f", "s16le", "-ac", "1", "-ar", "44100", "pipe:1")
                    .redirectErrorStream(true).start();
            byte[] pcm = decoder.getInputStream().readAllBytes();
            if (decoder.waitFor() != 0 || pcm.length == 0) throw new IllegalArgumentException("MP3_DECODE_FAILED");
            return Math.round(pcm.length / 88.2);
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
    }

    /** Tear down the service, including its window and scheduled callbacks. */
    @After public void teardown() {
        if (owner != null) owner.destroy();
    }

    /** Invoke the same protected command dispatch as the shell or notification. */
    private void command(String action) {
        service.onStartCommand(new Intent(service, service.getClass()).setAction(action), 0, 1);
    }

    /** Retain the actual panel root for delivery of paired remote events. */
    private View window() {
        View root = ReflectionHelpers.getField(panel, "root");
        assertNotNull(root);
        return root;
    }

    /** Deliver both halves of one voluntary remote key. */
    private void key(int code) {
        View root = window();
        assertTrue(root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code)));
        assertTrue(root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code)));
    }

    /** Hold OK long enough to switch horizontal step without committing. */
    private void fineStep() {
        View root = window();
        long start = android.os.SystemClock.uptimeMillis();
        assertTrue(root.dispatchKeyEvent(new KeyEvent(start, start, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_DPAD_CENTER, 0)));
        assertTrue(root.dispatchKeyEvent(new KeyEvent(start, start + 800, KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_DPAD_CENTER, 0)));
        assertTrue(panel.isOpen());
        assertTrue(label().contains("← → 1 s"));
    }

    /** Read the displayed candidate from the actual window button. */
    private String label() {
        android.widget.Button button = ReflectionHelpers.getField(panel, "text");
        return button.getText().toString();
    }

    /** All four directions clamp correctly and an orphan key-up cannot commit. */
    @Test public void directionsAndOneConfirmedCommit() {
        command("OPEN_PAUSED");
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(label().contains("candidat 00:08"));
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertTrue(label().contains("candidat 00:00"));
        key(KeyEvent.KEYCODE_DPAD_UP);
        assertTrue(label().contains("candidat 00:08"));
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertTrue(label().contains("candidat 00:00"));
        window().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
        assertTrue(panel.isOpen());
        long before = clock.generation();
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(before + 1, clock.generation());
        assertFalse(panel.isOpen());
        assertFalse(clock.running());
        assertEquals(StudioMultimediaRuntime.Authority.MANUAL, runtime.authority());
        assertNull(Shadows.shadowOf(service).getNextStartedActivity());
    }

    /** Reanchor at OK after a long edit, then use the same scheduler and renderer ports. */
    @Test public void runningPolicyAnchorsAtCommitAndDispatches() {
        command("OPEN_I");
        elapsed[0] += 20000;
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(clock.running());
        assertEquals(0, clock.position());
        elapsed[0] += 1000;
        clock.tick(clock.generation());
        elapsed[0] += 1500;
        clock.tick(clock.generation());
        assertEquals(List.of("text-1", "tone-1"), effects.events);
        assertNull(Shadows.shadowOf(service).getNextStartedActivity());
    }

    /** BACK and recovery CLOSE discard candidates and restore the prior running policy. */
    @Test public void backAndCloseDiscardCandidate() {
        clock.seek(selected.trackId, selected.revision, clock.generation(), 1000);
        clock.resume();
        command("OPEN_PAUSED");
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        elapsed[0] += 15000;
        key(KeyEvent.KEYCODE_BACK);
        assertFalse(panel.isOpen());
        assertTrue(clock.running());
        assertEquals(1000, clock.position());
        command("OPEN_I");
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        command("CLOSE");
        command("CLOSE");
        assertFalse(panel.isOpen());
        assertTrue(clock.running());
        assertEquals(1000, clock.position());
        assertEquals(DeveloperFocusState.State.CLOSED, panel.focusState());
    }

    /** A stale edit leaves the panel recoverable and does not publish a new anchor. */
    @Test public void revokedAdmissionDoesNotCloseOnOk() {
        command("OPEN_I");
        clock.revoke();
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(panel.isOpen());
        assertTrue(label().contains("refusé"));
        command("CLOSE");
        assertFalse(panel.isOpen());
        assertEquals(StudioMultimediaRuntime.Authority.UNSYNCED, runtime.authority());
    }

    /** Verify the experimental service remains protected and the APK has no network permission. */
    @Test public void protectedComponentUsesQualifiedPackage() throws Exception {
        assertEquals("com.scenevibe.tvcompanionpoc.netflixoverlay001", service.getPackageName());
        var pm = service.getPackageManager();
        assertEquals("android.permission.DUMP", pm.getServiceInfo(new ComponentName(service,
                NetflixManualSyncService.class), 0).permission);
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,
                pm.checkPermission("android.permission.INTERNET", service.getPackageName()));
    }

    /** Advance the injected monotonic clock with the service's nominal tick interval. */
    private void play(long durationMs) {
        for (long passed = 0; passed < durationMs; passed += 100) {
            elapsed[0] += 100;
            clock.tick(clock.generation());
        }
    }

    /** Verify every visible and audible boundary over 32 minutes, including the terminal clamp. */
    @Test public void longTrackDispatchesExactlyOnSchedule() throws Exception {
        loadFixture("netflix003c");
        command("OPEN_I");
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        long[] markers = {10000, 300000, 900000, 960000, 1500000, 1860000};
        long cursor = 0;
        for (int index = 0; index < markers.length; index++) {
            long marker = markers[index];
            play(marker - cursor - 100);
            assertEquals(index * 2, effects.events.size());
            play(100);
            assertEquals("text-" + (index + 1), effects.events.get(index * 2));
            play(2900);
            assertEquals(index * 2 + 1, effects.events.size());
            play(100);
            assertEquals("tone-" + (index + 1), effects.events.get(index * 2 + 1));
            cursor = marker + 3000;
        }
        play(1920000 - cursor);
        assertEquals(1920000, clock.position());
        assertFalse(clock.running());
        assertEquals(12, effects.events.size());
    }

    /** Model an ad manually, including delayed pause, delayed resume and correction of drift. */
    @Test public void interruptionRequiresHumanPauseAndCorrection() throws Exception {
        loadFixture("netflix003c");
        clock.seek(selected.trackId, selected.revision, clock.generation(), 900000);
        command("RESUME");
        play(10000);
        long programPosition = clock.position();
        play(23000);
        assertEquals(programPosition + 23000, clock.position());
        command("PAUSE");
        long paused = clock.position();
        int priorEvents = effects.events.size();
        play(120000);
        assertEquals(paused, clock.position());
        assertEquals(priorEvents, effects.events.size());
        command("OPEN_PAUSED");
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        fineStep();
        for (int step = 0; step < 3; step++) key(KeyEvent.KEYCODE_DPAD_LEFT);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(programPosition, clock.position());
        assertFalse(clock.running());
        play(40000);
        assertEquals(programPosition, clock.position());
        assertEquals(priorEvents, effects.events.size());
        // The programme advanced after the ad while SceneVibe was still paused.
        long afterLateResume = programPosition + 7000;
        command("OPEN_PAUSED");
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        fineStep();
        for (int step = 0; step < 3; step++) key(KeyEvent.KEYCODE_DPAD_LEFT);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(afterLateResume, clock.position());
        command("RESUME");
        play(46000);
        assertEquals(List.of("text-3", "tone-3", "text-4", "tone-4"), effects.events);
    }

    /** Skip elapsed windows on a forward jump and replay only eligible windows after rewind. */
    @Test public void longSeekAndCancellationKeepTheCalendarCoherent() throws Exception {
        loadFixture("netflix003c");
        command("OPEN_I");
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        play(13000);
        long oldGeneration = clock.generation();
        clock.seek(selected.trackId, selected.revision, oldGeneration, 1500000);
        clock.tick(oldGeneration);
        assertEquals(List.of("text-1", "tone-1"), effects.events);
        command("RESUME");
        play(3000);
        assertEquals(List.of("text-1", "tone-1", "text-5", "tone-5"), effects.events);
        command("OPEN_PAUSED");
        key(KeyEvent.KEYCODE_DPAD_UP);
        key(KeyEvent.KEYCODE_BACK);
        assertEquals(1503000, clock.position());
        assertTrue(clock.running());
        assertThrows(IllegalStateException.class, () ->
                clock.seek(selected.trackId, selected.revision + 1, clock.generation(), 300000));
        clock.seek(selected.trackId, selected.revision, clock.generation(), 300000);
        command("RESUME");
        play(3000);
        assertEquals(List.of("text-1", "tone-1", "text-5", "tone-5", "text-2", "tone-2"), effects.events);
    }

    /** Characterize the reported ARM, pause, edit, confirm, close and resume sequence. */
    @Test public void auditConfirmedPausedEditRequiresExplicitResume() {
        command("ARM"); // Already armed by setup with the verified cache on this bench.
        command("OPEN_I");
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        command("PAUSE");
        command("OPEN_PAUSED");
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        command("CLOSE");
        play(120000);
        assertFalse(clock.running());
        assertEquals(0, clock.position());
        assertTrue(effects.events.isEmpty());
        command("RESUME");
        play(1000);
        assertEquals(List.of("text-1"), effects.events);
    }

    /** Verify duplicate commands preserve one anchor and dispatch each event once. */
    @Test public void auditRepeatedPauseAndResumeRemainIdempotent() {
        command("OPEN_I");
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        play(100);
        command("PAUSE");
        long pausedGeneration = clock.generation();
        command("PAUSE");
        play(120000);
        assertEquals(pausedGeneration, clock.generation());
        assertEquals(100, clock.position());
        command("RESUME");
        play(400);
        command("RESUME");
        play(2000);
        assertEquals(2500, clock.position());
        assertEquals(List.of("text-1", "tone-1"), effects.events);
    }

    /** Verify both kinds of cancellation keep an already paused clock frozen. */
    @Test public void auditCancellationWhileSuspendedKeepsOriginalPosition() {
        clock.seek(selected.trackId, selected.revision, clock.generation(), 1000);
        for (boolean back : new boolean[]{true, false}) {
            command("OPEN_PAUSED");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            if (back) key(KeyEvent.KEYCODE_BACK);
            else command("CLOSE");
            play(30000);
            assertFalse(clock.running());
            assertEquals(1000, clock.position());
            assertTrue(effects.events.isEmpty());
        }
    }

    /** Recheck the 003-D reproducer with the corrected pause intent on CLOSE. */
    @Test public void auditPauseDuringEditDoesNotOverridePriorRunning() {
        command("OPEN_I");
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        play(100);
        command("OPEN_PAUSED");
        command("PAUSE");
        command("CLOSE");
        assertFalse(clock.running());
        assertEquals(100, clock.position());
    }

    /** Verify a sequence of unplayed confirmed anchors dispatches only after explicit resume. */
    @Test public void auditConsecutiveRecalibrationsOnlyPlayFinalAnchor() {
        for (long position : new long[]{1000, 7500, 0, 4000, 0}) {
            long previousGeneration = clock.generation();
            clock.seek(selected.trackId, selected.revision, previousGeneration, position);
            clock.tick(previousGeneration);
            assertFalse(clock.running());
            assertEquals(position, clock.position());
        }
        assertTrue(effects.events.isEmpty());
        command("RESUME");
        play(2500);
        assertEquals(List.of("text-1", "tone-1"), effects.events);
    }

    /** Preserve explicit pause after BACK and when the editor loses focus. */
    @Test public void pauseIntentSurvivesBackAndFocusLoss() {
        clock.seek(selected.trackId, selected.revision, clock.generation(), 0);
        for (boolean back : new boolean[]{true, false}) {
            command("RESUME");
            play(100);
            long frozen = clock.position();
            command("OPEN_PAUSED");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            command("PAUSE");
            command("RESUME");
            assertFalse(clock.running());
            if (back) key(KeyEvent.KEYCODE_BACK);
            else {
                View root = window();
                root.onWindowFocusChanged(true);
                root.onWindowFocusChanged(false);
            }
            assertFalse(panel.isOpen());
            assertFalse(clock.running());
            assertEquals(frozen, clock.position());
        }
    }

    /** A burst of resume commands does not replace the pending owner tick or the anchor. */
    @Test public void resumeBurstPreservesPendingTick() {
        clock.seek(selected.trackId, selected.revision, clock.generation(), 0);
        command("RESUME");
        Runnable pending = ReflectionHelpers.getField(service, "pendingTick");
        assertNotNull(pending);
        for (int i = 0; i < 100; i++) command("RESUME");
        assertSame(pending, ReflectionHelpers.getField(service, "pendingTick"));
        play(2500);
        assertEquals(List.of("text-1", "tone-1"), effects.events);
    }

    /** The local notification command opens and reopens a menu without changing the anchor. */
    @Test public void localMenuCanReopenAndStartPausedEdit() {
        clock.seek(selected.trackId, selected.revision, clock.generation(), 0);
        command("OPEN_CONTROLS");
        android.app.AlertDialog menu = ReflectionHelpers.getField(service, "controls");
        assertNotNull(menu);
        assertEquals(4, menu.getListView().getCount());
        assertEquals("Recaler et reprendre", menu.getListView().getItemAtPosition(0));
        assertEquals("Recaler sans reprise", menu.getListView().getItemAtPosition(1));
        assertEquals("Pause SceneVibe", menu.getListView().getItemAtPosition(2));
        assertEquals("Reprendre SceneVibe", menu.getListView().getItemAtPosition(3));
        command("CLOSE");
        assertNull(ReflectionHelpers.getField(service, "controls"));
        command("OPEN_CONTROLS");
        menu = ReflectionHelpers.getField(service, "controls");
        assertNotNull(menu);
        assertTrue(menu.getListView().performItemClick(null, 1, 1));
        assertTrue(panel.isOpen());
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(clock.running());
    }


    /** A user-selected recalage resumes once after OK, dispatching original TEXT/AUDIO. */
    @Test public void menuExplicitRecalageAndResumeDispatchesTextAudio() {
        command("OPEN_CONTROLS");
        android.app.AlertDialog menu = ReflectionHelpers.getField(service, "controls");
        android.widget.ListView choices = menu.getListView();
        int size = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY);
        choices.measure(size, size);
        choices.layout(0, 0, 1080, 1080);
        assertTrue(choices.requestFocus());
        choices.setSelection(0);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        choices.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
        choices.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertTrue(panel.isOpen());
        assertTrue(label().contains("reprise après OK"));
        assertFalse(clock.running());
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(panel.isOpen());
        assertTrue(clock.running());
        play(2500);
        assertEquals(List.of("text-1", "tone-1"), effects.events);
    }

    /** BACK cancels the unconfirmed resume policy and keeps the initial anchor paused. */
    @Test public void menuResumeChoiceCancelledByBackDoesNotStartTheTrack() {
        for (boolean initiallyRunning : new boolean[]{false, true}) {
            for (int choice : new int[]{0, 1}) {
                for (int closing : new int[]{0, 1, 2}) {
                    clock.seek(selected.trackId, selected.revision, clock.generation(), 0);
                    if (initiallyRunning) command("RESUME");
                    command("OPEN_CONTROLS");
                    android.app.AlertDialog menu = ReflectionHelpers.getField(service, "controls");
                    assertTrue(menu.getListView().performItemClick(null, choice, choice));
                    key(KeyEvent.KEYCODE_DPAD_RIGHT);
                    if (closing == 0) key(KeyEvent.KEYCODE_BACK);
                    else if (closing == 1) command("CLOSE");
                    else {
                        View root = window();
                        root.onWindowFocusChanged(true);
                        root.onWindowFocusChanged(false);
                    }
                    assertFalse(panel.isOpen());
                    assertFalse(clock.running());
                    assertEquals(0, clock.position());
                    play(3000);
                    assertTrue(effects.events.isEmpty());
                }
            }
        }
    }

    /** Explicit PAUSE during a pending resume-oriented edit always wins over its policy. */
    @Test public void explicitPauseDuringResumeChoicePreventsAutomaticPlayback() {
        command("OPEN_CONTROLS");
        android.app.AlertDialog menu = ReflectionHelpers.getField(service, "controls");
        assertTrue(menu.getListView().performItemClick(null, 0, 0));
        command("PAUSE");
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(clock.running());
        play(3000);
        assertTrue(effects.events.isEmpty());

        command("RESUME");
        command("OPEN_CONTROLS");
        menu = ReflectionHelpers.getField(service, "controls");
        assertTrue(menu.getListView().performItemClick(null, 0, 0));
        clock.seek(selected.trackId, selected.revision, clock.generation(), 0);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(panel.isOpen());
        assertFalse(clock.running());
        command("CLOSE");
        assertFalse(clock.running());
        play(3000);
        assertTrue(effects.events.isEmpty());
    }

    /** Exact exclusive ends never dispatch an expired TEXT or AUDIO. */
    @Test public void exactWindowEndsSkipExpiredEvents() {
        for (long end : new long[]{2000, 3000}) {
            clock.seek(selected.trackId, selected.revision, clock.generation(), end);
            command("RESUME");
            assertTrue(effects.events.isEmpty());
            command("PAUSE");
        }
        clock.seek(selected.trackId, selected.revision, clock.generation(), 0);
        command("RESUME");
        play(900);
        assertTrue(effects.events.isEmpty());
        play(100);
        assertEquals(List.of("text-1"), effects.events);
        play(1400);
        assertEquals(List.of("text-1"), effects.events);
        play(100);
        assertEquals(List.of("text-1", "tone-1"), effects.events);
    }

    /** Verify the TV launcher declaration and that its explicit button only arms SceneVibe. */
    @Test public void launcherPreparesOnlyTheExperimentalService() {
        var homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setPackage(service.getPackageName());
        var entries = service.getPackageManager().queryIntentActivities(homeIntent, 0);
        assertEquals(1, entries.size());
        assertEquals(NetflixManualLaunchActivity.class.getName(), entries.get(0).activityInfo.name);
        try (var activity = Robolectric.buildActivity(NetflixManualLaunchActivity.class).setup()) {
            var launch = activity.get();
            android.widget.Button button = ReflectionHelpers.getField(launch, "start");
            assertEquals("Préparer SceneVibe", button.getText().toString());
            button.performClick();
            Intent armed = Shadows.shadowOf(launch).getNextStartedService();
            assertNotNull(armed);
            assertEquals(NetflixManualSyncService.class.getName(), armed.getComponent().getClassName());
            assertEquals("ARM", armed.getAction());
            assertNull(Shadows.shadowOf(launch).getNextStartedActivity());
        }
    }

    /** Ignore a repeated OK from an already closed editor. */
    @Test public void repeatedCommitDoesNotReplaceConfirmedAnchor() {
        command("OPEN_PAUSED");
        View retired = window();
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        long committed = clock.generation();
        retired.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
        retired.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
        assertEquals(committed, clock.generation());
        assertFalse(clock.running());
    }

    /** Capture only the existing runtime's logical render commands. */
    private static final class Ports implements SceneRuntimeController.SceneSink, LocalAudioPort {
        final List<String> events = new ArrayList<>();
        private State state = State.IDLE;
        private String pending;

        /** Accept the verified fixture scene. */
        @Override public boolean preflight(OverlayManifest.Scene scene) { return true; }
        /** Record the selected TEXT window. */
        @Override public void show(OverlayManifest.Scene scene) { events.add(scene.id); }
        /** Simulate retirement of one visual window. */
        @Override public void hide(OverlayManifest.Scene scene) {}
        /** Simulate immediate teardown of visual windows. */
        @Override public void hideAll() {}
        /** Record the selected MP3 resource. */
        @Override public void prepare(String asset) { pending = asset; state = State.PREPARED; }
        /** Record the actual logical AUDIO start. */
        @Override public void start() { events.add(pending); state = State.STARTED; }
        /** Record termination of the logical player. */
        @Override public void stop() { state = State.IDLE; }
        /** Release the logical port. */
        @Override public void release() { state = State.IDLE; }
        /** Accept gain without producing sound. */
        @Override public void setGain(float gain) {}
        /** Project the current logical port state. */
        @Override public State state() { return state; }
        /** Exercise the existing native-offset path. */
        @Override public boolean supportsNativeOffsets() { return true; }
        /** Accept the offset on the fake output only. */
        @Override public void seekTo(long offsetMs) {}
    }
}
