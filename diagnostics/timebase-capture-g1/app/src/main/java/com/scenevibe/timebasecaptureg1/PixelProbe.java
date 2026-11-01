package com.scenevibe.timebasecaptureg1;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Bounded RGBA ROI copy and deterministic owned-pattern check, never OCR. Full images
 * stay in ImageReader ownership. Samples and the returned crop are not persisted.
 */
public final class PixelProbe {
    public static final int[] COLORS = {0xffed2939, 0xff2196f3, 0xffffd600, 0xff10ba80};

    /** No instances: all operations consume explicit caller-owned buffers. */
    private PixelProbe() { }

    /** Read RGBA with row padding; reject incomplete, unsupported or overflowing layouts. */
    public static int pixel(ByteBuffer b, int x, int y, int w, int h, int pixelStride, int rowStride) {
        if (b == null || x < 0 || y < 0 || x >= w || y >= h || w < 1 || h < 1
            || w > 4096 || h > 4096 || pixelStride != 4 || rowStride < (long)w*4)
            throw new IllegalArgumentException("IMAGE_INVALID");
        long p = (long)y*rowStride + (long)x*pixelStride;
        if (p + 3 >= b.limit()) throw new IllegalArgumentException("BUFFER_SHORT");
        int i = (int)p;
        return 0xff000000 | ((b.get(i)&255)<<16) | ((b.get(i+1)&255)<<8) | (b.get(i+2)&255);
    }

    /** Copy only a prevalidated rectangle, with a 16-million-pixel upper bound. */
    public static int[] crop(ByteBuffer b, int w, int h, int ps, int rs, int[] r) {
        // Validate the full layout before allocation, not after allocating an untrusted rectangle.
        pixel(b,w-1,h-1,w,h,ps,rs);
        if (r == null || r.length != 4 || r[0] < 0 || r[1] < 0 || r[2] > w || r[3] > h
            || r[2] <= r[0] || r[3] <= r[1]) throw new IllegalArgumentException("ROI_INVALID");
        int[] result = new int[Math.multiplyExact(r[2]-r[0], r[3]-r[1])];
        try {
            for (int y=r[1], i=0; y<r[3]; y++) for (int x=r[0]; x<r[2]; x++) result[i++]=pixel(b,x,y,w,h,ps,rs);
            return result;
        } catch (RuntimeException e) { Arrays.fill(result,0); throw e; }
    }

    /** Count four known color markers; this is not recognition of the timecode or a third-party UI. */
    public static int matches(int[] crop, int w, int h) {
        if (crop == null || w < 1 || h < 1 || (long)w*h != crop.length) return 0;
        int count=0;
        for (int i=0; i<4; i++) {
            int c=crop[Math.min(h-1,(int)(h*.75))*w+Math.min(w-1,(int)(w*(.2+i*.2)))];
            if (near(c,COLORS[i])) count++;
        }
        return count;
    }

    /** Permit minor color conversion while rejecting absent/black patterns. */
    public static boolean near(int a, int b) {
        return Math.abs(((a>>16)&255)-((b>>16)&255))<=25
            && Math.abs(((a>>8)&255)-((b>>8)&255))<=25 && Math.abs((a&255)-(b&255))<=25;
    }
}
