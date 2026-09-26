package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/** JVM tests for the same LAN/cloud parser and durable cache transition order. */
public final class CloudTrackRepositoryTest {
    /** Real store boundary substitute that commits a complete record or rejects it. */
    private static final class Memory implements CloudTrackRepository.Storage {
        final Map<String,String> values=new HashMap<>();boolean writable=true;
        @Override public String get(String key){return values.get(key);}
        @Override public boolean save(long revision,String json) {
            if(!writable)return false;
            values.put("revision",String.valueOf(revision));values.put("runtime",json);return true;
        }
        @Override public boolean saveAck(long revision){values.put("ackRevision",String.valueOf(revision));return true;}
        @Override public void clear(){values.clear();}
    }
    /** Creates a scheduler with no renderer or MediaSession side effects. */
    private static MediaSyncedTrackScheduler scheduler(){return new MediaSyncedTrackScheduler(new MediaSyncedTrackScheduler.Listener(){
        @Override public void onRender(ScheduledTrack.Event event){}
        @Override public void onPlayback(boolean playing,boolean freeze){}
    });}
    /** One text-only valid runtime assignment. */
    private static String track(String id){return "{\"type\":\"scenevibe.track.v1\",\"trackId\":\""+id+"\",\"targetPackage\":\"com.amazon.amazonvideo.livingroom\",\"pauseFreezesDisplay\":true,\"comments\":[{\"id\":\"c1\",\"text\":\"Hello\",\"startMs\":1000,\"durationMs\":6000}]}";}
    /** Validates the LAN parser's exact event bounds and rejects duplicate IDs. */
    @Test public void sharedParserRejectsMalformedOrPartialTrack() throws Exception {
        ScheduledTrack parsed=TrackParser.parse(new JSONObject(track("first")),media->null);
        assertEquals("first",parsed.trackId);assertEquals(1,parsed.comments.size());
        assertTrue(parsed.pauseFreezesDisplay);
        String invalid=track("first").replace("\"durationMs\":6000","\"durationMs\":999");
        try {TrackParser.parse(new JSONObject(invalid),media->null);fail("expected invalid duration");}
        catch(TrackParser.Invalid expected){assertEquals("invalid_track_comment",expected.code);}
    }
    /** A validated complete track persists before load and survives service recreation. */
    @Test public void cachedTrackRestoresAndOnlyNewerRevisionsReplace() throws Exception {
        Memory memory=new Memory();CloudTrackRepository first=new CloudTrackRepository(memory);
        assertTrue(first.install(1,track("first"),scheduler()));
        assertEquals("first",new JSONObject(memory.values.get("runtime")).optString("trackId"));
        CloudTrackRepository recreated=new CloudTrackRepository(memory);
        assertEquals(1,recreated.restore(scheduler()));
        assertFalse(recreated.install(1,track("duplicate"),scheduler()));
        assertFalse(recreated.install(2,track("invalid").replace("\"durationMs\":6000","\"durationMs\":1"),scheduler()));
        assertEquals(1,recreated.revision());
        assertTrue(recreated.install(2,track("newer"),scheduler()));
        assertEquals(2,recreated.revision());
        assertTrue(recreated.markAcknowledged(2));assertEquals(2,recreated.acknowledged());
    }
    /** Failed persistence and invalid media never reach the scheduler or ACK state. */
    @Test public void invalidOrUnwritableTrackCannotBeAcknowledged() {
        Memory memory=new Memory();CloudTrackRepository repository=new CloudTrackRepository(memory);
        String withMedia=track("media").replace("\"durationMs\":6000","\"durationMs\":6000,\"media\":{\"kind\":\"image\"}");
        assertFalse(repository.install(1,withMedia,scheduler()));
        memory.writable=false;
        assertFalse(repository.install(1,track("first"),scheduler()));
        assertEquals(0,repository.revision());assertFalse(repository.markAcknowledged(1));
        memory.writable=true;memory.values.put("runtime","malformed");memory.values.put("revision","7");
        assertEquals(0,new CloudTrackRepository(memory).restore(scheduler()));
        assertTrue(memory.values.isEmpty());
    }
}
