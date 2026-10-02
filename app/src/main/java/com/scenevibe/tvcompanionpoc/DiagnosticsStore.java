package com.scenevibe.tvcompanionpoc;

/**
 * The process-lifetime, bounded holder for the OBSERVATIONAL runtime signals that the
 * OverlayService / CloudControlClient / scheduler report (user section 10). It keeps a
 * single most-recent value per field - never a growing list - so it can never leak memory
 * or turn into an unbounded log. It is written by the runtime as it observes events and
 * read only to render {@link RuntimeDiagnostics}; nothing in the app changes a decision
 * based on it.
 *
 * <p>Secret ban (user section 11): only bounded codes and non-secret identifiers are
 * accepted. Media app is a package name, block/error values are bounded codes, revisions
 * are integers. No setter accepts a deviceToken, activationSecret, LAN token, pepper, full
 * FinalTrack, comment text or raw stack trace, and none is stored. The persistent facts
 * (app version, autostart opt-in, cached track, abbreviated ids, permissions) are NOT held
 * here; they are read from their own stores when a snapshot is assembled.
 *
 * <p>A single {@link #INSTANCE} is shared so the service can publish while the Diagnostics
 * screen reads; all access is synchronized and the getters return immutable value types.
 */
final class DiagnosticsStore {
    /** Shared observational store for the running process. */
    static final DiagnosticsStore INSTANCE = new DiagnosticsStore();

    private boolean serviceRunning;
    private RuntimeDiagnostics.CloudState cloudState = RuntimeDiagnostics.CloudState.DISCONNECTED;
    private String lastObservedMediaApp;
    private RuntimeDiagnostics.MediaIdentityState mediaIdentityState =
            RuntimeDiagnostics.MediaIdentityState.UNAVAILABLE;
    private String lastBlockCode;
    private AutostartPolicy.Decision lastAutostartDecision;
    private boolean hadSuccessfulCloudConnection;
    private long lastAssignmentRevisionReceived;
    private long lastSuccessfulAckRevision;
    private RuntimeDiagnostics.CloudErrorCode lastCloudErrorCode =
            RuntimeDiagnostics.CloudErrorCode.NONE;
    private RuntimeDiagnostics.ManifestCode lastManifestCode =
            RuntimeDiagnostics.ManifestCode.NONE;
    private RuntimeDiagnostics.SceneCode lastSceneCode =
            RuntimeDiagnostics.SceneCode.NONE;

    /** Package-visible so JVM tests can build a fresh, isolated store without the singleton. */
    DiagnosticsStore() {}

    /** Observational: the foreground service is up (true) or torn down (false). */
    synchronized void setServiceRunning(boolean running) { this.serviceRunning = running; }

    /** Observational: coarse cloud connection state; marks a successful connection once seen. */
    synchronized void setCloudState(RuntimeDiagnostics.CloudState state) {
        if (state != null) this.cloudState = state;
        if (state == RuntimeDiagnostics.CloudState.CONNECTED) this.hadSuccessfulCloudConnection = true;
    }

    /** Observational: the package name (never content) of the last foreground media app. */
    synchronized void setLastObservedMediaApp(String packageName) {
        this.lastObservedMediaApp = packageName;
    }

    /** Observational: whether the last media identity decision was eligible/blocked/unavailable. */
    synchronized void setMediaIdentityState(RuntimeDiagnostics.MediaIdentityState state) {
        if (state != null) this.mediaIdentityState = state;
    }

    /** Observational: bounded short code for the last fail-closed media identity decision. */
    synchronized void setLastBlockCode(String code) { this.lastBlockCode = code; }

    /**
     * Observational: the last bounded autostart {@link AutostartPolicy.Decision} the
     * BootReceiver computed (START or a specific skip reason). It is a bounded enum, never
     * free-form text, and it never gates behavior; it only surfaces on the diagnostics
     * screen instead of living solely in Logcat. It is NOT reset by the Cloud reset because
     * it describes the boot decision, not a live cloud identity/session.
     */
    synchronized void setLastAutostartDecision(AutostartPolicy.Decision decision) {
        if (decision != null) this.lastAutostartDecision = decision;
    }

    /** Observational: the highest assignment revision the client has received from the cloud. */
    synchronized void setLastAssignmentRevisionReceived(long revision) {
        if (revision > this.lastAssignmentRevisionReceived) this.lastAssignmentRevisionReceived = revision;
    }

    /** Observational: the last revision the cloud successfully acknowledged. */
    synchronized void setLastSuccessfulAckRevision(long revision) {
        if (revision > this.lastSuccessfulAckRevision) this.lastSuccessfulAckRevision = revision;
    }

    /** Observational: bounded cloud transport outcome; never a raw body or stack trace. */
    synchronized void setLastCloudErrorCode(RuntimeDiagnostics.CloudErrorCode code) {
        if (code != null) this.lastCloudErrorCode = code;
    }

    /**
     * Observational: bounded outcome of the last OverlayManifest install/validation (valid,
     * invalid contract, inconsistent with the runtimeTrack, or a durable cache failure). It
     * is a bounded enum, never comment/scene content or the full manifest JSON, and it never
     * gates behavior; it only surfaces on the diagnostics screen.
     */
    synchronized void setLastManifestCode(RuntimeDiagnostics.ManifestCode code) {
        if (code != null) this.lastManifestCode = code;
    }

    /**
     * Observational: bounded outcome of the last scene-runtime render attempt (a missing local
     * asset => SCENE_ASSET_UNAVAILABLE, an abandoned draw => SCENE_RENDER_FAILED). It is a
     * bounded enum, never comment/scene content, the manifest JSON or a credential, and it
     * never gates behavior; it only surfaces on the diagnostics screen. A missing scene asset
     * is recorded here as a bounded SCENE failure, NOT as a manifest inconsistency.
     */
    synchronized void setLastSceneCode(RuntimeDiagnostics.SceneCode code) {
        if (code != null) this.lastSceneCode = code;
    }

    /**
     * Clears the observational runtime signals that describe a live cloud identity/session
     * back to their neutral defaults. Used by the EXCEPTIONAL Cloud reset so the diagnostics
     * view does not keep showing stale connection/ACK markers after the identity is wiped.
     * This never influences business logic; it only resets the read model.
     */
    synchronized void resetCloudObservations() {
        this.cloudState = RuntimeDiagnostics.CloudState.DISCONNECTED;
        this.hadSuccessfulCloudConnection = false;
        this.lastAssignmentRevisionReceived = 0;
        this.lastSuccessfulAckRevision = 0;
        this.lastCloudErrorCode = RuntimeDiagnostics.CloudErrorCode.NONE;
        this.lastManifestCode = RuntimeDiagnostics.ManifestCode.NONE;
        this.lastSceneCode = RuntimeDiagnostics.SceneCode.NONE;
    }

    synchronized boolean serviceRunning() { return serviceRunning; }
    synchronized RuntimeDiagnostics.CloudState cloudState() { return cloudState; }
    synchronized String lastObservedMediaApp() { return lastObservedMediaApp; }
    synchronized RuntimeDiagnostics.MediaIdentityState mediaIdentityState() { return mediaIdentityState; }
    synchronized String lastBlockCode() { return lastBlockCode; }
    synchronized AutostartPolicy.Decision lastAutostartDecision() { return lastAutostartDecision; }
    synchronized boolean hadSuccessfulCloudConnection() { return hadSuccessfulCloudConnection; }
    synchronized long lastAssignmentRevisionReceived() { return lastAssignmentRevisionReceived; }
    synchronized long lastSuccessfulAckRevision() { return lastSuccessfulAckRevision; }
    synchronized RuntimeDiagnostics.CloudErrorCode lastCloudErrorCode() { return lastCloudErrorCode; }
    synchronized RuntimeDiagnostics.ManifestCode lastManifestCode() { return lastManifestCode; }
    synchronized RuntimeDiagnostics.SceneCode lastSceneCode() { return lastSceneCode; }
}
