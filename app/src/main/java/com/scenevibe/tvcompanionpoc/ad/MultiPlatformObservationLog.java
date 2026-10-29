package com.scenevibe.tvcompanionpoc.ad;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Bounded public-scalar journal for one explicitly selected experimental campaign.
 * This class owns no Android controller, commentary port, cache, preference or
 * detector. A positive flag is recorded, never promoted to physical ad proof.
 * Owner labels remain separate events. No programme string or token is accepted.
 */
public final class MultiPlatformObservationLog {
    /** Android's checked JSON API is contained here; only bounded, finite scalars are accepted. */
    public static final class Scalars extends JSONObject {
        /** Reject representation errors locally rather than leaking arbitrary diagnostic text. */
        @Override public Scalars put(String key,Object value){try{super.put(key,value);return this;}catch(org.json.JSONException invalid){throw new IllegalArgumentException("SCALAR_JSON_INVALID");}}
        /** Preserve the boolean overload across Android and desktop JSON implementations. */
        @Override public Scalars put(String key,boolean value){return put(key,Boolean.valueOf(value));}
        /** Preserve the integer overload across Android and desktop JSON implementations. */
        @Override public Scalars put(String key,int value){return put(key,Integer.valueOf(value));}
        /** Preserve monotonic and wall timestamps without narrowing to integer milliseconds. */
        @Override public Scalars put(String key,long value){return put(key,Long.valueOf(value));}
        /** Non-finite publisher values are explicitly unavailable, never invented zeroes. */
        @Override public Scalars put(String key,double value){return put(key,Double.isFinite(value)?Double.valueOf(value):JSONObject.NULL);}
    }
    public enum Platform {
        NETFLIX("com.netflix.ninja"), DISNEY_PLUS("com.disney.disneyplus");
        public final String packageName;
        /** Retain only the two explicitly authorized public package names. */
        Platform(String packageName){this.packageName=packageName;}
    }
    public enum ValueType { ABSENT, NULL, LONG, STRING, BOOLEAN, OTHER, UNREADABLE }
    public enum Compatibility { MATCH, MISMATCH, NOT_EVALUATED }

    /** A scalar-only snapshot; none of these fields can carry a media title or identity. */
    public static final class Frame {
        public final int session,state;
        public final long capturedMs,positionMs,estimatedMs,anchorAgeMs,durationMs;
        public final float speed;
        public final AdEvidenceDetector.Marker marker;
        public final ValueType markerType;
        public boolean selected,metadataPresent,identityPresent,identityChanged;
        public Compatibility compatibility=Compatibility.NOT_EVALUATED;
        public int metadataCallbacks,playbackCallbacks,extrasCallbacks,ignoredEvents;
        public String metadataKeys="ABSENT",sessionExtras="ABSENT",playbackExtras="ABSENT";
        /** Copy only public numeric facts and closed marker/type vocabulary. */
        public Frame(int session,int state,long capturedMs,long positionMs,long estimatedMs,float speed,
                     long anchorAgeMs,long durationMs,AdEvidenceDetector.Marker marker,ValueType markerType){
            this.session=session;this.state=state;this.capturedMs=capturedMs;this.positionMs=positionMs;
            this.estimatedMs=estimatedMs;this.speed=speed;this.anchorAgeMs=anchorAgeMs;this.durationMs=durationMs;
            this.marker=marker;this.markerType=markerType;
        }
    }
    private final Platform platform;
    private final long startedMs;
    private final Map<Integer,Frame> previous=new LinkedHashMap<>();
    private final Map<Integer,Long> logged=new LinkedHashMap<>();
    private JSONArray latest=new JSONArray();
    private boolean enabled=true;
    private int sequence,annotations,activeSessions,targetSessions;
    private long lastCapture=-1;
    private String selection="NO_TARGET_SESSION";

    /** Select one platform; a new campaign never inherits another platform's samples. */
    public MultiPlatformObservationLog(Platform platform){this(platform,0);}
    /** A public monotonic campaign coordinate permits deduplication of overlapping captures without an identity. */
    public MultiPlatformObservationLog(Platform platform,long startedMs){if(platform==null || startedMs<0)throw new IllegalArgumentException("PLATFORM_AND_MONOTONIC_START_REQUIRED");this.platform=platform;this.startedMs=startedMs;}
    /** Announce a new observation campaign without claiming that its platform published any session. */
    public JSONObject started(long now,long wall){return event("ENABLED",now,wall).put("mode","OBSERVATION_ONLY_NO_COMMENT_AUTHORITY");}
    /** Public package matching is exact; phones cannot submit arbitrary package names. */
    public boolean accepts(String packageName){return enabled && platform.packageName.equals(packageName);}
    /** Record a bounded batch without influencing the historical session selection or clock. */
    public List<JSONObject> capture(List<Frame> frames,int allCount,int targetCount,long now,long wall){
        List<JSONObject> out=new ArrayList<>();if(!enabled)return out;
        boolean countsChanged=lastCapture<0 || activeSessions!=allCount || targetSessions!=targetCount;
        activeSessions=allCount;targetSessions=targetCount;lastCapture=now;
        String next=targetCount==0?"NO_TARGET_SESSION":targetCount==1?"SINGLE_TARGET":"MULTIPLE_TARGET_SESSIONS";
        if(countsChanged || !next.equals(selection)){selection=next;out.add(event("SELECTION",now,wall).put("selection",selection)
                .put("activeSessionCount",allCount).put("targetSessionCount",targetCount));}
        latest=new JSONArray();Map<Integer,Frame> retained=new LinkedHashMap<>();
        for(Frame f:frames){if(retained.size()>=4)break;if(f==null || f.session<=0)continue;
            Frame old=previous.get(f.session);JSONObject row=projection(f,old,now);latest.put(row);retained.put(f.session,f);
            boolean changed=old==null || old.marker!=f.marker || old.markerType!=f.markerType || old.state!=f.state
                    || old.metadataPresent!=f.metadataPresent || f.identityChanged || old.selected!=f.selected;
            if(changed || now-logged.getOrDefault(f.session,-1000L)>=1000){
                out.add(event("PUBLIC_SAMPLE",now,wall).put("sample",row));logged.put(f.session,now);}
        }
        previous.clear();previous.putAll(retained);logged.keySet().retainAll(retained.keySet());return out;
    }
    /** Log a genuine callback's marker/type only; negative callbacks do not establish content return. */
    public JSONObject metadataCallback(int session,AdEvidenceDetector.Marker marker,ValueType type,long now,long wall){
        if(!enabled)return null;return event("METADATA_CALLBACK",now,wall).put("session",session)
                .put("marker",marker.name()).put("markerType",type.name());
    }
    /** Missing access/session information is explicit uncertainty, never AD_END. */
    public JSONObject unavailable(long now,long wall){if(!enabled)return null;
        boolean changed=!"ACCESS_OR_SESSIONS_UNAVAILABLE".equals(selection);selection="ACCESS_OR_SESSIONS_UNAVAILABLE";
        previous.clear();logged.clear();latest=new JSONArray();targetSessions=0;activeSessions=0;lastCapture=-1;
        return changed?event("UNAVAILABLE",now,wall):null;}
    /** Owner observations are comparison labels and cannot change automatic samples or counts. */
    public JSONObject annotate(String label,long now,long wall){
        if(!enabled)throw new IllegalStateException("OBSERVATION_DISABLED");
        if(!java.util.Arrays.asList("AD_VISIBLE","CONTENT_VISIBLE","NORMAL_SEEK","NORMAL_PAUSE").contains(label))
            throw new IllegalArgumentException("ANNOTATION_REFUSED");
        annotations++;return event("OWNER_ANNOTATION",now,wall).put("annotation",label)
                .put("targetSessionCount",targetSessions).put("origin","OWNER_LABEL_NOT_AUTOMATIC_SIGNAL");
    }
    /** Read-only UI status never renews monitoring, inserts a sample, or grants playback. */
    public Scalars status(long now){long age=lastCapture<0 || now<lastCapture?-1:now-lastCapture;
        return new Scalars().put("enabled",enabled).put("platform",platform.name()).put("package",platform.packageName).put("campaignStartedMs",startedMs)
                .put("observedPlatform",targetSessions==0?"NONE":platform.name()).put("selection",selection)
                .put("activeSessionCount",activeSessions).put("targetSessionCount",targetSessions)
                .put("sampleAgeMs",age).put("fresh",enabled && targetSessions>0 && age>=0 && age<=2500).put("annotations",annotations)
                .put("retainedSessionCount",latest.length()).put("truncated",targetSessions>latest.length())
                .put("mode","OBSERVATION_ONLY_NO_COMMENT_AUTHORITY").put("conclusion","INSUFFICIENT_EVIDENCE")
                .put("samples",copyLatest());}
    /** Return detached JSON so callers cannot mutate retained diagnostic samples. */
    private JSONArray copyLatest(){try{return new JSONArray(latest.toString());}catch(org.json.JSONException invalid){throw new IllegalStateException("SCALAR_COPY_INVALID");}}
    /** End the campaign in memory; no preference, cache or commentary state is touched. */
    public JSONObject close(long now,long wall){if(!enabled)return null;enabled=false;previous.clear();logged.clear();
        latest=new JSONArray();targetSessions=0;return event("DISABLED",now,wall);}
    /** Build a closed event envelope shared by callbacks, samples and owner labels. */
    private Scalars event(String event,long now,long wall){return new Scalars().put("event",event).put("sequence",++sequence).put("campaignStartedMs",startedMs)
            .put("platform",platform.name()).put("package",platform.packageName).put("elapsedMs",now).put("wallTimeMs",wall)
            .put("scope","PUBLIC_SCALARS_NOT_PHYSICAL_AD_PROOF");}
    /** Export only allowlisted numeric/boolean/enumerated facts; unknown metadata names never survive. */
    private JSONObject projection(Frame f,Frame old,long now){long elapsed=old==null?-1:f.capturedMs-old.capturedMs;
        Scalars row=new Scalars().put("session",f.session).put("state",stateName(f.state)).put("stateCode",f.state)
                .put("capturedAtMs",f.capturedMs).put("sampleAgeMs",now>=f.capturedMs?now-f.capturedMs:-1)
                .put("rawMs",f.positionMs).put("estimatedMs",f.estimatedMs).put("anchorAgeMs",f.anchorAgeMs)
                .put("durationMs",f.durationMs).put("speed",Float.isFinite(f.speed)?(double)f.speed:JSONObject.NULL)
                .put("marker",f.marker.name()).put("markerType",f.markerType.name()).put("metadataPresent",f.metadataPresent)
                .put("identityPresent",f.identityPresent).put("identityChanged",f.identityChanged)
                .put("identityCompatibility",f.compatibility.name()).put("selectedByHistoricalProbe",f.selected)
                .put("metadataCallbacks",f.metadataCallbacks).put("playbackCallbacks",f.playbackCallbacks)
                .put("extrasCallbacks",f.extrasCallbacks).put("ignoredCustomEvents",f.ignoredEvents)
                .put("metadataKeys",safeKeys(f.metadataKeys)).put("sessionExtras",safeExtras(f.sessionExtras))
                .put("playbackExtras",safeExtras(f.playbackExtras));
        if(old!=null && elapsed>0){double delta=f.positionMs-(double)old.positionMs;
            row.put("captureDeltaMs",elapsed).put("rawDeltaMs",f.positionMs>=0 && old.positionMs>=0?delta:JSONObject.NULL)
                .put("positionDiscontinuity",f.positionMs>=0 && old.positionMs>=0 && Math.abs(delta-elapsed*old.speed)>5000);}
        return row;
    }
    /** Do not trust a publisher-controlled string even when the caller labels it an inventory. */
    private static String safeKeys(String value){return value!=null && (value.equals("ABSENT") || value.equals("UNREADABLE")
            || value.matches("(?:(?:ADVERTISEMENT|MEDIA_ID|TITLE|DISPLAY_TITLE|DISPLAY_SUBTITLE|DURATION|ART|ALBUM_ART|DISPLAY_ICON|MEDIA_URI),?)*;otherKeys=[0-9]+"))?value:"OMITTED";}
    /** Extras expose only a count and the typed category of the same standardized key. */
    private static String safeExtras(String value){return value!=null && (value.equals("ABSENT") || value.equals("UNREADABLE")
            || value.equals("UNAVAILABLE") || value.matches("keys=[0-9]+;standardAdKey=(?:ABSENT|EXPLICIT_ZERO|EXPLICIT_NONZERO|WRONG_TYPE)"))?value:"OMITTED";}
    /** Android's documented state codes are recorded without turning any of them into an advertisement. */
    private static String stateName(int state){String[] names={"NONE","STOPPED","PAUSED","PLAYING","FAST_FORWARDING","REWINDING","BUFFERING","ERROR","CONNECTING","SKIPPING_TO_PREVIOUS","SKIPPING_TO_NEXT","SKIPPING_TO_QUEUE_ITEM"};
        return state>=0 && state<names.length?names[state]:"UNKNOWN";}
}
