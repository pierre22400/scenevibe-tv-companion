package com.scenevibe.tvcompanionpoc.ad;

/**
 * Experimental classifier for an EXPLICIT public publisher advertisement flag.
 * It owns no calendar, player, permission or clock. Missing flags, normal MEDIA
 * playback and temporal anomalies never establish visible editorial content or
 * advertising. Its decisions are observation-only until the publisher contract
 * has been corroborated on the actual Sony/Prime version.
 */
public final class AdEvidenceDetector {
    /** Candidate evidence states, never a claim that Sony pixels were inspected. */
    public enum State { CONTENT_CONFIRMED, AD_CONFIRMED, UNCERTAIN }
    /** ABSENT is different from a present explicit numeric zero. */
    public enum Marker { ABSENT, AD, CONTENT, INVALID }
    /** Distinct publisher-contract transitions; neither is inferred from a seek/pause. */
    public enum Edge { NONE, AD_START, AD_END }
    /** Closed diagnostic reasons contain no publisher payload. */
    public enum Reason { NO_PUBLIC_AD_MARKER, NO_TARGET_SESSION, SESSION_CHANGED, STALE_CAPTURE,
        INVALID_MARKER, CONTENT_MEDIA_INVALID, AD_CONFIRMING, CONTENT_CONFIRMING,
        EXPLICIT_AD_FLAG, EXPLICIT_CONTENT_FLAG, OBSERVATION_LOST }

    /** Facts obtained from a public controller; opaque session identity remains in memory only. */
    public static final class Sample {
        public final Object session;
        public final long capturedMs,positionMs;
        public final float speed;
        public final boolean singleTarget,identityCompatible,playing,clockValid;
        public final Marker marker;
        /** Retain already-captured scalars without estimating time or accepting a timeline event ID. */
        public Sample(Object session,long capturedMs,long positionMs,float speed,boolean singleTarget,
                boolean identityCompatible,boolean playing,boolean clockValid,Marker marker){
            this.session=session;this.capturedMs=capturedMs;this.positionMs=positionMs;this.speed=speed;
            this.singleTarget=singleTarget;this.identityCompatible=identityCompatible;this.playing=playing;
            this.clockValid=clockValid;this.marker=marker==null?Marker.INVALID:marker;
        }
    }

    /** Read-only candidate result; no rendering authority or content-time replacement is returned. */
    public static final class Decision {
        public final State state;
        public final Reason reason;
        public final Edge edge;
        public final int adStarts,adEnds;
        /** Freeze bounded diagnostic facts and transition counts. */
        private Decision(State state,Reason reason,Edge edge,int starts,int ends){
            this.state=state;this.reason=reason;this.edge=edge;adStarts=starts;adEnds=ends;
        }
        /** Format only the closed classifier vocabulary and counters. */
        @Override public String toString(){return "candidate="+state+" reason="+reason+" edge="+edge+" adStarts="+adStarts+" adEnds="+adEnds;}
    }

    private State state=State.UNCERTAIN;
    private Reason reason=Reason.NO_PUBLIC_AD_MARKER;
    private Object session;
    private Sample candidate;
    private long lastCapture=-1;
    private boolean adOpen;
    private int starts,ends;

    /** Begin without any assertion about the visible programme or advertising. */
    public AdEvidenceDetector(){}

    /** Reset only this experiment's inference, never another component's consumption or cache. */
    public void reset(){state=State.UNCERTAIN;reason=Reason.NO_PUBLIC_AD_MARKER;session=null;candidate=null;
        lastCapture=-1;adOpen=false;starts=0;ends=0;}

    /** Consume explicit publisher evidence; capture time bounds freshness but never advances film time. */
    public Decision observe(Sample sample,long nowMs){
        if(sample==null || sample.session==null || !sample.singleTarget){session=null;adOpen=false;return uncertain(Reason.NO_TARGET_SESSION);}
        if(sample.capturedMs<0 || nowMs<sample.capturedMs || nowMs-sample.capturedMs>2500)return uncertain(Reason.STALE_CAPTURE);
        boolean changed=session!=null && !session.equals(sample.session);
        if(changed){candidate=null;adOpen=false;lastCapture=-1;state=State.UNCERTAIN;reason=Reason.SESSION_CHANGED;}
        session=sample.session;
        if(sample.capturedMs<=lastCapture)return decision(Edge.NONE);
        lastCapture=sample.capturedMs;
        if(sample.marker==Marker.ABSENT)return uncertain(Reason.NO_PUBLIC_AD_MARKER);
        if(sample.marker==Marker.INVALID)return uncertain(Reason.INVALID_MARKER);
        if(sample.marker==Marker.CONTENT && (!sample.identityCompatible || !sample.clockValid
                || !sample.playing || sample.positionMs<0 || !Float.isFinite(sample.speed) || sample.speed<=0))
            return uncertain(Reason.CONTENT_MEDIA_INVALID);
        boolean coherent=candidate!=null && candidate.marker==sample.marker && candidate.session.equals(sample.session)
                && sample.capturedMs>candidate.capturedMs && sample.capturedMs-candidate.capturedMs<=2500;
        if(coherent && sample.marker==Marker.CONTENT){
            long elapsed=sample.capturedMs-candidate.capturedMs;
            double error=sample.positionMs-candidate.positionMs-elapsed*(double)candidate.speed;
            coherent=error>=-500 && error<=750;
        }
        if(!coherent){candidate=sample;state=State.UNCERTAIN;reason=changed?Reason.SESSION_CHANGED:
                sample.marker==Marker.AD?Reason.AD_CONFIRMING:Reason.CONTENT_CONFIRMING;return decision(Edge.NONE);}
        if(sample.capturedMs-candidate.capturedMs<100)return decision(Edge.NONE);
        candidate=sample;
        if(sample.marker==Marker.AD){
            state=State.AD_CONFIRMED;reason=Reason.EXPLICIT_AD_FLAG;
            if(!adOpen){adOpen=true;starts++;return decision(Edge.AD_START);}
        }else{
            state=State.CONTENT_CONFIRMED;reason=Reason.EXPLICIT_CONTENT_FLAG;
            if(adOpen){adOpen=false;ends++;return decision(Edge.AD_END);}
        }
        return decision(Edge.NONE);
    }

    /** Session/metadata loss is uncertainty, never an advertisement end. */
    public Decision unavailable(){return uncertain(Reason.OBSERVATION_LOST);}

    /** Expose candidate state without consuming a new capture or repeating its edge. */
    public Decision status(){return decision(Edge.NONE);}

    /** Lose consecutive evidence while remembering an already-open break within the same session. */
    private Decision uncertain(Reason cause){candidate=null;state=State.UNCERTAIN;reason=cause;return decision(Edge.NONE);}

    /** Snapshot independent transition counters without granting any commentary effect. */
    private Decision decision(Edge edge){return new Decision(state,reason,edge,starts,ends);}
}
