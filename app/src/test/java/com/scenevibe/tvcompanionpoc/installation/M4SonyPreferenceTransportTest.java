package com.scenevibe.tvcompanionpoc.installation;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Supplement the native process gate with transport rejection and failed-publication boundaries.
 * This preference substitute models memory publication on false commit, never Android XML/reboot.
 * Every retained A–G assertion stays in its original source; these cases are additive only.
 */
public final class M4SonyPreferenceTransportTest {
    private static final String PREFIX="scenevibe.os.android-preference.v1:";

    /** Publish memory even on failure, retaining an independent simulated disk map. */
    private static final class Preferences {
        final Map<String,Object> memory=new HashMap<>(),disk=new HashMap<>();
        boolean writable=true;
        int writes;
        final SharedPreferences proxy=(SharedPreferences)Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},
                (owner,method,args)->{
                    if ("getString".equals(method.getName())) return (String)memory.get(args[0]);
                    if ("edit".equals(method.getName())) return editor();
                    throw new AssertionError("Unexpected preference operation");
                });

        /** Keep the real backend's exact private file boundary, with no identity/credential access. */
        Context context() {
            return new ContextWrapper(null) {
                /** Resolve the application context without introducing another preference identity. */
                @Override public Context getApplicationContext() {return this;}
                /** Reject any attempt to access another file or mode. */
                @Override public SharedPreferences getSharedPreferences(String name,int mode) {
                    assertEquals("cloud_track",name);assertEquals(Context.MODE_PRIVATE,mode);return proxy;
                }
            };
        }

        /** Stage one batch and retain the prior disk if the explicit synthetic commit fault is enabled. */
        SharedPreferences.Editor editor() {
            Map<String,Object> staged=new HashMap<>(memory);
            return (SharedPreferences.Editor)Proxy.newProxyInstance(
                    SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},
                    (owner,method,args)->{
                        switch (method.getName()) {
                            case "putString": staged.put((String)args[0],args[1]);return owner;
                            case "remove": staged.remove(args[0]);return owner;
                            case "clear": staged.clear();return owner;
                            case "commit":
                                writes++;memory.clear();memory.putAll(staged);
                                if (!writable) return false;
                                disk.clear();disk.putAll(staged);return true;
                            default: throw new AssertionError("Unexpected editor operation");
                        }
                    });
        }
    }

    /** Bind actual production backend/store code to a fresh adapter over the same preference identity. */
    private static InstallationStore store(Preferences preferences) {
        return new InstallationStore(new AndroidInstallationBackend(preferences.context()));
    }

    /** Keep artifact bytes opaque; native tests separately restore both actual Video handlers. */
    private static InstallationSnapshot candidate(long revision) {
        return new InstallationSnapshot(new InstallRequest(revision,"opaque.v1",Map.of("bytes",new byte[]{0,10,-1})),"opaque.v1");
    }

    /** The XML-bound transport has a non-LF terminal while logical codec bytes and old residue remain exact. */
    @Test public void terminalEnvelopePreservesLogicalCodecAndHistoricalResidue() {
        Preferences p=new Preferences();p.memory.putAll(Map.of("revision","13","runtime","retained","ackRevision","13"));
        InstallationStore s=store(p);assertEquals(InstallationStore.CommitState.COMMITTED,s.commit(candidate(15)));
        String logical=InstallationSnapshotCodec.encode(candidate(15));assertTrue(logical.endsWith("\n"));
        assertEquals(PREFIX+logical+"!",p.disk.get(InstallationStore.SNAPSHOT_KEY));
        assertEquals(logical,new AndroidInstallationBackend(p.context()).get(InstallationStore.SNAPSHOT_KEY));
        assertEquals("13",p.disk.get("revision"));assertEquals("retained",p.disk.get("runtime"));
        assertEquals("13",p.disk.get("ackRevision"));assertEquals(1,p.writes);
    }

    /** Valid old raw strings stay readable without adding the envelope or migrating the file. */
    @Test public void exactRawGenericIsReadWithoutMigration() {
        Preferences p=new Preferences();String raw=InstallationSnapshotCodec.encode(candidate(15));
        p.memory.put(InstallationStore.SNAPSHOT_KEY,raw);p.disk.putAll(p.memory);
        assertEquals(15,store(p).read().snapshot().revision());assertEquals(raw,p.memory.get(InstallationStore.SNAPSHOT_KEY));
        assertEquals(p.disk,p.memory);assertEquals(0,p.writes);
    }

    /** Old indented XML corruption remains corrupt; historical 13 and ACK 15 cannot authorize fallback. */
    @Test public void oldFourSpacePaddingRemainsCorruptAndUnchanged() {
        Preferences p=new Preferences();p.memory.putAll(Map.of("revision","13","runtime","old","ackRevision","15"));
        String corrupt=InstallationSnapshotCodec.encode(candidate(15))+"    ";p.memory.put(InstallationStore.SNAPSHOT_KEY,corrupt);
        InstallationStore.ReadResult read=store(p).read();assertEquals(InstallationStore.ReadState.CORRUPT,read.state());
        assertEquals(InstallationStore.ReadFailure.GENERIC_INVALID,read.failure());assertNull(read.snapshot());
        assertEquals(0,read.acknowledgedRevision());assertEquals(corrupt,p.memory.get(InstallationStore.SNAPSHOT_KEY));
        assertEquals(0,p.writes);
    }

    /** Unknown versions, missing terminals, empty/oversized envelopes and extra trailing data never decode permissively. */
    @Test public void malformedTransportFailsClosedWithoutWrites() {
        String raw=InstallationSnapshotCodec.encode(candidate(15));
        for (String value:new String[]{PREFIX+raw,PREFIX+"!",PREFIX+raw+"! ",PREFIX+"x".repeat(4_001_025)+"!",
                "scenevibe.os.android-preference.v2:"+raw+"!",PREFIX+raw+"    !"}) {
            Preferences p=new Preferences();p.memory.put(InstallationStore.SNAPSHOT_KEY,value);
            assertEquals(InstallationStore.ReadState.CORRUPT,store(p).read().state());
            assertEquals(value,p.memory.get(InstallationStore.SNAPSHOT_KEY));assertEquals(0,p.writes);
        }
    }

    /** Wrapping cannot reject a complete package at the unchanged three-million-byte ceiling. */
    @Test public void maximumPackageRetainsEveryByteAndSeparateAck() {
        byte[] a=new byte[1_500_000],b=new byte[1_500_000];
        for (int i=0;i<a.length;i++) {a[i]=(byte)i;b[i]=(byte)(255-i);}
        InstallationSnapshot maximum=new InstallationSnapshot(new InstallRequest(15,"opaque.v1",Map.of("a",a,"b",b)),"opaque.v1");
        Preferences p=new Preferences();assertEquals(InstallationStore.CommitState.COMMITTED,store(p).commit(maximum));
        assertTrue(store(p).markAcknowledged(15));InstallationStore.ReadResult read=store(p).read();
        assertArrayEquals(a,read.snapshot().canonical().artifact("a"));assertArrayEquals(b,read.snapshot().canonical().artifact("b"));
        assertEquals(15,read.acknowledgedRevision());assertEquals(2,p.writes);
    }

    /** A failed new candidate cannot leak through the mask or be persisted by a later ACK of the prior revision. */
    @Test public void failedPublicationRestagesPhysicalEnvelopeExactlyOnce() {
        Preferences p=new Preferences();InstallationStore s=store(p);s.commit(candidate(14));s.markAcknowledged(14);
        Object original=p.disk.get(InstallationStore.SNAPSHOT_KEY);p.writable=false;
        assertEquals(InstallationStore.CommitState.CACHE_FAILED,s.commit(candidate(15)));
        assertEquals(14,store(p).read().snapshot().revision());assertFalse(store(p).markAcknowledged(15));
        p.writable=true;assertTrue(store(p).markAcknowledged(14));
        assertEquals(original,p.disk.get(InstallationStore.SNAPSHOT_KEY));assertEquals(14,store(p).read().snapshot().revision());
        assertEquals(4,p.writes);
    }

    /** ACK failure retains its prior value and recovery does not nest the prior physical snapshot envelope. */
    @Test public void failedAcknowledgementCannotDoubleWrapOrAdvanceAck() {
        Preferences p=new Preferences();InstallationStore s=store(p);s.commit(candidate(14));s.markAcknowledged(14);s.commit(candidate(15));
        Object original=p.disk.get(InstallationStore.SNAPSHOT_KEY);p.writable=false;assertFalse(s.markAcknowledged(15));
        assertEquals(14,store(p).read().acknowledgedRevision());p.writable=true;assertTrue(store(p).markAcknowledged(15));
        assertEquals(original,p.disk.get(InstallationStore.SNAPSHOT_KEY));assertEquals(15,store(p).read().acknowledgedRevision());
        assertEquals(5,p.writes);
    }

    /** Explicit reset retains the prior envelope on failure and clears it only after a successful whole-file commit. */
    @Test public void failedExplicitResetPreservesEnvelopeAndPriorAck() {
        Preferences p=new Preferences();InstallationStore s=store(p);s.commit(candidate(15));s.markAcknowledged(15);
        Object original=p.disk.get(InstallationStore.SNAPSHOT_KEY);p.writable=false;assertFalse(s.clearAll());
        assertEquals(15,store(p).read().snapshot().revision());assertEquals(15,store(p).read().acknowledgedRevision());
        assertEquals(original,p.disk.get(InstallationStore.SNAPSHOT_KEY));p.writable=true;assertTrue(store(p).clearAll());
        assertTrue(p.disk.isEmpty());assertEquals(InstallationStore.ReadState.EMPTY,store(p).read().state());assertEquals(4,p.writes);
    }

    /** A stored non-string remains a bounded backend read fault and cannot become an absent marker. */
    @Test public void malformedPreferenceTypeHasNoHistoricalFallback() {
        Preferences p=new Preferences();p.memory.putAll(Map.of("revision","13","runtime","old","ackRevision","13"));
        p.memory.put(InstallationStore.SNAPSHOT_KEY,Boolean.TRUE);InstallationStore.ReadResult read=store(p).read();
        assertEquals(InstallationStore.ReadState.CORRUPT,read.state());assertEquals(InstallationStore.ReadFailure.BACKEND_READ_FAILED,read.failure());
        assertNull(read.snapshot());assertEquals(0,p.writes);
    }

    /** New observational exception classification still propagates VM errors rather than laundering them as cache faults. */
    @Test public void fatalReadErrorStillPropagates() {
        InstallationStore.Backend fatal=new InstallationStore.Backend() {
            /** Provide only a stable lock for this fatal boundary test. */
            @Override public Object monitor() {return this;}
            /** A VM error must cross all read wrappers unchanged. */
            @Override public String get(String key) {throw new OutOfMemoryError();}
            /** Reading must never mutate storage. */
            @Override public boolean commit(Map<String,String> values,java.util.Set<String> removed,boolean clear) {throw new AssertionError();}
        };
        assertThrows(OutOfMemoryError.class,()->new InstallationStore(fatal).read());
    }
}
