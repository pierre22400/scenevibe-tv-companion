package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.graphics.Bitmap;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Renders the persistent noninteractive overlay and transient rich commentary. */
public final class OverlayRenderer {
    private static final String TAG = "SceneVibePoc";
    private final Context context;
    private final Runnable permissionLost;
    private final WindowManager windows;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout panel;
    private TextView badge;
    private ImageView mediaView;
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
                clearMedia();
                showStatusBadge();
                Log.i(TAG, "Dynamic commentary expired; status badge restored");
            }
        }
    };

    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            if (panel == null) return;
            if (commentaryText == null) {
                showStatusBadge();
            }
            ticks++;
            if (ticks % 30 == 0) {
                Log.d(TAG, "Overlay heartbeat; attached=" + panel.isAttachedToWindow());
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
        if (panel != null) {
            params.gravity = (bottom ? Gravity.BOTTOM : Gravity.TOP) | Gravity.END;
            windows.updateViewLayout(panel, params);
            Log.i(TAG, "Overlay position updated");
            return;
        }

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding(dp(18), dp(12), dp(18), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xAA17130F);
        background.setCornerRadius(dp(14));
        container.setBackground(background);

        ImageView image = new ImageView(context);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setVisibility(View.GONE);
        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(dp(520), dp(292));
        imageParams.bottomMargin = dp(10);
        container.addView(image, imageParams);

        TextView text = new TextView(context);
        text.setTextColor(0xFFFFFFFF);
        text.setTextSize(20);
        text.setGravity(Gravity.CENTER);
        text.setMaxWidth(dp(520));
        container.addView(text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        container.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
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
        layout.alpha = 0.92f;
        layout.setTitle("SceneVibe TV Companion POC");
        windows.addView(container, layout);

        panel = container;
        badge = text;
        mediaView = image;
        params = layout;
        showStatusBadge();
        heartbeat.run();
        Log.i(TAG, "Overlay view attached");
    }

    /**
     * Replace the badge body with commentary for exactly its requested duration.
     * Optional bitmap media is rendered above the text in the same overlay card.
     * The latest commentary owns expiry: any older pending expiry is cancelled.
     */
    public void showCommentary(String text, long durationMs, Bitmap mediaBitmap) {
        handler.post(() -> {
            if (panel == null || badge == null || mediaView == null) return;
            handler.removeCallbacks(commentaryExpiry);
            commentaryText = text;
            badge.setText("SceneVibe\n\n" + text);
            if (mediaBitmap != null) {
                mediaView.setImageBitmap(mediaBitmap);
                mediaView.setVisibility(View.VISIBLE);
            } else {
                clearMedia();
            }
            handler.postDelayed(commentaryExpiry, durationMs);
            Log.i(TAG, "Dynamic commentary displayed; durationMs=" + durationMs
                    + "; media=" + (mediaBitmap != null));
        });
    }

    public void dismiss() {
        handler.removeCallbacks(heartbeat);
        handler.removeCallbacks(commentaryExpiry);
        commentaryText = null;
        if (panel != null) {
            try {
                clearMedia();
                windows.removeViewImmediate(panel);
                Log.i(TAG, "Overlay view removed");
            } catch (IllegalArgumentException error) {
                Log.w(TAG, "Overlay was already detached", error);
            } finally {
                panel = null;
                badge = null;
                mediaView = null;
                params = null;
            }
        }
    }

    private void clearMedia() {
        if (mediaView != null) {
            mediaView.setImageDrawable(null);
            mediaView.setVisibility(View.GONE);
        }
    }

    private void showStatusBadge() {
        if (badge == null) return;
        clearMedia();
        badge.setText("SceneVibe\nTV Companion POC v0.3.0\n"
                + DateFormat.format("HH:mm:ss", System.currentTimeMillis()));
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
