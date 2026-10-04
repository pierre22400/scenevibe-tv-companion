package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationSnapshot;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

/**
 * Read the frozen Phase A cache fixtures with the new storage contract, retaining exact
 * text/revision/ACK without mutation. Keep representation corruption separate from Video
 * semantics, whose current parser and restore authority remain in CloudTrackRepository.
 */
public final class M4PhaseCCompatibilityTest {
    /** Observe store writes over the exact same map used by the qualified runtime fixtures. */
    private static final class Backend implements InstallationStore.Backend {
        final Map<String,String> values;
        int writes;
        /** Share the historical test file without copying or rewriting its text. */
        Backend(Map<String,String> values) {this.values=values;}
        /** Coordinate recreated readers of the same durable fixture. */
        @Override public Object monitor() {return values;}
        /** Return exact historical String data without serialization. */
        @Override public String get(String key) {return values.get(key);}
        /** Observe a complete atomic persistence batch, separate from any scheduler. */
        @Override public boolean commit(Map<String,String> puts,Set<String> removed,boolean clear) {
            writes++;Map<String,String> staged=clear?new HashMap<>():new HashMap<>(values);
            staged.putAll(puts);for (String key:removed) staged.remove(key);
            values.clear();values.putAll(staged);return true;
        }
    }

    /** Require corrupt representation and no compatibility write for one edited historical tuple. */
    private static void corrupt(Map<String,String> values) {
        Map<String,String> before=new HashMap<>(values);Backend backend=new Backend(values);
        InstallationStore.ReadResult result=new InstallationStore(backend).read();
        assertEquals(InstallationStore.ReadState.CORRUPT,result.state());assertNull(result.snapshot());
        assertEquals(0,result.acknowledgedRevision());assertEquals(0,backend.writes);
        assertTrue("corrupt read does not erase cache",before.equals(values));
    }

    /** The manifested fixture preserves exact 13/13 and both original UTF-8 strings. */
    @Test public void historicalManifestedFixtureReadPreservesExactRevisionAckAndText() throws Exception {
        Map<String,String> values=M4PhaseAFixtures.cacheFixture(true).values;
        Map<String,String> before=new HashMap<>(values);Backend backend=new Backend(values);
        InstallationStore.ReadResult result=new InstallationStore(backend).read();
        assertEquals(InstallationStore.ReadState.SNAPSHOT,result.state());assertEquals(13,result.snapshot().revision());
        assertEquals(13,result.acknowledgedRevision());
        assertEquals(TvCapabilities.CODEC_TRACK_OVERLAY,result.snapshot().codecId());
        assertEquals(InstallationStore.COMPAT_OVERLAY_HANDLER_ID,result.snapshot().handlerId());
        for (String key:new String[]{"runtime","manifest"})
            assertTrue("fixture text bytes unchanged",Arrays.equals(values.get(key).getBytes(StandardCharsets.UTF_8),result.snapshot().canonical().artifact(key)));
        assertEquals(0,backend.writes);assertTrue("tuple unchanged",before.equals(values));
    }

    /** The legacy fixture preserves exact 14/13, runtime bytes and absence of any manifest. */
    @Test public void historicalLegacyFixtureReadPreservesExactRevisionAckAndAbsentManifest() throws Exception {
        Map<String,String> values=M4PhaseAFixtures.cacheFixture(false).values;Backend backend=new Backend(values);
        InstallationStore.ReadResult result=new InstallationStore(backend).read();
        assertEquals(InstallationStore.ReadState.SNAPSHOT,result.state());assertEquals(14,result.snapshot().revision());
        assertEquals(13,result.acknowledgedRevision());assertEquals(TvCapabilities.CODEC_TRACK,result.snapshot().codecId());
        assertEquals(InstallationStore.COMPAT_TRACK_HANDLER_ID,result.snapshot().handlerId());
        assertTrue("runtime UTF-8 unchanged",Arrays.equals(values.get("runtime").getBytes(StandardCharsets.UTF_8),result.snapshot().canonical().artifact("runtime")));
        assertNull(result.snapshot().canonical().artifact("manifest"));assertEquals(1,result.snapshot().canonical().artifactCount());
        assertFalse(values.containsKey("manifest"));assertEquals(0,backend.writes);
    }

    /** Missing acknowledgement is zero rather than the installation revision. */
    @Test public void historicalAbsentAckIsZeroWithoutWrite() throws Exception {
        Map<String,String> values=M4PhaseAFixtures.cacheFixture(true).values;values.remove("ackRevision");
        Backend backend=new Backend(values);InstallationStore.ReadResult result=new InstallationStore(backend).read();
        assertEquals(13,result.snapshot().revision());assertEquals(0,result.acknowledgedRevision());assertEquals(0,backend.writes);
    }

    /** Non-numeric, non-positive and overflowed historical revision metadata fails closed. */
    @Test public void malformedHistoricalRevisionFailsClosed() throws Exception {
        for (String revision:new String[]{"","bad","0","-1","9223372036854775808"}) {
            Map<String,String> values=M4PhaseAFixtures.cacheFixture(true).values;values.put("revision",revision);corrupt(values);
        }
    }

    /** Neither half of the historical base tuple nor an orphan manifest is a readable snapshot. */
    @Test public void incompleteHistoricalTuplesFailClosed() throws Exception {
        for (String missing:new String[]{"runtime","revision"}) {
            Map<String,String> values=M4PhaseAFixtures.cacheFixture(true).values;values.remove(missing);corrupt(values);
        }
        for (String lone:new String[]{"runtime","revision","manifest"}) {
            Map<String,String> values=new HashMap<>();values.put(lone,lone.equals("revision")?"13":"opaque");corrupt(values);
        }
    }

    /** Invalid or orphan ACK metadata never leaks a misleading acknowledged revision. */
    @Test public void malformedOrFutureHistoricalAckFailsClosed() throws Exception {
        for (String ack:new String[]{"","bad","-1","14","9223372036854775808"}) {
            Map<String,String> values=M4PhaseAFixtures.cacheFixture(true).values;values.put("ackRevision",ack);corrupt(values);
        }
        corrupt(new HashMap<>(Map.of("ackRevision","13")));
    }

    /** Reading compatibility beside a live manifested revision has no cache/scheduler/renderer effect. */
    @Test public void compatibilityReadCannotMutateOrRetireCurrentRuntime() throws Exception {
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(true);
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(memory);runtime.restore();runtime.due();
        Map<String,String> before=new HashMap<>(memory.values);long generation=runtime.controller.currentGeneration();
        int loads=runtime.loads;Backend backend=new Backend(memory.values);
        assertEquals(13,new InstallationStore(backend).read().snapshot().revision());
        assertEquals(0,backend.writes);assertEquals(0,memory.commits);assertEquals(0,memory.ackCommits);
        assertEquals(loads,runtime.loads);assertEquals(generation,runtime.controller.currentGeneration());
        assertTrue("durable bytes unchanged",before.equals(memory.values));
        assertTrue(runtime.controller.hasVisibleScene());assertEquals(1,runtime.sink.shows);assertEquals(0,runtime.sink.hides);
        assertEquals(0,runtime.legacyRenders);
    }

    /** Bounds precede encoding, and invalid UTF-16 cannot be silently replaced with another byte sequence. */
    @Test public void oversizedOrUnpairedHistoricalStringsFailClosed() throws Exception {
        for (String key:new String[]{"runtime","manifest"}) {
            Map<String,String> values=M4PhaseAFixtures.cacheFixture(true).values;
            values.put(key,"x".repeat(key.equals("runtime")?400_001:800_001));corrupt(values);
            values=M4PhaseAFixtures.cacheFixture(true).values;values.put(key,"broken\uD800");corrupt(values);
            values=M4PhaseAFixtures.cacheFixture(true).values;values.put(key,"");corrupt(values);
        }
    }

    /** Opaque representation acceptance is not Video semantic acceptance: the current parser still refuses bad JSON. */
    @Test public void semanticValidationRemainsInQualifiedCompatibilityRepository() throws Exception {
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(true);memory.values.put("runtime","not JSON");
        Backend backend=new Backend(memory.values);
        assertEquals(InstallationStore.ReadState.SNAPSHOT,new InstallationStore(backend).read().state());
        assertEquals(0,backend.writes);
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(memory);
        assertEquals(0,runtime.restore());assertEquals(0,runtime.loads);assertEquals(0,runtime.sink.shows);
        assertTrue(memory.values.isEmpty());
    }

    /** The historical write seam can acknowledge only its positive current revision. */
    @Test public void historicalAckFacadePreservesWrongRevisionAndRecordsExactCurrent() throws Exception {
        Map<String,String> values=M4PhaseAFixtures.cacheFixture(false).values;Backend backend=new Backend(values);
        InstallationStore store=new InstallationStore(backend);
        for (long wrong:new long[]{-1,0,13,15}) assertFalse(store.saveHistoricalAcknowledgement(wrong));
        assertEquals(0,backend.writes);assertEquals("13",values.get("ackRevision"));
        assertTrue(store.saveHistoricalAcknowledgement(14));assertEquals(1,backend.writes);
        assertEquals(14,store.read().acknowledgedRevision());
    }

    /** The storage facade independently refuses non-positive revisions and over-bound historical fields. */
    @Test public void invalidHistoricalWritesCannotCreateUnreadableRepresentation() {
        Map<String,String> values=new HashMap<>();Backend backend=new Backend(values);InstallationStore store=new InstallationStore(backend);
        assertFalse(store.saveHistorical(0,"opaque",null));assertFalse(store.saveHistorical(-1,"opaque",null));
        assertFalse(store.saveHistorical(1,null,null));assertFalse(store.saveHistorical(1,"",null));
        assertFalse(store.saveHistorical(1,"x".repeat(400_001),null));
        assertFalse(store.saveHistorical(1,"opaque",""));assertFalse(store.saveHistorical(1,"opaque","x".repeat(800_001)));
        assertEquals(0,backend.writes);assertTrue(values.isEmpty());
    }

    /** The old facade must not read, overwrite or ACK historical residue behind any generic marker. */
    @Test public void authoritativeGenericMarkerBlocksHistoricalFacadeWithoutRestoreCutover() throws Exception {
        for (String marker:new String[]{"broken",""}) {
            Map<String,String> values=M4PhaseAFixtures.cacheFixture(true).values;values.put(InstallationStore.SNAPSHOT_KEY,marker);
            Map<String,String> before=new HashMap<>(values);Backend backend=new Backend(values);InstallationStore store=new InstallationStore(backend);
            for (String key:new String[]{"revision","runtime","manifest","ackRevision"}) assertNull(store.historicalValue(key));
            assertFalse(store.saveHistorical(14,"opaque",null));assertFalse(store.saveHistoricalAcknowledgement(13));
            assertEquals(0,backend.writes);assertTrue("residue never rewritten",before.equals(values));
            assertEquals(InstallationStore.ReadState.CORRUPT,store.read().state());
        }
    }
}
