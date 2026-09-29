package com.scenevibe.tvcompanionpoc;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Corrective review lock (review iteration 1): the Cloud client must be restartable after a
 * Reset.
 *
 * <p>Before the fix, {@code CloudControlClient.reset()} only flipped {@code running=false} and
 * {@code OverlayService} kept the same spent instance. A later {@code ACTION_CLOUD_CONNECT}
 * then hit {@code activate()}'s {@code if(!running) return}, silently doing nothing until the
 * whole service was restarted. The fix makes {@code reset()} tear its io executor fully down
 * (so no scheduled poll survives) and has {@code OverlayService} null the {@code cloudClient}
 * field after reset, so the next {@code onStartCommand} entry reconstructs a fresh, armed
 * client via the existing construction guard.
 *
 * <p>{@code CloudControlClient} wraps {@code Context}/HTTPS and cannot be driven on the pure
 * JVM, so this test locks the lifecycle decision that governs restartability through the pure,
 * package-private {@link OverlayService#shouldReconstructCloudClient(boolean, boolean)} helper,
 * which mirrors the real {@code onStartCommand} construction guard.
 */
public final class CloudClientRestartAfterResetTest {

    /**
     * After a reset the {@code cloudClient} field is null (dropped by the reset handler), so
     * with a configured Cloud origin the next entry MUST reconstruct a fresh client. This is
     * the exact condition that re-arms the cloud instead of leaving a silent no-op behind.
     */
    @Test public void reconstructsFreshClientAfterResetNulledTheField() {
        boolean cloudClientNull = true;   // ACTION_CLOUD_RESET set cloudClient = null
        boolean cloudOriginEmpty = false; // a real https origin is configured
        assertTrue("A null client field with a configured origin must reconstruct + start a fresh client",
                OverlayService.shouldReconstructCloudClient(cloudClientNull, cloudOriginEmpty));
    }

    /**
     * While a live client already exists (steady state, no reset yet) the entry must NOT build
     * a second client; the existing instance keeps polling.
     */
    @Test public void doesNotRebuildWhileLiveClientExists() {
        assertFalse("An existing (non-null) client must not be reconstructed",
                OverlayService.shouldReconstructCloudClient(false, false));
    }

    /**
     * With Cloud disabled (empty origin) no client is ever constructed, even after a reset
     * nulled the field. Reset restartability only applies when the cloud is configured.
     */
    @Test public void neverReconstructsWhenCloudOriginEmpty() {
        assertFalse("An empty CLOUD_ORIGIN must never construct a client, even with a null field",
                OverlayService.shouldReconstructCloudClient(true, true));
        assertFalse("An empty CLOUD_ORIGIN with a live client also constructs nothing",
                OverlayService.shouldReconstructCloudClient(false, true));
    }

    /**
     * Sanity: the helper is a pure mirror of the guard {@code cloudClient == null &&
     * !CLOUD_ORIGIN.isEmpty()}. Only the (null field, configured origin) quadrant is truthy,
     * which is precisely the post-reset re-arm case.
     */
    @Test public void onlyNullFieldWithConfiguredOriginIsTruthy() {
        assertTrue(OverlayService.shouldReconstructCloudClient(true, false));
        assertFalse(OverlayService.shouldReconstructCloudClient(true, true));
        assertFalse(OverlayService.shouldReconstructCloudClient(false, false));
        assertFalse(OverlayService.shouldReconstructCloudClient(false, true));
    }
}
