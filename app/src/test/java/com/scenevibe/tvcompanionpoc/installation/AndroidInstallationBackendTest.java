package com.scenevibe.tvcompanionpoc.installation;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

/**
 * Exercise the actual Android backend through a SharedPreferences boundary substitute.
 * A failed commit intentionally publishes memory while retaining the prior disk map, as
 * Android can do. No flash power-loss claim follows from this deterministic fault model.
 */
public final class AndroidInstallationBackendTest {
    /** Model independent preference memory and disk without exposing content in diagnostics. */
    private static final class Preferences {
        final Map<String,Object> memory=new HashMap<>(),disk=new HashMap<>();
        Set<String> lastPut=new HashSet<>(),lastRemoved=new HashSet<>();
        int edits,commits;
        boolean writable=true,throwOnCommit;
        final SharedPreferences proxy=(SharedPreferences)Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},
                (ignored,method,args)->{
                    if ("getString".equals(method.getName())) return (String)memory.getOrDefault(args[0],args[1]);
                    if ("edit".equals(method.getName())) return editor();
                    throw new AssertionError("Backend invoked a non-persistence method");
                });

        /** Use only the exact existing cloud_track preference file and private mode. */
        Context context() {
            return new ContextWrapper(null) {
                /** Preserve the original application-context lookup. */
                @Override public Context getApplicationContext() {return this;}
                /** Any attempt to access identity/pairing/credentials storage is a failure. */
                @Override public SharedPreferences getSharedPreferences(String name,int mode) {
                    assertEquals("cloud_track",name);assertEquals(Context.MODE_PRIVATE,mode);return proxy;
                }
            };
        }
        /** Stage an atomic batch but simulate memory publication even when disk commit fails. */
        SharedPreferences.Editor editor() {
            edits++;Map<String,Object> staged=new HashMap<>(memory);
            Set<String> put=new HashSet<>(),removed=new HashSet<>();
            return (SharedPreferences.Editor)Proxy.newProxyInstance(
                    SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},
                    (ignored,method,args)->{
                        switch (method.getName()) {
                            case "putString": staged.put((String)args[0],args[1]);put.add((String)args[0]);return ignored;
                            case "remove": staged.remove(args[0]);removed.add((String)args[0]);return ignored;
                            case "clear": staged.clear();return ignored;
                            case "commit":
                                commits++;lastPut=put;lastRemoved=removed;
                                memory.clear();memory.putAll(staged);
                                if (throwOnCommit) throw new IllegalStateException("Synthetic persistence failure");
                                if (!writable) return false;
                                disk.clear();disk.putAll(staged);return true;
                            default: throw new AssertionError("Unexpected editor operation");
                        }
                    });
        }
    }

    /** Bind a fresh store to the real Android backend over this preference-file identity. */
    private static InstallationStore store(Preferences preferences) {
        return new InstallationStore(new AndroidInstallationBackend(preferences.context()));
    }
    /** Supply a small opaque candidate; no product parsing or activation is involved. */
    private static InstallationSnapshot candidate(long revision) {
        return new InstallationSnapshot(new InstallRequest(revision,"codec",
                Collections.singletonMap("bytes",new byte[]{1,2,3})),"handler");
    }

    /** The actual backend publishes exactly one versioned snapshot batch and no automatic ACK. */
    @Test public void genericSnapshotUsesOneEditorCommitAndPreservesPrivateFileBoundary() {
        Preferences prefs=new Preferences();assertEquals(InstallationStore.CommitState.COMMITTED,store(prefs).commit(candidate(1)));
        assertEquals(1,prefs.edits);assertEquals(1,prefs.commits);
        assertEquals(Collections.singleton(InstallationStore.SNAPSHOT_KEY),prefs.lastPut);
        assertTrue(prefs.lastRemoved.isEmpty());assertTrue("memory/disk agree",prefs.memory.equals(prefs.disk));
        assertEquals(1,store(prefs).read().snapshot().revision());assertEquals(0,store(prefs).read().acknowledgedRevision());
    }
    /** False disk commit must not expose its memory-published revision, including to a recreated backend. */
    @Test public void failedMemoryPublicationKeepsPriorSnapshotAcrossStoreRecreation() {
        Preferences prefs=new Preferences();InstallationStore first=store(prefs);first.commit(candidate(1));first.markAcknowledged(1);
        Map<String,Object> durable=new HashMap<>(prefs.disk);prefs.writable=false;
        assertEquals(InstallationStore.CommitState.CACHE_FAILED,first.commit(candidate(2)));
        assertFalse("fault model actually published memory",prefs.memory.equals(prefs.disk));
        assertTrue("durable bytes unchanged",durable.equals(prefs.disk));
        assertEquals(1,first.read().snapshot().revision());assertEquals(1,store(prefs).read().snapshot().revision());
        assertEquals(1,store(prefs).read().acknowledgedRevision());assertEquals(3,prefs.edits);
    }
    /** A subsequent successful ACK restages the prior snapshot instead of accidentally persisting the failed new one. */
    @Test public void successfulAckAfterFailedSnapshotCannotPublishFailedCandidate() {
        Preferences prefs=new Preferences();InstallationStore store=store(prefs);store.commit(candidate(1));
        String durable=(String)prefs.disk.get(InstallationStore.SNAPSHOT_KEY);prefs.writable=false;
        assertEquals(InstallationStore.CommitState.CACHE_FAILED,store.commit(candidate(2)));
        assertFalse(store.markAcknowledged(2));prefs.writable=true;
        assertTrue(store(prefs).markAcknowledged(1));
        assertTrue("failed candidate never became durable",durable.equals(prefs.disk.get(InstallationStore.SNAPSHOT_KEY)));
        assertEquals(1,store(prefs).read().snapshot().revision());assertEquals(1,store(prefs).read().acknowledgedRevision());
        assertEquals(3,prefs.commits);
    }
    /** ACK failure is masked across recreation, and a later package commit retains the older confirmed ACK. */
    @Test public void failedAckMemoryPublicationDoesNotAdvanceOrLeakIntoNextCommit() {
        Preferences prefs=new Preferences();InstallationStore store=store(prefs);
        store.commit(candidate(1));store.markAcknowledged(1);store.commit(candidate(2));prefs.writable=false;
        Map<String,Object> durable=new HashMap<>(prefs.disk);
        assertFalse(store.markAcknowledged(2));assertTrue("ACK disk unchanged",durable.equals(prefs.disk));
        assertEquals(1,store(prefs).read().acknowledgedRevision());prefs.writable=true;
        assertEquals(InstallationStore.CommitState.COMMITTED,store(prefs).commit(candidate(3)));
        assertEquals("1",prefs.disk.get("ackRevision"));assertEquals(1,store(prefs).read().acknowledgedRevision());
    }
    /** A throwing disk boundary is also closed and cannot leak a partly published candidate or message. */
    @Test public void throwingCommitPreservesPriorViewWithoutRollbackWrite() {
        Preferences prefs=new Preferences();InstallationStore store=store(prefs);store.commit(candidate(1));prefs.throwOnCommit=true;
        assertEquals(InstallationStore.CommitState.CACHE_FAILED,store.commit(candidate(2)));
        assertEquals(1,store(prefs).read().snapshot().revision());assertEquals(2,prefs.edits);assertEquals(2,prefs.commits);
        prefs.throwOnCommit=false;assertEquals(InstallationStore.CommitState.COMMITTED,store(prefs).commit(candidate(2)));
        assertEquals(2,store(prefs).read().snapshot().revision());
    }
    /** A wrong stored primitive type is corrupt, never an absent generic marker enabling historical fallback. */
    @Test public void typedGenericCorruptionNeverFallsBackAndRemainsCorruptAfterFailure() {
        Preferences prefs=new Preferences();prefs.memory.put("revision","13");prefs.memory.put("runtime","opaque");
        prefs.memory.put("ackRevision","13");prefs.memory.put(InstallationStore.SNAPSHOT_KEY,Boolean.TRUE);prefs.disk.putAll(prefs.memory);
        assertEquals(InstallationStore.ReadState.CORRUPT,store(prefs).read().state());
        for (String key:new String[]{"revision","runtime","manifest","ackRevision"}) assertNull(store(prefs).historicalValue(key));
        prefs.writable=false;assertEquals(InstallationStore.CommitState.CACHE_FAILED,store(prefs).commit(candidate(14)));
        assertEquals(InstallationStore.ReadState.CORRUPT,store(prefs).read().state());assertEquals(1,prefs.edits);
    }
    /** A failed clear keeps the prior cache and a later successful explicit reset clears it once. */
    @Test public void cacheResetFailurePreservesPriorStateAndRecoveryClearsOnlyCacheFile() {
        Preferences prefs=new Preferences();InstallationStore store=store(prefs);store.commit(candidate(1));prefs.writable=false;
        store.clearHistorical();assertEquals(1,store(prefs).read().snapshot().revision());prefs.writable=true;
        store(prefs).clearHistorical();assertEquals(InstallationStore.ReadState.EMPTY,store(prefs).read().state());
        assertTrue(prefs.disk.isEmpty());assertEquals(3,prefs.commits);
    }
    /** Backend APIs cannot read/write arbitrary secret or diagnostic preference keys. */
    @Test public void backendRejectsUnknownOrAmbiguousBatchesBeforeEditorCreation() {
        Preferences prefs=new Preferences();AndroidInstallationBackend backend=new AndroidInstallationBackend(prefs.context());
        assertThrows(IllegalArgumentException.class,()->backend.get("credentials"));
        assertThrows(IllegalArgumentException.class,()->backend.commit(Map.of("credentials","synthetic"),Set.of(),false));
        assertThrows(IllegalArgumentException.class,()->backend.commit(Map.of("ackRevision","1"),Set.of("ackRevision"),false));
        assertEquals(0,prefs.edits);
    }
    /** Generic representation is not routed into a historical runtime merely because the same preference file is reused. */
    @Test public void validGenericStateRemainsUnavailableToHistoricalFacade() {
        Preferences prefs=new Preferences();prefs.memory.put("revision","13");prefs.memory.put("runtime","opaque old");
        prefs.disk.putAll(prefs.memory);InstallationStore store=store(prefs);store.commit(candidate(14));
        assertEquals(14,store.read().snapshot().revision());assertNull(store.historicalValue("runtime"));
        assertFalse(store.saveHistorical(15,"replacement",null));assertFalse(store.saveHistoricalAcknowledgement(13));
        assertEquals(1,prefs.commits);assertEquals("13",prefs.disk.get("revision"));
    }
}
