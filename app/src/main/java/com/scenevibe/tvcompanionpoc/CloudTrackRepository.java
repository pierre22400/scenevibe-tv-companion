package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import org.json.JSONArray;

/** Persists one validated text-only runtime track and its revision in app-private storage. */
final class CloudTrackRepository {
    interface Storage {
        String get(String key);
        boolean save(long revision, String json);
        boolean saveAck(long revision);
        void clear();
    }
    private final Storage storage;
    /** Wraps SharedPreferences.commit so a successful return means both fields are durable. */
    CloudTrackRepository(Context context) {
        SharedPreferences prefs=context.getApplicationContext().getSharedPreferences("cloud_track",Context.MODE_PRIVATE);
        storage=new Storage() {
            @Override public String get(String key) {return prefs.getString(key,null);}
            @Override public boolean save(long revision,String json) {
                return prefs.edit().putString("revision",String.valueOf(revision)).putString("runtime",json).commit();
            }
            @Override public boolean saveAck(long revision) {
                return prefs.edit().putString("ackRevision",String.valueOf(revision)).commit();
            }
            @Override public void clear() {prefs.edit().clear().commit();}
        };
    }
    /** Injectable persistence boundary for deterministic JVM tests. */
    CloudTrackRepository(Storage storage) {this.storage=storage;}
    /** Reads and validates cached state; corruption never reaches the scheduler. */
    synchronized long restore(MediaSyncedTrackScheduler scheduler) {
        try {
            String json=storage.get("runtime"), raw=storage.get("revision");
            if(json==null||raw==null)return 0;
            long revision=Long.parseLong(raw);
            if(revision<1)throw new IllegalArgumentException("Invalid cache revision");
            ScheduledTrack track=parse(json);
            scheduler.load(track);
            return revision;
        } catch(Exception invalid) {storage.clear();return 0;}
    }
    /** Validates completely, commits atomically and only then changes the live scheduler. */
    synchronized boolean install(long revision,String runtimeJson,MediaSyncedTrackScheduler scheduler) {
        if(revision<1||runtimeJson==null||runtimeJson.length()>400_000)return false;
        try {
            long prior=0;
            String raw=storage.get("revision");
            if(raw!=null)prior=Long.parseLong(raw);
            if(revision<=prior)return false;
            ScheduledTrack track=parse(runtimeJson);
            if(!storage.save(revision,runtimeJson))return false;
            scheduler.load(track);
            return true;
        } catch(Exception invalid) {return false;}
    }
    /** Returns the last durable revision without trusting a corrupt number. */
    synchronized long revision() {
        try {return Long.parseLong(storage.get("revision"));}
        catch(Exception invalid) {return 0;}
    }
    /** Revisions lacking a confirmed ACK remain eligible for redelivery and retry. */
    synchronized long acknowledged() {
        try {return Long.parseLong(storage.get("ackRevision"));}
        catch(Exception invalid) {return 0;}
    }
    /** Records an ACK only after the server confirms it, without changing the track JSON. */
    synchronized boolean markAcknowledged(long revision) {
        return revision==revision() && storage.saveAck(revision);
    }
    /** Applies the exact parser used by LAN; cloud runtime is text-only. */
    private ScheduledTrack parse(String json) throws Exception {
        JSONObject envelope=new JSONObject(json);
        JSONArray comments=envelope.optJSONArray("comments");
        if(comments==null)throw new IllegalArgumentException("Invalid cloud comments");
        for(int i=0;i<comments.length();i++) {
            JSONObject item=comments.optJSONObject(i);
            if(item==null||item.has("media"))throw new IllegalArgumentException("Cloud media unsupported");
        }
        return TrackParser.parse(envelope, media -> {
            throw new TrackParser.Invalid("invalid_media","Cloud v1 is text-only");
        });
    }
}
