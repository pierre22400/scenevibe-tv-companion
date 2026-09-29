package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The persisted "Start SceneVibe with TV" opt-in, in its own app-private preferences
 * file. It defaults to FALSE: without an explicit user opt-in the {@link BootReceiver}
 * never arms the overlay service, so a fresh install boots the TV with nothing running.
 * The value is a plain non-secret boolean and is never logged.
 */
final class AutostartPreference {
    private static final String PREFS = "autostart";
    private static final String KEY_ENABLED = "enabled";

    private AutostartPreference() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Reads the opt-in, defaulting to false so nothing autostarts without explicit consent. */
    static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    /** Persists the opt-in durably so the boot receiver reads a committed value. */
    static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).commit();
    }
}
