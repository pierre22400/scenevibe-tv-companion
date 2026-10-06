package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/**
 * Characterizes the qualified installation cores before M4 changes their responsibilities.
 * All transitions use the existing repository, service helpers, bridge and media scheduler.
 * Fixtures represent the historical preference format, not a future installation format.
 */
public final class M4PhaseAInstallationTest {
    /** Process recreation restores the old manifested cache without rewriting any UTF-8 byte. */
    @Test public void manifestedCacheRestoresArmedNotVisibleAndPreservesUnicode() throws Exception {
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(true);
        Map<String,String> before=new HashMap<>(memory.values);
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(memory);
        assertEquals(13,runtime.restore());
        assertEquals(13,runtime.cache.acknowledged());
        assertTrue(runtime.controller.isSceneRendererActiveFor(13));
        assertFalse(runtime.controller.hasVisibleScene());
        assertEquals(0,runtime.sink.shows);
        assertEquals(0,memory.commits);
        assertTrue("cache bytes unchanged",before.equals(memory.values));
        assertTrue("Unicode sequence unchanged",M4PhaseAFixtures.UNICODE.equals(runtime.firstEvent().text));
        runtime.due();
        assertEquals(1,runtime.sink.shows);
        assertEquals(0,runtime.legacyRenders);
    }

    /** The manifested-first restore must preserve a legacy cache and its independent ACK. */
    @Test public void legacyCacheRestoresThroughLegacyOwnerWithoutClearingIt() throws Exception {
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(false);
        Map<String,String> before=new HashMap<>(memory.values);
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(memory);
        assertEquals(14,runtime.restore());
        assertEquals(13,runtime.cache.acknowledged());
        assertFalse(runtime.controller.hasActiveManifest());
        assertNull(memory.values.get("manifest"));
        assertEquals(0,memory.clears);
        assertTrue("cache bytes unchanged",before.equals(memory.values));
        assertTrue("Unicode sequence unchanged",M4PhaseAFixtures.UNICODE.equals(runtime.firstEvent().text));
        runtime.due();
        assertEquals(1,runtime.legacyRenders);
        assertEquals(0,runtime.sink.shows);
    }

    /** A rejected durable write cannot load the scheduler, arm the replacement or damage the prior tuple. */
    @Test public void commitFailureKeepsPriorRevisionAndAckAndNeverArms() throws Exception {
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(true);
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(memory);
        runtime.restore();
        Map<String,String> before=new HashMap<>(memory.values);
        int loads=runtime.loads;
        memory.writable=false;
        assertFalse(runtime.apply(M4PhaseAFixtures.envelope(0,14)));
        assertEquals(loads,runtime.loads);
        assertEquals(13,runtime.controller.activeRevision());
        assertEquals(13,runtime.cache.acknowledged());
        assertTrue("failed commit preserves entire tuple",before.equals(memory.values));
        assertEquals(0,memory.commits);
    }

    /** Cross-contract timing rejection precedes any write or scheduler mutation. */
    @Test public void timingMismatchIsRejectedBeforeDurableInstall() throws Exception {
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(true);
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(memory);
        runtime.restore();
        Map<String,String> before=new HashMap<>(memory.values);
        JSONObject incoming=M4PhaseAFixtures.envelope(0,14);
        JSONObject scene=incoming.getJSONObject("overlayManifest").getJSONArray("scenes").getJSONObject(0);
        scene.put("startMs",scene.getLong("startMs")+1);
        int loads=runtime.loads;
        assertFalse(runtime.apply(incoming));
        assertTrue("invalid replacement preserves tuple",before.equals(memory.values));
        assertEquals(loads,runtime.loads);
        assertEquals(0,memory.commits);
    }

    /** Both the envelope gate and application gate reject an older revision without side effects. */
    @Test public void staleRevisionCannotReplaceOrArmOrAdvanceAck() throws Exception {
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(true);
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(memory);
        runtime.restore();
        JSONObject stale=M4PhaseAFixtures.envelope(0,12);
        Map<String,String> before=new HashMap<>(memory.values);
        int loads=runtime.loads;
        assertFalse(CloudProtocol.validAssignment(stale,stale.getString("deviceId"),13));
        assertFalse(runtime.apply(stale));
        assertTrue("stale delivery leaves durable tuple",before.equals(memory.values));
        assertEquals(loads,runtime.loads);
        assertEquals(13,runtime.controller.activeRevision());
        assertEquals(0,memory.commits);
    }

    /** An already armed redelivery retains the durable bytes, generation and visible scene. */
    @Test public void sameRevisionRedeliveryDoesNotRepersistOrReplaceVisibleScene() throws Exception {
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(true);
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(memory);
        runtime.restore();runtime.due();
        Map<String,String> before=new HashMap<>(memory.values);
        long generation=runtime.controller.currentGeneration();
        int loads=runtime.loads;
        assertTrue(runtime.apply(M4PhaseAFixtures.envelope(0,13)));
        assertTrue("redelivery uses durable bytes",before.equals(memory.values));
        assertEquals(generation,runtime.controller.currentGeneration());
        assertEquals(loads,runtime.loads);
        assertEquals(1,runtime.sink.shows);
        assertEquals(0,memory.commits);
    }

    /** A cold manifested redelivery arms from the cached copy rather than incoming text. */
    @Test public void coldRedeliveryConfirmsTheDurableManifestedCopy() throws Exception {
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(true);
        Map<String,String> before=new HashMap<>(memory.values);
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(memory);
        assertFalse(runtime.controller.hasActiveManifest());
        assertTrue(runtime.apply(M4PhaseAFixtures.envelope(0,13)));
        assertTrue(runtime.controller.isSceneRendererActiveFor(13));
        assertFalse(runtime.controller.hasVisibleScene());
        assertTrue("durable tuple authoritative",before.equals(memory.values));
        assertTrue("cached Unicode authoritative",M4PhaseAFixtures.UNICODE.equals(runtime.firstEvent().text));
        assertEquals(0,memory.commits);
    }

    /** A legacy redelivery after normal service restore neither repersists nor double-renders. */
    @Test public void legacyRedeliveryAfterRestoreKeepsLegacyOwner() throws Exception {
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(false);
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(memory);
        runtime.restore();
        JSONObject legacy=M4PhaseAFixtures.envelope(0,14);
        legacy.remove("overlayManifest");legacy.remove("visualMode");
        Map<String,String> before=new HashMap<>(memory.values);
        int loads=runtime.loads;
        assertTrue(runtime.apply(legacy));
        assertEquals(loads,runtime.loads);
        assertEquals(0,memory.commits);
        assertTrue("legacy bytes remain authoritative",before.equals(memory.values));
        runtime.due();
        assertEquals(1,runtime.legacyRenders);
        assertEquals(0,runtime.sink.shows);
    }

    /** Replacement retires the visible old owner and ignores old callbacks, even with identical ids. */
    @Test public void manifestedReplacementNeutralizesOldDueAndExpiryCallbacks() throws Exception {
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(true);
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(memory);
        runtime.restore();runtime.due();
        ScheduledTrack.Event old=runtime.firstEvent();
        long oldGeneration=runtime.controller.currentGeneration();
        assertTrue(runtime.apply(M4PhaseAFixtures.envelope(0,14)));
        assertEquals(0,runtime.sink.visible);
        assertEquals(13,runtime.cache.acknowledged());
        assertTrue(runtime.controller.currentGeneration()>oldGeneration);
        runtime.controller.onEventDue(old.id,oldGeneration);
        assertEquals(0,runtime.sink.visible);
        runtime.due();
        assertEquals(1,runtime.sink.visible);
        runtime.controller.onEventExpired(old.id,oldGeneration);
        assertEquals(1,runtime.sink.visible);
        runtime.controller.onEventDue(runtime.firstEvent().id);
        assertEquals(2,runtime.sink.shows);
        assertEquals(1,runtime.sink.maxVisible);
        assertEquals(0,runtime.legacyRenders);
    }

    /** Manifested N to legacy N+1 atomically removes the manifest and returns the sole visual owner. */
    @Test public void manifestedToLegacyReplacementDisarmsBeforeAckEligibility() throws Exception {
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(true);
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(memory);
        runtime.restore();runtime.due();
        ScheduledTrack.Event old=runtime.firstEvent();
        long oldGeneration=runtime.controller.currentGeneration();
        JSONObject legacy=M4PhaseAFixtures.envelope(0,14);
        legacy.remove("overlayManifest");legacy.remove("visualMode");
        assertTrue(runtime.apply(legacy));
        assertNull(memory.values.get("manifest"));
        assertFalse(runtime.controller.hasActiveManifest());
        assertEquals(0,runtime.sink.visible);
        assertTrue(memory.trace.indexOf("commit:14")<memory.trace.indexOf("legacy-arm:14"));
        runtime.controller.onEventDue(old.id,oldGeneration);
        runtime.due();
        assertEquals(1,runtime.legacyRenders);
        assertEquals(1,runtime.sink.shows);
        assertEquals(0,runtime.sink.visible);
        assertEquals(13,runtime.cache.acknowledged());
    }

    /** Installation and confirmed acknowledgement are separate durable transitions. */
    @Test public void acknowledgedRevisionAdvancesOnlyForTheInstalledRevision() throws Exception {
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(true);
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(memory);
        runtime.restore();
        assertTrue(runtime.apply(M4PhaseAFixtures.envelope(0,14)));
        String track=memory.values.get("runtime"),manifest=memory.values.get("manifest");
        assertEquals(13,runtime.cache.acknowledged());
        assertFalse(runtime.cache.markAcknowledged(13));
        memory.ackWritable=false;
        assertFalse(runtime.cache.markAcknowledged(14));
        assertEquals(13,runtime.cache.acknowledged());
        memory.ackWritable=true;
        assertTrue(runtime.cache.markAcknowledged(14));
        assertEquals(14,runtime.cache.acknowledged());
        assertTrue("runtime unchanged by ACK",track.equals(memory.values.get("runtime")));
        assertTrue("manifest unchanged by ACK",manifest.equals(memory.values.get("manifest")));
        M4PhaseAFixtures.Runtime recreated=new M4PhaseAFixtures.Runtime(memory);
        assertEquals(14,recreated.restore());
        assertEquals(14,recreated.cache.acknowledged());
        assertFalse(recreated.controller.hasVisibleScene());
    }

    /** Either corrupt artifact fails closed; no half of a manifested revision reaches the scheduler. */
    @Test public void corruptManifestOrRuntimeCannotHalfRestore() throws Exception {
        for (String key:new String[]{"runtime","manifest"}) {
            M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(true);
            memory.values.put(key,"{invalid-json");
            M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(memory);
            assertEquals(0,runtime.restore());
            assertEquals(0,runtime.loads);
            assertFalse(runtime.controller.hasActiveManifest());
            assertEquals(0,runtime.cache.revision());
            assertTrue("corrupt tuple discarded together",memory.values.isEmpty());
        }
    }
}
