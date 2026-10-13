package com.scenevibe.tvcompanionpoc;

/**
 * Local-only decoder seam controlled exclusively by SceneRuntimeController.
 * Preparation must attach a host, validate complete bytes and prepare without playback/focus.
 * Callback generations are captured at prepare and remain immutable through release.
 */
interface LocalVideoPort {
    /** Actual decoder lifecycle; it is diagnostic, not a second presentation owner. */
    enum State { DETACHED, PREPARING, READY, PLAYING, STOPPED, RELEASED }
    /** Decoder callbacks are serialized onto the regie's owner context. */
    interface Callbacks {
        /** Bytes/codec/duration and decoder are ready; no image/audio has started. */
        void ready(long generation);
        /** The player actually accepted start under the confirmed native pause. */
        void started(long generation);
        /** Normal file completion, distinct from every other termination. */
        void eof(long generation);
        /** Preparation, decoder, surface or lifecycle failure with a bounded reason. */
        void error(long generation, String reason);
    }

    /** Prepare only the trusted fixture and return readiness through generation-bound callbacks. */
    void prepare(LocalInterludePlan plan, long generation, Callbacks callbacks);

    /** Start the already prepared player; refuse obsolete generations or missing surfaces. */
    void play(long generation);

    /** Stop/release before the renderer detaches its Surface; idempotent and never resumes native. */
    void release(long generation);

    /** Expose the player state without selecting an event or commanding native transport. */
    State state();
}
