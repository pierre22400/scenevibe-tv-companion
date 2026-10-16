package com.scenevibe.tvcompanionpoc;

/**
 * Injectable bounded-wait seam for the WALL Android driver (section 3). The driver programs AT
 * MOST ONE wait at a time with a RELATIVE duration; production implements this with a single
 * {@code Handler.postDelayed} on the owner main thread (see {@link AndroidWallWaitScheduler}),
 * and JVM tests implement it with a fake that captures the pending runnable so a fire can be
 * driven deterministically.
 *
 * <p>The contract is intentionally relative-duration only: there is no {@code postAtTime} /
 * absolute-timestamp entry point, so an {@code elapsedRealtime} value can never be mistaken for an
 * uptime timestamp. {@code delayMs} is already clamped by the driver to
 * {@code min(nextBoundary - now, 1000 ms)} and is never negative and never zero-looping.</p>
 */
interface WallWaitScheduler {
    /**
     * Cancel any previously scheduled wait, then post the given runnable to run after
     * {@code delayMs} milliseconds (relative to now) on the owner thread. Posting replaces any
     * earlier pending wait so there is at most one outstanding wait at a time. Returns false when
     * the wait could not be registered (an initial registration failure fails ARM closed,
     * section 13); a true return means the single wait is armed.
     */
    boolean schedule(long delayMs, Runnable onFire);

    /** Cancel any outstanding wait; after this no previously posted runnable has an effect. */
    void cancel();
}
