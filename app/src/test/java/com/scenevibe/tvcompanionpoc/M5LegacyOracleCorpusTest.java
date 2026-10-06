package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.calendar.MediaCalendar;
import com.scenevibe.tvcompanionpoc.calendar.SceneEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/**
 * Frozen B fixtures executable against the actual historical engine, without a
 * candidate. Model-invalid inputs are rejected at the pure-value boundary and
 * never misrepresented as legacy parser/scheduler validation. HashMap-dependent
 * cases retain raw traces and compare repeat executions in the same JVM only.
 */
@RunWith(Parameterized.class)
public final class M5LegacyOracleCorpusTest {
    private static final JSONObject TRACES = new JSONObject();
    private final JSONObject fixture;

    /** Bind a single named fixture to its independent real JUnit execution. */
    public M5LegacyOracleCorpusTest(String id, JSONObject fixture) {
        this.fixture = fixture;
    }

    /** Read the frozen corpus without deriving expected effects from the engine. */
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> fixtures() throws Exception {
        JSONArray cases = new JSONObject(new String(M5FrozenLegacyOracle.resource("corpus.json"),
                StandardCharsets.UTF_8)).getJSONArray("cases");
        List<Object[]> result = new ArrayList<>();
        for (int i = 0; i < cases.length(); i++) {
            JSONObject fixture = cases.getJSONObject(i);
            result.add(new Object[] {fixture.getString("id"), fixture});
        }
        return result;
    }

    /** Lock deterministic traces; keep environment cases raw for C's same-JVM differential. */
    @Test
    public void qualifiedLegacyOrModelBoundaryMatchesFixture() throws Exception {
        if (fixture.getString("kind").equals("model-rejection")) {
            assertRejectedModel();
            return;
        }
        M5TemporalJournal actual = M5FrozenLegacyOracle.execute(fixture);
        assertEquals(fixture.getJSONArray("actions").length(), actual.frames().size());
        if (fixture.optBoolean("environmentDependent", false)) {
            M5TemporalJournal repeated = M5FrozenLegacyOracle.execute(fixture);
            assertTrue("Same-environment raw trace diverged",
                    M5TemporalJournal.exactlyEqual(actual.frames(), repeated.frames()));
        } else {
            assertTrue("Exact frozen legacy trace diverged",
                    M5TemporalJournal.exactlyEqual(M5TemporalJournal.fromJson(
                            fixture.getJSONArray("expected")), actual.frames()));
        }
        TRACES.put(fixture.getString("id"), new JSONObject()
                .put("environmentDependent", fixture.optBoolean("environmentDependent", false))
                .put("frames", actual.json()));
    }

    /** Apply invalid fixtures to the new model only, leaving Video ingress intact. */
    private void assertRejectedModel() throws org.json.JSONException {
        try {
            List<SceneEvent> events = new ArrayList<>();
            JSONArray entries = fixture.getJSONArray("events");
            for (int i = 0; i < entries.length(); i++) {
                JSONObject event = entries.getJSONObject(i);
                events.add(new SceneEvent(event.isNull("id") ? null : event.getString("id"),
                        event.getLong("start"), event.getLong("duration")));
            }
            new MediaCalendar(events, fixture.optBoolean("freeze", true));
            fail("Invalid model fixture was accepted");
        } catch (IllegalArgumentException expected) {
            assertNull(expected.getCause());
            assertTrue(expected.getMessage().equals("Invalid scene event")
                    || expected.getMessage().equals("Invalid media calendar"));
        }
    }

    /** Preserve actual unsorted journal evidence separately from frozen expectations. */
    @AfterClass
    public static void saveRawTraces() throws Exception {
        Path destination = Path.of("build/reports/m5-phase-b-oracle-traces.json");
        Files.createDirectories(destination.getParent());
        String result = new JSONObject().put("candidateImplemented", false)
                .put("environment", System.getProperty("java.vm.name") + " " + System.getProperty("java.version"))
                .put("traces", TRACES).toString(2) + "\n";
        Files.write(destination, result.getBytes(StandardCharsets.UTF_8));
    }
}
