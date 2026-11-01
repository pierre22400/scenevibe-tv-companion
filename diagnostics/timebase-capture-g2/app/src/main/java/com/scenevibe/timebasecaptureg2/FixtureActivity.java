package com.scenevibe.timebasecaptureg2;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.WindowManager;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.widget.FrameLayout;

/**
 * Exclusively owned synthetic display. Mixed mode protects a SurfaceView behind a
 * separate ordinary clock view; secure mode protects the entire Activity window.
 * This owned second Activity exercises voluntary departure from preparation. It never
 * starts projection or launches another Activity. G2 owns its separate bounded lifecycle.
 */
public final class FixtureActivity extends Activity {
    static volatile boolean visible;
    static volatile long drawnUptime;
    static volatile CapturePolicy.Mode shownMode;
    static volatile boolean surfaceDrawn;
    static volatile boolean animationEnabled;
    static volatile int drawCount;

    /** Construct the Android-managed fixture without persistent state. */
    public FixtureActivity() { }

    /** Read only a closed mode enum and draw an owned synthetic pattern. */
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        CapturePolicy.Mode mode;
        try { mode=CapturePolicy.Mode.valueOf(getIntent().getStringExtra("mode")); }
        catch (RuntimeException e) { finish(); return; }
        shownMode=mode; drawnUptime=0; surfaceDrawn=false;
        animationEnabled=!getIntent().getBooleanExtra("static",false);drawCount=0;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setDecorFitsSystemWindows(false);
        if (mode==CapturePolicy.Mode.SECURE) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        FrameLayout frame=new FrameLayout(this);
        if (mode==CapturePolicy.Mode.MIXED) {
            SurfaceView protectedSurface=new SurfaceView(this);
            protectedSurface.setSecure(true);
            protectedSurface.getHolder().addCallback(new SurfaceHolder.Callback() {
                /** Draw only manufactured magenta pixels on the owned secure surface. */
                @Override public void surfaceCreated(SurfaceHolder holder) { paintSurface(holder); }
                /** Repaint after a synthetic geometry change. */
                @Override public void surfaceChanged(SurfaceHolder holder,int format,int w,int h) { paintSurface(holder); }
                /** The framework owns surface teardown; no projection is restarted here. */
                @Override public void surfaceDestroyed(SurfaceHolder holder) { }
            });
            android.graphics.Rect bounds=getWindowManager().getMaximumWindowMetrics().getBounds();
            FrameLayout.LayoutParams geometry=new FrameLayout.LayoutParams((int)(bounds.width()*.8),(int)(bounds.height()*.25));
            geometry.leftMargin=(int)(bounds.width()*.1); geometry.topMargin=(int)(bounds.height()*.55);
            frame.addView(protectedSurface,geometry);
        }
        frame.addView(new PatternView(this),new FrameLayout.LayoutParams(-1,-1));
        setContentView(frame);
    }

    /** Fill an owned secure surface; null canvas is an explicit unavailable drawing. */
    private static void paintSurface(SurfaceHolder holder) {
        Canvas c=null;
        try { c=holder.lockCanvas(); if(c!=null)c.drawColor(Color.MAGENTA); }
        catch (RuntimeException e) { drawnUptime=0; }
        finally { if(c!=null) { holder.unlockCanvasAndPost(c); surfaceDrawn=true; } }
    }

    /** Mark only this resumed fixture eligible for a G2 acquisition. */
    @Override public void onResume() { super.onResume(); visible=true; }

    /** Report only our own fixture lifecycle; no third-party foreground is inspected. */
    @Override public void onPause() { visible=false; super.onPause(); }

    /** Owned pattern painter, using full-display fractions and no external media or text. */
    private static final class PatternView extends View {
        private final Paint paint=new Paint();
        /** Attach a synthetic view; it reads no third-party state. */
        PatternView(Activity activity) { super(activity); setWillNotDraw(false); }
        /** Render a fixed clock and four deterministic ROI markers, not an OCR training sample. */
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            drawCount++;
            paint.setColor(Color.WHITE); canvas.drawRect(0,0,getWidth(),getHeight()*.5f,paint);
            if(shownMode!=CapturePolicy.Mode.MIXED) {
                paint.setColor(Color.MAGENTA);
                canvas.drawRect(getWidth()*.1f,getHeight()*.55f,getWidth()*.9f,getHeight()*.8f,paint);
            }
            float left=getWidth()*.05f, top=getHeight()*.1f, w=getWidth()*.9f, h=getHeight()*.3f;
            paint.setColor(Color.BLACK); paint.setTextSize(h*.36f); paint.setAntiAlias(true);
            canvas.drawText("01:36 — OWN SYNTHETIC CONTROL",left,top+h*.4f,paint);
            paint.setAntiAlias(false);
            for(int i=0;i<4;i++) {
                paint.setColor(PixelProbe.COLORS[i]); float cx=left+w*(.2f+i*.2f);
                canvas.drawRect(cx-w*.04f,top+h*.65f,cx+w*.04f,top+h*.85f,paint);
            }
            if(drawnUptime==0)drawnUptime=SystemClock.uptimeMillis();
            // Owned animation supplies fresh frames during initialization, never acquisition polling.
            if(animationEnabled&&SystemClock.uptimeMillis()-drawnUptime<15000)postInvalidateDelayed(100);
        }
    }
}
