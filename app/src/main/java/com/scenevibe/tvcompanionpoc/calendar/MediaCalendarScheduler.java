package com.scenevibe.tvcompanionpoc.calendar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Passive, serialized MEDIA scheduling of exact IDs and bounded temporal values.
 * Inputs alone advance the state. Callbacks occur at the historical mutation
 * boundaries, without batching, retry, logging or an autonomous clock. Tokens
 * are external opaque activation labels, captured once for each transition.
 */
public final class MediaCalendarScheduler {
    private static final long MAX_LATE_MS = 2000L;
    private static final long FORWARD_SEEK_THRESHOLD_MS = 5000L;
    private static final long BACKWARD_SEEK_THRESHOLD_MS = 2000L;

    /** Receive only temporal effects and the producing activation's captured label. */
    public interface Sink {
        /** Remove or authorize the external visual binding without rendering here. */
        void onEligibility(String token, boolean eligible);
        /** Forward every eligible playback observation, including repeated values. */
        void onPlayback(String token, boolean playing, boolean freezeOnPause);
        /** Identify the first due event after its consumption and optional window mutation. */
        void onDue(String token, String eventId);
        /** Identify an expired event after removing its window. */
        void onExpire(String token, String eventId);
    }

    private final Sink sink;
    private final Set<String> consumed = new HashSet<>();
    private final Map<String, SceneEvent> windowed = new HashMap<>();
    private MediaCalendar calendar;
    private String activationToken;
    private long lastPositionMs = -1L;
    private boolean mediaEligible;

    /** Retain the synchronous sink; no collaborator starts background work. */
    public MediaCalendarScheduler(Sink sink) {
        this.sink = sink;
    }

    /** Emit invalidation for the previous binding before replacing and resetting state. */
    public synchronized void load(MediaCalendar next, String token) {
        sink.onEligibility(activationToken, false);
        calendar = next;
        activationToken = token;
        consumed.clear();
        windowed.clear();
        lastPositionMs = -1L;
        mediaEligible = false;
    }

    /** Invalidate the old binding before clearing it, even when already empty. */
    public synchronized void clear() {
        sink.onEligibility(activationToken, false);
        calendar = null;
        activationToken = null;
        consumed.clear();
        windowed.clear();
        lastPositionMs = -1L;
        mediaEligible = false;
    }

    /** Lose eligibility without expiring windows or erasing consumed identities. */
    public synchronized void onUnavailable() {
        if (calendar == null) return;
        deactivate(activationToken);
    }

    /** Apply one already-selected observation with the exact historical branch order. */
    public synchronized void onObservation(MediaObservation observation) {
        if (calendar == null || observation == null) return;
        final String token = activationToken;
        if (!observation.eligible()) {
            deactivate(token);
            return;
        }
        long positionMs = observation.positionMs();
        if (!mediaEligible) {
            mediaEligible = true;
            sink.onEligibility(token, true);
            lastPositionMs = -1L;
            if (positionMs >= 0L) rearmFrom(positionMs, token);
        }
        sink.onPlayback(token, observation.playing(), calendar.freezeOnPause());
        if (positionMs < 0L) return;
        if (lastPositionMs < 0L) {
            lastPositionMs = positionMs;
            skipTooOld(positionMs);
            if (observation.playing()) {
                renderDue(positionMs, token);
                expireElapsed(positionMs, token);
            }
            return;
        }
        long deltaMs = positionMs - lastPositionMs;
        if (deltaMs > FORWARD_SEEK_THRESHOLD_MS) {
            skipThrough(positionMs);
            lastPositionMs = positionMs;
            expireElapsed(positionMs, token);
            return;
        }
        if (deltaMs < -BACKWARD_SEEK_THRESHOLD_MS) {
            rearmFrom(positionMs, token);
            lastPositionMs = positionMs;
            return;
        }
        lastPositionMs = positionMs;
        if (observation.playing()) {
            renderDue(positionMs, token);
            expireElapsed(positionMs, token);
        }
    }

    /** Drop windows silently before the single eligibility-loss callback. */
    private void deactivate(String token) {
        boolean wasEligible = mediaEligible;
        mediaEligible = false;
        lastPositionMs = -1L;
        windowed.clear();
        if (wasEligible) sink.onEligibility(token, false);
    }

    /** Consume strictly too-old starts, retaining the exact 2000-ms boundary. */
    private void skipTooOld(long positionMs) {
        long cutoff = Math.max(0L, positionMs - MAX_LATE_MS);
        for (SceneEvent event : calendar.events()) {
            if (event.startMs() < cutoff) consumed.add(event.eventId());
        }
    }

    /** A forward seek consumes even the event exactly at the landing position. */
    private void skipThrough(long positionMs) {
        for (SceneEvent event : calendar.events()) {
            if (event.startMs() <= positionMs) consumed.add(event.eventId());
        }
    }

    /** Rearm in calendar order, removing each window before its immediate callback. */
    private void rearmFrom(long positionMs, String token) {
        for (SceneEvent event : calendar.events()) {
            if (event.startMs() >= positionMs && consumed.remove(event.eventId())) {
                SceneEvent removed = windowed.remove(event.eventId());
                if (removed != null) sink.onExpire(token, removed.eventId());
            }
        }
    }

    /** Consume and window exactly one first candidate before emitting DUE. */
    private void renderDue(long positionMs, String token) {
        skipTooOld(positionMs);
        for (SceneEvent event : calendar.events()) {
            if (consumed.contains(event.eventId())) continue;
            if (event.startMs() > positionMs) break;
            consumed.add(event.eventId());
            if (event.durationMs() > 0L) windowed.put(event.eventId(), event);
            sink.onDue(token, event.eventId());
            return;
        }
    }

    /** Collect in raw HashMap order, then remove and callback individually. */
    private void expireElapsed(long positionMs, String token) {
        if (windowed.isEmpty()) return;
        List<SceneEvent> due = null;
        for (SceneEvent event : windowed.values()) {
            if (event.startMs() + event.durationMs() <= positionMs) {
                if (due == null) due = new ArrayList<>();
                due.add(event);
            }
        }
        if (due == null) return;
        for (SceneEvent event : due) {
            windowed.remove(event.eventId());
            sink.onExpire(token, event.eventId());
        }
    }
}
