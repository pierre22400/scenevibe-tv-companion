package com.scenevibe.tvcompanionpoc;

import android.media.session.PlaybackState;
import android.util.Log;

import java.util.HashSet;
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
    }

    private final Listener listener;
    private final Set<String> consumed = new HashSet<>();
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
        }
    }

    private void deactivate(String reason, MediaSessionProbe.Snapshot snapshot) {
        boolean wasEligible = mediaEligible;
        mediaEligible = false;
        lastPositionMs = -1L;
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
            listener.onRender(event);
            return;
        }
    }
}
