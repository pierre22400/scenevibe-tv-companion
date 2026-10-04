package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationHandler;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import org.json.JSONObject;
import static org.junit.Assert.*;

/** Reuse frozen cache/M1 bytes; failures report bounded case labels, never artifact content. */
final class M4PhaseDHandlerFixtures {
    /** Load the exact historical runtime from the committed manifested or legacy fixture. */
    static JSONObject runtime(boolean manifested) throws Exception {
        return new JSONObject(M4PhaseAFixtures.cacheFixture(manifested).values.get("runtime"));
    }
    /** Load the exact historical graphical fixture without changing its file or digest. */
    static JSONObject manifest() throws Exception {
        return new JSONObject(M4PhaseAFixtures.cacheFixture(true).values.get("manifest"));
    }
    /** Preserve raw committed fixture strings as exact canonical UTF-8 request bytes. */
    static InstallRequest request(boolean manifested) throws Exception {
        M4PhaseAFixtures.Memory cache=M4PhaseAFixtures.cacheFixture(manifested);
        Map<String,byte[]> artifacts=new TreeMap<>();
        artifacts.put("runtime",cache.values.get("runtime").getBytes(StandardCharsets.UTF_8));
        if (manifested) artifacts.put("manifest",cache.values.get("manifest").getBytes(StandardCharsets.UTF_8));
        return new InstallRequest(Long.parseLong(cache.values.get("revision")),
                manifested?TvCapabilities.CODEC_TRACK_OVERLAY:TvCapabilities.CODEC_TRACK,artifacts);
    }
    /** Encode intentional test changes as request bytes without rewriting any frozen fixture. */
    static InstallRequest request(JSONObject runtime,JSONObject manifest) {
        Map<String,byte[]> artifacts=new TreeMap<>();
        artifacts.put("runtime",runtime.toString().getBytes(StandardCharsets.UTF_8));
        if (manifest!=null) artifacts.put("manifest",manifest.toString().getBytes(StandardCharsets.UTF_8));
        return new InstallRequest(21,manifest==null?TvCapabilities.CODEC_TRACK:TvCapabilities.CODEC_TRACK_OVERLAY,artifacts);
    }
    /** Replace one detached artifact for boundary tests, preserving immutable request semantics. */
    static InstallRequest withArtifact(InstallRequest request,String key,byte[] bytes) {
        Map<String,byte[]> artifacts=new TreeMap<>(request.artifacts());
        if (bytes==null) artifacts.remove(key);else artifacts.put(key,bytes);
        return new InstallRequest(request.revision(),request.codecId(),artifacts);
    }
    /** Prove validate, live prepare and durable restore all reject with fixed closed errors. */
    static void rejected(InstallationHandler handler,InstallRequest request,InstallationStatus expected) {
        assertEquals(expected,handler.validate(request,TvCapabilities.current()));
        for (boolean restore:new boolean[]{false,true}) {
            try {
                if (restore) handler.restoreFromCache(request,TvCapabilities.current());
                else handler.prepare(request,TvCapabilities.current());
                fail("Invalid preparation accepted");
            } catch (IllegalArgumentException invalid) {
                assertTrue("bounded failure label",expected.name().equals(invalid.getMessage()));
                assertNull("no submitted-content cause",invalid.getCause());
            }
        }
    }
    /** Test-only immutable fixture helper has no live instance. */
    private M4PhaseDHandlerFixtures() {}
}
