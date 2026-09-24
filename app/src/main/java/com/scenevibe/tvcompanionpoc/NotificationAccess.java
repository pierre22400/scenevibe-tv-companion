package com.scenevibe.tvcompanionpoc;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

/** Small compatibility helper for the user-granted notification-listener gate. */
public final class NotificationAccess {
    private NotificationAccess() {}

    public static ComponentName component(Context context) {
        return new ComponentName(context, MediaSessionAccessService.class);
    }

    public static boolean isGranted(Context context) {
        ComponentName component = component(context);
        if (Build.VERSION.SDK_INT >= 27) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            return manager != null && manager.isNotificationListenerAccessGranted(component);
        }
        String enabled = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(component.flattenToString());
    }
}
