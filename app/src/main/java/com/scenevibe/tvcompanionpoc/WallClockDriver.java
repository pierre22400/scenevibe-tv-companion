package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.wall.WallCalendar;
import com.scenevibe.tvcompanionpoc.wall.WallCalendarScheduler;

/**
 * The Android WALL clock/driver that PRODUCES the {@link WallCalendarScheduler.Result} values the
 * common runtime owner routes (FEAT-002 {@link LiveBannerRuntimePorts#onWallResult}). It lives
 * OUTSIDE the pure {@code wall/} package so that package stays Android-free and payload-free (the
 * Phase B purity gate still passes), and it NEVER modifies the pure scheduler: it only feeds the
 * scheduler fresh explicit epochs via {@link WallCalendarScheduler#evaluate}.
 *
 * <h2>Temporal authority and anchor (section 3)</h2>
 * The sole authority is the OS civil UTC time. Each read is bracketed by the monotonic
 * {@link WallClockSource#elapsedRealtimeMs()} before and after the epoch; an incoherent sample, a
 * decreasing monotonic counter, or a read interval &gt; {@link #READ_WINDOW_MS} ms is rejected,
 * with at most {@link #MAX_IMMEDIATE_RETRIES} immediate retries before failing closed. The anchor
 * kept IN MEMORY ONLY is {@code {epochUtc, referenceElapsed, activationToken, wallGeneration}}; the
 * projection {@code anchorEpoch + elapsedDelta} is used ONLY to prepare a wait and to measure
 * drift, never to decide DUE. Every callback re-reads a FRESH civil sample before any selection.
 *
 * <h2>Bounded single wait (section 3)</h2>
 * At most one wait is armed at a time via {@link WallWaitScheduler#schedule(long, Runnable)} with a
 * RELATIVE duration {@code min(nextBoundary - now, WALL_MAX_WAIT_MS)} and never a zero-delay loop.
 * No {@code elapsedRealtime} value is passed as an uptime timestamp. A boundary exactly at
 * {@code now} is handled in the current evaluation, then the wait targets a strictly future
 * boundary; after the horizon is exhausted the bounded &lt;=1s check still allows a backward
 * correction to re-show a still-active window without creating a new occurrence.
 *
 * <h2>Guards and races (section 13)</h2>
 * Every callback is guarded by {@code wallGeneration} + a unique timer ticket + the active
 * activation token: a stale guard is a NO-OP even if Android still delivered the runnable. The
 * {@code wallGeneration} guard (the temporal source) and the manifest generation captured once at
 * ARM inside the owner are TWO DISTINCT guards and are never conflated: a clock correction bumps
 * {@code wallGeneration} and reanchors but NEVER reloads the manifest or retags an old callback.
 *
 * <h2>Clock jump / reanchor (section 14)</h2>
 * An absolute drift &gt;= {@link #DRIFT_THRESHOLD_MS} ms between the fresh civil sample and the
 * monotonic projection invalidates the anchor; an explicit time/zone/resume/service-reentry/wake
 * signal forces a re-evaluation even below the threshold. A significant correction cancels the
 * ticket, increments {@code wallGeneration}, takes a fresh anchor and recomputes at most one
 * EXIT+DUE through the pure scheduler.
 *
 * <h2>Durability</h2>
 * No anchor, {@code wallGeneration}, timer ticket or consumed cursor is durable. A process restart
 * yields a fresh temporal context: a new {@link WallClockDriver} starts disarmed with no anchor.
 *
 * <p>This core is Android-independent (constructor takes the injectable {@link WallClockSource},
 * {@link WallWaitScheduler}, pure {@link WallCalendarScheduler}, the owner result sink and a
 * bounded {@link WallDriverDiagnostics}); the genuinely Android-only glue (real
 * {@code System.currentTimeMillis}, {@code SystemClock.elapsedRealtime},
 * {@code Handler.postDelayed} and the dynamic time/interactivity receiver) lives in
 * {@link AndroidWallClockSource} / {@link AndroidWallWaitScheduler} / {@link AndroidWallSignalReceiver}
 * composed by the owner.</p>
 */
final class WallClockDriver {

    /** Maximum coherent civil-time read window: a read bracketed by a wider monotonic interval is rejected. */
    static final long READ_WINDOW_MS = 50L;
    /** At most two immediate retries of an incoherent civil read before failing closed. */
    static final int MAX_IMMEDIATE_RETRIES = 2;
    /** Absolute drift (fresh civil vs monotonic projection) at or above which the anchor is invalidated. */
    static final long DRIFT_THRESHOLD_MS = 1_000L;
    /** The single bounded wait is at most one second so a silent correction is discovered &lt;= 1s. */
    static final long WALL_MAX_WAIT_MS = 1_000L;

    /** Why a re-evaluation was requested, so the driver can decide to reanchor even below threshold. */
    enum Trigger {
        /** The first evaluation right after a safe promotion (ARM). */
        ARM,
        /** The single bounded wait fired. */
        WAIT,
        /** An explicit time-change / timezone-change / resume / service-reentry / wake signal. */
        SIGNAL
    }

    /** Immutable in-memory temporal anchor (section 3); never serialized, never durable. */
    private static final class Anchor {
        final long epochUtc;
        final long referenceElapsed;
        final String activationToken;
        final long wallGeneration;
        Anchor(long epochUtc, long referenceElapsed, String activationToken, long wallGeneration) {
            this.epochUtc = epochUtc;
            this.referenceElapsed = referenceElapsed;
            this.activationToken = activationToken;
            this.wallGeneration = wallGeneration;
        }
    }

    private final WallClockSource clock;
    private final WallWaitScheduler waits;
    private final WallCalendarScheduler scheduler;
    private final BannerInstallationRuntimePorts owner;
    private final WallDriverDiagnostics diagnostics;

    /** The in-memory anchor; null means disarmed (no volatile path). */
    private Anchor anchor;
    /** The in-memory WALL calendar driven under the current activation; never durable. */
    private WallCalendar calendar;
    /** Monotonic WALL temporal-source guard, distinct from the owner's manifest generation. */
    private long wallGeneration;
    /** Unique ticket for the single outstanding wait; a replaced ticket neutralizes an old callback. */
    private long ticket;
    /** Local Banner presentation eligibility; a suspension masks the volatile path. */
    private boolean eligible;
    /** True only between a successful ARM and a disarm; a callback outside this window is a NO-OP. */
    private boolean armed;

    /** Bind the injectable seams; construction loads nothing, reads no clock and arms no wait. */
    WallClockDriver(WallClockSource clock, WallWaitScheduler waits, WallCalendarScheduler scheduler,
            BannerInstallationRuntimePorts owner, WallDriverDiagnostics diagnostics) {
        if (clock == null || waits == null || scheduler == null || owner == null || diagnostics == null) {
            throw new IllegalArgumentException("Missing WALL driver seam");
        }
        this.clock = clock;
        this.waits = waits;
        this.scheduler = scheduler;
        this.owner = owner;
        this.diagnostics = diagnostics;
    }

    /** A bracketed civil-time read; {@code ok} false masks and disarms the volatile path. */
    private static final class Sample {
        final boolean ok;
        final long epochMs;
        final long elapsedMs;
        private Sample(boolean ok, long epochMs, long elapsedMs) {
            this.ok = ok;
            this.epochMs = epochMs;
            this.elapsedMs = elapsedMs;
        }
        static final Sample INVALID = new Sample(false, 0L, 0L);
        static Sample valid(long epochMs, long elapsedMs) { return new Sample(true, epochMs, elapsedMs); }
    }

    /**
     * Read the OS civil time bracketed by the monotonic clock before and after. Reject an
     * incoherent sample (out-of-domain epoch), a decreasing monotonic counter, or a read interval
     * wider than {@link #READ_WINDOW_MS}; retry immediately at most {@link #MAX_IMMEDIATE_RETRIES}
     * times then fail closed. The elapsed value returned is the midpoint of the bracket (section 3).
     */
    private Sample readSample(long lastElapsed) {
        long previous = lastElapsed;
        for (int attempt = 0; attempt <= MAX_IMMEDIATE_RETRIES; attempt++) {
            long before = clock.elapsedRealtimeMs();
            long epoch = clock.currentEpochMs();
            long after = clock.elapsedRealtimeMs();
            // Monotonic must never decrease (within the bracket or versus the previous reference).
            if (after < before || before < previous) continue;
            // The read window must be coherent and narrow.
            long window = after - before;
            if (window < 0L || window > READ_WINDOW_MS) continue;
            // The civil epoch must be inside the valid WALL domain.
            if (epoch < 0L || epoch > com.scenevibe.tvcompanionpoc.wall.WallEvent.MAX_EPOCH_MS) continue;
            long midpoint = before + (window / 2L);
            return Sample.valid(epoch, midpoint);
        }
        return Sample.INVALID;
    }

    /**
     * ARM the volatile WALL path right after the owner has safely promoted pending to active
     * (first WALL evaluation only after promotion). It takes the first fresh anchor, initializes
     * local eligibility, runs the first bounded evaluation, and registers the single next wait.
     * The wait registration must succeed before ARMED: an initial clock or registration error
     * fails ARM closed (returns false), masks and disarms the volatile path, keeps the durable and
     * emits a bounded diagnostic, triggering no commit/ACK.
     *
     * @param activationToken the owner's active activation token this driver drives under
     * @param calendar the already-prepared pure WALL horizon to load into the scheduler
     * @return true only when the anchor, first evaluation and the single wait all succeeded
     */
    boolean arm(String activationToken, WallCalendar calendar) {
        if (activationToken == null || calendar == null) return false;
        disarm();
        if (wallGeneration == Long.MAX_VALUE) {
            // The temporal-source guard cannot mint a fresh value: fail ARM closed, never reuse.
            diagnostics.record(WallDriverDiagnostics.Code.WALL_DEADLINE_FAILED);
            return false;
        }
        Sample sample = readSample(Long.MIN_VALUE);
        if (!sample.ok) {
            maskAndDisarm();
            return false;
        }
        this.wallGeneration++;
        this.anchor = new Anchor(sample.epochMs, sample.elapsedMs, activationToken, wallGeneration);
        this.calendar = calendar;
        this.eligible = true;
        this.armed = true;
        diagnostics.record(WallDriverDiagnostics.Code.WALL_ANCHORED);
        // Load the pure calendar then route the first fresh-epoch selection under the active token.
        WallCalendarScheduler.Result first = scheduler.load(calendar, sample.epochMs, eligible);
        emit(activationToken, first);
        if (!rearm(first, sample.epochMs)) {
            // The single wait could not be registered: ARM fails closed.
            maskAndDisarm();
            return false;
        }
        return true;
    }

    /**
     * The single-wait callback. Android may still deliver a cancelled/replaced runnable, so this is
     * guarded by the exact {@code wallGeneration} + {@code ticket} + active {@code activationToken}
     * captured when the wait was armed; any mismatch (or a disarmed driver) is a NO-OP. A valid fire
     * re-reads a FRESH civil sample, measures drift, reanchors on a significant correction, then
     * runs ONE bounded evaluation and re-arms the single wait. An old deadline never proves DUE by
     * itself; only a fresh sample under the current guards can.
     */
    private void onWaitFired(long firedGeneration, long firedTicket, String firedToken) {
        if (!armed || anchor == null) return;
        if (firedGeneration != wallGeneration || firedTicket != ticket
                || !anchor.activationToken.equals(firedToken)) {
            // Stale wallGeneration / stale ticket / stale token: NO-OP even though Android delivered it.
            return;
        }
        evaluate(Trigger.WAIT);
    }

    /**
     * External entry for an explicit Android time/interactivity signal (ACTION_TIME_CHANGED /
     * ACTION_TIMEZONE_CHANGED / SCREEN_ON / resume / service re-entry / wake). It forces a
     * re-evaluation even below the drift threshold while the driver is armed; it never reloads the
     * manifest. A disarmed driver ignores it.
     */
    void onSignal() {
        if (!armed || anchor == null) return;
        evaluate(Trigger.SIGNAL);
    }

    /**
     * A local suspension signal (SCREEN_OFF / overlay loss): suspend and hide presentation and
     * disarm the single wait, but keep the (in-memory) anchor so a return can re-evaluate. The
     * owner hides immediately via the controller eligibility path.
     */
    void onSuspend() {
        if (!armed) return;
        eligible = false;
        waits.cancel();
        diagnostics.record(WallDriverDiagnostics.Code.WALL_DISPLAY_SUSPENDED);
        if (anchor != null) forwardEligibility(anchor.activationToken, false);
    }

    /**
     * Return from a local suspension: regain eligibility and re-evaluate the current window even if
     * the eventId is identical. The controller's {@code onEligibility(true)} restores eligibility
     * but forces nothing visible (section 6/22), so the driver re-establishes the current selection
     * with a fresh {@link WallCalendarScheduler#load} at a FRESH epoch, which re-emits the DUE for a
     * still-active window. No manifest is reloaded; the owner's captured generation is unchanged.
     */
    void onResume() {
        if (!armed || anchor == null || calendar == null) return;
        eligible = true;
        forwardEligibility(anchor.activationToken, true);
        reevaluateCurrentWindow(Trigger.SIGNAL);
    }

    /**
     * Core bounded evaluation shared by ARM/WAIT/SIGNAL. Reads a FRESH civil sample, applies the
     * section-14 reanchor policy, runs exactly one {@link WallCalendarScheduler#evaluate} under the
     * fresh epoch, routes at most one EXIT then one DUE to the owner under the active token, and
     * re-arms the single bounded wait. An unreadable/out-of-domain clock masks and disarms.
     */
    private void evaluate(Trigger trigger) {
        sampleAndSelect(trigger, false);
    }

    /**
     * Re-establish the CURRENT window selection even when the pure scheduler would be idempotent
     * (identical eventId). Used on eligibility regain (section 6/22): the controller does not force
     * a show on {@code onEligibility(true)}, so the driver reloads the retained calendar at a fresh
     * epoch, which re-emits the DUE for a still-active window. No manifest is reloaded.
     */
    private void reevaluateCurrentWindow(Trigger trigger) {
        sampleAndSelect(trigger, true);
    }

    /**
     * Core bounded evaluation shared by ARM/WAIT/SIGNAL/resume. Reads a FRESH civil sample, applies
     * the section-14 reanchor policy, runs exactly one pure-scheduler selection under the fresh
     * epoch ({@link WallCalendarScheduler#evaluate}, or {@link WallCalendarScheduler#load} to force a
     * re-selection of the current window), routes at most one EXIT then one DUE to the owner under
     * the active token, and re-arms the single bounded wait. An unreadable/out-of-domain clock masks
     * and disarms.
     */
    private void sampleAndSelect(Trigger trigger, boolean forceReselect) {
        Anchor current = anchor;
        if (current == null) return;
        Sample sample = readSample(current.referenceElapsed);
        if (!sample.ok) {
            maskAndDisarm();
            return;
        }
        // Monotonic projection of what the civil clock "should" read now, used ONLY for drift.
        long elapsedDelta = sample.elapsedMs - current.referenceElapsed;
        long projectedEpoch = current.epochUtc + elapsedDelta;
        long drift = Math.abs(sample.epochMs - projectedEpoch);
        boolean reanchor = drift >= DRIFT_THRESHOLD_MS || trigger == Trigger.SIGNAL;
        if (reanchor) {
            if (wallGeneration == Long.MAX_VALUE) {
                // Temporal-source guard cannot advance: fail closed rather than reuse a generation.
                maskAndDisarm();
                return;
            }
            // A significant correction cancels the ticket, bumps wallGeneration and reanchors.
            // The manifest generation captured at ARM inside the owner is NOT touched here.
            this.wallGeneration++;
            this.anchor = new Anchor(sample.epochMs, sample.elapsedMs,
                    current.activationToken, wallGeneration);
            current = this.anchor;
            diagnostics.record(WallDriverDiagnostics.Code.WALL_CLOCK_REEVALUATED);
            diagnostics.record(WallDriverDiagnostics.Code.WALL_ANCHORED);
        }
        // Fresh explicit epoch into the UNCHANGED pure scheduler; at most one EXIT then one DUE.
        WallCalendarScheduler.Result result = forceReselect && calendar != null
                ? scheduler.load(calendar, sample.epochMs, eligible)
                : scheduler.evaluate(sample.epochMs, eligible);
        emit(current.activationToken, result);
        rearm(result, sample.epochMs);
    }

    /**
     * Register the single next bounded wait with a relative duration {@code min(nextBoundary - now,
     * WALL_MAX_WAIT_MS)}. A boundary at or before {@code now} and an exhausted horizon still arm the
     * bounded &lt;=1s check so a backward correction can re-show a still-active window; nothing new
     * is ever created. A fresh unique ticket is minted so a previously posted callback is
     * neutralized. Returns false (and disarms) when the ticket counter or the wait registration
     * fails closed.
     */
    private boolean rearm(WallCalendarScheduler.Result result, long nowEpoch) {
        if (!armed || anchor == null) return false;
        if (ticket == Long.MAX_VALUE) {
            diagnostics.record(WallDriverDiagnostics.Code.WALL_DEADLINE_FAILED);
            return false;
        }
        long boundary = result == null ? WallCalendarScheduler.NO_BOUNDARY : result.nextBoundaryEpochMs();
        long delay;
        if (boundary == WallCalendarScheduler.NO_BOUNDARY) {
            // Horizon exhausted: keep the bounded <=1s heartbeat so a backward correction re-shows.
            diagnostics.record(WallDriverDiagnostics.Code.WALL_HORIZON_EXHAUSTED);
            delay = WALL_MAX_WAIT_MS;
        } else {
            // Relative duration from the FRESH civil epoch; never negative, never zero-looping, and
            // never an elapsedRealtime value treated as an uptime timestamp.
            long untilBoundary = boundary - nowEpoch;
            delay = Math.max(1L, Math.min(untilBoundary, WALL_MAX_WAIT_MS));
        }
        final long firedGeneration = wallGeneration;
        final long firedTicket = ++ticket;
        final String firedToken = anchor.activationToken;
        if (!waits.schedule(delay, () -> onWaitFired(firedGeneration, firedTicket, firedToken))) {
            diagnostics.record(WallDriverDiagnostics.Code.WALL_DEADLINE_FAILED);
            return false;
        }
        return true;
    }

    /** Route one already-selected pure result to the owner and record bounded effect diagnostics. */
    private void emit(String activationToken, WallCalendarScheduler.Result result) {
        if (result == null) return;
        for (WallCalendarScheduler.Effect effect : result.effects()) {
            diagnostics.record(effect.kind() == WallCalendarScheduler.Kind.EXIT
                    ? WallDriverDiagnostics.Code.WALL_EVENT_EXPIRED
                    : WallDriverDiagnostics.Code.WALL_EVENT_DUE);
        }
        owner.onWallResult(activationToken, result);
    }

    /** Forward a Banner eligibility transition to the common owner under the active token. */
    private void forwardEligibility(String activationToken, boolean nowEligible) {
        if (owner instanceof LiveBannerRuntimePorts) {
            ((LiveBannerRuntimePorts) owner).onEligibility(activationToken, nowEligible);
        }
    }

    /**
     * A clock that cannot be read / is out of domain (or a fail-closed registration) masks and
     * disarms the volatile path: it hides any presented Banner via the owner eligibility path,
     * cancels the wait and drops the anchor, keeping the durable installation and the owner's
     * manifest binding intact. It triggers NO new commit or ACK.
     */
    private void maskAndDisarm() {
        diagnostics.record(WallDriverDiagnostics.Code.WALL_CLOCK_INVALID);
        if (anchor != null) forwardEligibility(anchor.activationToken, false);
        disarm();
    }

    /**
     * Disarm the volatile path: cancel the single wait, drop the in-memory anchor and mark the
     * driver unarmed so any late-delivered runnable is a NO-OP. The durable installation, the
     * pure scheduler calendar and the owner's manifest binding are untouched; no new commit/ACK is
     * triggered. Idempotent.
     */
    void disarm() {
        this.armed = false;
        this.anchor = null;
        this.calendar = null;
        this.eligible = false;
        waits.cancel();
    }

    /** The current WALL temporal-source guard; exposed for tests, distinct from manifest generation. */
    long wallGeneration() { return wallGeneration; }

    /** True while the volatile path is armed with a live in-memory anchor. */
    boolean isArmed() { return armed && anchor != null; }
}
