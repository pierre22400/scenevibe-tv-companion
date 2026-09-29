package com.scenevibe.tvcompanionpoc;

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
        CloudDeviceCredentials credentials = new CloudDeviceCredentials(context);
        CloudTrackRepository cache = new CloudTrackRepository(context);
        long revision = cache.revision();
        boolean credentialUnavailable = credentials.credentialUnavailable();
        CloudState cloudState = credentialUnavailable
                ? CloudState.DISCONNECTED
                : credentials.userCode() != null ? CloudState.ACTIVATION_PENDING
                : credentials.connected() ? (credentials.offline() ? CloudState.OFFLINE : CloudState.CONNECTED)
                : CloudState.DISCONNECTED;
        CloudErrorCode errorCode = credentialUnavailable
                ? CloudErrorCode.CREDENTIAL_UNAVAILABLE : observed.lastCloudErrorCode();
        return new Builder()
                .appVersion(BuildConfig.VERSION_NAME)
                .serviceRunning(observed.serviceRunning())
                .autostartEnabled(AutostartPreference.isEnabled(context))
                .cloudState(cloudState)
                .installationId(new InstallationIdentity(context).installationId())
                .cloudDeviceId(credentials.cloudDeviceId())
                .cachedTrackPresent(revision > 0)
                .cachedTrackId(cache.cachedTrackId())
                .cachedRevision(revision)
                .lastAcknowledgedRevision(cache.acknowledged())
                .mediaSessionPermissionGranted(NotificationAccess.isGranted(context))
                .lastObservedMediaApp(observed.lastObservedMediaApp())
                .mediaIdentityState(observed.mediaIdentityState())
                .lastBlockCode(observed.lastBlockCode())
                .hadSuccessfulCloudConnection(observed.hadSuccessfulCloudConnection())
                .lastAssignmentRevisionReceived(observed.lastAssignmentRevisionReceived())
                .lastSuccessfulAckRevision(observed.lastSuccessfulAckRevision())
                .lastCloudErrorCode(errorCode)
                .build();
    }

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

    final String appVersion;
    final boolean serviceRunning;
    final boolean autostartEnabled;
    final CloudState cloudState;
    /** Abbreviated local installationId (never the full value); null when none exists yet. */
    final String installationIdAbbreviated;
    /** Abbreviated cloud device id (never the full value); null when none exists yet. */
    final String cloudDeviceIdAbbreviated;
    final boolean cachedTrackPresent;
    /** The cached track id (not a secret, not the full FinalTrack); null when none. */
    final String cachedTrackId;
    final long cachedRevision;
    final long lastAcknowledgedRevision;
    final boolean mediaSessionPermissionGranted;
    /** The package name of the last observed foreground media app; null when none. */
    final String lastObservedMediaApp;
    final MediaIdentityState mediaIdentityState;
    /** Bounded block code for the last fail-closed media identity decision; null when none. */
    final String lastBlockCode;
    /** Coarse marker that a successful cloud connection has occurred (no timestamp history). */
    final boolean hadSuccessfulCloudConnection;
    final long lastAssignmentRevisionReceived;
    final long lastSuccessfulAckRevision;
    final CloudErrorCode lastCloudErrorCode;

    private RuntimeDiagnostics(Builder builder) {
        this.appVersion = builder.appVersion;
        this.serviceRunning = builder.serviceRunning;
        this.autostartEnabled = builder.autostartEnabled;
        this.cloudState = builder.cloudState;
        this.installationIdAbbreviated = builder.installationIdAbbreviated;
        this.cloudDeviceIdAbbreviated = builder.cloudDeviceIdAbbreviated;
        this.cachedTrackPresent = builder.cachedTrackPresent;
        this.cachedTrackId = builder.cachedTrackId;
        this.cachedRevision = builder.cachedRevision;
        this.lastAcknowledgedRevision = builder.lastAcknowledgedRevision;
        this.mediaSessionPermissionGranted = builder.mediaSessionPermissionGranted;
        this.lastObservedMediaApp = builder.lastObservedMediaApp;
        this.mediaIdentityState = builder.mediaIdentityState;
        this.lastBlockCode = builder.lastBlockCode;
        this.hadSuccessfulCloudConnection = builder.hadSuccessfulCloudConnection;
        this.lastAssignmentRevisionReceived = builder.lastAssignmentRevisionReceived;
        this.lastSuccessfulAckRevision = builder.lastSuccessfulAckRevision;
        this.lastCloudErrorCode = builder.lastCloudErrorCode;
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
        private boolean cachedTrackPresent;
        private String cachedTrackId;
        private long cachedRevision;
        private long lastAcknowledgedRevision;
        private boolean mediaSessionPermissionGranted;
        private String lastObservedMediaApp;
        private MediaIdentityState mediaIdentityState = MediaIdentityState.UNAVAILABLE;
        private String lastBlockCode;
        private boolean hadSuccessfulCloudConnection;
        private long lastAssignmentRevisionReceived;
        private long lastSuccessfulAckRevision;
        private CloudErrorCode lastCloudErrorCode = CloudErrorCode.NONE;

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
        Builder hadSuccessfulCloudConnection(boolean value) { this.hadSuccessfulCloudConnection = value; return this; }
        Builder lastAssignmentRevisionReceived(long value) { this.lastAssignmentRevisionReceived = value; return this; }
        Builder lastSuccessfulAckRevision(long value) { this.lastSuccessfulAckRevision = value; return this; }
        Builder lastCloudErrorCode(CloudErrorCode value) { this.lastCloudErrorCode = value; return this; }

        RuntimeDiagnostics build() { return new RuntimeDiagnostics(this); }
    }
}
