package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import com.scenevibe.tvcompanionpoc.audio.LocalAudioPort;
import com.scenevibe.tvcompanionpoc.calendar.MediaObservation;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/** Exercise the variant's Android preference backend and real calendar under offline observations. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public final class NetflixLocalAdmissionTest {
    private Context context;
    private byte[] zip;
    private NetflixLocalAdmission.Selection selection;

    /** Load the detached, committed qualification reference independently of the package. */
    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        zip = resource("track.zip");
        JSONObject reference = new JSONObject(new String(resource("selection.json"), StandardCharsets.UTF_8));
        selection = new NetflixLocalAdmission.Selection(reference.getString("trackId"),
                reference.getString("programId"), reference.getLong("revision"), reference.getString("sha256"));
    }

    /** Read exactly one fixture resource supplied by the test source set. */
    private byte[] resource(String name) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/netflix003a/" + name)) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    /** Decode the actual fixture MP3 via FFmpeg and return its PCM sample duration. */
    private static long decode(byte[] bytes) throws Exception {
        Path file = Files.createTempFile("scenevibe-003a-", ".mp3");
        try {
            Files.write(file, bytes);
            Process decoder = new ProcessBuilder("ffmpeg", "-v", "error", "-i", file.toString(),
                    "-f", "s16le", "-ac", "1", "-ar", "44100", "pipe:1")
                    .redirectErrorStream(true).start();
            byte[] pcm = decoder.getInputStream().readAllBytes();
            if (decoder.waitFor() != 0 || pcm.length == 0) throw new IllegalArgumentException("MP3_DECODE_FAILED");
            return Math.round(pcm.length / 2d / 44100d * 1000d);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** Open the normal package-scoped entry with no Cloud client. */
    private NetflixLocalAdmission open(NetflixLocalAdmission.Selection reference) throws Exception {
        return NetflixLocalAdmission.open(context, reference, NetflixLocalAdmissionTest::decode);
    }

    /** Recreate the adapter and runtime, read only from cache, then observe two TEXT and two AUDIO dispatches. */
    @Test public void cacheReloadDrivesTheExistingScheduler() throws Exception {
        assertEquals(NetflixLocalAdmission.PACKAGE, context.getPackageName());
        open(selection).admit(zip);
        String namespace = "multimedia003d_cache_" + StudioMultimediaPackage.sha256(
                selection.trackId.getBytes(StandardCharsets.UTF_8)).substring(0, 32);
        Path persisted = Path.of(context.getApplicationInfo().dataDir, "shared_prefs", namespace + ".xml");
        assertTrue("Private snapshot XML was not persisted", Files.isRegularFile(persisted));
        assertTrue(Files.size(persisted) > zip.length);
        RecordingSinks effects = new RecordingSinks();
        StudioMultimediaRuntime runtime = new StudioMultimediaRuntime(effects, effects);
        String activation = open(selection).load(runtime);
        assertEquals(4, runtime.diagnostics().total);
        assertTrue(effects.events.isEmpty());
        try {
            for (long ms = 0; ms <= 8000; ms += 250) {
                runtime.observe(activation, new MediaObservation(true, ms, true));
            }
            assertEquals(List.of("TEXT:text-1", "AUDIO:tone-1", "TEXT:text-2", "AUDIO:tone-2"), effects.events);
            assertEquals(4, runtime.diagnostics().consumed);
            assertEquals(LocalAudioPort.State.IDLE, effects.state());
        } finally {
            runtime.close();
        }
    }

    /** Prove monotonic manual dispatch, authority, exact seek and revocation on the existing runtime. */
    @Test public void manualClockDrivesTheExistingScheduler() throws Exception {
        open(selection).admit(zip);
        RecordingSinks effects = new RecordingSinks();
        StudioMultimediaRuntime runtime = new StudioMultimediaRuntime(effects, effects, false, true, null);
        String activation = open(selection).load(runtime);
        long[] time = {50000};
        boolean[] admitted = {true};
        NetflixManualClock clock = new NetflixManualClock(runtime, activation, selection.trackId,
                selection.revision, 8000, () -> time[0], () -> admitted[0]);
        clock.seek(selection.trackId, selection.revision, clock.generation(), 0);
        assertEquals(StudioMultimediaRuntime.Authority.MANUAL, runtime.authority());
        clock.resume();
        time[0] += 1000;
        clock.tick(clock.generation());
        assertEquals(List.of("TEXT:text-1"), effects.events);
        runtime.observe(activation, new MediaObservation(true, 5500, true));
        assertEquals(1, effects.events.size());
        clock.pause();
        long obsolete = clock.generation();
        time[0] += 9000;
        assertEquals(1000, clock.position());
        clock.seek(selection.trackId, selection.revision, obsolete, 5500);
        clock.tick(obsolete);
        assertEquals(1, effects.events.size());
        clock.resume();
        assertEquals(List.of("TEXT:text-1", "AUDIO:tone-2"), effects.events);
        clock.seek(selection.trackId, selection.revision, clock.generation(), 2500);
        assertEquals(LocalAudioPort.State.IDLE, effects.state());
        clock.resume();
        assertEquals(List.of("TEXT:text-1", "AUDIO:tone-2", "AUDIO:tone-1"), effects.events);
        admitted[0] = false;
        clock.tick(clock.generation());
        assertEquals(LocalAudioPort.State.IDLE, effects.state());
        assertEquals(StudioMultimediaRuntime.Authority.UNSYNCED, runtime.authority());
        assertThrows(IllegalStateException.class, () -> clock.seek(selection.trackId, selection.revision,
                clock.generation(), 0));
    }

    /** Revocation of the verified cache stops the actual local activation on its next tick. */
    @Test public void changedPrivateSnapshotRevokesManualAuthority() throws Exception {
        open(selection).admit(zip);
        RecordingSinks effects = new RecordingSinks();
        StudioMultimediaRuntime runtime = new StudioMultimediaRuntime(effects, effects, false, true, null);
        long[] time = {10000};
        NetflixManualClock clock = open(selection).activateManual((track, permitted) -> runtime, () -> time[0]);
        clock.seek(selection.trackId, selection.revision, clock.generation(), 2500);
        clock.resume();
        assertEquals(LocalAudioPort.State.STARTED, effects.state());
        String namespace = "multimedia003d_cache_" + StudioMultimediaPackage.sha256(
                selection.trackId.getBytes(StandardCharsets.UTF_8)).substring(0, 32);
        assertTrue(context.getSharedPreferences(namespace, Context.MODE_PRIVATE).edit().clear().commit());
        time[0] += 100;
        clock.tick(clock.generation());
        assertEquals(LocalAudioPort.State.IDLE, effects.state());
        assertEquals(StudioMultimediaRuntime.Authority.UNSYNCED, runtime.authority());
    }

    /** An old Android seek-complete callback cannot restart a released native-port attempt. */
    @Test public void staleNativeSeekCompletionCannotRestartAudio() throws Exception {
        Path mp3 = Files.createTempFile("scenevibe-003b-", ".mp3");
        Files.write(mp3, entries().get("audio/tone-1.mp3"));
        try {
            org.robolectric.shadows.ShadowMediaPlayer.addMediaInfo(
                    org.robolectric.shadows.util.DataSource.toDataSource(mp3.toString()),
                    new org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(500, 0));
            com.scenevibe.tvcompanionpoc.audio.AndroidLocalAudioPort port =
                    new com.scenevibe.tvcompanionpoc.audio.AndroidLocalAudioPort(id -> mp3.toFile());
            port.prepare("tone-1");
            assertEquals(LocalAudioPort.State.PREPARED, port.state());
            android.media.MediaPlayer old = org.robolectric.util.ReflectionHelpers.getField(port, "player");
            org.robolectric.shadows.ShadowMediaPlayer shadow = org.robolectric.Shadows.shadowOf(old);
            android.media.MediaPlayer.OnSeekCompleteListener delayed =
                    org.robolectric.util.ReflectionHelpers.getField(shadow, "seekCompleteListener");
            assertNotNull(delayed);
            port.seekTo(100);
            port.start();
            port.release();
            delayed.onSeekComplete(old);
            assertEquals(LocalAudioPort.State.IDLE, port.state());
        } finally {
            Files.deleteIfExists(mp3);
        }
    }

    /** The default Studio parser continues to reject a Netflix package in the same binary. */
    @Test public void normalPrimeParserStillRejectsNetflix() {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> StudioMultimediaPackage.read(zip, NetflixLocalAdmissionTest::decode));
        assertEquals("SOURCE_PROFILE_UNSUPPORTED", refusal.getMessage());
    }

    /** Run the reference Prime fixture through the original delivery adapter and Studio entry. */
    @Test public void originalPrimeDeliveryProfileRemainsReadable() throws Exception {
        Path assets = Path.of(System.getProperty("scenevibe.003a.primeFixture"));
        MultimediaDeliveryProfile profile = new MultimediaDeliveryProfile(Files.readAllBytes(assets.resolve("profile.json")));
        Map<String,byte[]> mp3s = new LinkedHashMap<>();
        for (MultimediaDeliveryProfile.Resource resource : profile.resources) {
            mp3s.put(resource.sha256, Files.readAllBytes(assets.resolve(resource.sha256 + ".mp3")));
        }
        StudioMultimediaPackage parsed = profile.prepare(mp3s, NetflixLocalAdmissionTest::decode);
        RecordingSinks effects = new RecordingSinks();
        StudioMultimediaRuntime runtime = new StudioMultimediaRuntime(effects, effects);
        try {
            runtime.loadForSoftwareQualification(parsed, "prime-regression");
            assertEquals(6, runtime.diagnostics().total);
            assertTrue(effects.events.isEmpty());
        } finally {
            runtime.close();
        }
    }

    /** A re-confirmed outer digest cannot make an invalid overlay manifest acceptable. */
    @Test public void corruptManifestIsRefused() throws Exception {
        Map<String,byte[]> files = entries();
        replaceDocument(files, "originalManifest", "source/overlay.json",
                document(files, "source/overlay.json").put("type", "invalid-manifest"));
        byte[] invalid = pack(files);
        assertThrows(OverlayManifestParser.Invalid.class, () -> open(reference(invalid, 1)).admit(invalid));
    }

    /** A digest matching an incoherent program still fails cross-document identity checks. */
    @Test public void incoherentIdentityIsRefused() throws Exception {
        Map<String,byte[]> files = entries();
        JSONObject runtime = document(files, "source/runtime.json");
        runtime.getJSONObject("mediaIdentity").put("videoId", "another-program");
        replaceDocument(files, "originalRuntime", "source/runtime.json", runtime);
        assertRefused(pack(files), "PROGRAM_IDENTITY_MISMATCH");
    }

    /** Both the reference and the package manifest are checked against actual MP3 bytes. */
    @Test public void alteredMp3IsRefusedAndPriorCacheSurvives() throws Exception {
        open(selection).admit(zip);
        Map<String,byte[]> files = entries();
        files.get("audio/tone-1.mp3")[100] ^= 1;
        assertRefused(pack(files), "RESOURCE_HASH_OR_SIZE_MISMATCH");
        RecordingSinks effects = new RecordingSinks();
        StudioMultimediaRuntime runtime = new StudioMultimediaRuntime(effects, effects);
        open(selection).load(runtime);
        assertEquals(4, runtime.diagnostics().total);
        runtime.close();
    }

    /** Revisions must be positive and match the source document. */
    @Test public void invalidRevisionIsRefused() throws Exception {
        Map<String,byte[]> files = entries();
        replaceDocument(files, "track", "track.json", document(files, "track.json").put("revision", 0));
        assertRefused(pack(files), "REVISION_MISMATCH");
    }

    /** A detached selection for different bytes or another program grants no admission or reload. */
    @Test public void unauthorizedReferenceIsRefused() throws Exception {
        NetflixLocalAdmission.Selection wrong = new NetflixLocalAdmission.Selection(
                selection.trackId, selection.programId, 1, "0".repeat(64));
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class, () -> open(wrong).admit(zip));
        assertEquals("LOCAL_REFERENCE_DIGEST_MISMATCH", refusal.getMessage());
        open(selection).admit(zip);
        RecordingSinks effects = new RecordingSinks();
        StudioMultimediaRuntime runtime = new StudioMultimediaRuntime(effects, effects);
        assertThrows(IllegalArgumentException.class, () -> open(wrong).load(runtime));
        assertEquals(0, runtime.diagnostics().total);
        assertTrue(effects.events.isEmpty());
        NetflixLocalAdmission.Selection wrongProgram = new NetflixLocalAdmission.Selection(
                selection.trackId, "unconfirmed-program", 1, selection.sha256);
        assertEquals("LOCAL_REFERENCE_IDENTITY_MISMATCH",
                assertThrows(IllegalArgumentException.class, () -> open(wrongProgram).load(runtime)).getMessage());
    }

    /** A package cannot mint Cloud or MEDIA admission flags for itself. */
    @Test public void selfAssertedServerAuthorizationIsRefused() throws Exception {
        Map<String,byte[]> files = entries();
        files.put("manifest.json", document(files, "manifest.json").put("mediaAdmissionAuthorized", true)
                .toString().getBytes(StandardCharsets.UTF_8));
        assertRefused(pack(files), "PACKAGE_CANNOT_AUTHORIZE_MEDIA");
    }

    /** A valid changed package at the same revision cannot replace the cached bytes. */
    @Test public void revisionConflictPreservesImmutableCache() throws Exception {
        open(selection).admit(zip);
        Map<String,byte[]> files = entries();
        replaceDocument(files, "track", "track.json",
                document(files, "track.json").put("title", "Autre titre local"));
        assertRefused(pack(files), "LOCAL_REVISION_CONFLICT");
    }

    /** Refuse a different application before requesting any SharedPreferences handle. */
    @Test public void companionContextCannotOpenTheCache() {
        Context foreign = new ContextWrapper(context) {
            /** Project the foreign context itself to the package guard. */
            @Override public Context getApplicationContext() { return this; }
            /** Represent the production package. */
            @Override public String getPackageName() { return "com.scenevibe.tvcompanionpoc"; }
            /** Fail the test if the guard ever reaches production preferences. */
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                throw new AssertionError("Production preferences were accessed");
            }
        };
        assertEquals("QUALIFICATION_PACKAGE_REQUIRED", assertThrows(IllegalArgumentException.class,
                () -> NetflixLocalAdmission.open(foreign, selection, NetflixLocalAdmissionTest::decode)).getMessage());
    }

    /** Produce a detached reference to exercise deeper validation rather than only the ZIP digest. */
    private NetflixLocalAdmission.Selection reference(byte[] bytes, long revision) throws Exception {
        return new NetflixLocalAdmission.Selection(selection.trackId, selection.programId,
                revision, StudioMultimediaPackage.sha256(bytes));
    }

    /** Assert a specific fail-closed diagnostic for the selected package. */
    private void assertRefused(byte[] bytes, String reason) throws Exception {
        assertEquals(reason, assertThrows(IllegalArgumentException.class,
                () -> open(reference(bytes, 1)).admit(bytes)).getMessage());
    }

    /** Unpack the small fixture in memory for negative cases. */
    private Map<String,byte[]> entries() throws Exception {
        Map<String,byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) files.put(entry.getName(), input.readAllBytes());
        }
        return files;
    }

    /** Parse one fixture document without modifying any installed asset. */
    private JSONObject document(Map<String,byte[]> files, String path) throws Exception {
        return new JSONObject(new String(files.get(path), StandardCharsets.UTF_8));
    }

    /** Keep the outer hash consistent so inner validation is exercised. */
    private void replaceDocument(Map<String,byte[]> files, String key, String path, JSONObject value) throws Exception {
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        files.put(path, bytes);
        JSONObject manifest = document(files, "manifest.json");
        manifest.getJSONObject(key).put("sha256", StudioMultimediaPackage.sha256(bytes));
        files.put("manifest.json", manifest.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Encode an independent mutated ZIP for one refusal case. */
    private byte[] pack(Map<String,byte[]> files) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (Map.Entry<String,byte[]> entry : files.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    /** Record the real runtime's commands at the drawing and audio hardware boundaries. */
    private static final class RecordingSinks implements SceneRuntimeController.SceneSink, LocalAudioPort {
        final List<String> events = new ArrayList<>();
        private State state = State.IDLE;
        private String asset;

        /** Allow the validated TEXT scene to be dispatched. */
        @Override public boolean preflight(OverlayManifest.Scene scene) { return true; }
        /** Record the exact selected scene. */
        @Override public void show(OverlayManifest.Scene scene) { events.add("TEXT:" + scene.id); }
        /** Accept TEXT expiration without creating a window. */
        @Override public void hide(OverlayManifest.Scene scene) {}
        /** Accept teardown without creating a window. */
        @Override public void hideAll() {}
        /** Record the selected resource without simulating physical sound. */
        @Override public void prepare(String id) { asset = id; state = State.PREPARED; }
        /** Record a start emitted by the existing AUDIO renderer. */
        @Override public void start() { events.add("AUDIO:" + asset); state = State.STARTED; }
        /** Record release of the resource at expiration. */
        @Override public void stop() { state = State.IDLE; }
        /** Record teardown. */
        @Override public void release() { state = State.IDLE; }
        /** Accept the renderer's local gain. */
        @Override public void setGain(float gain) {}
        /** Return the recording port's lifecycle state. */
        @Override public State state() { return state; }
        /** Support the qualified renderer's precise offset seam. */
        @Override public boolean supportsNativeOffsets() { return true; }
        /** Accept the requested offset without producing sound. */
        @Override public void seekTo(long offset) {}
    }
}
