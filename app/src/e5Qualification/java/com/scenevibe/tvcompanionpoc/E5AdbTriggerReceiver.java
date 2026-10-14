package com.scenevibe.tvcompanionpoc;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

/**
 * Explicit, DUMP-protected shell admission present only in the opt-in E5 debug APK.
 * Android checks the sender permission at broadcast delivery; a receiver's Binder
 * caller is NOT the original broadcaster and must not be used for authentication.
 * No activity, network, arbitrary action, payload, transport or retry is exposed.
 */
public final class E5AdbTriggerReceiver extends BroadcastReceiver {
    static final String ACTION = "com.scenevibe.tvcompanionpoc.E5_ADB_VOD_INTERLUDE";

    /** Validate the fixed envelope and forward the same guarded service request as Diagnostics. */
    @Override public void onReceive(Context context, Intent intent) {
        String outcome = "REFUSED_MALFORMED";
        try {
            if (!OverlayService.e5QualificationEnabled()) {
                outcome = "REFUSED_FLAG_OFF";
            } else if (wellFormed(context, intent)) {
                if (!Settings.canDrawOverlays(context)) {
                    outcome = "REFUSED_OVERLAY_PERMISSION";
                } else {
                    ComponentName started = context.startForegroundService(E5LocalRequest.confirmedVod(context));
                    outcome = started == null ? "REFUSED_SERVICE_UNAVAILABLE" : "QUEUED_GUARDS_PENDING";
                }
            }
        } catch (RuntimeException refused) {
            // Includes Android background-service denial and malformed Parcelable data.
            // Do not retry, launch an Activity, grant permissions or change transport policy.
            outcome = "REFUSED_ANDROID_OR_PAYLOAD";
        }
        Log.i("SceneVibeE5Adb", outcome);
        if (isOrderedBroadcast()) {
            setResultCode("QUEUED_GUARDS_PENDING".equals(outcome) ? 1 : 0);
            setResultData(outcome);
        }
    }

    /** Require the explicit component, exact action, true Boolean VOD attestation and no extras. */
    static boolean wellFormed(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())
                || !new ComponentName(context, E5AdbTriggerReceiver.class).equals(intent.getComponent())
                || intent.getData() != null || intent.getType() != null
                || intent.getSelector() != null || intent.getClipData() != null
                || intent.getCategories() != null) return false;
        Bundle extras = intent.getExtras();
        return extras != null && extras.size() == 1
                && Boolean.TRUE.equals(extras.get("e5VodConfirmed"));
    }
}
