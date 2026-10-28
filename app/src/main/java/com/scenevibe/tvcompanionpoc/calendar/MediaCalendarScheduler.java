package com.scenevibe.tvcompanionpoc.calendar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Passive, serialized MEDIA scheduling of exact IDs and bounded temporal values.
 * Inputs alone advance the state. Callbacks occur at the historical mutation
 * boundaries, without batching, retry, logging or an autonomous clock. Tokens
 * are external opaque activation labels, captured once for each transition.
 */
public final class MediaCalendarScheduler {
    private static final long MAX_LATE_MS = 2000L;
    private static final long FORWARD_SEEK_THRESHOLD_MS = 5000L;
    private static final long BACKWARD_SEEK_THRESHOLD_MS = 2000L;

    /** Receive only temporal effects and the producing activation's captured label. */
    public interface Sink {
        /** Remove or authorize the external visual binding without rendering here. */
        void onEligibility(String token, boolean eligible);
        /** Forward every eligible playback observation, including repeated values. */
        void onPlayback(String token, boolean playing, boolean freezeOnPause);
        /** Identify the first due event after its consumption and optional window mutation. */
        void onDue(String token, String eventId);
        /** Identify an expired event after removing its window. */
        void onExpire(String token, String eventId);
        /** Supply a native AUDIO offset from this same selected media observation. */
        default void onAudioPosition(String token, String eventId, long offsetMs, boolean discontinuity) {}
        /** Restore a still-current TEXT window without consuming a second event. */
        default void onTextRestore(String token,String eventId){onDue(token,eventId);}
        /** Receive diagnostic transitions only; native STARTED is a separate acknowledgement. */
        default void onAudioTransition(String token,AudioDiagnostics audio){}
    }

    private final Sink sink;
    private final Set<String> consumed = new HashSet<>();
    private final Map<String, SceneEvent> windowed = new HashMap<>();
    private MediaCalendar calendar;
    private String activationToken;
    private long lastPositionMs = -1L;
    private boolean mediaEligible;
    private boolean interrupted;
    private final boolean recoverInterruptedTextRewind;
    private final boolean confirmedReplay;
    private final MediaRewindProof rewindProof=new MediaRewindProof();
    private boolean restoreTextPending,replayLandingPending,explicitReplayPending;
    private int textRestoreRequests;
    private final Map<String,AudioFacts> audioFacts=new java.util.LinkedHashMap<>();
    private long interruptedPositionMs=-1,rewindCandidateMs=-1;
    private int textDue,audioDue,textRearmed;
    private Set<String> audioIds=Collections.emptySet();

    /** Retain the synchronous sink; no collaborator starts background work. */
    public MediaCalendarScheduler(Sink sink) {
        this(sink,false);
    }

    /** v23 may rearm future TEXT after two lower observations; interrupted AUDIO stays consumed. */
    public MediaCalendarScheduler(Sink sink,boolean recoverInterruptedTextRewind) {
        this(sink,recoverInterruptedTextRewind,false);
    }

    /** v24 confirms observed rewinds and restores TEXT windows; historical constructors retain their policies. */
    public MediaCalendarScheduler(Sink sink,boolean recoverInterruptedTextRewind,boolean confirmedReplay) {
        this.sink=sink;this.recoverInterruptedTextRewind=recoverInterruptedTextRewind;this.confirmedReplay=confirmedReplay;
    }

    /** Emit invalidation for the previous binding before replacing and resetting state. */
    public synchronized void load(MediaCalendar next, String token) {
        load(next,token,Collections.emptySet());
    }

    /** Attach typed routing externally; the qualified immutable temporal values stay unchanged. */
    public synchronized void load(MediaCalendar next,String token,Set<String> audioEventIds) {
        if(audioEventIds==null) throw new IllegalArgumentException("AUDIO_IDS_REQUIRED");
        Set<String> known=new HashSet<>();
        if(next!=null) for(SceneEvent event:next.events()) known.add(event.eventId());
        if(!known.containsAll(audioEventIds)) throw new IllegalArgumentException("AUDIO_EVENT_NOT_IN_CALENDAR");
        this.audioIds=Collections.unmodifiableSet(new HashSet<>(audioEventIds));
        sink.onEligibility(activationToken, false);
        calendar = next;
        activationToken = token;
        consumed.clear();
        windowed.clear();
        lastPositionMs = -1L;
        mediaEligible = false;
        interrupted = false;
        interruptedPositionMs=-1;rewindCandidateMs=-1;textDue=0;audioDue=0;textRearmed=0;
        restoreTextPending=false;replayLandingPending=false;explicitReplayPending=false;textRestoreRequests=0;rewindProof.rebase();audioFacts.clear();
        if(next!=null)for(SceneEvent event:next.events())if(audioIds.contains(event.eventId())){
            audioFacts.put(event.eventId(),new AudioFacts(audioFacts.size()+1,event.startMs()));audioTransition(event.eventId(),"AUDIO_FUTURE","LOADED");
        }
    }

    /** Invalidate the old binding before clearing it, even when already empty. */
    public synchronized void clear() {
        sink.onEligibility(activationToken, false);
        calendar = null;
        audioIds=Collections.emptySet();
        activationToken = null;
        consumed.clear();
        windowed.clear();
        lastPositionMs = -1L;
        mediaEligible = false;
        interrupted = false;
        interruptedPositionMs=-1;rewindCandidateMs=-1;textDue=0;audioDue=0;textRearmed=0;
        restoreTextPending=false;replayLandingPending=false;explicitReplayPending=false;textRestoreRequests=0;rewindProof.rebase();audioFacts.clear();
    }

    /** Lose eligibility without expiring windows or erasing consumed identities. */
    public synchronized void onUnavailable() {
        if (calendar == null) return;
        if(confirmedReplay){onInterruption();return;}
        deactivate(activationToken);
    }

    /**
     * Stop only SceneVibe effects, preserving every consumed event identity and
     * all future timestamps. Unlike session reacquisition, the next observation
     * must not rearm a phrase already consumed before an interruption.
     */
    public synchronized void onInterruption() {
        if(confirmedReplay){rewindProof.interrupted();restoreTextPending=true;}
        if (calendar == null || interrupted) return;
        interruptedPositionMs=lastPositionMs;rewindCandidateMs=-1;
        interrupted = true;
        deactivate(activationToken);
    }

    /** Explicit owner-confirmed SEEK may rearm the historical calendar; ordinary interruption return never does. */
    public synchronized void onConfirmedSeek() {
        if (calendar == null) return;
        if(confirmedReplay){explicitReplayPending=true;restoreTextPending=true;rewindProof.rebase();}
        interrupted = false;
        deactivate(activationToken);
    }

    /** Apply one already-selected observation with the exact historical branch order. */
    public synchronized void onObservation(MediaObservation observation) {
        onObservation(observation,null,-1,Float.NaN);
    }

    /** Use actual capture/session evidence for replay only; selected MEDIA time still solely advances the calendar. */
    public synchronized void onObservation(MediaObservation observation,Object sessionKey,long capturedMs,float speed) {
        if (calendar == null || observation == null) return;
        final String token = activationToken;
        if (!observation.eligible()) {
            if(confirmedReplay){onInterruption();return;}
            deactivate(token);
            return;
        }
        long positionMs = observation.positionMs();
        if(confirmedReplay && positionMs<0){onInterruption();return;}
        if(confirmedReplay && positionMs>=0){
            MediaRewindProof.Result proof=rewindProof.observe(observation,sessionKey,capturedMs,speed);
            if(explicitReplayPending || proof==MediaRewindProof.Result.CONFIRMED){
                rearmReplayFrom(explicitReplayPending?positionMs:rewindProof.landing(),token);
                explicitReplayPending=false;replayLandingPending=true;restoreTextPending=true;rewindProof.applied();
            }else if(proof==MediaRewindProof.Result.WAITING){
                if(!interrupted){interruptedPositionMs=lastPositionMs;rewindCandidateMs=-1;interrupted=true;restoreTextPending=true;deactivate(token);}
                return;
            }
        }
        if (interrupted) {
            if (positionMs < 0L) return;
            if(recoverInterruptedTextRewind && !replayLandingPending && interruptedPositionMs>=0
                    && positionMs<interruptedPositionMs-BACKWARD_SEEK_THRESHOLD_MS){
                if(rewindCandidateMs<0 || positionMs<rewindCandidateMs || positionMs-rewindCandidateMs>FORWARD_SEEK_THRESHOLD_MS){
                    rewindCandidateMs=positionMs;return;
                }
                for(SceneEvent event:calendar.events())if(!audioIds.contains(event.eventId())
                        && event.startMs()>=rewindCandidateMs && consumed.remove(event.eventId()))textRearmed++;
            }
            interrupted = false;
            interruptedPositionMs=-1;rewindCandidateMs=-1;
            mediaEligible = true;
            sink.onEligibility(token, true);
            sink.onPlayback(token, observation.playing(), calendar.freezeOnPause());
            lastPositionMs = positionMs;
            // Skip all expired windows on a confirmed return. Do not call
            // rearmFrom/renderAudioAtLanding: both deliberately replay on SEEK.
            for (SceneEvent event : calendar.events()) {
                if (event.startMs() < positionMs && event.startMs() + event.durationMs() <= positionMs)
                    traverse(event,"INTERRUPTION_WINDOW_PASSED");
            }
            skipTooOld(positionMs);
            if (observation.playing()) {
                if(replayLandingPending)renderAudioAtLanding(positionMs,token,true);
                replayLandingPending=false;renderDue(positionMs, token);expireElapsed(positionMs, token);restoreText(positionMs,token);
            }
            return;
        }
        if (!mediaEligible) {
            mediaEligible = true;
            sink.onEligibility(token, true);
            lastPositionMs = -1L;
            if (positionMs >= 0L && !confirmedReplay) rearmFrom(positionMs, token);
        }
        sink.onPlayback(token, observation.playing(), calendar.freezeOnPause());
        if (positionMs < 0L) return;
        if (lastPositionMs < 0L) {
            lastPositionMs = positionMs;
            skipTooOld(positionMs);
            if (observation.playing()) {
                renderAudioAtLanding(positionMs, token, true);
                renderDue(positionMs, token);
                expireElapsed(positionMs, token);
                restoreText(positionMs,token);replayLandingPending=false;
            }
            return;
        }
        long deltaMs = positionMs - lastPositionMs;
        if (deltaMs > FORWARD_SEEK_THRESHOLD_MS) {
            skipThrough(positionMs);
            lastPositionMs = positionMs;
            expireElapsed(positionMs, token);
            reconcileAudio(positionMs, token, true);
            if (observation.playing()) renderAudioAtLanding(positionMs, token, true);
            if(observation.playing())restoreText(positionMs,token);
            return;
        }
        if (deltaMs < -BACKWARD_SEEK_THRESHOLD_MS) {
            if(confirmedReplay){rearmTextFrom(positionMs,token);restoreTextPending=true;}else rearmFrom(positionMs, token);
            lastPositionMs = positionMs;
            reconcileAudio(positionMs, token, true);
            if (observation.playing()) {if(!confirmedReplay || replayLandingPending)renderAudioAtLanding(positionMs, token, true);restoreText(positionMs,token);}
            replayLandingPending=false;
            return;
        }
        lastPositionMs = positionMs;
        reconcileAudio(positionMs, token, deltaMs < 0L);
        if (observation.playing() && deltaMs < 0L && !confirmedReplay) renderAudioAtLanding(positionMs, token, true);
        if (observation.playing()) {
            renderDue(positionMs, token);
            expireElapsed(positionMs, token);
            restoreText(positionMs,token);
        }
    }

    /** Drop windows silently before the single eligibility-loss callback. */
    private void deactivate(String token) {
        boolean wasEligible = mediaEligible;
        mediaEligible = false;
        lastPositionMs = -1L;
        windowed.clear();
        if (wasEligible) sink.onEligibility(token, false);
    }

    /** Consume strictly too-old starts, retaining the exact 2000-ms boundary. */
    private void skipTooOld(long positionMs) {
        long cutoff = Math.max(0L, positionMs - MAX_LATE_MS);
        for (SceneEvent event : calendar.events()) {
            if (event.startMs() < cutoff && (!audioIds.contains(event.eventId())
                    || event.startMs() + event.durationMs() <= positionMs)) traverse(event,"WINDOW_PASSED");
        }
    }

    /** A forward seek consumes even the event exactly at the landing position. */
    private void skipThrough(long positionMs) {
        for (SceneEvent event : calendar.events()) {
            if (event.startMs() <= positionMs) {
                if (audioIds.contains(event.eventId()) && positionMs < event.startMs() + event.durationMs()) {if(!confirmedReplay)consumed.remove(event.eventId());}
                else traverse(event,"FORWARD_SEEK_SKIPPED");
            }
        }
    }

    /** Rearm in calendar order, removing each window before its immediate callback. */
    private void rearmFrom(long positionMs, String token) {
        for (SceneEvent event : calendar.events()) {
            if (event.startMs() >= positionMs && consumed.remove(event.eventId())) {
                SceneEvent removed = windowed.remove(event.eventId());
                if (removed != null) sink.onExpire(token, removed.eventId());
            }
        }
    }

    /** Rearm both types only after a confirmed lower timeline, retaining every expired earlier event. */
    private void rearmReplayFrom(long positionMs,String token){
        for(SceneEvent event:calendar.events())if(event.startMs()>=positionMs
                || audioIds.contains(event.eventId()) && positionMs<event.startMs()+event.durationMs()){
            if(consumed.remove(event.eventId())){
                if(audioIds.contains(event.eventId()))audioTransition(event.eventId(),"AUDIO_REARMED","CONFIRMED_BACKWARD");else textRearmed++;
            }
            SceneEvent removed=windowed.remove(event.eventId());if(removed!=null)sink.onExpire(token,event.eventId());
        }
    }

    /** Retain v23 TEXT replay for a smaller lower coordinate without granting AUDIO replay. */
    private void rearmTextFrom(long positionMs,String token){for(SceneEvent event:calendar.events())if(!audioIds.contains(event.eventId())
            && event.startMs()>=positionMs && consumed.remove(event.eventId())){textRearmed++;if(windowed.remove(event.eventId())!=null)sink.onExpire(token,event.eventId());}}

    /** Restore a consumed TEXT only within its original window, never by replaying AUDIO or changing timestamps. */
    private void restoreText(long positionMs,String token){
        if(!confirmedReplay || !restoreTextPending)return;
        restoreTextPending=false;
        for(SceneEvent event:calendar.events())if(!audioIds.contains(event.eventId()) && !windowed.containsKey(event.eventId()) && consumed.contains(event.eventId())
                && event.startMs()<=positionMs && positionMs<event.startMs()+event.durationMs()){
            windowed.put(event.eventId(),event);textRestoreRequests++;sink.onTextRestore(token,event.eventId());return;
        }
    }

    /** A mode change preserves all event consumption and does not constitute SEEK evidence. */
    public synchronized void onPolicyChanged(){if(confirmedReplay){rewindProof.rebase();restoreTextPending=true;}}

    /** Re-enabling TEXT may restore the current valid window on the next supplied MEDIA observation. */
    public synchronized void requestTextRestore(){if(confirmedReplay){restoreTextPending=true;
        windowed.entrySet().removeIf(entry->!audioIds.contains(entry.getKey()));}}

    /** Track traversal/skip without claiming that a native decoder ever started. */
    private void traverse(SceneEvent event,String reason){if(consumed.add(event.eventId()) && audioIds.contains(event.eventId()))audioTransition(event.eventId(),"AUDIO_SKIPPED",reason);}

    /** Mutable per-event counters stay bounded by the immutable admitted calendar. */
    private static final class AudioFacts {
        final int ordinal;final long scheduledMs;
        int due,skipped,rearmed,prepareRequests,prepared,started,stopped,completed,refused,errors;
        boolean startedInPass;
        String stage="AUDIO_FUTURE",reason="LOADED";
        /** Index only a known AUDIO coordinate, never a token, resource URL or editorial payload. */
        AudioFacts(int ordinal,long scheduledMs){this.ordinal=ordinal;this.scheduledMs=scheduledMs;}
    }

    /** Immutable event outcome: traversal and native STARTED remain distinct facts. */
    public static final class AudioDiagnostics {
        public final int ordinal,due,skipped,rearmed,prepareRequests,prepared,started,stopped,completed,refused,errors;
        public final long scheduledMs;
        public final boolean traversed,consumedWithoutStart;
        public final String stage,reason;
        /** Freeze only bounded public diagnostic scalars and a closed stage/reason vocabulary. */
        private AudioDiagnostics(AudioFacts facts,boolean traversed){ordinal=facts.ordinal;scheduledMs=facts.scheduledMs;
            due=facts.due;skipped=facts.skipped;rearmed=facts.rearmed;prepareRequests=facts.prepareRequests;prepared=facts.prepared;
            started=facts.started;stopped=facts.stopped;completed=facts.completed;refused=facts.refused;errors=facts.errors;
            this.traversed=traversed;consumedWithoutStart=traversed && !facts.startedInPass;stage=facts.stage;reason=facts.reason;}
        /** Safe event-indexed trace, with no resource identifier or content. */
        @Override public String toString(){return "ordinal="+ordinal+" scheduledMs="+scheduledMs+" stage="+stage+" reason="+reason
                +" traversed="+traversed+" consumedWithoutStart="+consumedWithoutStart+" due="+due+" skipped="+skipped+" rearmed="+rearmed
                +" prepareRequests="+prepareRequests+" prepared="+prepared+" started="+started+" stopped="+stopped+" completed="+completed+" refused="+refused+" errors="+errors;}
    }

    /** Observe native facts without authorizing playback or changing calendar consumption. */
    public synchronized void recordAudio(String eventId,String stage,String reason){audioTransition(eventId,stage,reason);}

    /** Return one event's outcome to qualification/UI without exposing its opaque ID. */
    public synchronized AudioDiagnostics audioDiagnostics(String eventId){AudioFacts facts=audioFacts.get(eventId);return facts==null?null:new AudioDiagnostics(facts,consumed.contains(eventId));}

    /** Dispatch a finite diagnostic edge; rendering remains entirely controlled by DUE/position callbacks. */
    private void audioTransition(String eventId,String stage,String reason){
        AudioFacts facts=audioFacts.get(eventId);if(facts==null)return;
        switch(stage){
            case "AUDIO_DUE":facts.due++;break;
            case "AUDIO_SKIPPED":facts.skipped++;break;
            case "AUDIO_REARMED":facts.rearmed++;facts.startedInPass=false;break;
            case "AUDIO_PREPARE_REQUESTED":facts.prepareRequests++;break;
            case "AUDIO_PREPARED":facts.prepared++;break;
            case "AUDIO_RESOURCE_STARTED":facts.started++;facts.startedInPass=true;break;
            case "AUDIO_STOPPED":facts.stopped++;break;
            case "AUDIO_COMPLETED":facts.completed++;break;
            case "AUDIO_START_REFUSED":case "AUDIO_REFUSED":facts.refused++;break;
            case "AUDIO_ERROR":facts.errors++;break;
            default:break;
        }
        facts.stage=stage;facts.reason=reason;sink.onAudioTransition(activationToken,new AudioDiagnostics(facts,consumed.contains(eventId)));
    }

    /** Consume and window exactly one first candidate before emitting DUE. */
    private void renderDue(long positionMs, String token) {
        skipTooOld(positionMs);
        for (SceneEvent event : calendar.events()) {
            if (consumed.contains(event.eventId())) continue;
            if (event.startMs() > positionMs) break;
            consumed.add(event.eventId());
            if (audioIds.contains(event.eventId()) && positionMs >= event.startMs() + event.durationMs()) continue;
            if (event.durationMs() > 0L) windowed.put(event.eventId(), event);
            if (audioIds.contains(event.eventId())) sink.onAudioPosition(token,event.eventId(),positionMs-event.startMs(),false);
            if(audioIds.contains(event.eventId()))audioDue++;else textDue++;
            if(audioIds.contains(event.eventId()))audioTransition(event.eventId(),"AUDIO_DUE","ELIGIBLE_WINDOW");
            sink.onDue(token, event.eventId());
            if (!audioIds.contains(event.eventId())) return;
        }
    }

    /** Reconcile only AUDIO windows; every TEXT branch retains its existing admission. */
    private void reconcileAudio(long positionMs, String token, boolean discontinuity) {
        for (SceneEvent event : calendar.events()) {
            if (!audioIds.contains(event.eventId()) || !windowed.containsKey(event.eventId())) continue;
            if (positionMs < event.startMs() || positionMs >= event.startMs() + event.durationMs()) {
                windowed.remove(event.eventId()); sink.onExpire(token,event.eventId());
                if (positionMs < event.startMs() && !confirmedReplay) consumed.remove(event.eventId());
            } else sink.onAudioPosition(token,event.eventId(),positionMs-event.startMs(),discontinuity);
        }
    }

    /** Admit a seek landing inside AUDIO from the single calendar, never from a renderer clock. */
    private void renderAudioAtLanding(long positionMs, String token, boolean discontinuity) {
        for (SceneEvent event : calendar.events()) {
            if (!audioIds.contains(event.eventId()) || positionMs < event.startMs()
                    || positionMs >= event.startMs() + event.durationMs()) continue;
            if (windowed.containsKey(event.eventId())) continue;
            if(confirmedReplay && consumed.contains(event.eventId()))continue;
            consumed.add(event.eventId()); windowed.put(event.eventId(),event);
            sink.onAudioPosition(token,event.eventId(),positionMs-event.startMs(),discontinuity);
            audioDue++;
            audioTransition(event.eventId(),"AUDIO_DUE","LANDING_WITHIN_WINDOW");
            sink.onDue(token,event.eventId());
        }
    }

    /** Collect in raw HashMap order, then remove and callback individually. */
    private void expireElapsed(long positionMs, String token) {
        if (windowed.isEmpty()) return;
        List<SceneEvent> due = null;
        for (SceneEvent event : windowed.values()) {
            if (event.startMs() + event.durationMs() <= positionMs) {
                if (due == null) due = new ArrayList<>();
                due.add(event);
            }
        }
        if (due == null) return;
        for (SceneEvent event : due) {
            windowed.remove(event.eventId());
            sink.onExpire(token, event.eventId());
        }
    }

    /** Immutable temporal counters: no event IDs, content, tokens or additional playback clock. */
    public static final class Diagnostics {
        public final int total,consumed,future,eligible,windows,textDue,audioDue,textRearmed,textRestoreRequests;
        public final long positionMs,nextStartMs;
        public final boolean interrupted;
        public final String replay;
        /** Freeze one scheduler read without exposing mutable event sets. */
        private Diagnostics(int total,int consumed,int future,int eligible,int windows,int textDue,int audioDue,
                int textRearmed,int textRestoreRequests,long position,long next,boolean interrupted,String replay){
            this.total=total;this.consumed=consumed;this.future=future;this.eligible=eligible;this.windows=windows;
            this.textDue=textDue;this.audioDue=audioDue;this.textRearmed=textRearmed;positionMs=position;nextStartMs=next;this.interrupted=interrupted;
            this.textRestoreRequests=textRestoreRequests;this.replay=replay;
        }
        /** Stable numeric diagnostic, safe for transition logs and local developer UI. */
        @Override public String toString(){return "total="+total+" consumed="+consumed+" future="+future+" eligible="+eligible
                +" windows="+windows+" textDue="+textDue+" audioDue="+audioDue+" textRearmed="+textRearmed+" textRestoreRequests="+textRestoreRequests+" positionMs="+positionMs+" nextMs="+nextStartMs+" interrupted="+interrupted+" replay="+replay;}
    }
    /** Observe the first unconsumed future start separately from normal silence between windows. */
    public synchronized Diagnostics diagnostics(){
        int total=calendar==null?0:calendar.events().size(),future=0,due=0;long next=-1;
        if(calendar!=null)for(SceneEvent event:calendar.events())if(!consumed.contains(event.eventId())){
            if(lastPositionMs<0 || event.startMs()>lastPositionMs){future++;if(next<0)next=event.startMs();}
            else if(event.startMs()+event.durationMs()>lastPositionMs)due++;
        }
        return new Diagnostics(total,consumed.size(),future,due,windowed.size(),textDue,audioDue,textRearmed,textRestoreRequests,lastPositionMs,next,interrupted,rewindProof.reason());
    }
}
