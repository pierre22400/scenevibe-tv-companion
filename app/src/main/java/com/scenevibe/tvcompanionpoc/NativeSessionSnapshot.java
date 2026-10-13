package com.scenevibe.tvcompanionpoc;

import java.util.Objects;

/**
 * Coherent, ephemeral observation from ONE native MediaController.
 *
 * <p>The token never leaves memory. Provider update time and our observation time are distinct;
 * no field modifies the historical MediaSessionProbe or the MEDIA calendar. VOD confirmation
 * is a local operator attestation bound to this target, not inferred ad/live detection.</p>
 */
final class NativeSessionSnapshot {
    static final int PLAYING = 3;
    static final int PAUSED = 2;

    final Object token;
    final String packageName;
    final String mediaId;
    final String title;
    final String subtitle;
    final long durationMs;
    final long positionMs;
    final long observedAtMs;
    final long updatedAtMs;
    final float speed;
    final int state;
    final boolean canPause;
    final boolean canPlay;
    final boolean vodConfirmed;

    /** Capture values from the same controller; invalid evidence is refused by the owner. */
    NativeSessionSnapshot(Object token, String packageName, String mediaId, String title,
            String subtitle, long durationMs, long positionMs, long observedAtMs,
            long updatedAtMs, float speed, int state, boolean canPause, boolean canPlay,
            boolean vodConfirmed) {
        this.token = token;
        this.packageName = packageName;
        this.mediaId = clean(mediaId);
        this.title = clean(title);
        this.subtitle = clean(subtitle);
        this.durationMs = durationMs;
        this.positionMs = positionMs;
        this.observedAtMs = observedAtMs;
        this.updatedAtMs = updatedAtMs;
        this.speed = speed;
        this.state = state;
        this.canPause = canPause;
        this.canPlay = canPlay;
        this.vodConfirmed = vodConfirmed;
    }

    /**
     * Reuse the Spike's token-first/ID-first identity rule, narrowed to exact metadata.
     * No permissive title containment or 180-second duration tolerance is added to M5.
     */
    boolean sameTarget(NativeSessionSnapshot other) {
        if (other == null || token == null || !Objects.equals(token, other.token)
                || !Objects.equals(packageName, other.packageName)) return false;
        if (!mediaId.equals(other.mediaId)) return false;
        if (mediaId.isEmpty() && title.length() < 8) return false;
        return title.equals(other.title) && subtitle.equals(other.subtitle)
                && durationMs == other.durationMs && durationMs > 0L;
    }

    /** Estimate only a freshly bounded PLAYING observation; PAUSED uses the raw anchor. */
    long estimatedPosition(long nowMs) {
        if (state != PLAYING || speed != 1f || nowMs < updatedAtMs) return positionMs;
        double result = positionMs + (double) (nowMs - updatedAtMs);
        return result >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) result;
    }

    /** Canonicalize null metadata without exposing any value through diagnostics. */
    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }
}
