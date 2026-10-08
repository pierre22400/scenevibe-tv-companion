package com.scenevibe.tvcompanionpoc.mediaexperiment;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.OperatorAction;

/**
 * Translucent launcher/onboarding Activity for the isolated media-interlude POC.
 *
 * <p>The actual diagnostic controls deliberately do NOT live in this Activity.
 * Once both required grants are available, this Activity starts the foreground
 * service, asks it to show a TYPE_APPLICATION_OVERLAY control panel, and finishes
 * immediately so the native streaming Activity remains the foreground media app.</p>
 *
 * <p>This avoids the physical Sony failure observed with the original opaque
 * diagnostic Activity: Prime exposed a healthy PLAYING MediaSession until the
 * POC Activity became foreground, then getActiveSessions() became empty.</p>
 */
public final class MediaExperimentActivity extends Activity {
    private TextView overlayStatus;
    private TextView accessStatus;
    private boolean panelLaunchIssued;

    /** Build only the permission/onboarding surface. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(40), dp(28), dp(40), dp(28));
        controls.setBackgroundColor(0xDD101418);

        TextView title = new TextView(this);
        title.setText("SceneVibe Media Interlude POC");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        controls.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Permission launcher only. Once ready, read-only diagnostics "
                + "appear over native video. The remote stays with Prime; "
                + "operator tests are launched explicitly through ADB.");
        subtitle.setTextColor(0xFFB9C2CC);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(8), 0, dp(16));
        controls.addView(subtitle);

        overlayStatus = statusLabel(controls);
        accessStatus = statusLabel(controls);

        addButton(controls, "Display over other apps settings", this::openOverlaySettings);
        addButton(controls, "Media access settings", this::openNotificationAccessSettings);
        addButton(controls, "OPEN READ-ONLY DIAGNOSTICS", this::launchPanelIfReady);

        setContentView(controls);
    }

    /** Refresh grants and immediately return to the underlying media app when ready. */
    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        if (isReady()) {
            launchPanelAndFinish();
        }
    }

    /** Start the service-backed overlay only when both grants are present. */
    private void launchPanelIfReady() {
        refreshStatus();
        if (!isReady()) {
            Toast.makeText(this,
                    "Grant both Display over other apps and Media access first.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        launchPanelAndFinish();
    }

    /**
     * Show read-only observations outside the Activity stack, then immediately finish.
     * The underlying Prime/Netflix Activity becomes visible again immediately.
     */
    private void launchPanelAndFinish() {
        if (panelLaunchIssued) return;
        panelLaunchIssued = true;
        try {
            // Only explicit commands on a debuggable experimental APK may run
            // media tests. A plain launcher tap displays diagnostics only.
            OperatorAction action = OperatorAction.NONE;
            if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                action = OperatorAction.parse(
                        getIntent().getStringExtra(InterludeService.EXTRA_OPERATOR_ACTION));
            }
            InterludeService.startWithPanel(this, action);
            finish();
        } catch (RuntimeException error) {
            panelLaunchIssued = false;
            Toast.makeText(this, "Could not start diagnostic overlay.",
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Both grants are required for meaningful MediaSession/interlude qualification. */
    private boolean isReady() {
        return Settings.canDrawOverlays(this)
                && ExperimentNotificationAccess.isGranted(this);
    }

    /** Show overlay and media-access permission state. */
    private void refreshStatus() {
        if (overlayStatus != null) {
            overlayStatus.setText(Settings.canDrawOverlays(this)
                    ? "Display over other apps: Granted"
                    : "Display over other apps: Not granted");
        }
        if (accessStatus != null) {
            accessStatus.setText(ExperimentNotificationAccess.isGranted(this)
                    ? "Media access: Granted"
                    : "Media access: Not granted");
        }
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

    /** Open the package-specific overlay permission page. */
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

    /** Open notification-listener access, with a conservative settings fallback. */
    private void openNotificationAccessSettings() {
        Toast.makeText(this,
                "Enable SceneVibe media-interlude POC in Notification access.",
                Toast.LENGTH_LONG).show();
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (ActivityNotFoundException first) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
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
