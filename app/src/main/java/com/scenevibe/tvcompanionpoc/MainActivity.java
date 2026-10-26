package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.presentation.TvShellController;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

/**
 * TV remote friendly controls for the user-granted overlay capability.
 * The activity owns no overlay window and can be left after starting the service.
 */
public final class MainActivity extends Activity {
    private static final String TAG = "SceneVibePoc";
    private TextView permissionStatus;
    private TextView mediaAccessStatus;
    private TextView pairingStatus;
    private TextView cloudStatus;
    private Button autostartButton;
    private TvShellController presentation;
    private TextView textQualificationStatus;
    private Button textActivateButton,textStopButton;
    private CloudMultimediaControls cloudMultimedia;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /**
     * Periodic status refresh. In consumer mode (ENABLE_LAN_DEV false) it only refreshes
     * Cloud status; the LAN pairing status is not shown or scheduled. When ENABLE_LAN_DEV
     * is true it also refreshes the LAN pairing status exactly as before.
     */
    private final Runnable statusRefresh = new Runnable() {
        @Override public void run() {
            if (BuildConfig.TVFIRST_TEXT_SONY_QUALIFICATION) {
                refreshTextQualification();
                if (cloudMultimedia!=null) cloudMultimedia.refresh();
                if (presentation!=null) presentation.refreshRuntime();
                handler.postDelayed(this,1000L);return;
            }
            if (BuildConfig.ENABLE_LAN_DEV) {
                refreshPairing();
            }
            refreshCloud();
            if (presentation != null) presentation.refreshRuntime();
            handler.postDelayed(this, 1000L);
        }
    };

    /** Build the small, remote navigable control screen without UI libraries. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LinearLayout controls = createRuntimeControls();
        presentation = new TvShellController(this, controls, this::presentationStatus,
                this::openDiagnostics, savedInstanceState);
        setContentView(presentation.view());
    }

    /**
     * Preserve the existing actual control tree and every listener. The native shell hosts
     * this tree in Settings; merely navigating to it never starts or resets the service.
     */
    private LinearLayout createRuntimeControls() {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(48), dp(32), dp(48), dp(32));
        controls.setBackgroundColor(0xFF17130F);

        if (BuildConfig.TVFIRST_TEXT_SONY_QUALIFICATION) {
            if (BuildConfig.CLOUD_MULTIMEDIA_EXPERIMENTAL) {
                cloudMultimedia=new CloudMultimediaControls(this,this::textQualificationSource);
                controls.addView(cloudMultimedia.view());
            }
            textQualificationStatus=new TextView(this);
            textQualificationStatus.setTextColor(0xFFFFFFFF);textQualificationStatus.setTextSize(17);
            controls.addView(textQualificationStatus);
            Button activate=addButton(controls,"Activer les commentaires TEXT",this::activateTextQualification);
            textActivateButton=activate;
            activate.setTag("tvfirst009-start-text");
            Button stop=addButton(controls,"Arrêter les commentaires TEXT",() -> {
                TvFirst009TextServiceOwner.requestStop(this);refreshTextQualification();
            });
            textStopButton=stop;
            stop.setTag("tvfirst009-stop-text");
            addButton(controls,"Autorisation de superposition",this::openPermissionSettings);
            addButton(controls,"Accès aux sessions MEDIA",this::openMediaSessionAccessSettings);
            addButton(controls,"Diagnostics TEXT en lecture seule",this::openDiagnostics);
            TextView notice=new TextView(this);notice.setTextColor(0xFFD0C9BE);notice.setTextSize(15);
            notice.setText("Qualification TEXT sans publicité. Active volontairement, puis ouvre Prime Video toi-même. L’activation ne remplace pas la reconnaissance de Columbo. Aucun commentaire en cas d’identité ou de position inconnue.");
            controls.addView(notice);refreshTextQualification();return controls;
        }

        if (BuildConfig.TVFIRST_DELIVERY_ONLY) {
            permissionStatus = new TextView(this); mediaAccessStatus = new TextView(this); cloudStatus = new TextView(this);
            cloudStatus.setText("Association gérée depuis le parcours Compte. Cache uniquement.");
            permissionStatus.setText("Aucune autorisation d’overlay demandée par ce profil.");
            mediaAccessStatus.setText("MEDIA et rendu interdits.");
            controls.addView(cloudStatus); controls.addView(permissionStatus); controls.addView(mediaAccessStatus);
            addButton(controls, "Diagnostics en lecture seule", this::openDiagnostics);
            return controls;
        }

        TextView title = new TextView(this);
        title.setText("SceneVibe\nTV Companion " + BuildConfig.VERSION_NAME);
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

        mediaAccessStatus = new TextView(this);
        mediaAccessStatus.setTextColor(0xFFFFFFFF);
        mediaAccessStatus.setTextSize(18);
        mediaAccessStatus.setGravity(Gravity.CENTER);
        mediaAccessStatus.setPadding(0, 0, 0, dp(20));
        controls.addView(mediaAccessStatus);

        // LAN pairing status label is a LAN DEV surface only; absent in consumer mode.
        if (BuildConfig.ENABLE_LAN_DEV) {
            pairingStatus = new TextView(this);
            pairingStatus.setTextColor(0xFFFFFFFF);
            pairingStatus.setTextSize(18);
            pairingStatus.setGravity(Gravity.CENTER);
            controls.addView(pairingStatus);
        }

        cloudStatus = new TextView(this);
        cloudStatus.setTextColor(0xFFFFFFFF);
        cloudStatus.setTextSize(18);
        cloudStatus.setGravity(Gravity.CENTER);
        cloudStatus.setPadding(0, dp(12), 0, dp(12));
        controls.addView(cloudStatus);

        // Permission-granting entries are always present so the user can reach Granted.
        addButton(controls, "Display over other apps settings", this::openPermissionSettings);
        addButton(controls, "Media access settings", this::openMediaSessionAccessSettings);

        if (BuildConfig.ENABLE_LAN_DEV) {
            // LAN DEV keeps the prototype's placement-specific overlay controls and pairing tools.
            addButton(controls, "Start overlay · top right", () -> startOverlay(OverlayService.ACTION_TOP));
            addButton(controls, "Start overlay · bottom right", () -> startOverlay(OverlayService.ACTION_BOTTOM));
            addButton(controls, "Stop overlay", this::stopOverlay);
            addButton(controls, "Start pairing (120 seconds)", this::startPairing);
            addButton(controls, "Reset pairing", this::resetPairing);
            addButton(controls, "Connect to SceneVibe Cloud", this::connectCloud);
            addButton(controls, "Disconnect cloud", this::disconnectCloud);
            autostartButton = addButton(controls, autostartLabel(), this::toggleAutostart);
            addButton(controls, "Diagnostics", this::openDiagnostics);
        } else {
            // Consumer mode (section 15): a short, plain-language control list.
            autostartButton = addButton(controls, autostartLabel(), this::toggleAutostart);
            addButton(controls, "Start SceneVibe", () -> startOverlay(OverlayService.ACTION_TOP));
            addButton(controls, "Stop SceneVibe", this::stopOverlay);
            addButton(controls, "Connect to SceneVibe Cloud", this::connectCloud);
            addButton(controls, "Disconnect Cloud", this::disconnectCloud);
            addButton(controls, "Diagnostics", this::openDiagnostics);
        }

        TextView instruction = new TextView(this);
        instruction.setText(BuildConfig.ENABLE_LAN_DEV
                ? "Start the overlay, then open a streaming app. "
                        + "Dynamic commentary and bounded track loading listen on TV port 8765. With MediaSession access granted, loaded tracks are scheduled from the streaming app's passive playback clock."
                : "Grant both permissions, connect to SceneVibe Cloud, then start SceneVibe and open a streaming app. With media access granted, tracks follow the streaming app's playback.");
        instruction.setTextColor(0xFFD0C9BE);
        instruction.setTextSize(16);
        instruction.setGravity(Gravity.CENTER);
        instruction.setPadding(0, dp(24), 0, 0);
        controls.addView(instruction);

        return controls;
    }

    /** Read only bounded local observations; no pairing, Cloud activation or ACK is requested. */
    private TvShellController.RuntimeStatus presentationStatus() {
        if (BuildConfig.TVFIRST_TEXT_SONY_QUALIFICATION) {
            return new TvShellController.RuntimeStatus("État d’association et de cache à vérifier dans le parcours Compte existant.",
                    "Superposition : "+Settings.canDrawOverlays(this)+" · accès MEDIA : "+NotificationAccess.isGranted(this),
                    TvFirst009TextServiceOwner.describe(),"SceneVibe TV · "+BuildConfig.VERSION_NAME);
        }
        if (BuildConfig.TVFIRST_DELIVERY_ONLY) return new TvShellController.RuntimeStatus(
                "Compte vérifié par le parcours d’association uniquement.", "Aucun accès MEDIA ni overlay demandé.",
                "Rendu interdit · service désactivé dans cette variante", "SceneVibe TV · " + BuildConfig.VERSION_NAME);
        RuntimeDiagnostics status = RuntimeDiagnostics.capture(this, DiagnosticsStore.INSTANCE);
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        return new TvShellController.RuntimeStatus(cloudStatus.getText().toString(),
                permissionStatus.getText() + "\n" + mediaAccessStatus.getText(),
                "Overlay service: " + (status.serviceRunning ? "running" : "stopped"),
                getString(R.string.tv_version_device, android.os.Build.MODEL,
                        metrics.widthPixels, metrics.heightPixels));
    }

    /** Save only UI route/focus/scroll memory; all runtime persistence stays service-owned. */
    @Override protected void onSaveInstanceState(Bundle outState) {
        if (presentation != null) presentation.save(outState);
        super.onSaveInstanceState(outState);
    }

    /** Pop one native presentation visit, then let Android leave the launcher root normally. */
    @Override public void onBackPressed() {
        if (presentation == null || !presentation.back()) super.onBackPressed();
    }

    /** Refresh authorization after returning from Android settings. */
    @Override
    protected void onResume() {
        super.onResume();
        if (BuildConfig.TVFIRST_TEXT_SONY_QUALIFICATION) {
            handler.removeCallbacks(statusRefresh);
            if (presentation!=null) presentation.resume();
            statusRefresh.run();return;
        }
        if (BuildConfig.TVFIRST_DELIVERY_ONLY) {
            handler.removeCallbacks(statusRefresh);
            if (presentation != null) presentation.resume();
            return;
        }
        permissionStatus.setText(Settings.canDrawOverlays(this)
                ? "Display over other apps: Granted"
                : "Display over other apps: Not granted");
        mediaAccessStatus.setText(NotificationAccess.isGranted(this)
                ? "Media access: Granted"
                : "Media access: Not granted");
        handler.removeCallbacks(statusRefresh);
        statusRefresh.run();
        if (presentation != null) presentation.resume();
    }

    /** Pause only UI refresh and remember focus; the foreground service keeps its lifetime. */
    @Override protected void onPause() {
        if (presentation != null) presentation.pause();
        handler.removeCallbacks(statusRefresh);
        super.onPause();
    }

    /** Release Activity-owned 008 work; historical service lifetime remains independent. */
    @Override protected void onDestroy() {
        if (presentation != null) presentation.close();
        super.onDestroy();
    }

    /** Reuse the shell's existing real 008 source; never construct a parallel identity or Cloud client. */
    private TvFirst008Source textQualificationSource() {
        if (!BuildConfig.TVFIRST_TEXT_SONY_QUALIFICATION || presentation==null) return null;
        Object source=presentation.sourceForLocalQualification();
        return source instanceof TvFirst008Source?(TvFirst008Source)source:null;
    }

    /** Only this explicit D-pad button action can mint a one-use in-memory service request. */
    private void activateTextQualification() {
        TvFirst009TextServiceOwner.requestStart(this,textQualificationSource());
        refreshTextQualification();
    }

    /** Refresh bounded text without reading historical credentials or automatically starting anything. */
    private void refreshTextQualification() {
        if (textQualificationStatus==null) return;
        TvFirst008Source source=textQualificationSource();
        if (source!=null && source.accountStage()==com.scenevibe.tvcompanionpoc.presentation.TvAccountSource.AccountStage.ERROR
                && TvFirst009TextServiceOwner.status().active) TvFirst009TextServiceOwner.requestStop(this);
        textQualificationStatus.setText(TvFirst009TextServiceOwner.describe()
                +"\nSuperposition : "+Settings.canDrawOverlays(this)+" · accès MEDIA : "+NotificationAccess.isGranted(this));
        boolean busy=TvFirst009TextServiceOwner.controlBusy();
        textActivateButton.setEnabled(!busy);
        textStopButton.setEnabled(busy);
    }

    private void startPairing() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant overlay permission and start overlay first.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!startOverlay(null)) return;
        PairingRuntime.get(this).start();
        refreshPairing();
        Log.i(TAG, "Pairing window opened by TV user");
    }

    private void resetPairing() {
        PairingRuntime.get(this).reset();
        refreshPairing();
        Log.i(TAG, "Pairing reset by TV user");
    }

    private void refreshPairing() {
        PairingPolicy pairing = PairingRuntime.get(this);
        PairingPolicy.Status status = pairing.status();
        String label = status == PairingPolicy.Status.PAIRING_OPEN ? "Pairing open"
                : status == PairingPolicy.Status.PAIRED ? "Paired" : "Not paired";
        String address = lanIpv4();
        pairingStatus.setText("Pairing: " + label
                + (status == PairingPolicy.Status.PAIRING_OPEN
                        ? " · " + ((pairing.remainingMs() + 999) / 1000) + "s · Code: "
                                + pairing.codeForTv() : "")
                + "\nTV IPv4: " + (address == null ? "unavailable" : address)
                + " · Port: 8765");
    }

    /**
     * Consumer-safe Cloud status. It surfaces only the human-facing userCode and the
     * connected/offline flags; it never reveals an IP, port, HTTP route, revision,
     * activationId or deviceToken. During activation it shows the 6-digit code and a
     * "Waiting for connection..." line; once connected it shows "Connected"; an
     * offline/cached state is described without any network detail.
     */
    private void refreshCloud() {
        if (BuildConfig.CLOUD_ORIGIN.isEmpty()) {
            cloudStatus.setText("SceneVibe Cloud: Not configured");
            return;
        }
        CloudDeviceCredentials identity = new CloudDeviceCredentials(this);
        InstallationStore.ReadResult durable =
                new InstallationStore(new AndroidInstallationBackend(this)).read();
        cloudStatus.setText("SceneVibe Cloud: " + cloudStatus(identity.userCode(),
                identity.offline(), identity.connected(), durable));
    }

    /** Consumer wording uses structural saved-content presence, never a revision, codec or artifact. */
    static String cloudStatus(String code, boolean offline, boolean connected,
            InstallationStore.ReadResult durable) {
        String status;
        if (code != null) {
            status = "Code: " + code + "\nWaiting for connection...";
        } else if (offline) {
            status = durable != null && durable.state() == InstallationStore.ReadState.SNAPSHOT
                    ? "Offline (using saved SceneVibe content)" : "Offline";
        } else if (connected) {
            status = "Connected";
        } else {
            status = "Not connected";
        }
        return status;
    }

    /** Starts a new activation on the already user-started foreground service. */
    private void connectCloud() {
        if (BuildConfig.CLOUD_ORIGIN.isEmpty()) {
            Toast.makeText(this,"Build with SCENEVIBE_CLOUD_ORIGIN first.",Toast.LENGTH_LONG).show();
            return;
        }
        startOverlay(OverlayService.ACTION_CLOUD_CONNECT);
    }

    /**
     * Disconnect Cloud (normal UI): stops local cloud use and clears the activation
     * temporaries but KEEPS the durable deviceToken + cloudDeviceId, the InstallationIdentity
     * and the FinalTrack cache, so a later reconnect reuses the credential. The exceptional
     * Reset (which deletes those) lives only in DiagnosticsActivity.
     */
    private void disconnectCloud() {
        new CloudDeviceCredentials(this).disconnect();
        refreshCloud();
    }

    /** Opens the read-only Diagnostics screen; it is off the normal path (not a launcher entry). */
    private void openDiagnostics() {
        startActivity(new Intent(this, DiagnosticsActivity.class));
    }

    private String lanIpv4() {
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback()) continue;
                for (java.net.InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception error) {
            Log.w(TAG, "TV IPv4 discovery unavailable", error);
        }
        return null;
    }

    /**
     * Toggles the "Start SceneVibe with TV" opt-in. This only records the user's choice; it
     * never starts the service now. On the next boot the BootReceiver consults AutostartPolicy
     * and arms the overlay only if the opt-in is on and all runtime preconditions hold.
     */
    private void toggleAutostart() {
        boolean next = !AutostartPreference.isEnabled(this);
        AutostartPreference.setEnabled(this, next);
        if (autostartButton != null) autostartButton.setText(autostartLabel());
        Toast.makeText(this, next
                ? "SceneVibe will arm on TV start (when permissions and a track or credential are present)."
                : "SceneVibe will not start automatically with the TV.",
                Toast.LENGTH_LONG).show();
    }

    /** Reflects the persisted opt-in state on the toggle button label. */
    private String autostartLabel() {
        return AutostartPreference.isEnabled(this)
                ? "Start SceneVibe with TV: On"
                : "Start SceneVibe with TV: Off";
    }

    /** Make each control keyboard and TV D-pad accessible. */
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

    /**
     * Open the top-level Apps settings and let the user navigate to:
     * Special app access -> Notification access -> SceneVibe.
     *
     * On the physically tested Sony Bravia, launching NotificationAccessActivity
     * directly left D-pad focus trapped on the outer settings container, while
     * entering through Apps settings preserved normal remote navigation.
     */
    private void openMediaSessionAccessSettings() {
        Toast.makeText(this,
                "In Settings: Special app access -> Notification access -> SceneVibe.",
                Toast.LENGTH_LONG).show();
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
        } catch (ActivityNotFoundException first) {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (ActivityNotFoundException second) {
                Log.e(TAG, "MediaSession access settings unavailable on this TV", second);
                Toast.makeText(this,
                        "MediaSession access settings unavailable on this TV.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Start the foreground service only after an explicit user action and permission check. */
    private boolean startOverlay(String action) {
        if (BuildConfig.TVFIRST_DELIVERY_ONLY) return false;
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant Display over other apps first.", Toast.LENGTH_LONG).show();
            return false;
        }
        try {
            startForegroundService(new Intent(this, OverlayService.class).setAction(action));
            return true;
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not start overlay service", error);
            Toast.makeText(this, "Service start failed; inspect SceneVibePoc in logcat.",
                    Toast.LENGTH_LONG).show();
            return false;
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
