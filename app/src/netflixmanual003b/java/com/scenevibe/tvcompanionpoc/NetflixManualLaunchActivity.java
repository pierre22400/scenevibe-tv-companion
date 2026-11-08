package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Experimental TV entry point.
 * A focused, user-triggered button arms the local service from Android TV Home.
 * The service reports when the local track is ready for the existing MUTE shortcut.
 */
public final class NetflixManualLaunchActivity extends Activity {
    private final Runnable sessionChanged = this::showSession;
    private TextView status;
    private Button start;
    private Button stop;

    /** Present explicit remote-friendly actions without starting another player. */
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int inset = Math.round(32 * getResources().getDisplayMetrics().density);
        page.setPadding(inset, inset, inset, inset);

        status = new TextView(this);
        status.setTextSize(22);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        page.addView(status);

        start = new Button(this);
        start.setText("Préparer SceneVibe");
        start.setOnClickListener(view -> prepare());
        page.addView(start);

        stop = new Button(this);
        stop.setText("Arrêter la session");
        stop.setOnClickListener(view -> NetflixManualSyncService.requestStopFromLauncher());
        page.addView(stop);

        setContentView(page);
        start.requestFocus();
    }

    /** Read the service status when entering or recreating the visible launcher. */
    @Override protected void onStart() {
        super.onStart();
        NetflixManualSyncService.observeSession(sessionChanged);
        showSession();
    }

    /** Refresh permissions after returning from Android settings. */
    @Override protected void onResume() {
        super.onResume();
        showSession();
    }

    /** Detach the view without stopping the service or its editorial clock. */
    @Override protected void onStop() {
        NetflixManualSyncService.removeSessionObserver(sessionChanged);
        super.onStop();
    }

    /** Request overlay permission when necessary, otherwise arm from the visible activity. */
    private void prepare() {
        if (!Settings.canDrawOverlays(this)) {
            status.setText("Autorise la superposition dans les réglages TV, puis relance la préparation.");
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException unavailable) {
                status.setText("Ouvre les réglages TV pour autoriser la superposition SceneVibe.");
            }
            return;
        }
        start.setEnabled(false);
        status.setText("Préparation en cours…");
        try {
            startForegroundService(new Intent(this, NetflixManualSyncService.class).setAction("ARM"));
        } catch (IllegalStateException | SecurityException unavailable) {
            status.setText("Impossible de démarrer la préparation. Réessaie depuis cet écran.");
            start.setEnabled(true);
            start.requestFocus();
        }
    }

    /** Display the real admission result and keep an enabled action reachable by D-pad. */
    private void showSession() {
        NetflixManualSyncService.LaunchStatus session = NetflixManualSyncService.launchStatus();
        boolean active = session == NetflixManualSyncService.LaunchStatus.PREPARING
                || session == NetflixManualSyncService.LaunchStatus.READY;
        boolean permissionMissing = active && !Settings.canDrawOverlays(this);
        status.setText(permissionMissing
                ? "La superposition est désactivée. Autorise-la pour utiliser le panneau."
                : switch (session) {
                    case IDLE -> "Session non préparée · piste de test, 32 min.";
                    case PREPARING -> "Préparation en cours…";
                    case READY -> "SceneVibe est prêt. Ouvre ton film, puis maintiens MUTE pour afficher les commandes.";
                    case FAILED -> "Échec de préparation de la piste locale. Tu peux réessayer.";
                    case STOPPED -> "Session arrêtée. Prépare SceneVibe pour recommencer.";
                });
        start.setEnabled(!active || permissionMissing);
        stop.setEnabled(active);
        if (getCurrentFocus() == null || !getCurrentFocus().isEnabled()) {
            (start.isEnabled() ? start : stop).requestFocus();
        }
    }
}
