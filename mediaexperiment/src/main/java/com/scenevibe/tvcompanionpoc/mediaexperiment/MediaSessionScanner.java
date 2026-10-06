package com.scenevibe.tvcompanionpoc.mediaexperiment;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.util.Log;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.Diagnostics;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.PlaybackSnapshot;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SessionTarget;

import java.util.List;

/**
 * Isolated MediaSession discovery for the POC, mirroring the IDEA of :app's
 * {@code MediaSessionProbe} without importing it.
 *
 * <p>It uses {@code MediaSessionManager.getActiveSessions(ownListenerComponent)}
 * (gated by this module's own {@link ExperimentMediaAccessService}) to find the
 * relevant active controller, reads its {@link PlaybackState} and
 * {@link MediaMetadata}, and converts them into the Android-free FEAT-002
 * {@link PlaybackSnapshot} / {@link SessionTarget} value objects.</p>
 *
 * <p>MediaSession transport controls are the ONLY mechanism used here. There is
 * NO accessibility, NO input injection, NO coordinate injection, and NO reading
 * of the streaming app's view hierarchy.</p>
 */
final class MediaSessionScanner {
    private static final String TAG = "SceneVibeInterludePoc";

    private final Context context;
    private final MediaSessionManager sessions;
    private final ComponentName listenerComponent;

    /** The controller for the currently selected session, if any. */
    private MediaController current;

    MediaSessionScanner(Context context) {
        this.context = context.getApplicationContext();
        this.sessions = context.getSystemService(MediaSessionManager.class);
        this.listenerComponent = ExperimentNotificationAccess.component(context);
    }

    /** True when this module's notification-listener access has been granted. */
    boolean accessGranted() {
        return ExperimentNotificationAccess.isGranted(context);
    }

    /**
     * Discover and select the most relevant active controller (prefer a PLAYING
     * session). Returns null when access is missing or no session is available.
     * On success, {@link #currentController()} returns the selected controller.
     */
    PlaybackSnapshot scan() {
        current = null;
        if (!accessGranted() || sessions == null) {
            return null;
        }
        final List<MediaController> controllers;
        try {
            controllers = sessions.getActiveSessions(listenerComponent);
        } catch (SecurityException denied) {
            Log.w(TAG, "ACCESS_DENIED active sessions unavailable");
            return null;
        } catch (RuntimeException failure) {
            Log.w(TAG, "SCAN_FAILED active sessions query");
            return null;
        }
        if (controllers == null || controllers.isEmpty()) {
            return null;
        }

        MediaController chosen = null;
        PlaybackSnapshot chosenSnapshot = null;
        for (MediaController controller : controllers) {
            PlaybackSnapshot snapshot = toSnapshot(controller);
            if (chosen == null
                    || (chosenSnapshot.state != PlaybackState.STATE_PLAYING
                        && snapshot.state == PlaybackState.STATE_PLAYING)) {
                chosen = controller;
                chosenSnapshot = snapshot;
            }
        }
        current = chosen;
        return chosenSnapshot;
    }

    /** Re-read a fresh snapshot for the already-selected controller (sampling). */
    PlaybackSnapshot sampleCurrent() {
        if (current == null) {
            return null;
        }
        return toSnapshot(current);
    }

    MediaController currentController() {
        return current;
    }

    /** Build an immutable session target from the selected controller. */
    SessionTarget toTarget(PlaybackSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new SessionTarget(
                snapshot.packageName,
                snapshot.mediaId,
                snapshot.title,
                snapshot.subtitle,
                snapshot.durationMs);
    }

    /**
     * Record the bounded set of transport action flags advertised by the current
     * session into {@link Diagnostics}. Only coarse mechanism facts, never content.
     */
    void recordActions(Diagnostics diagnostics) {
        boolean play = false;
        boolean pause = false;
        boolean playPause = false;
        if (current != null) {
            PlaybackState state = current.getPlaybackState();
            if (state != null) {
                long actions = state.getActions();
                play = (actions & PlaybackState.ACTION_PLAY) != 0L;
                pause = (actions & PlaybackState.ACTION_PAUSE) != 0L;
                playPause = (actions & PlaybackState.ACTION_PLAY_PAUSE) != 0L;
            }
        }
        diagnostics.actionPlayAvailable = play;
        diagnostics.actionPauseAvailable = pause;
        diagnostics.actionPlayPauseAvailable = playPause;
    }

    /** True when the current session advertises PAUSE (or PLAY_PAUSE) support. */
    boolean canPause() {
        if (current == null) {
            return false;
        }
        PlaybackState state = current.getPlaybackState();
        if (state == null) {
            return false;
        }
        long actions = state.getActions();
        return (actions & PlaybackState.ACTION_PAUSE) != 0L
                || (actions & PlaybackState.ACTION_PLAY_PAUSE) != 0L;
    }

    private PlaybackSnapshot toSnapshot(MediaController controller) {
        PlaybackState state = controller.getPlaybackState();
        MediaMetadata metadata = controller.getMetadata();
        int stateCode = state == null ? PlaybackState.STATE_NONE : state.getState();

        String mediaId = metadataText(metadata, MediaMetadata.METADATA_KEY_MEDIA_ID);
        String title = metadataText(metadata, MediaMetadata.METADATA_KEY_TITLE);
        if (title.isEmpty()) {
            title = metadataText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        }
        String subtitle = metadataText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        long durationMs = metadata == null
                ? -1L : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);

        // The POC-local PlaybackStateCodes mirror android.media.session.PlaybackState
        // numeric values exactly, so the state code is forwarded without translation.
        return new PlaybackSnapshot(
                controller.getPackageName(),
                stateCode,
                mediaId,
                title,
                subtitle,
                durationMs);
    }

    private String metadataText(MediaMetadata metadata, String key) {
        if (metadata == null) {
            return "";
        }
        CharSequence value = metadata.getText(key);
        return value == null ? "" : value.toString();
    }
}
