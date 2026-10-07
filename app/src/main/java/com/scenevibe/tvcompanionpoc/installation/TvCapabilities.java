package com.scenevibe.tvcompanionpoc.installation;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Canonical immutable description of the existing executable compatibility paths, not of
 * everything a parser accepts. The generic registry is still unwired in Phase B. Media
 * freeze is supported for native overlays; continuing the legacy display countdown is a
 * separate capability and does not advertise native wall-clock scene execution.
 */
public final class TvCapabilities {
    public static final String CODEC_TRACK_OVERLAY="scenevibe.runtime-track-overlay.v1";
    public static final String CODEC_TRACK="scenevibe.runtime-track.v1";
    /**
     * Reserved local TV codec for the Banner WALL overlay installation shape (section 8). It is a
     * recognized inert installation-shape id so the common static registry can hold one Banner
     * handler beside the two Video handlers; it is NOT a Cloud/public capability announcement and
     * flipping {@link #supportsWallClockExecution()} is deliberately deferred to the assembled,
     * test-covered local path, so this constant alone advertises no live WALL execution.
     */
    public static final String CODEC_BANNER_WALL_OVERLAY="scenevibe.banner-wall-overlay.v1";
    public static final String OVERLAY_CONTRACT="scenevibe.overlay-manifest.v1";
    public static final String LEGACY_CONTRACT="scenevibe.track.v1";
    private static final TvCapabilities CURRENT=new TvCapabilities();
    private final SortedSet<String> codecs,renderingContracts;
    private final Set<ExecutionRequirements.ClockMode> clocks;
    private final Set<ExecutionRequirements.PauseBehavior> pauses;

    /** Pin the current APK truth with sorted immutable identifiers and closed enums. */
    private TvCapabilities() {
        codecs=Collections.unmodifiableSortedSet(new TreeSet<>(Arrays.asList(CODEC_TRACK_OVERLAY,CODEC_TRACK,CODEC_BANNER_WALL_OVERLAY)));
        renderingContracts=Collections.unmodifiableSortedSet(new TreeSet<>(Arrays.asList(OVERLAY_CONTRACT,LEGACY_CONTRACT)));
        // MEDIA remains the only clock the existing Video paths execute. WALL is additionally
        // recognized here ONLY as the Banner installation shape the common static registry may
        // hold; supportsWallClockExecution() stays false and the per-codec supports() gate below
        // confines WALL to the Banner codec, so no Video path ever accepts a WALL requirement.
        clocks=Collections.unmodifiableSet(EnumSet.of(ExecutionRequirements.ClockMode.MEDIA,ExecutionRequirements.ClockMode.WALL));
        pauses=Collections.unmodifiableSet(EnumSet.allOf(ExecutionRequirements.PauseBehavior.class));
    }
    /** Return the same deterministic build-local descriptor without consulting device or network state. */
    public static TvCapabilities current() {return CURRENT;}
    /** Return recognized inert installation-shape ids; this is not a generic-handler availability claim. */
    public SortedSet<String> supportedCodecs() {return codecs;}
    /** Return rendering contracts implemented by existing compatibility paths. */
    public SortedSet<String> supportedRenderingContracts() {return renderingContracts;}
    /** Advertise MEDIA only, even though a manifest parser accepts WALL data. */
    public Set<ExecutionRequirements.ClockMode> supportedClocks() {return clocks;}
    /** Return the union of pause behaviors; supports() checks their contract-specific combinations. */
    public Set<ExecutionRequirements.PauseBehavior> supportedPauseBehaviors() {return pauses;}
    /** No executable wall-clock handler exists in the qualified APK. */
    public boolean supportsWallClockExecution() {return false;}
    /** Renderer-local references never imply an asset downloader. */
    public boolean supportsRemoteAssetAcquisition() {return false;}
    /** The deferred asset cache is not advertised or constructed. */
    public boolean supportsSharedAssetCache() {return false;}
    /** Preserve the current transport ceiling as a necessary, not sufficient, package bound. */
    public int maximumPackageBytes() {return InstallationBounds.MAX_PACKAGE_BYTES;}
    /** Cover the full UTF-8 encoding of the largest qualified UTF-16 artifact. */
    public int maximumArtifactBytes() {return InstallationBounds.MAX_ARTIFACT_BYTES;}
    /** A qualified installation holds at most its two current artifacts. */
    public int maximumArtifactCount() {return InstallationBounds.MAX_ARTIFACTS;}
    /** Retain the compatibility parser's exact character bound for each known contract. */
    public int maximumArtifactUtf16Units(String contract) {
        if (LEGACY_CONTRACT.equals(contract)) return 400_000;
        if (OVERLAY_CONTRACT.equals(contract)) return 800_000;
        return 0;
    }
    /** Pin the already-enforced manifest scene ceiling. */
    public int maximumScenes() {return 256;}
    /** Pin the recursive primitive count PER SCENE, not merely top-level elements. */
    public int maximumPrimitivesPerScene() {return 256;}
    /** Express the derived manifest-wide ceiling without claiming a new parser rule. */
    public int maximumPrimitivesPerManifest() {return maximumScenes()*maximumPrimitivesPerScene();}
    /** Pin the parser's maximum nested group depth. */
    public int maximumGroupDepth() {return 4;}
    /** Pin the qualified scene canvas width. */
    public int canvasWidth() {return 1920;}
    /** Pin the qualified scene canvas height. */
    public int canvasHeight() {return 1080;}
    /** Pin the parser's Unicode code-point limit for one text primitive. */
    public int maximumTextCodePoints() {return 2000;}

    /** Check executable combinations; native media scenes never pretend to continue by wall time. */
    public boolean supports(String codec,ExecutionRequirements requirements) {
        if (codec==null||requirements==null||!codecs.contains(codec)||!clocks.contains(requirements.clock())
                ||requirements.requiresRemoteAssetAcquisition()||requirements.requiresSharedAssetCache()) return false;
        if (CODEC_TRACK_OVERLAY.equals(codec))
            return OVERLAY_CONTRACT.equals(requirements.renderingContract())
                    &&requirements.clock()==ExecutionRequirements.ClockMode.MEDIA
                    &&requirements.pauseBehavior()==ExecutionRequirements.PauseBehavior.FREEZE;
        if (CODEC_BANNER_WALL_OVERLAY.equals(codec))
            // The Banner shape renders through the overlay contract on the WALL clock with a
            // continuing pause behavior; no Video codec accepts WALL and this codec accepts no MEDIA.
            return OVERLAY_CONTRACT.equals(requirements.renderingContract())
                    &&requirements.clock()==ExecutionRequirements.ClockMode.WALL
                    &&requirements.pauseBehavior()==ExecutionRequirements.PauseBehavior.CONTINUE;
        return CODEC_TRACK.equals(codec)
                &&requirements.clock()==ExecutionRequirements.ClockMode.MEDIA
                &&LEGACY_CONTRACT.equals(requirements.renderingContract())&&pauses.contains(requirements.pauseBehavior());
    }

    /** Pure shape/capability check; semantic parsing remains the future static handler's responsibility. */
    public InstallationStatus validate(InstallRequest request,ExecutionRequirements requirements) {
        if (request==null||requirements==null) return InstallationStatus.INVALID_PACKAGE;
        if (!supports(request.codecId(),requirements)) return InstallationStatus.UNSUPPORTED_CAPABILITY;
        // The manifested Video codec carries two artifacts (runtime + manifest); the legacy Video
        // codec and the single-body Banner codec each carry exactly one.
        int expectedArtifacts=CODEC_TRACK_OVERLAY.equals(request.codecId())?2:1;
        return request.artifactCount()==expectedArtifacts?InstallationStatus.VALIDATED:InstallationStatus.INVALID_PACKAGE;
    }
}
