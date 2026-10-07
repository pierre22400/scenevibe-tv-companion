package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.wall.WallCalendarScheduler;

/**
 * Banner (WALL) owner-thread activation boundary, the WALL analogue of
 * {@link VideoInstallationRuntimePorts}. Retirement is synchronous. Operations expose no Android
 * window, transport, ACK, clock or timer API: the Android WALL clock/driver is deliberately
 * deferred to FEAT-003 and lives OUTSIDE the pure {@code wall/} package. These ports let the
 * single principal common owner ARM a prepared Banner package through the exact same
 * load/arm/select discipline the Video ports already use, so the whole surface stays JVM-testable
 * with fakes.
 *
 * <p>Section 7 alternating-kind invariant: selecting Banner first retires the opposite (Video)
 * visual owner and neutralizes the MEDIA path; there is exactly one current installation, one
 * principal owner and one active temporal path, never a concurrent Video+Banner installation.</p>
 */
interface BannerInstallationRuntimePorts {
    /** Verify the calling thread is allowed to mutate this runtime; false permits no mutation. */
    boolean isOwnerThread();
    /** Synchronously retire the opposite Video visual owner(s) before any Banner promotion. */
    boolean retireVideoVisualOwner();
    /** Synchronously hide/unload the Banner manifested ownership and invalidate its generation. */
    boolean retireManifestedVisualOwner();
    /** Load the exact prepared Banner calendar+manifest under PENDING only; pending never renders. */
    boolean loadPreparedBanner(BannerPreparedState state);
    /** Arm exactly the prepared manifest and revision without reparsing any artifact. */
    boolean armPreparedManifest(long revision, OverlayManifest manifest);
    /** Promote PENDING to ACTIVE only after every prior activation operation succeeded. */
    boolean selectActiveBanner(long revision);
    /**
     * Route one already-selected pure {@link WallCalendarScheduler.Result} under the opaque active
     * token: EXIT then DUE, generation-guarded, to the reused controller. A stale/pending token is
     * a NO-OP. The owner, not the controller, holds the activation token and captured generation.
     */
    void onWallResult(String token, WallCalendarScheduler.Result result);
    /** Idempotently retire any partial activation and clear active selection after failure. */
    void abortActivation();
}
