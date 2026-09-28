package com.scenevibe.tvcompanionpoc;

import android.graphics.Bitmap;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** One validated runtime track parser for both authenticated LAN and outbound cloud delivery. */
final class TrackParser {
    private static final long MAX_START_MS = 43_200_000L;
    private static final long MAX_MEDIA_DURATION_MS = 43_200_000L;
    /** Media is decoded by the existing LAN renderer; cloud text-only rejects any media. */
    interface MediaDecoder { Bitmap decode(JSONObject media) throws Invalid; }
    /** A safe, fixed protocol error with no submitted text or token. */
    static final class Invalid extends Exception {
        final String code;
        /** Captures a bounded protocol reason without raw input. */
        Invalid(String code, String message) {super(message);this.code=code;}
    }
    /** Validates the complete track before returning a schedulable immutable snapshot. */
    static ScheduledTrack parse(JSONObject json, MediaDecoder decoder) throws Invalid {
        if (!"scenevibe.track.v1".equals(json.optString("type")))
            throw new Invalid("invalid_type", "Expected scenevibe.track.v1");
        String trackId=json.optString("trackId", "").trim();
        String targetPackage=json.optString("targetPackage", "").trim();
        JSONObject media=json.optJSONObject("mediaIdentity");
        JSONArray comments=json.optJSONArray("comments");
        if (trackId.isEmpty()||trackId.length()>128||targetPackage.isEmpty()||targetPackage.length()>200
                ||media==null||comments==null||comments.length()<1||comments.length()>256)
            throw new Invalid("invalid_track", "Invalid trackId, targetPackage, mediaIdentity or comments");

        String platform=media.optString("platform","").trim();
        String videoId=media.optString("videoId","").trim();
        String title=media.optString("title","").trim();
        Object rawMediaDuration=media.opt("durationMs");
        if (!"prime_video".equals(platform)||videoId.isEmpty()||videoId.length()>256
                ||title.isEmpty()||title.length()>500||!(rawMediaDuration instanceof Number))
            throw new Invalid("invalid_media_identity","Invalid media identity");
        double mediaDurationDouble=((Number)rawMediaDuration).doubleValue();
        long mediaDurationMs=((Number)rawMediaDuration).longValue();
        if (!Double.isFinite(mediaDurationDouble)||mediaDurationDouble!=(double)mediaDurationMs
                ||mediaDurationMs<1||mediaDurationMs>MAX_MEDIA_DURATION_MS)
            throw new Invalid("invalid_media_identity","Invalid media duration");

        ArrayList<ScheduledTrack.Event> events=new ArrayList<>();
        Set<String> ids=new HashSet<>();
        for(int i=0;i<comments.length();i++) {
            JSONObject item=comments.optJSONObject(i);
            if(item==null)throw new Invalid("invalid_track_comment","Each comments item must be an object");
            String id=item.optString("id", "").trim(), text=item.optString("text", "").trim();
            long startMs=item.optLong("startMs",-1L), durationMs=item.optLong("durationMs",-1L);
            if(id.isEmpty()||id.length()>128||!ids.add(id)||text.isEmpty()||text.length()>1000
                    ||startMs<0||startMs>MAX_START_MS||durationMs<1000||durationMs>60000)
                throw new Invalid("invalid_track_comment","Invalid or duplicate id, text, startMs or durationMs");
            Bitmap mediaBitmap=null;
            JSONObject mediaJson=item.optJSONObject("media");
            if(mediaJson!=null)mediaBitmap=decoder.decode(mediaJson);
            events.add(new ScheduledTrack.Event(id,text,startMs,durationMs,mediaBitmap));
        }
        Object pause=json.opt("pauseFreezesDisplay");
        if(pause!=null&&!(pause instanceof Boolean))
            throw new Invalid("invalid_track","pauseFreezesDisplay must be a boolean");
        return new ScheduledTrack(trackId,targetPackage,
                new ScheduledTrack.MediaIdentity(platform,videoId,title,mediaDurationMs),
                events,Boolean.TRUE.equals(pause));
    }
    /** Utility class with no mutable state. */
    private TrackParser() {}
}
