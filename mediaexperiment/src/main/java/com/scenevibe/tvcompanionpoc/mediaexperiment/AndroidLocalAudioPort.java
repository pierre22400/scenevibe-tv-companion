package com.scenevibe.tvcompanionpoc.mediaexperiment;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.LocalAudioPort;

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

    AndroidLocalAudioPort(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void playShortClip() {
        stop();
        try {
            MediaPlayer created = MediaPlayer.create(context, R.raw.scenevibe_cue);
            if (created == null) {
                Log.w(TAG, "LOCAL_AUDIO_CREATE_FAILED");
                return;
            }
            created.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            created.setOnCompletionListener(mp -> Log.i(TAG, "LOCAL_AUDIO_COMPLETED"));
            created.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "LOCAL_AUDIO_ERROR what=" + what + " extra=" + extra);
                return false;
            });
            player = created;
            created.start();
            Log.i(TAG, "LOCAL_AUDIO_STARTED");
        } catch (RuntimeException error) {
            Log.w(TAG, "LOCAL_AUDIO_START_FAILED");
            stop();
        }
    }

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
