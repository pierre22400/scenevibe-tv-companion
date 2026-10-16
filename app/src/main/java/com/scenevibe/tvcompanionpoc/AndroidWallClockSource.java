package com.scenevibe.tvcompanionpoc;

import android.os.SystemClock;

/**
 * The ONLY {@code android.*}-touching WALL clock glue (section 3). It reads the OS civil UTC time
 * with {@code System.currentTimeMillis()} and the monotonic since-boot counter with
 * {@code SystemClock.elapsedRealtime()} (which includes deep sleep). It adds no NTP, no server
 * clock and no setting command: it is a direct, side-effect-free read of the two platform bases
 * behind the injectable {@link WallClockSource} seam, so the testable {@link WallClockDriver} core
 * stays Android-free. It never exposes uptime, so an elapsed value can never leak into a
 * {@code postAtTime} uptime timestamp.
 */
final class AndroidWallClockSource implements WallClockSource {

    /** Read OS civil UTC time; the sole temporal authority, which the user/OS may correct. */
    @Override public long currentEpochMs() {
        return System.currentTimeMillis();
    }

    /** Read the monotonic since-boot counter in milliseconds; includes deep sleep, never decreases. */
    @Override public long elapsedRealtimeMs() {
        return SystemClock.elapsedRealtime();
    }
}
