package com.scenevibe.tvcompanionpoc;

/**
 * Bounded software profile for E5-NATIVE-035; these limits are NOT Sony measurements.
 *
 * <p>Zero tolerance uses a raw, stationary PAUSED anchor. A one-millisecond seek refuses PLAY.
 * The 20-second provider-age bound covers a 13-second clip watchdog plus two 3-second
 * confirmations and one second of observation slack. It is deliberately finite and must be
 * checked during the later physical mandate; it is not an Android-wide guarantee.</p>
 */
final class NativeQualificationPolicy {
    /** Bounded evidence codes distinguish metadata failures from actual clock/position age. */
    enum Evidence {
        SUFFICIENT, SAMPLE_MISSING, TOKEN_MISSING, DURATION_OR_IDENTITY,
        POSITION_MISSING, POSITION_OUT_OF_RANGE, UPDATE_TIME_INVALID,
        OBSERVATION_TIME_INVALID, SAMPLE_STALE, PROVIDER_STALE
    }
    static final String PRIME_PACKAGE = "com.amazon.amazonvideo.livingroom";
    final long preparationTimeoutMs;
    final long pauseTimeoutMs;
    final long playingTimeoutMs;
    final long clipWatchdogMs;
    final long sampleMaxAgeMs;
    final long providerMaxAgeMs;
    final long positionToleranceMs;
    final long pollMs;

    /** Define a finite profile; no field controls the editorial MEDIA/WALL calendar. */
    NativeQualificationPolicy(long preparationTimeoutMs, long pauseTimeoutMs,
            long playingTimeoutMs, long clipWatchdogMs, long sampleMaxAgeMs,
            long providerMaxAgeMs, long positionToleranceMs, long pollMs) {
        if (preparationTimeoutMs <= 0 || pauseTimeoutMs <= 0 || playingTimeoutMs <= 0
                || clipWatchdogMs <= 0 || sampleMaxAgeMs <= 0 || providerMaxAgeMs <= 0
                || positionToleranceMs < 0 || pollMs <= 0) {
            throw new IllegalArgumentException("Unbounded qualification profile");
        }
        this.preparationTimeoutMs = preparationTimeoutMs;
        this.pauseTimeoutMs = pauseTimeoutMs;
        this.playingTimeoutMs = playingTimeoutMs;
        this.clipWatchdogMs = clipWatchdogMs;
        this.sampleMaxAgeMs = sampleMaxAgeMs;
        this.providerMaxAgeMs = providerMaxAgeMs;
        this.positionToleranceMs = positionToleranceMs;
        this.pollMs = pollMs;
    }

    /** Return the provisional, tested software limits awaiting Sony qualification. */
    static NativeQualificationPolicy local035() {
        return new NativeQualificationPolicy(5000, 3000, 3000, 13000, 1000, 20000, 0, 250);
    }

    /** The first physical profile is Sony Android 12/API31 only, not every Android TV. */
    static boolean supportedTarget(String brand, int sdk) {
        return "sony".equalsIgnoreCase(brand) && sdk == 31;
    }

    /** Refuse missing, future, old or incoherent positions before any transport action. */
    boolean fresh(NativeSessionSnapshot sample, long nowMs) {
        return evidence(sample, nowMs) == Evidence.SUFFICIENT;
    }

    /**
     * Evaluate each existing bound without changing its threshold or clock basis.
     * Unknown duration -1 needs the stronger explicit-ID/VOD evidence; known durations
     * retain their position upper bound. No duration, position or timestamp is invented.
     */
    Evidence evidence(NativeSessionSnapshot sample, long nowMs) {
        if (sample == null) return Evidence.SAMPLE_MISSING;
        if (sample.token == null) return Evidence.TOKEN_MISSING;
        if (!sample.usableDurationEvidence()) return Evidence.DURATION_OR_IDENTITY;
        if (sample.positionMs < 0) return Evidence.POSITION_MISSING;
        if (sample.durationMs > 0 && sample.positionMs > sample.durationMs
                || sample.estimatedPosition(nowMs) == Long.MAX_VALUE) {
            return Evidence.POSITION_OUT_OF_RANGE;
        }
        if (sample.updatedAtMs <= 0 || sample.updatedAtMs > sample.observedAtMs) {
            return Evidence.UPDATE_TIME_INVALID;
        }
        if (sample.observedAtMs > nowMs) return Evidence.OBSERVATION_TIME_INVALID;
        if (nowMs - sample.observedAtMs > sampleMaxAgeMs) return Evidence.SAMPLE_STALE;
        if (nowMs - sample.updatedAtMs > providerMaxAgeMs) return Evidence.PROVIDER_STALE;
        return Evidence.SUFFICIENT;
    }
}
