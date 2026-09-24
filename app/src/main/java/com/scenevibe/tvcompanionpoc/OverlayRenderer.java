package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Renders one small, translucent, noninteractive WindowManager overlay.
 * Future event delivery can call this renderer without changing the service lifecycle.
 */
public final class OverlayRenderer {
    private static final String TAG = "SceneVibePoc";
    private final Context context;
    private final Runnable permissionLost;
    private final WindowManager windows;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView badge;
    private WindowManager.LayoutParams params;
    private int ticks;
    private final Runnable heartbeat = new Runnable() {
        /** Update the clock, record a sparse heartbeat and detect revoked permission. */
        @Override
        public void run() {
            if (badge == null) {
                return;
            }
            badge.setText("SceneVibe\nTV Companion POC\n"
                    + DateFormat.format("HH:mm:ss", System.currentTimeMillis()));
            ticks++;
            if (ticks % 30 == 0) {
                Log.d(TAG, "Overlay heartbeat; attached=" + badge.isAttachedToWindow());
                if (!Settings.canDrawOverlays(context)) {
                    permissionLost.run();
                    return;
                }
            }
            handler.postDelayed(this, 1000);
        }
    };

    /** Obtain the standard window service without depending on a TV manufacturer. */
    public OverlayRenderer(Context context, Runnable permissionLost) {
        this.context = context;
        this.permissionLost = permissionLost;
        windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windows == null) {
            throw new IllegalStateException("WindowManager unavailable");
        }
    }

    /** Attach once and subsequently move the same window between the two positions. */
    public void show(boolean bottom) {
        if (badge != null) {
            params.gravity = (bottom ? Gravity.BOTTOM : Gravity.TOP) | Gravity.END;
            windows.updateViewLayout(badge, params);
            Log.i(TAG, "Overlay position updated");
            return;
        }

        TextView view = new TextView(context);
        view.setTextColor(0xFFFFFFFF);
        view.setTextSize(20);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(18), dp(10), dp(18), dp(10));
        view.setMaxWidth(dp(300));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xAA17130F);
        background.setCornerRadius(dp(14));
        view.setBackground(background);
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            /** Distinguish an attached window from an app that still has a running service. */
            @Override
            public void onViewAttachedToWindow(View attached) {
                Log.i(TAG, "Overlay view attached");
            }

            /** Surface unexpected detachment in adb logcat. */
            @Override
            public void onViewDetachedFromWindow(View detached) {
                Log.i(TAG, "Overlay view detached");
            }
        });

        WindowManager.LayoutParams layout = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        layout.gravity = (bottom ? Gravity.BOTTOM : Gravity.TOP) | Gravity.END;
        layout.x = dp(28);
        layout.y = dp(28);
        // Android 12+ restricts touch pass-through under untrusted windows.
        // A single small window with alpha <= 0.8 stays within that threshold.
        layout.alpha = 0.75f;
        layout.setTitle("SceneVibe TV Companion POC");
        Log.i(TAG, "Adding TYPE_APPLICATION_OVERLAY with WindowManager");
        windows.addView(view, layout);
        badge = view;
        params = layout;
        heartbeat.run();
    }

    /** Cancel updates and remove the actual window when the service stops. */
    public void dismiss() {
        handler.removeCallbacks(heartbeat);
        if (badge != null) {
            try {
                windows.removeViewImmediate(badge);
                Log.i(TAG, "Overlay view removed");
            } catch (IllegalArgumentException error) {
                Log.w(TAG, "Overlay was already detached", error);
            } finally {
                badge = null;
                params = null;
            }
        }
    }

    /** Convert density independent sizes to display pixels. */
    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}

