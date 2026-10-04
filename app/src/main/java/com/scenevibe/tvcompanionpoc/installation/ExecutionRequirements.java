package com.scenevibe.tvcompanionpoc.installation;

/**
 * Immutable producer-neutral execution needs, supplied by a future validated handler.
 * Unsupported needs can be represented so the local descriptor can reject them; this
 * object creates no clock, asset cache or downloader and claims no parsed artifact.
 */
public final class ExecutionRequirements {
    /** Clock vocabulary is distinct from the set executable by this APK. */
    public enum ClockMode { MEDIA, WALL }
    /** Pause vocabulary can be restricted independently for each executable contract. */
    public enum PauseBehavior { FREEZE, CONTINUE }

    private final String renderingContract;
    private final ClockMode clock;
    private final PauseBehavior pauseBehavior;
    private final int timedSceneCount;
    private final boolean remoteAssetAcquisition,sharedAssetCache;

    /** Construct bounded requirements only; compatibility handlers own artifact derivation later. */
    public ExecutionRequirements(String renderingContract,ClockMode clock,PauseBehavior pauseBehavior,
            int timedSceneCount,boolean remoteAssetAcquisition,boolean sharedAssetCache) {
        this.renderingContract=InstallationBounds.id(renderingContract);
        if (clock==null||pauseBehavior==null||timedSceneCount<1||timedSceneCount>256)
            throw new IllegalArgumentException("Invalid execution requirements");
        this.clock=clock;this.pauseBehavior=pauseBehavior;this.timedSceneCount=timedSceneCount;
        this.remoteAssetAcquisition=remoteAssetAcquisition;this.sharedAssetCache=sharedAssetCache;
    }
    /** Return the bounded rendering-contract identifier, never payload content. */
    public String renderingContract() {return renderingContract;}
    /** Return requested clock semantics without starting a clock. */
    public ClockMode clock() {return clock;}
    /** Return requested pause semantics without mutating playback. */
    public PauseBehavior pauseBehavior() {return pauseBehavior;}
    /** Return the declared bounded number of timed values; handlers must validate its derivation. */
    public int timedSceneCount() {return timedSceneCount;}
    /** Report whether the package requires acquisition which this APK cannot provide. */
    public boolean requiresRemoteAssetAcquisition() {return remoteAssetAcquisition;}
    /** Report whether the package requires the deferred shared asset-cache capability. */
    public boolean requiresSharedAssetCache() {return sharedAssetCache;}
}
