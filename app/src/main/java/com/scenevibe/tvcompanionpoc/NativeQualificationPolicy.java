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
        return sample != null && sample.token != null && sample.durationMs > 0
                && sample.positionMs >= 0 && sample.positionMs <= sample.durationMs
                && sample.updatedAtMs > 0 && sample.updatedAtMs <= sample.observedAtMs
                && sample.observedAtMs <= nowMs && nowMs - sample.observedAtMs <= sampleMaxAgeMs
                && nowMs - sample.updatedAtMs <= providerMaxAgeMs;
    }
}
