package com.scenevibe.tvcompanionpoc.mediaexperiment;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.util.Log;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SpeechFixturePort;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Spike 2.0 MP3 player using an explicitly provisioned app-specific external file.
 *
 * <p>It deliberately makes NO AudioFocus request. This violates the usual media
 * app preference and is permitted here ONLY as a user-initiated, time-bounded,
 * isolated diagnostic of Android's mixing ability. It neither captures Prime
 * audio nor changes global volume or the native playback transport.</p>
 *
 * <p>CI bundles a reproducibly encoded MP3 as a fallback. An optional file
 * in this experimental package's external-files directory takes precedence.
 * The bundled fallback is copied into this package's private cache before
 * use, avoiding OEM extractors interpreting APK resource offsets. Neither
 * path needs network, broad storage, cloud identity or production data.</p>
 */
final class AndroidSpeechFixturePort implements SpeechFixturePort {
    private static final String TAG = "SceneVibeVoiceSpike2";
    private static final String FILENAME = "scenevibe_voice_10s.mp3";
    private final Context context;
    private final Runnable completed;
    private final Runnable failed;
    private MediaPlayer player;

    /** Retain an application context and service-owned completion handlers. */
    AndroidSpeechFixturePort(Context context, Runnable completed, Runnable failed) {
        this.context = context.getApplicationContext();
        this.completed = completed;
        this.failed = failed;
    }

    /** Validate a 10-second local MP3 and start voice playback without requesting focus. */
    @Override
    public void start() {
        stop();
        File fixture;
        try {
            fixture = fixtureFile();
        } catch (IOException failure) {
            throw new IllegalStateException("VOICE_FIXTURE_MISSING", failure);
        }
        if (!fixture.isFile() || fixture.length() < 1000L) {
            throw new IllegalStateException("VOICE_FIXTURE_MISSING");
        }
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(fixture.getAbsolutePath());
            String duration = metadata.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION);
            long millis = duration == null ? -1L : Long.parseLong(duration);
            if (millis < 9500L || millis > 10500L) {
                throw new IllegalStateException("VOICE_FIXTURE_DURATION_NOT_10S");
            }
        } finally {
            try {
                metadata.release();
            } catch (java.io.IOException releaseFailure) {
                Log.w(TAG, "METADATA_RELEASE_FAILED", releaseFailure);
            }
        }
        try {
            MediaPlayer prepared = new MediaPlayer();
            player = prepared;
            prepared.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            prepared.setDataSource(fixture.getAbsolutePath());
            prepared.setOnCompletionListener(mp -> {
                if (player == mp) completed.run();
            });
            prepared.setOnErrorListener((mp, what, extra) -> {
                if (player == mp) failed.run();
                return true;
            });
            prepared.prepare();
            prepared.start();
            Log.i(TAG, "VOICE_MP3_STARTED no_focus_request=true");
        } catch (Exception error) {
            stop();
            throw new IllegalStateException("VOICE_MP3_START_FAILED", error);
        }
    }

    /**
     * Prefer an explicit experiment-only external override, otherwise copy the
     * packaged sample to the private app cache for OEM-friendly decoding.
     * No production state is consulted or modified.
     */
    private File fixtureFile() throws IOException {
        File directory = context.getExternalFilesDir(null);
        File override = directory == null ? null : new File(directory, FILENAME);
        if (override != null && override.exists()) return override;
        File fixture = new File(context.getCacheDir(), FILENAME);
        try (InputStream input = context.getAssets().open(FILENAME);
             FileOutputStream output = new FileOutputStream(fixture, false)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.flush();
        }
        return fixture;
    }

    /** Release local playback without touching Prime or any global audio stream. */
    @Override
    public void stop() {
        MediaPlayer existing = player;
        player = null;
        if (existing == null) return;
        try {
            existing.reset();
        } catch (RuntimeException ignored) {
            // Safely release partially prepared or already stopped players.
        } finally {
            existing.release();
        }
    }
}
