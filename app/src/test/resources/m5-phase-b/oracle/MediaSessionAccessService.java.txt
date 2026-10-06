package com.scenevibe.tvcompanionpoc;

import android.service.notification.NotificationListenerService;
import android.util.Log;

/**
 * User-authorized notification-listener component used only as the Android
 * access gate required by MediaSessionManager.getActiveSessions().
 *
 * This POC deliberately does not inspect, store or forward notifications.
 */
public final class MediaSessionAccessService extends NotificationListenerService {
    private static final String TAG = "SceneVibeMedia";

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "Notification-listener access connected for passive MediaSession probe");
    }

    @Override
    public void onListenerDisconnected() {
        Log.i(TAG, "Notification-listener access disconnected");
        super.onListenerDisconnected();
    }
}
