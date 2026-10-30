package com.scenevibe.tvcompanionpoc.ad;

import com.scenevibe.tvcompanionpoc.ad.MultiPlatformObservationLog.Frame;
import com.scenevibe.tvcompanionpoc.ad.MultiPlatformObservationLog.Scalars;
import org.json.JSONArray;

/**
 * Public numeric differences for an observation campaign, never ad inference.
 * Positions use the EXISTING sampler estimate at its capture time, retaining
 * both raw anchors separately. This utility neither extrapolates another clock
 * nor exposes an effect decision. Missing, stale, cross-session, identity or
 * state transitions remain explicitly incomparable rather than fabricated zero.
 */
public final class MultiPlatformTransitionFacts {
    public static final long MAX_CAPTURE_AGE_MS=2500,MAX_PAIR_GAP_MS=5000;
    public static final long MAX_ANCHOR_AGE_MS=120000,RESIDUAL_THRESHOLD_MS=5000;

    /** No instance or mutable state is needed; all inputs are already-read public scalars. */
    private MultiPlatformTransitionFacts(){}

    /** Return bounded differences with nullable expectations and an explicit comparison reason. */
    public static Scalars compare(Frame current,Frame previous,long now){
        String reason=reason(current,previous,now);
        boolean comparable="COMPARABLE".equals(reason);
        long elapsed=previous==null?-1:current.capturedMs-previous.capturedMs;
        Double delta=previous==null || current.estimatedMs<0 || previous.estimatedMs<0?null:
                current.estimatedMs-(double)previous.estimatedMs;
        Double expected=comparable?(previous.state==3?elapsed*(double)previous.speed:0d):null;
        Double residual=comparable?delta-expected:null;
        return new Scalars().put("comparable",comparable).put("reason",reason)
                .put("positionBasis","EXISTING_SAMPLER_ESTIMATE_NOT_NEW_CLOCK")
                .put("deltaElapsedMs",elapsed<0?org.json.JSONObject.NULL:elapsed)
                .put("deltaPositionMs",delta==null?org.json.JSONObject.NULL:delta)
                .put("expectedPositionDeltaMs",expected==null?org.json.JSONObject.NULL:expected)
                .put("positionResidualMs",residual==null?org.json.JSONObject.NULL:residual)
                .put("previousStateCode",previous==null?org.json.JSONObject.NULL:previous.state)
                .put("discontinuity",residual!=null && Math.abs(residual)>RESIDUAL_THRESHOLD_MS)
                .put("thresholdMs",RESIDUAL_THRESHOLD_MS);
    }

    /** Explain why two observations cannot describe one stable public position progression. */
    private static String reason(Frame current,Frame previous,long now){
        if(previous==null)return "FIRST_SESSION_SAMPLE";
        if(current.session!=previous.session)return "DIFFERENT_SESSION";
        long elapsed=current.capturedMs-previous.capturedMs;
        if(elapsed<=0 || elapsed>MAX_PAIR_GAP_MS)return "NONCOMPARABLE_CAPTURE_TIME";
        if(now<current.capturedMs || now-current.capturedMs>MAX_CAPTURE_AGE_MS)return "STALE_CAPTURE";
        if(current.identityChanged)return "IDENTITY_CHANGED";
        if(current.durationMs>0 && previous.durationMs>0 && current.durationMs!=previous.durationMs)return "DURATION_CHANGED";
        if(current.positionMs<0 || previous.positionMs<0 || current.estimatedMs<0 || previous.estimatedMs<0)return "POSITION_UNAVAILABLE";
        if(current.anchorAgeMs<0 || previous.anchorAgeMs<0)return "ANCHOR_UNAVAILABLE";
        if(current.anchorAgeMs>MAX_ANCHOR_AGE_MS || previous.anchorAgeMs>MAX_ANCHOR_AGE_MS)return "OLD_ANCHOR";
        if(!Float.isFinite(current.speed) || !Float.isFinite(previous.speed))return "SPEED_UNAVAILABLE";
        if(current.state!=previous.state)return "STATE_CHANGED";
        if(current.speed!=previous.speed)return "SPEED_CHANGED";
        if(current.state!=3 && current.state!=2 && current.state!=1)return "STATE_NOT_PROGRESS_COMPARABLE";
        return "COMPARABLE";
    }

    /** List observable changes without assigning advertisement meaning to any transition. */
    public static JSONArray changes(Frame current,Frame previous,long now){
        JSONArray out=new JSONArray();
        if(previous==null){out.put("SESSION_APPEARED");return out;}
        if(current.state!=previous.state){out.put("PLAYBACK_STATE_CHANGED");
            if(current.state==3 && (previous.state==2 || previous.state==6))out.put("PLAYBACK_RESUMED");}
        if(Float.compare(current.speed,previous.speed)!=0)out.put("SPEED_CHANGED");
        if(current.identityChanged)out.put("MEDIA_IDENTITY_CHANGED");
        if(current.metadataPresent!=previous.metadataPresent || current.metadataCallbacks!=previous.metadataCallbacks
                || !java.util.Objects.equals(current.metadataKeys,previous.metadataKeys))out.put("METADATA_CHANGED");
        if(current.marker!=previous.marker || current.markerType!=previous.markerType)out.put("MARKER_CHANGED");
        if(current.positionMs>=0 && current.positionMs<=1000 && previous.positionMs>10000)out.put("POSITION_RESET");
        Scalars facts=compare(current,previous,now);
        if(facts.optBoolean("discontinuity"))out.put(current.estimatedMs>previous.estimatedMs?"POSITION_JUMP_FORWARD":"POSITION_JUMP_BACKWARD");
        return out;
    }
}
