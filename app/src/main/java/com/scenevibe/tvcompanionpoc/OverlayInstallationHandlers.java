package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationHandlerRegistry;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;

/**
 * The single common static composition for local qualification (section 8): the two existing Video
 * handlers PLUS one Banner handler, bound explicitly against build-local instances. It replaces the
 * exclusively-Video {@link VideoInstallationHandlers#registry()} as the installer's registry source
 * without any discovery, reflection or remote handler, and without a hidden Banner branch inside the
 * Video handlers. Counts and the codec whitelist stay concordant with
 * {@link TvCapabilities#supportedCodecs()}; the Banner codec expects a single artifact.
 *
 * <p>This is the registry/handler-shape extension needed for LOCAL qualification. It is NOT a live
 * public capability flip and begins no Cloud wire: {@code supportsWallClockExecution()} stays false
 * and local Banner qualification is proven by the composition/driver tests, not by the flag.</p>
 */
final class OverlayInstallationHandlers {
    private static final BannerInstallationHandler BANNER = new BannerInstallationHandler();
    private static final InstallationHandlerRegistry REGISTRY = new InstallationHandlerRegistry(
            new InstallationHandlerRegistry.Entry(InstallationStore.COMPAT_OVERLAY_HANDLER_ID,
                    TvCapabilities.CODEC_TRACK_OVERLAY, new CloudPackageVideoInstallationHandler(
                            VideoInstallationHandlers.manifested(),TvCapabilities.CODEC_TRACK_OVERLAY,InstallationStore.COMPAT_OVERLAY_HANDLER_ID)),
            new InstallationHandlerRegistry.Entry(InstallationStore.COMPAT_TRACK_HANDLER_ID,
                    TvCapabilities.CODEC_TRACK, new CloudPackageVideoInstallationHandler(
                            VideoInstallationHandlers.legacy(),TvCapabilities.CODEC_TRACK,InstallationStore.COMPAT_TRACK_HANDLER_ID)),
            new InstallationHandlerRegistry.Entry(BannerInstallationHandler.HANDLER_ID,
                    TvCapabilities.CODEC_BANNER_WALL_OVERLAY, BANNER));

    /** Return the already-created Banner handler for current composition callers. */
    static BannerInstallationHandler banner() { return BANNER; }

    /** Return immutable deterministic metadata for the common Video+Banner installer composition. */
    static InstallationHandlerRegistry registry() { return REGISTRY; }

    /** Static composition has no mutable instance or discovery path. */
    private OverlayInstallationHandlers() {}
}
