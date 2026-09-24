package com.scenevibe.tvcompanionpoc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

/**
 * User-started foreground service that owns the Android overlay window.
 * No network, media, accessibility, screen capture or streaming application API is used.
 */
public final class OverlayService extends Service {
    public static final String ACTION_TOP = "com.scenevibe.tvcompanionpoc.SHOW_TOP";
    public static final String ACTION_BOTTOM = "com.scenevibe.tvcompanionpoc.SHOW_BOTTOM";
    private static final String TAG = "SceneVibePoc";
    private static final String CHANNEL_ID = "overlay_poc";
    private static final int NOTIFICATION_ID = 1001;
    private OverlayRenderer renderer;
    private boolean foregroundReady;

    /** Create the notification channel and enter foreground mode promptly. */
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "OverlayService created");
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    "SceneVibe overlay test", NotificationManager.IMPORTANCE_LOW));
            Intent launch = new Intent(this, MainActivity.class);
            PendingIntent openControls = PendingIntent.getActivity(this, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("SceneVibe TV Companion POC")
                    .setContentText("Test overlay is running. Open to stop it.")
                    .setContentIntent(openControls)
                    .setOngoing(true)
                    .build();
            startForeground(NOTIFICATION_ID, notification);
            foregroundReady = true;
            Log.i(TAG, "Foreground notification started");
        } catch (RuntimeException error) {
            Log.e(TAG, "Foreground service initialization failed", error);
            stopSelf();
        }
    }

    /** Show or reposition the window; a system restart restores the last position. */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!foregroundReady) {
            return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission absent; stopping service");
            stopSelf();
            return START_NOT_STICKY;
        }
        String action = intent == null ? null : intent.getAction();
        boolean bottom = ACTION_BOTTOM.equals(action)
                || (action == null && getSharedPreferences("overlay", Context.MODE_PRIVATE)
                        .getBoolean("bottom", false));
        getSharedPreferences("overlay", Context.MODE_PRIVATE).edit()
                .putBoolean("bottom", bottom).apply();
        try {
            if (renderer == null) {
                renderer = new OverlayRenderer(this, this::onPermissionLost);
            }
            renderer.show(bottom);
            Log.i(TAG, "Overlay visible; position=" + (bottom ? "bottom" : "top"));
            return START_STICKY;
        } catch (RuntimeException error) {
            Log.e(TAG, "WindowManager overlay failed", error);
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    /** Stop if Android revokes the user's overlay capability during the test. */
    private void onPermissionLost() {
        Log.w(TAG, "Overlay permission revoked while running");
        stopSelf();
    }

    /** This service accepts no external binding or command channel. */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Remove the window and notification on every normal destruction path. */
    @Override
    public void onDestroy() {
        if (renderer != null) {
            renderer.dismiss();
            renderer = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        Log.i(TAG, "OverlayService destroyed");
        super.onDestroy();
    }
}
