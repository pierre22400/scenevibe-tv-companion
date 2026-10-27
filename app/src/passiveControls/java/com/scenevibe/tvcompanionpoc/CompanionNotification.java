package com.scenevibe.tvcompanionpoc;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;

/**
 * Closed notification adapter for historical variants, without AndroidX or new
 * merged permissions. BuildConfig.TVUX_CONTROL_PANEL is false in these variants;
 * this method is never an alternative entry into the existing service.
 */
final class CompanionNotification {
    /** Fail closed if an unsupported profile ever requests the experimental control notification. */
    static Notification create(Context context,String channel,PendingIntent panel,PendingIntent stop) {
        throw new IllegalStateException("TVUX_NOTIFICATION_VARIANT_REQUIRED");
    }
    /** No state, registration or initialization side effects exist here. */
    private CompanionNotification() {}
}
