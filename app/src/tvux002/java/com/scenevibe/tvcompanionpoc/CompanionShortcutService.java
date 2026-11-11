package com.scenevibe.tvcompanionpoc;

import android.accessibilityservice.AccessibilityButtonController;
import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Stable principal-package bridge from Android's user-configured accessibility
 * shortcut (MUTE on the owner's Sony) to the existing OverlayService owner.
 * It observes no window content or ordinary keys, creates no playback session,
 * and registers only the public AccessibilityButtonController click callback.
 */
public final class CompanionShortcutService extends AccessibilityService {
    private AccessibilityButtonController controller;
    private AccessibilityButtonController.AccessibilityButtonCallback callback;
    private static CompanionShortcutService connected;
    private static int connections,callbacks,accepted,duplicates,refused;
    private static String lastResult="NOT_CONNECTED";

    /** Register the public callback for this exact Android connection; connection alone opens nothing. */
    @Override protected void onServiceConnected(){
        detachCallback();
        if(!BuildConfig.COMPANION_MUTE_002)return;
        controller=getAccessibilityButtonController();
        callback=new AccessibilityButtonController.AccessibilityButtonCallback(){
            /** Forward only an actual click belonging to the current, still connected registration. */
            @Override public void onClicked(AccessibilityButtonController source){
                if(connected!=CompanionShortcutService.this || callback!=this || controller!=source)return;
                callbacks=increment(callbacks);trace("SHORTCUT_CALLBACK_RECEIVED");
                TvFirst009TextServiceOwner.ShortcutResult result=TvFirst009TextServiceOwner.openFromAccessibilityShortcut();
                lastResult=result.name();
                if(result==TvFirst009TextServiceOwner.ShortcutResult.WINDOW_REQUESTED)accepted=increment(accepted);
                else if(result==TvFirst009TextServiceOwner.ShortcutResult.ALREADY_OPEN)duplicates=increment(duplicates);
                else refused=increment(refused);
                trace("SHORTCUT_"+result.name());
            }
        };
        controller.registerAccessibilityButtonCallback(callback);
        connected=this;connections=increment(connections);lastResult="CONNECTED";trace("SHORTCUT_CONNECTED");
    }

    /** No accessibility event types or window contents are requested or inspected. */
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    /** This click-only bridge emits no accessibility feedback. */
    @Override public void onInterrupt(){}
    /** Retire the registration before Android disconnects this service. */
    @Override public boolean onUnbind(Intent intent){detachCallback();return super.onUnbind(intent);}
    /** Retire callbacks synchronously; a stale instance cannot clear a replacement connection. */
    @Override public void onDestroy(){detachCallback();super.onDestroy();}

    /** Invalidate identity before unregistering so queued callbacks cannot open a stale panel. */
    private void detachCallback(){
        AccessibilityButtonController previous=controller;
        AccessibilityButtonController.AccessibilityButtonCallback retired=callback;
        callback=null;controller=null;
        if(connected==this){connected=null;lastResult="DISCONNECTED";trace("SHORTCUT_DISCONNECTED");}
        if(previous!=null && retired!=null)previous.unregisterAccessibilityButtonCallback(retired);
    }
    /** Bound diagnostics without recording keys, window trees, identities or editorial content. */
    private static int increment(int value){return Math.min(100000,value+1);}
    /** Emit only fixed lifecycle, callback and command-result markers. */
    private static void trace(String event){Log.i("SceneVibeMute002",event);}
    /** Expose only bounded process-local evidence, never a callback injection or service-start command. */
    static Snapshot diagnostics(){return new Snapshot(connected!=null,connections,callbacks,accepted,duplicates,refused,lastResult);}

    /** Immutable diagnostic projection usable by the existing software and native qualification benches. */
    static final class Snapshot {
        final boolean connected;
        final int connections,callbacks,accepted,duplicates,refused;
        final String result;
        /** Copy finite counters and the closed result vocabulary only. */
        Snapshot(boolean connected,int connections,int callbacks,int accepted,int duplicates,int refused,String result){
            this.connected=connected;this.connections=connections;this.callbacks=callbacks;this.accepted=accepted;
            this.duplicates=duplicates;this.refused=refused;this.result=result;
        }
    }
}
