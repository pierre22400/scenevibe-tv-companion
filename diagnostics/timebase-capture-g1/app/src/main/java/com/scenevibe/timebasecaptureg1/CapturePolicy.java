package com.scenevibe.timebasecaptureg1;

/**
 * Pure G1 validation and attempt state. Inputs are explicit, finite scalars; no Android
 * services or content identities are read. A terminal generation can never capture again.
 */
public final class CapturePolicy {
    public static final long MAX_ARM_MS = 15000, MAX_CAPTURE_MS = 10000;
    public static final int MAX_DIMENSION = 4096;
    public enum Mode { PUBLIC, MIXED, SECURE }
    public enum State { DISARMED, ARMED, ACQUIRING, CLOSED }
    public State state = State.DISARMED;
    public long generation, armedAt, startedAt;

    /** Create a disconnected policy; no state survives a new process. */
    public CapturePolicy() { }

    /** Arm a new generation with a monotone time; reject overlap and invalid delay. */
    public long arm(long now, long delay) {
        if (state == State.ARMED || state == State.ACQUIRING || now < 0 || delay < 500 || delay > 5000)
            throw new IllegalArgumentException("ARM_OR_DELAY_INVALID");
        generation++; armedAt = now; state = State.ARMED; return generation;
    }

    /** Begin only the live generation before expiry; times use the same elapsed base. */
    public boolean begin(long token, long now) {
        if (state != State.ARMED || token != generation || now < armedAt || now - armedAt >= MAX_ARM_MS) return false;
        startedAt = now; state = State.ACQUIRING; return true;
    }

    /** Accept a frame only in the current bounded acquisition; stale generations fail closed. */
    public boolean accepts(long token, long now) {
        return state == State.ACQUIRING && token == generation && now >= startedAt && now - startedAt < MAX_CAPTURE_MS;
    }

    /** Terminal and idempotent; callers separately close all Android resources. */
    public void close() { state = State.CLOSED; }

    /** Validate normalized rectangle; return conservative pixel edges, rejecting empty/outside ROIs. */
    public static int[] roi(double x, double y, double width, double height, int w, int h) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width) || !Double.isFinite(height)
            || x < 0 || y < 0 || width <= 0 || height <= 0 || x + width > 1 || y + height > 1
            || w < 1 || h < 1 || w > MAX_DIMENSION || h > MAX_DIMENSION) throw new IllegalArgumentException("ROI_INVALID");
        // Remove only sub-nanopixel floating-point roundoff at an exact integer boundary.
        int[] r = {(int)Math.floor(x*w+1e-9), (int)Math.floor(y*h+1e-9), (int)Math.ceil((x+width)*w-1e-9), (int)Math.ceil((y+height)*h-1e-9)};
        if (r[2] <= r[0] || r[3] <= r[1]) throw new IllegalArgumentException("ROI_EMPTY");
        return r;
    }
}
