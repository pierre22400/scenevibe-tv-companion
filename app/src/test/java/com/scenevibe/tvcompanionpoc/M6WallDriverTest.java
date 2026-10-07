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
 * Deterministic, Android-free JVM tests for the WALL Android driver ({@link WallClockDriver} plus
 * its injectable {@link WallClockSource} / {@link WallWaitScheduler} seams), driving the REAL
 * common-owner Banner ports ({@link LiveBannerRuntimePorts}) and the UNCHANGED pure
 * {@link WallCalendarScheduler} through a recording {@link SceneRuntimeController.SceneSink}. They
 * cover the section-14 / section-18 "Driver" matrix with fake epoch/elapsed sources: forward and
 * backward jumps, drift under/over the 1000 ms threshold, an explicit signal forcing re-evaluation
 * under threshold, deep-sleep inclusion in elapsed vs uptime exclusion, a read interval &gt; 50 ms
 * rejected with bounded retries then fail-closed, a decreasing monotonic counter rejected, a
 * cancelled/replaced callback delivered as a NO-OP, generation/ticket overflow failing closed, and
 * a process restart yielding a fresh temporal context. No Android, no real clock: the driver is
 * fully exercised on the JVM. The pure {@code wall/} package and MediaCalendarScheduler are
 * untouched.
 */
public final class M6WallDriverTest {

    // ---- fakes --------------------------------------------------------------------------

    /** Fake drawing seam proving exactly-one-visual-scene while the driver drives the owner. */
    private static final class RecordingSink implements SceneRuntimeController.SceneSink {
        final List<String> visual = new ArrayList<>();
        int visibleCount;
        @Override public boolean preflight(OverlayManifest.Scene scene) { return true; }
        @Override public void show(OverlayManifest.Scene scene) {
            visual.add("show:" + scene.id);
            visibleCount++;
            assertTrue("exactly one visual scene", visibleCount <= 1);
        }
        @Override public void hide(OverlayManifest.Scene scene) {
            visual.add("hide:" + scene.id);
            if (visibleCount > 0) visibleCount--;
        }
        @Override public void hideAll() { visual.add("hide-all"); visibleCount = 0; }
    }

    /**
     * Scriptable civil+monotonic source. Each {@code elapsedRealtimeMs()} call returns the next
     * scripted value (so a read can be bracketed with a wide/narrow window or a decreasing
     * counter); {@code currentEpochMs()} returns the current scripted civil epoch. This is the
     * section-18 "fake sources epoch/elapsed/uptime" seam.
     */
    private static final class FakeClock implements WallClockSource {
        long epoch;
        final List<Long> elapsedScript = new ArrayList<>();
        int elapsedCursor;
        long epochReads, elapsedReads;
        FakeClock(long epoch, long elapsed) { this.epoch = epoch; steadyElapsed(elapsed); }
        /** Make every bracket read return the same elapsed value (zero-width, coherent window). */
        void steadyElapsed(long elapsed) {
            elapsedScript.clear();
            elapsedCursor = 0;
            for (int i = 0; i < 2048; i++) elapsedScript.add(elapsed);
        }
        /** Advance both bases by the same real delta (no drift). */
        void advance(long deltaMs) { epoch += deltaMs; bumpElapsed(deltaMs); }
        /** Advance only the monotonic base (deep sleep / drift measurement). */
        void bumpElapsed(long deltaMs) {
            long base = elapsedScript.isEmpty() ? 0 : elapsedScript.get(elapsedScript.size() - 1);
            elapsedScript.clear();
            elapsedCursor = 0;
            for (int i = 0; i < 2048; i++) elapsedScript.add(base + deltaMs);
        }
        /** Script an explicit sequence of elapsed reads (for wide-window / decreasing tests). */
        void scriptElapsed(long... values) {
            elapsedScript.clear();
            elapsedCursor = 0;
            for (long v : values) elapsedScript.add(v);
            // Pad with the last value so later reads stay coherent.
            long last = values.length == 0 ? 0 : values[values.length - 1];
            for (int i = 0; i < 2048; i++) elapsedScript.add(last);
        }
        @Override public long currentEpochMs() { epochReads++; return epoch; }
        @Override public long elapsedRealtimeMs() {
            elapsedReads++;
            long v = elapsedScript.get(Math.min(elapsedCursor, elapsedScript.size() - 1));
            elapsedCursor++;
            return v;
        }
    }

    /** Fake single-wait seam capturing the pending runnable so a fire is driven deterministically. */
    private static final class FakeWaits implements WallWaitScheduler {
        Runnable pending;
        long lastDelay = -1;
        int scheduleCount, cancelCount;
        boolean failRegistration;
        @Override public boolean schedule(long delayMs, Runnable onFire) {
            scheduleCount++;
            if (failRegistration) return false;
            lastDelay = delayMs;
            pending = onFire;
            return true;
        }
        @Override public void cancel() { cancelCount++; pending = null; }
        /** Simulate Android delivering the single pending runnable now. */
        void fire() {
            Runnable r = pending;
            assertNotNull("a wait must be pending to fire", r);
            r.run();
        }
        /** Simulate Android delivering a specific (possibly stale) captured runnable. */
        static void deliver(Runnable r) { r.run(); }
    }

    /** The common-owner + driver composition fake, mirroring the FEAT-002 owner fixture style. */
    private static final class Fixture {
        boolean owner = true;
        boolean videoVisible;
        long selected;
        final List<String> eligibility = new ArrayList<>();
        final List<WallDriverDiagnostics.Code> diag = new ArrayList<>();
        final RecordingSink sink = new RecordingSink();
        final SceneRuntimeController controller = new SceneRuntimeController(sink);
        final WallCalendarScheduler scheduler = new WallCalendarScheduler();
        final LiveBannerRuntimePorts ports;
        final FakeClock clock;
        final FakeWaits waits = new FakeWaits();
        final WallClockDriver driver;
        Fixture(long epoch, long elapsed) {
            clock = new FakeClock(epoch, elapsed);
            ports = new LiveBannerRuntimePorts(() -> owner, () -> controller,
                    () -> videoVisible = false,
                    () -> {},
                    () -> {},
                    revision -> selected = revision,
                    eligible -> eligibility.add(Boolean.toString(eligible)));
            driver = new WallClockDriver(clock, waits, scheduler, ports, diag::add);
        }
        boolean visible() { return controller.hasVisibleScene() || videoVisible; }
        String visibleId() { return controller.visibleSceneId(); }
    }

    // ---- fixtures -----------------------------------------------------------------------

    private static final long BASE = 1_000_000_000_000L;

    private static WallEvent window(String id, long start, long durationMs) {
        return new WallEvent(id, start, start + durationMs);
    }

    private static WallCalendar calendarOf(WallEvent... events) {
        long start = Long.MAX_VALUE, end = Long.MIN_VALUE;
        for (WallEvent e : events) {
            start = Math.min(start, e.startEpochMs());
            end = Math.max(end, e.endEpochMs());
        }
        return new WallCalendar(start, end, Arrays.asList(events));
    }

    private static OverlayManifest.Scene scene(String id) {
        return new OverlayManifest.Scene(id, 0, 6_000, new ArrayList<>());
    }

    private static OverlayManifest bannerManifest(String sourceId, String... sceneIds) {
        List<OverlayManifest.Scene> scenes = new ArrayList<>();
        for (String id : sceneIds) scenes.add(scene(id));
        return new OverlayManifest("banner:" + sourceId, "banner", sourceId, "wall", "continue", scenes);
    }

    /** Promote one Banner through the real owner, returning the calendar the driver will anchor. */
    private static WallCalendar promote(Fixture f, long revision, WallEvent... windows) {
        WallCalendar calendar = calendarOf(windows);
        String[] ids = new String[windows.length];
        for (int i = 0; i < windows.length; i++) ids[i] = windows[i].eventId();
        BannerPreparedState state = new BannerPreparedState(calendar, bannerManifest("src-" + revision, ids));
        assertEquals(InstallationStatus.ARMED, BannerPreparedState.arm(state, f.ports, revision));
        return calendar;
    }

    /** Read the owner's active activation token for driving the driver under the real binding. */
    private static String activeToken(Fixture f) throws Exception {
        Field field = f.ports.getClass().getDeclaredField("active");
        field.setAccessible(true);
        Object binding = field.get(f.ports);
        if (binding == null) return null;
        Field token = binding.getClass().getDeclaredField("token");
        token.setAccessible(true);
        return (String) token.get(binding);
    }

    // ---- ARM / promotion ----------------------------------------------------------------

    /** First WALL evaluation occurs only after promotion; an in-window ARM shows exactly one scene. */
    @Test public void armAfterPromotionInWindowShowsOneScene() throws Exception {
        Fixture f = new Fixture(BASE, 10_000);
        WallCalendar cal = promote(f, 7, window("evt-a", BASE, 6_000));
        assertFalse(f.visible());
        assertTrue(f.driver.arm(activeToken(f), cal));
        assertTrue(f.driver.isArmed());
        assertEquals("evt-a", f.visibleId());
        // A single bounded wait is armed, relative and <= 1000 ms.
        assertEquals(1, f.waits.scheduleCount);
        assertTrue(f.waits.lastDelay >= 0 && f.waits.lastDelay <= WallClockDriver.WALL_MAX_WAIT_MS);
    }

    /** ARM before start shows nothing; the wait is bounded to <= 1000 ms toward the start boundary. */
    @Test public void armBeforeStartShowsNothingAndWaitsBounded() throws Exception {
        Fixture f = new Fixture(BASE, 10_000);
        WallCalendar cal = promote(f, 8, window("evt-a", BASE + 10_000, 6_000));
        assertTrue(f.driver.arm(activeToken(f), cal));
        assertFalse(f.visible());
        assertEquals(WallClockDriver.WALL_MAX_WAIT_MS, f.waits.lastDelay);
    }

    /** A failed wait registration fails ARM closed: masked, disarmed, durable kept, no render. */
    @Test public void failedWaitRegistrationFailsArmClosed() throws Exception {
        Fixture f = new Fixture(BASE, 10_000);
        WallCalendar cal = promote(f, 9, window("evt-a", BASE, 6_000));
        f.waits.failRegistration = true;
        assertFalse(f.driver.arm(activeToken(f), cal));
        assertFalse(f.driver.isArmed());
        assertFalse(f.visible());
        assertTrue(f.diag.contains(WallDriverDiagnostics.Code.WALL_CLOCK_INVALID));
    }

    // ---- fresh evaluation on fire -------------------------------------------------------

    /** Every callback re-reads fresh civil time: a fired wait after start shows the window. */
    @Test public void firedWaitReadsFreshEpochAndShows() throws Exception {
        Fixture f = new Fixture(BASE - 5_000, 10_000);
        WallCalendar cal = promote(f, 10, window("evt-a", BASE, 6_000));
        assertTrue(f.driver.arm(activeToken(f), cal));
        assertFalse(f.visible());
        // Time (and monotonic) advance together into the window, then the single wait fires.
        f.clock.advance(5_000);
        f.waits.fire();
        assertEquals("evt-a", f.visibleId());
    }

    /** A window fully expired by a fresh fire emits EXIT and hides, no replay of missed windows. */
    @Test public void firedWaitAfterEndExpiresScene() throws Exception {
        Fixture f = new Fixture(BASE, 10_000);
        WallCalendar cal = promote(f, 11, window("evt-a", BASE, 6_000));
        assertTrue(f.driver.arm(activeToken(f), cal));
        assertEquals("evt-a", f.visibleId());
        f.clock.advance(6_000);
        f.waits.fire();
        assertFalse(f.visible());
    }

    // ---- clock jump / reanchor (section 14) ---------------------------------------------

    /** Forward +30 min skips intermediate windows and shows only the active window at the new epoch. */
    @Test public void forwardThirtyMinuteJumpSkipsIntermediateWindows() throws Exception {
        Fixture f = new Fixture(BASE, 10_000);
        WallCalendar cal = promote(f, 12,
                window("evt-a", BASE, 6_000),
                window("evt-b", BASE + 60_000, 6_000),
                window("evt-c", BASE + 1_800_000, 6_000));
        assertTrue(f.driver.arm(activeToken(f), cal));
        assertEquals("evt-a", f.visibleId());
        // A +30 min civil jump with NO matching monotonic advance => large drift => reanchor.
        f.clock.epoch = BASE + 1_800_000;
        f.waits.fire();
        assertEquals("evt-c", f.visibleId());
        assertTrue(f.diag.contains(WallDriverDiagnostics.Code.WALL_CLOCK_REEVALUATED));
    }

    /** Backward -5 min can re-show an already-active window; no MEDIA-seek logic, fresh sample only. */
    @Test public void backwardFiveMinuteJumpCanReshowWindow() throws Exception {
        Fixture f = new Fixture(BASE + 300_000, 10_000);
        WallCalendar cal = promote(f, 13,
                window("evt-a", BASE, 6_000),
                window("evt-b", BASE + 300_000, 6_000));
        assertTrue(f.driver.arm(activeToken(f), cal));
        assertEquals("evt-b", f.visibleId());
        // Network correction -5 min (civil only) => drift >= threshold => reanchor to evt-a.
        f.clock.epoch = BASE;
        f.waits.fire();
        assertEquals("evt-a", f.visibleId());
    }

    /** Drift just under 1000 ms on a plain wait does NOT reanchor (no WALL_CLOCK_REEVALUATED). */
    @Test public void driftUnderThresholdDoesNotReanchor() throws Exception {
        Fixture f = new Fixture(BASE, 10_000);
        WallCalendar cal = promote(f, 14, window("evt-a", BASE, 6_000));
        assertTrue(f.driver.arm(activeToken(f), cal));
        long genBefore = f.driver.wallGeneration();
        // Monotonic advances 1000 ms but civil advances 1999 ms => drift 999 ms (< 1000).
        f.clock.bumpElapsed(1_000);
        f.clock.epoch = BASE + 1_999;
        f.waits.fire();
        assertEquals(genBefore, f.driver.wallGeneration());
        assertFalse(f.diag.contains(WallDriverDiagnostics.Code.WALL_CLOCK_REEVALUATED));
    }

    /** Drift at/over 1000 ms reanchors and bumps wallGeneration. */
    @Test public void driftOverThresholdReanchors() throws Exception {
        Fixture f = new Fixture(BASE, 10_000);
        WallCalendar cal = promote(f, 15, window("evt-a", BASE, 6_000));
        assertTrue(f.driver.arm(activeToken(f), cal));
        long genBefore = f.driver.wallGeneration();
        f.clock.bumpElapsed(1_000);
        f.clock.epoch = BASE + 2_000; // drift 1000 ms exactly
        f.waits.fire();
        assertEquals(genBefore + 1, f.driver.wallGeneration());
        assertTrue(f.diag.contains(WallDriverDiagnostics.Code.WALL_CLOCK_REEVALUATED));
    }

    /** An explicit signal forces re-evaluation and reanchor even with zero drift (under threshold). */
    @Test public void explicitSignalForcesReevaluationUnderThreshold() throws Exception {
        Fixture f = new Fixture(BASE - 1_000, 10_000);
        WallCalendar cal = promote(f, 16, window("evt-a", BASE, 6_000));
        assertTrue(f.driver.arm(activeToken(f), cal));
        assertFalse(f.visible());
        long genBefore = f.driver.wallGeneration();
        // Civil and monotonic advance together (zero drift) into the window, then a signal arrives.
        f.clock.advance(1_000);
        f.driver.onSignal();
        assertEquals("evt-a", f.visibleId());
        assertEquals(genBefore + 1, f.driver.wallGeneration());
    }

    /** The manifest generation captured at ARM is NOT changed by a clock correction (distinct guards). */
    @Test public void clockCorrectionDoesNotChangeManifestGeneration() throws Exception {
        Fixture f = new Fixture(BASE, 10_000);
        WallCalendar cal = promote(f, 17, window("evt-a", BASE, 6_000));
        long manifestGenBefore = f.controller.currentGeneration();
        assertTrue(f.driver.arm(activeToken(f), cal));
        long wallGenBefore = f.driver.wallGeneration();
        f.clock.bumpElapsed(1_000);
        f.clock.epoch = BASE + 5_000; // big drift => reanchor
        f.waits.fire();
        // wallGeneration (temporal guard) advanced, manifest generation (controller) did not.
        assertEquals(wallGenBefore + 1, f.driver.wallGeneration());
        assertEquals(manifestGenBefore, f.controller.currentGeneration());
    }

    // ---- deep sleep inclusion vs uptime exclusion ---------------------------------------

    /**
     * Deep sleep is included in elapsed: a wait that fires after a long sleep re-reads fresh civil
     * time and shows only the still-active window, with no missed-window replay. The driver never
     * passes an elapsed value as an uptime timestamp (it only ever schedules a relative delay).
     */
    @Test public void deepSleepInclusionShowsOnlyStillActiveWindow() throws Exception {
        Fixture f = new Fixture(BASE, 10_000);
        WallCalendar cal = promote(f, 18,
                window("evt-a", BASE, 6_000),
                window("evt-b", BASE + 100_000, 6_000));
        assertTrue(f.driver.arm(activeToken(f), cal));
        assertEquals("evt-a", f.visibleId());
        // Deep sleep: both civil and monotonic advance together by 100s (elapsed includes sleep).
        f.clock.advance(100_000);
        f.waits.fire();
        assertEquals("evt-b", f.visibleId());
        assertEquals(1, f.sink.visibleCount);
    }

    // ---- bracketed read validation (section 3) ------------------------------------------

    /** A read interval > 50 ms is rejected; two retries then a coherent read succeeds. */
    @Test public void wideReadWindowRejectedThenCoherentRetrySucceeds() throws Exception {
        Fixture f = new Fixture(BASE, 10_000);
        WallCalendar cal = promote(f, 19, window("evt-a", BASE, 6_000));
        // First two bracketed reads are wide (before=0, after=200 => window 200 > 50), third narrow.
        f.clock.scriptElapsed(0, 200, 100, 300, 500, 500);
        assertTrue(f.driver.arm(activeToken(f), cal));
        assertEquals("evt-a", f.visibleId());
    }

    /** A persistently wide read window fails closed after the bounded retries (ARM refused). */
    @Test public void persistentlyWideReadWindowFailsClosed() throws Exception {
        Fixture f = new Fixture(BASE, 10_000);
        WallCalendar cal = promote(f, 20, window("evt-a", BASE, 6_000));
        // Every bracket is wide (window 1000 > 50): before/after alternate 0,1000 repeatedly.
        f.clock.scriptElapsed(0, 1_000, 0, 1_000, 0, 1_000, 0, 1_000);
        assertFalse(f.driver.arm(activeToken(f), cal));
        assertFalse(f.driver.isArmed());
        assertTrue(f.diag.contains(WallDriverDiagnostics.Code.WALL_CLOCK_INVALID));
    }

    /** A decreasing monotonic counter is rejected and, if persistent, fails closed. */
    @Test public void decreasingElapsedRejectedFailsClosed() throws Exception {
        Fixture f = new Fixture(BASE, 10_000);
        WallCalendar cal = promote(f, 21, window("evt-a", BASE, 6_000));
        // after < before on every bracket (1000 then 0) => incoherent, exhausts retries.
        f.clock.scriptElapsed(1_000, 0, 1_000, 0, 1_000, 0, 1_000, 0);
        assertFalse(f.driver.arm(activeToken(f), cal));
        assertFalse(f.driver.isArmed());
        assertTrue(f.diag.contains(WallDriverDiagnostics.Code.WALL_CLOCK_INVALID));
    }

    // ---- cancelled / replaced callbacks (section 13) ------------------------------------

    /** A cancelled callback delivered late by Android is a NO-OP (disarm neutralizes it). */
    @Test public void cancelledCallbackDeliveredIsNoOp() throws Exception {
        Fixture f = new Fixture(BASE - 5_000, 10_000);
        WallCalendar cal = promote(f, 22, window("evt-a", BASE, 6_000));
        assertTrue(f.driver.arm(activeToken(f), cal));
        Runnable stale = f.waits.pending;
        assertNotNull(stale);
        // Disarm (e.g. a Video selection cancels the WALL path) then Android still delivers it.
        f.driver.disarm();
        f.clock.advance(5_000);
        FakeWaits.deliver(stale);
        assertFalse(f.visible());
    }

    /** A replaced ticket neutralizes an OLD captured callback even without a generation change. */
    @Test public void replacedTicketNeutralizesOldCallback() throws Exception {
        Fixture f = new Fixture(BASE - 5_000, 10_000);
        WallCalendar cal = promote(f, 23, window("evt-a", BASE, 6_000));
        assertTrue(f.driver.arm(activeToken(f), cal));
        Runnable oldCallback = f.waits.pending;
        // A fresh evaluation (signal) re-arms with a NEW ticket; the old captured callback is stale.
        f.driver.onSignal();
        assertNotSame(oldCallback, f.waits.pending);
        List<String> before = new ArrayList<>(f.sink.visual);
        f.clock.advance(5_000);
        FakeWaits.deliver(oldCallback);
        assertEquals(before, f.sink.visual);
    }

    // ---- suspend / resume (section 3) ---------------------------------------------------

    /** A local suspension hides immediately and disarms the wait; resume re-evaluates the window. */
    @Test public void suspendHidesAndResumeReevaluates() throws Exception {
        Fixture f = new Fixture(BASE, 10_000);
        WallCalendar cal = promote(f, 24, window("evt-a", BASE, 6_000));
        assertTrue(f.driver.arm(activeToken(f), cal));
        assertEquals("evt-a", f.visibleId());
        f.driver.onSuspend();
        assertFalse(f.visible());
        assertTrue(f.diag.contains(WallDriverDiagnostics.Code.WALL_DISPLAY_SUSPENDED));
        f.driver.onResume();
        assertEquals("evt-a", f.visibleId());
    }

    // ---- horizon exhaustion -------------------------------------------------------------

    /** After the horizon the bounded <=1s check still re-arms so a backward correction can re-show. */
    @Test public void horizonExhaustedKeepsBoundedHeartbeat() throws Exception {
        Fixture f = new Fixture(BASE + 10_000, 10_000);
        WallCalendar cal = promote(f, 25, window("evt-a", BASE, 6_000));
        assertTrue(f.driver.arm(activeToken(f), cal));
        // Already past the only window and its end boundary => no future boundary.
        assertFalse(f.visible());
        assertTrue(f.diag.contains(WallDriverDiagnostics.Code.WALL_HORIZON_EXHAUSTED));
        assertEquals(WallClockDriver.WALL_MAX_WAIT_MS, f.waits.lastDelay);
        // A backward correction into the window re-shows it with a fresh sample.
        f.clock.epoch = BASE + 1_000;
        f.waits.fire();
        assertEquals("evt-a", f.visibleId());
    }

    // ---- overflow fail-closed -----------------------------------------------------------

    /** wallGeneration overflow fails ARM closed with no silent reuse. */
    @Test public void wallGenerationOverflowFailsArmClosed() throws Exception {
        Fixture f = new Fixture(BASE, 10_000);
        WallCalendar cal = promote(f, 26, window("evt-a", BASE, 6_000));
        Field gen = WallClockDriver.class.getDeclaredField("wallGeneration");
        gen.setAccessible(true);
        gen.setLong(f.driver, Long.MAX_VALUE);
        assertFalse(f.driver.arm(activeToken(f), cal));
        assertFalse(f.driver.isArmed());
        assertTrue(f.diag.contains(WallDriverDiagnostics.Code.WALL_DEADLINE_FAILED));
    }

    /** Timer ticket overflow fails closed on re-arm (disarms, no new wait). */
    @Test public void ticketOverflowFailsClosed() throws Exception {
        Fixture f = new Fixture(BASE, 10_000);
        WallCalendar cal = promote(f, 27, window("evt-a", BASE, 6_000));
        Field ticket = WallClockDriver.class.getDeclaredField("ticket");
        ticket.setAccessible(true);
        ticket.setLong(f.driver, Long.MAX_VALUE);
        assertFalse(f.driver.arm(activeToken(f), cal));
        assertTrue(f.diag.contains(WallDriverDiagnostics.Code.WALL_DEADLINE_FAILED));
    }

    // ---- process restart = fresh temporal context ---------------------------------------

    /** A new driver starts disarmed with no restored anchor/generation/ticket (fresh context). */
    @Test public void processRestartYieldsFreshTemporalContext() throws Exception {
        Fixture f = new Fixture(BASE, 10_000);
        WallCalendar cal = promote(f, 28, window("evt-a", BASE, 6_000));
        assertTrue(f.driver.arm(activeToken(f), cal));
        long wallGen = f.driver.wallGeneration();
        assertTrue(wallGen > 0);
        // "Restart": a brand new driver over the same durable calendar carries no prior volatile state.
        WallClockDriver restarted = new WallClockDriver(f.clock, new FakeWaits(), new WallCalendarScheduler(),
                f.ports, f.diag::add);
        assertFalse(restarted.isArmed());
        assertEquals(0L, restarted.wallGeneration());
    }

    // ---- pending never renders through the driver ---------------------------------------

    /** The driver cannot be armed under a token that was never promoted (pending never renders). */
    @Test public void driverRefusesNullTokenOrCalendar() {
        Fixture f = new Fixture(BASE, 10_000);
        assertFalse(f.driver.arm(null, calendarOf(window("evt-a", BASE, 6_000))));
        assertFalse(f.driver.arm("banner-activation-1", null));
        assertFalse(f.driver.isArmed());
    }

    // ---- composition seam: arm INSIDE the selection sink (the real OverlayService wiring) ----

    /**
     * The real {@code OverlayService} composition arms the WALL driver <em>inside</em> the Banner
     * selection sink ({@code revision -> { activeRevision = revision; armWallDriver(); }}), so the
     * driver's synchronous first {@code emit} runs back through {@link LiveBannerRuntimePorts#onWallResult}
     * while the owner is still mid-{@code selectActiveBanner}. {@code armWallDriver} reads the owner's
     * {@code activeToken()} / {@code activeState()} and arms the driver under that exact token. This
     * fixture reproduces that wiring faithfully (the owner, driver and pure scheduler are the REAL
     * production classes); neither {@link M6WallDriverTest} nor {@link M6BannerOwnerTest} otherwise
     * promotes and arms in a single synchronous step.
     *
     * <p>Regression guard: before the owner established the active binding ahead of invoking the
     * selection sink, {@code matching(token)} saw {@code active == null} when the driver's first DUE
     * landed, so a Banner whose window was already live at promotion was dropped and the idempotent
     * {@code <=1s} heartbeat never re-emitted it. This test fails in that regressed ordering and
     * passes once promotion sets the active binding before the sink runs.</p>
     */
    private static final class ComposedFixture {
        boolean owner = true;
        boolean videoVisible;
        long selected = -1;
        final List<String> eligibility = new ArrayList<>();
        final List<WallDriverDiagnostics.Code> diag = new ArrayList<>();
        final RecordingSink sink = new RecordingSink();
        final SceneRuntimeController controller = new SceneRuntimeController(sink);
        final WallCalendarScheduler scheduler = new WallCalendarScheduler();
        final FakeClock clock;
        final FakeWaits waits = new FakeWaits();
        final LiveBannerRuntimePorts ports;
        final WallClockDriver driver;
        int armWallDriverCalls;
        ComposedFixture(long epoch, long elapsed) {
            clock = new FakeClock(epoch, elapsed);
            // The selection sink wires armWallDriver() EXACTLY as OverlayService does: it reads the
            // owner's active token + prepared calendar and arms the real driver synchronously.
            ports = new LiveBannerRuntimePorts(() -> owner, () -> controller,
                    () -> videoVisible = false,
                    () -> {},
                    () -> {},
                    revision -> { selected = revision; armWallDriver(); },
                    eligible -> eligibility.add(Boolean.toString(eligible)));
            driver = new WallClockDriver(clock, waits, scheduler, ports, diag::add);
        }
        /** Faithful copy of OverlayService.armWallDriver(): arm under the owner's active binding. */
        private void armWallDriver() {
            armWallDriverCalls++;
            String token = ports.activeToken();
            BannerPreparedState state = ports.activeState();
            if (token == null || state == null) { driver.disarm(); return; }
            driver.arm(token, state.calendar);
        }
        String visibleId() { return controller.visibleSceneId(); }
        boolean visible() { return controller.hasVisibleScene() || videoVisible; }
    }

    /**
     * Promote a Banner through the REAL owner whose selection sink arms the driver synchronously
     * (the exact OverlayService ordering); a window already live at promotion must render exactly
     * one scene immediately after promotion, driven by the driver's synchronous first emit.
     */
    @Test public void armInsideSelectionSinkRendersWindowLiveAtPromotion() {
        ComposedFixture f = new ComposedFixture(BASE, 10_000);
        WallCalendar cal = calendarOf(window("evt-a", BASE, 6_000));
        BannerPreparedState state = new BannerPreparedState(cal, bannerManifest("src-live", "evt-a"));
        // The ordered ARM promotes and (inside the sink) arms the driver in one synchronous call.
        assertEquals(InstallationStatus.ARMED, BannerPreparedState.arm(state, f.ports, 7));
        assertEquals(1, f.armWallDriverCalls);
        assertTrue("driver armed inside the selection sink", f.driver.isArmed());
        // The first fresh evaluation's DUE lands while the owner is mid-promotion; the active
        // binding must already be set so matching(token) honors it and the window renders.
        assertTrue("a window live at promotion must render immediately", f.visible());
        assertEquals("evt-a", f.visibleId());
        assertEquals(1, f.sink.visibleCount);
        assertEquals(7, f.selected);
        // Exactly one bounded wait was armed, relative and <= 1000 ms, from the fresh epoch.
        assertEquals(1, f.waits.scheduleCount);
        assertTrue(f.waits.lastDelay >= 0 && f.waits.lastDelay <= WallClockDriver.WALL_MAX_WAIT_MS);
    }

    /**
     * Pending still never renders through the composition seam: a window whose start is in the
     * future is armed inside the selection sink but shows nothing until a later fresh fire, proving
     * the fix did not promote a pending binding into an early render.
     */
    @Test public void armInsideSelectionSinkBeforeStartShowsNothing() {
        ComposedFixture f = new ComposedFixture(BASE, 10_000);
        WallCalendar cal = calendarOf(window("evt-a", BASE + 10_000, 6_000));
        BannerPreparedState state = new BannerPreparedState(cal, bannerManifest("src-future", "evt-a"));
        assertEquals(InstallationStatus.ARMED, BannerPreparedState.arm(state, f.ports, 8));
        assertTrue(f.driver.isArmed());
        // Promotion happened and the driver armed, but the window is not yet open: nothing renders.
        assertFalse(f.visible());
        // Advancing into the window and firing the single bounded wait then shows exactly one scene.
        f.clock.advance(10_000);
        f.waits.fire();
        assertEquals("evt-a", f.visibleId());
        assertEquals(1, f.sink.visibleCount);
    }
}
