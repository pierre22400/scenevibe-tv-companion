package com.scenevibe.tvcompanionpoc.mediaexperiment;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.LocalAudioPort;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Android implementation of {@link LocalAudioPort}: plays the SHORT bundled
 * SceneVibe test cue from {@code res/raw/scenevibe_cue.m4a} via {@link MediaPlayer}.
 *
 * <p>The clip is a tiny local asset; there is NO network access. The state machine
 * only calls {@link #playShortClip()} after audio focus was GRANTED, and it never
 * changes stream volume. Playback uses the same spoken-assistant AudioAttributes as
 * the focus request so the platform treats it consistently.</p>
 *
 * <p>Some Android TV / MediaTek builds cannot reliably parse an MP4/M4A resource
 * when MediaPlayer receives the APK file descriptor plus a non-zero asset offset.
 * The cue is therefore copied byte-for-byte to the app cache and played from a
 * standalone file path. This changes only fixture transport, not audio-focus or
 * media-session behaviour.</p>
 */
final class AndroidLocalAudioPort implements LocalAudioPort {
    private static final String TAG = "SceneVibeInterludePoc";
    private static final String CUE_CACHE_NAME = "scenevibe_cue.m4a";

    private final Context context;
    private MediaPlayer player;

    /** Keep only the application context for the bundled cue. */
    AndroidLocalAudioPort(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Configure AudioAttributes and the local data source before preparing the player. */
    @Override
    public void playShortClip() {
        stop();
        try {
            File cueFile = materializeCue();

            MediaPlayer created = new MediaPlayer();
            player = created;
            created.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            created.setDataSource(cueFile.getAbsolutePath());
            created.setOnCompletionListener(mp -> Log.i(TAG, "LOCAL_AUDIO_COMPLETED"));
            created.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "LOCAL_AUDIO_ERROR what=" + what + " extra=" + extra);
                stop();
                return true;
            });
            created.prepare();
            created.start();
            Log.i(TAG, "LOCAL_AUDIO_STARTED source=cache-file");
        } catch (IOException | RuntimeException error) {
            Log.w(TAG, "LOCAL_AUDIO_START_FAILED", error);
            stop();
            throw new IllegalStateException("Local cue setup failed", error);
        }
    }

    /**
     * Copy the raw resource into a standalone file.
     *
     * <p>Always rewrite it for this tiny POC fixture so the bytes used by MediaPlayer
     * are guaranteed to match the APK currently installed.</p>
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

    /** Release cue playback safely, including partially prepared players. */
    @Override
    public void stop() {
        MediaPlayer existing = player;
        player = null;
        if (existing == null) {
            return;
        }
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
}
