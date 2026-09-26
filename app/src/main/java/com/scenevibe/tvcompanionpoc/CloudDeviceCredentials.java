package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.content.SharedPreferences;

/** App-private device credential is never the LAN pairing token or a controller token. */
final class CloudDeviceCredentials {
    private final SharedPreferences prefs;
    /** Uses Android private preferences and excludes OS backup through the existing manifest. */
    CloudDeviceCredentials(Context context) {
        prefs=context.getApplicationContext().getSharedPreferences("cloud_identity",Context.MODE_PRIVATE);
    }
    /** Returns a persisted token only to the outbound TV client. */
    String deviceToken() {return prefs.getString("deviceToken",null);}
    /** Pending token is usable only once the cloud claim succeeds. */
    String pendingToken() {return prefs.getString("pendingToken",null);}
    /** Returns a pending activation secret only for TV-side polling. */
    String activationSecret() {return prefs.getString("activationSecret",null);}
    /** Returns the current activation ID. */
    String activationId() {return prefs.getString("activationId",null);}
    /** Displays only the temporary human code on TV, never the credential. */
    String code() {return prefs.getString("code",null);}
    /** Exposes state without private values. */
    boolean connected() {return prefs.getBoolean("connected",false);}
    /** Last transport outcome is display-only and never gates cached scheduling. */
    boolean offline() {return prefs.getBoolean("offline",false);}
    /** Updates UI observability without storing any server payload. */
    void setOffline(boolean value) {prefs.edit().putBoolean("offline",value).apply();}
    /** Commits all candidate state before the TV can claim an activation. */
    boolean pending(String id,String activationSecret,String token,String code) {
        return prefs.edit().putString("activationId",id).putString("activationSecret",activationSecret)
                .putString("pendingToken",token).putString("code",code).commit();
    }
    /** Marks activation confirmed and removes its one-time secret and user code. */
    boolean confirm() {String token=pendingToken();if(token==null)return false;
        return prefs.edit().putString("deviceToken",token).remove("pendingToken")
            .remove("activationSecret").remove("activationId").remove("code")
            .putBoolean("connected",true).commit();}
    /** Drops an expired pending activation without revoking the previous connected device. */
    void clearPending() {prefs.edit().remove("activationId").remove("activationSecret")
            .remove("pendingToken").remove("code").commit();}
    /** Stops cloud polling locally; retains old device proof so reconnection can rotate it. */
    void disconnect() {prefs.edit().remove("activationId").remove("activationSecret")
            .remove("pendingToken").remove("code").putBoolean("connected",false).commit();}
}
