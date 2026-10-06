package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.calendar.MediaCalendarScheduler;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Recording-only fixtures shared verbatim with the Android differential test APK.
 * Builds exact Video values and invokes real entry points; no scheduling algorithm
 * is implemented here. B frame tokens remain input labels, while callback tokens
 * are separately checked against the binding active at transition entry.
 */
public final class M5CDifferentialHarness {
    /** Abstract entry points permit only an actual candidate or actual legacy instance. */
    interface Engine {
        /** Replace from fixture values without introducing an ingress parser. */
        void load(ScheduledTrack track, String token);
        /** Invalidate the current binding through its real clear entry point. */
        void clear();
        /** Supply the exact synthetic snapshot with no real session acquisition. */
        void snapshot(ScheduledTrack track, MediaSessionProbe.Snapshot value);
        /** Invoke the distinct actual unavailability entry point. */
        void unavailable();
    }

    /** Construct already-owned Video values just as the frozen B oracle wrapper does. */
    static ScheduledTrack track(JSONObject fixture, JSONObject action) throws Exception {
        JSONArray events=action.has("events")?action.getJSONArray("events"):fixture.getJSONArray("events");
        List<ScheduledTrack.Event> values=new ArrayList<>();
        for(int i=0;i<events.length();i++) {
            JSONObject event=events.getJSONObject(i);
            values.add(new ScheduledTrack.Event(event.getString("id"),"synthetic test payload",
                    event.getLong("start"),event.getLong("duration"),null));
        }
        JSONObject identity=fixture.optJSONObject("identity");
        if(identity==null) identity=new JSONObject();
        return new ScheduledTrack("synthetic-track",identity.optString("package","com.amazon.amazonvideo.livingroom"),
                new ScheduledTrack.MediaIdentity(identity.optString("platform","prime_video"),
                        identity.optString("id","fixture-video"),identity.optString("title","Columbo Murder by the Book"),
                        identity.optLong("duration",1_000_000L)),values,action.optBoolean("freeze",fixture.optBoolean("freeze",true)));
    }

    /** Copy independent raw and estimated positions, state and identity without normalization. */
    static MediaSessionProbe.Snapshot snapshot(JSONObject action) throws Exception {
        long raw=action.optLong("position",0L);
        return new MediaSessionProbe.Snapshot(action.optString("package","com.amazon.amazonvideo.livingroom"),
                action.optInt("state",3),"fixture-state",raw,action.optLong("estimated",raw),1f,0L,
                action.optString("mediaId","fixture-video"),action.optString("title","Columbo Murder by the Book"),
                action.optString("subtitle",""),action.optLong("duration",1_000_000L));
    }

    /** Record raw effects, validating captured callback tokens independently of B input labels. */
    static Engine candidate(M5TemporalJournal journal) {
        final String[] binding={null};
        MediaCalendarScheduler scheduler=new MediaCalendarScheduler(new MediaCalendarScheduler.Sink() {
            /** Check old-binding invalidation and eligible transitions at their immediate boundary. */
            @Override public void onEligibility(String token,boolean eligible) {
                checkToken(token,binding[0]);
                journal.append(eligible?M5TemporalJournal.Kind.ELIGIBLE:M5TemporalJournal.Kind.INELIGIBLE,null,false,false);
            }
            /** Preserve every repeated eligible observation. */
            @Override public void onPlayback(String token,boolean playing,boolean freeze) {
                checkToken(token,binding[0]);journal.append(M5TemporalJournal.Kind.PLAYBACK,null,playing,freeze);
            }
            /** Record only exact event IDs after the actual candidate mutation. */
            @Override public void onDue(String token,String id) {
                checkToken(token,binding[0]);journal.append(M5TemporalJournal.Kind.DUE,id,false,false);
            }
            /** Preserve raw expiry ordering without sorting or visual state. */
            @Override public void onExpire(String token,String id) {
                checkToken(token,binding[0]);journal.append(M5TemporalJournal.Kind.EXPIRE,id,false,false);
            }
        });
        return new Engine() {
            /** Old binding remains expected until load returns after its invalidation callback. */
            @Override public void load(ScheduledTrack track,String token) {
                scheduler.load(M5VideoTestProjection.project(track),token);binding[0]=token;
            }
            /** Clear emits with the old binding and subsequently removes it. */
            @Override public void clear() {scheduler.clear();binding[0]=null;}
            /** The actual Video adapter preserves null and canonical mismatch. */
            @Override public void snapshot(ScheduledTrack track,MediaSessionProbe.Snapshot value) {
                scheduler.onObservation(VideoMediaObservationAdapter.observe(track,value));
            }
            /** No temporal work happens autonomously. */
            @Override public void unavailable() {scheduler.onUnavailable();}
        };
    }

    /** A token may be null but can never be replaced by the current input's unrelated label. */
    private static void checkToken(String actual,String expected) {
        if(!java.util.Objects.equals(actual,expected)) throw new AssertionError("Captured activation token differs");
    }

    /** Android uses the unchanged current legacy source, pinned to the six-source B oracle. */
    static Engine legacy(M5TemporalJournal journal) {
        MediaSyncedTrackScheduler scheduler=new MediaSyncedTrackScheduler(new MediaSyncedTrackScheduler.Listener() {
            /** Translate the immediate old callback without retiming it. */
            @Override public void onRender(ScheduledTrack.Event event) {journal.append(M5TemporalJournal.Kind.DUE,event.id,false,false);}
            /** Keep repeated PLAYBACK emissions. */
            @Override public void onPlayback(boolean playing,boolean freeze) {journal.append(M5TemporalJournal.Kind.PLAYBACK,null,playing,freeze);}
            /** Record eligibility transitions without rendering. */
            @Override public void onEligibility(boolean eligible) {journal.append(eligible?M5TemporalJournal.Kind.ELIGIBLE:M5TemporalJournal.Kind.INELIGIBLE,null,false,false);}
            /** Keep the actual environment's raw map order. */
            @Override public void onExpire(ScheduledTrack.Event event) {journal.append(M5TemporalJournal.Kind.EXPIRE,event.id,false,false);}
        });
        return new Engine() {
            /** Supply the already constructed Video value to the unchanged legacy entry point. */
            @Override public void load(ScheduledTrack track,String token) {scheduler.load(track);}
            /** Execute legacy clear, not a rewritten state model. */
            @Override public void clear() {scheduler.clear();}
            /** Identity remains exclusively the actual legacy matcher's decision. */
            @Override public void snapshot(ScheduledTrack track,MediaSessionProbe.Snapshot value) {scheduler.onPlaybackSnapshot(value);}
            /** Distinguish unavailable from snapshot null. */
            @Override public void unavailable() {scheduler.onPlaybackUnavailable();}
        };
    }

    /** Run every numbered input, including empty frames and reconstruction without a cursor. */
    static M5TemporalJournal execute(JSONObject fixture,boolean useCandidate) throws Exception {
        M5TemporalJournal journal=new M5TemporalJournal();
        Engine engine=useCandidate?candidate(journal):legacy(journal);
        ScheduledTrack track=null;
        String label=null;
        JSONArray actions=fixture.getJSONArray("actions");
        for(int i=0;i<actions.length();i++) {
            JSONObject action=actions.getJSONObject(i);
            if(action.has("token")) label=action.isNull("token")?null:action.getString("token");
            journal.begin(label);
            switch(action.getString("op")) {
                case "reconstruct": engine=useCandidate?candidate(journal):legacy(journal);
                case "load": track=track(fixture,action);engine.load(track,label);break;
                case "clear": engine.clear();track=null;break;
                case "unavailable": engine.unavailable();break;
                case "snapshot": engine.snapshot(track,snapshot(action));break;
                case "null": engine.snapshot(track,null);break;
                default: throw new IllegalArgumentException("Unknown corpus input");
            }
            journal.end();
        }
        return journal;
    }

    /** Compare all 82 applicable B cases in one Android VM and export their unsorted evidence. */
    public static JSONObject executeAndroid(JSONArray cases) throws Exception {
        JSONObject traces=new JSONObject();int compared=0,hashmap=0;
        for(int i=0;i<cases.length();i++) {
            JSONObject fixture=cases.getJSONObject(i);
            if(!fixture.getString("kind").equals("oracle")) continue;
            M5TemporalJournal old=execute(fixture,false),candidate=execute(fixture,true);
            if(!M5TemporalJournal.exactlyEqual(old.frames(),candidate.frames())) throw new AssertionError("Android differential diverged: "+fixture.getString("id"));
            traces.put(fixture.getString("id"),new JSONObject().put("oracle",old.json()).put("candidate",candidate.json())
                    .put("environmentDependent",fixture.optBoolean("environmentDependent",false)));
            compared++;if(fixture.optBoolean("environmentDependent",false)) hashmap++;
        }
        if(compared!=82||hashmap!=4) throw new AssertionError("Incomplete Android differential inventory");
        return new JSONObject().put("compared",compared).put("hashmap",hashmap).put("divergences",0).put("traces",traces);
    }

    /** Prevent construction of the recording-only harness. */
    private M5CDifferentialHarness() {}
}
