package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.audio.LocalAudioPort;
import com.scenevibe.tvcompanionpoc.audio.SceneVibeAudioRenderer;
import com.scenevibe.tvcompanionpoc.calendar.MediaCalendarScheduler;
import com.scenevibe.tvcompanionpoc.calendar.MediaObservation;

/**
 * Experimental composition of ONE M5 calendar, the existing scene regie and the
 * existing AUDIO renderer. Software admission is explicit and never a native
 * MEDIA permission. No timer, Cloud access, protected capture or Prime command
 * exists here. The supplied SceneSink can delegate to the existing SceneRenderer.
 */
final class StudioMultimediaRuntime {
    private final SceneRuntimeController text;
    private final SceneVibeAudioRenderer audio;
    private final MediaCalendarScheduler scheduler;
    private StudioMultimediaPackage track;
    private StudioMultimediaPackage preparedCloudInput;
    private String token;
    private long generation;
    private boolean textEnabled=true;
    private boolean eligible;
    private final java.util.Map<String,String> revisionDigests=new java.util.HashMap<>();
    private final java.util.Set<String> usedActivations=new java.util.HashSet<>();
    private final boolean confirmedReplay;
    private final java.util.Map<Long,String> nativeAttempts=new java.util.HashMap<>();
    private String audioStopReason="NATIVE_PLAYER";

    /** Retain renderer ports without preparing or starting any local player. */
    StudioMultimediaRuntime(SceneRuntimeController.SceneSink sink,LocalAudioPort port) {
        this(sink,port,false);
    }

    /** Select v23's bounded TEXT recovery while retaining historical calendar semantics by default. */
    StudioMultimediaRuntime(SceneRuntimeController.SceneSink sink,LocalAudioPort port,boolean recoverTextRewind) {
        this(sink,port,recoverTextRewind,false,null);
    }

    /** Reuse the same calendar/renderers with v24 replay proof and an optional diagnostic-only audio observer. */
    StudioMultimediaRuntime(SceneRuntimeController.SceneSink sink,LocalAudioPort port,boolean recoverTextRewind,
            boolean confirmedReplay,java.util.function.Consumer<MediaCalendarScheduler.AudioDiagnostics> journal) {
        this.confirmedReplay=confirmedReplay;
        text=new SceneRuntimeController(sink); audio=new SceneVibeAudioRenderer(port);
        scheduler=new MediaCalendarScheduler(new MediaCalendarScheduler.Sink() {
            /** Apply only the active calendar's eligibility. */
            @Override public void onEligibility(String captured,boolean valid) {
                if(!owns(captured)) return; eligible=valid;
                text.onEligibility(valid && textEnabled); audio.onEligibility(captured,valid);
            }
            /** Preserve the selected policy for both renderers. */
            @Override public void onPlayback(String captured,boolean playing,boolean freeze) {
                if(!owns(captured)) return;
                text.onPlayback(playing,freeze);String previous=audioStopReason;audioStopReason=playing?previous:"MEDIA_PAUSED";
                try{audio.onPlayback(captured,playing,freeze);}finally{audioStopReason=previous;}
            }
            /** Dispatch typed events; AUDIO is never disguised as TEXT. */
            @Override public void onDue(String captured,String id) {
                if(!owns(captured)) return;
                if(track.audio.containsKey(id)) {
                    var outcome=audio.onDueResult(captured,id);
                    if(confirmedReplay && outcome!=SceneVibeAudioRenderer.DueResult.REQUESTED)
                        scheduler.recordAudio(id,outcome==SceneVibeAudioRenderer.DueResult.ALREADY_ACTIVE?"AUDIO_IGNORED":"AUDIO_REFUSED",outcome.name());
                }
                else if(textEnabled) text.onEventDue(id,generation);
            }
            /** Expire only the original activation's rendering window. */
            @Override public void onExpire(String captured,String id) {
                if(!owns(captured)) return;
                if(track.audio.containsKey(id)){String previous=audioStopReason;audioStopReason="CALENDAR_WINDOW_CLOSED";
                    try{audio.onExpire(captured,id);}finally{audioStopReason=previous;}}
                else text.onEventExpired(id,generation);
            }
            /** The single scheduler owns all offsets, including seek reconciliation. */
            @Override public void onAudioPosition(String captured,String id,long offset,boolean discontinuity) {
                if(owns(captured)) audio.onAudioPosition(captured,id,offset,discontinuity);
            }
            /** A restoration request keeps the event consumed; only the current TEXT window may draw again. */
            @Override public void onTextRestore(String captured,String id){if(owns(captured) && textEnabled)text.onEventDue(id,generation);}
            /** Publish bounded facts without authorizing a player or mutating a timeline. */
            @Override public void onAudioTransition(String captured,MediaCalendarScheduler.AudioDiagnostics event){if(journal!=null)journal.accept(event);}
        },recoverTextRewind,confirmedReplay);
    }

    /** Offer a verified cache to the existing calendar input without arming, observing or drawing it. */
    void prepareFromVerifiedCloudCache(MultimediaPrivateCache.Prepared prepared) {
        if(prepared==null)throw new IllegalArgumentException("VERIFIED_CACHE_REQUIRED");
        preparedCloudInput=prepared.track;
    }

    /** Expose the inert shared calendar for the separate, still-unconnected native admission owner. */
    com.scenevibe.tvcompanionpoc.calendar.MediaCalendar preparedCloudCalendar() {
        return preparedCloudInput==null?null:preparedCloudInput.calendar;
    }

    /** Explicit JVM/software admission only; this entry is never wired to Sony. */
    void loadForSoftwareQualification(StudioMultimediaPackage next,String activation) {
        load(next,activation);
    }

    /** Native service entry requires a current, explicit cache/edition admission rather than a software label. */
    void loadAdmittedCloud(CloudMultimediaAdmission admission) {
        if(admission==null || !admission.current())throw new IllegalStateException("CURRENT_NATIVE_ADMISSION_REQUIRED");
        load(admission.reference.prepared.track,admission.activation);
    }

    /** Share the already-qualified scheduler/renderers after the caller's distinct admission boundary. */
    private void load(StudioMultimediaPackage next,String activation) {
        if(next==null || activation==null || activation.isEmpty()) throw new IllegalArgumentException("ACTIVATION_REQUIRED");
        String revisionKey=next.trackId+"#"+next.revision;
        String previous=revisionDigests.get(revisionKey);
        if(previous!=null && !previous.equals(next.trackSha256)) throw new IllegalArgumentException("REVISION_CONTENT_CONFLICT");
        if(!usedActivations.add(activation)) throw new IllegalArgumentException("ACTIVATION_TOKEN_REUSED");
        close();
        track=next; token=activation;
        try {
            text.loadManifest(next.revision,next.originalManifest); generation=text.currentGeneration();
            audio.setCatalog(token,next.audio.values()); scheduler.load(next.calendar,token,next.audio.keySet());
            revisionDigests.put(revisionKey,next.trackSha256);
        } catch(RuntimeException refused) { close();throw refused; }
    }

    /** Refuse a package whose edition/mapping were never independently established. */
    void requireNativeIdentityEvidence() {
        if(track==null || !track.hasReferencedIdentity()) throw new IllegalStateException("MEDIA_EDITION_OR_MAPPING_UNPROVEN");
        throw new IllegalStateException("EXTERNAL_TRUSTED_IDENTITY_REGISTRY_NOT_CONNECTED");
    }

    /** Consume a selected observation from the matching activation without estimating time. */
    void observe(String activation,MediaObservation observation) {
        if(!owns(activation)) return;
        if(observation==null || !observation.eligible() || observation.positionMs()<0) scheduler.onUnavailable();
        else scheduler.onObservation(observation);
    }

    /** Feed actual capture/session evidence to the same scheduler; no position is extrapolated here. */
    void observe(String activation,MediaObservation observation,Object session,long capturedMs,float speed){
        if(!owns(activation))return;
        if(observation==null || !observation.eligible() || observation.positionMs()<0)scheduler.onUnavailable();
        else scheduler.onObservation(observation,session,capturedMs,speed);
    }

    /** TEXT OFF hides only the existing visual regie; future due events keep their calendar. */
    void setTextEnabled(boolean enabled) { boolean restore=enabled && !textEnabled;textEnabled=enabled; text.onEligibility(eligible && enabled);
        if(restore)scheduler.requestTextRestore();}

    /** AUDIO OFF stops immediately and leaves TEXT and the shared calendar untouched. */
    void setAudioEnabled(boolean enabled) { audio.setAudioEnabled(enabled); }

    /** Apply gain exclusively through the local SceneVibe audio port. */
    void setGain(float gain) { audio.setGain(gain); }

    /** Stop local effects during AD-SYNC without consuming or rearming calendar events. */
    void suspendComments() { scheduler.onInterruption(); }

    /** Explain why effects close while retaining the exact admission, consumption and native stop policy. */
    void suspendComments(String reason){String previous=audioStopReason;audioStopReason=reason;
        try{scheduler.onInterruption();}finally{audioStopReason=previous;}}

    /** Switching synchronization policy preserves the calendar and is never inferred to be a SEEK. */
    void policyChanged(){scheduler.onPolicyChanged();}

    /** Correlate native acknowledgements with the renderer's event at PREPARE, including repeated playback attempts. */
    void nativeAudio(long attempt,String stage){
        if(!confirmedReplay)return;
        if("AUDIO_PREPARE_REQUESTED".equals(stage) && audio.activeEventId()!=null)nativeAttempts.put(attempt,audio.activeEventId());
        String id=nativeAttempts.get(attempt);if(id==null)return;
        scheduler.recordAudio(id,stage,audioStopReason);
        if(java.util.Arrays.asList("AUDIO_COMPLETED","AUDIO_STOPPED","AUDIO_ERROR","AUDIO_START_REFUSED").contains(stage))nativeAttempts.remove(attempt);
    }

    /** Return one known event's traversal/native outcome, with no resource identity in the public projection. */
    MediaCalendarScheduler.AudioDiagnostics audioDiagnostics(String id){return scheduler.audioDiagnostics(id);}

    /** An explicit local owner decision preserves the historical replay policy after an actual seek. */
    void confirmSeek() { scheduler.onConfirmedSeek(); }

    /** Lose session/identity without retaining an audible local comment. */
    void unavailable() { scheduler.onUnavailable(); }

    /** Project the actual shared calendar, never an independent timeline or regenerated events. */
    MediaCalendarScheduler.Diagnostics diagnostics(){return scheduler.diagnostics();}

    /** Invalidate the activation before releasing both existing renderers. */
    void close() {
        scheduler.clear(); audio.onError(); text.unload();
        token=null; track=null; eligible=false;
        nativeAttempts.clear();
    }

    /** Compare exact opaque activation labels before handling any callback. */
    private boolean owns(String activation) { return token!=null && token.equals(activation); }
}
