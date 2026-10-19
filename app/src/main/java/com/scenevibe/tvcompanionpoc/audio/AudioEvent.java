package com.scenevibe.tvcompanionpoc.audio;

/**
 * Minimal, ADDITIVE, Android-free AUDIO event value type.
 *
 * <p>The existing TEXT event contracts cannot represent an AUDIO comment:
 * {@code calendar/SceneEvent} carries only {@code {eventId, startMs, durationMs}}
 * and {@code ScheduledTrack.Event} carries {@code {id, text, startMs, durationMs,
 * mediaBitmap}}. Neither has a type discriminant, a local asset reference, a
 * SceneVibe gain, nor a pause/seek policy. This value adds exactly the mandated
 * fields and nothing else. It is NOT serialized into any Cloud/production JSON
 * schema; TEXT events ({@code SceneEvent}, {@code ScheduledTrack.Event}) stay
 * byte-for-byte unchanged.</p>
 *
 * <p>The temporal bounds mirror {@code SceneEvent} ({@code startMs} in
 * {@code [0, 43_200_000]}, {@code durationMs <= 60_000}, {@code eventId} non-empty
 * and {@code <= 128} UTF-16 units) so the AUDIO path lives on the same media clock
 * as TEXT. The asset reference is an OPAQUE local id/name (a bundled/local
 * resource), never a Cloud URL.</p>
 */
public final class AudioEvent {

    /** Discriminant that distinguishes AUDIO from TEXT; today TEXT has none. */
    public enum Type {
        /** The only type this value can carry. Present to make routing explicit. */
        AUDIO
    }

    /**
     * Behaviour of an already-started comment on pause/seek.
     *
     * <p>Default is {@link #STOP_ON_PAUSE}: unlike TEXT which freezes its media
     * window while the position does not advance, an in-progress audio comment is
     * stopped on pause (a frozen-then-resumed half-phrase is perceived as a defect,
     * and sound over a frozen picture is undesirable). {@link #FREEZE_AND_RESUME}
     * is recorded as an alternative but is NOT the default pending physical Sony
     * proof.</p>
     */
    public enum PausePolicy {
        /** Stop the comment on observed pause; do not replay a consumed phrase. */
        STOP_ON_PAUSE,
        /** Reserved alternative; not selected by default (needs physical proof). */
        FREEZE_AND_RESUME
    }

    private final String eventId;
    private final Type type;
    private final String assetRef;
    private final long startMs;
    private final long durationMs;
    private final float gain;
    private final PausePolicy pausePolicy;

    /**
     * Validate the bounded AUDIO value in the style of {@code SceneEvent}.
     *
     * @param eventId     opaque identity, non-empty, at most 128 UTF-16 units
     * @param assetRef    opaque LOCAL asset id/name (never a Cloud URL), non-empty,
     *                    at most 256 UTF-16 units
     * @param startMs     media start position, in {@code [0, 43_200_000]}
     * @param durationMs  expected media window, at most {@code 60_000}
     * @param gain        SceneVibe LOCAL gain in {@code [0.0, 1.0]} (not NaN)
     * @param pausePolicy pause/seek policy for an in-progress comment
     */
    public AudioEvent(String eventId, Type type, String assetRef, long startMs,
                      long durationMs, float gain, PausePolicy pausePolicy) {
        if (eventId == null || eventId.isEmpty() || eventId.length() > 128
                || type == null
                || assetRef == null || assetRef.isEmpty() || assetRef.length() > 256
                || startMs < 0 || startMs > 43_200_000L || durationMs > 60_000L
                || Float.isNaN(gain) || gain < 0.0f || gain > 1.0f
                || pausePolicy == null) {
            throw new IllegalArgumentException("Invalid audio event");
        }
        this.eventId = eventId;
        this.type = type;
        this.assetRef = assetRef;
        this.startMs = startMs;
        this.durationMs = durationMs;
        this.gain = gain;
        this.pausePolicy = pausePolicy;
    }

    /** Convenience factory: type AUDIO, default {@link PausePolicy#STOP_ON_PAUSE}. */
    public static AudioEvent audio(String eventId, String assetRef, long startMs,
                                   long durationMs, float gain) {
        return new AudioEvent(eventId, Type.AUDIO, assetRef, startMs, durationMs, gain,
                PausePolicy.STOP_ON_PAUSE);
    }

    /** Return the exact opaque identifier, including whitespace and Unicode. */
    public String eventId() {
        return eventId;
    }

    /** Return the type discriminant (always {@link Type#AUDIO} here). */
    public Type type() {
        return type;
    }

    /** Return the opaque LOCAL asset reference (bundled/local resource). */
    public String assetRef() {
        return assetRef;
    }

    /** Return the validated media start position in milliseconds. */
    public long startMs() {
        return startMs;
    }

    /** Return the exact expected media window duration. */
    public long durationMs() {
        return durationMs;
    }

    /** Return the SceneVibe LOCAL gain in {@code [0.0, 1.0]}. */
    public float gain() {
        return gain;
    }

    /** Return the pause/seek policy for an in-progress comment. */
    public PausePolicy pausePolicy() {
        return pausePolicy;
    }
}
