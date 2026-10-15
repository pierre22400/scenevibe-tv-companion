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
            verifySonySnapshot(context);
            result.putInt("passed", 20);
            result.putInt("failed", 0);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putInt("failed", 1);
            result.putString("failure", error.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    /** Exercise real Android metadata/state conversion without controlling any external session. */
    private void verifySonySnapshot(Context context) throws Exception {
        android.media.session.MediaSession session =
                new android.media.session.MediaSession(context, "E5-039-synthetic");
        try {
            long now = android.os.SystemClock.elapsedRealtime();
            session.setMetadata(new android.media.MediaMetadata.Builder()
                    .putString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID, "synthetic-vod")
                    .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, -1).build());
            session.setPlaybackState(new android.media.session.PlaybackState.Builder()
                    .setActions(android.media.session.PlaybackState.ACTION_PAUSE
                            | android.media.session.PlaybackState.ACTION_PLAY)
                    .setState(3, 2475551, 1f, now).build());
            NativeSessionSnapshot playing = awaitSnapshot(session, 3, -1);
            require(playing != null && playing.durationMs == -1, "ANDROID_UNKNOWN_DURATION");
            require(NativeQualificationPolicy.local035().fresh(playing,
                    android.os.SystemClock.elapsedRealtime()), "ANDROID_FRESH_POSITION");
            session.setPlaybackState(new android.media.session.PlaybackState.Builder()
                    .setActions(android.media.session.PlaybackState.ACTION_PLAY)
                    .setState(2, 2475551, 0f, android.os.SystemClock.elapsedRealtime()).build());
            NativeSessionSnapshot paused = awaitSnapshot(session, 2, -1);
            require(playing.sameTarget(paused), "ANDROID_SAME_TARGET_AFTER_PAUSE");
            require(paused.state == 2 && paused.estimatedPosition(
                    android.os.SystemClock.elapsedRealtime() + 1000) == 2475551, "ANDROID_RAW_PAUSED");
            session.setMetadata(new android.media.MediaMetadata.Builder()
                    .putString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID, "synthetic-vod").build());
            NativeSessionSnapshot missing = awaitSnapshot(session, 2, 0);
            require(missing.durationMs == 0 && !NativeQualificationPolicy.local035().fresh(missing,
                    android.os.SystemClock.elapsedRealtime()), "ANDROID_MISSING_DURATION_REFUSED");
        } finally {
            session.release();
        }
    }

    /** Await only publication of our synthetic fixture; never retry an application transport. */
    private NativeSessionSnapshot awaitSnapshot(android.media.session.MediaSession session,
            int expectedState, long expectedDuration) throws Exception {
        java.lang.reflect.Method method = AndroidNativeSessionPort.class.getDeclaredMethod(
                "snapshot", android.media.session.MediaController.class, boolean.class);
        method.setAccessible(true);
        long deadline = android.os.SystemClock.elapsedRealtime() + 2000;
        do {
            NativeSessionSnapshot snapshot = (NativeSessionSnapshot) method.invoke(null,
                    session.getController(), true);
            if (snapshot != null && snapshot.state == expectedState
                    && snapshot.durationMs == expectedDuration) return snapshot;
            Thread.sleep(10);
        } while (android.os.SystemClock.elapsedRealtime() < deadline);
        android.media.MediaMetadata metadata = session.getController().getMetadata();
        android.media.session.PlaybackState state = session.getController().getPlaybackState();
        throw new AssertionError("SYNTHETIC_PUBLICATION_TIMEOUT state="
                + (state == null ? -1 : state.getState()) + " duration="
                + (metadata == null ? "missing" : metadata.getLong(
                        android.media.MediaMetadata.METADATA_KEY_DURATION)));
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
