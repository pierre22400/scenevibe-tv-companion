package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.calendar.MediaObservation;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pin the external adapter to the unchanged matcher and probe's already-selected values. */
public final class M5VideoObservationAdapterTest {
    /** Build a known eligible Video track without an installation parser. */
    private static ScheduledTrack track() throws Exception {return M5CDifferentialHarness.track(new JSONObject("{events:[{id:e,start:0,duration:1}]}"),new JSONObject());}
    /** Copy null as no-op, rather than constructing an unavailable observation. */
    @Test public void nullTrackAndSnapshot() throws Exception {assertNull(VideoMediaObservationAdapter.observe(null,M5CDifferentialHarness.snapshot(new JSONObject())));assertNull(VideoMediaObservationAdapter.observe(track(),null));}
    /** Only state PLAYING becomes true, even when position is valid in other states. */
    @Test public void everyPlaybackStateUsesExactPlayingClassification() throws Exception {for(int state=0;state<=11;state++){MediaObservation o=VideoMediaObservationAdapter.observe(track(),M5CDifferentialHarness.snapshot(new JSONObject().put("state",state)));assertTrue(o.eligible());assertEquals(state==3,o.playing());}}
    /** Estimated position wins exactly when nonnegative, without extrapolation or clamping. */
    @Test public void exactRawEstimatedSelection() throws Exception {for(long estimate:new long[]{-99,-1,0,777}){MediaObservation o=VideoMediaObservationAdapter.observe(track(),M5CDifferentialHarness.snapshot(new JSONObject().put("position",-7).put("estimated",estimate)));assertEquals(estimate>=0?estimate:-7,o.positionMs());}}
    /** Match decisions come from the actual legacy matcher; false is canonically false/-1/false. */
    @Test public void unchangedMatcherAndCanonicalMismatch() throws Exception {for(String key:new String[]{"package","mediaId","title","duration"}){JSONObject a=new JSONObject();a.put(key,key.equals("duration")?2L:"unrelated");ScheduledTrack t=track();MediaSessionProbe.Snapshot v=M5CDifferentialHarness.snapshot(a);MediaObservation o=VideoMediaObservationAdapter.observe(t,v);assertEquals(MediaIdentityMatcher.matches(t,v),o.eligible());if(!o.eligible()){assertEquals(-1,o.positionMs());assertFalse(o.playing());}}}
}
