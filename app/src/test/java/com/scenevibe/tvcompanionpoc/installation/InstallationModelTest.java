package com.scenevibe.tvcompanionpoc.installation;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/**
 * Pure Phase B value tests: enforce bounds, byte ownership and immutable preparation.
 * Synthetic sentinel content is asserted through booleans so no failing test prints payloads.
 * No producer parser, storage, rendering or transport is simulated in these model tests.
 */
public final class InstallationModelTest {
    /** Make a small inert single-artifact handoff; these bytes are not claimed as parsed runtime data. */
    private static InstallRequest request() {
        return new InstallRequest(13,TvCapabilities.CODEC_TRACK,
                Collections.singletonMap("data","synthetic-content-sentinel".getBytes(StandardCharsets.UTF_8)));
    }
    /** Construct the qualified legacy freeze profile without decoding any artifact. */
    private static ExecutionRequirements requirements() {
        return new ExecutionRequirements(TvCapabilities.LEGACY_CONTRACT,ExecutionRequirements.ClockMode.MEDIA,
                ExecutionRequirements.PauseBehavior.FREEZE,1,false,false);
    }
    /** Preserve positive revisions exactly, including the long representation boundary. */
    @Test public void positiveRevisionIsRetainedExactly() {
        InstallRequest value=new InstallRequest(Long.MAX_VALUE,TvCapabilities.CODEC_TRACK,request().artifacts());
        assertEquals(Long.MAX_VALUE,value.revision());assertEquals(1,value.artifactCount());
    }
    /** An invalid revision is rejected before constructing a package. */
    @Test public void nonPositiveRevisionsAreRejected() {
        for (long revision:new long[]{0,-1,Long.MIN_VALUE})
            assertThrows(IllegalArgumentException.class,()->new InstallRequest(revision,TvCapabilities.CODEC_TRACK,request().artifacts()));
    }
    /** Unknown but well-formed ids can be handed off; malformed/URL/oversized ids cannot. */
    @Test public void identifierBoundsRejectInvalidValuesWithoutTruncating() {
        for (String id:Arrays.asList(null,"","a/b","https://invalid.example",String.join("",Collections.nCopies(129,"a"))))
            assertThrows(IllegalArgumentException.class,()->new InstallRequest(1,id,request().artifacts()));
        String longest=String.join("",Collections.nCopies(128,"a"));
        assertEquals(longest,new InstallRequest(1,longest,request().artifacts()).codecId());
    }
    /** The artifact-set count is bounded before arrays are copied. */
    @Test public void absentOrExcessArtifactSetsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new InstallRequest(1,TvCapabilities.CODEC_TRACK,null));
        assertThrows(IllegalArgumentException.class,()->new InstallRequest(1,TvCapabilities.CODEC_TRACK,Collections.emptyMap()));
        Map<String,byte[]> excess=new HashMap<>();
        for (String name:new String[]{"a","b","c"}) excess.put(name,new byte[]{1});
        assertThrows(IllegalArgumentException.class,()->new InstallRequest(1,TvCapabilities.CODEC_TRACK,excess));
    }
    /** Each artifact has a bounded non-empty byte representation and a safe identifier. */
    @Test public void invalidArtifactIdsAndSizesAreRejected() {
        for (byte[] bytes:new byte[][]{null,new byte[0],new byte[2_400_001]})
            assertThrows(IllegalArgumentException.class,()->new InstallRequest(1,TvCapabilities.CODEC_TRACK,
                    Collections.singletonMap("data",bytes)));
        assertThrows(IllegalArgumentException.class,()->new InstallRequest(1,TvCapabilities.CODEC_TRACK,
                Collections.singletonMap("unsafe/name",new byte[]{1})));
    }
    /** Aggregate bounds reject a package even when both artifacts are individually bounded. */
    @Test public void totalPackageBytesAreBounded() {
        Map<String,byte[]> bytes=new HashMap<>();bytes.put("a",new byte[2_000_000]);bytes.put("b",new byte[1_000_001]);
        assertThrows(IllegalArgumentException.class,()->new InstallRequest(1,TvCapabilities.CODEC_TRACK_OVERLAY,bytes));
        bytes.put("b",new byte[1_000_000]);
        assertEquals(3_000_000,new InstallRequest(1,TvCapabilities.CODEC_TRACK_OVERLAY,bytes).packageBytes());
    }
    /** The largest single-artifact ceiling is inclusive and is not confused with characters. */
    @Test public void exactArtifactByteCeilingIsAccepted() {
        InstallRequest value=new InstallRequest(1,TvCapabilities.CODEC_TRACK,
                Collections.singletonMap("data",new byte[2_400_000]));
        assertEquals(2_400_000,value.packageBytes());
    }
    /** Later mutation of the caller's arrays/map cannot alter retained canonical bytes. */
    @Test public void requestDefensivelyCopiesInputMapAndArrays() {
        byte[] bytes=new byte[]{1,2};Map<String,byte[]> supplied=new HashMap<>();supplied.put("data",bytes);
        InstallRequest value=new InstallRequest(13,TvCapabilities.CODEC_TRACK,supplied);
        bytes[0]=9;supplied.clear();
        assertTrue("owned bytes unchanged",Arrays.equals(new byte[]{1,2},value.artifact("data")));
        assertEquals(2,value.packageBytes());
    }
    /** Every output array is detached and structural collections are unmodifiable. */
    @Test public void requestDefensivelyCopiesAllOutputArrays() {
        InstallRequest value=request();byte[] before=value.artifact("data");
        value.artifact("data")[0]=0;
        Map<String,byte[]> output=value.artifacts();output.get("data")[0]=0;
        assertThrows(UnsupportedOperationException.class,()->output.put("extra",new byte[]{1}));
        assertTrue("canonical bytes unchanged",Arrays.equals(before,value.artifact("data")));
    }
    /** Null/unknown artifact lookups are absent, not crashes or loading requests. */
    @Test public void unknownAndNullArtifactLookupsAreAbsent() {
        assertNull(request().artifact(null));assertNull(request().artifact("unknown"));
    }
    /** Iteration order is canonical regardless of source insertion order. */
    @Test public void artifactIterationIsDeterministic() {
        Map<String,byte[]> values=new HashMap<>();values.put("z",new byte[]{1});values.put("a",new byte[]{2});
        InstallRequest value=new InstallRequest(1,TvCapabilities.CODEC_TRACK_OVERLAY,values);
        assertEquals(Arrays.asList("a","z"),new java.util.ArrayList<>(value.artifacts().keySet()));
    }
    /** Unicode bytes survive the inert handoff unchanged, including combining accents and emoji. */
    @Test public void unicodeBytesAreNeverNormalizedOrTruncated() {
        byte[] bytes="Été — cœur / e\u0301 🇫🇷".getBytes(StandardCharsets.UTF_8);
        InstallRequest value=new InstallRequest(13,TvCapabilities.CODEC_TRACK,Collections.singletonMap("data",bytes));
        assertTrue("UTF-8 bytes unchanged",Arrays.equals(bytes,value.artifact("data")));
    }
    /** Prepared values retain one revision/codec, immutable requirements and memory-only status. */
    @Test public void preparedValueIsImmutableAndNeverClaimsArmed() {
        Map<String,String> values=new HashMap<>();values.put("count","1");
        InstallRequest canonical=request();ExecutionRequirements needs=requirements();
        PreparedInstallation prepared=new PreparedInstallation(canonical,"handler:track",values,needs,TvCapabilities.current());
        values.put("count","2");
        assertEquals("1",prepared.values().get("count"));
        assertThrows(UnsupportedOperationException.class,()->prepared.values().clear());
        assertSame(canonical,prepared.canonical());assertSame(needs,prepared.requirements());
        assertEquals(13,prepared.revision());assertEquals(TvCapabilities.CODEC_TRACK,prepared.codecId());
        assertEquals("handler:track",prepared.handlerId());assertEquals(InstallationStatus.PREPARED,prepared.status());
        assertNotEquals(InstallationStatus.ARMED,prepared.status());
    }
    /** Invalid local preparation never converts unsupported requirements into an executable claim. */
    @Test public void preparedInstallationRejectsUnsupportedOrMissingInputs() {
        ExecutionRequirements wall=new ExecutionRequirements(TvCapabilities.LEGACY_CONTRACT,ExecutionRequirements.ClockMode.WALL,
                ExecutionRequirements.PauseBehavior.FREEZE,1,false,false);
        assertThrows(IllegalArgumentException.class,()->new PreparedInstallation(request(),"handler",Collections.emptyMap(),wall,TvCapabilities.current()));
        assertThrows(IllegalArgumentException.class,()->new PreparedInstallation(null,"handler",Collections.emptyMap(),requirements(),TvCapabilities.current()));
        assertThrows(IllegalArgumentException.class,()->new PreparedInstallation(request(),"handler",Collections.emptyMap(),requirements(),null));
        assertThrows(IllegalArgumentException.class,()->new PreparedInstallation(request(),null,Collections.emptyMap(),requirements(),TvCapabilities.current()));
    }
    /** Prepared scalar count, key and value bounds are enforced without accepting mutable objects. */
    @Test public void preparedScalarBoundsAreEnforced() {
        Map<String,String> excess=new HashMap<>();for(int i=0;i<257;i++) excess.put("v"+i,"1");
        for (Map<String,String> invalid:Arrays.asList(excess,Collections.singletonMap("value",(String)null),
                Collections.singletonMap("unsafe/key","1"),Collections.singletonMap("value",String.join("",Collections.nCopies(4001,"a")))))
            assertThrows(IllegalArgumentException.class,()->new PreparedInstallation(request(),"handler",invalid,requirements(),TvCapabilities.current()));
    }
    /** The total encoded size of individually bounded prepared scalars remains bounded. */
    @Test public void preparedScalarsHaveAnAggregateUtf8Bound() {
        Map<String,String> values=new HashMap<>();String value=String.join("",Collections.nCopies(4000,"é"));
        for(int i=0;i<256;i++) values.put("v"+i,value);
        assertEquals(256,new PreparedInstallation(request(),"handler",values,requirements(),TvCapabilities.current()).values().size());
        String larger=String.join("",Collections.nCopies(4000,"中"));
        for(int i=0;i<256;i++) values.put("v"+i,larger);
        assertThrows(IllegalArgumentException.class,()->new PreparedInstallation(request(),"handler",values,requirements(),TvCapabilities.current()));
    }
    /** Bounded fixed errors never echo a submitted identifier; values have no content-bearing toString. */
    @Test public void errorsAndDefaultStringsDoNotExposePayloads() {
        String sentinel="synthetic-content-sentinel";
        Exception error=assertThrows(IllegalArgumentException.class,()->new InstallRequest(1,sentinel+"/",request().artifacts()));
        assertFalse(error.getMessage().contains(sentinel));assertFalse(request().toString().contains(sentinel));
        PreparedInstallation value=new PreparedInstallation(request(),"handler",Collections.singletonMap("value",sentinel),requirements(),TvCapabilities.current());
        assertFalse(value.toString().contains(sentinel));
    }
    /** Result vocabulary is finite and has no caller-defined diagnostic/payload slot. */
    @Test public void statusesAreExactlyTheClosedLifecycleVocabulary() {
        assertEquals(Arrays.asList("VALIDATED","PREPARED","ARMED","STALE","UNSUPPORTED_CAPABILITY","INVALID_PACKAGE","CACHE_FAILED","ARM_FAILED"),
                Arrays.asList(Arrays.stream(InstallationStatus.values()).map(Enum::name).toArray(String[]::new)));
    }
}
