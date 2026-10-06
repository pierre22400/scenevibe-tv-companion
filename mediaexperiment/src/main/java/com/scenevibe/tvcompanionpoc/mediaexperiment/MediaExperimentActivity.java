package com.scenevibe.tvcompanionpoc.mediaexperiment;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.AudioFocusPort;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.Diagnostics;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.InterludeState;

/**
 * TV diagnostic launcher for the isolated media-interlude POC.
 *
 * <p>Programmatic D-pad UI (mirroring :app's {@code MainActivity} style) with
 * exactly the five required diagnostic actions and a scrollable, BOUNDED diagnostics
 * area. The diagnostics area shows ONLY coarse mechanism facts from the FEAT-002
 * {@link Diagnostics}: selected package, playback state, advertised actions, mediaId
 * presence, audio-focus result, pause sent/confirmed/timeout, overlay attached,
 * local video started/completed/error, play sent, resume confirmed/timeout. It never
 * shows content text or credentials.</p>
 *
 * <p>It also exposes buttons to open the overlay-permission and notification-access
 * settings, reusing the production Activity's intent patterns.</p>
 */
public final class MediaExperimentActivity extends Activity
        implements InterludeService.DiagnosticsListener {
    private static final String TAG = "SceneVibeInterludePoc";

    private TextView overlayStatus;
    private TextView accessStatus;
    private TextView diagnosticsView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private InterludeService service;
    private boolean bound;

    private final ServiceConnection connection = new ServiceConnection() {
        /** Bind operator actions to the isolated foreground runtime. */
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((InterludeService.LocalBinder) binder).service();
            service.setDiagnosticsListener(MediaExperimentActivity.this);
            bound = true;
        }

        /** Clear a disconnected runtime without issuing transport commands. */
        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            bound = false;
        }
    };

    /** Create D-pad controls and bounded, content-free diagnostics. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(40), dp(28), dp(40), dp(28));
        controls.setBackgroundColor(0xFF101418);

        TextView title = new TextView(this);
        title.setText("SceneVibe Media Interlude POC");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        controls.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Isolated capability spike. Diagnostics only. "
                + "No claim of real ducking/pause without an on-device observation.");
        subtitle.setTextColor(0xFFB9C2CC);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(8), 0, dp(16));
        controls.addView(subtitle);

        overlayStatus = statusLabel(controls);
        accessStatus = statusLabel(controls);

        addButton(controls, "Display over other apps settings", this::openOverlaySettings);
        addButton(controls, "Media access settings", this::openNotificationAccessSettings);

        // The five required diagnostic actions, in order.
        addButton(controls, "SCAN MEDIA SESSION",
                () -> withService(InterludeService::scanMediaSession));
        addButton(controls, "TEST AUDIO DUCK",
                () -> withService(InterludeService::testAudioDuck));
        addButton(controls, "TEST PAUSE",
                () -> withService(InterludeService::testPause));
        addButton(controls, "TEST FULL INTERLUDE",
                () -> withService(InterludeService::testFullInterlude));
        addButton(controls, "EMERGENCY RESTORE / STOP",
                () -> withService(InterludeService::emergencyStop));

        TextView diagnosticsTitle = new TextView(this);
        diagnosticsTitle.setText("Diagnostics (bounded)");
        diagnosticsTitle.setTextColor(0xFFFFFFFF);
        diagnosticsTitle.setTextSize(18);
        diagnosticsTitle.setPadding(0, dp(20), 0, dp(8));
        controls.addView(diagnosticsTitle);

        diagnosticsView = new TextView(this);
        diagnosticsView.setTextColor(0xFFCFE8FF);
        diagnosticsView.setTextSize(15);
        diagnosticsView.setFocusable(true);
        diagnosticsView.setText(renderDiagnostics(null, InterludeState.IDLE, false));
        controls.addView(diagnosticsView);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(controls);
        setContentView(scroll);
    }

    /** Start and bind the isolated foreground service when permitted. */
    @Override
    protected void onStart() {
        super.onStart();
        // Start the mediaPlayback foreground service (requires overlay permission for
        // the interlude; the service stops itself if the permission is absent) and
        // bind so the Activity can drive the five actions and receive diagnostics.
        if (Settings.canDrawOverlays(this)) {
            try {
                InterludeService.start(this);
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not start InterludeService");
            }
        }
        bindService(new Intent(this, InterludeService.class), connection, Context.BIND_AUTO_CREATE);
    }

    /** Refresh permission status after returning from settings. */
    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    /** Detach the UI listener while allowing the explicit test to finish. */
    @Override
    protected void onStop() {
        if (bound) {
            if (service != null) {
                service.setDiagnosticsListener(null);
            }
            unbindService(connection);
            bound = false;
        }
        super.onStop();
    }

    /** Render only bounded mechanism facts on the UI thread. */
    @Override
    public void onDiagnostics(Diagnostics diagnostics, InterludeState state, boolean accessGranted) {
        handler.post(() -> {
            diagnosticsView.setText(renderDiagnostics(diagnostics, state, accessGranted));
            refreshStatus();
        });
    }

    /** Route an operator action only when the local runtime is bound. */
    private void withService(java.util.function.Consumer<InterludeService> action) {
        if (service == null) {
            Toast.makeText(this, "Runtime not ready yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        action.accept(service);
    }

    /** Show overlay and media-access permission state. */
    private void refreshStatus() {
        overlayStatus.setText(Settings.canDrawOverlays(this)
                ? "Display over other apps: Granted"
                : "Display over other apps: Not granted");
        accessStatus.setText(ExperimentNotificationAccess.isGranted(this)
                ? "Media access: Granted"
                : "Media access: Not granted");
    }

    /** Render ONLY the bounded diagnostics fields. No content text, no credentials. */
    private String renderDiagnostics(Diagnostics d, InterludeState state, boolean accessGranted) {
        StringBuilder out = new StringBuilder();
        out.append("state: ").append(state == null ? "IDLE" : state.name()).append('\n');
        out.append("notification access: ").append(accessGranted ? "granted" : "not granted")
                .append('\n');
        if (d == null) {
            out.append("(no run yet)");
            return out.toString();
        }
        out.append("selected package: ").append(valueOrDash(d.selectedPackage)).append('\n');
        out.append("playback state: ").append(valueOrDash(d.playbackStateName)).append('\n');
        out.append("actions: ")
                .append("play=").append(d.actionPlayAvailable)
                .append(" pause=").append(d.actionPauseAvailable)
                .append(" play_pause=").append(d.actionPlayPauseAvailable).append('\n');
        out.append("mediaId present: ").append(d.mediaIdPresent).append('\n');
        out.append("audio focus: ").append(focusText(d.audioFocusResult)).append('\n');
        out.append("pause command sent: ").append(d.pauseCommandSent).append('\n');
        out.append("pause confirmed: ").append(d.pauseConfirmed).append('\n');
        out.append("pause timeout: ").append(d.pauseTimedOut).append('\n');
        out.append("initial playback state: ").append(d.initialPlaybackStateName).append('\n');
        out.append("pause ownership acquired: ").append(d.pauseOwnershipAcquired).append('\n');
        out.append("original session present: ").append(d.originalSessionIdentityPresent).append('\n');
        out.append("session revalidation attempted: ").append(d.sessionRevalidationAttempted).append('\n');
        out.append("session revalidation succeeded: ").append(d.sessionRevalidationSucceeded).append('\n');
        out.append("relevant active package changed: ").append(d.relevantActivePackageChanged).append('\n');
        out.append("latest state before resume: ").append(d.latestStateBeforeResume).append('\n');
        out.append("resume denied reason: ").append(d.resumeDeniedReason.name()).append('\n');
        out.append("overlay attached: ").append(d.overlayAttached).append('\n');
        out.append("local video started: ").append(d.localVideoStarted).append('\n');
        out.append("local video completed: ").append(d.localVideoCompleted).append('\n');
        out.append("local video error: ").append(d.localVideoError).append('\n');
        out.append("play command sent: ").append(d.playCommandSent).append('\n');
        out.append("resume confirmed: ").append(d.resumeConfirmed).append('\n');
        out.append("resume timeout: ").append(d.resumeTimedOut);
        return out.toString();
    }

    /** Render focus result without implying physical ducking. */
    private String focusText(AudioFocusPort.Result result) {
        if (result == null) {
            return "-";
        }
        return result == AudioFocusPort.Result.GRANTED ? "granted" : "denied";
    }

    /** Render missing coarse diagnostic labels consistently. */
    private String valueOrDash(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    /** Create a readable permission-status label. */
    private TextView statusLabel(LinearLayout parent) {
        TextView label = new TextView(this);
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(16);
        label.setPadding(0, dp(4), 0, dp(4));
        parent.addView(label);
        return label;
    }

    /** Create a focusable D-pad action button. */
    private Button addButton(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(17);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(dp(460), dp(54));
        params.topMargin = dp(8);
        parent.addView(button, params);
        return button;
    }

    /** Overlay permission settings, mirroring MainActivity's intent fallback pattern. */
    private void openOverlaySettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException first) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (ActivityNotFoundException second) {
                Toast.makeText(this, "Overlay settings unavailable on this TV.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Notification-access settings, mirroring MainActivity's intent fallback pattern. */
    private void openNotificationAccessSettings() {
        Toast.makeText(this,
                "In Settings: Special app access -> Notification access -> this POC.",
                Toast.LENGTH_LONG).show();
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
        } catch (ActivityNotFoundException first) {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (ActivityNotFoundException second) {
                Toast.makeText(this, "Notification-access settings unavailable on this TV.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Convert logical UI dimensions to device pixels. */
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
