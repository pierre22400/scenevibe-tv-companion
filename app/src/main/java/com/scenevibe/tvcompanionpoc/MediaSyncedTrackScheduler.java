package com.scenevibe.tvcompanionpoc;

import android.media.session.PlaybackState;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Drives a loaded SceneVibe runtime track from the passive MediaSession clock.
 * The clock is accepted only while the active session matches the track's exact media identity.
 */
public final class MediaSyncedTrackScheduler {
    private static final String TAG = "SceneVibeSync";
    static final long FORWARD_SEEK_THRESHOLD_MS = 5000L;
    static final long BACKWARD_SEEK_THRESHOLD_MS = 2000L;
    static final long MAX_LATE_MS = 2000L;

    public interface Listener {
        void onRender(ScheduledTrack.Event event);
        void onPlayback(boolean playing, boolean pauseFreezesDisplay);
        /** False means any currently visible track card must disappear immediately. */
        default void onEligibility(boolean eligible) {}
        /**
         * A previously rendered comment's media-time window ({@code startMs + durationMs}) has
         * elapsed, so a scene driven by this comment must disappear now. This is media-position
         * driven, NOT a wall-clock timer: expiry only advances with the MediaSession position,
         * so a pause (which stops the position from advancing) naturally freezes the window and
         * a backward seek before the window re-arms the comment. The scheduler remains the sole
         * temporal authority (sections 7/4/12); the regie decides how to hide. Case A (legacy
         * OverlayRenderer) self-expires via its own freeze-aware countdown and ignores this.
         */
        default void onExpire(ScheduledTrack.Event event) {}
    }

    private final Listener listener;
    private final Set<String> consumed = new HashSet<>();
    /**
     * Comments that have been rendered (onRender fired) and whose media-time window has not yet
     * elapsed. Each is expired via {@link Listener#onExpire} once the media position reaches
     * {@code startMs + durationMs}. Keyed by event id so a backward seek that re-arms a comment
     * also removes any pending window for it. Only populated for a positive durationMs.
     */
    private final Map<String, ScheduledTrack.Event> windowed = new HashMap<>();
    private ScheduledTrack track;
    private long lastPositionMs = -1L;
    private boolean mediaEligible;

    public MediaSyncedTrackScheduler(Listener listener) {
        this.listener = listener;
    }

    public synchronized void load(ScheduledTrack newTrack) {
        listener.onEligibility(false);
        track = newTrack;
        consumed.clear();
        windowed.clear();
        lastPositionMs = -1L;
        mediaEligible = false;
        Log.i(TAG, "TRACK_LOADED trackId=" + newTrack.trackId
                + " targetPackage=" + newTrack.targetPackage
                + " mediaVideoId=" + newTrack.mediaIdentity.videoId
                + " comments=" + newTrack.comments.size());
    }

    public synchronized void clear() {
        listener.onEligibility(false);
        if (track != null) {
            Log.i(TAG, "TRACK_CLEARED trackId=" + track.trackId);
        }
        track = null;
        consumed.clear();
        windowed.clear();
        lastPositionMs = -1L;
        mediaEligible = false;
    }

    /** No active/authorized session is never allowed to leave a frozen card on screen. */
    public synchronized void onPlaybackUnavailable() {
        if (track == null) return;
        deactivate("session_unavailable", null);
    }

    public synchronized void onPlaybackSnapshot(MediaSessionProbe.Snapshot snapshot) {
        if (track == null || snapshot == null) return;
        if (!MediaIdentityMatcher.matches(track, snapshot)) {
            deactivate("media_identity_mismatch", snapshot);
            return;
        }

        long positionMs = snapshot.estimatedPositionMs >= 0L
                ? snapshot.estimatedPositionMs : snapshot.positionMs;

        if (!mediaEligible) {
            mediaEligible = true;
            listener.onEligibility(true);
            lastPositionMs = -1L;
            if (positionMs >= 0L) {
                int rearmed = rearmFrom(positionMs);
                Log.i(TAG, "MEDIA_IDENTITY_MATCH trackId=" + track.trackId
                        + " package=" + snapshot.packageName
                        + " positionMs=" + positionMs
                        + " rearmedFuture=" + rearmed);
            }
        }

        listener.onPlayback(snapshot.state == PlaybackState.STATE_PLAYING,
                track.pauseFreezesDisplay);
        if (positionMs < 0L) return;

        if (lastPositionMs < 0L) {
            lastPositionMs = positionMs;
            int skipped = skipTooOld(positionMs);
            Log.i(TAG, "CLOCK_ANCHORED package=" + snapshot.packageName
                    + " state=" + snapshot.stateName
                    + " positionMs=" + positionMs
                    + " skippedPast=" + skipped);
            if (snapshot.state == PlaybackState.STATE_PLAYING) {
                renderDue(positionMs);
                expireElapsed(positionMs);
            }
            return;
        }

        long deltaMs = positionMs - lastPositionMs;

        if (deltaMs > FORWARD_SEEK_THRESHOLD_MS) {
            int skipped = skipThrough(positionMs);
            Log.i(TAG, "FORWARD_SEEK fromMs=" + lastPositionMs
                    + " toMs=" + positionMs
                    + " skipped=" + skipped);
            lastPositionMs = positionMs;
            // A forward seek jumps media time past any comment whose window ends before the new
            // position, so its scene must disappear just as it would have on normal playback.
            expireElapsed(positionMs);
            return;
        }

        if (deltaMs < -BACKWARD_SEEK_THRESHOLD_MS) {
            int rearmed = rearmFrom(positionMs);
            Log.i(TAG, "BACKWARD_SEEK fromMs=" + lastPositionMs
                    + " toMs=" + positionMs
                    + " rearmed=" + rearmed);
            lastPositionMs = positionMs;
            return;
        }

        lastPositionMs = positionMs;
        if (snapshot.state == PlaybackState.STATE_PLAYING) {
            renderDue(positionMs);
            expireElapsed(positionMs);
        }
    }

    private void deactivate(String reason, MediaSessionProbe.Snapshot snapshot) {
        boolean wasEligible = mediaEligible;
        mediaEligible = false;
        lastPositionMs = -1L;
        // Eligibility loss hides the active path immediately (the regie drops the visible scene
        // on onEligibility(false)); any pending media-time window is now moot, so drop it rather
        // than fire a late onExpire for a scene that is already gone.
        windowed.clear();
        if (wasEligible) listener.onEligibility(false);
        if (wasEligible || snapshot != null) {
            Log.i(TAG, "MEDIA_IDENTITY_BLOCKED reason=" + reason
                    + " package=" + (snapshot == null ? "-" : snapshot.packageName)
                    + " title=" + (snapshot == null ? "-" : snapshot.title)
                    + " subtitle=" + (snapshot == null ? "-" : snapshot.subtitle));
        }
    }

    private int skipTooOld(long positionMs) {
        int count = 0;
        long cutoff = Math.max(0L, positionMs - MAX_LATE_MS);
        for (ScheduledTrack.Event event : track.comments) {
            if (event.startMs < cutoff && consumed.add(event.id)) {
                count++;
            }
        }
        return count;
    }

    private int skipThrough(long positionMs) {
        int count = 0;
        for (ScheduledTrack.Event event : track.comments) {
            if (event.startMs <= positionMs && consumed.add(event.id)) {
                count++;
            }
        }
        return count;
    }

    private int rearmFrom(long positionMs) {
        int count = 0;
        for (ScheduledTrack.Event event : track.comments) {
            if (event.startMs >= positionMs && consumed.remove(event.id)) {
                // A comment seeked back before its own start is fully re-armed: forget any
                // pending media-time window so a later forward replay re-renders and re-windows
                // it cleanly rather than expiring against the stale previous window.
                windowed.remove(event.id);
                count++;
            }
        }
        return count;
    }

    private void renderDue(long positionMs) {
        skipTooOld(positionMs);
        for (ScheduledTrack.Event event : track.comments) {
            if (consumed.contains(event.id)) continue;
            if (event.startMs > positionMs) break;
            consumed.add(event.id);
            Log.i(TAG, "COMMENT_DUE trackId=" + track.trackId
                    + " id=" + event.id
                    + " scheduledMs=" + event.startMs
                    + " positionMs=" + positionMs
                    + " latenessMs=" + Math.max(0L, positionMs - event.startMs)
                    + " media=" + (event.mediaBitmap != null));
            // Arm the media-time window so this comment is expired once the position reaches
            // startMs+durationMs. A non-positive duration has no window (never auto-expires here,
            // matching the legacy renderer which only self-expires a positive duration).
            if (event.durationMs > 0L) {
                windowed.put(event.id, event);
            }
            listener.onRender(event);
            return;
        }
    }

    /**
     * Fires {@link Listener#onExpire} for every armed comment whose media-time window
     * ({@code startMs + durationMs}) has elapsed at the current position, then forgets it. This
     * is the sole media-driven expiry path: it is called only on a position ADVANCE while
     * playing (and once per forward-seek-through), so a pause - which does not advance the
     * MediaSession position - never drains a window, and the qualified freeze behavior holds for
     * Case B scenes. Backward seeks are handled by {@link #rearmFrom} re-arming the comment.
     */
    private void expireElapsed(long positionMs) {
        if (windowed.isEmpty()) return;
        List<ScheduledTrack.Event> due = null;
        for (ScheduledTrack.Event event : windowed.values()) {
            if (event.startMs + event.durationMs <= positionMs) {
                if (due == null) due = new ArrayList<>();
                due.add(event);
            }
        }
        if (due == null) return;
        for (ScheduledTrack.Event event : due) {
            windowed.remove(event.id);
            Log.i(TAG, "COMMENT_EXPIRED trackId=" + track.trackId
                    + " id=" + event.id
                    + " scheduledMs=" + event.startMs
                    + " durationMs=" + event.durationMs
                    + " positionMs=" + positionMs);
            listener.onExpire(event);
        }
    }
}
