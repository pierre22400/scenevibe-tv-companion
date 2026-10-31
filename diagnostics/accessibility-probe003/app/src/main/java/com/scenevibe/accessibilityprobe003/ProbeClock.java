package com.scenevibe.accessibilityprobe003;

/** Injected clock: uptime and elapsedRealtime have different epochs across device sleep. */
public interface ProbeClock {
    /** Return elapsedRealtime milliseconds for the collection deadline. */
    long elapsed();
    /** Return uptime milliseconds, comparable only with accessibility eventTime. */
    long uptime();
    /** Return wall-clock milliseconds for approximate offline owner correlation. */
    long wall();
}
