package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.ExecutionRequirements;
import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationHandler;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.PreparedInstallation;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.util.Collections;
import java.util.Map;
import org.json.JSONObject;

/**
 * Own manifested Video parsing and the single cross-contract bridge evaluation. Generic
 * preparation enforces executable MEDIA/FREEZE capabilities; the narrow historical String
 * seam preserves parser-valid MEDIA/CONTINUE acceptance until a separately authorized cutover.
 * Validate/prepare/encode/restore have no persistence, runtime, rendering or ACK side effects.
 */
final class VideoManifestInstallationHandler implements InstallationHandler {
    /** Fixed Video-specific failure exposes only the existing bounded diagnostic code. */
    static final class Invalid extends VideoRuntimePreparation.Invalid {
        final RuntimeDiagnostics.ManifestCode code;
        /** Carry neither parser cause nor input content into the repository's existing result. */
        Invalid(RuntimeDiagnostics.ManifestCode code) {super(InstallationStatus.INVALID_PACKAGE);this.code=code;}
    }

    /** Construct a stateless build-local handler without any platform or live-runtime dependency. */
    VideoManifestInstallationHandler() {}

    /** Return a closed semantic/capability result with no mutation or raw exception exposure. */
    @Override public InstallationStatus validate(InstallRequest request,TvCapabilities capabilities) {
        try {prepare(request,capabilities);return InstallationStatus.VALIDATED;}
        catch (VideoRuntimePreparation.Invalid invalid) {return invalid.status;}
        catch (RuntimeException invalid) {return InstallationStatus.INVALID_PACKAGE;}
    }

    /** Parse once for preparation, check the executable profile, then bind exact canonical bytes. */
    @Override public PreparedInstallation prepare(InstallRequest request,TvCapabilities capabilities) {
        Map<String,byte[]> artifacts=VideoRuntimePreparation.artifacts(request,TvCapabilities.CODEC_TRACK_OVERLAY,"runtime","manifest");
        VideoPreparedState state=parse(VideoRuntimePreparation.utf8(artifacts.get("runtime"),400_000),
                VideoRuntimePreparation.utf8(artifacts.get("manifest"),800_000));
        ExecutionRequirements needs=new ExecutionRequirements(TvCapabilities.OVERLAY_CONTRACT,
                "media".equals(state.manifest.clockMode)?ExecutionRequirements.ClockMode.MEDIA:ExecutionRequirements.ClockMode.WALL,
                "freeze".equals(state.manifest.pauseBehavior)?ExecutionRequirements.PauseBehavior.FREEZE:ExecutionRequirements.PauseBehavior.CONTINUE,
                state.manifest.scenes.size(),false,false);
        VideoRuntimePreparation.requireCapabilities(request,needs,capabilities);
        requireCoherence(state);
        return new PreparedInstallation(request,InstallationStore.COMPAT_OVERLAY_HANDLER_ID,
                Collections.emptyMap(),needs,capabilities,state.bind(request,InstallationStore.COMPAT_OVERLAY_HANDLER_ID,needs));
    }

    /** Preserve current String-call acceptance and diagnostics without applying the future capability gate. */
    VideoPreparedState prepareCompatibility(String runtimeJson,String manifestJson) {
        VideoPreparedState state=parse(runtimeJson,manifestJson);requireCoherence(state);return state;
    }

    /** Return only the exact immutable candidate belonging to this handler and trusted prepared state. */
    @Override public InstallRequest encodeForCache(PreparedInstallation prepared) {
        if (VideoPreparedState.owned(prepared,InstallationStore.COMPAT_OVERLAY_HANDLER_ID,TvCapabilities.CODEC_TRACK_OVERLAY,true)==null)
            throw new VideoRuntimePreparation.Invalid(InstallationStatus.INVALID_PACKAGE);
        return prepared.canonical();
    }

    /** Durable bytes remain untrusted: run the same live semantic/profile preparation without ARM. */
    @Override public PreparedInstallation restoreFromCache(InstallRequest snapshot,TvCapabilities capabilities) {
        return prepare(snapshot,capabilities);
    }

    /** Retire opposite ownership and arm already parsed values through the Video-only owner ports. */
    @Override public InstallationStatus arm(PreparedInstallation prepared,RuntimePorts ports) {
        return VideoPreparedState.arm(prepared,ports,InstallationStore.COMPAT_OVERLAY_HANDLER_ID,TvCapabilities.CODEC_TRACK_OVERLAY,true);
    }

    /** Apply the qualified parsers, discarding all parser causes/content on rejection. */
    private static VideoPreparedState parse(String runtimeJson,String manifestJson) {
        try {return VideoPreparedState.compatibility(VideoRuntimePreparation.parseRuntime(runtimeJson),
                OverlayManifestParser.parse(new JSONObject(manifestJson)));}
        catch (Exception invalid) {throw new Invalid(RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID);}
    }

    /** Sole production bridge call; both generic and historical semantic paths use this exact decision. */
    private static void requireCoherence(VideoPreparedState state) {
        VideoOverlayManifestBridge.Result cross=VideoOverlayManifestBridge.validate(state.track,state.manifest);
        if (!cross.ok) throw new Invalid(cross.code);
    }
}
