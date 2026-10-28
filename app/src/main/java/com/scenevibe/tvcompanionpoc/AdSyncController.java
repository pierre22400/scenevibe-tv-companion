package com.scenevibe.tvcompanionpoc;

/**
 * Local, reactive interruption guard over the one existing Android MEDIA clock.
 * No timer, network, player command, ad classifier or editorial retiming exists
 * here. A compatible continuous session is not proof of absence of advertising.
 * Ambiguous discontinuities close the gate until the user confirms a fresh
 * position; manual calibration adds an offset only to the selected observation.
 * The opt-in v22 policy instead automatically confirms recovery from two valid
 * current observations. Manual holds remain explicit; uncertainty is not an ad.
 */
final class AdSyncController {
    enum State { DISABLED, SYNCHRONIZED, SUSPENDED, UNCERTAIN, RESYNCHRONIZING, MANUAL }
    enum Reason { OFF, CONTINUITY_ONLY, USER_SUSPENDED, SESSION_UNAVAILABLE,
        IDENTITY_OR_CLOCK_INVALID, SESSION_CHANGED, DURATION_CHANGED, CLOCK_JUMP,
        FRESH_POSITION_FROZEN, CONFIRMATION_REQUIRED, FRESH_ANCHOR_REQUIRED, MANUAL_POSITION, OBSERVATION_STALE,
        AUTO_RECOVERED, MEDIA_IDENTITY_INVALID, MEDIA_CLOCK_INVALID }
    /** Precise, payload-free outcomes shared by the application and the remote control panel. */
    enum Calibration {
        APPLIED("Recalage accepté ; prochaine observation MEDIA attendue."),
        SESSION_ABSENT("Session MEDIA absente. Attends le retour du film puis réessaie."),
        IDENTITY_INCOMPATIBLE("Identité MEDIA incompatible avec la track activée."),
        INVALID_OBSERVATION("Observation MEDIA invalide : position, état ou ancre indisponible."),
        OBSERVATION_STALE("Observation MEDIA trop ancienne. Réessaie sans quitter le film."),
        OFFSET_LIMIT("Écart supérieur à une heure. Vérifie le temps saisi et l’édition du film."),
        FORMAT_INVALID("Format temporel incorrect. Utilise mm:ss ou hh:mm:ss."),
        COMPANION_INACTIVE("Session Companion inactive. Active d’abord la track dans SceneVibe."),
        SYNC_DISABLED("La protection des interruptions est désactivée.");
        final String message;
        /** Give every closed diagnostic a specific, actionable sentence without private metadata. */
        Calibration(String message) { this.message=message; }
    }
    static final long MAX_OFFSET_MS=3_600_000L;
    private static final long FRESH_MS=2500L;

    /** Immutable public-session scalars; the opaque session key is never logged or serialized. */
    static final class Sample {
        final Object session;
        final boolean usable,playing,identityCompatible;
        final long rawMs,selectedMs,anchorMs,capturedMs,durationMs;
        final float speed;
        /** Retain facts already read by the passive probe, without deriving another media clock. */
        Sample(Object session,boolean usable,boolean playing,long raw,long selected,long anchor,long captured,float speed,long duration) {
            this(session,usable,playing,raw,selected,anchor,captured,speed,duration,usable);
        }
        /** Distinguish identity refusal from an invalid clock without accepting either as usable. */
        Sample(Object session,boolean usable,boolean playing,long raw,long selected,long anchor,long captured,float speed,long duration,boolean compatible) {
            identityCompatible=compatible;
            this.session=session;this.usable=usable;this.playing=playing;rawMs=raw;selectedMs=selected;
            anchorMs=anchor;capturedMs=captured;this.speed=speed;durationMs=duration;
        }
        /** Reject missing, future, stale-by-capture and nonfinite temporal facts before any manual admission. */
        boolean valid(){return usable && session!=null && rawMs>=0 && selectedMs>=0 && capturedMs>=0
                && anchorMs>0 && anchorMs<=capturedMs && durationMs>=-1 && durationMs<=43_200_000L && Float.isFinite(speed) && (!playing || speed>0f);}
    }

    /** A decision authorizes only SceneVibe effects; ad status is deliberately unavailable. */
    static final class Decision {
        final State state;
        final Reason reason;
        final boolean allow;
        final long contentMs,offsetMs;
        /** Snapshot bounded diagnostics without any title, account or platform identity value. */
        Decision(State state,Reason reason,boolean allow,long content,long offset) {
            this.state=state;this.reason=reason;this.allow=allow;contentMs=content;offsetMs=offset;
        }
    }

    private boolean enabled,held,resumeRequested;
    private State state=State.DISABLED;
    private Reason reason=Reason.OFF;
    private long offsetMs;
    private Sample latest,accepted,candidate;
    private Object boundSession;
    private long boundDuration=-1;
    private final boolean automaticRecovery;
    private boolean manualHold;

    /** Start disabled so the qualified v17 path remains the default. */
    AdSyncController() {this(false);}
    /** Select v22's non-destructive policy without rewriting historical qualification profiles. */
    AdSyncController(boolean automaticRecovery) {this.automaticRecovery=automaticRecovery;}

    /** Reset process-local inference on opt-in changes; no preference can activate playback. */
    void setEnabled(boolean enabled) {
        if(automaticRecovery){
            this.enabled=enabled;held=manualHold;resumeRequested=false;candidate=null;accepted=null;boundSession=null;boundDuration=-1;
            state=manualHold?State.SUSPENDED:enabled?(offsetMs==0?State.SYNCHRONIZED:State.MANUAL):State.DISABLED;
            reason=manualHold?Reason.USER_SUSPENDED:enabled?Reason.CONTINUITY_ONLY:Reason.OFF;return;
        }
        this.enabled=enabled;held=false;resumeRequested=false;candidate=null;accepted=null;
        boundSession=null;boundDuration=-1;offsetMs=0;
        state=enabled?State.SYNCHRONIZED:State.DISABLED;
        reason=enabled?Reason.CONTINUITY_ONLY:Reason.OFF;
    }

    /** Expose the opt-in independently of the two existing TEXT/AUDIO switches. */
    boolean enabled(){return enabled;}

    /** Stop effects immediately, retaining the scheduler's consumed identities and future events. */
    void suspend(){if(enabled || automaticRecovery){manualHold=true;hold(State.SUSPENDED,Reason.USER_SUSPENDED);}}

    /** Request confirmation from two fresh compatible observations, never from a delay alone. */
    void requestResume(){if(enabled || automaticRecovery){held=true;resumeRequested=true;candidate=null;
        state=State.RESYNCHRONIZING;reason=Reason.CONFIRMATION_REQUIRED;}}

    /** Loss of a session is uncertainty, never evidence that an advertisement started or ended. */
    void unavailable(){if(automaticRecovery){observeRecovering(null,0);return;}latest=null;if(enabled && state!=State.SUSPENDED)hold(State.UNCERTAIN,Reason.SESSION_UNAVAILABLE);}

    /** Consume a real captured observation, preserving the historical selected position when disabled. */
    Decision observe(Sample sample) {
        return observe(sample,sample==null?0:sample.capturedMs);
    }

    /** Check capture freshness against the caller's current monotonic time, not the age of PlaybackState's anchor. */
    Decision observe(Sample sample,long nowMs) {
        if(automaticRecovery)return observeRecovering(sample,nowMs);
        latest=sample;
        if(enabled && sample!=null && (nowMs<sample.capturedMs || nowMs-sample.capturedMs>FRESH_MS)) {
            hold(State.UNCERTAIN,Reason.OBSERVATION_STALE);return decision(false,-1);
        }
        if(!enabled)return decision(sample!=null && sample.usable,sample==null?-1:sample.selectedMs);
        if(sample==null || !sample.valid()) {
            if(state!=State.SUSPENDED)hold(State.UNCERTAIN,Reason.IDENTITY_OR_CLOCK_INVALID);
            return decision(false,-1);
        }
        if(held) {
            if(resumeRequested)confirmResume(sample);
            return decision(!held,translated(sample));
        }
        if(boundSession!=null && !boundSession.equals(sample.session))hold(State.UNCERTAIN,Reason.SESSION_CHANGED);
        else if(boundDuration>0 && (sample.durationMs<=0 || Math.abs(sample.durationMs-boundDuration)>1000L))
            hold(State.UNCERTAIN,Reason.DURATION_CHANGED);
        else if(accepted!=null && !continuous(accepted,sample))hold(State.UNCERTAIN,Reason.CLOCK_JUMP);
        else if(accepted!=null && frozen(accepted,sample))hold(State.UNCERTAIN,Reason.FRESH_POSITION_FROZEN);
        long position=translated(sample);
        if(position<0)hold(State.UNCERTAIN,Reason.IDENTITY_OR_CLOCK_INVALID);
        if(!held)accept(sample);
        return decision(!held,position);
    }

    /** Keep invalid MEDIA closed, but never latch an unknown advertising suspicion permanently. */
    private Decision observeRecovering(Sample sample,long nowMs) {
        latest=sample;
        Reason invalid=sample==null || sample.session==null?Reason.SESSION_UNAVAILABLE:
                !sample.identityCompatible?Reason.MEDIA_IDENTITY_INVALID:
                !sample.valid()?Reason.MEDIA_CLOCK_INVALID:
                nowMs<sample.capturedMs || nowMs-sample.capturedMs>FRESH_MS?Reason.OBSERVATION_STALE:null;
        if(invalid!=null){
            candidate=null;
            if(manualHold && !resumeRequested){state=State.SUSPENDED;reason=Reason.USER_SUSPENDED;held=true;}
            else {held=true;state=State.UNCERTAIN;reason=invalid;}
            return decision(false,-1);
        }
        if(manualHold && !resumeRequested){state=State.SUSPENDED;reason=Reason.USER_SUSPENDED;return decision(false,-1);}
        if(!enabled && !manualHold){held=false;state=State.DISABLED;reason=Reason.OFF;accept(sample);return decision(true,sample.selectedMs);}
        if(held){
            boolean explicit=resumeRequested;
            confirmResume(sample);
            if(!held){manualHold=false;if(!enabled){state=State.DISABLED;reason=Reason.OFF;}
                else if(!explicit)reason=Reason.AUTO_RECOVERED;}
            return decision(!held,translated(sample));
        }
        Reason discontinuity=boundSession!=null && !boundSession.equals(sample.session)?Reason.SESSION_CHANGED:
                accepted!=null && !continuous(accepted,sample)?Reason.CLOCK_JUMP:
                accepted!=null && frozen(accepted,sample)?Reason.FRESH_POSITION_FROZEN:null;
        // Duration alone is not an ad signal. The independent native identity
        // matcher has already established compatibility with this exact track.
        if(discontinuity!=null){hold(State.UNCERTAIN,discontinuity);candidate=sample;return decision(false,-1);}
        long position=translated(sample);
        if(position<0){hold(State.UNCERTAIN,Reason.MEDIA_CLOCK_INVALID);return decision(false,-1);}
        state=offsetMs==0?State.SYNCHRONIZED:State.MANUAL;
        if(reason!=Reason.AUTO_RECOVERED)reason=offsetMs==0?Reason.CONTINUITY_ONLY:Reason.MANUAL_POSITION;
        accept(sample);return decision(true,position);
    }

    /** Confirm a user-supplied film time against a recent, still eligible passive session capture. */
    boolean calibrate(long contentMs,long nowMs) {return calibrateResult(contentMs,nowMs)==Calibration.APPLIED;}

    /** Explain every refusal before applying an offset to a freshly queried, compatible session only. */
    Calibration calibrateResult(long contentMs,long nowMs) {
        if(!enabled)return Calibration.SYNC_DISABLED;
        if(latest==null || latest.session==null)return Calibration.SESSION_ABSENT;
        if(!latest.identityCompatible)return Calibration.IDENTITY_INCOMPATIBLE;
        if(!latest.valid())return Calibration.INVALID_OBSERVATION;
        if(nowMs<latest.capturedMs || nowMs-latest.capturedMs>FRESH_MS)return Calibration.OBSERVATION_STALE;
        if(contentMs<0 || contentMs>43_200_000L)return Calibration.FORMAT_INVALID;
        long offset=contentMs-latest.selectedMs;
        if(offset < -MAX_OFFSET_MS || offset > MAX_OFFSET_MS)return Calibration.OFFSET_LIMIT;
        offsetMs=offset;held=false;manualHold=false;resumeRequested=false;candidate=null;
        state=State.MANUAL;reason=Reason.MANUAL_POSITION;accept(latest);return Calibration.APPLIED;
    }

    /** Parse a user-confirmed mm:ss or hh:mm:ss without guessing a unit or accepting negative/overflow input. */
    static long parseFilmTime(String text) {
        if(text==null || !text.trim().matches("[0-9]{1,3}:[0-9]{2}(:[0-9]{2})?"))throw new IllegalArgumentException("FILM_TIME_FORMAT");
        String[] parts=text.trim().split(":");
        long first=Long.parseLong(parts[0]),second=Long.parseLong(parts[1]);
        if(second>=60 && parts.length==3 || parts.length==2 && second>=60)throw new IllegalArgumentException("FILM_TIME_RANGE");
        long seconds=parts.length==2?first*60+second:first*3600+second*60+Long.parseLong(parts[2]);
        if(parts.length==3 && Long.parseLong(parts[2])>=60 || seconds>43_200)throw new IllegalArgumentException("FILM_TIME_RANGE");
        return seconds*1000;
    }

    /** Present the current gate without claiming advertisement observability. */
    Decision status(){return decision((automaticRecovery?latest!=null && latest.valid():enabled) && !held,latest==null?-1:translated(latest));}

    /** Require two distinct fresh captures of the same compatible content-time progression after explicit user intent. */
    private void confirmResume(Sample sample) {
        // A freshly queried PLAYING session may legitimately keep an old update
        // anchor. Capture age is checked in observe(sample, now); comparing two
        // current positions still guards progression, session, duration and freeze.
        if(candidate==null || !candidate.session.equals(sample.session) || !continuous(candidate,sample)
                || !automaticRecovery && candidate.durationMs!=sample.durationMs || frozen(candidate,sample)){
            candidate=sample;reason=Reason.CONFIRMATION_REQUIRED;return;
        }
        long elapsed=sample.capturedMs-candidate.capturedMs;
        if(elapsed<250)return;
        if(elapsed>FRESH_MS){candidate=sample;return;}
        if(translated(sample)<0){candidate=null;reason=Reason.IDENTITY_OR_CLOCK_INVALID;return;}
        held=false;resumeRequested=false;candidate=null;
        state=offsetMs==0?State.SYNCHRONIZED:State.MANUAL;
        reason=offsetMs==0?Reason.CONTINUITY_ONLY:Reason.MANUAL_POSITION;accept(sample);
    }

    /** Compare only successive selected observations; this expected delta is a guard, never a playback clock. */
    private boolean continuous(Sample before,Sample after) {
        long elapsed=after.capturedMs-before.capturedMs;
        if(elapsed<0 || elapsed>FRESH_MS)return false;
        double expected=before.playing?elapsed*(double)before.speed:0d;
        double delta=(double)after.selectedMs-before.selectedMs-expected;
        return delta>=-2000d && delta<=5000d;
    }

    /** A newly updated PLAYING anchor that repeats its position is ambiguous; an unchanged old anchor is normal extrapolation. */
    private boolean frozen(Sample before,Sample after) {
        return before.playing && after.playing && after.anchorMs>before.anchorMs && after.rawMs==before.rawMs;
    }

    /** Translate observations with checked bounds, without changing the calendar's editorial timestamps. */
    private long translated(Sample sample) {
        if(sample==null || sample.selectedMs<0)return -1;
        if(offsetMs>0 && sample.selectedMs>Long.MAX_VALUE-offsetMs)return -1;
        long position=sample.selectedMs+offsetMs;return position<0?-1:position;
    }

    /** Bind only the accepted session and update the comparison anchor. */
    private void accept(Sample sample){accepted=sample;boundSession=sample.session;boundDuration=sample.durationMs;}

    /** Enter a reversible hold; disappearance alone cannot release it. */
    private void hold(State next,Reason cause){held=true;resumeRequested=false;candidate=null;state=next;reason=cause;}

    /** Construct one truthful bounded effect decision from the process-local state. */
    private Decision decision(boolean allow,long content){return new Decision(state,reason,allow,content,offsetMs);}
}
