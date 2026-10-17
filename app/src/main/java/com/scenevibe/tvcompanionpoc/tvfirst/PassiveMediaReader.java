package com.scenevibe.tvcompanionpoc.tvfirst;

import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read public MediaSession facts only. Session tokens remain private map keys and are
 * replaced by process-local labels; no transport controls, application automation or
 * edition/foreground inference is performed. Only the current bounded batch is retained.
 */
public final class PassiveMediaReader {
    private final Map<MediaSession.Token, String> keys = new HashMap<>();
    private long nextKey, sequence;

    /** Construct an idle reader with no network, timer or platform query. */
    public PassiveMediaReader() { }

    /** Capture one platform query; failures publish explicit unknowns without legacy fallback. */
    public MediaEvidence capture(MediaSessionManager manager, ComponentName listener,
            boolean notificationAccess, boolean overlayAccess) {
        long captured = SystemClock.elapsedRealtime();
        long number = ++sequence;
        if (!notificationAccess) return unavailable(number, captured,
                MediaEvidence.Availability.ACCESS_REQUIRED, notificationAccess, overlayAccess);
        if (manager == null) return unavailable(number, captured,
                MediaEvidence.Availability.UNAVAILABLE, notificationAccess, overlayAccess);
        try {
            List<MediaController> controllers = manager.getActiveSessions(listener);
            if (controllers == null) return unavailable(number, captured,
                    MediaEvidence.Availability.UNAVAILABLE, notificationAccess, overlayAccess);
            if (controllers.size() > MediaEvidence.MAX_SESSIONS) {
                keys.clear();
                return batch(number, captured, MediaEvidence.Availability.TOO_MANY_SESSIONS,
                        notificationAccess, overlayAccess, controllers.size(), new ArrayList<>());
            }
            List<MediaEvidence.Session> observations = new ArrayList<>();
            Map<MediaSession.Token, String> retained = new HashMap<>();
            for (MediaController controller : controllers) {
                MediaSession.Token token = controller.getSessionToken();
                if (token == null) throw new IllegalStateException("Session identity unavailable");
                String key = retained.get(token);
                if (key == null) key = keys.get(token);
                if (key == null) key = "session-" + (++nextKey);
                retained.put(token, key);
                PlaybackState state = controller.getPlaybackState();
                MediaMetadata metadata = controller.getMetadata();
                long position = state == null ? -1 : state.getPosition();
                long anchor = state == null ? -1 : state.getLastPositionUpdateTime();
                float speed = state == null ? Float.NaN : state.getPlaybackSpeed();
                int code = state == null ? PlaybackState.STATE_NONE : state.getState();
                observations.add(new MediaEvidence.Session(key, controller.getPackageName(),
                        metadataText(metadata, MediaMetadata.METADATA_KEY_MEDIA_ID),
                        metadataText(metadata, MediaMetadata.METADATA_KEY_TITLE),
                        metadataText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE), code,
                        position, anchor, speed, estimate(position, anchor, speed, code, captured),
                        metadata == null || !metadata.containsKey(MediaMetadata.METADATA_KEY_DURATION)
                                ? -1 : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)));
            }
            keys.clear(); keys.putAll(retained);
            return batch(number, captured, MediaEvidence.Availability.AVAILABLE,
                    notificationAccess, overlayAccess, controllers.size(), observations);
        } catch (RuntimeException refused) {
            return unavailable(number, captured, MediaEvidence.Availability.QUERY_FAILED,
                    notificationAccess, overlayAccess);
        }
    }

    /** Retain a missing raw metadata field rather than invent a title or identifier. */
    private static String metadataText(MediaMetadata metadata, String key) {
        if (metadata == null) return null;
        CharSequence value = metadata.getText(key);
        return value == null ? null : value.toString();
    }

    /** Descriptive extrapolation only; invalid/future anchors are never clamped into validity. */
    static long estimate(long position, long anchor, float speed, int state, long captured) {
        if (position < 0 || anchor <= 0 || anchor > captured || !Float.isFinite(speed)) return -1;
        if (state != PlaybackState.STATE_PLAYING && state != PlaybackState.STATE_FAST_FORWARDING
                && state != PlaybackState.STATE_REWINDING) return position;
        double estimated = position + (captured - anchor) * (double) speed;
        return !Double.isFinite(estimated) || estimated < 0 || estimated >= Long.MAX_VALUE
                ? -1 : Math.round(estimated);
    }

    /** Bound lifetime state after a missing source; no removed session key remains eligible. */
    private MediaEvidence unavailable(long number, long captured, MediaEvidence.Availability state,
            boolean notification, boolean overlay) {
        keys.clear();
        return batch(number, captured, state, notification, overlay, 0, new ArrayList<>());
    }

    /** Acquisition alone never proves programme identity, edition, foreground or timeline. */
    private static MediaEvidence batch(long number, long captured, MediaEvidence.Availability state,
            boolean notification, boolean overlay, int count, List<MediaEvidence.Session> sessions) {
        return new MediaEvidence(number, captured, state, notification, overlay, count, sessions,
                MediaEvidence.Proof.UNKNOWN, MediaEvidence.Proof.UNKNOWN,
                MediaEvidence.Proof.UNKNOWN, MediaEvidence.Proof.UNKNOWN);
    }
}
