package com.scenevibe.tvcompanionpoc.ad;

import com.scenevibe.tvcompanionpoc.ad.AdEvidenceDetector.Marker;
import com.scenevibe.tvcompanionpoc.ad.AdEvidenceDetector.Sample;

/**
 * Experimental SceneVibe-only advertising gate, independent of editorial time.
 * ABSENT is ordinary publisher behavior, never standalone content proof. After
 * a positive flag, absence must be followed by coherent, progressing compatible
 * MEDIA before this gate releases. No cache, preference, admission, player or
 * clock is owned here. Owner annotations cannot enter this machine.
 */
public final class AdProtectionController {
    public enum State { NORMAL, AD_PENDING, AD_GUARD, EXIT_VERIFY, UNCERTAIN }
    public enum Edge { NONE, AD_START, AD_PRESENT, AD_END }
    /** Tunable bounded evidence policy, expressed in capture time rather than editorial offsets. */
    public static final class Policy {
        public final long freshMs,confirmMs,exitMs,progressMs,errorMs;
        public final int exitSamples;
        /** Reject unsafe or internally inconsistent experiment thresholds. */
        public Policy(long freshMs,long confirmMs,long exitMs,long progressMs,long errorMs,int exitSamples){
            if(freshMs<500 || confirmMs<100 || exitMs<500 || exitMs>freshMs || progressMs<250 || errorMs<0 || exitSamples<3)
                throw new IllegalArgumentException("AD_POLICY_INVALID");
            this.freshMs=freshMs;this.confirmMs=confirmMs;this.exitMs=exitMs;this.progressMs=progressMs;this.errorMs=errorMs;this.exitSamples=exitSamples;
        }
    }
    /** A gate result is not a MEDIA permit or a replacement film position. */
    public static final class Decision {
        public final State state;
        public final Edge edge;
        public final String reason;
        public final boolean enabled,allow;
        public final int adStarts,adEnds;
        /** Freeze only closed diagnostic vocabulary and counters. */
        private Decision(State state,Edge edge,String reason,boolean enabled,boolean allow,int starts,int ends){
            this.state=state;this.edge=edge;this.reason=reason;this.enabled=enabled;this.allow=allow;adStarts=starts;adEnds=ends;
        }
    }
    private final Policy policy;
    private boolean enabled,episode,confirmed,observedAppearance;
    private State state=State.NORMAL;
    private String reason="PROTECTION_OFF";
    private Object session,lastAbsentSession;
    private long lastCapture=-1,lastAbsent=-1,firstPositive=-1,exitFirst=-1;
    private Sample exitLast;
    private int positives,exits,starts,ends;

    /** Use 2 positive polls, then at least 3 advancing exit captures over 750 ms. */
    public AdProtectionController(){this(new Policy(2500,100,750,500,500,3));}
    /** Accept an explicit tested policy without altering the application architecture. */
    public AdProtectionController(Policy policy){if(policy==null)throw new IllegalArgumentException("AD_POLICY_REQUIRED");this.policy=policy;}

    /** OFF is an explicit reference-path escape, not a hidden permanent preference. */
    public void setEnabled(boolean value){if(enabled==value)return;enabled=value;episode=false;confirmed=false;observedAppearance=false;
        session=null;lastAbsentSession=null;lastCapture=-1;lastAbsent=-1;firstPositive=-1;positives=0;clearExit();starts=0;ends=0;
        state=value?State.UNCERTAIN:State.NORMAL;reason=value?"INITIAL_CAPTURE_REQUIRED":"PROTECTION_OFF";}

    /** Immediately inhibit local effects on a valid target metadata callback; polling confirms presence separately. */
    public Decision positive(Object targetSession,long now){
        if(!enabled)return status();
        if(targetSession==null || now<0)return unavailable();
        if(!episode){episode=true;observedAppearance=lastAbsentSession!=null && lastAbsentSession.equals(targetSession)
                && now>=lastAbsent && now-lastAbsent<=policy.freshMs;}
        if(session!=null && !session.equals(targetSession)){positives=0;firstPositive=-1;}
        session=targetSession;clearExit();state=confirmed?State.AD_GUARD:State.AD_PENDING;reason="EXPLICIT_AD_PREVENTIVE_HOLD";
        return decision(Edge.NONE);
    }

    /** Read actual target captures; normal seeks/pauses without a positive flag never create an ad episode. */
    public Decision observe(Sample sample,long now){
        if(!enabled)return status();
        if(sample==null || sample.session==null || !sample.singleTarget)return unavailable();
        if(sample.capturedMs<0 || now<sample.capturedMs || now-sample.capturedMs>policy.freshMs)return uncertain("CAPTURE_STALE");
        if(lastCapture>=0 && sample.capturedMs<=lastCapture)return status();
        boolean changed=session!=null && !session.equals(sample.session);
        boolean gap=lastCapture>=0 && sample.capturedMs-lastCapture>policy.freshMs;
        if(changed || gap){clearExit();positives=0;firstPositive=-1;lastAbsent=-1;lastAbsentSession=null;}
        session=sample.session;lastCapture=sample.capturedMs;
        if(sample.marker==Marker.AD){
            positive(sample.session,sample.capturedMs);
            if(firstPositive<0){firstPositive=sample.capturedMs;positives=1;}
            else positives++;
            if(positives>=2 && sample.capturedMs-firstPositive>=policy.confirmMs){
                state=State.AD_GUARD;reason="EXPLICIT_AD_CONFIRMED";
                if(!confirmed){confirmed=true;if(observedAppearance){starts++;return decision(Edge.AD_START);}return decision(Edge.AD_PRESENT);}
            }
            return status();
        }
        positives=0;firstPositive=-1;
        if(sample.marker==Marker.INVALID){if(episode)return uncertain("MARKER_MALFORMED_DURING_HOLD");
            state=State.NORMAL;reason="MALFORMED_NOT_AD_EVIDENCE";return status();}
        if(!episode){lastAbsent=sample.capturedMs;lastAbsentSession=sample.session;state=State.NORMAL;reason="NO_EXPLICIT_AD";return status();}
        if(!sample.identityCompatible || !sample.clockValid || !sample.playing || sample.positionMs<0
                || !Float.isFinite(sample.speed) || sample.speed<=0)return uncertain("RETURN_MEDIA_UNPROVEN");
        state=State.EXIT_VERIFY;reason="ABSENT_VERIFY_IDENTITY_AND_PROGRESS";
        boolean coherent=exitLast!=null && exitLast.session.equals(sample.session);
        if(coherent){long elapsed=sample.capturedMs-exitLast.capturedMs;double delta=sample.positionMs-(double)exitLast.positionMs;
            coherent=elapsed>0 && elapsed<=policy.freshMs && delta>=0
                    && Math.abs(delta-elapsed*(double)exitLast.speed)<=policy.errorMs;}
        if(!coherent){exitFirst=sample.capturedMs;exits=1;}else exits++;
        Sample previous=exitLast;exitLast=sample;
        // Keep the first coordinate separately: expected progress must actually
        // occur; a frozen PLAYING position cannot pass by elapsed time alone.
        if(!coherent || previous==null)exitPosition=sample.positionMs;
        if(exits>=policy.exitSamples && sample.capturedMs-exitFirst>=policy.exitMs && sample.positionMs-exitPosition>=policy.progressMs){
            boolean wasConfirmed=confirmed;episode=false;confirmed=false;observedAppearance=false;clearExit();
            lastAbsent=sample.capturedMs;lastAbsentSession=sample.session;state=State.NORMAL;
            reason=wasConfirmed?"FILM_CLOCK_REQUALIFIED":"ISOLATED_FLAG_RECOVERED";
            if(wasConfirmed){ends++;return decision(Edge.AD_END);}
        }
        return status();
    }
    private long exitPosition;
    /** No absence, session destruction or timeout establishes an advertising end. */
    public Decision unavailable(){return enabled?uncertain(episode?"AD_HOLD_MEDIA_UNAVAILABLE":"MEDIA_UNAVAILABLE"):status();}
    /** Stale monitoring cannot release an active break; an explicit OFF remains available to the owner. */
    public Decision watch(long now){if(enabled && lastCapture>=0 && now-lastCapture>policy.freshMs)return uncertain("OBSERVATION_TIMEOUT");return status();}
    /** The historical MEDIA gate remains independently necessary even when this gate says allow. */
    public Decision status(){return decision(Edge.NONE);}
    /** Discard only consecutive exit proof; retain the positive episode across uncertainty. */
    private Decision uncertain(String cause){clearExit();positives=0;firstPositive=-1;state=State.UNCERTAIN;reason=cause;return status();}
    /** Reset consecutive exit observations, never editorial consumption. */
    private void clearExit(){exitLast=null;exitFirst=-1;exits=0;exitPosition=-1;}
    /** Snapshot without consuming an edge on a later status request. */
    private Decision decision(Edge edge){return new Decision(state,edge,reason,enabled,!enabled || state==State.NORMAL,starts,ends);}
}
