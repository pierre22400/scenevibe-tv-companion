package com.scenevibe.tvcompanionpoc.mediaexperiment;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.LocalAudioPort;

import java.io.IOException;

/**
 * Android implementation of {@link LocalAudioPort}: plays the SHORT bundled
 * SceneVibe test cue from {@code res/raw/scenevibe_cue.m4a} via {@link MediaPlayer}.
 *
 * <p>The clip is a tiny local asset; there is NO network access. The state machine
 * only calls {@link #playShortClip()} after audio focus was GRANTED, and it never
 * changes stream volume. Playback uses the same spoken-assistant AudioAttributes as
 * the focus request so the platform treats it consistently.</p>
 */
final class AndroidLocalAudioPort implements LocalAudioPort {
    private static final String TAG = "SceneVibeInterludePoc";

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
            MediaPlayer created = new MediaPlayer();
            player = created;
            created.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            try (AssetFileDescriptor descriptor =
                    context.getResources().openRawResourceFd(R.raw.scenevibe_cue)) {
                if (descriptor == null) throw new IOException("Local cue unavailable");
                created.setDataSource(descriptor.getFileDescriptor(),
                        descriptor.getStartOffset(), descriptor.getLength());
            }
            created.setOnCompletionListener(mp -> Log.i(TAG, "LOCAL_AUDIO_COMPLETED"));
            created.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "LOCAL_AUDIO_ERROR what=" + what + " extra=" + extra);
                stop();
                return true;
            });
            created.prepare();
            created.start();
            Log.i(TAG, "LOCAL_AUDIO_STARTED");
        } catch (IOException | RuntimeException error) {
            Log.w(TAG, "LOCAL_AUDIO_START_FAILED");
            stop();
            throw new IllegalStateException("Local cue setup failed", error);
        }
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
