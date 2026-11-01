package com.scenevibe.timebasecaptureg2;

import java.util.Arrays;

/**
 * A single volatile generation-bound ROI, never persisted or reusable after expiry.
 * Array ownership transfers into this lease; erase clears pixel values and references.
 */
public final class RoiLease {
    private int[] pixels;
    public int width,height;
    public long generation,expiresAt;

    /** Begin with no image or generation. */
    public RoiLease() { }

    /** Replace only through a new explicit attempt; retain at most thirty elapsed seconds. */
    public synchronized void put(long gen,int[] data,int w,int h,long now) {
        clear();
        if(data==null||w<1||h<1||(long)w*h!=data.length)throw new IllegalArgumentException("ROI_LAYOUT_INVALID");
        generation=gen;pixels=data;width=w;height=h;expiresAt=now+CapturePolicy.ROI_MS;
    }

    /** Return current pixels only to the same generation before its strict expiry. */
    public synchronized int[] get(long gen,long now) {
        if(now>=expiresAt){clear();return null;}return gen==generation?pixels:null;
    }

    /** Zero the owned array and drop all references; safe to repeat. */
    public synchronized void clear() {
        if(pixels!=null)Arrays.fill(pixels,0);pixels=null;width=0;height=0;generation=0;expiresAt=0;
    }
}
