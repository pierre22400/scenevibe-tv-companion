package com.scenevibe.tvcompanionpoc;

/**
 * Frozen pre-G service helpers, retained only for historical characterization.
 * These exact bodies have no production caller; G startup tests use the actual service seam.
 */
final class M4PhaseGHistoricalService {
    /**
     * Android-free, unit-testable core of the manifested-install ACK decision (section 7). It
     * installs runtimeTrack + manifest ATOMICALLY and cross-contract-validated via the FEAT-002
     * repository (which runs {@link VideoOverlayManifestBridge}, persists durably and loads the
     * scheduler), records the bounded outcome into {@code diagnostics}, then arms the regie by
     * replacing the controller's active revision from the just-committed durable copy. It
     * returns the armed revision (&gt; 0) ONLY when the whole durable install + scheduler accept
     * + regie accept chain succeeded; it returns 0 on ANY failure, in which case the prior cache
     * is left intact and the client must NOT ACK. No comment/scene content or secret is logged.
     *
     * @return the armed revision on full success, or 0 when the install/arm chain failed
     */
    static long installManifestedRevision(CloudTrackRepository repository,
            MediaSyncedTrackScheduler scheduler, SceneRuntimeController controller,
            DiagnosticsStore diagnostics, long revision, String runtimeJson, String manifestJson) {
        CloudTrackRepository.InstallResult result =
                repository.install(revision, runtimeJson, manifestJson, scheduler);
        // Observational only: record the bounded install outcome, never any content.
        diagnostics.setLastManifestCode(result.code);
        if (!result.ok) return 0;
        // Durable install + scheduler accept succeeded; now arm the regie from the just-committed
        // durable copy so the bridge stays the sole owner of the cross-contract rules (it already
        // ran inside install()).
        CloudTrackRepository.RestoreResult armed = repository.restoreWithManifest(scheduler);
        if (!armed.ok) return 0;
        if (controller != null) controller.replaceRevision(armed.revision, armed.manifest);
        return armed.revision;
    }

    /**
     * Android-free, unit-testable core of the re-delivered manifested revision re-arm
     * (section 7/14). On a manifested assignment at {@code revision <= cached} the durable
     * install already happened, so this re-arms the regie DEFENSIVELY WITHOUT re-persisting: if
     * the controller already holds a manifest for the cached revision it is a no-op success; if
     * not (e.g. the service armed a legacy restore, or a prior arm was lost) it re-reads the
     * just-cached durable manifested copy via {@link CloudTrackRepository#restoreWithManifest}
     * and arms the regie from it. It returns the armed revision (&gt; 0) only when the regie
     * holds a manifest for the cached revision afterwards, and 0 when the cache has no durable
     * manifest for it (the manifested re-delivery must then fail closed, no ACK). It never
     * lowers the active revision and never logs content.
     *
     * @return the armed (cached) revision on success, or 0 when no durable manifest is armable
     */
    static long confirmManifestedRevisionArmed(CloudTrackRepository repository,
            MediaSyncedTrackScheduler scheduler, SceneRuntimeController controller, long revision) {
        if (controller == null) return 0;
        // Already armed for the cached revision => benign idempotent re-ACK, nothing to do.
        if (controller.hasActiveManifest() && controller.activeRevision() == revision) {
            return revision;
        }
        // Not armed for this revision yet: re-read the durable manifested copy and arm from it,
        // without re-persisting. A revision with no durable manifest fails closed.
        CloudTrackRepository.RestoreResult armed = repository.restoreWithManifest(scheduler);
        if (!armed.ok || armed.revision != revision) return 0;
        controller.replaceRevision(armed.revision, armed.manifest);
        return armed.revision;
    }

    /**
     * Android-free/testable Case-B -> Case-A transition. Once a newer legacy runtime revision
     * is already durable and accepted by the scheduler, disarm any loaded OverlayManifest
     * BEFORE that legacy revision can ACK. Returning the revision lets the Android service
     * update its visual-owner revision without coupling CloudControlClient to graphics state.
     */
    static long activateLegacyRevision(SceneRuntimeController controller,long revision) {
        if(controller==null||revision<1)return 0;
        controller.unload();
        return revision;
    }

    /** This historical oracle has no live service instance. */
    private M4PhaseGHistoricalService() {}
}
