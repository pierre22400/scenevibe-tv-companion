package com.scenevibe.tvcompanionpoc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;

/**
 * Autostart entry point (user section 9). It listens ONLY to BOOT_COMPLETED and
 * MY_PACKAGE_REPLACED (declared in the manifest); it is deliberately NOT registered for
 * LOCKED_BOOT_COMPLETED and is NOT directBootAware, so it only ever runs after the user
 * has unlocked the device and Credential Encrypted storage (the app-private prefs holding
 * the opt-in, cache and credentials) is available.
 *
 * <p>On a matching broadcast it gathers the real Android signals, delegates the yes/no
 * decision to the pure {@link AutostartPolicy}, and then EITHER starts {@link OverlayService}
 * in the boot-prepare (armed, not visible) mode OR records a bounded diagnostic and does
 * nothing else. It never launches {@link MainActivity}, never loops, never auto-requests a
 * permission and never crashes: the whole body is wrapped so a failure degrades to a logged
 * diagnostic rather than a boot-time crash loop.
 */
public final class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "SceneVibePoc";

    /** Read durable metadata after unlock; this receiver never parses, restores or writes a package. */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        try {
            Context app = context.getApplicationContext();

            boolean autostartEnabled = AutostartPreference.isEnabled(app);
            boolean overlayGranted = Settings.canDrawOverlays(app);
            boolean mediaGranted = NotificationAccess.isGranted(app);

            // A usable Cloud credential = a durable deviceToken + cloudDeviceId that is NOT
            // in the fail-closed credential-unavailable state (FEAT-003).
            CloudDeviceCredentials credentials = new CloudDeviceCredentials(app);
            boolean hasUsableCloudCredential = !credentials.credentialUnavailable()
                    && notEmpty(credentials.deviceToken())
                    && notEmpty(credentials.cloudDeviceId());

            InstallationStore.ReadResult durable =
                    new InstallationStore(new AndroidInstallationBackend(app)).read();
            AutostartPolicy.Decision decision = decide(
                    autostartEnabled, overlayGranted, mediaGranted,
                    hasUsableCloudCredential, durable);

            // Observational only: publish the bounded decision (START or a specific skip
            // reason) into the diagnostics store so it is visible on the Diagnostics screen
            // and not only in Logcat. This never gates the decision itself.
            DiagnosticsStore.INSTANCE.setLastAutostartDecision(decision);

            if (decision == AutostartPolicy.Decision.START) {
                arm(app);
            } else {
                // Bounded, secret-free diagnostic only; no UI, no permission prompt, no retry loop.
                Log.i(TAG, "Autostart skipped on " + action + "; decision=" + decision);
            }
        } catch (RuntimeException error) {
            // Fail-closed: a boot receiver must never crash or loop. Record and return.
            Log.w(TAG, "Autostart evaluation failed; staying disarmed");
        }
    }

    /**
     * Starts the overlay service in boot-prepare mode. Uses startForegroundService so the
     * service can promptly call startForeground with its declared specialUse type, which is
     * the correct pattern for a specialUse FGS under targetSdk 35 (see FEAT-005 findings).
     * A failure here is swallowed into a diagnostic so boot never crashes.
     */
    private void arm(Context app) {
        try {
            Intent prepare = new Intent(app, OverlayService.class)
                    .setAction(OverlayService.ACTION_BOOT_PREPARE);
            app.startForegroundService(prepare);
            Log.i(TAG, "Autostart armed OverlayService in boot-prepare mode");
        } catch (RuntimeException startFailure) {
            // e.g. a platform ForegroundServiceStartNotAllowedException: surface, do not crash.
            Log.w(TAG, "Autostart could not start OverlayService from boot");
        }
    }

    /** Delegate unchanged permission/opt-in ordering; only a complete durable representation counts. */
    static AutostartPolicy.Decision decide(boolean enabled, boolean overlay, boolean media,
            boolean credential, InstallationStore.ReadResult durable) {
        return AutostartPolicy.decide(enabled, overlay, media, credential,
                durable != null && durable.state() == InstallationStore.ReadState.SNAPSHOT);
    }

    /** Usable credentials require both existing non-empty identifiers; no value is logged. */
    private static boolean notEmpty(String value) {
        return value != null && !value.isEmpty();
    }
}
