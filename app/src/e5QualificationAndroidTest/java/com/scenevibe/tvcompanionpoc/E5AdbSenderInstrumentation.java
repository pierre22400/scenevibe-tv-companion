package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Run in the TEST APK's own distinct UID, not the Companion target process.
 * This real Android negative sender has no DUMP permission; the platform must
 * suppress receiver delivery despite an otherwise valid, explicit request.
 */
public final class E5AdbSenderInstrumentation extends Instrumentation {
    /** Start the finite worker in the separate unprivileged test-package process. */
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    /** Send the exact command envelope under an unauthorized UID and require no delivery. */
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context sender = getTargetContext();
            if (!sender.getPackageName().equals("com.scenevibe.tvcompanionpoc.test")
                    || sender.checkSelfPermission("android.permission.DUMP") != PackageManager.PERMISSION_DENIED) {
                throw new AssertionError("UNPRIVILEGED_SEPARATE_UID_REQUIRED");
            }
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<String> received = new AtomicReference<>();
            Intent request = new Intent("com.scenevibe.tvcompanionpoc.E5_ADB_VOD_INTERLUDE")
                    .setComponent(new ComponentName("com.scenevibe.tvcompanionpoc",
                            "com.scenevibe.tvcompanionpoc.E5AdbTriggerReceiver"))
                    .putExtra("e5VodConfirmed", true)
                    // Do not confuse a stopped target or a congested background queue
                    // with a permission denial; this is an emulator-only negative probe.
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | Intent.FLAG_RECEIVER_FOREGROUND);
            sender.sendOrderedBroadcast(request, null, new BroadcastReceiver() {
                /** Observe unchanged fallback data only after platform permission filtering. */
                @Override public void onReceive(Context context, Intent intent) {
                    received.set(getResultData());
                    done.countDown();
                }
            }, new Handler(Looper.getMainLooper()), 0, "NOT_DELIVERED", null);
            if (!done.await(30, TimeUnit.SECONDS)) {
                throw new AssertionError("NEGATIVE_PROBE_RESULT_TIMEOUT");
            }
            if (!"NOT_DELIVERED".equals(received.get())) {
                throw new AssertionError("UNAUTHORIZED_DELIVERY:" + received.get());
            }
            result.putInt("passed", 1);
            result.putInt("failed", 0);
            result.putInt("senderUid", android.os.Process.myUid());
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putInt("failed", 1);
            result.putString("failure", error.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
