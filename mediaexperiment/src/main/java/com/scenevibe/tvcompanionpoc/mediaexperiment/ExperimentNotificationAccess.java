package com.scenevibe.tvcompanionpoc.mediaexperiment;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;

/**
 * Isolated equivalent of :app's {@code NotificationAccess}, scoped to THIS module's
 * own {@link ExperimentMediaAccessService} component. It never references the
 * production class or :app's ComponentName.
 *
 * <p>It reads the same {@code enabled_notification_listeners} secure setting the
 * platform uses, because on some Android TV builds (including the tested Sony
 * Bravia) {@code NotificationManager#isNotificationListenerAccessGranted} can
 * report false while the listener is in fact connected.</p>
 */
public final class ExperimentNotificationAccess {
    private ExperimentNotificationAccess() {}

    public static ComponentName component(Context context) {
        return new ComponentName(context, ExperimentMediaAccessService.class);
    }

    public static boolean isGranted(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        if (enabled == null || enabled.isEmpty()) {
            return false;
        }
        ComponentName expected = component(context);
        for (String entry : enabled.split(":")) {
            ComponentName actual = ComponentName.unflattenFromString(entry);
            if (expected.equals(actual)) {
                return true;
            }
        }
        return false;
    }
}
