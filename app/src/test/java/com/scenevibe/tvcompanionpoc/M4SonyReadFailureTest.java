package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationSnapshot;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Distinct persistence/codec/ACK faults remain fail-closed and render only the fixed observational enum. */
@RunWith(Parameterized.class)
public final class M4SonyReadFailureTest {
    private final String scenario;
    private final InstallationStore.ReadFailure expected;

    /** Bind one deterministic observation without modifying a retained test or supplying native persistence claims. */
    public M4SonyReadFailureTest(String scenario,InstallationStore.ReadFailure expected) {
        this.scenario=scenario;this.expected=expected;
    }

    /** Exercise every failure enum and coherent empty/generic/historical readings through the actual projection. */
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> scenarios() {
        return Arrays.asList(new Object[][]{
                {"empty",InstallationStore.ReadFailure.NONE},{"generic",InstallationStore.ReadFailure.NONE},
                {"historical",InstallationStore.ReadFailure.NONE},{"invalid-generic",InstallationStore.ReadFailure.GENERIC_INVALID},
                {"invalid-historical",InstallationStore.ReadFailure.HISTORICAL_INVALID},{"invalid-ack",InstallationStore.ReadFailure.ACK_INVALID},
                {"generic-ahead",InstallationStore.ReadFailure.ACK_AHEAD_GENERIC},
                {"historical-ahead",InstallationStore.ReadFailure.ACK_AHEAD_WITHOUT_GENERIC},
                {"backend",InstallationStore.ReadFailure.BACKEND_READ_FAILED}});
    }

    /** Render exact fault classes without retaining submitted strings, raw exceptions, authority or any storage mutation. */
    @Test public void fixedObservationDoesNotChangeReadStateAuthorityOrLeakContent() {
        Map<String,String> values=new HashMap<>();
        InstallationStore.Backend backend=new InstallationStore.Backend() {
            /** Keep all candidate/ACK operations under the same local monitor. */
            @Override public Object monitor() {return this;}
            /** Inject only a read-boundary fault; its arbitrary message must never reach the result or view. */
            @Override public String get(String key) {
                if ("backend".equals(scenario)) throw new IllegalStateException("private https://secret.invalid token stack");
                return values.get(key);
            }
            /** Setup may commit a valid candidate; actual diagnostic reads must leave the entire map unchanged. */
            @Override public boolean commit(Map<String,String> batch,Set<String> removed,boolean clear) {
                if (clear) values.clear();values.putAll(batch);for (String key:removed) values.remove(key);return true;
            }
        };
        InstallationStore store=new InstallationStore(backend);
        if (scenario.equals("generic")||scenario.equals("generic-ahead"))
            store.commit(new InstallationSnapshot(new InstallRequest(15,"opaque.v1",Map.of("bytes",new byte[]{1})),"opaque.v1"));
        if (scenario.contains("historical")) {values.put("revision","13");values.put("runtime","private historical bytes");}
        if (scenario.equals("invalid-generic")) {values.put(InstallationStore.SNAPSHOT_KEY,"private invalid generic bytes");values.put("revision","13");values.put("runtime","old");}
        if (scenario.equals("invalid-historical")) values.put("revision","broken");
        if (scenario.equals("invalid-ack")) values.put("ackRevision","private invalid ACK");
        if (scenario.endsWith("-ahead")) values.put("ackRevision","16");
        Map<String,String> before=new HashMap<>(values);InstallationStore.ReadResult read=store.read();
        assertEquals(expected,read.failure());RuntimeDiagnostics diagnostic=RuntimeDiagnostics.installationSnapshot(store,new DiagnosticsStore()).build();
        assertEquals(expected,diagnostic.installationReadFailure);
        String text=DiagnosticsActivity.render(diagnostic);assertTrue(text.contains("Installation read failure: "+expected.name()));
        assertFalse(text.contains("private"));assertFalse(text.contains("https://"));assertFalse(text.contains("token"));assertFalse(text.contains("stack"));
        if (expected!=InstallationStore.ReadFailure.NONE) {
            assertEquals(InstallationStore.ReadState.CORRUPT,read.state());assertNull(read.snapshot());assertEquals(0,read.acknowledgedRevision());
            assertFalse(diagnostic.installationPresent);assertEquals(0,diagnostic.installedRevision);assertEquals(0,diagnostic.acknowledgedRevision);
        } else assertEquals("empty".equals(scenario)?InstallationStore.ReadState.EMPTY:InstallationStore.ReadState.SNAPSHOT,read.state());
        assertEquals(before,values);
    }
}
