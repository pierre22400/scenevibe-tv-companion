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

/**
 * Isolated legacy text handler: one runtime artifact and the qualified TrackParser rules.
 * Pause requirements reflect the existing display countdown, with no new clock. Preparation
 * has no persistence or live effect; this path has no graphical parser or bridge dependency.
 */
final class VideoLegacyInstallationHandler implements InstallationHandler {
    /** Construct a stateless build-local handler without any Android or transport owner. */
    VideoLegacyInstallationHandler() {}

    /** Return a closed semantic/capability result and suppress all parser content on failure. */
    @Override public InstallationStatus validate(InstallRequest request,TvCapabilities capabilities) {
        try {prepare(request,capabilities);return InstallationStatus.VALIDATED;}
        catch (VideoRuntimePreparation.Invalid invalid) {return invalid.status;}
        catch (RuntimeException invalid) {return InstallationStatus.INVALID_PACKAGE;}
    }

    /** Prepare one exact bounded text track and derive the actual parsed pause/count profile. */
    @Override public PreparedInstallation prepare(InstallRequest request,TvCapabilities capabilities) {
        Map<String,byte[]> artifacts=VideoRuntimePreparation.artifacts(request,TvCapabilities.CODEC_TRACK,"runtime");
        ScheduledTrack track;
        try {track=parseCompatibility(VideoRuntimePreparation.utf8(artifacts.get("runtime"),400_000));}
        catch (Exception invalid) {throw new VideoRuntimePreparation.Invalid(InstallationStatus.INVALID_PACKAGE);}
        ExecutionRequirements needs=new ExecutionRequirements(TvCapabilities.LEGACY_CONTRACT,ExecutionRequirements.ClockMode.MEDIA,
                track.pauseFreezesDisplay?ExecutionRequirements.PauseBehavior.FREEZE:ExecutionRequirements.PauseBehavior.CONTINUE,
                track.comments.size(),false,false);
        VideoRuntimePreparation.requireCapabilities(request,needs,capabilities);
        VideoPreparedState state=VideoPreparedState.compatibility(track,null).bind(request,InstallationStore.COMPAT_TRACK_HANDLER_ID,needs);
        return new PreparedInstallation(request,InstallationStore.COMPAT_TRACK_HANDLER_ID,Collections.emptyMap(),needs,capabilities,state);
    }

    /** Preserve the original String parser behavior for current legacy install/restore callers. */
    ScheduledTrack parseCompatibility(String json) throws Exception {
        try {return VideoRuntimePreparation.parseRuntime(json);}
        catch (Exception invalid) {throw new VideoRuntimePreparation.Invalid(InstallationStatus.INVALID_PACKAGE);}
    }

    /** Return only the exact immutable candidate owned by this handler and prepared state. */
    @Override public InstallRequest encodeForCache(PreparedInstallation prepared) {
        if (VideoPreparedState.owned(prepared,InstallationStore.COMPAT_TRACK_HANDLER_ID,TvCapabilities.CODEC_TRACK,false)==null)
            throw new VideoRuntimePreparation.Invalid(InstallationStatus.INVALID_PACKAGE);
        return prepared.canonical();
    }

    /** Revalidate durable bytes through the exact live preparation rules, without store writes or ARM. */
    @Override public PreparedInstallation restoreFromCache(InstallRequest snapshot,TvCapabilities capabilities) {
        return prepare(snapshot,capabilities);
    }

    /** Synchronously retire manifested ownership before loading/selecting the prepared legacy revision. */
    @Override public InstallationStatus arm(PreparedInstallation prepared,RuntimePorts ports) {
        return VideoPreparedState.arm(prepared,ports,InstallationStore.COMPAT_TRACK_HANDLER_ID,TvCapabilities.CODEC_TRACK,false);
    }
}
