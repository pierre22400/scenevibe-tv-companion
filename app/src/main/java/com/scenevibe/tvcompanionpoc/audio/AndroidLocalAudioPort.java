package com.scenevibe.tvcompanionpoc.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;

import com.scenevibe.tvcompanionpoc.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Android adapter for {@link LocalAudioPort}: plays the SHORT bundled SceneVibe
 * cue via {@link MediaPlayer}, driven entirely by {@link SceneVibeAudioRenderer}
 * (which is itself purely reactive to the scheduler callbacks). Modeled on the A1
 * spike {@code AndroidLocalAudioPort}.
 *
 * <p>The clip is a tiny LOCAL asset bundled in {@code res/raw/scenevibe_cue.aac};
 * there is NO network access. Playback uses the spoken-assistant AudioAttributes
 * ({@code USAGE_ASSISTANT} + {@code CONTENT_TYPE_SPEECH}) so the platform treats it
 * as speech alongside Prime, WITHOUT any audio-focus request.</p>
 *
 * <p>AUDIO 002 mandate (§15), enforced here:</p>
 * <ul>
 *   <li>NO audio-focus request of any kind (no focus-request API, no focus-gain
 *       constant); coexistence relies on Prime continuing to play.</li>
 *   <li>NO system/global stream-volume change and no global playback-manager
 *       mutation; the SceneVibe volume is applied as a LOCAL
 *       {@link MediaPlayer#setVolume(float, float)} on this player's own stream
 *       only, via {@link #setGain(float)}.</li>
 *   <li>NO transport (PLAY/PAUSE/SEEK) toward Prime and NO media-session controller.</li>
 * </ul>
 *
 * <p>Codec lesson (A1 physical gate): the AAC-LC payload was valid but its M4A/MP4
 * container was REJECTED by the Sony/MediaTek OEM extractor, while the same access
 * units bundled as ADTS/AAC were a PASS. This adapter bundles the ADTS/AAC cue and
 * AVOIDS the M4A/MP4 container.</p>
 *
 * <p>Not thread-safe: like the renderer, it is expected to be driven from a single
 * (main) thread via the scheduler callback stream.</p>
 */
public final class AndroidLocalAudioPort implements LocalAudioPort {

    private static final String TAG = "SceneVibePoc";
    private static final String CUE_CACHE_NAME = "scenevibe_cue.aac";

    private final Context context;

    private MediaPlayer player;
    private State state = State.IDLE;
    /** Last SceneVibe LOCAL gain in {@code [0.0, 1.0]}; applied to the player stream only. */
    private float gain = 1.0f;

    /** Keep only the application context for the bundled cue. */
    public AndroidLocalAudioPort(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context");
        }
        this.context = context.getApplicationContext();
    }

    /**
     * Prepare the LOCAL cue WITHOUT emitting sound ({@code prepare != start}). The
     * {@code assetRef} is accepted for symmetry with the Android-free seam; the POC
     * ships a single bundled cue so every reference resolves to it.
     */
    @Override
    public void prepare(String assetRef) {
        // Deterministic prepare: always start from a clean player.
        release();
        try {
            File cueFile = materializeCue();
            MediaPlayer created = new MediaPlayer();
            player = created;
            created.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            created.setDataSource(cueFile.getAbsolutePath());
            // LOCAL stream gain only; never a system/global volume change.
            created.setVolume(gain, gain);
            created.setOnCompletionListener(mp -> Log.i(TAG, "LOCAL_AUDIO_COMPLETED"));
            created.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "LOCAL_AUDIO_ERROR what=" + what + " extra=" + extra);
                // Teardown to a truthful terminal state; never interpreted as coexistence.
                release();
                state = State.ERROR;
                return true;
            });
            created.prepare();
            state = State.PREPARED;
            Log.i(TAG, "LOCAL_AUDIO_PREPARED source=cache-file-adts");
        } catch (IOException | RuntimeException error) {
            Log.w(TAG, "LOCAL_AUDIO_PREPARE_FAILED", error);
            release();
            state = State.ERROR;
        }
    }

    /** Begin audible playback of the prepared comment. Safe/no-op if not prepared. */
    @Override
    public void start() {
        MediaPlayer existing = player;
        if (existing == null || state != State.PREPARED) {
            return;
        }
        try {
            existing.setVolume(gain, gain);
            existing.start();
            state = State.STARTED;
            Log.i(TAG, "LOCAL_AUDIO_STARTED");
        } catch (RuntimeException error) {
            Log.w(TAG, "LOCAL_AUDIO_START_FAILED", error);
            release();
            state = State.ERROR;
        }
    }

    /**
     * Stop playback. Safe (idempotent) to call repeatedly. Returns the Android-free
     * state to {@link State#IDLE}; the underlying player is released in {@link #release()}.
     */
    @Override
    public void stop() {
        MediaPlayer existing = player;
        if (existing == null) {
            if (state != State.ERROR) {
                state = State.IDLE;
            }
            return;
        }
        try {
            existing.reset();
        } catch (RuntimeException ignored) {
            // Teardown must not throw.
        }
        state = State.IDLE;
    }

    /** Release the player and all resources. Safe to call repeatedly. */
    @Override
    public void release() {
        MediaPlayer existing = player;
        player = null;
        if (existing != null) {
            try {
                existing.reset();
            } catch (RuntimeException ignored) {
                // Teardown must not throw.
            } finally {
                try {
                    existing.release();
                } catch (RuntimeException ignored) {
                    // Teardown must not throw.
                }
            }
        }
        if (state != State.ERROR) {
            state = State.IDLE;
        }
    }

    /**
     * Apply the SceneVibe LOCAL gain to THIS player's own stream only, in
     * {@code [0.0, 1.0]}. Never touches system/Prime volume (no global
     * stream-volume setter, no global playback-manager mutation).
     */
    @Override
    public void setGain(float gain) {
        if (Float.isNaN(gain) || gain < 0.0f || gain > 1.0f) {
            throw new IllegalArgumentException("gain");
        }
        this.gain = gain;
        MediaPlayer existing = player;
        if (existing != null) {
            try {
                existing.setVolume(gain, gain);
            } catch (RuntimeException ignored) {
                // A gain change against a torn-down player must not throw.
            }
        }
    }

    /** Return the current Android-free {@link State} for truthful diagnostics. */
    @Override
    public State state() {
        return state;
    }

    /**
     * Copy the raw resource into a standalone cache file. Always rewrite it for this
     * tiny POC fixture so the bytes used by MediaPlayer match the installed APK.
     */
    private File materializeCue() throws IOException {
        File cueFile = new File(context.getCacheDir(), CUE_CACHE_NAME);
        try (InputStream input = context.getResources().openRawResource(R.raw.scenevibe_cue);
             FileOutputStream output = new FileOutputStream(cueFile, false)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        }
        Log.i(TAG, "LOCAL_AUDIO_MATERIALIZED bytes=" + cueFile.length());
        return cueFile;
    }
}
