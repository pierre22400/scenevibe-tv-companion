package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.wall.WallCalendarScheduler;

/**
 * The single principal common owner's installer-facing port surface. It is the ONE
 * {@link com.scenevibe.tvcompanionpoc.installation.InstallationHandler.RuntimePorts} the
 * {@link com.scenevibe.tvcompanionpoc.installation.PackageInstaller} hands to whichever handler it
 * resolves, and it routes each handler's ARM to the matching real activation core: the existing
 * {@link OverlayService.LiveVideoRuntimePorts} for Video and the {@link LiveBannerRuntimePorts} for
 * Banner (section 13). It creates NO second owner authority: it only dispatches to the two STATIC
 * ports both bound by the same OverlayService on the same main-thread owner, and never instantiates
 * {@code MediaSyncedTrackScheduler} or any competing scheduler.
 *
 * <p>Alternating-kind safety (section 7): each handler's ARM sequence begins with its own
 * kind-specific retirement ({@link VideoInstallationRuntimePorts#retireLegacyVisualOwner} for Video,
 * {@link BannerInstallationRuntimePorts#retireVideoVisualOwner} for Banner), which this composite
 * uses to latch the arming kind. The methods whose signatures are shared by both interfaces
 * ({@code isOwnerThread}, {@code retireManifestedVisualOwner}, {@code armPreparedManifest},
 * {@code abortActivation}) then route to the latched owner, so a Video ARM never touches the Banner
 * core and vice versa. {@code abortActivation} clears both cores to guarantee no partial activation
 * survives a cross-kind swap, then resets the latch.</p>
 */
final class OverlayRuntimePorts
        implements VideoInstallationRuntimePorts, BannerInstallationRuntimePorts {

    /** Which activation core the current ARM sequence is driving; reset after an abort. */
    private enum Kind { NONE, VIDEO, BANNER }

    private final OverlayService.LiveVideoRuntimePorts video;
    private final LiveBannerRuntimePorts banner;
    private Kind arming = Kind.NONE;

    /** Bind the two already-constructed STATIC activation cores; construction loads/renders nothing. */
    OverlayRuntimePorts(OverlayService.LiveVideoRuntimePorts video, LiveBannerRuntimePorts banner) {
        if (video == null || banner == null) throw new IllegalArgumentException("Missing owner core");
        this.video = video;
        this.banner = banner;
    }

    /** The two cores share the one owner-thread predicate; either answers identically. */
    @Override public boolean isOwnerThread() { return video.isOwnerThread(); }

    // ---- Video-only entry points: latch VIDEO and delegate ------------------------------

    @Override public boolean retireLegacyVisualOwner() {
        arming = Kind.VIDEO;
        return video.retireLegacyVisualOwner();
    }

    @Override public boolean loadPreparedVideo(VideoPreparedState state) {
        arming = Kind.VIDEO;
        return video.loadPreparedVideo(state);
    }

    @Override public boolean selectActiveRevision(long revision, boolean manifested) {
        return video.selectActiveRevision(revision, manifested);
    }

    // ---- Banner-only entry points: latch BANNER and delegate ----------------------------

    @Override public boolean retireVideoVisualOwner() {
        arming = Kind.BANNER;
        return banner.retireVideoVisualOwner();
    }

    @Override public boolean loadPreparedBanner(BannerPreparedState state) {
        arming = Kind.BANNER;
        return banner.loadPreparedBanner(state);
    }

    @Override public boolean selectActiveBanner(long revision) {
        return banner.selectActiveBanner(revision);
    }

    @Override public void onWallResult(String token, WallCalendarScheduler.Result result) {
        banner.onWallResult(token, result);
    }

    // ---- shared-signature entry points: route to the latched owner ----------------------

    @Override public boolean retireManifestedVisualOwner() {
        return arming == Kind.BANNER
                ? banner.retireManifestedVisualOwner()
                : video.retireManifestedVisualOwner();
    }

    @Override public boolean armPreparedManifest(long revision, OverlayManifest manifest) {
        return arming == Kind.BANNER
                ? banner.armPreparedManifest(revision, manifest)
                : video.armPreparedManifest(revision, manifest);
    }

    /** Idempotently clear BOTH cores so no partial cross-kind activation survives, then reset. */
    @Override public void abortActivation() {
        try { banner.abortActivation(); }
        finally {
            try { video.abortActivation(); }
            finally { arming = Kind.NONE; }
        }
    }
}
