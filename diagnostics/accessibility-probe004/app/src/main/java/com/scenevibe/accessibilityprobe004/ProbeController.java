package com.scenevibe.accessibilityprobe004;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Volatile campaign authority for this diagnostic alone. It knows no player, Companion,
 * cache or editorial event. Clock/file dependencies permit deterministic software tests.
 */
public final class ProbeController {
    private final ProbeClock clock;
    private final JournalStore store;
    private final Map<Integer, Long> explored = new HashMap<>();
    private boolean connected;
    private boolean active;
    private ProbeProfile profile = ProbeProfile.OWN_CONTROL;
    private String run;
    private long start;
    private long sequence;
    private long generation;
    private boolean observationInFlight;
    private long rejected;
    private long coalesced;
    private SafeRow.Reason lastReason;

    /** Create a disconnected controller with no persisted preferences or campaign restoration. */
    public ProbeController(ProbeClock clock, JournalStore store) { this.clock = clock; this.store = store; }

    /** Treat every service connection, including reconnection, as a disarmed boundary. */
    public synchronized void connect() { stop(SafeRow.Reason.RECONNECTED); connected = true; }

    /** Stop a disconnected/interrupted service; no automatic rearming is possible. */
    public synchronized void disconnect(SafeRow.Reason reason) { stop(reason); connected = false; }

    /** Arm one immutable profile only after a live connection and explicit owner action. */
    public synchronized boolean arm(ProbeProfile selected) {
        if (!connected || active || selected == null) return false;
        generation++;
        observationInFlight = false;
        profile = selected;
        start = clock.elapsed();
        sequence = rejected = coalesced = 0;
        explored.clear();
        run = UUID.randomUUID().toString();
        try { store.prune(clock.wall()); store.open(run); }
        catch (IOException failure) { lastReason = SafeRow.Reason.IO_FAILURE; return false; }
        active = true;
        lastReason = null;
        return record(new SafeRow(SafeRow.Kind.ARMED).number("durationLimitMs", ProbeLimits.DURATION_MS)
            .number("byteLimit", ProbeLimits.BYTES).number("nodeLimit", ProbeLimits.NODES)
            .number("depthLimit", ProbeLimits.DEPTH).number("exploreIntervalMs", ProbeLimits.EXPLORE_INTERVAL_MS));
    }

    /** Expire collection independently of incoming events, using elapsedRealtime only. */
    public synchronized boolean checkDeadline() {
        if (active && clock.elapsed() < start) stop(SafeRow.Reason.CLOCK_INVALID);
        if (active && clock.elapsed() - start >= ProbeLimits.DURATION_MS) stop(SafeRow.Reason.TIME_LIMIT);
        return active;
    }

    /** Filter packages before payload/source access; count rejected events without their contents. */
    public synchronized boolean accepts(CharSequence pkg) {
        if (!checkDeadline()) return false;
        if (!profile.accepts(pkg)) { rejected++; return false; }
        return true;
    }

    /** Record one safe row; a byte ceiling or I/O failure stops instead of silently losing data. */
    public synchronized boolean record(SafeRow row) {
        if (!checkDeadline()) return false;
        try {
            if (!store.append(row.json(run, sequence + 1, profile, clock), false)) {
                stop(SafeRow.Reason.BYTE_LIMIT); return false;
            }
            sequence++;
            return true;
        } catch (IOException failure) { stop(SafeRow.Reason.IO_FAILURE); return false; }
    }

    /** Return event age validity using uptime, never elapsedRealtime or a wall-clock difference. */
    public SafeRow.Quality eventQuality(long eventUptime) {
        if (eventUptime < 0 || eventUptime > clock.uptime()) return SafeRow.Quality.FUTURE_EVENT;
        return clock.uptime() - eventUptime > ProbeLimits.STALE_EVENT_MS
            ? SafeRow.Quality.STALE_EVENT : SafeRow.Quality.OK;
    }

    /** Admit one bounded exploration per window per interval, with a bounded window map. */
    public synchronized boolean allowExplore(int windowId) {
        if (!checkDeadline()) return false;
        Long previous = explored.get(windowId);
        if ((previous != null && clock.elapsed() - previous < ProbeLimits.EXPLORE_INTERVAL_MS)
            || (previous == null && explored.size() >= ProbeLimits.WINDOWS)) { coalesced++; return false; }
        explored.put(windowId, clock.elapsed());
        return true;
    }

    /** Stop once, reserve a terminal integrity row and close; process death may still omit it. */
    public synchronized void stop(SafeRow.Reason reason) {
        if (!active) return;
        active = false;
        generation++;
        lastReason = reason;
        SafeRow end = new SafeRow(SafeRow.Kind.SESSION_END).category("reason", reason)
            .number("rejectedPackageCount", rejected).number("coalescedCount", coalesced)
            .number("droppedEventCount", reason == SafeRow.Reason.BYTE_LIMIT || reason == SafeRow.Reason.IO_FAILURE ? 1 : 0)
            .flag("systemDeliveryCompletenessKnown", false).flag("observationInFlightAtStop", observationInFlight);
        try { store.append(end.json(run, ++sequence, profile, clock), true); }
        catch (IOException failure) { lastReason = SafeRow.Reason.IO_FAILURE; }
        finally { try { store.close(); } catch (IOException failure) { lastReason = SafeRow.Reason.IO_FAILURE; } }
        explored.clear();
    }

    /** Return volatile arm state, enforcing expiration even when the UI only requests status. */
    public synchronized boolean active() { return checkDeadline(); }
    /** Return connection state for the voluntary setup UI. */
    public synchronized boolean connected() { return connected; }
    /** Return the current compiled profile, not a value obtained from another app. */
    public synchronized ProbeProfile profile() { return profile; }
    /** Return the elapsed start for one-shot Android deadline scheduling. */
    public synchronized long startedAt() { return start; }
    /** Return accepted rows including the terminal row when available. */
    public synchronized long sequence() { return sequence; }
    /** Return the explicit last stop/failure category or null before any stop. */
    public synchronized SafeRow.Reason lastReason() { return lastReason; }
    /** Return the volatile generation used to reject asynchronous results from older campaigns. */
    public synchronized long generation() { return generation; }

    /** Verify both generation and deadline before each new asynchronous read or write. */
    public synchronized boolean generationActive(long expected) { return generation == expected && checkDeadline(); }

    /** Write only to the same still-active generation; stale results cannot reach a later file. */
    public synchronized boolean recordFor(long expected, SafeRow row) {
        return generationActive(expected) && record(row);
    }

    /** Mark a worker-owned read without allowing an older task to alter a new campaign. */
    public synchronized void observationState(long expected, boolean inFlight) {
        if (generationActive(expected)) observationInFlight = inFlight;
    }

}
