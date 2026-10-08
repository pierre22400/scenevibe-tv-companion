package com.scenevibe.tvcompanionpoc.mediaexperiment;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.MediaMetadataRetriever;
import java.io.File;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.OverlayVideoPort;

/**
 * Android implementation of {@link OverlayVideoPort}: a FULLSCREEN
 * {@code TYPE_APPLICATION_OVERLAY} window hosting a {@link TextureView} driven by a
 * {@link MediaPlayer} that plays the bundled LOCAL {@code assets/interlude.mp4}.
 *
 * <p>Rendering mechanism: a {@link TextureView} (not a VideoView) is used so the
 * surface composes correctly inside a WindowManager overlay window and so the
 * fullscreen video can be the interactive SceneVibe surface shown over the paused
 * streaming app. The window is {@code MATCH_PARENT x MATCH_PARENT}.</p>
 *
 * <p>Unlike :app's {@code SceneRenderer} (a non-interactive commentary overlay that
 * sets {@code FLAG_NOT_TOUCHABLE}), this is a fullscreen interactive video surface,
 * so it does NOT set {@code FLAG_NOT_TOUCHABLE}; it only sets
 * {@code FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_NO_LIMITS} so the D-pad is not trapped.
 * The overlay is gated on {@code Settings.canDrawOverlays} and cleaned up with
 * {@code removeViewImmediate}, mirroring {@code SceneRenderer}'s window lifecycle.</p>
 *
 * <p>There is NO network streaming: the only video source is the bundled local
 * asset. Completion fires {@link Callbacks#onVideoCompleted()}; any MediaPlayer or
 * attach failure fires {@link Callbacks#onVideoError()}.</p>
 */
final class AndroidOverlayVideoPort implements OverlayVideoPort {
    private static final String TAG = "SceneVibeInterludePoc";
    private static final String LOCAL_VIDEO_ASSET = "interlude.mp4";
    private static final String TEN_SECOND_VIDEO = "scenevibe_interlude_10s.mp4";

    /** Delivered back to the state machine on the main thread. */
    interface Callbacks {
        /** Normal completion can request fresh guarded resume. */
        void onVideoCompleted();
        /** Errors require teardown without native PLAY. */
        void onVideoError();
    }

    private final Context context;
    private final WindowManager windows;
    private final Callbacks callbacks;

    private FrameLayout root;
    private TextureView textureView;
    private MediaPlayer player;
    private Surface surface;
    private boolean useTenSecondFixture;

    /** Keep the overlay lifecycle isolated from the production companion. */
    AndroidOverlayVideoPort(Context context, Callbacks callbacks) {
        this.context = context.getApplicationContext();
        this.callbacks = callbacks;
        this.windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    /** Select an independently supplied MP4 for the next run, never the legacy default. */
    void selectTenSecondFixture(boolean selected) {
        if (root != null) throw new IllegalStateException("VIDEO_OVERLAY_ALREADY_ACTIVE");
        useTenSecondFixture = selected;
    }

    /** Check external MP4 video+audio presence and 10s duration BEFORE pausing Prime. */
    boolean isTenSecondFixtureReady() {
        File directory = context.getExternalFilesDir(null);
        File fixture = directory == null ? null : new File(directory, TEN_SECOND_VIDEO);
        if (fixture == null || !fixture.isFile() || fixture.length() < 10000L) return false;
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(fixture.getAbsolutePath());
            String duration = metadata.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION);
            String video = metadata.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
            String audio = metadata.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO);
            long millis = duration == null ? -1L : Long.parseLong(duration);
            return millis >= 9500L && millis <= 10500L
                    && "yes".equalsIgnoreCase(video)
                    && "yes".equalsIgnoreCase(audio);
        } catch (RuntimeException failure) {
            return false;
        } finally {
            try {
                metadata.release();
            } catch (java.io.IOException releaseFailure) {
                Log.w(TAG, "METADATA_RELEASE_FAILED", releaseFailure);
            }
        }
    }

    /** Attach a fullscreen local surface only when overlay permission is available. */
    @Override
    public void attach() {
        if (windows == null) {
            throw new IllegalStateException("WindowManager unavailable");
        }
        if (!Settings.canDrawOverlays(context)) {
            // Fail closed: without overlay permission there is no interlude surface.
            throw new IllegalStateException("Overlay permission not granted");
        }
        if (root != null) {
            return;
        }
        FrameLayout container = new FrameLayout(context);
        container.setBackgroundColor(Color.BLACK);

        TextureView view = new TextureView(context);
        container.addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));

        // Fullscreen interactive video overlay: MATCH_PARENT x MATCH_PARENT. We do
        // NOT set FLAG_NOT_TOUCHABLE (this is an interactive SceneVibe surface);
        // FLAG_NOT_FOCUSABLE keeps the TV D-pad from being trapped by the overlay.
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle("SceneVibe Interlude POC");

        windows.addView(container, params);
        this.root = container;
        this.textureView = view;
        Log.i(TAG, "OVERLAY_ATTACHED fullscreen=true");
    }

    /** Start local video once its texture exists; stale surface callbacks are ignored. */
    @Override
    public void playLocalVideo() {
        if (textureView == null) {
            throw new IllegalStateException("Overlay not attached");
        }
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            /** Prepare video only while this attempt still owns the overlay. */
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
                if (textureView == null || root == null) return;
                startPlayback(st);
            }

            /** The fullscreen window already owns size management. */
            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
            }

            /** Allow framework texture release during overlay teardown. */
            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                return true;
            }

            /** Texture refresh has no transport effect. */
            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture st) {
            }
        });
        // If the surface is already available (e.g. relayout), start immediately.
        if (textureView.isAvailable()) {
            startPlayback(textureView.getSurfaceTexture());
        }
    }

    /** Configure the local player before preparation and bind callbacks to its lifetime. */
    private void startPlayback(SurfaceTexture texture) {
        if (player != null) {
            return;
        }
        try {
            surface = new Surface(texture);
            MediaPlayer created = new MediaPlayer();
            player = created;
            created.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build());
            if (useTenSecondFixture) {
                File directory = context.getExternalFilesDir(null);
                if (directory == null) throw new IllegalStateException("VIDEO_FIXTURE_STORAGE_MISSING");
                created.setDataSource(new File(directory, TEN_SECOND_VIDEO).getAbsolutePath());
            } else {
                try (AssetFileDescriptor afd = context.getAssets().openFd(LOCAL_VIDEO_ASSET)) {
                    created.setDataSource(
                            afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                }
            }
            created.setSurface(surface);
            created.setOnCompletionListener(mp -> {
                if (player != mp || root == null) return;
                Log.i(TAG, "LOCAL_VIDEO_COMPLETED");
                callbacks.onVideoCompleted();
            });
            created.setOnErrorListener((mp, what, extra) -> {
                if (player != mp || root == null) return true;
                Log.w(TAG, "LOCAL_VIDEO_ERROR what=" + what + " extra=" + extra);
                callbacks.onVideoError();
                return true;
            });
            created.setOnPreparedListener(mp -> {
                if (player == mp && root != null) mp.start();
            });
            created.prepareAsync();
            Log.i(TAG, "LOCAL_VIDEO_PREPARING fixture10s=" + useTenSecondFixture);
        } catch (Exception error) {
            Log.w(TAG, "LOCAL_VIDEO_SETUP_FAILED");
            callbacks.onVideoError();
        }
    }

    /** Stop/release video and remove the overlay safely, including partial setup. */
    @Override
    public void remove() {
        MediaPlayer existing = player;
        player = null;
        if (existing != null) {
            try {
                existing.reset();
            } catch (RuntimeException ignored) {
                // Teardown must not throw.
            }
            try {
                existing.release();
            } catch (RuntimeException ignored) {
                // Teardown must not throw.
            }
        }
        if (surface != null) {
            try {
                surface.release();
            } catch (RuntimeException ignored) {
                // Teardown must not throw.
            }
            surface = null;
        }
        FrameLayout container = root;
        root = null;
        textureView = null;
        if (container != null && windows != null) {
            try {
                windows.removeViewImmediate(container);
            } catch (IllegalArgumentException ignored) {
                // Already detached: idempotent cleanup.
            }
        }
    }
}
