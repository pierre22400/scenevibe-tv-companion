package com.scenevibe.tvcompanionpoc;

/**
 * Injectable temporal source seam for the WALL Android driver (section 3). It isolates the two
 * physical Android bases the driver reads so the driver core stays JVM-testable with fake epoch
 * and elapsed sources exactly as the section-18 "Driver" gates require:
 *
 * <ul>
 *   <li>{@link #currentEpochMs()} is the OS civil UTC time ({@code System.currentTimeMillis()} in
 *       production). It is the sole temporal authority; the user or the OS may correct it, and the
 *       driver never adds NTP or a server clock. It can jump forward or backward.</li>
 *   <li>{@link #elapsedRealtimeMs()} is the monotonic since-boot counter
 *       ({@code SystemClock.elapsedRealtime()} in production). It INCLUDES deep sleep, never
 *       decreases, and is used ONLY to bracket a civil sample and to measure drift, never to
 *       decide DUE.</li>
 * </ul>
 *
 * <p>This interface deliberately exposes no uptime base: the driver computes a RELATIVE wait and
 * uses {@code Handler.postDelayed}, so an {@code elapsedRealtime} value is never passed as an
 * uptime timestamp to {@code postAtTime} (section 3). The real Android implementation
 * {@link AndroidWallClockSource} is the only {@code android.*}-touching clock glue; this seam
 * carries no Android import so {@link WallClockDriver} runs on the pure JVM.</p>
 */
interface WallClockSource {
    /** Read the OS civil UTC time in milliseconds; may jump, is validated by the driver. */
    long currentEpochMs();

    /** Read the monotonic since-boot counter in milliseconds; includes deep sleep, never decreases. */
    long elapsedRealtimeMs();
}
