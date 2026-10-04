package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import org.json.JSONObject;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

/**
 * Exercises the production Context-to-SharedPreferences repository adapter, not a copied save
 * method. The editor substitute records batches; native disk power-loss is not simulated.
 */
public final class M4PhaseAAtomicStoreTest {
    /** Observe exactly which keys the production editor commits together. */
    private static final class Preferences {
        final Map<String,String> values=new HashMap<>();
        Set<String> lastPut=new HashSet<>(),lastRemoved=new HashSet<>();
        int edits,commits;
        boolean writable=true;

        /** Supply the existing cloud_track file only; any other storage access is a failure. */
        Context context() {
            SharedPreferences prefs=(SharedPreferences)Proxy.newProxyInstance(
                    SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},
                    (proxy,method,args)->{
                        if ("getString".equals(method.getName())) return values.getOrDefault(args[0],(String)args[1]);
                        if ("edit".equals(method.getName())) return editor();
                        throw new AssertionError("Unexpected preference method: "+method.getName());
                    });
            return new ContextWrapper(null) {
                /** Use this test context for the production adapter's application lookup. */
                @Override public Context getApplicationContext() {return this;}
                /** Pin the historical file name and private storage mode. */
                @Override public SharedPreferences getSharedPreferences(String name,int mode) {
                    assertEquals("cloud_track",name);assertEquals(Context.MODE_PRIVATE,mode);return prefs;
                }
            };
        }

        /** Stage a single batch and expose it only when the substituted durable commit succeeds. */
        private SharedPreferences.Editor editor() {
            edits++;
            Map<String,String> staged=new HashMap<>(values);
            Set<String> put=new HashSet<>(),removed=new HashSet<>();
            return (SharedPreferences.Editor)Proxy.newProxyInstance(
                    SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},
                    (proxy,method,args)->{
                        switch (method.getName()) {
                            case "putString": staged.put((String)args[0],(String)args[1]);put.add((String)args[0]);return proxy;
                            case "remove": staged.remove(args[0]);removed.add((String)args[0]);return proxy;
                            case "clear": staged.clear();return proxy;
                            case "commit":
                                commits++;lastPut=put;lastRemoved=removed;
                                if (!writable) return false;
                                values.clear();values.putAll(staged);return true;
                            default: throw new AssertionError("Unexpected editor method: "+method.getName());
                        }
                    });
        }
    }

    /** A scheduler callback observes the committed complete tuple, never staged half-artifacts. */
    private static MediaSyncedTrackScheduler observing(Preferences prefs,Set<String> expectedKeys) {
        return new MediaSyncedTrackScheduler(new MediaSyncedTrackScheduler.Listener() {
            /** Rendering is outside this adapter batch assertion. */
            @Override public void onRender(ScheduledTrack.Event event) {}
            /** Playback has no storage responsibility. */
            @Override public void onPlayback(boolean playing,boolean freeze) {}
            /** The real load occurs after the sole commit, with the full historical key set. */
            @Override public void onEligibility(boolean eligible) {
                assertEquals(1,prefs.commits);assertEquals(expectedKeys,prefs.values.keySet());
                assertEquals("14",prefs.values.get("revision"));assertEquals("13",prefs.values.get("ackRevision"));
            }
        });
    }

    /** The actual Android adapter writes revision, runtime and manifest through one editor/commit. */
    @Test public void productionManifestedAdapterCommitsOneCompleteBatchBeforeLoad() throws Exception {
        Preferences prefs=new Preferences();prefs.values.putAll(M4PhaseAFixtures.cacheFixture(true).values);
        CloudTrackRepository cache=new CloudTrackRepository(prefs.context());
        JSONObject envelope=M4PhaseAFixtures.envelope(0,14);
        String runtime=envelope.getJSONObject("runtimeTrack").toString();
        String manifest=envelope.getJSONObject("overlayManifest").toString();
        assertTrue(cache.install(14,runtime,manifest,observing(prefs,
                Set.of("revision","runtime","manifest","ackRevision"))).ok);
        assertEquals(1,prefs.edits);assertEquals(1,prefs.commits);
        assertEquals(Set.of("revision","runtime","manifest"),prefs.lastPut);
        assertTrue(prefs.lastRemoved.isEmpty());
        assertTrue("runtime bytes retained",runtime.equals(prefs.values.get("runtime")));
        assertTrue("manifest bytes retained",manifest.equals(prefs.values.get("manifest")));
        assertEquals(13,cache.acknowledged());
    }

    /** Legacy persistence removes the old manifest in the same commit as the new runtime. */
    @Test public void productionLegacyAdapterRemovesManifestWithinTheReplacementBatch() throws Exception {
        Preferences prefs=new Preferences();prefs.values.putAll(M4PhaseAFixtures.cacheFixture(true).values);
        CloudTrackRepository cache=new CloudTrackRepository(prefs.context());
        String runtime=M4PhaseAFixtures.envelope(0,14).getJSONObject("runtimeTrack").toString();
        assertTrue(cache.install(14,runtime,observing(prefs,Set.of("revision","runtime","ackRevision"))));
        assertEquals(1,prefs.edits);assertEquals(1,prefs.commits);
        assertEquals(Set.of("revision","runtime"),prefs.lastPut);
        assertEquals(Set.of("manifest"),prefs.lastRemoved);
        assertEquals(13,cache.acknowledged());
    }

    /** A false commit result from the real adapter cannot authorize scheduler loading. */
    @Test public void productionAdapterHonorsFalseCommitWithoutLoadingScheduler() throws Exception {
        Preferences prefs=new Preferences();prefs.values.putAll(M4PhaseAFixtures.cacheFixture(true).values);
        Map<String,String> before=new HashMap<>(prefs.values);prefs.writable=false;
        CloudTrackRepository cache=new CloudTrackRepository(prefs.context());
        JSONObject envelope=M4PhaseAFixtures.envelope(0,14);
        MediaSyncedTrackScheduler scheduler=new MediaSyncedTrackScheduler(new MediaSyncedTrackScheduler.Listener() {
            /** No event is possible after this failed write. */
            @Override public void onRender(ScheduledTrack.Event event) {fail("Unexpected render");}
            /** No playback callback is possible after this failed write. */
            @Override public void onPlayback(boolean playing,boolean freeze) {fail("Unexpected playback callback");}
            /** A failed write must not reach scheduler.load. */
            @Override public void onEligibility(boolean eligible) {fail("Load before durable success");}
        });
        assertFalse(cache.install(14,envelope.getJSONObject("runtimeTrack").toString(),
                envelope.getJSONObject("overlayManifest").toString(),scheduler).ok);
        assertEquals(1,prefs.edits);assertEquals(1,prefs.commits);
        assertTrue("durable boundary rejected entire batch",before.equals(prefs.values));
    }
}
