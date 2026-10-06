package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationHandler;

/**
 * Video-only owner-thread activation boundary for deterministic fakes and later composition.
 * Retirement is synchronous. Operations expose no Android window, transport or ACK API.
 * M4 Phase D supplied no adapter; M5 Phase D composes the sole service-owned temporal engine.
 */
interface VideoInstallationRuntimePorts extends InstallationHandler.RuntimePorts {
    /** Verify the calling thread is allowed to mutate this runtime; false permits no mutation. */
    boolean isOwnerThread();
    /** Synchronously retire the legacy visual, including any pending visual completion. */
    boolean retireLegacyVisualOwner();
    /** Synchronously hide/unload manifested ownership and invalidate its previous generation. */
    boolean retireManifestedVisualOwner();
    /** Load the exact prepared Video binding and its memory-only calendar together. */
    boolean loadPreparedVideo(VideoPreparedState state);
    /** Arm exactly the prepared manifest and revision without reparsing any artifact. */
    boolean armPreparedManifest(long revision,OverlayManifest manifest);
    /** Select the active revision only after all prior activation operations succeeded. */
    boolean selectActiveRevision(long revision,boolean manifested);
    /** Idempotently retire any partial activation and clear active selection after failure. */
    void abortActivation();
}
