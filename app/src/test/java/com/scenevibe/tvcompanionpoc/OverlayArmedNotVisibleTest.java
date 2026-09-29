package com.scenevibe.tvcompanionpoc;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * FEAT-002 (Correction 1): locks the "armed != visible" boundary in Consumer Mode.
 *
 * <p>{@code OverlayService}/{@code OverlayRenderer} are Android classes and cannot be driven
 * on the pure JVM, so the mode-dependent decisions are extracted into two pure, package-private
 * static helpers that this test exercises directly:
 * <ul>
 *   <li>{@link OverlayService#shouldShowOnEntry(String, boolean)} — whether an
 *       {@code onStartCommand} entry draws the overlay immediately or merely arms the runtime.</li>
 *   <li>{@link OverlayService#shouldRestoreBadgeOnExpiry(boolean)} — whether comment expiry /
 *       media-identity loss restores the permanent status badge (LAN DEV) or fully removes the
 *       window (Consumer Mode).</li>
 * </ul>
 *
 * <p>The lazy-creation contract itself (renderer built only inside the scheduler {@code onRender}
 * when a comment is due, and recreatable after a dismiss because {@code dismiss()} nulls the
 * view state) is enforced structurally in {@code OverlayService.showRenderer()} /
 * {@code OverlayRenderer.dismiss()} and is verified by code inspection plus the compile build;
 * there is no Android WindowManager here to attach a real window.
 */
public final class OverlayArmedNotVisibleTest {

    // --- Consumer Mode (ENABLE_LAN_DEV false): every entry path is ARM-only. ---

    @Test public void consumerStartSceneVibeTopDoesNotShow() {
        assertFalse("Start SceneVibe (ACTION_TOP) must arm, not show, in Consumer Mode",
                OverlayService.shouldShowOnEntry(OverlayService.ACTION_TOP, false));
    }

    @Test public void consumerBottomDoesNotShow() {
        assertFalse("ACTION_BOTTOM must arm, not show, in Consumer Mode",
                OverlayService.shouldShowOnEntry(OverlayService.ACTION_BOTTOM, false));
    }

    @Test public void consumerCloudConnectDoesNotShow() {
        assertFalse("ACTION_CLOUD_CONNECT must arm (activate cloud), not draw a window",
                OverlayService.shouldShowOnEntry(OverlayService.ACTION_CLOUD_CONNECT, false));
    }

    @Test public void consumerBootPrepareDoesNotShow() {
        assertFalse("ACTION_BOOT_PREPARE must stay armed-not-visible in Consumer Mode",
                OverlayService.shouldShowOnEntry(OverlayService.ACTION_BOOT_PREPARE, false));
    }

    @Test public void consumerNullIntentRestartDoesNotShow() {
        assertFalse("A START_STICKY restart with a null Intent must arm, not show",
                OverlayService.shouldShowOnEntry(null, false));
    }

    @Test public void consumerExpiryRemovesWindowInsteadOfBadge() {
        assertFalse("Consumer Mode expiry/ineligibility must remove the window, not restore a badge",
                OverlayService.shouldRestoreBadgeOnExpiry(false));
    }

    // --- LAN DEV (ENABLE_LAN_DEV true): show-on-entry and badge-restore preserved. ---

    @Test public void lanDevTopShowsImmediately() {
        assertTrue("LAN DEV ACTION_TOP must show the window immediately",
                OverlayService.shouldShowOnEntry(OverlayService.ACTION_TOP, true));
    }

    @Test public void lanDevBottomShowsImmediately() {
        assertTrue("LAN DEV ACTION_BOTTOM must show the window immediately",
                OverlayService.shouldShowOnEntry(OverlayService.ACTION_BOTTOM, true));
    }

    @Test public void lanDevNullIntentRestartShowsImmediately() {
        assertTrue("LAN DEV null-Intent restart must show the window immediately",
                OverlayService.shouldShowOnEntry(null, true));
    }

    @Test public void lanDevBootPrepareStaysArmed() {
        assertFalse("ACTION_BOOT_PREPARE must stay armed-not-visible even in LAN DEV",
                OverlayService.shouldShowOnEntry(OverlayService.ACTION_BOOT_PREPARE, true));
    }

    @Test public void lanDevCloudConnectDoesNotDrawWindow() {
        // Cloud connect arms the cloud client in both modes; it never draws a window on entry.
        assertFalse("ACTION_CLOUD_CONNECT arms the cloud client but never draws a window on entry",
                OverlayService.shouldShowOnEntry(OverlayService.ACTION_CLOUD_CONNECT, true));
    }

    @Test public void lanDevExpiryRestoresBadge() {
        assertTrue("LAN DEV must keep the permanent status-badge-restore behavior on expiry",
                OverlayService.shouldRestoreBadgeOnExpiry(true));
    }
}
