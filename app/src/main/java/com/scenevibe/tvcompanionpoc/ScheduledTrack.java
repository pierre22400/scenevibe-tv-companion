package com.scenevibe.tvcompanionpoc;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;

/**
 * Bounded runtime representation of a FinalTrack after sender-side assetRef resolution.
 *
 * This is deliberately not the canonical FinalTrack contract. The desktop sender keeps
 * machine-local asset paths outside FinalTrack, resolves them, and sends only bounded
 * runtime bytes needed by the TV renderer.
 */
final class ScheduledTrack {
    final String trackId;
    final String targetPackage;
    final List<Event> comments;

    ScheduledTrack(String trackId, String targetPackage, List<Event> comments) {
        this.trackId = trackId;
        this.targetPackage = targetPackage;
        ArrayList<Event> sorted = new ArrayList<>(comments);
        sorted.sort(Comparator.comparingLong(event -> event.startMs));
        this.comments = Collections.unmodifiableList(sorted);
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
