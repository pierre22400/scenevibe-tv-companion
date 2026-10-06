package com.scenevibe.tvcompanionpoc;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;

/** Small compatibility helper for the user-granted notification-listener gate. */
public final class NotificationAccess {
    private NotificationAccess() {}

    public static ComponentName component(Context context) {
        return new ComponentName(context, MediaSessionAccessService.class);
    }

    /**
     * Read the same secure setting Android's notification service uses for enabled
     * listeners. Some Android TV builds (including the tested Sony Bravia) can have
     * a connected listener while NotificationManager#isNotificationListenerAccessGranted
     * still reports false, so that API is not reliable for this UI status.
     */
    public static boolean isGranted(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        if (enabled == null || enabled.isEmpty()) {
            return false;
        }

        ComponentName expected = component(context);
        String[] entries = enabled.split(":");
        for (String entry : entries) {
            ComponentName actual = ComponentName.unflattenFromString(entry);
            if (expected.equals(actual)) {
                return true;
            }
        }
        return false;
    }
}
