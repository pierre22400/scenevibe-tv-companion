package com.scenevibe.tvcompanionpoc.mediaexperiment;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.AudioFocusPort;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.Diagnostics;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.InterludeState;

/**
 * READ-ONLY diagnostic overlay. Neither the window nor any descendant accepts
 * Sony remote focus, DPAD events or touch events.
 *
 * <p>The previous focusable action panel plausibly captured keys on the Sony,
 * and the 2026-10-08 incident required a reboot to restore nominal behaviour.
 * All operator actions now arrive through the short-lived translucent launcher
 * using an explicitly named ADB command. There are NO interactive controls in
 * this TYPE_APPLICATION_OVERLAY window.</p>
 */
final class DiagnosticOverlayWindow {
    private final Context context;
    private final WindowManager windows;

    private ScrollView root;
    private TextView diagnosticsView;
    private TextView voiceView;
    private TextView fixtureView;
    private WindowManager.LayoutParams params;
    private boolean lastInterludeOverlayAttached;

    DiagnosticOverlayWindow(Context context) {
        this.context = context.getApplicationContext();
        this.windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    /** Attach a nonfocusable, nontouchable read-only panel over native media. */
    void show() {
        if (root != null) return;
        if (windows == null) throw new IllegalStateException("WindowManager unavailable");
        if (!Settings.canDrawOverlays(context)) {
            throw new IllegalStateException("Overlay permission not granted");
        }

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(14), dp(18), dp(14));
        content.setBackgroundColor(0xDC101418);

        TextView title = new TextView(context);
        title.setText("SceneVibe Media Spike 2.0.2");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        content.addView(title);

        TextView hint = new TextView(context);
        hint.setText("READ ONLY — remote stays with Prime. Trigger tests via ADB. "
                + "Emergency: adb shell am force-stop "
                + "com.scenevibe.tvcompanionpoc.mediaexperiment");
        hint.setTextColor(0xFFB9C2CC);
        hint.setTextSize(12);
        content.addView(hint);

        voiceView = new TextView(context);
        voiceView.setTextColor(0xFFCFE8FF);
        voiceView.setTextSize(12);
        voiceView.setText("voice coexistence: IDLE");
        content.addView(voiceView);

        fixtureView = new TextView(context);
        fixtureView.setTextColor(0xFFCFE8FF);
        fixtureView.setTextSize(12);
        fixtureView.setText("video fixture: not checked");
        content.addView(fixtureView);

        diagnosticsView = new TextView(context);
        diagnosticsView.setTextColor(0xFFCFE8FF);
        diagnosticsView.setTextSize(12);
        diagnosticsView.setText(renderDiagnostics(null, InterludeState.IDLE, false));
        content.addView(diagnosticsView);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setFocusable(false);
        scroll.setFocusableInTouchMode(false);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        scroll.addView(content);

        params = new WindowManager.LayoutParams(
                dp(540),
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.END | Gravity.TOP;
        params.setTitle("SceneVibe Media POC read-only diagnostics");
        windows.addView(scroll, params);
        root = scroll;
        lastInterludeOverlayAttached = false;
    }

    /** Refresh diagnostics, optionally re-layer this passive window over the video. */
    void update(Diagnostics diagnostics, InterludeState state, boolean accessGranted) {
        if (root == null || diagnosticsView == null) return;
        diagnosticsView.setText(renderDiagnostics(diagnostics, state, accessGranted));
        boolean attached = diagnostics != null && diagnostics.overlayAttached;
        if (attached && !lastInterludeOverlayAttached) raiseAboveInterlude();
        lastInterludeOverlayAttached = attached;
    }

    void setVoiceStatus(String status) {
        if (voiceView != null) voiceView.setText("voice coexistence: " + status);
    }

    void setFixtureStatus(String status) {
        if (fixtureView != null) fixtureView.setText("video fixture: " + status);
    }

    /** Re-layer without ever making this window focusable or touchable. */
    private void raiseAboveInterlude() {
        if (root == null || windows == null || params == null) return;
        ScrollView existing = root;
        try {
            windows.removeViewImmediate(existing);
            windows.addView(existing, params);
        } catch (RuntimeException error) {
            root = null;
            diagnosticsView = null;
            voiceView = null;
            fixtureView = null;
            params = null;
        }
    }

    /** Release the passive window without changing any native transport. */
    void remove() {
        ScrollView old = root;
        root = null;
        diagnosticsView = null;
        voiceView = null;
        fixtureView = null;
        params = null;
        lastInterludeOverlayAttached = false;
        if (old != null && windows != null) {
            try {
                windows.removeViewImmediate(old);
            } catch (IllegalArgumentException ignored) {
                // Idempotent removal.
            }
        }
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
