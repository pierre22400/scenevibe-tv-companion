package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.WindowManager;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Real Android renderer/Surface/MediaPlayer qualification with SIMULATED native sessions.
 *
 * <p>Runs only on an explicitly selected local/CI emulator. It cannot issue native MediaController
 * commands, contact Cloud or qualify Prime/Sony/audio audibility. The exact MP4 is decoded;
 * texture updates, shared-root membership and actual release are measured without screenshots.</p>
 */
public final class E5NativeInstrumentation extends Instrumentation {
    private int passed;

    /** Start the explicit test worker; the framework does not invoke onStart by itself. */
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    /** Run the bounded platform cases and report real performed assertions, not physical claims. */
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            nominal();
            cancelledPreparation();
            surfaceRetirement();
            missingFixture();
            flagOff();
            result.putInt("passed", passed);
            result.putInt("failed", 0);
            result.putString("scope", "REAL_ANDROID_PLAYER_HOST / SIMULATED_NATIVE / NOT_SONY");
            Log.i("E5_PLATFORM", "PASS cases=" + passed + " native=SIMULATED sony=NOT_EXECUTED");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putInt("passed", passed);
            result.putInt("failed", 1);
            result.putString("failure", error.toString());
            Log.e("E5_PLATFORM", "FAIL", error);
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    /** Exercise actual prepare/start/file EOF/frames/release and the real regie's guarded path. */
    private void nominal() throws Exception {
        Harness h = create(getTargetContext(), true);
        start(h);
        waitTerminal(h, 30000);
        runOnMainSync(() -> {
            require(h.controller.localPhase() == SceneRuntimeController.LocalPhase.SUCCESS, "NOMINAL_PHASE");
            require(h.nativePort.pauses == 1 && h.nativePort.plays == 1, "SIMULATED_CONFIRMATIONS");
            require(h.player.renderedFramesForTest() > 0, "REAL_TEXTURE_UPDATES");
            require(h.player.resourcesReleasedForTest(), "REAL_RESOURCES_RELEASED");
            require(!h.renderer.localHostInRendererRoot(), "HOST_DETACHED");
        });
        passed++;
        Log.i("E5_PLATFORM", "PASS nominal_real_decode_eof_cleanup frames=" + h.player.renderedFramesForTest());
    }

    /** Release while prepare is pending; late readiness must not pause or start anything. */
    private void cancelledPreparation() throws Exception {
        Harness h = create(getTargetContext(), true);
        runOnMainSync(() -> {
            require(h.controller.requestLocal(LocalInterludePlan.qualification(true)), "REQUEST_PREPARE");
            h.controller.cancelLocal("STOP_DURING_PREPARE");
        });
        SystemClock.sleep(1200);
        runOnMainSync(() -> {
            h.controller.tickLocal();
            require(h.nativePort.pauses == 0 && h.nativePort.plays == 0, "NO_LATE_TRANSPORT");
            require(h.player.resourcesReleasedForTest(), "PREPARATION_RELEASE");
            require(!h.renderer.localHostInRendererRoot(), "PREPARATION_DETACH");
        });
        passed++;
        Log.i("E5_PLATFORM", "PASS cancellation_and_late_callbacks");
    }

    /** Destroy the renderer host during real playback; decoder release precedes detach. */
    private void surfaceRetirement() throws Exception {
        Harness h = create(getTargetContext(), true);
        start(h);
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (SystemClock.elapsedRealtime() < deadline) {
            tick(h);
            if (h.controller.localPhase() == SceneRuntimeController.LocalPhase.PLAYER_PLAYING) break;
            SystemClock.sleep(50);
        }
        runOnMainSync(() -> {
            require(h.controller.localPhase() == SceneRuntimeController.LocalPhase.PLAYER_PLAYING, "REAL_STARTED");
            require(h.renderer.localHostInRendererRoot(), "ONE_SHARED_ROOT");
            int flags = h.renderer.windowFlagsForTest();
            require((flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0, "PASSIVE_FOCUS");
            require((flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0, "PASSIVE_TOUCH");
            h.renderer.dismissNow();
        });
        waitTerminal(h, 5000);
        runOnMainSync(() -> {
            require(h.nativePort.plays == 0, "NO_PLAY_ON_SURFACE_LOSS");
            require(h.player.resourcesReleasedForTest(), "SURFACE_RELEASE");
            require(h.controller.localPhase() == SceneRuntimeController.LocalPhase.RECOVERY_REQUIRED,
                    "SURFACE_RECOVERY_REQUIRED");
        });
        passed++;
        Log.i("E5_PLATFORM", "PASS real_surface_retirement_and_passive_window");
    }

    /** Missing bytes in an independent test-package AssetManager refuse before native PAUSE. */
    private void missingFixture() throws Exception {
        AssetManager emptyAssets = getContext().getAssets();
        Context wrapper = new ContextWrapper(getTargetContext()) {
            /** Preserve the test's deliberately empty asset source while retaining private storage. */
            @Override public Context getApplicationContext() { return this; }
            /** No target fixture is exposed through this independent asset manager. */
            @Override public AssetManager getAssets() { return emptyAssets; }
        };
        Harness h = create(wrapper, true);
        start(h);
        waitTerminal(h, 7000);
        runOnMainSync(() -> {
            require(h.nativePort.pauses == 0 && h.nativePort.plays == 0, "MISSING_BEFORE_PAUSE");
            require(h.player.resourcesReleasedForTest(), "MISSING_CLEANUP");
            require(h.controller.localPhase() == SceneRuntimeController.LocalPhase.REFUSED, "MISSING_REFUSED");
        });
        passed++;
        Log.i("E5_PLATFORM", "PASS actual_missing_asset");
    }

    /** An OFF regie prepares no decoder, host or native command. */
    private void flagOff() {
        Harness h = create(getTargetContext(), false);
        runOnMainSync(() -> {
            require(!h.controller.requestLocal(LocalInterludePlan.qualification(true)), "OFF_REFUSAL");
            require(h.player.state() == LocalVideoPort.State.DETACHED, "OFF_NO_PLAYER");
            require(!h.renderer.localHostInRendererRoot(), "OFF_NO_WINDOW");
            require(h.nativePort.pauses == 0 && h.nativePort.plays == 0, "OFF_NO_TRANSPORT");
        });
        passed++;
        Log.i("E5_PLATFORM", "PASS flag_off_no_side_effects");
    }

    /** Construct real Android player/renderer with only the native session port simulated. */
    private Harness create(Context playerContext, boolean enabled) {
        AtomicReference<Harness> reference = new AtomicReference<>();
        runOnMainSync(() -> reference.set(new Harness(playerContext, enabled)));
        return reference.get();
    }

    /** Admit the explicit local plan; it is never loaded into an editorial calendar. */
    private void start(Harness h) {
        runOnMainSync(() -> require(h.controller.requestLocal(LocalInterludePlan.qualification(true)),
                "LOCAL_REQUEST"));
    }

    /** Poll only execution/transport confirmation; this does not create MEDIA/WALL events. */
    private void tick(Harness h) {
        runOnMainSync(h.controller::tickLocal);
    }

    /** Await a finite real lifecycle while leaving the main thread free for player callbacks. */
    private void waitTerminal(Harness h, long budget) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + budget;
        while (SystemClock.elapsedRealtime() < deadline) {
            tick(h);
            if (!h.controller.isLocalPresentationActive()) return;
            SystemClock.sleep(50);
        }
        runOnMainSync(() -> h.controller.cancelLocal("INSTRUMENTATION_TIMEOUT"));
        throw new AssertionError("PLATFORM_WAIT_TIMEOUT");
    }

    /** Fail on an actually observed condition, without converting missing proof to a skip/pass. */
    private static void require(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    /** Real graphical/player composition with an explicitly simulated native transport. */
    private final class Harness {
        final SceneRenderer renderer;
        final AndroidLocalVideoPort player;
        final SimulatedNative nativePort = new SimulatedNative();
        final SceneRuntimeController controller;

        /** Bind the same renderer instance to both the real player and the real regie. */
        Harness(Context playerContext, boolean enabled) {
            renderer = new SceneRenderer(getTargetContext(), ref -> null,
                    () -> { throw new AssertionError("OVERLAY_PERMISSION"); });
            player = new AndroidLocalVideoPort(playerContext, renderer);
            controller = new SceneRuntimeController(new SceneRuntimeController.SceneSink() {
                /** The real renderer performs the graphical asset preflight. */
                @Override public boolean preflight(OverlayManifest.Scene scene) { return renderer.preflight(scene); }
                /** Regular graph scenes use the original renderer. */
                @Override public void show(OverlayManifest.Scene scene) { renderer.render(scene); }
                /** Ordinary retirement preserves renderer lifecycle. */
                @Override public void hide(OverlayManifest.Scene scene) { renderer.dismiss(scene); }
                /** Drop the only renderer root after player resources were released. */
                @Override public void hideAll() { renderer.dismissNow(); }
                /** Present graphics without replacing the already prepared TextureView. */
                @Override public void showLocal(LocalInterludePlan plan) { renderer.showLocalScene(plan); }
            });
            NativeTransportOwner.Diagnostics diagnostics = (event, generation, sample, reason) ->
                    Log.i("E5_PLATFORM", event + " phase=" + controller.localPhase() + " reason=" + reason);
            NativeTransportOwner owner = new NativeTransportOwner(nativePort,
                    NativeQualificationPolicy.local035(), diagnostics, SystemClock::elapsedRealtime);
            controller.configureLocal(enabled, player, owner, SystemClock::elapsedRealtime, diagnostics);
        }
    }

    /** Fake native programme only: no MediaController or application command is reachable. */
    private static final class SimulatedNative implements NativeSessionPort {
        final Object token = new Object();
        int state = NativeSessionSnapshot.PLAYING;
        long position = 300000;
        long updated = SystemClock.elapsedRealtime();
        int pauses;
        int plays;

        /** Capture independently observed native state under the same monotone clock. */
        private NativeSessionSnapshot sample() {
            return new NativeSessionSnapshot(token, NativeQualificationPolicy.PRIME_PACKAGE,
                    "platform-simulation", "Synthetic VOD for platform tests", "Simulation", 3000000,
                    position, SystemClock.elapsedRealtime(), updated,
                    state == NativeSessionSnapshot.PLAYING ? 1f : 0f, state, true, true, true);
        }
        /** Select the fake programme; no actual Prime package is consulted. */
        @Override public NativeSessionSnapshot select(boolean vod) { return sample(); }
        /** Observe the fake source separately from command dispatch. */
        @Override public NativeSessionSnapshot observe(NativeSessionSnapshot original) { return sample(); }
        /** Model an independently observable PAUSED state at its real execution timestamp. */
        @Override public boolean pause(NativeSessionSnapshot expected, NativeQualificationPolicy p, long now) {
            long stamp = SystemClock.elapsedRealtime();
            position += stamp - updated;
            updated = stamp;
            state = NativeSessionSnapshot.PAUSED;
            pauses++;
            return true;
        }
        /** Model native PLAYING so the regie must observe it rather than infer command success. */
        @Override public boolean play(NativeSessionSnapshot anchor, NativeQualificationPolicy p, long now) {
            updated = SystemClock.elapsedRealtime();
            state = NativeSessionSnapshot.PLAYING;
            plays++;
            return true;
        }
    }
}
