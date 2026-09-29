package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Read-only Diagnostics screen (user section 10). It is reachable from MainActivity but is
 * intentionally absent from the normal parcours: it declares NO LEANBACK_LAUNCHER category
 * and is exported=false, so it is not a launcher entry and cannot be started by another app.
 *
 * <p>It renders a {@link RuntimeDiagnostics} snapshot as bounded codes / short strings and
 * hosts the EXCEPTIONAL "Reset SceneVibe Cloud connection" action - the ONLY place that
 * action lives. It never shows a secret: the installationId and cloudDeviceId are printed
 * abbreviated, and no deviceToken/activationSecret/token/pepper/URL is read or displayed.
 * The view is D-pad navigable, matching MainActivity's manual-view style.
 */
public final class DiagnosticsActivity extends Activity {
    private static final String TAG = "SceneVibePoc";
    private TextView report;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(48), dp(32), dp(48), dp(32));
        controls.setBackgroundColor(0xFF17130F);

        TextView title = new TextView(this);
        title.setText("SceneVibe Diagnostics");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        controls.addView(title);

        report = new TextView(this);
        report.setTextColor(0xFFD0C9BE);
        report.setTextSize(16);
        report.setGravity(Gravity.START);
        report.setFocusable(true);
        report.setPadding(0, dp(20), 0, dp(20));
        controls.addView(report);

        addButton(controls, "Refresh diagnostics", this::refresh);
        addButton(controls, "Reset SceneVibe Cloud connection", this::resetCloud);

        TextView note = new TextView(this);
        note.setText("Reset deletes this TV's SceneVibe Cloud credential and cached track. "
                + "The local installation id is kept. Reconnect from the main screen afterwards.");
        note.setTextColor(0xFFA89F92);
        note.setTextSize(13);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(16), 0, 0);
        controls.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(controls);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /** Rebuilds the read-only snapshot from the current stores and observational signals. */
    private void refresh() {
        RuntimeDiagnostics diagnostics = RuntimeDiagnostics.capture(this, DiagnosticsStore.INSTANCE);
        report.setText(render(diagnostics));
    }

    /**
     * Executes the EXCEPTIONAL Cloud reset: wipe the cloud credential + cloud identity and
     * clear the runtime cache, while leaving the local InstallationIdentity untouched. It is
     * only ever run from this explicit button - never automatically on a network/timeout/401.
     */
    private void resetCloud() {
        new CloudDeviceCredentials(this).reset();
        new CloudTrackRepository(this).clear();
        DiagnosticsStore.INSTANCE.resetCloudObservations();
        Log.i(TAG, "SceneVibe Cloud connection reset by TV user");
        Toast.makeText(this, "SceneVibe Cloud connection reset. Local installation id kept.",
                Toast.LENGTH_LONG).show();
        refresh();
    }

    /** Renders every section-10 field as a bounded code / short string, never a secret. */
    private String render(RuntimeDiagnostics d) {
        StringBuilder sb = new StringBuilder();
        line(sb, "App version", d.appVersion);
        line(sb, "Overlay service", d.serviceRunning ? "running" : "stopped");
        line(sb, "Autostart", d.autostartEnabled ? "enabled" : "disabled");
        line(sb, "Cloud", d.cloudState.name());
        line(sb, "Installation id", d.installationIdAbbreviated == null ? "-" : d.installationIdAbbreviated);
        line(sb, "Cloud device id", d.cloudDeviceIdAbbreviated == null ? "-" : d.cloudDeviceIdAbbreviated);
        line(sb, "Cached track", d.cachedTrackPresent ? "yes" : "no");
        line(sb, "Cached track id", d.cachedTrackId == null ? "-" : d.cachedTrackId);
        line(sb, "Cached revision", String.valueOf(d.cachedRevision));
        line(sb, "Last acknowledged revision", String.valueOf(d.lastAcknowledgedRevision));
        line(sb, "MediaSession permission", d.mediaSessionPermissionGranted ? "granted" : "not granted");
        line(sb, "Last observed media app", d.lastObservedMediaApp == null ? "-" : d.lastObservedMediaApp);
        line(sb, "Media identity", d.mediaIdentityState.name());
        line(sb, "Last block code", d.lastBlockCode == null ? "-" : d.lastBlockCode);
        line(sb, "Cloud ever connected", d.hadSuccessfulCloudConnection ? "yes" : "no");
        line(sb, "Last assignment revision", String.valueOf(d.lastAssignmentRevisionReceived));
        line(sb, "Last successful ACK", String.valueOf(d.lastSuccessfulAckRevision));
        line(sb, "Last cloud error", d.lastCloudErrorCode.name());
        return sb.toString().trim();
    }

    private void line(StringBuilder sb, String label, String value) {
        sb.append(label).append(": ").append(value).append('\n');
    }

    /** Match MainActivity's D-pad friendly buttons. */
    private Button addButton(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(18);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(440), dp(56));
        params.topMargin = dp(8);
        parent.addView(button, params);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
