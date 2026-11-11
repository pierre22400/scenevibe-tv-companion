package com.scenevibe.tvcompanionpoc;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import com.scenevibe.tvcompanionpoc.calendar.SceneEvent;
import com.scenevibe.tvcompanionpoc.presentation.TvAccountSource;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Verifies the same production private cache and Android Keystore over two standard APK updates. */
@RunWith(AndroidJUnit4.class)
public final class SonySignedContinuityTest {
    private static final String TOKEN = "QQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ";
    private static final String PLAYER = "com.amazon.amazonvideo.livingroom/.OwnedPlayer";
    private final Instrumentation instrument = InstrumentationRegistry.getInstrumentation();
    private final Context target = instrument.getTargetContext();
    private final File proof = new File(target.getFilesDir(), "sony001a-private-proof.json");

    /** Launch the owned viewer or update its native MEDIA position through normal Android intents. */
    static void player(UiDevice remote, long position, boolean playing) throws Exception {
        String response = remote.executeShellCommand("am start -W -n " + PLAYER
                + " --el position " + position + " --ez playing " + playing);
        assertTrue("Owned MEDIA activity launch failed", response.contains("Status: ok"));
    }

    /** Return a UiDevice connection that keeps existing accessibility services active. */
    static UiDevice device(Instrumentation instrument) throws Exception {
        Configurator.getInstance().setUiAutomationFlags(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        instrument.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        UiDevice device = UiDevice.getInstance(instrument);
        assertEquals("1", device.executeShellCommand("getprop ro.kernel.qemu").trim());
        return device;
    }

    /** Poll production state on the Android main Looper with a bounded timeout. */
    private void await(BooleanSupplier check, String description, long timeout) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeout;
        while (SystemClock.elapsedRealtime() < deadline) {
            AtomicBoolean ok = new AtomicBoolean();
            instrument.runOnMainSync(() -> ok.set(check.getAsBoolean()));
            if (ok.get()) return;
            Thread.sleep(100);
        }
        fail(description + " status=" + TvFirst009TextServiceOwner.status().code);
    }

    /** Return only the bounded, test-owned private grant and a blank library. */
    private JSONObject response(String path, JSONObject identity, long revision) throws Exception {
        if (path.contains("/association")) {
            return new JSONObject().put("profile", TvFirst008Wire.PROFILE)
                    .put("deviceId", identity.getString("deviceId")).put("generation", identity.getString("generation"))
                    .put("accountAssociated", true).put("rendering", "FORBIDDEN")
                    .put("consentExpiresAt", Instant.now().plusSeconds(3600).toString()).put("currentRevision", revision);
        }
        if (path.contains("/library")) {
            return new JSONObject().put("profile", TvFirst008Wire.PROFILE).put("items", new JSONArray()).put("next", JSONObject.NULL);
        }
        throw new IllegalArgumentException("UNEXPECTED_OWNED_PRIVATE_ROUTE");
    }

    /** Seed only v32, then reopen the same data through release code on v34 and v35. */
    @Test public void retainPrivateStateAndRender() throws Exception {
        UiDevice remote = device(instrument);
        int version = Integer.parseInt(InstrumentationRegistry.getArguments().getString("expectedVersion"));
        assertEquals(version, target.getPackageManager().getPackageInfo(target.getPackageName(), 0).getLongVersionCode());
        assertEquals(0, target.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE);
        assertTrue(android.provider.Settings.canDrawOverlays(target));
        assertTrue(NotificationAccess.isGranted(target));

        boolean seed = "true".equals(InstrumentationRegistry.getArguments().getString("seed"));
        TvFirst008State state = new TvFirst008State(target);
        ManualNativeFixture fixture = seed ? new ManualNativeFixture() : null;
        var prefs = target.getSharedPreferences("multimedia_user_preferences", Context.MODE_PRIVATE);
        if (seed) {
            assertEquals(32, version);
            assertFalse(proof.exists());
            assertNull(state.read("identity"));
            state.claimed(new JSONObject().put("profile", TvFirst008Wire.PROFILE).put("deviceId", fixture.binding.device)
                    .put("generation", fixture.generation).put("deviceToken", TOKEN));
            assertTrue(prefs.edit().putBoolean("text", true).putBoolean("audio", true).putBoolean("adsync", false).commit());
        }
        JSONObject identity = state.read("identity");
        assertNotNull(identity);
        assertTrue("Original credential did not decrypt", TOKEN.equals(state.token(identity)));
        JSONObject initial = seed ? null : new JSONObject(new String(Files.readAllBytes(proof.toPath()), StandardCharsets.UTF_8));
        if (!seed) {
            assertEquals(initial.getString("device"), identity.getString("deviceId"));
            assertEquals(initial.getString("generation"), identity.getString("generation"));
        }
        assertTrue(prefs.getBoolean("text", false));
        assertTrue(prefs.getBoolean("audio", false));
        assertFalse(prefs.getBoolean("adsync", true));
        assertEquals(version == 34, target.getPackageManager().checkPermission(
                android.Manifest.permission.RECEIVE_BOOT_COMPLETED, target.getPackageName()) == 0);

        long revision = seed ? fixture.binding.revision - 1 : initial.getLong("revision");
        TvFirst008Source source = new TvFirst008Source(target, state,
                (method, path, token, body) -> {
                    assertTrue("Private request used a different credential", TOKEN.equals(token));
                    return response(path, identity, revision);
                }, "https://owned-fixture.invalid", Executors.newSingleThreadExecutor());
        DiagnosticsActivity activity = null;
        try {
            instrument.runOnMainSync(source::resume);
            await(() -> !source.busy() && source.accountStage() == TvAccountSource.AccountStage.ASSOCIATED,
                    "Private association not available", 20000);
            if (seed) {
                AtomicReference<MultimediaPrivateCache.Prepared> stored = new AtomicReference<>();
                instrument.runOnMainSync(() -> source.downloadMultimedia(fixture.binding.publication, stored::set, fixture.port));
                await(() -> stored.get() != null && !source.busy(), "Initial verified cache missing", 25000);
                var binding = stored.get().binding;
                Files.write(proof.toPath(), new JSONObject().put("device", binding.device)
                        .put("generation", binding.generation).put("revision", binding.revision)
                        .put("digest", binding.sha256).toString().getBytes(StandardCharsets.UTF_8));
                initial = new JSONObject(new String(Files.readAllBytes(proof.toPath()), StandardCharsets.UTF_8));
            }
            var restored = MultimediaAndroidBridge.restoreCache(target, source);
            assertNotNull(restored);
            assertEquals(initial.getString("digest"), restored.binding.sha256);
            assertEquals(initial.getLong("revision"), restored.binding.revision);
            assertEquals(3, restored.track.originalManifest.scenes.size());
            assertEquals(3, restored.track.audio.size());
            for (var row : restored.profile.resources) {
                assertEquals(row.sha256, MultimediaDeliveryProfile.hash(Files.readAllBytes(restored.resource(row.id).toPath())));
            }
            System.out.println("SONY001A_PRIVATE_STATE_PASS version=" + version + " original_receipt original_keystore preferences 3_TEXT 3_AUDIO");

            activity = (DiagnosticsActivity) instrument.startActivitySync(new Intent(target, DiagnosticsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            DiagnosticsActivity running = activity;
            instrument.runOnMainSync(() -> assertTrue("Private cache admission refused",
                    TvFirst009TextServiceOwner.requestMultimediaStart(running, source, restored, true)));
            await(TvFirst009TextServiceOwner::multimediaActive, "Private runtime not started", 20000);
            int texts = 0;
            int audios = 0;
            for (SceneEvent event : restored.track.calendar.events()) {
                long position = Math.max(0, event.startMs() - 1000);
                player(remote, position, false);
                await(() -> TvFirst009TextServiceOwner.status().positionMs == position, "Owned MEDIA position missing", 15000);
                player(remote, position, true);
                if (restored.track.audio.containsKey(event.eventId())) {
                    int expected = ++audios;
                    await(() -> TvFirst009TextServiceOwner.multimediaAudioStarted() >= expected, "Native MP3 not started", 15000);
                } else {
                    int expected = ++texts;
                    await(() -> TvFirst009TextServiceOwner.multimediaTextRequested() >= expected, "TEXT window not requested", 15000);
                }
            }
            assertEquals(3, texts);
            assertEquals(3, audios);
            System.out.println("SONY001A_PRIVATE_RENDER_PASS version=" + version + " 3_TEXT 3_AUDIO");
            SonyRetainedMuteChecks.exercise(instrument, version);
        } finally {
            DiagnosticsActivity running = activity;
            instrument.runOnMainSync(() -> {
                TvFirst009TextServiceOwner.requestStop(target);
                if (running != null) running.finish();
                source.close();
            });
        }
    }
}
