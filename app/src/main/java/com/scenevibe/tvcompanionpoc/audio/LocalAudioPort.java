package com.scenevibe.tvcompanionpoc.audio;

/**
 * Android-free seam over a SHORT local audio comment, orchestrated by
 * {@link SceneVibeAudioRenderer} in reaction to scheduler callbacks ONLY.
 *
 * <p>Mirrors the A1 spike {@code LocalAudioPort}/{@code AndroidLocalAudioPort}
 * Ports pattern. The Android adapter (future) implements this with a local player
 * (e.g. {@code MediaPlayer} + {@code AudioAttributes} {@code USAGE_ASSISTANT} /
 * {@code CONTENT_TYPE_SPEECH}); tests supply JVM fakes.</p>
 *
 * <p>Deliberate omissions, carried from the A1 {@code VoiceCoexistenceProbe} design
 * and the AUDIO 002 mandate:</p>
 * <ul>
 *   <li>NO audio-focus request of any kind (no focus-request API, no focus-gain
 *       constant). Coexistence relies on Prime continuing to play.</li>
 *   <li>NO system/global stream-volume change and no global {@code AudioManager}
 *       mutation: the
 *       SceneVibe volume is a LOCAL {@link #setGain(float)} on this port's own
 *       stream only (e.g. {@code MediaPlayer.setVolume}).</li>
 *   <li>NO transport (PLAY/PAUSE/SEEK) toward Prime and NO {@code MediaController}.</li>
 * </ul>
 *
 * <p>Codec lesson (A1 physical gate): the AAC-LC payload was valid but its M4A/MP4
 * container was REJECTED by the Sony/MediaTek OEM extractor, while the same access
 * units bundled as ADTS/AAC were a PASS. Adapters MUST prefer a bundled ADTS/AAC cue
 * and AVOID the M4A/MP4 container in that original spike profile.</p>
 * <p>Studio multimedia 001 instead resolves and verifies actual MP3 files; it never
 * substitutes the historical AAC cue for an imported resource.</p>
 */
public interface LocalAudioPort {

    /** Android-free lifecycle state of the local comment player. */
    enum State {
        /** No resource held. Fresh, released, stopped, or after an error teardown. */
        IDLE,
        /** Resource prepared but NOT audible yet ({@code prepare != start}). */
        PREPARED,
        /** Comment is audibly playing. */
        STARTED,
        /** Frozen local comment, with its player resources retained. */
        PAUSED,
        /** A prepare/start/decode failure occurred; truthful terminal state. */
        ERROR
    }

    /**
     * Prepare the LOCAL resource referenced by {@code assetRef} WITHOUT emitting
     * sound. Side-effect-free audibly: {@code prepare != start}. Used for
     * pre-preparation to reduce player-prep startup latency.
     */
    void prepare(String assetRef);

    /** Begin audible playback of the prepared comment. Safe/no-op if not prepared. */
    void start();

    /** Stop playback. Must be safe (idempotent) to call repeatedly. */
    void stop();

    /** Release the player and all resources. Must be safe to call repeatedly. */
    void release();

    /**
     * Apply the SceneVibe LOCAL gain to THIS port's own stream only, in
     * {@code [0.0, 1.0]}. Never touches system/Prime volume.
     */
    void setGain(float gain);

    /** Return the current Android-free {@link State} for truthful diagnostics. */
    State state();

    /** Advertise the native offset/freeze capability explicitly; legacy ports refuse it. */
    default boolean supportsNativeOffsets() { return false; }

    /** Freeze this local player only; unsupported implementations fail explicitly. */
    default void pause() { throw new UnsupportedOperationException("FREEZE_UNSUPPORTED"); }

    /** Resume this local player only, retaining the same native position. */
    default void resume() { throw new UnsupportedOperationException("RESUME_UNSUPPORTED"); }

    /** Seek the local MP3, never the platform player; preparation still remains silent. */
    default void seekTo(long offsetMs) { throw new UnsupportedOperationException("OFFSET_UNSUPPORTED"); }
}
