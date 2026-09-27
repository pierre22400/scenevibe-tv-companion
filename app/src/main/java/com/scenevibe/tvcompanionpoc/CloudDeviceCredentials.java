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
    /**
     * Minimal persistence boundary mirroring CloudTrackRepository.Storage so the pure
     * lifecycle transitions are testable on the JVM without an Android runtime. The
     * production implementation wraps app-private SharedPreferences and reports commit
     * durability; a successful boolean return means the batch is durable.
     */
    interface Storage {
        String getString(String key);
        boolean getFlag(String key);
        void putFlag(String key,boolean value);
        boolean persistActivation(String cloudDeviceId,String deviceToken,
                String activationId,String activationSecret,String userCode);
        boolean confirmClaimed();
        void clearActivationTemporaries();
        void disconnect();
    }
    private final Storage storage;
    /** Uses Android private preferences and excludes OS backup through the existing manifest. */
    CloudDeviceCredentials(Context context) {
        SharedPreferences prefs=context.getApplicationContext()
                .getSharedPreferences("cloud_identity",Context.MODE_PRIVATE);
        storage=new Storage() {
            @Override public String getString(String key) {return prefs.getString(key,null);}
            @Override public boolean getFlag(String key) {return prefs.getBoolean(key,false);}
            @Override public void putFlag(String key,boolean value) {prefs.edit().putBoolean(key,value).apply();}
            @Override public boolean persistActivation(String cloudDeviceId,String deviceToken,
                    String activationId,String activationSecret,String userCode) {
                return prefs.edit()
                        .putString("cloudDeviceId",cloudDeviceId)
                        .putString("deviceToken",deviceToken)
                        .putString("activationId",activationId)
                        .putString("activationSecret",activationSecret)
                        .putString("userCode",userCode)
                        .commit();
            }
            @Override public boolean confirmClaimed() {
                return prefs.edit().remove("activationId").remove("activationSecret").remove("userCode")
                        .putBoolean("connected",true).commit();
            }
            @Override public void clearActivationTemporaries() {
                prefs.edit().remove("activationId").remove("activationSecret").remove("userCode").commit();
            }
            @Override public void disconnect() {
                prefs.edit().remove("activationId").remove("activationSecret").remove("userCode")
                        .putBoolean("connected",false).commit();
            }
        };
    }
    /** Injectable persistence boundary for deterministic JVM tests. */
    CloudDeviceCredentials(Storage storage) {this.storage=storage;}
    /** The durable cloud device credential returned by the 201; independent of LAN v0.6. */
    String deviceToken() {return storage.getString("deviceToken");}
    /** The cloud-minted device UUID used in assignment and ACK URLs. */
    String cloudDeviceId() {return storage.getString("cloudDeviceId");}
    /** Returns the temporary activation secret only for TV-side status polling. */
    String activationSecret() {return storage.getString("activationSecret");}
    /** Returns the current temporary activation ID. */
    String activationId() {return storage.getString("activationId");}
    /** Displays only the temporary human pairing code on TV, never the credential. */
    String userCode() {return storage.getString("userCode");}
    /** Exposes state without private values. */
    boolean connected() {return storage.getFlag("connected");}
    /** Last transport outcome is display-only and never gates cached scheduling. */
    boolean offline() {return storage.getFlag("offline");}
    /** Updates UI observability without storing any server payload. */
    void setOffline(boolean value) {storage.putFlag("offline",value);}
    /**
     * On any valid 201 this atomically persists the durable cloudDeviceId + deviceToken
     * immediately (before any claim) and records activationId/activationSecret/userCode as
     * temporary activation state. A successful return means every field is durable.
     */
    boolean persistActivation(String cloudDeviceId,String deviceToken,
            String activationId,String activationSecret,String userCode) {
        return storage.persistActivation(cloudDeviceId,deviceToken,activationId,activationSecret,userCode);
    }
    /** Marks the activation claimed: keeps deviceToken+cloudDeviceId, drops the temporaries. */
    boolean confirmClaimed() {return storage.confirmClaimed();}
    /** Drops ONLY the expired activation temporaries; the durable device credential survives. */
    void clearExpiredActivation() {storage.clearActivationTemporaries();}
    /**
     * Stops cloud polling locally. Clears activation temporaries and connected flag but KEEPS
     * cloudDeviceId+deviceToken so reconnection can reuse the durable credential; it never
     * touches the FinalTrack cache.
     */
    void disconnect() {storage.disconnect();}
}
