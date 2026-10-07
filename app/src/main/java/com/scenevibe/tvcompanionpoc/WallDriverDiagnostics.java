package com.scenevibe.tvcompanionpoc;

/**
 * Bounded observational diagnostic sink for the WALL Android driver (section 15). It receives a
 * single closed {@link Code} per notable transition and NOTHING else: no eventId, token,
 * credential, URL, payload, Banner content, free-form text or stack trace ever crosses this seam.
 * A diagnostic NEVER gates selection, the clock or an ACK; it only records that an effect
 * happened. The full {@link RuntimeDiagnostics}/{@link DiagnosticsStore} WALL taxonomy wiring and
 * the {@link DiagnosticsActivity} view are a later feature (FEAT-004); this driver-local seam emits
 * only the minimal subset it needs and the production composition can forward it into the store.
 */
interface WallDriverDiagnostics {

    /**
     * The closed, bounded WALL driver diagnostic vocabulary (a subset of the section-15 taxonomy,
     * limited to what the volatile driver observes). Each is observational only.
     */
    enum Code {
        /** A fresh in-memory anchor was taken (first ARM or after a reanchor). */
        WALL_ANCHORED,
        /** The current fresh-epoch evaluation produced a DUE for the selected window. */
        WALL_EVENT_DUE,
        /** The current fresh-epoch evaluation retired the previously selected window. */
        WALL_EVENT_EXPIRED,
        /** A drift/signal correction forced a reanchor and bounded re-evaluation. */
        WALL_CLOCK_REEVALUATED,
        /** The calendar horizon is exhausted; the bounded &lt;=1s check still allows a backward
         *  correction to re-show a still-active window without creating a new occurrence. */
        WALL_HORIZON_EXHAUSTED,
        /** Presentation was suspended and hidden on a local suspension signal. */
        WALL_DISPLAY_SUSPENDED,
        /** The civil clock could not be read coherently / is out of the valid domain; the volatile
         *  path is masked and disarmed, the durable is kept, no commit/ACK is triggered. */
        WALL_CLOCK_INVALID,
        /** A bounded fail-closed condition disarmed the next wake (registration or counter limit). */
        WALL_DEADLINE_FAILED
    }

    /** Record one bounded driver transition; carries no content, only the closed code. */
    void record(Code code);
}
