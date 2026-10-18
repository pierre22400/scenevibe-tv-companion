package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.PackageInstaller;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import com.scenevibe.tvcompanionpoc.presentation.TvPreparation;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Real V1 handlers/store preparation plus failure/restart evidence; no rendering ports are supplied. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 35})
public final class TvFirst008CacheTest {
    /** Frame exact Unicode bytes using the shared independently framed contract vector. */
    @Test public void digestMatchesIndependentCloudFrame() {
        Map<String,byte[]> artifacts = new HashMap<>();
        artifacts.put("runtime", "{\"text\":\"Été 😀\"}".getBytes(StandardCharsets.UTF_8));
        artifacts.put("manifest", "x\ny".getBytes(StandardCharsets.UTF_8));
        assertEquals("299557422f6e755ed1c848a4f694955fdf5cf88c14b736da7d527113473ffcc2", TvFirst008Wire.digest(new InstallRequest(1, "codec", artifacts)));
    }
    /** Prepare manifested and legacy bytes through actual semantic handlers, never ARM or ACK. */
    @Test public void bothCodecsPersistExactBytesAndRestartWithoutRuntimePorts() throws Exception {
        for (boolean manifest : new boolean[] {false, true}) {
            M4PhaseEVideoFixtures.Backend backend = new M4PhaseEVideoFixtures.Backend(new HashMap<>());
            InstallationStore store = new InstallationStore(backend);
            TvFirst008Wire.Envelope envelope = fixture(manifest, 1);
            assertTrue(new TvFirst008Cache(store).prepare(envelope));
            assertTrue(new TvFirst008Cache(new InstallationStore(backend)).matches(envelope.binding));
            assertEquals(0, backend.ackWrites); assertEquals(0, backend.clears); assertEquals(1, backend.writes);
            assertEquals(0, store.read().acknowledgedRevision());
            for (String key : envelope.request.artifacts().keySet()) assertArrayEquals(envelope.request.artifact(key), store.read().snapshot().canonical().artifact(key));
        }
    }
    /** A false commit cannot prove local readiness or publish a receipt. */
    @Test public void failedCommitDoesNotProduceCacheEvidence() throws Exception {
        M4PhaseEVideoFixtures.Backend backend = new M4PhaseEVideoFixtures.Backend(new HashMap<>()); backend.writable = false;
        TvFirst008Cache cache = new TvFirst008Cache(new InstallationStore(backend));
        TvFirst008Wire.Envelope envelope = fixture(false, 1);
        assertFalse(cache.prepare(envelope)); assertFalse(cache.matches(envelope.binding)); assertEquals(0, backend.ackWrites);
    }
    /** Same revision with different incoming bytes is refused; earlier revisions cannot overwrite. */
    @Test public void preparedRetryRequiresExactBytesAndRefusesStaleData() throws Exception {
        M4PhaseEVideoFixtures.Backend backend = new M4PhaseEVideoFixtures.Backend(new HashMap<>());
        PackageInstaller installer = new PackageInstaller(new InstallationStore(backend), VideoInstallationHandlers.registry(), TvCapabilities.current());
        InstallRequest request = fixture(false, 2).request;
        assertEquals(InstallationStatus.PREPARED, installer.prepareOnly(request));
        assertEquals(InstallationStatus.PREPARED, installer.prepareOnly(request));
        assertEquals(InstallationStatus.INVALID_PACKAGE, installer.prepareOnly(M4PhaseDHandlerFixtures.withArtifact(request, "runtime", "changed".getBytes(StandardCharsets.UTF_8))));
        assertEquals(InstallationStatus.STALE, installer.prepareOnly(new InstallRequest(1, request.codecId(), request.artifacts())));
        assertEquals(1, backend.writes); assertEquals(0, backend.ackWrites);
    }
    /** Digest tampering or binding substitution fails before installation. */
    @Test public void everyBindingSubstitutionAndCorruptArtifactIsRefused() throws Exception {
        TvFirst008Wire.Envelope envelope = fixture(true, 1);
        JSONObject raw = wire(envelope.request, envelope.binding);
        for (String key : new String[] {"deviceId", "generation", "intentionId", "finalTrackId", "revision", "codecId", "digest"}) {
            JSONObject changed = new JSONObject(raw.toString());
            changed.put(key, key.equals("revision") ? 2 : key.equals("digest") ? "0".repeat(64) : key.equals("codecId") ? TvCapabilities.CODEC_TRACK : UUID.randomUUID().toString());
            assertThrows(IllegalArgumentException.class, () -> new TvFirst008Wire.Envelope(changed, envelope.binding));
        }
        raw.getJSONObject("artifacts").put("runtime", Base64.getEncoder().encodeToString("bad".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> new TvFirst008Wire.Envelope(raw, envelope.binding));
    }
    /** Generation-specific real Android files never alter old cache, identity or ACK state. */
    @Test public void realBackendIsScopedAndReadinessNeedsSeparateConfirmedReceipt() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("cloud_track", 0).edit().putString("sentinel", "old").commit();
        TvFirst008Wire.Envelope envelope = fixture(false, 1);
        assertTrue(new TvFirst008Cache(context, envelope.binding.generation).prepare(envelope));
        assertEquals("old", context.getSharedPreferences("cloud_track", 0).getString("sentinel", null));
        assertFalse(new TvFirst008Cache(context, UUID.randomUUID().toString()).matches(envelope.binding));
        TvPreparation.Binding fact = new TvPreparation.Binding("offer", "intent", "device", "track", "digest", 1, "generation", "codec");
        TvPreparation noReceipt = new TvPreparation(TvPreparation.Stage.READY, null, false, TvPreparation.Evidence.cacheOnly(fact, fact, fact, null));
        assertFalse(noReceipt.readyFor("offer"));
        TvPreparation ready = new TvPreparation(TvPreparation.Stage.READY, null, false, TvPreparation.Evidence.cacheOnly(fact, fact, fact, fact));
        assertTrue(ready.readyFor("offer")); assertFalse(ready.mediaAdmittedFor("offer")); assertNull(ready.evidence.acknowledged);
        TvPreparation.Binding foreign = new TvPreparation.Binding("offer", "intent", "device", "track", "digest", 1, "other", "codec");
        assertFalse(new TvPreparation(TvPreparation.Stage.READY, null, false, TvPreparation.Evidence.cacheOnly(fact, fact, fact, foreign)).readyFor("offer"));
    }
    /** Produce canonical test artifacts from frozen, unchanged M4 fixture bytes. */
    static TvFirst008Wire.Envelope fixture(boolean manifested, long revision) throws Exception {
        InstallRequest request = M4PhaseEVideoFixtures.request(manifested, revision);
        JSONObject binding = new JSONObject().put("profile", TvFirst008Wire.PROFILE).put("deviceId", UUID.randomUUID().toString())
                .put("generation", UUID.randomUUID().toString()).put("intentionId", UUID.randomUUID().toString()).put("finalTrackId", UUID.randomUUID().toString())
                .put("revision", revision).put("codecId", request.codecId()).put("digest", TvFirst008Wire.digest(request));
        TvFirst008Wire.Binding expected = new TvFirst008Wire.Binding(binding);
        return new TvFirst008Wire.Envelope(wire(request, expected), expected);
    }
    /** Encode raw artifacts exactly as the production Cloud contract, without normalizing JSON. */
    static JSONObject wire(InstallRequest request, TvFirst008Wire.Binding binding) throws Exception {
        JSONObject artifacts = new JSONObject();
        for (Map.Entry<String,byte[]> entry : request.artifacts().entrySet()) artifacts.put(entry.getKey(), Base64.getEncoder().encodeToString(entry.getValue()));
        String track = new JSONObject(new String(request.artifact("runtime"), StandardCharsets.UTF_8)).getString("trackId");
        return binding.json().put("type", "scenevibe.cloud.cache-assignment.v1").put("trackId", track).put("artifacts", artifacts);
    }
}
