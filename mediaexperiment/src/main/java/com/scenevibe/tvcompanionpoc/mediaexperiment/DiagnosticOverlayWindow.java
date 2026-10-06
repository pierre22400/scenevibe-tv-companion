package com.scenevibe.tvcompanionpoc.mediaexperiment;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.AudioFocusPort;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.Diagnostics;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.InterludeState;

/**
 * Focusable TV remote control panel hosted as TYPE_APPLICATION_OVERLAY.
 *
 * <p>This is the physical-test harness. It deliberately stays outside the Android
 * Activity stack so Prime/Netflix remains the underlying native media Activity and
 * can keep exposing its MediaSession while the operator drives POC actions.</p>
 *
 * <p>The panel is narrow and translucent. It is focusable for D-pad input and uses
 * FLAG_NOT_TOUCH_MODAL so it does not claim the whole display. When the local
 * fullscreen interlude overlay is first attached, this panel is removed/re-added
 * once to keep EMERGENCY STOP reachable above that video surface.</p>
 */
final class DiagnosticOverlayWindow {
    interface Actions {
        void scan();
        void duck();
        void pause();
        void fullInterlude();
        void emergencyStop();
    }

    private final Context context;
    private final WindowManager windows;
    private final Actions actions;

    private ScrollView root;
    private TextView diagnosticsView;
    private Button firstButton;
    private WindowManager.LayoutParams params;
    private boolean lastInterludeOverlayAttached;

    DiagnosticOverlayWindow(Context context, Actions actions) {
        this.context = context.getApplicationContext();
        this.actions = actions;
        this.windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    /** Attach the focusable translucent control panel without launching an Activity. */
    void show() {
        if (root != null) return;
        if (windows == null) {
            throw new IllegalStateException("WindowManager unavailable");
        }
        if (!Settings.canDrawOverlays(context)) {
            throw new IllegalStateException("Overlay permission not granted");
        }

        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(20), dp(18), dp(20), dp(18));
        controls.setBackgroundColor(0xDC101418);

        TextView title = new TextView(context);
        title.setText("SceneVibe Media POC");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        controls.addView(title);

        TextView hint = new TextView(context);
        hint.setText("Overlay controls — native video remains underneath");
        hint.setTextColor(0xFFB9C2CC);
        hint.setTextSize(12);
        hint.setPadding(0, dp(4), 0, dp(8));
        controls.addView(hint);

        firstButton = addButton(controls, "SCAN MEDIA SESSION", actions::scan);
        addButton(controls, "TEST AUDIO DUCK", actions::duck);
        addButton(controls, "TEST PAUSE", actions::pause);
        addButton(controls, "TEST FULL INTERLUDE", actions::fullInterlude);
        addButton(controls, "EMERGENCY RESTORE / STOP", actions::emergencyStop);

        TextView diagnosticsTitle = new TextView(context);
        diagnosticsTitle.setText("Diagnostics");
        diagnosticsTitle.setTextColor(Color.WHITE);
        diagnosticsTitle.setTextSize(16);
        diagnosticsTitle.setPadding(0, dp(12), 0, dp(5));
        controls.addView(diagnosticsTitle);

        diagnosticsView = new TextView(context);
        diagnosticsView.setTextColor(0xFFCFE8FF);
        diagnosticsView.setTextSize(12);
        diagnosticsView.setText(renderDiagnostics(null, InterludeState.IDLE, false));
        controls.addView(diagnosticsView);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        scroll.addView(controls);

        params = new WindowManager.LayoutParams(
                dp(540),
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.END | Gravity.TOP;
        params.setTitle("SceneVibe Media POC controls");

        windows.addView(scroll, params);
        root = scroll;
        lastInterludeOverlayAttached = false;

        // The overlay window itself is focusable; request D-pad focus on the first action.
        firstButton.post(firstButton::requestFocus);
    }

    /** Update bounded diagnostics and keep STOP reachable above the local video overlay. */
    void update(Diagnostics diagnostics, InterludeState state, boolean accessGranted) {
        if (root == null || diagnosticsView == null) return;
        diagnosticsView.setText(renderDiagnostics(diagnostics, state, accessGranted));

        boolean interludeAttached = diagnostics != null && diagnostics.overlayAttached;
        if (interludeAttached && !lastInterludeOverlayAttached) {
            raiseAboveInterlude();
        }
        lastInterludeOverlayAttached = interludeAttached;
    }

    /** Remove and re-add the same panel once so it is above a newly added video window. */
    private void raiseAboveInterlude() {
        if (root == null || params == null || windows == null) return;
        ScrollView existing = root;
        try {
            windows.removeViewImmediate(existing);
            windows.addView(existing, params);
            if (firstButton != null) firstButton.post(firstButton::requestFocus);
        } catch (RuntimeException ignored) {
            // Diagnostic visibility must never mutate or resume native media state.
        }
    }

    /** Remove the diagnostic panel idempotently. */
    void remove() {
        ScrollView existing = root;
        root = null;
        diagnosticsView = null;
        firstButton = null;
        params = null;
        lastInterludeOverlayAttached = false;
        if (existing != null && windows != null) {
            try {
                windows.removeViewImmediate(existing);
            } catch (IllegalArgumentException ignored) {
                // Already detached.
            }
        }
    }

    /** Create a D-pad button that dispatches only its explicit isolated action. */
    private Button addButton(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams layout =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        layout.topMargin = dp(5);
        parent.addView(button, layout);
        return button;
    }

    /** Render only bounded mechanism facts; never token/title/subtitle/credentials. */
    private String renderDiagnostics(
            Diagnostics d, InterludeState state, boolean accessGranted) {
        StringBuilder out = new StringBuilder();
        out.append("state: ").append(state == null ? "IDLE" : state.name()).append('\n');
        out.append("notification access: ")
                .append(accessGranted ? "granted" : "not granted").append('\n');
        if (d == null) {
            out.append("(no run yet)");
            return out.toString();
        }
        out.append("selected package: ").append(valueOrDash(d.selectedPackage)).append('\n');
        out.append("playback state: ").append(valueOrDash(d.playbackStateName)).append('\n');
        out.append("actions: play=").append(d.actionPlayAvailable)
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
        out.append("session revalidation attempted: ")
                .append(d.sessionRevalidationAttempted).append('\n');
        out.append("session revalidation succeeded: ")
                .append(d.sessionRevalidationSucceeded).append('\n');
        out.append("relevant active package changed: ")
                .append(d.relevantActivePackageChanged).append('\n');
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

    private String focusText(AudioFocusPort.Result result) {
        if (result == null) return "-";
        return result == AudioFocusPort.Result.GRANTED ? "granted" : "denied";
    }

    private String valueOrDash(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
