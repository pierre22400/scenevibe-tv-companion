package com.scenevibe.tvcompanionpoc;

import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import com.scenevibe.tvcompanionpoc.ad.MultiPlatformObservationLog;
import com.scenevibe.tvcompanionpoc.ad.MultiPlatformObservationLog.Compatibility;
import com.scenevibe.tvcompanionpoc.ad.MultiPlatformObservationLog.Frame;
import com.scenevibe.tvcompanionpoc.ad.MultiPlatformObservationLog.Platform;
import com.scenevibe.tvcompanionpoc.ad.MultiPlatformObservationLog.ValueType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.json.JSONObject;

/**
 * Netflix/Disney+ read-only channel on the existing MediaSessionProbe sampler.
 * This observer has no protection sink, runtime, renderer, audio or transport
 * reference. Real packages are an exact allowlist; a separately fenced owned
 * publisher seam exists only in the disposable debug Android test build.
 */
final class MultiPlatformAdObservationProbe implements MediaSessionProbe.ControllerObserver {
    private final MultiPlatformObservationLog journal;
    private final String targetPackage;
    private final Function<MediaSessionProbe.Snapshot,Compatibility> compatibility;
    private final boolean ownedFixture;
    private final Map<Object,Entry> entries=new LinkedHashMap<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean closed;
    private int ordinal,allCount,targetCount;

    /** A production campaign can observe only one of the two authorized platform packages. */
    MultiPlatformAdObservationProbe(Platform platform,Function<MediaSessionProbe.Snapshot,Compatibility> compatibility){
        this(platform,platform.packageName,compatibility,false);
    }
    /** The own-publisher test seam never claims an official Netflix or Disney+ Android session. */
    static MultiPlatformAdObservationProbe ownedFixture(Platform platform){
        if(!BuildConfig.DEBUG || !BuildConfig.CLOUD_ORIGIN.isEmpty())throw new IllegalStateException("DEBUG_OWN_PUBLISHER_ONLY");
        return new MultiPlatformAdObservationProbe(platform,BuildConfig.APPLICATION_ID,snapshot->Compatibility.NOT_EVALUATED,true);
    }
    /** Construct only in the two explicit entry points; package aliases cannot come from HTTP requests. */
    private MultiPlatformAdObservationProbe(Platform platform,String targetPackage,Function<MediaSessionProbe.Snapshot,Compatibility> compatibility,boolean ownedFixture){
        this.journal=new MultiPlatformObservationLog(platform,SystemClock.elapsedRealtime());this.targetPackage=targetPackage;this.compatibility=compatibility;this.ownedFixture=ownedFixture;
        emit(journal.started(SystemClock.elapsedRealtime(),System.currentTimeMillis()));
    }
    /** Keep opaque session keys and programme identity only in memory, never in a log object. */
    private final class Entry {
        final MediaController controller;
        final int number=++ordinal;
        final MediaController.Callback callback;
        MediaSessionProbe.Snapshot snapshot;
        MediaMetadata metadata;
        PlaybackState playback;
        boolean seen,destroyed,hadIdentity,identityChanged;
        String oldId,oldTitle,oldSubtitle,lastCallback="",lastPlayback="";
        long oldDuration=-1;
        long extrasReadAt=-1;
        String sessionExtras="ABSENT";
        int metadataCallbacks,playbackCallbacks,extrasCallbacks,ignoredEvents;
        /** Register documented read callbacks, without obtaining a transport control port. */
        Entry(MediaController controller){this.controller=controller;
            callback=new MediaController.Callback(){
                /** Retain the typed standardized flag at real metadata callbacks, without private values. */
                @Override public void onMetadataChanged(MediaMetadata value){if(!current())return;metadataCallbacks++;
                    var marker=AdObservationProbe.readMarker(value);var type=markerType(value);String shape=marker+":"+type+":"+AdObservationProbe.metadataShape(value);
                    if(!shape.equals(lastCallback)){lastCallback=shape;emit(journal.metadataCallback(number,marker,type,value!=null,SystemClock.elapsedRealtime(),System.currentTimeMillis()));}}
                /** State publication is a counter; PAUSED/BUFFERING never becomes advertising proof. */
                @Override public void onPlaybackStateChanged(PlaybackState value){if(!current())return;playbackCallbacks++;
                    int state=value==null?0:value.getState();long position=value==null?-1:value.getPosition();
                    float speed=value==null?Float.NaN:value.getPlaybackSpeed();long anchor=value==null?-1:value.getLastPositionUpdateTime();
                    String shape=state+":"+position+":"+speed+":"+anchor;
                    if(!shape.equals(lastPlayback)){lastPlayback=shape;emit(journal.playbackCallback(number,state,position,speed,anchor,SystemClock.elapsedRealtime(),System.currentTimeMillis()));}}
                /** Unknown extra names and values are never exported. */
                @Override public void onExtrasChanged(android.os.Bundle value){if(current())extrasCallbacks++;}
                /** Custom event names/payloads are not a documented advertising protocol. */
                @Override public void onSessionEvent(String name,android.os.Bundle value){if(current())ignoredEvents++;}
                /** Session destruction loses observations and cannot establish content resumption. */
                @Override public void onSessionDestroyed(){if(current())destroyed=true;}
            };controller.registerCallback(callback,main);
        }
        /** Late callbacks from a detached controller cannot enter a later campaign. */
        private boolean current(){return !closed && !destroyed && entries.get(controller.getSessionToken())==this;}
        /** Unregister only this observer's callback, including when the publisher has disappeared. */
        void detach(){try{controller.unregisterCallback(callback);}catch(RuntimeException unavailable){/* No fallback authority. */}}
    }
    /** Report the real framework type without numeric getter coercion or private class/value serialization. */
    static ValueType markerType(MediaMetadata metadata){try{
        if(metadata==null || !metadata.containsKey(AdObservationProbe.AD_KEY))return ValueType.ABSENT;
        Object value=android.support.v4.media.MediaMetadataCompat.fromMediaMetadata(metadata).getBundle().get(AdObservationProbe.AD_KEY);
        if(value==null)return ValueType.NULL;if(value instanceof Long)return ValueType.LONG;
        if(value instanceof String || value instanceof CharSequence)return ValueType.STRING;
        if(value instanceof Boolean)return ValueType.BOOLEAN;return ValueType.OTHER;
    }catch(RuntimeException refused){return ValueType.UNREADABLE;}}
    /** Inspect the already-read batch; the controller's public owner must agree with the snapshot. */
    @Override public void onController(MediaController controller,MediaSessionProbe.Snapshot snapshot,MediaMetadata metadata,PlaybackState playback){
        if(closed)return;
        if(snapshot==null || !targetPackage.equals(snapshot.packageName) || !targetPackage.equals(controller.getPackageName()))return;
        targetCount++;Entry entry=entries.get(snapshot.sessionKey);
        if(entry==null){if(entries.size()>=4)return;entry=new Entry(controller);entries.put(snapshot.sessionKey,entry);}
        entry.seen=true;entry.snapshot=snapshot;entry.metadata=metadata;entry.playback=playback;
        boolean identity=!snapshot.mediaId.isEmpty() || !snapshot.title.isEmpty() || !snapshot.subtitle.isEmpty();
        entry.identityChanged=entry.hadIdentity && (!Objects.equals(entry.oldId,snapshot.mediaId)
                || !Objects.equals(entry.oldTitle,snapshot.title) || !Objects.equals(entry.oldSubtitle,snapshot.subtitle) || entry.oldDuration!=snapshot.durationMs);
        entry.hadIdentity=identity;entry.oldId=snapshot.mediaId;entry.oldTitle=snapshot.title;entry.oldSubtitle=snapshot.subtitle;entry.oldDuration=snapshot.durationMs;
    }
    /** Keep the API's full session count even if a particular controller read later fails. */
    @Override public void onBatch(int activeSessions){if(!closed)allCount=activeSessions;}
    /** Finish a diagnostic batch without replacing the historical selected session or invoking its listener. */
    @Override public void onSelection(MediaSessionProbe.Snapshot selected){if(closed)return;
        ArrayList<Frame> frames=new ArrayList<>();var iterator=entries.values().iterator();
        while(iterator.hasNext()){Entry entry=iterator.next();if(!entry.seen || entry.destroyed){entry.detach();iterator.remove();continue;}entry.seen=false;
            var s=entry.snapshot;Frame f=new Frame(entry.number,s.state,s.capturedAtMs,s.positionMs,s.estimatedPositionMs,s.speed,s.updateAgeMs,s.durationMs,
                    AdObservationProbe.readMarker(entry.metadata),markerType(entry.metadata));
            f.selected=selected!=null && Objects.equals(selected.sessionKey,s.sessionKey);f.metadataPresent=entry.metadata!=null;
            f.identityPresent=entry.hadIdentity;f.identityChanged=entry.identityChanged;f.compatibility=compatibility.apply(s);
            f.metadataCallbacks=entry.metadataCallbacks;f.playbackCallbacks=entry.playbackCallbacks;f.extrasCallbacks=entry.extrasCallbacks;f.ignoredEvents=entry.ignoredEvents;
            f.metadataKeys=AdObservationProbe.metadataShape(entry.metadata);
            long now=SystemClock.elapsedRealtime();
            if(entry.extrasReadAt<0 || now-entry.extrasReadAt>=1000){entry.extrasReadAt=now;
                try{entry.sessionExtras=AdObservationProbe.extrasShape(entry.controller.getExtras());}catch(RuntimeException unavailable){entry.sessionExtras="UNAVAILABLE";}}
            f.sessionExtras=entry.sessionExtras;
            f.playbackExtras=AdObservationProbe.extrasShape(entry.playback==null?null:entry.playback.getExtras());frames.add(f);
        }
        for(JSONObject event:journal.capture(frames,allCount,targetCount,SystemClock.elapsedRealtime(),System.currentTimeMillis()))emit(event);
        allCount=0;targetCount=0;
    }
    /** Release read callbacks on permission/session loss; recover through later normal sampler batches. */
    @Override public void onUnavailable(){if(closed)return;for(Entry entry:entries.values())entry.detach();entries.clear();allCount=0;targetCount=0;
        emit(journal.unavailable(SystemClock.elapsedRealtime(),System.currentTimeMillis()));}
    /** Owner labels are written to their own campaign event stream and never to a detector. */
    void annotate(String label){emit(journal.annotate(label,SystemClock.elapsedRealtime(),System.currentTimeMillis()));}
    /** Expose a defensive diagnostic projection; reading it cannot affect a commentary gate. */
    JSONObject status(){return journal.status(SystemClock.elapsedRealtime()).put("publisherScope",ownedFixture?"OWN_FIXTURE_NOT_REAL_PLATFORM":"PUBLIC_PLATFORM_PACKAGE");}
    /** A disabled observer cannot keep callbacks, samples or labels alive. */
    @Override public void close(){if(closed)return;closed=true;for(Entry entry:entries.values())entry.detach();entries.clear();emit(journal.close(SystemClock.elapsedRealtime(),System.currentTimeMillis()));}
    /** Emit closed JSON scalars only, with an explicit synthetic publisher scope on native tests. */
    private void emit(JSONObject event){if(event!=null){try{event.put("publisherScope",ownedFixture?"OWN_FIXTURE_NOT_REAL_PLATFORM":"PUBLIC_PLATFORM_PACKAGE");}
        catch(org.json.JSONException invalid){throw new IllegalArgumentException("SCALAR_JSON_INVALID");}Log.i("SceneVibeMultiAd",event.toString());}}
}
