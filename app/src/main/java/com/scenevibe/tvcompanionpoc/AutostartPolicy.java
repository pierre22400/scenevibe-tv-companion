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
}
