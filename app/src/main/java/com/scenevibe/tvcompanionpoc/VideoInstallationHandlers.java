package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationHandlerRegistry;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;

/**
 * Explicit eager Video-side composition of exactly the two qualified codec/handler identities.
 * Generic registry code remains product-neutral; lookup constructs nothing and needs no Context.
 * Current repository access uses named semantic facades, never registry-driven installation.
 */
final class VideoInstallationHandlers {
    private static final VideoManifestInstallationHandler MANIFESTED=new VideoManifestInstallationHandler();
    private static final VideoLegacyInstallationHandler LEGACY=new VideoLegacyInstallationHandler();
    private static final InstallationHandlerRegistry REGISTRY=new InstallationHandlerRegistry(
            new InstallationHandlerRegistry.Entry(InstallationStore.COMPAT_OVERLAY_HANDLER_ID,TvCapabilities.CODEC_TRACK_OVERLAY,MANIFESTED),
            new InstallationHandlerRegistry.Entry(InstallationStore.COMPAT_TRACK_HANDLER_ID,TvCapabilities.CODEC_TRACK,LEGACY));

    /** Return the already-created manifested semantic owner for current compatibility callers. */
    static VideoManifestInstallationHandler manifested() {return MANIFESTED;}
    /** Return the already-created legacy semantic owner for current compatibility callers. */
    static VideoLegacyInstallationHandler legacy() {return LEGACY;}
    /** Return immutable deterministic metadata intended for later generic installer composition only. */
    static InstallationHandlerRegistry registry() {return REGISTRY;}
    /** Static composition has no mutable instance or discovery path. */
    private VideoInstallationHandlers() {}
}
