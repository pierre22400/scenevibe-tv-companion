package com.scenevibe.tvcompanionpoc.mediaexperiment;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
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

    /** Delivered back to the state machine on the main thread. */
    interface Callbacks {
        void onVideoCompleted();
        void onVideoError();
    }

    private final Context context;
    private final WindowManager windows;
    private final Callbacks callbacks;

    private FrameLayout root;
    private TextureView textureView;
    private MediaPlayer player;
    private Surface surface;

    AndroidOverlayVideoPort(Context context, Callbacks callbacks) {
        this.context = context.getApplicationContext();
        this.callbacks = callbacks;
        this.windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

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

    @Override
    public void playLocalVideo() {
        if (textureView == null) {
            throw new IllegalStateException("Overlay not attached");
        }
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
                startPlayback(st);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture st) {
            }
        });
        // If the surface is already available (e.g. relayout), start immediately.
        if (textureView.isAvailable()) {
            startPlayback(textureView.getSurfaceTexture());
        }
    }

    private void startPlayback(SurfaceTexture texture) {
        if (player != null) {
            return;
        }
        try {
            surface = new Surface(texture);
            MediaPlayer created = new MediaPlayer();
            created.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build());
            try (AssetFileDescriptor afd = context.getAssets().openFd(LOCAL_VIDEO_ASSET)) {
                created.setDataSource(
                        afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            }
            created.setSurface(surface);
            created.setOnCompletionListener(mp -> {
                Log.i(TAG, "LOCAL_VIDEO_COMPLETED");
                callbacks.onVideoCompleted();
            });
            created.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "LOCAL_VIDEO_ERROR what=" + what + " extra=" + extra);
                callbacks.onVideoError();
                return true;
            });
            created.setOnPreparedListener(MediaPlayer::start);
            created.prepareAsync();
            player = created;
            Log.i(TAG, "LOCAL_VIDEO_PREPARING asset=" + LOCAL_VIDEO_ASSET);
        } catch (Exception error) {
            Log.w(TAG, "LOCAL_VIDEO_SETUP_FAILED");
            callbacks.onVideoError();
        }
    }

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
