package com.scenevibe.tvcompanionpoc;

import android.media.session.PlaybackState;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * Drives a loaded SceneVibe runtime track from the passive MediaSession clock.
 *
 * Policy for this POC:
 * - PAUSED/BUFFERING/NONE never advance or render comments.
 * - normal forward playback renders at most one due comment per probe sample;
 * - a forward jump greater than 5 s is treated as a seek and crossed comments are skipped;
 * - a backward jump greater than 2 s re-arms comments at/after the new position;
 * - comments more than 2 s late are skipped rather than burst-rendered.
 */
public final class MediaSyncedTrackScheduler {
    private static final String TAG = "SceneVibeSync";
    static final long FORWARD_SEEK_THRESHOLD_MS = 5000L;
    static final long BACKWARD_SEEK_THRESHOLD_MS = 2000L;
    static final long MAX_LATE_MS = 2000L;

    public interface Listener {
        void onRender(ScheduledTrack.Event event);
        void onPlayback(boolean playing, boolean pauseFreezesDisplay);
    }

    private final Listener listener;
    private final Set<String> consumed = new HashSet<>();
    private ScheduledTrack track;
    private long lastPositionMs = -1L;

    public MediaSyncedTrackScheduler(Listener listener) {
        this.listener = listener;
    }

    public synchronized void load(ScheduledTrack newTrack) {
        track = newTrack;
        consumed.clear();
        lastPositionMs = -1L;
        Log.i(TAG, "TRACK_LOADED trackId=" + newTrack.trackId
                + " targetPackage=" + newTrack.targetPackage
                + " comments=" + newTrack.comments.size());
    }

    public synchronized void clear() {
        if (track != null) {
            Log.i(TAG, "TRACK_CLEARED trackId=" + track.trackId);
        }
        track = null;
        consumed.clear();
        lastPositionMs = -1L;
    }

    public synchronized void onPlaybackSnapshot(MediaSessionProbe.Snapshot snapshot) {
        if (track == null || snapshot == null) return;
        if (!track.targetPackage.equals(snapshot.packageName)) return;
        listener.onPlayback(snapshot.state == PlaybackState.STATE_PLAYING,
                track.pauseFreezesDisplay);

        long positionMs = snapshot.estimatedPositionMs >= 0L
                ? snapshot.estimatedPositionMs : snapshot.positionMs;
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
