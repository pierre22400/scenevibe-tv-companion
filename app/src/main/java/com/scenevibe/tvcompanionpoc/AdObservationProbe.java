package com.scenevibe.tvcompanionpoc;

import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import com.scenevibe.tvcompanionpoc.ad.AdEvidenceDetector;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.json.JSONObject;

/**
 * Opt-in public MediaController instrumentation, sharing the existing sampler.
 * This experiment never sends a transport command, reads a screen, changes a
 * commentary gate or supplies a content clock. Only bounded scalars and known
 * public key names enter logcat; titles, IDs, unknown key names/values, artwork,
 * custom event payloads, credentials and session tokens stay out of diagnostics.
 * Candidate AD_START/AD_END describe a publisher flag contract, not Sony pixels.
 * AD-DETECTION 002 optionally forwards those same facts to a separate explicit
 * SceneVibe protection consumer; the historical observation-only mode retains
 * its absence of commentary authority. No reader ever controls the video app.
 */
final class AdObservationProbe implements MediaSessionProbe.ControllerObserver {
    /** Optional protection consumer shares reads; annotations never reach this interface. */
    interface ProtectionSink {
        /** Inhibit promptly on a public positive callback; negative callbacks cannot release effects. */
        void positive(Object session,long capturedMs);
        /** Confirm/requalify from the same unambiguous already-read MEDIA snapshots. */
        void sample(AdEvidenceDetector.Sample sample,long now);
    }
    static final String AD_KEY="android.media.metadata.ADVERTISEMENT";
    private static final String TAG="SceneVibeAdProbe";
    private final String targetPackage;
    private final Predicate<MediaSessionProbe.Snapshot> compatible;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AdEvidenceDetector detector=new AdEvidenceDetector();
    private final Map<Object,Entry> entries=new LinkedHashMap<>();
    private boolean closed,overflow,diagnostics=true;
    private ProtectionSink protection;
    private int ordinal,sequence,annotations;
    private long lastCapture=-1;
    private String lastState="";

    /** Bind only the already-admitted target and matcher; construction does not start a second probe. */
    AdObservationProbe(String targetPackage,Predicate<MediaSessionProbe.Snapshot> compatible){
        this(targetPackage,compatible,true);
    }
    /** Protection-only reads do not silently opt the owner into passive diagnostic recording. */
    AdObservationProbe(String targetPackage,Predicate<MediaSessionProbe.Snapshot> compatible,boolean diagnostics){
        this.targetPackage=targetPackage;this.compatible=compatible;
        this.diagnostics=diagnostics;
        emit("ENABLED",new JSONObject());
    }

    /** Attach an independent gate without enabling diagnostic logging or owner annotations. */
    void setProtectionSink(ProtectionSink sink){protection=sink;}

    /** Switch passive logging independently of the gate, retaining existing controller callbacks. */
    void setDiagnosticsEnabled(boolean enabled){diagnostics=enabled;}

    /** Keep opaque controller identity and callback bookkeeping only in process memory. */
    private final class Entry {
        final MediaController controller;
        final int number=++ordinal;
        final MediaController.Callback callback;
        MediaSessionProbe.Snapshot snapshot;
        MediaMetadata metadata;
        PlaybackState playback;
        AdEvidenceDetector.Marker marker=AdEvidenceDetector.Marker.ABSENT;
        boolean seen,dirty=true,destroyed;
        long lastDetails=-1,lastLog=-1;
        int metadataCallbacks,playbackCallbacks,extrasCallbacks,audioCallbacks,ignoredEvents;
        String oldMediaId,oldTitle,oldSubtitle,lastCallbackShape="";
        AdEvidenceDetector.Marker lastCallbackMarker;
        long oldDuration=-1;
        boolean identityChanged;
        JSONObject details=new JSONObject();

        /** Register only documented read callbacks on the existing main Looper. */
        Entry(MediaController controller){
            this.controller=controller;
            callback=new MediaController.Callback(){
                /** Retain only the public flag's presence/value category at metadata transitions. */
                @Override public void onMetadataChanged(MediaMetadata value){if(closed)return;metadataCallbacks++;dirty=true;
                    AdEvidenceDetector.Marker marker=readMarker(value);String shape=metadataShape(value);
                    if(marker==AdEvidenceDetector.Marker.AD && protection!=null && !destroyed
                            && entries.get(controller.getSessionToken())==Entry.this)
                        protection.positive(controller.getSessionToken(),SystemClock.elapsedRealtime());
                    if(marker!=lastCallbackMarker || !shape.equals(lastCallbackShape)){
                        emit("METADATA_CALLBACK",facts("session",number,"marker",marker.name(),"keys",shape));lastCallbackMarker=marker;lastCallbackShape=shape;}}
                /** Count state publication without using the callback as a scheduler or ad detector. */
                @Override public void onPlaybackStateChanged(PlaybackState value){if(!closed){playbackCallbacks++;dirty=true;}}
                /** Extra bundles are inventoried on the next bounded read, never interpreted as private protocol. */
                @Override public void onExtrasChanged(Bundle value){if(!closed){extrasCallbacks++;dirty=true;}}
                /** Audio route changes are observations, not ad confirmation. */
                @Override public void onAudioInfoChanged(MediaController.PlaybackInfo value){if(!closed){audioCallbacks++;dirty=true;}}
                /** Android reserves custom event handling for owned sessions; ignore names and payloads. */
                @Override public void onSessionEvent(String name,Bundle value){if(!closed){ignoredEvents++;dirty=true;}}
                /** Destruction loses evidence; it never establishes the end of an advertisement. */
                @Override public void onSessionDestroyed(){if(!closed){destroyed=true;dirty=true;}}
            };
            controller.registerCallback(callback,main);
        }

        /** Release a read-only callback even if the publisher has already disappeared. */
        void detach(){try{controller.unregisterCallback(callback);}catch(RuntimeException ignored){/* Already unavailable. */}}
    }

    /**
     * Read a present numeric flag according to the public AndroidX metadata contract.
     * Framework lint's closed built-in key list predates this interoperable key;
     * only this documented extension read is exempted, never API/permission checks.
     */
    @android.annotation.SuppressLint("WrongConstant")
    static AdEvidenceDetector.Marker readMarker(MediaMetadata metadata){
        try{if(metadata==null || !metadata.containsKey(AD_KEY))return AdEvidenceDetector.Marker.ABSENT;
            // The framework getLong getter coerces wrong types and null to zero.
            // Use the public AndroidX bundle adapter solely to validate this key's
            // actual type; no private API, extra controller or payload export.
            Object value=android.support.v4.media.MediaMetadataCompat.fromMediaMetadata(metadata).getBundle().get(AD_KEY);
            if(!(value instanceof Long))return AdEvidenceDetector.Marker.INVALID;
            return ((Long)value)==0?AdEvidenceDetector.Marker.CONTENT:AdEvidenceDetector.Marker.AD;
        }catch(RuntimeException malformed){return AdEvidenceDetector.Marker.INVALID;}
    }

    /** Publish only a closed set of standard key names and a count of unexamined publisher keys. */
    static String metadataShape(MediaMetadata metadata){
        if(metadata==null)return "ABSENT";
        try{int known=0;List<String> present=new ArrayList<>();
            for(String key:new String[]{AD_KEY,MediaMetadata.METADATA_KEY_MEDIA_ID,MediaMetadata.METADATA_KEY_TITLE,
                    MediaMetadata.METADATA_KEY_DISPLAY_TITLE,MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
                    MediaMetadata.METADATA_KEY_DURATION,MediaMetadata.METADATA_KEY_ART,MediaMetadata.METADATA_KEY_ALBUM_ART,
                    MediaMetadata.METADATA_KEY_DISPLAY_ICON,MediaMetadata.METADATA_KEY_MEDIA_URI}){
                if(metadata.containsKey(key)){known++;present.add(key.substring(key.lastIndexOf('.')+1));}}
            return String.join(",",present)+";otherKeys="+Math.max(0,metadata.size()-known);
        }catch(RuntimeException unreadable){return "UNREADABLE";}
    }

    /** Inventory an extras bundle without serializing any unknown key name or value. */
    static String extrasShape(Bundle extras){
        if(extras==null)return "ABSENT";
        try{String flag="ABSENT";if(extras.containsKey(AD_KEY)){Object value=extras.get(AD_KEY);
                flag=value instanceof Long?((Long)value==0?"EXPLICIT_ZERO":"EXPLICIT_NONZERO"):"WRONG_TYPE";}
            return "keys="+extras.size()+";standardAdKey="+flag;
        }catch(RuntimeException unreadable){return "UNREADABLE";}
    }

    /** Observe already-read snapshots only for the admitted publisher; bound retained sessions to four. */
    @Override public void onController(MediaController controller,MediaSessionProbe.Snapshot snapshot,MediaMetadata metadata,PlaybackState playback){
        if(closed || !targetPackage.equals(snapshot.packageName))return;
        Entry entry=entries.get(snapshot.sessionKey);
        if(entry==null){if(entries.size()>=4){overflow=true;return;}entry=new Entry(controller);entries.put(snapshot.sessionKey,entry);}
        entry.seen=true;entry.snapshot=snapshot;entry.metadata=metadata;entry.playback=playback;entry.marker=readMarker(metadata);
        entry.identityChanged=entry.oldMediaId!=null && (!Objects.equals(entry.oldMediaId,snapshot.mediaId)
                || !Objects.equals(entry.oldTitle,snapshot.title) || !Objects.equals(entry.oldSubtitle,snapshot.subtitle)
                || entry.oldDuration!=snapshot.durationMs);
        entry.oldMediaId=snapshot.mediaId;entry.oldTitle=snapshot.title;entry.oldSubtitle=snapshot.subtitle;entry.oldDuration=snapshot.durationMs;
        long now=SystemClock.elapsedRealtime();
        if(diagnostics && (entry.lastDetails<0 || now-entry.lastDetails>=1000)){entry.details=publicDetails(entry);entry.lastDetails=now;entry.dirty=false;}
    }

    /** Read public API families independently; a disappearing field does not erase other observations. */
    private JSONObject publicDetails(Entry entry){
        long began=SystemClock.elapsedRealtime();JSONObject out=new JSONObject();
        try{put(out,"sessionExtras",extrasShape(entry.controller.getExtras()));}catch(RuntimeException refused){put(out,"sessionExtras","UNAVAILABLE");}
        if(!entry.details.has("sessionInfo")){
            try{put(out,"sessionInfo",Build.VERSION.SDK_INT>=29?extrasShape(entry.controller.getSessionInfo()):"API_BELOW_29");}
            catch(RuntimeException refused){put(out,"sessionInfo","UNAVAILABLE");}
        }else put(out,"sessionInfo",entry.details.optString("sessionInfo"));
        try{MediaController.PlaybackInfo info=entry.controller.getPlaybackInfo();
            if(info!=null){put(out,"playbackType",info.getPlaybackType());put(out,"volumeControl",info.getVolumeControl());
                if(info.getAudioAttributes()!=null){put(out,"audioUsage",info.getAudioAttributes().getUsage());put(out,"audioContentType",info.getAudioAttributes().getContentType());}}}
        catch(RuntimeException refused){put(out,"audioInfo","UNAVAILABLE");}
        put(out,"readCostMs",SystemClock.elapsedRealtime()-began);put(out,"detailsCapturedMs",began);return out;
    }

    /** Classify one unambiguous target batch without changing the historical selected MEDIA snapshot. */
    @Override public void onSelection(MediaSessionProbe.Snapshot selected){
        if(closed)return;
        Entry only=null;int count=0;
        var iterator=entries.values().iterator();
        while(iterator.hasNext()){Entry entry=iterator.next();if(!entry.seen || entry.destroyed){entry.detach();iterator.remove();continue;}
            entry.seen=false;only=entry;count++;}
        long now=SystemClock.elapsedRealtime();lastCapture=now;
        boolean single=count==1 && !overflow && selected!=null && Objects.equals(only.snapshot.sessionKey,selected.sessionKey);
        overflow=false;
        if(!single){if(protection!=null)protection.sample(null,now);transition(detector.observe(null,now));return;}
        MediaSessionProbe.Snapshot sample=only.snapshot;
        boolean identity=compatible.test(sample);
        boolean clock=sample.positionMs>=0 && sample.estimatedPositionMs>=0 && sample.updateAgeMs>=0
                && Float.isFinite(sample.speed) && sample.speed>0;
        AdEvidenceDetector.Sample captured=new AdEvidenceDetector.Sample(sample.sessionKey,sample.capturedAtMs,
                sample.estimatedPositionMs,sample.speed,true,identity,sample.state==PlaybackState.STATE_PLAYING,clock,only.marker);
        if(protection!=null)protection.sample(captured,now);
        AdEvidenceDetector.Decision decision=detector.observe(captured,now);
        boolean changed=transition(decision);
        if(changed || only.identityChanged || only.lastLog<0 || now-only.lastLog>=1000){
            JSONObject frame=facts("session",only.number,"marker",only.marker.name(),"state",sample.stateName,
                    "rawMs",sample.positionMs,"selectedMs",sample.estimatedPositionMs,"anchorAgeMs",sample.updateAgeMs,
                    "speed",sample.speed,"durationMs",sample.durationMs,"identityCompatible",identity,"identityChanged",only.identityChanged,
                    "metadataKeys",metadataShape(only.metadata),"details",only.details,"metadataCallbacks",only.metadataCallbacks,
                    "playbackCallbacks",only.playbackCallbacks,"extrasCallbacks",only.extrasCallbacks,"audioCallbacks",only.audioCallbacks,
                    "ignoredCustomEvents",only.ignoredEvents,"candidate",decision.state.name());
            PlaybackState state=only.playback;
            if(state!=null){put(frame,"actions",state.getActions());put(frame,"bufferedMs",state.getBufferedPosition());
                put(frame,"playbackError",state.getState()==PlaybackState.STATE_ERROR);put(frame,"playbackExtras",extrasShape(state.getExtras()));}
            emit("PUBLIC_SAMPLE",frame);only.lastLog=now;
        }
    }

    /** Report changes and edge counters, never a repeated candidate AD_START on a status query. */
    private boolean transition(AdEvidenceDetector.Decision decision){String state=decision.state+":"+decision.reason;
        if(state.equals(lastState) && decision.edge==AdEvidenceDetector.Edge.NONE)return false;
        lastState=state;emit("CANDIDATE",facts("candidate",decision.state.name(),"reason",decision.reason.name(),
                "edge",decision.edge.name(),"adStarts",decision.adStarts,"adEnds",decision.adEnds));return true;}

    /** Lack of observations is uncertainty; release old callbacks instead of retaining publisher sessions. */
    @Override public void onUnavailable(){if(closed)return;for(Entry entry:entries.values())entry.detach();entries.clear();lastCapture=-1;
        if(protection!=null)protection.sample(null,SystemClock.elapsedRealtime());
        transition(detector.observe(null,SystemClock.elapsedRealtime()));}

    /** Record a user observation separately; it is never passed to the candidate or commentary runtime. */
    void annotate(String marker){if(closed || !diagnostics)throw new IllegalStateException("OBSERVATION_DISABLED");
        if(!java.util.Arrays.asList("AD_VISIBLE","CONTENT_VISIBLE","NORMAL_SEEK","NORMAL_PAUSE").contains(marker))throw new IllegalArgumentException("ANNOTATION_REFUSED");
        annotations++;emit("OWNER_ANNOTATION",facts("annotation",marker,"origin","PHONE_OWNER_OBSERVATION_NOT_AUTOMATIC_DETECTION"));}

    /** Return only experimental state, with no implicit playback authority and no stored owner content. */
    JSONObject status(){if(!closed && lastCapture>=0 && SystemClock.elapsedRealtime()-lastCapture>2500)transition(detector.unavailable());
        int metadata=0,playback=0,extras=0,ignored=0;for(Entry entry:entries.values()){
            metadata+=entry.metadataCallbacks;playback+=entry.playbackCallbacks;extras+=entry.extrasCallbacks;ignored+=entry.ignoredEvents;}
        AdEvidenceDetector.Decision value=detector.status();return facts("enabled",!closed && diagnostics,"mode","OBSERVATION_ONLY_NO_COMMENT_GATE",
                "candidate",value.state.name(),"reason",value.reason.name(),"adStarts",value.adStarts,"adEnds",value.adEnds,
                "annotations",annotations,"sessionCount",entries.size(),"metadataCallbacks",metadata,"playbackCallbacks",playback,
                "extrasCallbacks",extras,"ignoredCustomEvents",ignored);}

    /** Fully detach the optional reader; no cache, channel, manual pause or event consumption is reset. */
    @Override public void close(){if(closed)return;closed=true;for(Entry entry:entries.values())entry.detach();entries.clear();emit("DISABLED",new JSONObject());}

    /** Build closed diagnostics without ever calling Bundle.toString or controller/token.toString. */
    private static JSONObject facts(Object... pairs){JSONObject result=new JSONObject();for(int i=0;i<pairs.length;i+=2)put(result,(String)pairs[i],pairs[i+1]);return result;}

    /** Contain JSON representation failures within this diagnostic module. */
    private static void put(JSONObject object,String key,Object value){try{object.put(key,value);}catch(Exception ignored){/* Unsupported diagnostic scalar is omitted. */}}

    /** Correlate device elapsed/wall clocks; a wall timestamp never becomes a content-time estimate. */
    private void emit(String event,JSONObject fields){if(!diagnostics)return;put(fields,"event",event);put(fields,"sequence",++sequence);put(fields,"elapsedMs",SystemClock.elapsedRealtime());
        put(fields,"wallTimeMs",System.currentTimeMillis());put(fields,"scope","PUBLIC_API_CANDIDATE_NOT_PHYSICAL_AD_PROOF");Log.i(TAG,fields.toString());}
}
