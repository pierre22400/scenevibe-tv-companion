package com.scenevibe.tvcompanionpoc;

import android.os.Handler;
import android.os.Looper;

/**
 * The genuinely Android-only bounded-wait glue behind the injectable {@link WallWaitScheduler}
 * seam (section 3). It posts AT MOST ONE wait at a time with a single {@link Handler} bound to the
 * owner main {@link Looper}, using {@link Handler#postDelayed(Runnable, long)} with a RELATIVE
 * duration the driver already clamped to {@code min(nextBoundary - now, 1000 ms)}.
 *
 * <p>It deliberately uses {@code postDelayed} (relative, uptime-based delay) and NEVER
 * {@code postAtTime}, so an {@code elapsedRealtime} value is never passed as an uptime timestamp.
 * The wait accepts its delay while the device sleeps (section 3); no AlarmManager-exact, wake lock,
 * process wake or Direct Boot is introduced. {@link #schedule} removes any previously posted
 * runnable first, so there is always at most one outstanding wait; {@link #cancel} removes it so a
 * late-delivered runnable from a replaced ticket is additionally neutralized by the driver's
 * ticket/generation guards.</p>
 */
final class AndroidWallWaitScheduler implements WallWaitScheduler {

    private final Handler handler;
    private Runnable pending;

    /** Bind to the owner main thread so the single wait runs on the one serializing owner. */
    AndroidWallWaitScheduler() {
        this(new Handler(Looper.getMainLooper()));
    }

    /** Visible for owner composition/testing of the glue with an explicit main-thread handler. */
    AndroidWallWaitScheduler(Handler handler) {
        if (handler == null) throw new IllegalArgumentException("Missing WALL wait handler");
        this.handler = handler;
    }

    /** Replace any pending wait and post a single relative-delay runnable on the owner thread. */
    @Override public boolean schedule(long delayMs, Runnable onFire) {
        if (onFire == null || delayMs < 0L) return false;
        cancel();
        pending = onFire;
        // postDelayed is RELATIVE (never postAtTime): no elapsedRealtime is used as an uptime stamp.
        return handler.postDelayed(onFire, delayMs);
    }

    /** Remove the single outstanding wait, if any; idempotent. */
    @Override public void cancel() {
        if (pending != null) {
            handler.removeCallbacks(pending);
            pending = null;
        }
    }
}
