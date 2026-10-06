package com.scenevibe.tvcompanionpoc;

import android.media.session.PlaybackState;
import com.scenevibe.tvcompanionpoc.calendar.MediaObservation;

/**
 * NON-LIVE Video boundary: the unchanged matcher selects eligibility and the
 * unchanged probe has already estimated position. This stateless adapter neither
 * acquires a session nor changes Video identity, playback or installation policy.
 */
final class VideoMediaObservationAdapter {
    /** Preserve null no-op; canonicalize mismatch, otherwise copy the legacy selection. */
    static MediaObservation observe(ScheduledTrack track, MediaSessionProbe.Snapshot snapshot) {
        if (track == null || snapshot == null) return null;
        if (!MediaIdentityMatcher.matches(track, snapshot)) {
            return new MediaObservation(false, -1L, false);
        }
        return new MediaObservation(true,
                snapshot.estimatedPositionMs >= 0L ? snapshot.estimatedPositionMs : snapshot.positionMs,
                snapshot.state == PlaybackState.STATE_PLAYING);
    }

    /** Prevent construction of a boundary with no mutable acquisition state. */
    private VideoMediaObservationAdapter() {}
}
