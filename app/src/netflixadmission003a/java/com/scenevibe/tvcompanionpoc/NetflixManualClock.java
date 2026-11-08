package com.scenevibe.tvcompanionpoc;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Owner-thread clock for one admitted track activation. It schedules no events itself. */
final class NetflixManualClock {
    private final StudioMultimediaRuntime runtime;
    private final String activation;
    private final String trackId;
    private final long revision;
    private final long horizonMs;
    private final LongSupplier elapsed;
    private final BooleanSupplier admitted;
    private final Thread owner = Thread.currentThread();
    private long anchorMs;
    private long anchoredAtMs;
    private long generation;
    private boolean running;
    private boolean revoked;

    /** Bind the exact activation and a monotonic source before accepting any observations. */
    NetflixManualClock(StudioMultimediaRuntime runtime, String activation, String trackId,
            long revision, long horizonMs, LongSupplier elapsed, BooleanSupplier admitted) {
        if (revision < 1 || horizonMs < 1) throw new IllegalArgumentException("MANUAL_REFERENCE_INVALID");
        this.runtime = runtime;
        this.activation = activation;
        this.trackId = trackId;
        this.revision = revision;
        this.horizonMs = horizonMs;
        this.elapsed = elapsed;
        this.admitted = admitted;
        runtime.reserveManual(activation);
    }

    /** Project editorial time, clamped to the track horizon. */
    long position() {
        checkOwner();
        long delta = running ? Math.max(0, elapsed.getAsLong() - anchoredAtMs) : 0;
        return anchorMs + Math.min(horizonMs - anchorMs, delta);
    }

    /** Report the verified track bound for display and candidate clamping. */
    long horizon() { return horizonMs; }

    /** Read the editable clock state on the serialized owner. */
    boolean running() { checkOwner(); return running; }

    /** Report whether the admitted activation owns a confirmed manual anchor. */
    boolean anchored() {
        checkOwner();
        return current() && runtime.authority() == StudioMultimediaRuntime.Authority.MANUAL;
    }

    /** Confirm the activation is still admitted before presenting a candidate. */
    boolean editable() { checkOwner(); return current(); }

    /** Return the binding generation for callbacks posted by the owner. */
    long generation() { checkOwner(); return generation; }

    /** Record a candidate at validation time and leave playback explicitly paused. */
    void seek(String selectedTrack, long selectedRevision, long expectedGeneration, long candidateMs) {
        checkOwner();
        if (!current() || !trackId.equals(selectedTrack) || revision != selectedRevision
                || generation != expectedGeneration) {
            throw new IllegalStateException("MANUAL_SELECTION_STALE");
        }
        long target = Math.max(0, Math.min(horizonMs, candidateMs));
        runtime.landManual(activation, target, target < position());
        anchorMs = target;
        running = false;
        generation++;
    }

    /** Anchor progression to the current monotonic instant. */
    void resume() {
        checkOwner();
        if (!current() || runtime.authority() != StudioMultimediaRuntime.Authority.MANUAL || running) return;
        anchoredAtMs = elapsed.getAsLong();
        running = true;
        tick(generation);
    }

    /** Freeze the editorial position and remove local effects immediately. */
    void pause() {
        checkOwner();
        if (!running) return;
        anchorMs = position();
        running = false;
        generation++;
        if (runtime.ownsManual(activation)) runtime.suspendComments();
    }

    /** Discard obsolete callbacks and submit a current observation to the existing runtime. */
    void tick(long expectedGeneration) {
        checkOwner();
        if (expectedGeneration != generation || !current() || !running) return;
        long now = position();
        runtime.observeManual(activation, now, true);
        if (now == horizonMs) pause();
    }

    /** Permanently invalidate this clock and stop its activation's effects. */
    void revoke() {
        checkOwner();
        if (revoked) return;
        pause();
        revoked = true;
        generation++;
        runtime.revokeManual(activation);
    }

    /** Recheck the owner-supplied admission independently of temporal authority. */
    private boolean current() {
        if (!revoked && (!runtime.ownsManual(activation) || !admitted.getAsBoolean())) revoke();
        return !revoked;
    }

    /** Prevent concurrent position and scheduler mutations. */
    private void checkOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("MANUAL_WRONG_OWNER");
    }
}
