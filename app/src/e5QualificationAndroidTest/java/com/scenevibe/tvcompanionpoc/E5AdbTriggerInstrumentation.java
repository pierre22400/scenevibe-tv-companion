package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

/**
 * Actual Android envelope parsing and adapter-to-service equivalence, without native commands.
 * A capture context intercepts service dispatch, so the tests cannot pause a real programme.
 */
public final class E5AdbTriggerInstrumentation extends Instrumentation {
    /** Start the finite instrumentation worker. */
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    /** Verify the installed component protection and all admitted/refused wire envelopes. */
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            ComponentName component = new ComponentName(context, E5AdbTriggerReceiver.class);
            ActivityInfo receiver = context.getPackageManager().getReceiverInfo(component, 0);
            require(receiver.exported && "android.permission.DUMP".equals(receiver.permission), "PROTECTED");
            require(context.checkSelfPermission("android.permission.DUMP") == PackageManager.PERMISSION_DENIED,
                    "APP_DOES_NOT_REQUEST_DUMP");
            Intent valid = valid(context);
            require(E5AdbTriggerReceiver.wellFormed(context, valid), "VALID");
            require(!E5AdbTriggerReceiver.wellFormed(context, null), "NULL");
            require(!E5AdbTriggerReceiver.wellFormed(context, new Intent(valid).setComponent(null)), "IMPLICIT");
            require(!E5AdbTriggerReceiver.wellFormed(context, new Intent(valid).setAction("other")), "ACTION");
            require(!E5AdbTriggerReceiver.wellFormed(context, withoutVod(valid)), "MISSING");
            require(!E5AdbTriggerReceiver.wellFormed(context, new Intent(valid).putExtra("e5VodConfirmed", false)), "FALSE");
            require(!E5AdbTriggerReceiver.wellFormed(context, new Intent(valid).putExtra("e5VodConfirmed", "true")), "TYPE");
            require(!E5AdbTriggerReceiver.wellFormed(context, new Intent(valid).putExtra("arbitrary", "payload")), "EXTRA");
            require(!E5AdbTriggerReceiver.wellFormed(context, new Intent(valid).setData(android.net.Uri.parse("file:///x"))), "DATA");
            require(!E5AdbTriggerReceiver.wellFormed(context, new Intent(valid).addCategory("other")), "CATEGORY");
            Capture capture = new Capture(context);
            runOnMainSync(() -> new E5AdbTriggerReceiver().onReceive(capture, valid));
            require(capture.calls == 1, "SINGLE_DISPATCH");
            Intent diagnostics = E5LocalRequest.confirmedVod(context);
            require(diagnostics.filterEquals(capture.request)
                    && diagnostics.getBooleanExtra("e5VodConfirmed", false)
                    == capture.request.getBooleanExtra("e5VodConfirmed", false), "DIAGNOSTICS_SAME_ENVELOPE");
            runOnMainSync(() -> new E5AdbTriggerReceiver().onReceive(capture,
                    new Intent(valid).putExtra("e5VodConfirmed", false)));
            require(capture.calls == 1, "MALFORMED_NO_DISPATCH");
            result.putInt("passed", 15);
            result.putInt("failed", 0);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putInt("failed", 1);
            result.putString("failure", error.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    /** Remove the attestation while retaining the explicit endpoint. */
    private Intent withoutVod(Intent source) {
        Intent copy = new Intent(source);
        copy.removeExtra("e5VodConfirmed");
        return copy;
    }

    /** Build the fixed ADB wire envelope with an explicit VOD attestation. */
    private Intent valid(Context context) {
        return new Intent(E5AdbTriggerReceiver.ACTION)
                .setComponent(new ComponentName(context, E5AdbTriggerReceiver.class))
                .putExtra("e5VodConfirmed", true);
    }

    /** Fail instrumentation on missing observed behavior; no inferred success is allowed. */
    private void require(boolean condition, String code) {
        if (!condition) throw new AssertionError(code);
    }

    /** Capture exactly the forwarded service intent, with no transport or Activity execution. */
    private static final class Capture extends ContextWrapper {
        int calls;
        Intent request;
        /** Keep Android permission/Settings behavior while intercepting only dispatch. */
        Capture(Context context) { super(context); }
        /** Observe the existing service boundary instead of executing native session commands. */
        @Override public ComponentName startForegroundService(Intent intent) {
            calls++;
            request = intent;
            return intent.getComponent();
        }
        /** Any accidental Activity launch is an immediate regression. */
        @Override public void startActivity(Intent intent) { throw new AssertionError("ACTIVITY_LAUNCHED"); }
    }
}
