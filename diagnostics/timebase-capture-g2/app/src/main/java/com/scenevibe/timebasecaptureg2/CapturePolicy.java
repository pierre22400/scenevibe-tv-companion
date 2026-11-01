package com.scenevibe.timebasecaptureg2;

/**
 * Synchronized generation policy for an explicit, one-consent, one-frame attempt.
 * Every time argument is elapsedRealtime milliseconds. No Android token is stored here.
 */
public final class CapturePolicy {
    public static final long MAX_CAPTURE_MS=10000, ROI_MS=30000, MAX_DELAY_MS=60000;
    public static final long INITIALIZATION_MS=250;
    public static final int MAX_DIMENSION=4096;
    public enum Mode { PUBLIC, MIXED, SECURE }
    public enum State { DISARMED, CONSENT_PENDING, WAITING, CAPTURING, CLOSED }
    public State state=State.DISARMED;
    public long generation, reservedAt, waitAt, triggeredAt, delay;
    public boolean consumed, preparationLeft, usefulFrame;

    /** Create a disarmed nonpersistent policy. */
    public CapturePolicy() { }

    /** Reserve before opening consent, rejecting double arm and invalid bounded delay. */
    public synchronized long arm(long now,long ms) {
        if(state!=State.DISARMED&&state!=State.CLOSED)throw new IllegalStateException("ATTEMPT_ACTIVE");
        if(now<0||ms<500||ms>MAX_DELAY_MS)throw new IllegalArgumentException("DELAY_INVALID");
        generation++;reservedAt=now;delay=ms;consumed=false;preparationLeft=false;usefulFrame=false;
        state=State.CONSENT_PENDING;return generation;
    }

    /** Consume a current consent once, with a separate 120-second dialog deadline. */
    public synchronized boolean consent(long gen,long now) {
        if(gen!=generation||state!=State.CONSENT_PENDING||consumed||now<reservedAt||now-reservedAt>120000)return false;
        consumed=true;waitAt=now;state=State.WAITING;return true;
    }

    /** Record departure from our preparation UI; never inspect a different package. */
    public synchronized void left() { if(state==State.WAITING)preparationLeft=true; }

    /** Open the capture window only after the explicit delay and departure, within arm validity. */
    public synchronized boolean begin(long gen,long now,boolean preparationVisible) {
        if(gen!=generation||state!=State.WAITING||preparationVisible||!preparationLeft||now<waitAt+delay||now>waitAt+delay+2000)return false;
        triggeredAt=now;state=State.CAPTURING;return true;
    }

    /** Reject old generation, initialization callbacks, duplicate useful frames and deadline overrun. */
    public synchronized boolean accepts(long gen,long now) {
        return bufferRejection(gen,now)==null;
    }

    /** Return a closed technical cause, or null when current generation/window permits one frame. */
    public synchronized String bufferRejection(long gen,long now) {
        if(gen!=generation)return "STALE_GENERATION";
        if(state!=State.CAPTURING)return "CAPTURE_INACTIVE";
        if(usefulFrame)return "USEFUL_FRAME_ALREADY_ACQUIRED";
        if(now<triggeredAt)return "RECEIPT_CLOCK_INVALID";
        if(now-triggeredAt<INITIALIZATION_MS)return "INITIALIZATION_REJECTED";
        if(now-triggeredAt>=MAX_CAPTURE_MS)return "ACQUISITION_EXPIRED";
        return null;
    }

    /** Compare only two observed timestamps from the same source; the first value, including zero, has no predecessor. */
    public static String timestampRejection(boolean seen,long previous,long current) {
        if(!seen)return null;
        if(current==previous)return "DUPLICATE_TIMESTAMP";
        return current<previous?"NON_MONOTONIC_TIMESTAMP":null;
    }

    /** Commit the one logical frame atomically before publishing a ROI. */
    public synchronized boolean frame(long gen,long now) {
        if(!accepts(gen,now))return false;usefulFrame=true;return true;
    }

    /** Close terminally; no callback or old consent can revive this attempt. */
    public synchronized void close() { state=State.CLOSED; }

    /** Report only whether an attempt is live, without leaking its token or pixels. */
    public synchronized boolean active() { return state!=State.DISARMED&&state!=State.CLOSED; }

    /** Validate normalized ROI and convert to bounded pixel edges without subpixel rounding artifacts. */
    public static int[] roi(double x,double y,double w,double h,int width,int height) {
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(w)||!Double.isFinite(h)||x<0||y<0||w<=0||h<=0
            ||x+w>1||y+h>1||width<1||height<1||width>4096||height>4096)throw new IllegalArgumentException("ROI_INVALID");
        return new int[]{(int)Math.floor(x*width+1e-9),(int)Math.floor(y*height+1e-9),(int)Math.ceil((x+w)*width-1e-9),(int)Math.ceil((y+h)*height-1e-9)};
    }
}
