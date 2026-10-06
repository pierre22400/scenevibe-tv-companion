package com.scenevibe.tvcompanionpoc.mediaexperiment;

import android.service.notification.NotificationListenerService;
import android.util.Log;

/**
 * This module's OWN user-authorized notification-listener component.
 *
 * <p>It exists solely as the Android access gate required by
 * {@code MediaSessionManager.getActiveSessions(ComponentName)}. It is an isolated
 * equivalent of :app's {@code MediaSessionAccessService} and shares no code with
 * it; it points only at this module's own package identity.</p>
 *
 * <p>This POC deliberately does NOT inspect, store, or forward notifications. It
 * reads no content and holds no credentials.</p>
 */
public final class ExperimentMediaAccessService extends NotificationListenerService {
    private static final String TAG = "SceneVibeInterludePoc";

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "POC notification-listener access connected for MediaSession discovery");
    }

    @Override
    public void onListenerDisconnected() {
        Log.i(TAG, "POC notification-listener access disconnected");
        super.onListenerDisconnected();
    }
}
