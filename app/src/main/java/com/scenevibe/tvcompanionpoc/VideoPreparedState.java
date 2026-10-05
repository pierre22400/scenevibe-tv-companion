package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.ExecutionRequirements;
import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationHandler;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.PreparedInstallation;

/**
 * Trusted immutable Video parse state, memory-only and never serialized. Canonical request
 * and requirements identities bind generic preparation to the exact parsed state. Historical
 * String evaluation has no generic binding and therefore cannot be armed as a generic package.
 * No scheduler, renderer, Context, service, credentials, network client or store is retained.
 */
final class VideoPreparedState implements InstallationHandler.PreparedState {
    final ScheduledTrack track;
    final OverlayManifest manifest;
    private final InstallRequest canonical;
    private final String handlerId;
    private final ExecutionRequirements requirements;

    /** Retain only qualified immutable parser values and optional immutable generic binding. */
    private VideoPreparedState(ScheduledTrack track,OverlayManifest manifest,InstallRequest canonical,
            String handlerId,ExecutionRequirements requirements) {
        if (track==null) throw new IllegalArgumentException("Missing prepared Video state");
        for (ScheduledTrack.Event event:track.comments)
            if (event.mediaBitmap!=null) throw new IllegalArgumentException("Invalid prepared Video state");
        this.track=track;this.manifest=manifest;this.canonical=canonical;
        this.handlerId=handlerId;this.requirements=requirements;
    }

    /** Return a typed historical parse result without advertising generic capability acceptance. */
    static VideoPreparedState compatibility(ScheduledTrack track,OverlayManifest manifest) {
        return new VideoPreparedState(track,manifest,null,null,null);
    }

    /** Bind already validated parser values to their exact immutable generic candidate/profile. */
    VideoPreparedState bind(InstallRequest request,String id,ExecutionRequirements needs) {
        return new VideoPreparedState(track,manifest,request,id,needs);
    }

    /** Check ownership and exact canonical/profile identity without reinterpreting untrusted bytes. */
    static VideoPreparedState owned(PreparedInstallation prepared,String id,String codec,boolean manifested) {
        if (prepared==null||!id.equals(prepared.handlerId())||!codec.equals(prepared.codecId())
                ||!(prepared.preparedState() instanceof VideoPreparedState)) return null;
        VideoPreparedState state=(VideoPreparedState)prepared.preparedState();
        return state.canonical==prepared.canonical()&&state.requirements==prepared.requirements()
                &&id.equals(state.handlerId)&&(state.manifest!=null)==manifested?state:null;
    }

    /** Activate prepared values only through eligible Video ports; any partial failure is unarmed. */
    static InstallationStatus arm(PreparedInstallation prepared,InstallationHandler.RuntimePorts ports,
            String id,String codec,boolean manifested) {
        VideoPreparedState state=owned(prepared,id,codec,manifested);
        if (state==null||!(ports instanceof VideoInstallationRuntimePorts)) return InstallationStatus.ARM_FAILED;
        VideoInstallationRuntimePorts runtime=(VideoInstallationRuntimePorts)ports;
        boolean owner=false;
        try {
            owner=runtime.isOwnerThread();
            if (!owner) return InstallationStatus.ARM_FAILED;
            boolean retired=manifested
                    ?runtime.retireLegacyVisualOwner()&&runtime.retireManifestedVisualOwner()
                    :runtime.retireManifestedVisualOwner()&&runtime.retireLegacyVisualOwner();
            if (!retired||!runtime.loadPreparedTrack(state.track)
                    ||(manifested&&!runtime.armPreparedManifest(prepared.revision(),state.manifest))
                    ||!runtime.selectActiveRevision(prepared.revision(),manifested)) {
                abort(runtime);return InstallationStatus.ARM_FAILED;
            }
            return InstallationStatus.ARMED;
        } catch (RuntimeException failed) {
            if (owner) abort(runtime);
            return InstallationStatus.ARM_FAILED;
        }
    }

    /** Failure cleanup cannot throw a content-bearing exception through the handler contract. */
    private static void abort(VideoInstallationRuntimePorts runtime) {
        try {runtime.abortActivation();} catch (RuntimeException failedCleanup) { /* Closed ARM_FAILED only. */ }
    }
}
