package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Spike MediaPlayer/TextureView/Surface mechanism integrated into SceneRenderer's ONE root.
 *
 * <p>No WindowManager, network URI, AudioFocus, MediaSession or native transport is owned here.
 * A fixed APK asset is copied completely to a unique private file, verified and pinned by a
 * read-only descriptor before preparation. All callbacks return to the regie's main context.</p>
 */
final class AndroidLocalVideoPort implements LocalVideoPort {
    private final Context context;
    private final SceneRenderer renderer;
    private final Handler owner = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private Surface surface;
    private ParcelFileDescriptor descriptor;
    private File fixture;
    private SceneRenderer.LocalVideoHost host;
    private Callbacks callbacks;
    private long generation;
    private boolean prepared;
    private State state = State.DETACHED;
    private long frames;

    /** Reuse the shared renderer host rather than opening another overlay window. */
    AndroidLocalVideoPort(Context context, SceneRenderer renderer) {
        this.context = context.getApplicationContext();
        this.renderer = renderer;
    }

    /** Verify the fixed local bytes/codec, attach transparently and prepare without start/focus. */
    @Override public void prepare(LocalInterludePlan plan, long attempt, Callbacks callback) {
        assertOwner();
        if (state != State.DETACHED && state != State.RELEASED) {
            throw new IllegalStateException("PLAYER_BUSY");
        }
        generation = attempt;
        callbacks = callback;
        state = State.PREPARING;
        prepared = false;
        frames = 0;
        String stage = "FIXTURE_COPY";
        try {
            fixture = materialize();
            stage = "FIXTURE_DESCRIPTOR";
            descriptor = ParcelFileDescriptor.open(fixture, ParcelFileDescriptor.MODE_READ_ONLY);
            stage = "STREAM_PREFLIGHT";
            validateStreams();
            stage = "HOST_ATTACH";
            host = renderer.attachLocalVideo(plan, this::beforeDetach);
            final long captured = attempt;
            host.view.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                /** Bind a Surface only while this attempt still owns its renderer host. */
                @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int w, int h) {
                    if (!current(captured) || host == null || !host.attached()) return;
                    bindSurface(texture, captured);
                }
                /** Geometry remains owned by SceneRenderer, never by a second window. */
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int w, int h) {}
                /** Release the player synchronously before framework texture destruction. */
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                    if (current(captured)) fail(captured, "SURFACE_LOST");
                    return true;
                }
                /** Count actual texture updates for platform proof, without capturing content. */
                @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {
                    if (current(captured) && state == State.PLAYING) frames++;
                }
            });
            stage = "PLAYER_CREATE";
            MediaPlayer created = new MediaPlayer();
            player = created;
            created.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
            stage = "PLAYER_SOURCE";
            created.setDataSource(descriptor.getFileDescriptor());
            created.setOnPreparedListener(mp -> {
                if (!current(captured) || player != mp) return;
                int duration = mp.getDuration();
                if (duration < 9500 || duration > 10500) {
                    fail(captured, "PLAYER_DURATION_INVALID");
                    return;
                }
                prepared = true;
                readyIfComplete(captured);
            });
            created.setOnCompletionListener(mp -> {
                if (!current(captured) || player != mp || state != State.PLAYING) return;
                state = State.STOPPED;
                owner.post(() -> callback.eof(captured));
            });
            created.setOnErrorListener((mp, what, extra) -> {
                if (current(captured) && player == mp) fail(captured, "PLAYER_DECODE_ERROR");
                return true;
            });
            if (host.view.isAvailable()) bindSurface(host.view.getSurfaceTexture(), captured);
            stage = "PLAYER_ASYNC";
            created.prepareAsync();
        } catch (Exception failed) {
            fail(attempt, preparationFailure(stage, failed));
        }
    }

    /** Start only the prepared attempt; readiness alone never starts audio or decoded frames. */
    @Override public void play(long attempt) {
        assertOwner();
        if (!current(attempt) || state != State.READY || !prepared || surface == null
                || player == null || host == null || !host.attached()) {
            throw new IllegalStateException("PLAYER_NOT_READY");
        }
        try {
            player.start();
            state = State.PLAYING;
            Callbacks callback = callbacks;
            owner.post(() -> callback.started(attempt));
        } catch (RuntimeException failed) {
            fail(attempt, "PLAYER_START_FAILED");
        }
    }

    /** Stop/release the decoder and Surface before the renderer removes its TextureView. */
    @Override public void release(long attempt) {
        assertOwner();
        if (generation != attempt || state == State.RELEASED) return;
        releaseResources();
        state = State.RELEASED;
    }

    /** Return actual decoder state; the presentation state remains in SceneRuntimeController. */
    @Override public State state() { return state; }

    /** Count rendered updates for instrumentation; this is not a physical audio/TV proof. */
    long renderedFramesForTest() { return frames; }

    /** Check pinned resources are all gone; used by real Android lifecycle tests. */
    boolean resourcesReleasedForTest() {
        return player == null && surface == null && descriptor == null && fixture == null;
    }

    /** Copy and hash fixed APK bytes into a unique private file, never an external override. */
    private File materialize() throws Exception {
        File target = File.createTempFile("e5-native-035-", ".mp4", context.getCacheDir());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long count = 0;
        try (InputStream input = context.getAssets().open(LocalInterludePlan.ASSET_NAME);
                FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
                digest.update(buffer, 0, length);
                count += length;
            }
            output.getFD().sync();
        } catch (Exception failed) {
            target.delete();
            throw failed;
        }
        if (count != LocalInterludePlan.FILE_BYTES || !hex(digest.digest()).equals(LocalInterludePlan.SHA256)) {
            target.delete();
            throw new IllegalStateException("FIXTURE_DIGEST_MISMATCH");
        }
        return target;
    }

    /** Require the known MP4 streams and a platform decoder before native PAUSE is requested. */
    private void validateStreams() throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        boolean video = false;
        boolean audio = false;
        try {
            extractor.setDataSource(descriptor.getFileDescriptor());
            if (extractor.getTrackCount() != 2) throw new IllegalStateException("FIXTURE_TRACKS");
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if ("video/avc".equals(mime)) {
                    video = format.getInteger(MediaFormat.KEY_WIDTH) == 1280
                            && format.getInteger(MediaFormat.KEY_HEIGHT) == 720;
                } else if ("audio/mp4a-latm".equals(mime)) {
                    audio = format.getInteger(MediaFormat.KEY_SAMPLE_RATE) == 48000
                            && format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1;
                } else {
                    throw new IllegalStateException("FIXTURE_MIME");
                }
                if (new MediaCodecList(MediaCodecList.ALL_CODECS).findDecoderForFormat(format) == null) {
                    throw new IllegalStateException("FIXTURE_DECODER_UNAVAILABLE");
                }
            }
            if (!video || !audio) throw new IllegalStateException("FIXTURE_STREAMS");
        } finally {
            extractor.release();
        }
    }

    /** Attach a prepared Surface without starting; a late surface cannot resurrect old playback. */
    private void bindSurface(SurfaceTexture texture, long attempt) {
        if (!current(attempt) || texture == null || surface != null) return;
        try {
            surface = new Surface(texture);
            if (player != null) player.setSurface(surface);
            readyIfComplete(attempt);
        } catch (RuntimeException failed) {
            fail(attempt, "SURFACE_BIND_FAILED");
        }
    }

    /** Publish READY only after both codec preparation and a live renderer Surface are present. */
    private void readyIfComplete(long attempt) {
        if (!current(attempt) || state != State.PREPARING || !prepared || surface == null) return;
        player.setSurface(surface);
        state = State.READY;
        Callbacks callback = callbacks;
        owner.post(() -> callback.ready(attempt));
    }

    /** Synchronously release before detach; inform the regie only if retirement was unexpected. */
    private void beforeDetach() {
        if (state == State.RELEASED || state == State.DETACHED) return;
        fail(generation, "HOST_RETIRED");
    }

    /** Invalidate callbacks, release resources and report an error distinct from normal EOF. */
    private void fail(long attempt, String reason) {
        if (!current(attempt)) return;
        Callbacks callback = callbacks;
        releaseResources();
        state = State.RELEASED;
        owner.post(() -> callback.error(attempt, reason));
    }

    /** Release every owned object idempotently; native PLAY is never reachable from cleanup. */
    private void releaseResources() {
        MediaPlayer retired = player;
        player = null;
        if (retired != null) {
            retired.setOnPreparedListener(null);
            retired.setOnCompletionListener(null);
            retired.setOnErrorListener(null);
            try { retired.reset(); } catch (RuntimeException ignored) { /* Partially prepared. */ }
            try { retired.release(); } catch (RuntimeException ignored) { /* Already released. */ }
        }
        if (host != null) host.view.setSurfaceTextureListener(null);
        if (surface != null) { surface.release(); surface = null; }
        if (descriptor != null) {
            try { descriptor.close(); } catch (Exception ignored) { /* Idempotent teardown. */ }
            descriptor = null;
        }
        if (fixture != null) { fixture.delete(); fixture = null; }
        prepared = false;
    }

    /** Reject old attempts and any callback arriving after resource release. */
    private boolean current(long attempt) {
        return generation == attempt && state != State.RELEASED && state != State.DETACHED;
    }

    /** Confine player/Surface/view operations to the same serialized Android owner as the regie. */
    private static void assertOwner() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("OWNER_THREAD");
    }

    /** Diagnose fixed preparation steps without logging file paths, media or arbitrary messages. */
    private static String preparationFailure(String stage, Exception failed) {
        String message = failed.getMessage();
        if ("FIXTURE_DIGEST_MISMATCH".equals(message)
                || "FIXTURE_TRACKS".equals(message) || "FIXTURE_MIME".equals(message)
                || "FIXTURE_DECODER_UNAVAILABLE".equals(message)
                || "FIXTURE_STREAMS".equals(message)) return message;
        String type = failed instanceof SecurityException ? "PERMISSION"
                : failed instanceof java.io.FileNotFoundException ? "MISSING"
                : failed instanceof java.io.IOException ? "IO"
                : failed instanceof IllegalStateException ? "STATE" : "SETUP";
        return stage + "_" + type;
    }

    /** Encode the digest without any source path or token entering diagnostics. */
    private static String hex(byte[] bytes) {
        StringBuilder output = new StringBuilder();
        for (byte value : bytes) output.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return output.toString();
    }
}
