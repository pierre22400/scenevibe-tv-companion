package com.scenevibe.tvcompanionpoc;

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

/** Compare every applicable frozen B sequence to the real candidate without changing a golden. */
@RunWith(Parameterized.class)
public final class M5CandidateDifferentialTest {
    private static final JSONObject TRACES=new JSONObject();
    private final JSONObject fixture;
    /** Own one real differential execution per named scheduler case. */
    public M5CandidateDifferentialTest(String id,JSONObject fixture) {this.fixture=fixture;}
    /** Retain all 82 oracle cases; the ten invalid model cases remain in their original B tests. */
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> fixtures() throws Exception {
        JSONArray cases=new JSONObject(new String(M5FrozenLegacyOracle.resource("corpus.json"),StandardCharsets.UTF_8)).getJSONArray("cases");
        List<Object[]> result=new ArrayList<>();
        for(int i=0;i<cases.length();i++) {JSONObject f=cases.getJSONObject(i);if(f.getString("kind").equals("oracle")) result.add(new Object[]{f.getString("id"),f});}
        assertEquals(82,result.size());return result;
    }
    /** Compare raw journals in this JVM, including tokens, empty frames and repeated callbacks. */
    @Test public void exactFrozenOracleEqualsCandidate() throws Exception {
        M5TemporalJournal old=M5FrozenLegacyOracle.execute(fixture),candidate=M5CDifferentialHarness.execute(fixture,true);
        assertTrue("Oracle/candidate divergence: "+fixture.getString("id"),M5TemporalJournal.exactlyEqual(old.frames(),candidate.frames()));
        if(!fixture.optBoolean("environmentDependent",false)) assertTrue("Frozen deterministic trace diverged",
                M5TemporalJournal.exactlyEqual(M5TemporalJournal.fromJson(fixture.getJSONArray("expected")),candidate.frames()));
        TRACES.put(fixture.getString("id"),new JSONObject().put("oracle",old.json()).put("candidate",candidate.json())
                .put("environmentDependent",fixture.optBoolean("environmentDependent",false)));
    }
    /** Persist actual raw evidence without modifying the frozen fixtures or the B journal semantics. */
    @AfterClass public static void saveRawTraces() throws Exception {
        Path path=Path.of("build/reports/m5-phase-c-differential.json");Files.createDirectories(path.getParent());
        assertEquals(82,TRACES.length());
        Files.write(path,(new JSONObject().put("compared",82).put("divergences",0).put("environment",System.getProperty("java.vm.name")+" "+System.getProperty("java.version"))
                .put("traces",TRACES).toString(2)+"\n").getBytes(StandardCharsets.UTF_8));
    }
}
