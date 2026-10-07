package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.wall.WallCalendar;

/**
 * Trusted immutable Banner parse state, memory-only and never serialized. It carries only the
 * pure {@link WallCalendar} horizon and the already validated {@link OverlayManifest} whose
 * {@code product == banner}, {@code clockMode == wall} and {@code pauseBehavior == continue}
 * (section 8). It holds NO clock, scheduler, renderer, Context, service, credentials, network
 * client or store, so the common owner that binds it stays JVM-testable like the Video state.
 *
 * <p>The handler (FEAT-004) is responsible for the full scene/window bijection validation before
 * ARM; this type only pins the already trusted calendar/manifest pair the owner activates. It is
 * the WALL analogue of {@link VideoPreparedState}, deliberately parallel so a single principal
 * owner can host STATIC Video and Banner ports without a competing LiveBanner authority.</p>
 */
final class BannerPreparedState {
    final WallCalendar calendar;
    final OverlayManifest manifest;

    /** Retain the exact qualified calendar/manifest pair; construction loads/renders nothing. */
    BannerPreparedState(WallCalendar calendar, OverlayManifest manifest) {
        if (calendar == null || manifest == null) {
            throw new IllegalArgumentException("Missing prepared Banner state");
        }
        this.calendar = calendar;
        this.manifest = manifest;
    }

    /**
     * Activate a prepared Banner through the common owner's Banner ports, in the exact ordered ARM
     * discipline the Video path uses (section 13 retire-before-load, promote-at-end): retire the
     * opposite Video visual owner and neutralize MEDIA, load under PENDING, ARM the manifest, then
     * promote. Any owner refusal or partial failure aborts to a bounded {@link InstallationStatus#ARM_FAILED}
     * with no silent partial activation. The handler (FEAT-004) and installer wiring that reach this
     * entry point are a later feature; this method pins the owner-side activation contract now.
     */
    static InstallationStatus arm(BannerPreparedState state, BannerInstallationRuntimePorts ports,
            long revision) {
        if (state == null || ports == null || revision < 1) return InstallationStatus.ARM_FAILED;
        boolean owner = false;
        try {
            owner = ports.isOwnerThread();
            if (!owner) return InstallationStatus.ARM_FAILED;
            if (!ports.retireVideoVisualOwner()
                    || !ports.retireManifestedVisualOwner()
                    || !ports.loadPreparedBanner(state)
                    || !ports.armPreparedManifest(revision, state.manifest)
                    || !ports.selectActiveBanner(revision)) {
                abort(ports);
                return InstallationStatus.ARM_FAILED;
            }
            return InstallationStatus.ARMED;
        } catch (RuntimeException failed) {
            if (owner) abort(ports);
            return InstallationStatus.ARM_FAILED;
        }
    }

    /** Failure cleanup cannot throw a content-bearing exception through the handler contract. */
    private static void abort(BannerInstallationRuntimePorts ports) {
        try { ports.abortActivation(); } catch (RuntimeException failedCleanup) { /* Closed ARM_FAILED only. */ }
    }
}
