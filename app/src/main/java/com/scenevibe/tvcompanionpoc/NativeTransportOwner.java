package com.scenevibe.tvcompanionpoc;

import java.util.function.LongSupplier;

/**
 * Unique, in-memory NativeTransportLease owner subordinate to SceneRuntimeController.
 *
 * <p>Adapted from Spike SafeResumeGuard/SessionCatalog: PAUSE-send is not PAUSED confirmation;
 * normal EOF is not permission to PLAY. This version additionally binds raw position, provider
 * update time, command confirmations and generation. It owns no renderer, queue or scheduler.</p>
 */
final class NativeTransportOwner {
    /** Bounded outcomes contain no reusable token, content or credential. */
    enum Result { WAITING, CONFIRMED, REFUSED }
    /** Explain refusal without silently changing mode or choosing a different controller. */
    enum Reason {
        NONE, BUSY, GENERATION, TARGET, NOT_PLAYING, NOT_VOD, STALE_POSITION,
        ACTION_UNAVAILABLE, PAUSE_DISPATCH_FAILED, PAUSE_NOT_CONFIRMED,
        SESSION_CHANGED, POSITION_CHANGED, STATE_CHANGED, PLAY_DISPATCH_FAILED,
        PLAY_NOT_CONFIRMED, NOT_NORMAL_EOF
    }
    /** Structured diagnostic seam; Android decides how to pseudonymize observations. */
    interface Diagnostics {
        /** Record a command/observation/decision with its ephemeral attempt generation. */
        void record(String event, long generation, NativeSessionSnapshot snapshot, Reason reason);
        /** Preserve the exact post-query evaluation time and result; old observers remain valid. */
        default void evaluated(String event, long generation, NativeSessionSnapshot snapshot,
                Reason reason, long evaluatedAtMs, NativeQualificationPolicy.Evidence evidence) {
            record(event, generation, snapshot, reason);
        }
    }
    /** The only lease; it is never persisted, restored or transferred between presentations. */
    private static final class Lease {
        final long generation;
        final NativeSessionSnapshot initial;
        final long pauseSentAt;
        NativeSessionSnapshot paused;
        long playSentAt;
        boolean playSent;

        /** Bind the native target and the time of our own successful pause dispatch. */
        Lease(long generation, NativeSessionSnapshot initial, long pauseSentAt) {
            this.generation = generation;
            this.initial = initial;
            this.pauseSentAt = pauseSentAt;
        }
    }

    private final NativeSessionPort port;
    private final NativeQualificationPolicy policy;
    private final Diagnostics diagnostics;
    private final LongSupplier observationClock;
    private Lease lease;
    private Reason reason = Reason.NONE;

    /** Bind one coherent session port and the provisional local qualification limits. */
    NativeTransportOwner(NativeSessionPort port, NativeQualificationPolicy policy,
            Diagnostics diagnostics) {
        this(port, policy, diagnostics, null);
    }

    /** Read execution time after each query, so a real query is not mistaken for future data. */
    NativeTransportOwner(NativeSessionPort port, NativeQualificationPolicy policy,
            Diagnostics diagnostics, LongSupplier observationClock) {
        if (port == null || policy == null || diagnostics == null) {
            throw new IllegalArgumentException("Transport seams required");
        }
        this.port = port;
        this.policy = policy;
        this.diagnostics = diagnostics;
        this.observationClock = observationClock;
    }

    /** Admit a single PLAYING VOD target and send PAUSE only after the regie's local preflight. */
    boolean requestPause(long generation, long nowMs, boolean vodConfirmed) {
        if (lease != null) return refuse(Reason.BUSY);
        NativeSessionSnapshot target = port.select(vodConfirmed);
        nowMs = afterObservation(nowMs);
        diagnostics.evaluated("NATIVE_ADMISSION_CHECK", generation, target, Reason.NONE,
                nowMs, policy.evidence(target, nowMs));
        if (target == null || !NativeQualificationPolicy.PRIME_PACKAGE.equals(target.packageName)) {
            return refuse(Reason.TARGET);
        }
        if (!target.vodConfirmed) return refuse(Reason.NOT_VOD);
        if (!policy.fresh(target, nowMs)) return refuse(Reason.STALE_POSITION);
        if (target.state != NativeSessionSnapshot.PLAYING || target.speed != 1f) {
            return refuse(Reason.NOT_PLAYING);
        }
        if (!target.canPause || !target.canPlay) return refuse(Reason.ACTION_UNAVAILABLE);
        if (target.mediaId.isEmpty() && target.title.length() < 8) return refuse(Reason.TARGET);
        if (target.durationMs > 0 && target.estimatedPosition(nowMs) >= target.durationMs) {
            return refuse(Reason.STALE_POSITION);
        }
        diagnostics.record("TARGET_VALIDATED", generation, target, Reason.NONE);
        lease = new Lease(generation, target, nowMs);
        if (!port.pause(target, policy, nowMs)) {
            lease = null;
            return refuse(Reason.PAUSE_DISPATCH_FAILED);
        }
        reason = Reason.NONE;
        diagnostics.record("PAUSE_SENT", generation, target, Reason.NONE);
        return true;
    }

    /** Confirm our pause, with a coherent position and a provider update after dispatch. */
    Result confirmPaused(long generation, long nowMs) {
        if (!owns(generation)) return Result.REFUSED;
        NativeSessionSnapshot current = port.observe(lease.initial);
        nowMs = afterObservation(nowMs);
        if (!sameFresh(current, nowMs)) return Result.REFUSED;
        if (current.state == NativeSessionSnapshot.PLAYING && !lease.playSent) {
            if (nowMs - lease.pauseSentAt < policy.pauseTimeoutMs) return Result.WAITING;
            refuse(Reason.PAUSE_NOT_CONFIRMED);
            return Result.REFUSED;
        }
        if (current.state != NativeSessionSnapshot.PAUSED || current.speed != 0f
                || current.updatedAtMs < lease.pauseSentAt) {
            refuse(Reason.STATE_CHANGED);
            return Result.REFUSED;
        }
        long start = lease.initial.estimatedPosition(lease.pauseSentAt);
        long advance = nowMs - lease.pauseSentAt + 2 * policy.pollMs;
        if (current.positionMs < start - policy.positionToleranceMs
                || current.positionMs - start > advance + policy.positionToleranceMs) {
            refuse(Reason.POSITION_CHANGED);
            return Result.REFUSED;
        }
        if (nowMs - lease.pauseSentAt > policy.pauseTimeoutMs) {
            refuse(Reason.PAUSE_NOT_CONFIRMED);
            return Result.REFUSED;
        }
        lease.paused = current;
        diagnostics.record("PAUSED_CONFIRMED", generation, current, Reason.NONE);
        return Result.CONFIRMED;
    }

    /** Reject any observed seek, state update, competing player or loss during the interlude. */
    boolean pauseStillOwned(long generation, long nowMs) {
        if (!owns(generation) || lease.paused == null || lease.playSent) {
            return refuse(Reason.PAUSE_NOT_CONFIRMED);
        }
        NativeSessionSnapshot current = port.observe(lease.initial);
        nowMs = afterObservation(nowMs);
        if (!sameFresh(current, nowMs)) return false;
        if (current.state != NativeSessionSnapshot.PAUSED || current.speed != 0f) {
            return refuse(Reason.STATE_CHANGED);
        }
        if (Math.abs(current.positionMs - lease.paused.positionMs) > policy.positionToleranceMs
                || current.updatedAtMs != lease.paused.updatedAtMs) {
            return refuse(Reason.POSITION_CHANGED);
        }
        return true;
    }

    /** Allow one PLAY only on normal EOF and a newly revalidated owned PAUSED anchor. */
    boolean requestPlay(long generation, long nowMs, boolean normalEof) {
        if (!normalEof) return refuse(Reason.NOT_NORMAL_EOF);
        if (!pauseStillOwned(generation, nowMs)) return false;
        nowMs = afterObservation(nowMs);
        diagnostics.record("TARGET_REVALIDATED", generation, lease.paused, Reason.NONE);
        if (!lease.paused.canPlay || !port.play(lease.paused, policy, nowMs)) {
            return refuse(Reason.PLAY_DISPATCH_FAILED);
        }
        lease.playSent = true;
        lease.playSentAt = nowMs;
        diagnostics.record("PLAY_SENT", generation, lease.paused, Reason.NONE);
        return true;
    }

    /** Observe PLAYING after our dispatch; a sent command alone never becomes success. */
    Result confirmPlaying(long generation, long nowMs) {
        if (!owns(generation) || !lease.playSent) return Result.REFUSED;
        NativeSessionSnapshot current = port.observe(lease.initial);
        nowMs = afterObservation(nowMs);
        if (!sameFresh(current, nowMs)) return Result.REFUSED;
        if (current.state == NativeSessionSnapshot.PAUSED) {
            if (nowMs - lease.playSentAt < policy.playingTimeoutMs) return Result.WAITING;
            refuse(Reason.PLAY_NOT_CONFIRMED);
            return Result.REFUSED;
        }
        if (current.state != NativeSessionSnapshot.PLAYING || current.speed != 1f
                || current.updatedAtMs < lease.playSentAt) {
            refuse(Reason.STATE_CHANGED);
            return Result.REFUSED;
        }
        long position = current.estimatedPosition(nowMs);
        long advance = nowMs - lease.playSentAt + 2 * policy.pollMs;
        if (position < lease.paused.positionMs - policy.positionToleranceMs
                || position - lease.paused.positionMs > advance + policy.positionToleranceMs) {
            refuse(Reason.POSITION_CHANGED);
            return Result.REFUSED;
        }
        diagnostics.record("PLAYING_CONFIRMED", generation, current, Reason.NONE);
        return Result.CONFIRMED;
    }

    /** Invalidate only this attempt's lease; no PLAY is ever sent during release. */
    void release(long generation) {
        if (lease != null && lease.generation == generation) {
            diagnostics.record("LEASE_RELEASED", generation, null, reason);
            lease = null;
        }
    }

    /** True only while a single ephemeral target is owned. */
    boolean hasLease() {
        return lease != null;
    }

    /** Return the bounded latest refusal to the regie. */
    Reason reason() {
        return reason;
    }

    /** Clear only the retired decision before a new admitted attempt; never replace a lease. */
    void beginAttempt() {
        if (lease == null) reason = Reason.NONE;
    }

    /** Fail closed for callbacks from another presentation generation. */
    private boolean owns(long generation) {
        return lease != null && lease.generation == generation || refuse(Reason.GENERATION);
    }

    /** Validate target identity and position age before every observation-dependent action. */
    private boolean sameFresh(NativeSessionSnapshot current, long nowMs) {
        diagnostics.evaluated("NATIVE_OBSERVATION", lease.generation, current, Reason.NONE,
                nowMs, policy.evidence(current, nowMs));
        if (current == null || !lease.initial.sameTarget(current)) return refuse(Reason.SESSION_CHANGED);
        if (!current.vodConfirmed) return refuse(Reason.NOT_VOD);
        if (!policy.fresh(current, nowMs)) return refuse(Reason.STALE_POSITION);
        return true;
    }

    /** Record a bounded denial while retaining the lease for explicit cleanup by the regie. */
    private boolean refuse(Reason denied) {
        reason = denied;
        return false;
    }

    /** Freshness uses a post-query time from the same monotone source, never an editorial clock. */
    private long afterObservation(long supplied) {
        return observationClock == null ? supplied : observationClock.getAsLong();
    }
}
