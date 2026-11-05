package com.scenevibe.tvcompanionpoc;

import android.accessibilityservice.AccessibilityButtonController;
import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Experimental bridge from the system accessibility shortcut to the existing controls.
 * The user enables and assigns this service through Android settings.
 */
public final class NetflixShortcutService extends AccessibilityService {
    private AccessibilityButtonController controller;

    private AccessibilityButtonController.AccessibilityButtonCallback callback;

    /** Register only the click callback and leave the panel closed on connection. */
    @Override protected void onServiceConnected() {
        detachCallback();
        controller = getAccessibilityButtonController();
        callback = new AccessibilityButtonController.AccessibilityButtonCallback() {
            /** Forward only clicks belonging to the current Android connection. */
            @Override public void onClicked(AccessibilityButtonController source) {
                if (callback == this && controller == source) {
                    traceShortcut("SHORTCUT_CALLBACK_RECEIVED");
                    NetflixManualSyncService.requestControlsFromShortcut();
                }
            }
        };
        controller.registerAccessibilityButtonCallback(callback);
        traceShortcut("SHORTCUT_CONNECTED");
    }

    /** Ignore accessibility events. This service requests no event types. */
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}

    /** No accessibility feedback is emitted by this bridge. */
    @Override public void onInterrupt() {}

    /** Drop callback registration when Android disconnects the service. */
    @Override public boolean onUnbind(Intent intent) {
        detachCallback();
        return super.onUnbind(intent);
    }

    /** Drop any remaining callback registration before destruction. */
    @Override public void onDestroy() {
        detachCallback();
        super.onDestroy();
    }

    /** Clear identity before unregistering so a pending click cannot open the panel. */
    private void detachCallback() {
        AccessibilityButtonController previous = controller;
        AccessibilityButtonController.AccessibilityButtonCallback retired = callback;
        callback = null;
        controller = null;
        if (previous != null && retired != null) {
            previous.unregisterAccessibilityButtonCallback(retired);
            traceShortcut("SHORTCUT_DISCONNECTED");
        }
    }

    /** Log only fixed lifecycle and command markers, without user input or window data. */
    static void traceShortcut(String marker) {
        Log.i("SceneVibeShortcut", marker);
    }
}
