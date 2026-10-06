package com.scenevibe.tvcompanionpoc;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;

/**
 * Bounded runtime representation of a FinalTrack after sender-side projection.
 * A track is bound to both a streaming package and one specific media identity.
 */
final class ScheduledTrack {
    final String trackId;
    final String targetPackage;
    final MediaIdentity mediaIdentity;
    final boolean pauseFreezesDisplay;
    final List<Event> comments;

    ScheduledTrack(String trackId, String targetPackage, MediaIdentity mediaIdentity,
            List<Event> comments, boolean pauseFreezesDisplay) {
        this.trackId = trackId;
        this.targetPackage = targetPackage;
        this.mediaIdentity = mediaIdentity;
        this.pauseFreezesDisplay = pauseFreezesDisplay;
        ArrayList<Event> sorted = new ArrayList<>(comments);
        sorted.sort(Comparator.comparingLong(event -> event.startMs));
        this.comments = Collections.unmodifiableList(sorted);
    }

    /** Canonical media identity projected from the FinalTrack and checked at playback time. */
    static final class MediaIdentity {
        final String platform;
        final String videoId;
        final String title;
        final long durationMs;

        MediaIdentity(String platform, String videoId, String title, long durationMs) {
            this.platform = platform;
            this.videoId = videoId;
            this.title = title;
            this.durationMs = durationMs;
        }
    }

    static final class Event {
        final String id;
        final String text;
        final long startMs;
        final long durationMs;
        final Bitmap mediaBitmap;

        Event(String id, String text, long startMs, long durationMs, Bitmap mediaBitmap) {
            this.id = id;
            this.text = text;
            this.startMs = startMs;
            this.durationMs = durationMs;
            this.mediaBitmap = mediaBitmap;
        }
    }
}
