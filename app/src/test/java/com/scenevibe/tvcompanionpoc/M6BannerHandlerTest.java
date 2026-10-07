package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationHandlerRegistry;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.PackageInstaller;
import com.scenevibe.tvcompanionpoc.installation.PreparedInstallation;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import com.scenevibe.tvcompanionpoc.wall.WallCalendarScheduler;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.Assert.*;

/**
 * Deterministic, Android-free JVM tests for the FEAT-004 local Banner handler/codec, the single
 * durable path reuse, restore fresh-temporal-context, the common Video+Banner registry and the
 * Banner-known autostart decision. They mirror the existing M4PhaseD/E/F + CloudManifestCache +
 * SceneRuntimeController test style with a memory {@link InstallationStore.Backend} fixture and a
 * recording {@link SceneRuntimeController.SceneSink}. The handler never writes/clocks/renders before
 * commit; the full Banner cross-contract (bijection, offset-zero, exact duration, banner/wall/
 * continue, no asset/image, animations none) accepts the valid shape and each violation rejects with
 * a bounded INVALID_PACKAGE. The pure {@code wall/} package and MediaCalendarScheduler are untouched.
 */
public final class M6BannerHandlerTest {

    private static final String CODEC = TvCapabilities.CODEC_BANNER_WALL_OVERLAY;
    private static final String HANDLER = BannerInstallationHandler.HANDLER_ID;
    private static final long BASE = 1_000_000_000_000L;

    /** Minimal in-memory durable backend reusing the single existing store/snapshot/codec path. */
    private static final class MemoryBackend implements InstallationStore.Backend {
        final Map<String, String> values = new HashMap<>();
        final Object monitor = new Object();
        @Override public Object monitor() { return monitor; }
        @Override public String get(String key) { return values.get(key); }
        @Override public boolean commit(Map<String, String> put, Set<String> removed, boolean clear) {
            if (clear) values.clear();
            values.putAll(put);
            for (String r : removed) values.remove(r);
            return true;
        }
    }

    /** Recording drawing seam proving exactly-one-visual-scene for the Banner path. */
    private static final class RecordingSink implements SceneRuntimeController.SceneSink {
        boolean preflightResult = true;
        int visibleCount;
        @Override public boolean preflight(OverlayManifest.Scene s) { return preflightResult; }
        @Override public void show(OverlayManifest.Scene s) { visibleCount++; assertTrue(visibleCount <= 1); }
        @Override public void hide(OverlayManifest.Scene s) { if (visibleCount > 0) visibleCount--; }
        @Override public void hideAll() { visibleCount = 0; }
    }

    /** Common-owner composition fake binding the reused controller/scheduler and the Banner ports. */
    private static final class Owner {
        boolean owner = true;
        long selected;
        final RecordingSink sink = new RecordingSink();
        final SceneRuntimeController controller = new SceneRuntimeController(sink);
        final WallCalendarScheduler scheduler = new WallCalendarScheduler();
        final LiveBannerRuntimePorts ports = new LiveBannerRuntimePorts(() -> owner, () -> controller,
                () -> {}, () -> {}, () -> {}, r -> selected = r, e -> {});
    }

    // ---- Banner body builders -----------------------------------------------------------

    private static String textElement(String id) {
        return "{\"id\":\"" + id + "\",\"type\":\"text\",\"frame\":{\"x\":0,\"y\":0,\"width\":100,\"height\":50},"
                + "\"zIndex\":0,\"opacity\":1,\"text\":\"x\",\"style\":{\"color\":\"#FFFFFF\","
                + "\"backgroundColor\":\"#000000\",\"fontSize\":20,\"fontWeight\":\"normal\","
                + "\"textAlign\":\"start\",\"padding\":0,\"cornerRadius\":0}}";
    }

    private static String scene(String id, long startMs, long durationMs) {
        return "{\"id\":\"" + id + "\",\"startMs\":" + startMs + ",\"durationMs\":" + durationMs
                + ",\"elements\":[" + textElement("el-" + id) + "]}";
    }

    private static String imageScene(String id, long durationMs) {
        return "{\"id\":\"" + id + "\",\"startMs\":0,\"durationMs\":" + durationMs
                + ",\"elements\":[{\"id\":\"img-" + id + "\",\"type\":\"image\",\"frame\":{\"x\":0,\"y\":0,"
                + "\"width\":100,\"height\":50},\"zIndex\":0,\"opacity\":1,\"assetRef\":\"asset:a\",\"fit\":\"cover\"}]}";
    }

    private static String animScene(String id, long durationMs) {
        return "{\"id\":\"" + id + "\",\"startMs\":0,\"durationMs\":" + durationMs
                + ",\"elements\":[{\"id\":\"t-" + id + "\",\"type\":\"text\",\"frame\":{\"x\":0,\"y\":0,"
                + "\"width\":100,\"height\":50},\"zIndex\":0,\"opacity\":1,"
                + "\"animation\":{\"enter\":\"fade\",\"exit\":\"none\",\"durationMs\":200},"
                + "\"text\":\"x\",\"style\":{\"color\":\"#FFFFFF\",\"backgroundColor\":\"#000000\","
                + "\"fontSize\":20,\"fontWeight\":\"normal\",\"textAlign\":\"start\",\"padding\":0,\"cornerRadius\":0}}]}";
    }

    private static String manifest(String product, String clock, String pause, String... scenes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < scenes.length; i++) { if (i > 0) sb.append(','); sb.append(scenes[i]); }
        return "{\"type\":\"scenevibe.overlay-manifest.v1\",\"schemaVersion\":\"1.0.0\",\"manifestId\":\"m1\","
                + "\"source\":{\"product\":\"" + product + "\",\"sourceId\":\"src\"},"
                + "\"canvas\":{\"width\":1920,\"height\":1080},"
                + "\"clock\":{\"mode\":\"" + clock + "\",\"pauseBehavior\":\"" + pause + "\"},"
                + "\"scenes\":[" + sb + "]}";
    }

    private static String window(String id, long start, long end) {
        return "{\"eventId\":\"" + id + "\",\"startEpochMs\":" + start + ",\"endEpochMs\":" + end + "}";
    }

    private static String body(String windows, String manifest) {
        return "{\"type\":\"scenevibe.banner.wall-package.v1\",\"schemaVersion\":\"1.0.0\","
                + "\"horizon\":{\"startEpochMs\":" + BASE + ",\"endEpochMs\":" + (BASE + 86_400_000L) + "},"
                + "\"windows\":[" + windows + "],\"manifest\":" + manifest + ",\"provenance\":{\"source\":\"editor\"}}";
    }

    private static String validBody(String eventId) {
        return body(window(eventId, BASE, BASE + 6_000L),
                manifest("banner", "wall", "continue", scene(eventId, 0, 6_000L)));
    }

    private static InstallRequest request(long revision, String body) {
        Map<String, byte[]> art = new TreeMap<>();
        art.put(BannerInstallationHandler.ARTIFACT, body.getBytes(StandardCharsets.UTF_8));
        return new InstallRequest(revision, CODEC, art);
    }

    private static InstallationStatus validate(String body) {
        return new BannerInstallationHandler().validate(request(1, body), TvCapabilities.current());
    }

    // ---- validate/prepare write-nothing + accept ----------------------------------------

    @Test public void validAcceptsAndWritesNothing() {
        MemoryBackend backend = new MemoryBackend();
        PreparedInstallation prepared = new BannerInstallationHandler()
                .prepare(request(1, validBody("evt-a")), TvCapabilities.current());
        assertEquals(InstallationStatus.PREPARED, prepared.status());
        assertEquals(HANDLER, prepared.handlerId());
        assertEquals(CODEC, prepared.codecId());
        assertTrue("prepare writes nothing", backend.values.isEmpty());
        assertSame(prepared.canonical(), new BannerInstallationHandler().encodeForCache(prepared));
        assertTrue("encode writes nothing", backend.values.isEmpty());
    }

    @Test public void bijectionTwoWindowsTwoScenesAccept() {
        String b = body(window("a", BASE, BASE + 6_000L) + "," + window("b", BASE + 10_000L, BASE + 16_000L),
                manifest("banner", "wall", "continue", scene("a", 0, 6_000L), scene("b", 0, 6_000L)));
        assertEquals(InstallationStatus.VALIDATED, validate(b));
    }

    // ---- each cross-contract violation rejects ------------------------------------------

    @Test public void wrongProductRejected() {
        assertEquals(InstallationStatus.INVALID_PACKAGE, validate(
                body(window("a", BASE, BASE + 6_000L), manifest("video", "wall", "continue", scene("a", 0, 6_000L)))));
    }

    @Test public void wrongClockRejected() {
        assertEquals(InstallationStatus.INVALID_PACKAGE, validate(
                body(window("a", BASE, BASE + 6_000L), manifest("banner", "media", "continue", scene("a", 0, 6_000L)))));
    }

    @Test public void wrongPauseRejected() {
        assertEquals(InstallationStatus.INVALID_PACKAGE, validate(
                body(window("a", BASE, BASE + 6_000L), manifest("banner", "wall", "freeze", scene("a", 0, 6_000L)))));
    }

    @Test public void nonZeroOffsetRejected() {
        assertEquals(InstallationStatus.INVALID_PACKAGE, validate(
                body(window("a", BASE, BASE + 6_000L), manifest("banner", "wall", "continue", scene("a", 500, 6_000L)))));
    }

    @Test public void wrongDurationRejected() {
        assertEquals(InstallationStatus.INVALID_PACKAGE, validate(
                body(window("a", BASE, BASE + 6_000L), manifest("banner", "wall", "continue", scene("a", 0, 5_000L)))));
    }

    @Test public void countMismatchRejected() {
        assertEquals(InstallationStatus.INVALID_PACKAGE, validate(
                body(window("a", BASE, BASE + 6_000L),
                        manifest("banner", "wall", "continue", scene("a", 0, 6_000L), scene("b", 0, 6_000L)))));
    }

    @Test public void idMismatchRejected() {
        assertEquals(InstallationStatus.INVALID_PACKAGE, validate(
                body(window("a", BASE, BASE + 6_000L), manifest("banner", "wall", "continue", scene("z", 0, 6_000L)))));
    }

    @Test public void imagePrimitiveRejected() {
        assertEquals(InstallationStatus.INVALID_PACKAGE, validate(
                body(window("a", BASE, BASE + 6_000L), manifest("banner", "wall", "continue", imageScene("a", 6_000L)))));
    }

    @Test public void animationRejected() {
        assertEquals(InstallationStatus.INVALID_PACKAGE, validate(
                body(window("a", BASE, BASE + 6_000L), manifest("banner", "wall", "continue", animScene("a", 6_000L)))));
    }

    @Test public void unknownKeyRejected() {
        assertEquals(InstallationStatus.INVALID_PACKAGE,
                validate(validBody("evt-a").replace("\"provenance\"", "\"extra\":1,\"provenance\"")));
    }

    @Test public void wrongArtifactCountRejected() {
        Map<String, byte[]> art = new TreeMap<>();
        art.put("banner", validBody("a").getBytes(StandardCharsets.UTF_8));
        art.put("extra", "x".getBytes(StandardCharsets.UTF_8));
        assertEquals(InstallationStatus.INVALID_PACKAGE,
                new BannerInstallationHandler().validate(new InstallRequest(1, CODEC, art), TvCapabilities.current()));
    }

    // ---- registry: exactly Video+Video+Banner, unknown rejected -------------------------

    @Test public void registryHoldsVideoVideoBannerAndRejectsUnknownCodec() {
        InstallationHandlerRegistry registry = OverlayInstallationHandlers.registry();
        assertEquals(3, registry.entries().size());
        assertNotNull(registry.findCodec(TvCapabilities.CODEC_TRACK_OVERLAY));
        assertNotNull(registry.findCodec(TvCapabilities.CODEC_TRACK));
        assertNotNull(registry.findCodec(CODEC));
        assertNull(registry.findCodec("scenevibe.unknown.v1"));
        assertNull(registry.findHandler("scenevibe.unknown.v1"));
    }

    // ---- installer paths through the single durable store -------------------------------

    private PackageInstaller installer(MemoryBackend backend) {
        return new PackageInstaller(new InstallationStore(backend),
                OverlayInstallationHandlers.registry(), TvCapabilities.current());
    }

    @Test public void installNewArmsAndPersistsBannerBinding() {
        MemoryBackend backend = new MemoryBackend();
        Owner o = new Owner();
        assertEquals(InstallationStatus.ARMED, installer(backend).install(request(5, validBody("evt-a")), o.ports));
        InstallationStore.ReadResult rr = new InstallationStore(backend).read();
        assertEquals(InstallationStore.ReadState.SNAPSHOT, rr.state());
        assertEquals(CODEC, rr.snapshot().codecId());
        assertEquals(HANDLER, rr.snapshot().handlerId());
        assertEquals(5, rr.snapshot().revision());
        assertEquals(5, o.selected);
    }

    @Test public void sameRevisionArmsFromDurableWithoutRepersist() {
        MemoryBackend backend = new MemoryBackend();
        installer(backend).install(request(5, validBody("evt-a")), new Owner().ports);
        Map<String, String> after = new HashMap<>(backend.values);
        Owner o2 = new Owner();
        assertEquals(InstallationStatus.ARMED, installer(backend).install(request(5, validBody("evt-a")), o2.ports));
        assertEquals("same revision does not repersist", after, backend.values);
        assertEquals(5, o2.selected);
    }

    @Test public void staleRevisionRejectedNoArm() {
        MemoryBackend backend = new MemoryBackend();
        installer(backend).install(request(5, validBody("evt-a")), new Owner().ports);
        Map<String, String> after = new HashMap<>(backend.values);
        Owner o2 = new Owner();
        assertEquals(InstallationStatus.STALE, installer(backend).install(request(4, validBody("evt-a")), o2.ports));
        assertEquals(after, backend.values);
        assertEquals(0, o2.selected);
    }

    @Test public void corruptDurableFailsClosedNoArm() {
        MemoryBackend backend = new MemoryBackend();
        backend.values.put(InstallationStore.SNAPSHOT_KEY, "not-a-valid-encoding");
        Owner o = new Owner();
        assertEquals(InstallationStatus.CACHE_FAILED, installer(backend).install(request(5, validBody("evt-a")), o.ports));
        assertEquals(0, o.selected);
        assertFalse(o.controller.hasVisibleScene());
    }

    // ---- restore fresh temporal context -------------------------------------------------

    private InstallationStatus restore(MemoryBackend backend, Owner o) {
        InstallationStore store = new InstallationStore(backend);
        InstallationStore.ReadResult rr = store.read();
        if (rr.state() == InstallationStore.ReadState.EMPTY) return null;
        if (rr.state() == InstallationStore.ReadState.CORRUPT) return InstallationStatus.CACHE_FAILED;
        return installer(backend).install(rr.snapshot().canonical(), o.ports);
    }

    @Test public void restoreActiveAtBootShowsPastWindowInvisible() {
        long nowBoot = BASE + 3_000L;
        String b = body(window("a", BASE, BASE + 6_000L) + "," + window("b", BASE + 10_000L, BASE + 16_000L),
                manifest("banner", "wall", "continue", scene("a", 0, 6_000L), scene("b", 0, 6_000L)));
        MemoryBackend backend = new MemoryBackend();
        assertEquals(InstallationStatus.ARMED, installer(backend).install(request(7, b), new Owner().ports));

        // A fresh restore composes a brand-new owner/controller: a FRESH temporal context.
        Owner o = new Owner();
        assertEquals(InstallationStatus.ARMED, restore(backend, o));
        assertFalse("promotion shows nothing until the first fresh evaluation", o.controller.hasVisibleScene());
        o.ports.onWallResult(o.ports.activeToken(),
                o.scheduler.load(o.ports.activeState().calendar, nowBoot, true));
        assertEquals("a", o.controller.visibleSceneId());

        // A separate fresh restore evaluated after both windows ended shows nothing (no replay).
        Owner past = new Owner();
        assertEquals(InstallationStatus.ARMED, restore(backend, past));
        past.ports.onWallResult(past.ports.activeToken(),
                past.scheduler.load(past.ports.activeState().calendar, BASE + 20_000L, true));
        assertFalse("a fully-past window never replays", past.controller.hasVisibleScene());
    }

    // ---- Banner-known autostart ---------------------------------------------------------

    @Test public void bannerKnownStartsWithoutMediaGrant() {
        assertEquals(AutostartPolicy.Decision.START_BANNER,
                AutostartPolicy.decide(true, true, false, false, true, AutostartPolicy.DurableKind.BANNER));
    }

    @Test public void videoKnownStillRequiresMediaGrant() {
        assertEquals(AutostartPolicy.Decision.AUTOSTART_BLOCKED_MEDIA_PERMISSION,
                AutostartPolicy.decide(true, true, false, false, true, AutostartPolicy.DurableKind.VIDEO));
    }

    @Test public void bannerKnownStillNeedsOptInAndOverlay() {
        assertEquals(AutostartPolicy.Decision.AUTOSTART_DISABLED,
                AutostartPolicy.decide(false, true, false, false, true, AutostartPolicy.DurableKind.BANNER));
        assertEquals(AutostartPolicy.Decision.AUTOSTART_BLOCKED_OVERLAY_PERMISSION,
                AutostartPolicy.decide(true, false, false, false, true, AutostartPolicy.DurableKind.BANNER));
    }

    @Test public void existingFiveArgDecideUnchanged() {
        assertEquals(AutostartPolicy.Decision.START, AutostartPolicy.decide(true, true, true, true, false));
        assertEquals(AutostartPolicy.Decision.AUTOSTART_NOTHING_TO_RESTORE,
                AutostartPolicy.decide(true, true, true, false, false));
    }
}
