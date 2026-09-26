package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.security.SecureRandom;

/** Shares the pairing policy between the TV activity and the foreground service. */
final class PairingRuntime {
    private static PairingPolicy instance;

    static synchronized PairingPolicy get(Context context) {
        if (instance == null) {
            SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences("pairing", Context.MODE_PRIVATE);
            PairingPolicy.Storage storage = new PairingPolicy.Storage() {
                @Override public String get(String key) { return prefs.getString(key, null); }
                @Override public void put(String key, String value) {
                    if (!prefs.edit().putString(key, value).commit())
                        throw new IllegalStateException("Pairing credential could not be persisted");
                }
                @Override public void remove(String key) {
                    if (!prefs.edit().remove(key).commit())
                        throw new IllegalStateException("Pairing credential could not be revoked");
                }
            };
            instance = new PairingPolicy(SystemClock::elapsedRealtime, storage, new SecureRandom());
        }
        return instance;
    }

    private PairingRuntime() {}
}
