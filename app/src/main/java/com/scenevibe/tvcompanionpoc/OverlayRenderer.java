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

/** Renders the persistent noninteractive overlay and transient commentary. */
public final class OverlayRenderer {
    private static final String TAG = "SceneVibePoc";
    private final Context context;
    private final Runnable permissionLost;
    private final WindowManager windows;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView badge;
    private WindowManager.LayoutParams params;
    private int ticks;
    private String commentaryText;

    /**
     * Exact expiry for the currently displayed commentary.
     * A newer commentary removes this callback before scheduling its own expiry.
     */
    private final Runnable commentaryExpiry = new Runnable() {
        @Override
        public void run() {
            commentaryText = null;
            if (badge != null) {
                showStatusBadge();
                Log.i(TAG, "Dynamic commentary expired; status badge restored");
            }
        }
    };

    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            if (badge == null) return;
            if (commentaryText == null) {
                showStatusBadge();
            }
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

    public OverlayRenderer(Context context, Runnable permissionLost) {
        this.context = context;
        this.permissionLost = permissionLost;
        windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windows == null) throw new IllegalStateException("WindowManager unavailable");
    }

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
        view.setMaxWidth(dp(560));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xAA17130F);
        background.setCornerRadius(dp(14));
        view.setBackground(background);
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View attached) {
                Log.i(TAG, "Overlay view attached");
            }
            @Override public void onViewDetachedFromWindow(View detached) {
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
        layout.alpha = 0.75f;
        layout.setTitle("SceneVibe TV Companion POC");
        windows.addView(view, layout);
        badge = view;
        params = layout;
        heartbeat.run();
        Log.i(TAG, "Overlay view attached");
    }

    /**
     * Replace the badge body with a commentary for exactly its requested duration.
     * The latest commentary owns expiry: any older pending expiry is cancelled.
     */
    public void showCommentary(String text, long durationMs) {
        handler.post(() -> {
            if (badge == null) return;
            handler.removeCallbacks(commentaryExpiry);
            commentaryText = text;
            badge.setText("SceneVibe\n\n" + text);
            handler.postDelayed(commentaryExpiry, durationMs);
            Log.i(TAG, "Dynamic commentary displayed; durationMs=" + durationMs);
        });
    }

    public void dismiss() {
        handler.removeCallbacks(heartbeat);
        handler.removeCallbacks(commentaryExpiry);
        commentaryText = null;
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

    private void showStatusBadge() {
        badge.setText("SceneVibe\nTV Companion POC v0.2.1\n"
                + DateFormat.format("HH:mm:ss", System.currentTimeMillis()));
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
