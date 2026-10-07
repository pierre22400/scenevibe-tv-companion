package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;

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
        note.setText("Reset deletes this TV's SceneVibe Cloud credential and installed SceneVibe content, "
                + "then creates a fresh local installation id so the TV can pair again safely. "
                + "The previous TV entry remains in your SceneVibe account until you remove it.");
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
     * Executes the EXCEPTIONAL Cloud reset: rotate the local InstallationIdentity, wipe the
     * cloud credential + cloud identity and clear the installation file. Rotation is intentional:
     * after deleting the device credential, reusing the old installation id would require proof
     * the TV no longer owns. It is only ever run from this explicit button - never automatically
     * on a network/timeout/401.
     */
    private void resetCloud() {
        if (DiagnosticsStore.INSTANCE.serviceRunning()) {
            // The service (and thus a CloudControlClient that may be polling) is up. Route the
            // reset through the runtime so it runs on the client's io executor after
            // running=false: no in-flight GET/ACK can rewrite the cache and no post-reset poll
            // uses the old credential. The service also dismisses the renderer.
            Intent reset = new Intent(this, OverlayService.class)
                    .setAction(OverlayService.ACTION_CLOUD_RESET);
            startService(reset);
        } else {
            // No running service means no concurrent client, so a direct-but-safe wipe is
            // correct: nothing can rewrite the cache after we clear it here.
            boolean cleared = resetStoppedInstallation(
                    new InstallationStore(new AndroidInstallationBackend(this)),
                    () -> new InstallationIdentity(this).rotateForCloudReset(),
                    () -> new CloudDeviceCredentials(this).reset(), DiagnosticsStore.INSTANCE);
            if (!cleared) {
                Log.w(TAG, "SceneVibe Cloud reset incomplete");
                Toast.makeText(this, "SceneVibe Cloud reset incomplete. Check diagnostics.",
                        Toast.LENGTH_LONG).show();
                refresh();
                return;
            }
        }
        Log.i(TAG, "SceneVibe Cloud connection reset by TV user");
        Toast.makeText(this, "SceneVibe Cloud reset. A fresh TV identity is ready to pair.",
                Toast.LENGTH_LONG).show();
        refresh();
    }

    /**
     * Explicit stopped-service reset only: no Cloud worker exists in this branch. Rotate the
     * separate identity, clear credentials, then clear generic/historical state in one batch.
     * A refused wipe keeps readable durable state and reports only a bounded failure.
     */
    static boolean resetStoppedInstallation(InstallationStore store, Runnable rotateIdentity,
            Runnable resetCredentials, DiagnosticsStore observed) {
        try {
            rotateIdentity.run();
            resetCredentials.run();
            boolean cleared = store.clearAll();
            observed.resetCloudObservations();
            if (!cleared) observed.setLastCloudErrorCode(RuntimeDiagnostics.CloudErrorCode.NETWORK);
            return cleared;
        } catch (RuntimeException failed) {
            observed.setLastCloudErrorCode(RuntimeDiagnostics.CloudErrorCode.NETWORK);
            return false;
        }
    }

    /** Render generic metadata first and retain labeled compatibility aliases, without any content. */
    static String render(RuntimeDiagnostics d) {
        StringBuilder sb = new StringBuilder();
        line(sb, "App version", d.appVersion);
        line(sb, "Temporal engine", "scenevibe.media-calendar.v1");
        line(sb, "Overlay service", d.serviceRunning ? "running" : "stopped");
        line(sb, "Autostart", d.autostartEnabled ? "enabled" : "disabled");
        line(sb, "Cloud", d.cloudState.name());
        line(sb, "Installation id", d.installationIdAbbreviated == null ? "-" : d.installationIdAbbreviated);
        line(sb, "Cloud device id", d.cloudDeviceIdAbbreviated == null ? "-" : d.cloudDeviceIdAbbreviated);
        line(sb, "Installed package", d.installationState == RuntimeDiagnostics.InstallationState.CORRUPT
                ? "corrupt" : d.installationPresent ? "yes" : "no");
        line(sb, "Package codec", d.packageCodecId == null ? "-" : d.packageCodecId);
        line(sb, "Package handler", d.packageHandlerId == null ? "-" : d.packageHandlerId);
        line(sb, "Installed revision", String.valueOf(d.installedRevision));
        line(sb, "Acknowledged revision", String.valueOf(d.acknowledgedRevision));
        line(sb, "Last startup restore", d.lastStartupRestoreResult == null ? "-" : d.lastStartupRestoreResult.name());
        line(sb, "Installation read failure", d.installationReadFailure.name());
        line(sb, "Cached track (compatibility)", d.cachedTrackPresent ? "yes" : "no");
        line(sb, "Cached track id (compatibility)", d.cachedTrackId == null ? "-" : d.cachedTrackId);
        line(sb, "Cached revision (compatibility)", String.valueOf(d.cachedRevision));
        line(sb, "Last acknowledged revision (compatibility)", String.valueOf(d.lastAcknowledgedRevision));
        line(sb, "MediaSession permission", d.mediaSessionPermissionGranted ? "granted" : "not granted");
        line(sb, "Last observed media app", d.lastObservedMediaApp == null ? "-" : d.lastObservedMediaApp);
        line(sb, "Media identity", d.mediaIdentityState.name());
        line(sb, "Last block code", d.lastBlockCode == null ? "-" : d.lastBlockCode);
        line(sb, "Autostart decision", d.lastAutostartDecision == null ? "-" : d.lastAutostartDecision.name());
        line(sb, "Cloud ever connected", d.hadSuccessfulCloudConnection ? "yes" : "no");
        line(sb, "Last assignment revision", String.valueOf(d.lastAssignmentRevisionReceived));
        line(sb, "Last successful ACK", String.valueOf(d.lastSuccessfulAckRevision));
        line(sb, "Last cloud error", d.lastCloudErrorCode.name());
        line(sb, "Last manifest outcome", d.lastManifestCode.name());
        line(sb, "Last scene outcome", d.lastSceneCode.name());
        line(sb, "Last WALL outcome", d.lastWallCode.name());
        line(sb, "WALL clock kind", d.wallClockKind == null ? "-" : d.wallClockKind);
        line(sb, "WALL generation", String.valueOf(d.wallGeneration));
        line(sb, "WALL window count", String.valueOf(d.wallWindowCount));
        line(sb, "WALL anchored", d.wallAnchored ? "yes" : "no");
        line(sb, "WALL wait armed", d.wallWaitArmed ? "yes" : "no");
        return sb.toString().trim();
    }

    /** Append a fixed label and an already bounded observational scalar. */
    private static void line(StringBuilder sb, String label, String value) {
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
