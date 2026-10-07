package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.ExecutionRequirements;
import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationHandler;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.PreparedInstallation;
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
final class BannerPreparedState implements InstallationHandler.PreparedState {
    final WallCalendar calendar;
    final OverlayManifest manifest;
    private final InstallRequest canonical;
    private final String handlerId;
    private final ExecutionRequirements requirements;

    /** Retain the exact qualified calendar/manifest pair; construction loads/renders nothing. */
    BannerPreparedState(WallCalendar calendar, OverlayManifest manifest) {
        if (calendar == null || manifest == null) {
            throw new IllegalArgumentException("Missing prepared Banner state");
        }
        this.calendar = calendar;
        this.manifest = manifest;
        this.canonical = null;
        this.handlerId = null;
        this.requirements = null;
    }

    /** Bind the already-validated calendar/manifest to their exact immutable candidate/profile. */
    private BannerPreparedState(BannerPreparedState state, InstallRequest request, String id,
            ExecutionRequirements needs) {
        this.calendar = state.calendar;
        this.manifest = state.manifest;
        this.canonical = request;
        this.handlerId = id;
        this.requirements = needs;
    }

    /** Bind already validated values to their exact immutable generic candidate/profile. */
    BannerPreparedState bind(InstallRequest request, String id, ExecutionRequirements needs) {
        return new BannerPreparedState(this, request, id, needs);
    }

    /** Check ownership and exact canonical/profile identity without reinterpreting untrusted bytes. */
    static BannerPreparedState owned(PreparedInstallation prepared, String id, String codec) {
        if (prepared == null || !id.equals(prepared.handlerId()) || !codec.equals(prepared.codecId())
                || !(prepared.preparedState() instanceof BannerPreparedState)) {
            return null;
        }
        BannerPreparedState state = (BannerPreparedState) prepared.preparedState();
        return state.canonical == prepared.canonical() && state.requirements == prepared.requirements()
                && id.equals(state.handlerId) ? state : null;
    }

    /**
     * Activate a prepared Banner through the common owner's Banner ports, in the exact ordered ARM
     * discipline the Video path uses (section 13 retire-before-load, promote-at-end): retire the
     * opposite Video visual owner and neutralize MEDIA, load under PENDING, ARM the manifest, then
     * promote. Any owner refusal or partial failure aborts to a bounded {@link InstallationStatus#ARM_FAILED}
     * with no silent partial activation. The handler (FEAT-004) and installer wiring that reach this
     * entry point are a later feature; this method pins the owner-side activation contract now.
     */
    /**
     * Activate a durable-restored Banner through the generic installer contract: resolve the exact
     * owned state and codec/handler binding, require Banner owner ports, then run the ordered ARM.
     * Any foreign metadata or wrong port type fails closed to {@link InstallationStatus#ARM_FAILED}.
     */
    static InstallationStatus arm(PreparedInstallation prepared, InstallationHandler.RuntimePorts ports,
            String id, String codec) {
        BannerPreparedState state = owned(prepared, id, codec);
        if (state == null || !(ports instanceof BannerInstallationRuntimePorts)) {
            return InstallationStatus.ARM_FAILED;
        }
        return arm(state, (BannerInstallationRuntimePorts) ports, prepared.revision());
    }

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
