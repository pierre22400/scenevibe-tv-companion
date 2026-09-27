package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * App-private cloud identity. Keeps three distinct values: the local installationId
 * (sourced from PairingPolicy.deviceId(), used ONLY as installationId at activation
 * start), the cloudDeviceId (UUID minted by the 201 response, used in assignment/ack
 * URLs), and the durable cloud deviceToken (the credential returned by the 201). It is
 * never the LAN pairing token or a controller token. No stored value is ever logged.
 */
final class CloudDeviceCredentials {
    private final SharedPreferences prefs;
    /** Uses Android private preferences and excludes OS backup through the existing manifest. */
    CloudDeviceCredentials(Context context) {
        prefs=context.getApplicationContext().getSharedPreferences("cloud_identity",Context.MODE_PRIVATE);
    }
    /** The durable cloud device credential returned by the 201; independent of LAN v0.6. */
    String deviceToken() {return prefs.getString("deviceToken",null);}
    /** The cloud-minted device UUID used in assignment and ACK URLs. */
    String cloudDeviceId() {return prefs.getString("cloudDeviceId",null);}
    /** Returns the temporary activation secret only for TV-side status polling. */
    String activationSecret() {return prefs.getString("activationSecret",null);}
    /** Returns the current temporary activation ID. */
    String activationId() {return prefs.getString("activationId",null);}
    /** Displays only the temporary human pairing code on TV, never the credential. */
    String userCode() {return prefs.getString("userCode",null);}
    /** Exposes state without private values. */
    boolean connected() {return prefs.getBoolean("connected",false);}
    /** Last transport outcome is display-only and never gates cached scheduling. */
    boolean offline() {return prefs.getBoolean("offline",false);}
    /** Updates UI observability without storing any server payload. */
    void setOffline(boolean value) {prefs.edit().putBoolean("offline",value).apply();}
    /**
     * On any valid 201 this atomically persists the durable cloudDeviceId + deviceToken
     * immediately (before any claim) and records activationId/activationSecret/userCode as
     * temporary activation state. A successful return means every field is durable.
     */
    boolean persistActivation(String cloudDeviceId,String deviceToken,
            String activationId,String activationSecret,String userCode) {
        return prefs.edit()
                .putString("cloudDeviceId",cloudDeviceId)
                .putString("deviceToken",deviceToken)
                .putString("activationId",activationId)
                .putString("activationSecret",activationSecret)
                .putString("userCode",userCode)
                .commit();
    }
    /** Marks the activation claimed: keeps deviceToken+cloudDeviceId, drops the temporaries. */
    boolean confirmClaimed() {
        return prefs.edit().remove("activationId").remove("activationSecret").remove("userCode")
                .putBoolean("connected",true).commit();
    }
    /** Drops ONLY the expired activation temporaries; the durable device credential survives. */
    void clearExpiredActivation() {
        prefs.edit().remove("activationId").remove("activationSecret").remove("userCode").commit();
    }
    /**
     * Stops cloud polling locally. Clears activation temporaries and connected flag but KEEPS
     * cloudDeviceId+deviceToken so reconnection can reuse the durable credential; it never
     * touches the FinalTrack cache.
     */
    void disconnect() {
        prefs.edit().remove("activationId").remove("activationSecret").remove("userCode")
                .putBoolean("connected",false).commit();
    }
}
