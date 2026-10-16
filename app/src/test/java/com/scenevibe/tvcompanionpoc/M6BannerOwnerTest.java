package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.wall.WallCalendar;
import com.scenevibe.tvcompanionpoc.wall.WallCalendarScheduler;
import com.scenevibe.tvcompanionpoc.wall.WallEvent;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Deterministic, Android-free JVM tests for the single principal common runtime owner's Banner
 * (WALL) activation ports ({@link LiveBannerRuntimePorts} + {@link BannerPreparedState#arm}). They
 * exercise the section-13 race table: a fresh unique token per ARM, a generation captured once at
 * manifest ARM, promotion only after every prior ARM step succeeds, token+generation-guarded
 * routing through the reused {@link SceneRuntimeController}, pending-never-renders, the
 * alternating-kind retirement of the opposite Video/MEDIA path, and token/generation overflow that
 * fails ARM closed. No Android, no clock and no real time: the pure {@link WallCalendarScheduler}
 * supplies already-selected results and a recording {@link SceneRuntimeController.SceneSink} fake
 * observes exactly-one-visual-scene. The pure {@code wall/} package and MediaCalendarScheduler are
 * untouched.
 */
public final class M6BannerOwnerTest {

    /** Fake drawing seam that records calls and proves exactly-one-visual-scene for Banner. */
    private static final class RecordingSink implements SceneRuntimeController.SceneSink {
        final List<String> visual = new ArrayList<>();
        boolean preflightResult = true;
        int visibleCount;
        @Override public boolean preflight(OverlayManifest.Scene scene) {
            visual.add("preflight:" + scene.id);
            return preflightResult;
        }
        @Override public void show(OverlayManifest.Scene scene) {
            visual.add("show:" + scene.id);
            visibleCount++;
            assertTrue("exactly one visual scene must be active", visibleCount <= 1);
        }
        @Override public void hide(OverlayManifest.Scene scene) {
            visual.add("hide:" + scene.id);
            if (visibleCount > 0) visibleCount--;
        }
        @Override public void hideAll() {
            visual.add("hide-all");
            visibleCount = 0;
        }
    }

    /**
     * Common-owner composition fake: a single owner predicate, the reused controller/scheduler, a
     * recording Video-retirement and MEDIA-neutralization seam, and the Banner ports under test.
     * Only the owner identity and native removal are substituted, exactly like the Video fixtures.
     */
    private static final class Runtime {
        boolean owner = true;
        boolean videoVisible;
        int videoRetired, mediaNeutralized, scenesRetired;
        long selected;
        final List<String> eligibility = new ArrayList<>();
        final RecordingSink sink = new RecordingSink();
        final SceneRuntimeController controller = new SceneRuntimeController(sink);
        final WallCalendarScheduler scheduler = new WallCalendarScheduler();
        final LiveBannerRuntimePorts ports;
        Runtime() {
            ports = new LiveBannerRuntimePorts(() -> owner, () -> controller,
                    () -> { videoRetired++; videoVisible = false; },
                    () -> mediaNeutralized++,
                    () -> scenesRetired++,
                    revision -> selected = revision,
                    eligible -> eligibility.add(Boolean.toString(eligible)));
        }
        /** Read an opaque test binding token without adding a production diagnostic accessor. */
        String token(String slot) throws Exception {
            Field field = ports.getClass().getDeclaredField(slot);
            field.setAccessible(true);
            Object binding = field.get(ports);
            if (binding == null) return null;
            Field token = binding.getClass().getDeclaredField("token");
            token.setAccessible(true);
            return (String) token.get(binding);
        }
        /** The sole visible owner is a Banner scene or the opposite Video surface, never both. */
        boolean visible() { return controller.hasVisibleScene() || videoVisible; }
    }

    // ---- fixtures -----------------------------------------------------------------------

    /** Fixed abstract epochs; nothing here reads a clock (FEAT-003 owns the Android driver). */
    private static final long BASE = 1_000_000_000_000L;

    private static WallEvent window(String id, long start, long durationMs) {
        return new WallEvent(id, start, start + durationMs);
    }

    private static WallCalendar calendar(WallEvent... events) {
        long start = Long.MAX_VALUE, end = Long.MIN_VALUE;
        for (WallEvent e : events) {
            start = Math.min(start, e.startEpochMs());
            end = Math.max(end, e.endEpochMs());
        }
        return new WallCalendar(start, end, Arrays.asList(events));
    }

    private static OverlayManifest.Scene scene(String id) {
        // Banner: id == eventId, startMs == 0 (offset from occurrence anchor), durationMs > 0.
        return new OverlayManifest.Scene(id, 0, 6_000, new ArrayList<>());
    }

    private static OverlayManifest bannerManifest(String sourceId, String... sceneIds) {
        List<OverlayManifest.Scene> scenes = new ArrayList<>();
        for (String id : sceneIds) scenes.add(scene(id));
        return new OverlayManifest("banner:" + sourceId, "banner", sourceId, "wall", "continue", scenes);
    }

    private static BannerPreparedState prepared(String sourceId, String... ids) {
        WallEvent[] windows = new WallEvent[ids.length];
        for (int i = 0; i < ids.length; i++) windows[i] = window(ids[i], BASE + i * 10_000L, 6_000);
        return new BannerPreparedState(calendar(windows), bannerManifest(sourceId, ids));
    }

    /**
     * Load the owner's active calendar into the pure scheduler at a given epoch and return the
     * selection result (the DUE is emitted by the load that establishes the selection; a later
     * idempotent evaluate at the same epoch would be empty).
     */
    private static WallCalendarScheduler.Result dueAt(Runtime r, BannerPreparedState state, long nowEpochMs) {
        return r.scheduler.load(state.calendar, nowEpochMs, true);
    }

    /** Drive the standard ordered Banner ARM through the owner-side activation entry point. */
    private static BannerPreparedState armBanner(Runtime r, long revision, String... ids) {
        BannerPreparedState state = prepared("src-" + revision, ids);
        assertEquals(InstallationStatus.ARMED, BannerPreparedState.arm(state, r.ports, revision));
        return state;
    }

    // ---- promotion / eligibility --------------------------------------------------------

    /** ARM only selects ownership; the first in-window WALL result makes exactly one scene visible. */
    @Test public void armPromotesThenDueRendersOneScene() throws Exception {
        Runtime r = new Runtime();
        BannerPreparedState state = armBanner(r, 7, "evt-a");
        assertEquals(7, r.selected);
        assertFalse(r.visible());
        assertNotNull(r.token("active"));
        // Banner local eligibility is initialized AFTER promotion, nothing forced visible yet.
        assertEquals(Arrays.asList("true"), r.eligibility);
        r.ports.onWallResult(r.token("active"), dueAt(r, state, BASE));
        assertTrue(r.visible());
        assertEquals("evt-a", r.controller.visibleSceneId());
    }

    /** Selecting Banner retires the opposite Video surface and neutralizes the MEDIA path once. */
    @Test public void bannerSelectionRetiresVideoAndNeutralizesMedia() throws Exception {
        Runtime r = new Runtime();
        r.videoVisible = true;
        armBanner(r, 3, "evt-a");
        assertFalse(r.videoVisible);
        assertTrue(r.videoRetired >= 1);
        assertTrue(r.mediaNeutralized >= 1);
    }

    /** A pending binding before promotion renders nothing and is refused as a callback owner. */
    @Test public void pendingBindingCannotRenderBeforePromotion() throws Exception {
        Runtime r = new Runtime();
        assertTrue(r.ports.retireVideoVisualOwner());
        assertTrue(r.ports.retireManifestedVisualOwner());
        BannerPreparedState state = prepared("src", "evt-a");
        assertTrue(r.ports.loadPreparedBanner(state));
        String pending = r.token("pending");
        assertNotNull(pending);
        assertNull(r.token("active"));
        List<String> before = new ArrayList<>(r.sink.visual);
        r.ports.onWallResult(pending, dueAt(r, state, BASE));
        assertEquals(before, r.sink.visual);
        assertFalse(r.visible());
    }

    // ---- section 13 race table ----------------------------------------------------------

    /** Replacement just before DUE: the old token is invalidated and its DUE is ignored. */
    @Test public void replacementJustBeforeDueIgnoresOldToken() throws Exception {
        Runtime r = new Runtime();
        BannerPreparedState first = armBanner(r, 10, "evt-a");
        String old = r.token("active");
        WallCalendarScheduler.Result staleDue = dueAt(r, first, BASE);
        // A new package replaces the old one before the old DUE is routed.
        BannerPreparedState second = armBanner(r, 11, "evt-b");
        assertNotEquals(old, r.token("active"));
        r.ports.onWallResult(old, staleDue);
        assertFalse(r.visible());
        // Only the new package can render.
        r.ports.onWallResult(r.token("active"), dueAt(r, second, BASE));
        assertEquals("evt-b", r.controller.visibleSceneId());
    }

    /** Replacement during display: immediate retire of the old scene, then only the new package. */
    @Test public void replacementDuringDisplayRetiresThenNewPackageOnly() throws Exception {
        Runtime r = new Runtime();
        BannerPreparedState first = armBanner(r, 20, "evt-a");
        r.ports.onWallResult(r.token("active"), dueAt(r, first, BASE));
        assertEquals("evt-a", r.controller.visibleSceneId());
        BannerPreparedState second = armBanner(r, 21, "evt-b");
        // Promotion retired the manifested scene synchronously; nothing of the old package is up.
        assertFalse(r.visible());
        r.ports.onWallResult(r.token("active"), dueAt(r, second, BASE));
        assertEquals("evt-b", r.controller.visibleSceneId());
        assertEquals(1, r.sink.visibleCount);
    }

    /** Old EXPIRE after a new ARM at the same eventId: token+generation keep the new scene up. */
    @Test public void oldExpireAfterNewArmSameEventIdCannotHideNewScene() throws Exception {
        Runtime r = new Runtime();
        BannerPreparedState first = armBanner(r, 30, "evt-a");
        String old = r.token("active");
        // Build an EXIT effect for evt-a under the OLD binding (clear emits EXIT for the selected).
        r.scheduler.load(first.calendar, BASE, true);
        r.scheduler.evaluate(BASE, true);
        WallCalendarScheduler.Result oldExit = r.scheduler.clear();
        // New package at the SAME eventId is armed and shown.
        BannerPreparedState second = armBanner(r, 31, "evt-a");
        r.ports.onWallResult(r.token("active"), dueAt(r, second, BASE));
        assertEquals("evt-a", r.controller.visibleSceneId());
        // The stale EXIT under the old token is a NO-OP: the new scene stays visible.
        r.ports.onWallResult(old, oldExit);
        assertEquals("evt-a", r.controller.visibleSceneId());
        assertTrue(r.visible());
    }

    /** Clear during wait: the active calendar is emptied and the runnable is delivered as a NO-OP. */
    @Test public void clearDuringWaitDeliversRunnableWithoutEffect() throws Exception {
        Runtime r = new Runtime();
        BannerPreparedState state = armBanner(r, 40, "evt-a");
        r.ports.onWallResult(r.token("active"), dueAt(r, state, BASE));
        assertTrue(r.visible());
        // A clear empties the calendar; its EXIT retires the visible scene under the active token.
        r.scheduler.load(state.calendar, BASE, true);
        r.scheduler.evaluate(BASE, true);
        WallCalendarScheduler.Result cleared = r.scheduler.clear();
        r.ports.onWallResult(r.token("active"), cleared);
        assertFalse(r.visible());
        // A further empty evaluation delivered late is inert.
        List<String> before = new ArrayList<>(r.sink.visual);
        r.ports.onWallResult(r.token("active"), r.scheduler.evaluate(BASE, true));
        assertEquals(before, r.sink.visual);
    }

    /** Service stop/start: stop invalidates+retires, start re-arms fresh, no token is reused. */
    @Test public void serviceStopStartReArmsWithoutTokenReuse() throws Exception {
        Runtime r = new Runtime();
        BannerPreparedState first = armBanner(r, 50, "evt-a");
        r.ports.onWallResult(r.token("active"), dueAt(r, first, BASE));
        String old = r.token("active");
        // Stop: invalidation + retire.
        r.ports.abortActivation();
        assertNull(r.token("active"));
        assertNull(r.token("pending"));
        assertFalse(r.visible());
        assertEquals(0, r.selected);
        // Start: a fresh activation with a brand new token.
        BannerPreparedState second = armBanner(r, 51, "evt-b");
        assertNotNull(r.token("active"));
        assertNotEquals(old, r.token("active"));
        // The old token can no longer drive anything.
        List<String> before = new ArrayList<>(r.sink.visual);
        r.ports.onWallResult(old, dueAt(r, first, BASE));
        assertEquals(before, r.sink.visual);
    }

    /** Pending callback before promotion is refused; only the active binding's callbacks run. */
    @Test public void pendingCallbackBeforePromotionIsRefused() throws Exception {
        Runtime r = new Runtime();
        BannerPreparedState active = armBanner(r, 60, "evt-a");
        // Begin a second ARM, stop before selection: pending exists, active is invalidated.
        assertTrue(r.ports.retireVideoVisualOwner());
        assertTrue(r.ports.retireManifestedVisualOwner());
        BannerPreparedState next = prepared("src2", "evt-b");
        assertTrue(r.ports.loadPreparedBanner(next));
        String pending = r.token("pending");
        assertNull(r.token("active"));
        List<String> before = new ArrayList<>(r.sink.visual);
        r.ports.onWallResult(pending, dueAt(r, next, BASE));
        assertEquals(before, r.sink.visual);
        assertFalse(r.visible());
    }

    /** Video<->Banner swap retires all visuals and neutralizes the opposite temporal path. */
    @Test public void videoBannerSwapRetiresAllVisualsAndNeutralizesPath() throws Exception {
        Runtime r = new Runtime();
        r.videoVisible = true;
        BannerPreparedState banner = armBanner(r, 70, "evt-a");
        assertFalse(r.videoVisible);
        int videoRetired = r.videoRetired, mediaNeutralized = r.mediaNeutralized;
        r.ports.onWallResult(r.token("active"), dueAt(r, banner, BASE));
        assertEquals("evt-a", r.controller.visibleSceneId());
        // A subsequent Banner-to-Video swap is modeled by the owner retiring the Banner scene and
        // invalidating the WALL activation before MEDIA loads (abort stands in for the Video select).
        r.ports.retireManifestedVisualOwner();
        r.ports.abortActivation();
        assertNull(r.token("active"));
        assertFalse(r.visible());
        assertTrue(r.scenesRetired >= 1);
        // The earlier Banner selection already retired Video and neutralized MEDIA exactly once/kind.
        assertTrue(videoRetired >= 1);
        assertTrue(mediaNeutralized >= 1);
    }

    /** Eligibility loss hides immediately; regain re-evaluates the current window even if identical. */
    @Test public void eligibilityLossHidesAndRegainReevaluatesSameWindow() throws Exception {
        Runtime r = new Runtime();
        BannerPreparedState state = armBanner(r, 80, "evt-a");
        String token = r.token("active");
        r.ports.onWallResult(token, dueAt(r, state, BASE));
        assertTrue(r.visible());
        // Suspension / overlay loss hides immediately.
        r.ports.onEligibility(token, false);
        assertFalse(r.visible());
        assertEquals("false", r.eligibility.get(r.eligibility.size() - 1));
        // Regain alone forces nothing; re-evaluating the current window (same eventId) shows again.
        r.ports.onEligibility(token, true);
        assertFalse(r.visible());
        r.ports.onWallResult(token, dueAt(r, state, BASE));
        assertTrue(r.visible());
        assertEquals("evt-a", r.controller.visibleSceneId());
    }

    // ---- fail-closed / guards -----------------------------------------------------------

    /** Token overflow fails ARM closed with no silent reuse and no promotion. */
    @Test public void tokenOverflowFailsArmClosed() throws Exception {
        Runtime r = new Runtime();
        Field counter = r.ports.getClass().getDeclaredField("nextActivation");
        counter.setAccessible(true);
        counter.setLong(r.ports, Long.MAX_VALUE);
        BannerPreparedState state = prepared("src", "evt-a");
        assertEquals(InstallationStatus.ARM_FAILED, BannerPreparedState.arm(state, r.ports, 90));
        assertNull(r.token("active"));
        assertNull(r.token("pending"));
        assertEquals(0, r.selected);
    }

    /** A non-owner thread cannot load, promote, route callbacks or clean up any binding. */
    @Test public void nonOwnerCannotMutateAnyBinding() throws Exception {
        Runtime r = new Runtime();
        BannerPreparedState state = armBanner(r, 100, "evt-a");
        r.ports.onWallResult(r.token("active"), dueAt(r, state, BASE));
        String token = r.token("active");
        List<String> before = new ArrayList<>(r.sink.visual);
        r.owner = false;
        assertFalse(r.ports.loadPreparedBanner(state));
        assertFalse(r.ports.selectActiveBanner(101));
        r.ports.abortActivation();
        r.ports.onWallResult(token, dueAt(r, state, BASE));
        assertEquals(before, r.sink.visual);
        assertEquals(token, r.token("active"));
        assertEquals(100, r.selected);
    }

    /** Failed selection cannot promote a pending binding; an unarmed revision stays pending-only. */
    @Test public void failedSelectionCannotPromotePending() throws Exception {
        Runtime r = new Runtime();
        assertTrue(r.ports.retireVideoVisualOwner());
        assertTrue(r.ports.retireManifestedVisualOwner());
        BannerPreparedState state = prepared("src", "evt-a");
        assertTrue(r.ports.loadPreparedBanner(state));
        // No armPreparedManifest step ran, so no generation was captured: selection must refuse.
        assertFalse(r.ports.selectActiveBanner(110));
        assertNull(r.token("active"));
        r.ports.abortActivation();
        assertNull(r.token("pending"));
    }

    /** Null/mismatched manifest refuses ARM without mutating an active binding. */
    @Test public void mismatchedManifestRefusesArm() throws Exception {
        Runtime r = new Runtime();
        assertTrue(r.ports.retireManifestedVisualOwner());
        BannerPreparedState state = prepared("src", "evt-a");
        assertTrue(r.ports.loadPreparedBanner(state));
        OverlayManifest other = bannerManifest("other", "evt-a");
        assertFalse(r.ports.armPreparedManifest(5, other));
        assertFalse(r.ports.armPreparedManifest(5, null));
        assertNull(r.token("active"));
    }
}
