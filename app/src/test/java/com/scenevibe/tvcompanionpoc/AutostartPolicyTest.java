package com.scenevibe.tvcompanionpoc;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Pure-JVM coverage of the autostart decision rules (user sections 18.C and 18.H). The
 * {@link AutostartPolicy} is Android-free, so every branch is exercised here without an
 * Android runtime, mirroring how {@link BootReceiver} would call it with the real device
 * signals.
 *
 * <p>Rule under test: START requires autostart enabled AND overlay permission AND media
 * access AND (a usable Cloud credential OR a valid cached track); otherwise the FIRST
 * unmet precondition names a bounded diagnostic code.
 */
public final class AutostartPolicyTest {

    /** The default opt-in is off, so a fresh install never arms on boot. */
    @Test public void defaultDisabledNeverStarts() {
        // All runtime preconditions satisfied, but autostart off -> disabled, no start.
        assertEquals(AutostartPolicy.Decision.AUTOSTART_DISABLED,
                AutostartPolicy.decide(false, true, true, true, true));
    }

    /** Explicitly disabled autostart returns AUTOSTART_DISABLED regardless of everything else. */
    @Test public void disabledReturnsDisabled() {
        assertEquals(AutostartPolicy.Decision.AUTOSTART_DISABLED,
                AutostartPolicy.decide(false, false, false, false, false));
        assertEquals(AutostartPolicy.Decision.AUTOSTART_DISABLED,
                AutostartPolicy.decide(false, true, true, false, true));
    }

    /** Enabled but no overlay permission -> AUTOSTART_BLOCKED_OVERLAY_PERMISSION. */
    @Test public void overlayPermissionAbsentBlocks() {
        assertEquals(AutostartPolicy.Decision.AUTOSTART_BLOCKED_OVERLAY_PERMISSION,
                AutostartPolicy.decide(true, false, true, true, true));
    }

    /** Overlay is checked before media, so a missing overlay wins even if media is also missing. */
    @Test public void overlayPermissionTakesPrecedenceOverMedia() {
        assertEquals(AutostartPolicy.Decision.AUTOSTART_BLOCKED_OVERLAY_PERMISSION,
                AutostartPolicy.decide(true, false, false, true, true));
    }

    /** Enabled, overlay granted, but no media/notification access -> media blocked. */
    @Test public void mediaAccessAbsentBlocks() {
        assertEquals(AutostartPolicy.Decision.AUTOSTART_BLOCKED_MEDIA_PERMISSION,
                AutostartPolicy.decide(true, true, false, true, true));
    }

    /** All permissions present but nothing to restore -> AUTOSTART_NOTHING_TO_RESTORE. */
    @Test public void noUsefulResourceReturnsNothingToRestore() {
        assertEquals(AutostartPolicy.Decision.AUTOSTART_NOTHING_TO_RESTORE,
                AutostartPolicy.decide(true, true, true, false, false));
    }

    /** A usable Cloud credential alone is enough to start. */
    @Test public void usableCloudCredentialStarts() {
        assertEquals(AutostartPolicy.Decision.START,
                AutostartPolicy.decide(true, true, true, true, false));
    }

    /** A valid cached track alone is enough to start. */
    @Test public void validCachedTrackStarts() {
        assertEquals(AutostartPolicy.Decision.START,
                AutostartPolicy.decide(true, true, true, false, true));
    }

    /** Both resources present also starts. */
    @Test public void bothResourcesStart() {
        assertEquals(AutostartPolicy.Decision.START,
                AutostartPolicy.decide(true, true, true, true, true));
    }
}
