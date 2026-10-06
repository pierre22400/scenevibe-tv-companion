package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/**
 * Phase A uses the existing cache, parser, scheduler and service cores unchanged.
 * Only persistence and drawing boundaries are substituted. Fixtures pin the old
 * preference keys, not a future M4 model; traces contain stage names/revisions only.
 */
final class M4PhaseAFixtures {
    /** Keep the exact Unicode sequence, including a combining accent and surrogate pairs. */
    static final String UNICODE="Été à l’Île — cœur, Noël, déjà ; français 🇫🇷 / e\u0301";

    /** Read bounded committed fixture bytes explicitly as UTF-8. */
    static JSONObject resource(String path) throws Exception {
        try (InputStream stream=M4PhaseAFixtures.class.getResourceAsStream(path)) {
            assertNotNull("characterization resource",stream);
            byte[] bytes=stream.readAllBytes();
            assertTrue("bounded test fixture",bytes.length<400_000);
            return new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        }
    }

    /** Reuse the producer's pinned Cloud envelope instead of inventing a new wire contract. */
    static JSONObject envelope(int index,long revision) throws Exception {
        JSONObject value=resource("/m1/cloud-envelopes.json").getJSONArray("cases")
                .getJSONObject(index).getJSONObject("envelope");
        value.put("revision",revision);
        return value;
    }

    /** Load exactly the qualified historical storage shape, with no schema migration. */
    static Memory cacheFixture(boolean manifested) throws Exception {
        JSONObject fixture=resource(manifested?"/m4/video-manifested-cache-v1.json"
                :"/m4/video-legacy-cache-v1.json");
        Memory memory=new Memory();
        for (Iterator<String> keys=fixture.keys();keys.hasNext();) {
            String key=keys.next();memory.values.put(key,fixture.getString(key));
        }
        return memory;
    }

    /** Mirror a single atomic durable write while observing calls to the real repository. */
    static final class Memory implements CloudTrackRepository.Storage {
        final Map<String,String> values=new HashMap<>();
        final List<String> trace=new ArrayList<>();
        int commits,ackCommits,clears;
        boolean writable=true,ackWritable=true;

        /** Read one historical cache key without changing durable state. */
        @Override public String get(String key) {return values.get(key);}
        /** Legacy persistence retires the previous manifest in that same write. */
        @Override public boolean save(long revision,String runtime) {return save(revision,runtime,null);}
        /** Publish revision and both artifacts together, or reject the entire write. */
        @Override public boolean save(long revision,String runtime,String manifest) {
            if (!writable) return false;
            Map<String,String> staged=new HashMap<>(values);
            staged.put("revision",String.valueOf(revision));staged.put("runtime",runtime);
            if (manifest==null) staged.remove("manifest");else staged.put("manifest",manifest);
            values.clear();values.putAll(staged);commits++;
            trace.add("commit:"+revision);return true;
        }
        /** A server-confirmed ACK has its own durability boundary. */
        @Override public boolean saveAck(long revision) {
            if (!ackWritable) return false;
            values.put("ackRevision",String.valueOf(revision));ackCommits++;
            trace.add("ack-save:"+revision);return true;
        }
        /** Corruption/reset erases this cache only. */
        @Override public void clear() {values.clear();clears++;}
    }

    /** Observe the single SceneRenderer owner; no native window or clock is simulated. */
    static final class Sink implements SceneRuntimeController.SceneSink {
        int shows,hides,visible,maxVisible;
        boolean failHide;
        /** Text preflight declares no asset or network behavior. */
        @Override public boolean preflight(OverlayManifest.Scene scene) {return true;}
        /** A second simultaneous visual owner is a test failure. */
        @Override public void show(OverlayManifest.Scene scene) {
            shows++;visible++;maxVisible=Math.max(maxVisible,visible);
            assertEquals("one visible scene",1,visible);
        }
        /** An injected retirement failure exercises the actual activation exception path. */
        @Override public void hide(OverlayManifest.Scene scene) {
            if (failHide) throw new IllegalStateException("test retirement failure");
            hides++;visible=0;
        }
        /** Retire all test visuals through the same boundary. */
        @Override public void hideAll() {visible=0;}
    }

    /** Compose only existing production classes and their already available test seams. */
    static final class Runtime {
        final Memory memory;
        final CloudTrackRepository cache;
        final Sink sink=new Sink();
        final SceneRuntimeController controller=new SceneRuntimeController(sink);
        final MediaSyncedTrackScheduler scheduler;
        final M4PhaseFHistoricalCloudClient.ManifestInstaller installer;
        int legacyRenders,loads;

        /** Bind the real scheduler/bridge/service helpers to deterministic boundary fakes. */
        Runtime(Memory memory) {
            this.memory=memory;cache=new CloudTrackRepository(memory);
            scheduler=new MediaSyncedTrackScheduler(new MediaSyncedTrackScheduler.Listener() {
                /** Select the same mutually exclusive manifested/legacy owner predicate. */
                @Override public void onRender(ScheduledTrack.Event event) {
                    if (controller.isSceneRendererActiveFor(cache.revision())) controller.onEventDue(event.id);
                    else legacyRenders++;
                }
                /** Forward passive playback state without creating another Video clock. */
                @Override public void onPlayback(boolean playing,boolean freeze) {
                    controller.onPlayback(playing,freeze);
                }
                /** Forward media-time expiry only to the manifested visual owner. */
                @Override public void onExpire(ScheduledTrack.Event event) {
                    if (controller.isSceneRendererActiveFor(cache.revision())) controller.onEventExpired(event.id);
                }
                /** A load resets eligibility only after the cache has committed. */
                @Override public void onEligibility(boolean eligible) {
                    if (!eligible) {loads++;memory.trace.add("scheduler:"+cache.revision());}
                    controller.onEligibility(eligible);
                }
            });
            installer=new M4PhaseFHistoricalCloudClient.ManifestInstaller() {
                /** Delegate the entire manifested install/restore/arm sequence to the current core. */
                @Override public boolean install(long revision,String runtime,String manifest) {
                    long armed=M4PhaseGHistoricalService.installManifestedRevision(cache,scheduler,controller,
                            new DiagnosticsStore(),revision,runtime,manifest);
                    if (armed==revision) memory.trace.add("arm:"+revision);
                    return armed==revision;
                }
                /** Redelivery must confirm the durable copy, never install incoming bytes again. */
                @Override public boolean confirmArmed(long revision) {
                    long armed=M4PhaseGHistoricalService.confirmManifestedRevisionArmed(cache,scheduler,controller,revision);
                    if (armed==revision) memory.trace.add("confirm:"+revision);
                    return armed==revision;
                }
                /** Return visual ownership to the legacy path before ACK eligibility. */
                @Override public boolean activateLegacy(long revision) {
                    long active=M4PhaseGHistoricalService.activateLegacyRevision(controller,revision);
                    if (active==revision) memory.trace.add("legacy-arm:"+revision);
                    return active==revision;
                }
            };
        }

        /** Apply through the real owner gate without a transport or a synthetic installer. */
        boolean apply(JSONObject envelope) throws Exception {
            return M4PhaseFHistoricalCloudClient.applyAssignment(AssignmentMutationGate.direct(),()->true,
                    envelope.getLong("revision"),envelope.getJSONObject("runtimeTrack").toString(),
                    envelope.has("overlayManifest")?envelope.getJSONObject("overlayManifest").toString():null,
                    cache,scheduler,installer);
        }

        /** Simulate process recreation using the current manifested-first service restore order. */
        long restore() {
            CloudTrackRepository.RestoreResult restored=cache.restoreWithManifest(scheduler);
            if (restored.ok) {
                controller.replaceRevision(restored.revision,restored.manifest);
                return restored.revision;
            }
            return cache.restore(scheduler);
        }

        /** Extract the exact current due event with the actual runtime parser. */
        ScheduledTrack.Event firstEvent() throws Exception {
            return TrackParser.parse(new JSONObject(memory.values.get("runtime")),asset->null).comments.get(0);
        }

        /** Drive a passive matching MediaSession observation at the event's media position. */
        void due() throws Exception {
            JSONObject media=new JSONObject(memory.values.get("runtime")).getJSONObject("mediaIdentity");
            long position=firstEvent().startMs;
            scheduler.onPlaybackSnapshot(new MediaSessionProbe.Snapshot(
                    "com.amazon.amazonvideo.livingroom",android.media.session.PlaybackState.STATE_PLAYING,
                    "PLAYING",position,position,1.0f,0L,media.getString("videoId"),media.getString("title"),
                    "",media.getLong("durationMs")));
        }
    }

    /** Persist non-secret credential metadata separately from the real injected secret store. */
    static final class Credentials implements CloudDeviceCredentials.Storage {
        final Map<String,String> values=new HashMap<>();
        final Map<String,Boolean> flags=new HashMap<>();
        /** Read the credential metadata seam. */
        @Override public String getString(String key) {return values.get(key);}
        /** Read a connection lifecycle flag. */
        @Override public boolean getFlag(String key) {return flags.getOrDefault(key,false);}
        /** Change one lifecycle flag independently of package persistence. */
        @Override public void putFlag(String key,boolean value) {flags.put(key,value);}
        /** Store only public identity and activation temporaries. */
        @Override public boolean persistActivation(String device,String token,String activation,
                String secret,String code) {
            values.put("cloudDeviceId",device);values.put("activationId",activation);
            values.put("userCode",code);return true;
        }
        /** A claim keeps the stable device id and removes only activation temporaries. */
        @Override public boolean confirmClaimed() {clearActivationTemporaries();flags.put("connected",true);return true;}
        /** Clear the metadata corresponding to an expired or claimed activation. */
        @Override public void clearActivationTemporaries() {values.remove("activationId");values.remove("userCode");}
        /** Disconnect keeps durable credentials and does not touch package cache. */
        @Override public void disconnect() {clearActivationTemporaries();flags.put("connected",false);}
        /** Explicit credential reset retains its existing destructive meaning. */
        @Override public void reset() {values.clear();flags.clear();}
        /** Remove a historical plaintext key without touching the secret store. */
        @Override public void removeLegacyPlaintext(String key) {values.remove(key);}
    }

    /** Test-only utility class is not an installable production model. */
    private M4PhaseAFixtures() {}
}
