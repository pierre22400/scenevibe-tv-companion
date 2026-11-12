package com.scenevibe.tvcompanionpoc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

/**
 * Reprend le récepteur public déjà initialisé après le boot Android. Le service
 * contrôle ensuite la possession et le cache via HTTPS avant tout effet MEDIA.
 */
public final class PublicViewerBootReceiver extends BroadcastReceiver {
    /** Reçoit seulement les événements système et n'ouvre aucune interface. */
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!BuildConfig.PUBLIC_VIEWER_001
                || (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))) return;
        Context app = context.getApplicationContext();
        if (!Settings.canDrawOverlays(app) || !NotificationAccess.isGranted(app)) return;
        try {
            if (PublicCompanionService.hasHistoricalIdentity(app)
                    || (!BuildConfig.PUBLIC_VIEWER_ORIGIN.isEmpty() && PublicCompanionOwner.identityState(app).read("identity") != null)) {
                app.startForegroundService(new Intent(app, PublicCompanionService.class));
                Log.i("SceneVibePublic", "PUBLIC_RECEIVER_BOOT_START_REQUESTED");
            }
        } catch (RuntimeException refused) {
            Log.i("SceneVibePublic", "PUBLIC_RECEIVER_BOOT_UNAVAILABLE");
        }
    }
}
