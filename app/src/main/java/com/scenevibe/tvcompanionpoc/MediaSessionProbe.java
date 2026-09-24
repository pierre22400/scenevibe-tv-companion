package com.scenevibe.tvcompanionpoc;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.List;

/**
 * Passive one-second probe of active Android MediaSessions.
 *
 * It reads only published session metadata/playback state and never sends
 * transport controls or interacts with the streaming application's UI.
 */
public final class MediaSessionProbe {
    private static final String TAG = "SceneVibeMedia";
    private static final long SAMPLE_INTERVAL_MS = 1000L;

    private final Context context;
    private final MediaSessionManager sessions;
    private final ComponentName listenerComponent;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean started;

    private final Runnable sampler = new Runnable() {
        @Override
        public void run() {
            if (!started) return;
            sample();
            handler.postDelayed(this, SAMPLE_INTERVAL_MS);
        }
    };

    public MediaSessionProbe(Context context) {
        this.context = context;
        sessions = context.getSystemService(MediaSessionManager.class);
        listenerComponent = NotificationAccess.component(context);
    }

    public void start() {
        if (started) return;
        started = true;
        Log.i(TAG, "Passive MediaSession probe started; intervalMs=" + SAMPLE_INTERVAL_MS);
        sampler.run();
    }

    public void stop() {
        started = false;
        handler.removeCallbacks(sampler);
        Log.i(TAG, "Passive MediaSession probe stopped");
    }

    private void sample() {
        if (!NotificationAccess.isGranted(context)) {
            Log.i(TAG, "ACCESS_REQUIRED notificationListener=false");
            return;
        }
        if (sessions == null) {
            Log.w(TAG, "MEDIA_SESSION_MANAGER_UNAVAILABLE");
            return;
        }

        final List<MediaController> controllers;
        try {
            controllers = sessions.getActiveSessions(listenerComponent);
        } catch (SecurityException error) {
            Log.w(TAG, "ACCESS_DENIED active sessions unavailable", error);
            return;
        } catch (RuntimeException error) {
            Log.w(TAG, "PROBE_FAILED active sessions query", error);
            return;
        }

        if (controllers.isEmpty()) {
            Log.i(TAG, "NO_ACTIVE_SESSIONS");
            return;
        }

        for (int index = 0; index < controllers.size(); index++) {
            logController(index, controllers.get(index));
        }
    }

    private void logController(int index, MediaController controller) {
        PlaybackState state = controller.getPlaybackState();
        MediaMetadata metadata = controller.getMetadata();

        long positionMs = state == null ? -1L : state.getPosition();
        float speed = state == null ? 0f : state.getPlaybackSpeed();
        long updatedAtMs = state == null ? -1L : state.getLastPositionUpdateTime();
        long updateAgeMs = updatedAtMs <= 0L ? -1L
                : Math.max(0L, SystemClock.elapsedRealtime() - updatedAtMs);
        long estimatedPositionMs = estimatePositionMs(state, positionMs, speed, updateAgeMs);

        String title = metadataText(metadata, MediaMetadata.METADATA_KEY_TITLE);
        if (title.isEmpty()) {
            title = metadataText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        }
        String subtitle = metadataText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        long durationMs = metadata == null
                ? -1L : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);

        Log.i(TAG,
                "SESSION index=" + index
                + " package=" + controller.getPackageName()
                + " state=" + stateName(state)
                + " positionMs=" + positionMs
                + " estimatedMs=" + estimatedPositionMs
                + " speed=" + speed
                + " updateAgeMs=" + updateAgeMs
                + " durationMs=" + durationMs
                + " title=" + safe(title)
                + " subtitle=" + safe(subtitle));
    }

    private long estimatePositionMs(
            PlaybackState state, long positionMs, float speed, long updateAgeMs) {
        if (state == null || positionMs < 0L || updateAgeMs < 0L) return positionMs;
        int code = state.getState();
        if (code != PlaybackState.STATE_PLAYING
                && code != PlaybackState.STATE_FAST_FORWARDING
                && code != PlaybackState.STATE_REWINDING) {
            return positionMs;
        }
        return Math.max(0L, positionMs + Math.round(updateAgeMs * speed));
    }

    private String metadataText(MediaMetadata metadata, String key) {
        if (metadata == null) return "";
        CharSequence value = metadata.getText(key);
        return value == null ? "" : value.toString();
    }

    private String stateName(PlaybackState state) {
        if (state == null) return "NONE";
        switch (state.getState()) {
            case PlaybackState.STATE_NONE: return "NONE";
            case PlaybackState.STATE_STOPPED: return "STOPPED";
            case PlaybackState.STATE_PAUSED: return "PAUSED";
            case PlaybackState.STATE_PLAYING: return "PLAYING";
            case PlaybackState.STATE_FAST_FORWARDING: return "FAST_FORWARDING";
            case PlaybackState.STATE_REWINDING: return "REWINDING";
            case PlaybackState.STATE_BUFFERING: return "BUFFERING";
            case PlaybackState.STATE_ERROR: return "ERROR";
            case PlaybackState.STATE_CONNECTING: return "CONNECTING";
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS: return "SKIPPING_TO_PREVIOUS";
            case PlaybackState.STATE_SKIPPING_TO_NEXT: return "SKIPPING_TO_NEXT";
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM: return "SKIPPING_TO_QUEUE_ITEM";
            default: return "UNKNOWN_" + state.getState();
        }
    }

    private String safe(String value) {
        if (value == null || value.isEmpty()) return "-";
        return value.replace('\n', ' ').replace('\r', ' ').replace(' ', '_');
    }
}
