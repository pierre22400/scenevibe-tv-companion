package com.scenevibe.tvcompanionpoc;

/**
 * Pure, Android-free decision function for the "Start SceneVibe with TV" autostart
 * opt-in (user section 9 / 18.C / 18.H). It takes only booleans and returns a bounded
 * {@link Decision}, so the whole rule set is exercised on the JVM by
 * {@code AutostartPolicyTest} without any Android runtime.
 *
 * <p>The autostart may proceed to a boot-prepare (armed, not visible) start ONLY when:
 * <ol>
 *   <li>the user explicitly enabled autostart (default is OFF, so absence never starts),</li>
 *   <li>Display-over-other-apps (overlay) permission is granted,</li>
 *   <li>Notification / MediaSession access is granted, AND</li>
 *   <li>there is at least one useful resource to restore: a usable Cloud credential OR a
 *       valid cached runtime track.</li>
 * </ol>
 * Any other state yields the most specific bounded diagnostic code instead of a start;
 * nothing here launches UI, requests a permission, or throws.
 */
final class AutostartPolicy {
    private AutostartPolicy() {}

    /**
     * The bounded outcome of the boot decision. {@link #START} is the only value that
     * arms the overlay service; every other value is a no-op diagnostic recorded by the
     * receiver without any user-visible effect.
     */
    enum Decision {
        /** All preconditions met: start OverlayService in boot-prepare (armed) mode. */
        START,
        /**
         * A KNOWN Banner codec/handler durable is present with opt-in + overlay, so the service may
         * boot-prepare (armed, not visible) WITHOUT a MediaSession/Notification grant (section 12).
         * It is a distinct bounded value from {@link #START} so the diagnostics screen records that
         * the boot armed a Banner path specifically; the receiver treats it exactly like START.
         */
        START_BANNER,
        /** Autostart opt-in is off; the default state, so the TV boots with nothing armed. */
        AUTOSTART_DISABLED,
        /** Autostart enabled but Display-over-other-apps is not granted. */
        AUTOSTART_BLOCKED_OVERLAY_PERMISSION,
        /** Autostart enabled, overlay granted, but Notification/MediaSession access is missing. */
        AUTOSTART_BLOCKED_MEDIA_PERMISSION,
        /** Everything permitted, but neither a usable Cloud credential nor a cached track exists. */
        AUTOSTART_NOTHING_TO_RESTORE
    }

    /**
     * Evaluates the boot decision. The checks are ordered so the returned code names the
     * FIRST unmet precondition, which keeps the diagnostic specific and stable.
     *
     * @param autostartEnabled        the persisted "Start SceneVibe with TV" opt-in
     * @param overlayPermissionGranted Settings.canDrawOverlays result
     * @param mediaAccessGranted       NotificationAccess.isGranted result
     * @param hasUsableCloudCredential a durable deviceToken+cloudDeviceId that is NOT
     *                                 credential-unavailable
     * @param hasValidCachedTrack      a cached runtime track at revision &gt; 0
     */
    static Decision decide(boolean autostartEnabled,
            boolean overlayPermissionGranted,
            boolean mediaAccessGranted,
            boolean hasUsableCloudCredential,
            boolean hasValidCachedTrack) {
        if (!autostartEnabled) return Decision.AUTOSTART_DISABLED;
        if (!overlayPermissionGranted) return Decision.AUTOSTART_BLOCKED_OVERLAY_PERMISSION;
        if (!mediaAccessGranted) return Decision.AUTOSTART_BLOCKED_MEDIA_PERMISSION;
        if (!hasUsableCloudCredential && !hasValidCachedTrack) {
            return Decision.AUTOSTART_NOTHING_TO_RESTORE;
        }
        return Decision.START;
    }

    /**
     * The bounded durable kind the receiver reads from METADATA only (never a Banner parse): the
     * codec/handler identity of the one present installation. {@link #BANNER} is a known Banner
     * codec/handler snapshot; {@link #VIDEO} is a known Video shape; {@link #UNKNOWN} is a present
     * but unrecognized shape; {@link #NONE} is no durable installation at all.
     */
    enum DurableKind { NONE, VIDEO, BANNER, UNKNOWN }

    /**
     * Banner-aware boot decision (section 12). A KNOWN Banner codec/handler durable permits a
     * boot-prepare (armed, not visible) start on opt-in + overlay + durable present WITHOUT a
     * MediaSession/Notification grant, because a WALL Banner needs no passive MediaSession clock. A
     * {@link DurableKind#VIDEO} or {@link DurableKind#UNKNOWN} durable, and the credentials-only /
     * nothing-to-restore / permission-ordering paths, keep their exact existing decisions by
     * delegating to the five-argument {@link #decide}. This never parses a package: {@code kind} is
     * derived from durable metadata by the caller.
     *
     * @param autostartEnabled        the persisted "Start SceneVibe with TV" opt-in
     * @param overlayPermissionGranted Settings.canDrawOverlays result
     * @param mediaAccessGranted       NotificationAccess.isGranted result
     * @param hasUsableCloudCredential a usable durable Cloud credential
     * @param hasValidCachedTrack      a durable installation snapshot is present (revision &gt; 0)
     * @param kind                     the bounded durable kind read from metadata only
     */
    static Decision decide(boolean autostartEnabled,
            boolean overlayPermissionGranted,
            boolean mediaAccessGranted,
            boolean hasUsableCloudCredential,
            boolean hasValidCachedTrack,
            DurableKind kind) {
        if (!autostartEnabled) return Decision.AUTOSTART_DISABLED;
        if (!overlayPermissionGranted) return Decision.AUTOSTART_BLOCKED_OVERLAY_PERMISSION;
        // A known Banner durable may arm without the MediaSession grant: it does not drive a passive
        // MediaSession clock. Overlay + opt-in + a present known-Banner installation suffice.
        if (kind == DurableKind.BANNER && hasValidCachedTrack) {
            return Decision.START_BANNER;
        }
        // Every other path (Video, unknown, credentials-only, permission ordering) is unchanged.
        return decide(autostartEnabled, overlayPermissionGranted, mediaAccessGranted,
                hasUsableCloudCredential, hasValidCachedTrack);
    }
}
