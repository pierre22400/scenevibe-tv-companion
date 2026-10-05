package com.scenevibe.tvcompanionpoc.installation;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

/**
 * Deterministic durable-representation tests: atomic opaque snapshots, separate ACK,
 * canonical bounded encoding and closed corruption. Assertions compare payloads through
 * booleans only. This suite intentionally has no product parser, scheduler or renderer.
 */
public final class InstallationStoreTest {
    /** In-memory durability boundary exposes a staged batch only after successful commit. */
    private static final class Memory implements InstallationStore.Backend {
        final Map<String,String> values=new HashMap<>();
        int commits;
        boolean writable=true;
        /** Share one monitor across all recreated stores for this file. */
        @Override public Object monitor() {return this;}
        /** Read inert storage values without interpretation or mutation. */
        @Override public String get(String key) {return values.get(key);}
        /** Publish the complete batch or preserve every prior field. */
        @Override public boolean commit(Map<String,String> puts,Set<String> removed,boolean clear) {
            commits++;if (!writable) return false;
            Map<String,String> next=clear?new HashMap<>():new HashMap<>(values);
            next.putAll(puts);for (String key:removed) next.remove(key);
            values.clear();values.putAll(next);return true;
        }
    }
    /** Construct an opaque candidate independently of any executable handler or product JSON. */
    private static InstallationSnapshot snapshot(long revision) {
        return new InstallationSnapshot(new InstallRequest(revision,"bounded.codec.v1",
                Collections.singletonMap("artifact",new byte[]{0,1,(byte)255})),"bounded.handler.v1");
    }
    /** Encode test rows using the specified durable format, never a copied product envelope. */
    private static String encoded(String revision,String codec,String handler,String count,String... rows) {
        return InstallationStore.FORMAT_VERSION+"\n"+revision+"\n"+codec+"\n"+handler+"\n"+count+"\n"
                +String.join("\n",rows)+(rows.length==0?"":"\n");
    }
    /** Read direct corrupt input and require no write and no partial candidate/ACK. */
    private static void corrupt(String representation) {
        Memory memory=new Memory();memory.values.put(InstallationStore.SNAPSHOT_KEY,representation);
        InstallationStore.ReadResult result=new InstallationStore(memory).read();
        assertEquals(InstallationStore.ReadState.CORRUPT,result.state());
        assertNull(result.snapshot());assertEquals(0,result.acknowledgedRevision());assertEquals(0,memory.commits);
    }

    /** An untouched file is cleanly empty and has no synthesized acknowledgement. */
    @Test public void emptyStoreIsNonMutatingAndAcknowledgedZero() {
        Memory memory=new Memory();InstallationStore.ReadResult result=new InstallationStore(memory).read();
        assertEquals(InstallationStore.ReadState.EMPTY,result.state());assertNull(result.snapshot());
        assertEquals(0,result.acknowledgedRevision());assertEquals(0,memory.commits);
    }
    /** One commit preserves exact revision, independent codec/handler ids and the entire artifact set. */
    @Test public void genericCommitPublishesOneCompleteSnapshotWithExactIdentities() {
        Memory memory=new Memory();InstallationStore store=new InstallationStore(memory);
        assertEquals(InstallationStore.CommitState.COMMITTED,store.commit(snapshot(19)));
        assertEquals(1,memory.commits);assertEquals(Collections.singleton(InstallationStore.SNAPSHOT_KEY),memory.values.keySet());
        InstallationSnapshot read=new InstallationStore(memory).read().snapshot();
        assertEquals(19,read.revision());assertEquals("bounded.codec.v1",read.codecId());
        assertEquals("bounded.handler.v1",read.handlerId());
        assertTrue("all canonical bytes preserved",Arrays.equals(snapshot(19).canonical().artifact("artifact"),read.canonical().artifact("artifact")));
    }
    /** Reordering source maps cannot alter canonical versioned storage bytes. */
    @Test public void persistedEncodingIsVersionedAndDeterministic() {
        Map<String,byte[]> forward=new LinkedHashMap<>(),reverse=new LinkedHashMap<>();
        forward.put("a",new byte[]{1});forward.put("z",new byte[]{2});
        reverse.put("z",new byte[]{2});reverse.put("a",new byte[]{1});
        Memory first=new Memory(),second=new Memory();
        new InstallationStore(first).commit(new InstallationSnapshot(new InstallRequest(9,"codec",forward),"handler"));
        new InstallationStore(second).commit(new InstallationSnapshot(new InstallRequest(9,"codec",reverse),"handler"));
        assertTrue("encoded bytes deterministic",first.values.equals(second.values));
        assertTrue(first.values.get(InstallationStore.SNAPSHOT_KEY).startsWith(InstallationStore.FORMAT_VERSION+"\n"));
    }
    /** French accents, ligature, apostrophe, decomposed accent and emoji survive exact byte round-trip. */
    @Test public void genericUnicodeBytesRoundTripWithoutNormalization() {
        byte[] unicode="Été à l’Île — cœur / é / e\u0301 / 🇫🇷 / 😀".getBytes(StandardCharsets.UTF_8);
        Memory memory=new Memory();InstallationStore store=new InstallationStore(memory);
        assertEquals(InstallationStore.CommitState.COMMITTED,store.commit(new InstallationSnapshot(
                new InstallRequest(1,"codec",Collections.singletonMap("artifact",unicode)),"handler")));
        byte[] restored=store.read().snapshot().canonical().artifact("artifact");
        assertTrue("UTF-8 unchanged",Arrays.equals(unicode,restored));
    }
    /** Storage is opaque: non-UTF-8 bytes and every byte value remain valid canonical artifacts. */
    @Test public void arbitraryBinaryBytesArePreservedExactly() {
        byte[] binary=new byte[256];for (int i=0;i<binary.length;i++) binary[i]=(byte)i;
        Memory memory=new Memory();InstallationStore store=new InstallationStore(memory);
        store.commit(new InstallationSnapshot(new InstallRequest(1,"codec",Collections.singletonMap("bytes",binary)),"handler"));
        assertTrue("opaque byte values unchanged",Arrays.equals(binary,store.read().snapshot().canonical().artifact("bytes")));
    }
    /** Mutating returned arrays cannot change a later read or the persisted encoded candidate. */
    @Test public void readReturnsDefensiveImmutableCandidates() {
        Memory memory=new Memory();InstallationStore store=new InstallationStore(memory);store.commit(snapshot(1));
        String before=memory.values.get(InstallationStore.SNAPSHOT_KEY);
        store.read().snapshot().canonical().artifact("artifact")[0]=99;
        store.read().snapshot().canonical().artifacts().get("artifact")[1]=99;
        assertTrue("durable representation unchanged",before.equals(memory.values.get(InstallationStore.SNAPSHOT_KEY)));
        assertTrue("new read detached",Arrays.equals(new byte[]{0,1,(byte)255},store.read().snapshot().canonical().artifact("artifact")));
    }
    /** A rejected snapshot commit preserves every previously durable byte and acknowledged revision. */
    @Test public void failedGenericCommitLeavesPriorSnapshotByteIdentical() {
        Memory memory=new Memory();InstallationStore store=new InstallationStore(memory);
        store.commit(snapshot(1));assertTrue(store.markAcknowledged(1));
        Map<String,String> before=new HashMap<>(memory.values);memory.writable=false;
        assertEquals(InstallationStore.CommitState.CACHE_FAILED,store.commit(snapshot(2)));
        assertTrue("entire durable file preserved",before.equals(memory.values));
        assertEquals(1,new InstallationStore(memory).read().snapshot().revision());
        assertEquals(1,store.read().acknowledgedRevision());
    }
    /** COMMIT never manufactures an ACK; replacing a snapshot retains the prior confirmed ACK. */
    @Test public void acknowledgedRevisionIsSeparateFromSnapshotCommit() {
        Memory memory=new Memory();InstallationStore store=new InstallationStore(memory);store.commit(snapshot(1));
        assertFalse(memory.values.containsKey("ackRevision"));assertEquals(0,store.read().acknowledgedRevision());
        assertTrue(store.markAcknowledged(1));store.commit(snapshot(2));
        assertEquals(1,store.read().acknowledgedRevision());assertEquals(2,store.read().snapshot().revision());
    }
    /** Missing, zero, stale and future confirmations cannot change the separate durable ACK. */
    @Test public void onlyExactCurrentRevisionCanBeAcknowledged() {
        Memory memory=new Memory();InstallationStore store=new InstallationStore(memory);
        assertFalse(store.markAcknowledged(1));store.commit(snapshot(2));
        for (long revision:new long[]{-1,0,1,3,Long.MAX_VALUE}) assertFalse(store.markAcknowledged(revision));
        assertEquals(1,memory.commits);assertEquals(0,store.read().acknowledgedRevision());
        assertTrue(store.markAcknowledged(2));assertTrue(store.markAcknowledged(2));
        assertFalse(store.markAcknowledged(1));assertEquals(2,store.read().acknowledgedRevision());
    }
    /** An ACK disk failure leaves the prior confirmed ACK independently of the new durable revision. */
    @Test public void failedAcknowledgementKeepsPriorAck() {
        Memory memory=new Memory();InstallationStore store=new InstallationStore(memory);
        store.commit(snapshot(1));store.markAcknowledged(1);store.commit(snapshot(2));
        Map<String,String> before=new HashMap<>(memory.values);memory.writable=false;
        assertFalse(store.markAcknowledged(2));assertTrue("file unchanged",before.equals(memory.values));
        assertEquals(1,new InstallationStore(memory).read().acknowledgedRevision());
    }
    /** A candidate cannot put the already-confirmed ACK beyond its revision or accept a null snapshot. */
    @Test public void invalidCandidateCannotWriteOrRegressBelowDurableAck() {
        Memory memory=new Memory();InstallationStore store=new InstallationStore(memory);
        store.commit(snapshot(2));store.markAcknowledged(2);int before=memory.commits;
        assertEquals(InstallationStore.CommitState.INVALID_SNAPSHOT,store.commit(snapshot(1)));
        assertEquals(InstallationStore.CommitState.INVALID_SNAPSHOT,store.commit(null));assertEquals(before,memory.commits);
        assertThrows(IllegalArgumentException.class,()->new InstallationSnapshot(null,"handler"));
        assertThrows(IllegalArgumentException.class,()->new InstallationSnapshot(snapshot(1).canonical(),"unsafe/handler"));
    }
    /** Truncated fields, missing final delimiter and trailing metadata never produce a partial snapshot. */
    @Test public void incompleteAndTrailingGenericRepresentationsFailClosed() {
        String valid=encoded("1","codec","handler","1","artifact","AQ==");
        for (int end=0;end<valid.length();end++) if (valid.charAt(end)=='\n') corrupt(valid.substring(0,end));
        corrupt(valid+"unexpected\n");corrupt(valid+"\n");
    }
    /** Any present unknown format marker is corrupt, not a compatibility fallback trigger. */
    @Test public void unknownOrMalformedVersionFailsClosed() {
        corrupt("");corrupt("unknown\n");
        corrupt(encoded("1","codec","handler","1","artifact","AQ==").replace(InstallationStore.FORMAT_VERSION,"scenevibe.os.installation-store.v2"));
    }
    /** A corrupt generic marker cannot resurrect a fully present older historical tuple. */
    @Test public void corruptGenericNeverFallsBackToHistoricalCache() {
        Memory memory=new Memory();memory.values.put("revision","13");memory.values.put("runtime","opaque historical");
        memory.values.put("ackRevision","13");memory.values.put(InstallationStore.SNAPSHOT_KEY,"broken");
        InstallationStore.ReadResult result=new InstallationStore(memory).read();
        assertEquals(InstallationStore.ReadState.CORRUPT,result.state());assertNull(result.snapshot());assertEquals(0,memory.commits);
    }
    /** Invalid IDs, duplicate/unsorted IDs and excessive counts are representation corruption. */
    @Test public void invalidArtifactMetadataFailsClosed() {
        corrupt(encoded("1","unsafe/codec","handler","1","a","AQ=="));
        corrupt(encoded("1","codec","unsafe/handler","1","a","AQ=="));
        corrupt(encoded("1","codec","handler","0"));corrupt(encoded("1","codec","handler","3"));
        corrupt(encoded("1","codec","handler","1","unsafe/id","AQ=="));
        corrupt(encoded("1","codec","handler","2","a","AQ==","a","Ag=="));
        corrupt(encoded("1","codec","handler","2","z","AQ==","a","Ag=="));
        corrupt(encoded("1","codec","handler","1","x".repeat(129),"AQ=="));
    }
    /** Base64 must be padded, canonical, non-empty and contain no tolerated whitespace or invalid alphabet. */
    @Test public void malformedAndNonCanonicalArtifactEncodingFailsClosed() {
        for (String bytes:new String[]{"","A","AQ","AQ=","AR==","AQ== ","!!!!","AA_A","===="})
            corrupt(encoded("1","codec","handler","1","a",bytes));
    }
    /** Installation revision framing is positive canonical decimal and cannot overflow a long. */
    @Test public void malformedAndOverflowedGenericRevisionFailsClosed() {
        for (String revision:new String[]{"","0","-1","+1","01","x","9223372036854775808","1".repeat(20)})
            corrupt(encoded(revision,"codec","handler","1","a","AQ=="));
        Memory memory=new Memory();InstallationStore store=new InstallationStore(memory);store.commit(snapshot(Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE,store.read().snapshot().revision());
    }
    /** Malformed, negative and future ACKs fail closed even with a complete generic snapshot. */
    @Test public void invalidAcknowledgementFailsClosedAndCannotBeReconfirmed() {
        for (String ack:new String[]{"","-1","x","3","9223372036854775808"}) {
            Memory memory=new Memory();InstallationStore store=new InstallationStore(memory);store.commit(snapshot(2));
            memory.values.put("ackRevision",ack);int before=memory.commits;
            assertEquals(InstallationStore.ReadState.CORRUPT,store.read().state());
            assertFalse(store.markAcknowledged(2));assertEquals(before,memory.commits);
        }
    }
    /** Encoding growth is explicitly bounded before attempting a large field decode. */
    @Test public void encodedOuterAndArtifactBoundsFailClosed() {
        corrupt("x".repeat(InstallationSnapshotCodec.MAX_ENCODED_CHARACTERS+1));
        corrupt(encoded("1","codec","handler","1","a","A".repeat(3_200_004)));
    }
    /** Aggregate decoded bytes are checked even when individual artifact encodings are valid. */
    @Test public void decodedAggregateLimitRejectsOtherwiseBoundedArtifacts() {
        String first=Base64.getEncoder().encodeToString(new byte[2_000_000]);
        String second=Base64.getEncoder().encodeToString(new byte[1_000_001]);
        corrupt(encoded("1","codec","handler","2","a",first,"b",second));
    }
    /** Inclusive byte ceilings remain usable after Base64 framing growth. */
    @Test public void maximumBytePackageRoundTripsWithinEncodedGrowthBound() {
        Map<String,byte[]> bytes=new HashMap<>();bytes.put("a",new byte[2_400_000]);bytes.put("b",new byte[600_000]);
        Memory memory=new Memory();InstallationStore store=new InstallationStore(memory);
        assertEquals(InstallationStore.CommitState.COMMITTED,store.commit(new InstallationSnapshot(new InstallRequest(1,"codec",bytes),"handler")));
        assertTrue(memory.values.get(InstallationStore.SNAPSHOT_KEY).length()<=InstallationSnapshotCodec.MAX_ENCODED_CHARACTERS);
        assertEquals(3_000_000,store.read().snapshot().canonical().packageBytes());
    }
    /** A valid generic marker wins deterministically while historical fields remain unmodified residue. */
    @Test public void genericPrecedencePreservesHistoricalFieldsWithoutAmbiguousFallback() {
        Memory memory=new Memory();memory.values.put("revision","13");memory.values.put("runtime","old opaque bytes");
        memory.values.put("manifest","old opaque manifest");memory.values.put("ackRevision","13");
        Map<String,String> old=new HashMap<>(memory.values);InstallationStore store=new InstallationStore(memory);
        assertEquals(InstallationStore.CommitState.COMMITTED,store.commit(snapshot(14)));
        for (String key:old.keySet()) assertTrue("historical residue retained",old.get(key).equals(memory.values.get(key)));
        assertEquals(14,store.read().snapshot().revision());assertEquals(13,store.read().acknowledgedRevision());
        memory.values.put("revision","broken");assertEquals(14,store.read().snapshot().revision());
    }
}
