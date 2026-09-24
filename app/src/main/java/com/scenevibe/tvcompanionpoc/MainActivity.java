package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * TV remote friendly controls for the user-granted overlay capability.
 * The activity owns no overlay window and can be left after starting the service.
 */
public final class MainActivity extends Activity {
    private static final String TAG = "SceneVibePoc";
    private TextView permissionStatus;

    /** Build the small, remote navigable control screen without UI libraries. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(48), dp(32), dp(48), dp(32));
        controls.setBackgroundColor(0xFF17130F);

        TextView title = new TextView(this);
        title.setText("SceneVibe\nTV Companion POC v0.2.1");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(32);
        title.setGravity(Gravity.CENTER);
        controls.addView(title);

        permissionStatus = new TextView(this);
        permissionStatus.setTextColor(0xFFFFFFFF);
        permissionStatus.setTextSize(18);
        permissionStatus.setGravity(Gravity.CENTER);
        permissionStatus.setPadding(0, dp(20), 0, dp(20));
        controls.addView(permissionStatus);

        addButton(controls, "Open overlay permission settings", this::openPermissionSettings);
        addButton(controls, "Start overlay · top right", () -> startOverlay(OverlayService.ACTION_TOP));
        addButton(controls, "Start overlay · bottom right", () -> startOverlay(OverlayService.ACTION_BOTTOM));
        addButton(controls, "Stop overlay", this::stopOverlay);

        TextView instruction = new TextView(this);
        instruction.setText("Start the overlay, then open a streaming app. "
                + "Dynamic commentary listens on TV port 8765 while the overlay service runs.");
        instruction.setTextColor(0xFFD0C9BE);
        instruction.setTextSize(16);
        instruction.setGravity(Gravity.CENTER);
        instruction.setPadding(0, dp(24), 0, 0);
        controls.addView(instruction);

        setContentView(controls);
    }

    /** Refresh authorization after returning from Android settings. */
    @Override
    protected void onResume() {
        super.onResume();
        permissionStatus.setText(Settings.canDrawOverlays(this)
                ? "Display over other apps: granted"
                : "Display over other apps: not granted. Open settings first.");
    }

    /** Make each control keyboard and TV D-pad accessible. */
    private void addButton(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(18);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(440), dp(56));
        params.topMargin = dp(8);
        parent.addView(button, params);
    }

    /** Request the system settings page; Android 11+ may show the top-level list. */
    private void openPermissionSettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException first) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (ActivityNotFoundException second) {
                Log.e(TAG, "Overlay settings unavailable on this TV", second);
                Toast.makeText(this, "Overlay settings unavailable; see README for ADB fallback.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Start the foreground service only after an explicit user action and permission check. */
    private void startOverlay(String action) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant Display over other apps first.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startForegroundService(new Intent(this, OverlayService.class).setAction(action));
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not start overlay service", error);
            Toast.makeText(this, "Service start failed; inspect SceneVibePoc in logcat.",
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Stop the service so it removes the window and its foreground notification. */
    private void stopOverlay() {
        stopService(new Intent(this, OverlayService.class));
        Toast.makeText(this, "Overlay stop requested.", Toast.LENGTH_SHORT).show();
    }

    /** Convert density independent sizes to display pixels. */
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
