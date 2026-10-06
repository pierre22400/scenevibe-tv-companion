package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.calendar.MediaCalendar;
import com.scenevibe.tvcompanionpoc.calendar.SceneEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
    final MediaCalendar calendar;
    final Map<String,ScheduledTrack.Event> eventsById;
    private final InstallRequest canonical;
    private final String handlerId;
    private final ExecutionRequirements requirements;

    /** Retain qualified parser values and build their memory-only temporal projection once. */
    private VideoPreparedState(ScheduledTrack track,OverlayManifest manifest,boolean textOnly) {
        if (track==null) throw new IllegalArgumentException("Missing prepared Video state");
        ArrayList<SceneEvent> events=new ArrayList<>();
        Map<String,ScheduledTrack.Event> index=new HashMap<>();
        for (ScheduledTrack.Event event:track.comments) {
            if (textOnly&&event.mediaBitmap!=null) throw new IllegalArgumentException("Invalid prepared Video state");
            events.add(new SceneEvent(event.id,event.startMs,event.durationMs));
            index.put(event.id,event);
        }
        this.track=track;this.manifest=manifest;
        this.calendar=new MediaCalendar(events,track.pauseFreezesDisplay);
        this.eventsById=Collections.unmodifiableMap(index);
        this.canonical=null;this.handlerId=null;this.requirements=null;
    }

    /** Bind identities while retaining the exact calendar/index already constructed at preparation. */
    private VideoPreparedState(VideoPreparedState state,InstallRequest request,String id,
            ExecutionRequirements needs) {
        for (ScheduledTrack.Event event:state.track.comments)
            if (event.mediaBitmap!=null) throw new IllegalArgumentException("Invalid prepared Video state");
        this.track=state.track;this.manifest=state.manifest;this.calendar=state.calendar;
        this.eventsById=state.eventsById;this.canonical=request;this.handlerId=id;this.requirements=needs;
    }

    /** Return a typed historical parse result without advertising generic capability acceptance. */
    static VideoPreparedState compatibility(ScheduledTrack track,OverlayManifest manifest) {
        return new VideoPreparedState(track,manifest,true);
    }

    /** Project the existing LAN parser's optional bitmap payload outside the generic text-only ingress. */
    static VideoPreparedState lan(ScheduledTrack track) {
        return new VideoPreparedState(track,null,false);
    }

    /** Bind already validated parser values to their exact immutable generic candidate/profile. */
    VideoPreparedState bind(InstallRequest request,String id,ExecutionRequirements needs) {
        return new VideoPreparedState(this,request,id,needs);
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
            if (!retired||!runtime.loadPreparedVideo(state)
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
