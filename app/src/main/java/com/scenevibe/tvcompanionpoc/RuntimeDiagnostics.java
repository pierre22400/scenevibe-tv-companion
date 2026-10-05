package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallationSnapshot;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;

/**
 * An immutable, observational snapshot of the SceneVibe TV runtime (user section 10). It
 * is purely a read model: it is NEVER consulted to make a business decision (no start,
 * connect, ACK, render, reset or scheduling choice reads it) and it holds NO history, so
 * it is bounded by construction - one snapshot describes the current moment only.
 *
 * <p>Secret ban (user section 11): this model NEVER carries the deviceToken, the
 * activationSecret, the LAN token, the pepper, DATABASE_URL, any Vercel secret, the full
 * FinalTrack, comment text, or a raw stack trace containing network/credential values.
 * The two identities that ARE surfaced - the local installationId and the cloud
 * cloudDeviceId - are exposed ONLY abbreviated (see {@link #abbreviate(String)}), never in
 * full. Every error is a bounded short code (reusing the AUTOSTART_* / CREDENTIAL_UNAVAILABLE
 * codes and the {@link CloudErrorCode} enum), never a free-form message.
 */
final class RuntimeDiagnostics {
    /** Number of leading characters kept when abbreviating a non-secret identity. */
    static final int ABBREVIATION_LENGTH = 8;

    /**
     * Assembles the current snapshot from the persistent app-private stores plus the
     * bounded {@link DiagnosticsStore} of observed runtime signals. It only READS state and
     * abbreviates the two ids; it never mutates anything and never reads a secret value (the
     * deviceToken / activationSecret are not fetched here at all). Because a keystore failure
     * surfaces as {@link CloudDeviceCredentials#credentialUnavailable()}, that condition is
     * mapped to the bounded {@link CloudErrorCode#CREDENTIAL_UNAVAILABLE} without exposing
     * any secret.
     */
    static RuntimeDiagnostics capture(android.content.Context context, DiagnosticsStore observed) {
        // Read-only peeks: capture() must be truly observational. It must NOT run the credential
        // migration and must NOT mint/persist an installationId when none exists yet.
        CloudDeviceCredentials credentials = CloudDeviceCredentials.peek(context);
        InstallationStore store = new InstallationStore(new AndroidInstallationBackend(context));
        boolean credentialUnavailable = credentials.credentialUnavailable();
        CloudState cloudState = credentialUnavailable
                ? CloudState.DISCONNECTED
                : credentials.userCode() != null ? CloudState.ACTIVATION_PENDING
                : credentials.connected() ? (credentials.offline() ? CloudState.OFFLINE : CloudState.CONNECTED)
                : CloudState.DISCONNECTED;
        CloudErrorCode errorCode = credentialUnavailable
                ? CloudErrorCode.CREDENTIAL_UNAVAILABLE : observed.lastCloudErrorCode();
        return installationSnapshot(store, observed)
                .appVersion(BuildConfig.VERSION_NAME)
                .serviceRunning(observed.serviceRunning())
                .autostartEnabled(AutostartPreference.isEnabled(context))
                .cloudState(cloudState)
                .installationId(new InstallationIdentity(context).peekInstallationId())
                .cloudDeviceId(credentials.cloudDeviceId())
                .mediaSessionPermissionGranted(NotificationAccess.isGranted(context))
                .lastObservedMediaApp(observed.lastObservedMediaApp())
                .mediaIdentityState(observed.mediaIdentityState())
                .lastBlockCode(observed.lastBlockCode())
                .lastAutostartDecision(observed.lastAutostartDecision())
                .hadSuccessfulCloudConnection(observed.hadSuccessfulCloudConnection())
                .lastAssignmentRevisionReceived(observed.lastAssignmentRevisionReceived())
                .lastSuccessfulAckRevision(observed.lastSuccessfulAckRevision())
                .lastCloudErrorCode(errorCode)
                .lastManifestCode(observed.lastManifestCode())
                .lastSceneCode(observed.lastSceneCode())
                .build();
    }

    /** Read generic metadata once, without parsing opaque artifacts or minting/migrating any identity. */
    static Builder installationSnapshot(InstallationStore store, DiagnosticsStore observed) {
        return new Builder().installation(store.read())
                .lastStartupRestoreResult(observed.lastStartupRestoreResult());
    }

    /** Structural durable state only; READY does not claim executable capability, visibility or ACK. */
    enum InstallationState { EMPTY, READY, CORRUPT }

    /** Coarse cloud connection state; bounded, display-only, never a credential. */
    enum CloudState { CONNECTED, OFFLINE, ACTIVATION_PENDING, DISCONNECTED }

    /**
     * Whether the last projected media identity was eligible to render, structurally
     * blocked (fail-closed) or simply not yet observed. Bounded and observational only.
     */
    enum MediaIdentityState { ELIGIBLE, BLOCKED, UNAVAILABLE }

    /**
     * Bounded cloud transport outcome codes. These are the only user-facing cloud error
     * values; a raw HTTP body, stack trace or credential is never surfaced. NONE means the
     * last cloud interaction had no error to report.
     */
    enum CloudErrorCode { NONE, NETWORK, TIMEOUT, UNAUTHORIZED, PROTOCOL, CREDENTIAL_UNAVAILABLE }

    /**
     * Bounded OverlayManifest outcome codes. These are the only manifest values that ever
     * reach a diagnostics surface; comment/scene content, the full manifest JSON and any
     * credential are never surfaced. NONE means the last manifest install had no manifest
     * issue to report. MANIFEST_INVALID is a structural/contract failure (wrong product,
     * clock, bound source id, or a duplicate id); MANIFEST_INCONSISTENT is a
     * runtimeTrack&lt;-&gt;manifest mismatch (missing/extra scene or non-matching timing);
     * MANIFEST_CACHE_FAILED is a durable persistence failure during an atomic install.
     */
    enum ManifestCode { NONE, MANIFEST_INVALID, MANIFEST_INCONSISTENT, MANIFEST_CACHE_FAILED }

    /**
     * Bounded scene-runtime outcome codes (user section 19). These describe what happened the
     * last time the regie tried to turn a manifest scene into pixels; they are the only
     * scene-runtime values that reach a diagnostics surface and never carry comment/scene
     * content, the manifest JSON or any credential. A missing scene asset is a bounded SCENE
     * failure (NOT a manifest inconsistency): SCENE_ASSET_UNAVAILABLE means the regie
     * suppressed a show because a required local asset did not resolve (no Cloud transport this
     * cycle, section 15); SCENE_RENDER_FAILED means a scene draw was abandoned before any
     * partial overlay could appear. NONE means the last scene attempt had nothing to report.
     * Like every diagnostics code these are observational only and never gate logic.
     */
    enum SceneCode { NONE, SCENE_ASSET_UNAVAILABLE, SCENE_RENDER_FAILED }

    final String appVersion;
    final boolean serviceRunning;
    final boolean autostartEnabled;
    final CloudState cloudState;
    /** Abbreviated local installationId (never the full value); null when none exists yet. */
    final String installationIdAbbreviated;
    /** Abbreviated cloud device id (never the full value); null when none exists yet. */
    final String cloudDeviceIdAbbreviated;
    final InstallationState installationState;
    /** Bounded durable-read fault, purely observational and independent of startup/runtime success. */
    final InstallationStore.ReadFailure installationReadFailure;
    final boolean installationPresent;
    final long installedRevision;
    final long acknowledgedRevision;
    /** Bounded build-local metadata only; no artifact value is retained. */
    final String packageCodecId;
    final String packageHandlerId;
    /** Last startup attempt only, not the current selection or Cloud confirmation. */
    final InstallationStatus lastStartupRestoreResult;
    /** Compatibility alias of generic snapshot presence; never an independent cache authority. */
    final boolean cachedTrackPresent;
    /** Video compatibility-only id; capture leaves it null rather than parsing opaque artifacts. */
    final String cachedTrackId;
    /** Compatibility alias of installedRevision. */
    final long cachedRevision;
    /** Compatibility alias of generic acknowledgedRevision. */
    final long lastAcknowledgedRevision;
    final boolean mediaSessionPermissionGranted;
    /** The package name of the last observed foreground media app; null when none. */
    final String lastObservedMediaApp;
    final MediaIdentityState mediaIdentityState;
    /** Bounded block code for the last fail-closed media identity decision; null when none. */
    final String lastBlockCode;
    /** Bounded last autostart decision published by BootReceiver; null when none observed. */
    final AutostartPolicy.Decision lastAutostartDecision;
    /** Coarse marker that a successful cloud connection has occurred (no timestamp history). */
    final boolean hadSuccessfulCloudConnection;
    final long lastAssignmentRevisionReceived;
    final long lastSuccessfulAckRevision;
    final CloudErrorCode lastCloudErrorCode;
    /** Bounded outcome of the last OverlayManifest install/validation; NONE when none seen. */
    final ManifestCode lastManifestCode;
    /** Bounded outcome of the last scene-runtime render attempt; NONE when none seen. */
    final SceneCode lastSceneCode;

    /** Freeze scalar observational metadata only; no store, snapshot or artifact is retained. */
    private RuntimeDiagnostics(Builder builder) {
        this.appVersion = builder.appVersion;
        this.serviceRunning = builder.serviceRunning;
        this.autostartEnabled = builder.autostartEnabled;
        this.cloudState = builder.cloudState;
        this.installationIdAbbreviated = builder.installationIdAbbreviated;
        this.cloudDeviceIdAbbreviated = builder.cloudDeviceIdAbbreviated;
        this.installationState = builder.installationState;
        this.installationReadFailure = builder.installationReadFailure;
        this.installationPresent = builder.installationPresent;
        this.installedRevision = builder.installedRevision;
        this.acknowledgedRevision = builder.acknowledgedRevision;
        this.packageCodecId = builder.packageCodecId;
        this.packageHandlerId = builder.packageHandlerId;
        this.lastStartupRestoreResult = builder.lastStartupRestoreResult;
        this.cachedTrackPresent = builder.cachedTrackPresent;
        this.cachedTrackId = builder.cachedTrackId;
        this.cachedRevision = builder.cachedRevision;
        this.lastAcknowledgedRevision = builder.lastAcknowledgedRevision;
        this.mediaSessionPermissionGranted = builder.mediaSessionPermissionGranted;
        this.lastObservedMediaApp = builder.lastObservedMediaApp;
        this.mediaIdentityState = builder.mediaIdentityState;
        this.lastBlockCode = builder.lastBlockCode;
        this.lastAutostartDecision = builder.lastAutostartDecision;
        this.hadSuccessfulCloudConnection = builder.hadSuccessfulCloudConnection;
        this.lastAssignmentRevisionReceived = builder.lastAssignmentRevisionReceived;
        this.lastSuccessfulAckRevision = builder.lastSuccessfulAckRevision;
        this.lastCloudErrorCode = builder.lastCloudErrorCode;
        this.lastManifestCode = builder.lastManifestCode;
        this.lastSceneCode = builder.lastSceneCode;
    }

    /**
     * Abbreviates a non-secret identity to its first {@link #ABBREVIATION_LENGTH} characters
     * plus an ellipsis, so a diagnostics screen can distinguish devices without ever showing
     * the full value. Returns null for a null/empty input and never returns the whole value
     * of an id longer than the abbreviation length.
     */
    static String abbreviate(String value) {
        if (value == null || value.isEmpty()) return null;
        if (value.length() <= ABBREVIATION_LENGTH) return value;
        return value.substring(0, ABBREVIATION_LENGTH) + "\u2026";
    }

    /** Mutable builder; the produced {@link RuntimeDiagnostics} is immutable. */
    static final class Builder {
        private String appVersion = "";
        private boolean serviceRunning;
        private boolean autostartEnabled;
        private CloudState cloudState = CloudState.DISCONNECTED;
        private String installationIdAbbreviated;
        private String cloudDeviceIdAbbreviated;
        private InstallationState installationState = InstallationState.EMPTY;
        private InstallationStore.ReadFailure installationReadFailure = InstallationStore.ReadFailure.NONE;
        private boolean installationPresent;
        private long installedRevision;
        private long acknowledgedRevision;
        private String packageCodecId;
        private String packageHandlerId;
        private InstallationStatus lastStartupRestoreResult;
        private boolean cachedTrackPresent;
        private String cachedTrackId;
        private long cachedRevision;
        private long lastAcknowledgedRevision;
        private boolean mediaSessionPermissionGranted;
        private String lastObservedMediaApp;
        private MediaIdentityState mediaIdentityState = MediaIdentityState.UNAVAILABLE;
        private String lastBlockCode;
        private AutostartPolicy.Decision lastAutostartDecision;
        private boolean hadSuccessfulCloudConnection;
        private long lastAssignmentRevisionReceived;
        private long lastSuccessfulAckRevision;
        private CloudErrorCode lastCloudErrorCode = CloudErrorCode.NONE;
        private ManifestCode lastManifestCode = ManifestCode.NONE;
        private SceneCode lastSceneCode = SceneCode.NONE;

        /** Project one coherent read into bounded metadata and old aliases; never inspect canonical bytes. */
        Builder installation(InstallationStore.ReadResult durable) {
            installationReadFailure = durable == null ? InstallationStore.ReadFailure.BACKEND_READ_FAILED : durable.failure();
            installationState = durable == null || durable.state() == InstallationStore.ReadState.CORRUPT
                    ? InstallationState.CORRUPT : durable.state() == InstallationStore.ReadState.SNAPSHOT
                    ? InstallationState.READY : InstallationState.EMPTY;
            installationPresent = installationState == InstallationState.READY;
            InstallationSnapshot snapshot = installationPresent ? durable.snapshot() : null;
            installedRevision = snapshot == null ? 0 : snapshot.revision();
            acknowledgedRevision = installationPresent ? durable.acknowledgedRevision() : 0;
            packageCodecId = snapshot == null ? null : snapshot.codecId();
            packageHandlerId = snapshot == null ? null : snapshot.handlerId();
            cachedTrackPresent = installationPresent;
            cachedRevision = installedRevision;
            lastAcknowledgedRevision = acknowledgedRevision;
            cachedTrackId = null;
            return this;
        }

        /** Accept only the closed startup observation; null means EMPTY/not attempted. */
        Builder lastStartupRestoreResult(InstallationStatus result) {
            this.lastStartupRestoreResult = result; return this;
        }

        Builder appVersion(String value) { this.appVersion = value == null ? "" : value; return this; }
        Builder serviceRunning(boolean value) { this.serviceRunning = value; return this; }
        Builder autostartEnabled(boolean value) { this.autostartEnabled = value; return this; }
        Builder cloudState(CloudState value) { this.cloudState = value; return this; }
        /** Stores ONLY the abbreviated installationId; callers must never pass a full secret. */
        Builder installationId(String fullValue) {
            this.installationIdAbbreviated = abbreviate(fullValue); return this;
        }
        /** Stores ONLY the abbreviated cloudDeviceId; callers must never pass a full secret. */
        Builder cloudDeviceId(String fullValue) {
            this.cloudDeviceIdAbbreviated = abbreviate(fullValue); return this;
        }
        Builder cachedTrackPresent(boolean value) { this.cachedTrackPresent = value; return this; }
        Builder cachedTrackId(String value) { this.cachedTrackId = value; return this; }
        Builder cachedRevision(long value) { this.cachedRevision = value; return this; }
        Builder lastAcknowledgedRevision(long value) { this.lastAcknowledgedRevision = value; return this; }
        Builder mediaSessionPermissionGranted(boolean value) { this.mediaSessionPermissionGranted = value; return this; }
        Builder lastObservedMediaApp(String value) { this.lastObservedMediaApp = value; return this; }
        Builder mediaIdentityState(MediaIdentityState value) { this.mediaIdentityState = value; return this; }
        Builder lastBlockCode(String value) { this.lastBlockCode = value; return this; }
        Builder lastAutostartDecision(AutostartPolicy.Decision value) { this.lastAutostartDecision = value; return this; }
        Builder hadSuccessfulCloudConnection(boolean value) { this.hadSuccessfulCloudConnection = value; return this; }
        Builder lastAssignmentRevisionReceived(long value) { this.lastAssignmentRevisionReceived = value; return this; }
        Builder lastSuccessfulAckRevision(long value) { this.lastSuccessfulAckRevision = value; return this; }
        Builder lastCloudErrorCode(CloudErrorCode value) { this.lastCloudErrorCode = value; return this; }
        Builder lastManifestCode(ManifestCode value) { this.lastManifestCode = value; return this; }
        Builder lastSceneCode(SceneCode value) { this.lastSceneCode = value; return this; }

        RuntimeDiagnostics build() { return new RuntimeDiagnostics(this); }
    }
}
