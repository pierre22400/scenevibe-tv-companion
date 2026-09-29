package com.scenevibe.tvcompanionpoc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

/**
 * User-started foreground service that owns the Android overlay window.
 * The LAN transport can render direct commentary or load a bounded track.
 * Track timing is driven only by passive MediaSession state published by the
 * foreground streaming app; SceneVibe never sends transport controls.
 */
public final class OverlayService extends Service {
    public static final String ACTION_TOP = "com.scenevibe.tvcompanionpoc.SHOW_TOP";
    public static final String ACTION_BOTTOM = "com.scenevibe.tvcompanionpoc.SHOW_BOTTOM";
    public static final String ACTION_CLOUD_CONNECT = "com.scenevibe.tvcompanionpoc.CLOUD_CONNECT";
    /**
     * Boot-prepare (armed, not visible) entry used by {@link BootReceiver}. It prepares the
     * scheduler, cache restore, MediaSession probe and Cloud client WITHOUT creating or
     * showing the OverlayRenderer, so a reboot shows no card, no parasitic badge and no
     * stale comment. The renderer is created lazily only when a comment is actually due.
     */
    public static final String ACTION_BOOT_PREPARE = "com.scenevibe.tvcompanionpoc.BOOT_PREPARE";
    /**
     * EXCEPTIONAL "Reset SceneVibe Cloud connection" forwarded from {@link DiagnosticsActivity}
     * when the service is running. It runs the coordinated reset on the CloudControlClient's io
     * executor (so no in-flight GET/ACK rewrites the cache) and then dismisses the renderer, so
     * no stale card/comment survives a reset. It never touches the local InstallationIdentity.
     */
    public static final String ACTION_CLOUD_RESET = "com.scenevibe.tvcompanionpoc.CLOUD_RESET";
    /** Bounded observational block code: the projected media identity no longer matches. */
    static final String BLOCK_CODE_MEDIA_IDENTITY = "MEDIA_IDENTITY_BLOCKED";
    /** Bounded observational block code: no active/authorized MediaSession is available. */
    static final String BLOCK_CODE_SESSION_UNAVAILABLE = "SESSION_UNAVAILABLE";
    private static final String TAG = "SceneVibePoc";
    private static final String CHANNEL_ID = "overlay_poc";
    private static final int NOTIFICATION_ID = 1001;
    private OverlayRenderer renderer;
    private boolean foregroundReady;
    /** Last chosen overlay position, so a lazily created renderer honors the user's choice. */
    private boolean bottomPosition;
    private CommentaryServer commentaryServer;
    private MediaSessionProbe mediaSessionProbe;
    private MediaSyncedTrackScheduler trackScheduler;
    private CloudTrackRepository cloudTrackRepository;
    private CloudControlClient cloudClient;

    /** Create the notification channel and enter foreground mode promptly. */
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "OverlayService created");
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    "SceneVibe overlay test", NotificationManager.IMPORTANCE_LOW));
            Intent launch = new Intent(this, MainActivity.class);
            PendingIntent openControls = PendingIntent.getActivity(this, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("SceneVibe TV Companion POC")
                    .setContentText("Overlay and MediaSession-synced track scheduler are running.")
                    .setContentIntent(openControls)
                    .setOngoing(true)
                    .build();
            startForeground(NOTIFICATION_ID, notification);
            foregroundReady = true;
            // Observational only: record that the foreground service is up. Never gates logic.
            DiagnosticsStore.INSTANCE.setServiceRunning(true);
            Log.i(TAG, "Foreground notification started");
        } catch (RuntimeException error) {
            Log.e(TAG, "Foreground service initialization failed", error);
            stopSelf();
        }
    }

    /**
     * Show or reposition the window, or (from the boot path) arm the runtime without showing
     * anything. A system restart restores the last position for the user-initiated show path.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!foregroundReady) {
            return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission absent; stopping service");
            stopSelf();
            return START_NOT_STICKY;
        }
        String action = intent == null ? null : intent.getAction();
        // "armed != visible": in Consumer Mode (ENABLE_LAN_DEV false) EVERY entry path (Start
        // SceneVibe / ACTION_TOP, ACTION_BOTTOM, ACTION_CLOUD_CONNECT, ACTION_BOOT_PREPARE and
        // a START_STICKY restart with a null Intent) is ARM-only: it primes the scheduler,
        // cache, MediaSession probe and Cloud client but never draws the overlay. The renderer
        // is created lazily only inside the scheduler onRender callback when a comment is due.
        // In LAN DEV only, ACTION_TOP/ACTION_BOTTOM and a null-Intent restart still show the
        // window immediately (the permanent POC badge behavior), while ACTION_BOOT_PREPARE
        // stays armed-not-visible in both modes.
        boolean bootPrepare = ACTION_BOOT_PREPARE.equals(action);
        boolean showOnEntry = shouldShowOnEntry(action, BuildConfig.ENABLE_LAN_DEV);
        boolean bottom = ACTION_BOTTOM.equals(action)
                || (!ACTION_TOP.equals(action) && getSharedPreferences("overlay", Context.MODE_PRIVATE)
                        .getBoolean("bottom", false));
        bottomPosition = bottom;
        // Do not overwrite the persisted user position from the boot path.
        if (!bootPrepare) {
            getSharedPreferences("overlay", Context.MODE_PRIVATE).edit()
                    .putBoolean("bottom", bottom).apply();
        }
        try {
            if (showOnEntry) {
                showRenderer();
            }

            if (trackScheduler == null) {
                trackScheduler = new MediaSyncedTrackScheduler(new MediaSyncedTrackScheduler.Listener() {
                    @Override public void onRender(ScheduledTrack.Event event) {
                        // A comment is due: create the renderer lazily if the boot path has not
                        // shown it yet, then display the tracked commentary.
                        showRenderer();
                        if (renderer != null) renderer.showTrackedCommentary(
                                event.text, event.durationMs, event.mediaBitmap);
                    }
                    @Override public void onPlayback(boolean playing, boolean freeze) {
                        if (renderer != null) renderer.onPlayback(playing, freeze);
                    }
                    @Override public void onEligibility(boolean eligible) {
                        // Observational only: mirror the last media-identity decision into the
                        // bounded diagnostics store. This never influences the eligibility rule.
                        DiagnosticsStore.INSTANCE.setMediaIdentityState(eligible
                                ? RuntimeDiagnostics.MediaIdentityState.ELIGIBLE
                                : RuntimeDiagnostics.MediaIdentityState.BLOCKED);
                        // Observational only: record a bounded block code on the eligibility
                        // loss transition. The scheduler itself is frozen; this maps the
                        // observable transition into DiagnosticsStore and never gates logic.
                        if (!eligible) DiagnosticsStore.INSTANCE.setLastBlockCode(BLOCK_CODE_MEDIA_IDENTITY);
                        // Media no longer matches: hide the tracked card immediately. Never
                        // create a renderer just to hide nothing.
                        if (!eligible && renderer != null) renderer.onTrackEligibility(false);
                    }
                });
                cloudTrackRepository = new CloudTrackRepository(this);
                long restored = cloudTrackRepository.restore(trackScheduler);
                if (restored > 0) Log.i(TAG, "Cached cloud track restored; revision=" + restored);
            }

            // The CommentaryServer / port 8765 / LAN pairing surface is a LAN DEV tool only.
            // In the consumer runtime (ENABLE_LAN_DEV false) it is never constructed and the
            // port is never opened; Cloud + MediaSession below run unchanged.
            if (BuildConfig.ENABLE_LAN_DEV && commentaryServer == null) {
                commentaryServer = new CommentaryServer(
                        (id, text, durationMs, mediaBitmap) -> {
                            if (renderer != null) {
                                renderer.showCommentary(text, durationMs, mediaBitmap);
                            }
                        },
                        track -> {
                            if (trackScheduler != null) {
                                trackScheduler.load(track);
                            }
                        }, PairingRuntime.get(this));
                commentaryServer.start();
            }

            if (mediaSessionProbe == null) {
                mediaSessionProbe = new MediaSessionProbe(this, new MediaSessionProbe.Listener() {
                    @Override public void onSnapshot(MediaSessionProbe.Snapshot snapshot) {
                        // Observational only: record the last foreground media app package
                        // (a package name, never any content) for the diagnostics screen.
                        if (snapshot != null) {
                            DiagnosticsStore.INSTANCE.setLastObservedMediaApp(snapshot.packageName);
                        }
                        if (trackScheduler != null) trackScheduler.onPlaybackSnapshot(snapshot);
                    }
                    @Override public void onUnavailable() {
                        // Observational only: no active/authorized session. Record a bounded
                        // block code before forwarding to the frozen scheduler; never gates logic.
                        DiagnosticsStore.INSTANCE.setLastBlockCode(BLOCK_CODE_SESSION_UNAVAILABLE);
                        if (trackScheduler != null) trackScheduler.onPlaybackUnavailable();
                    }
                });
                mediaSessionProbe.start();
            }

            if (shouldReconstructCloudClient(cloudClient == null, BuildConfig.CLOUD_ORIGIN.isEmpty())) {
                // Fresh construction on first entry AND after ACTION_CLOUD_RESET nulled the
                // field: a reset client's io executor is shut down and cannot be reused, so the
                // only correct way to re-arm the cloud is a brand-new CloudControlClient here.
                cloudClient = new CloudControlClient(this, cloudTrackRepository, trackScheduler);
                cloudClient.start();
            }
            if (ACTION_CLOUD_CONNECT.equals(action) && cloudClient != null) cloudClient.activate();
            if (ACTION_CLOUD_RESET.equals(action)) {
                // Coordinated reset: the client flips running=false synchronously (so no
                // in-flight GET/ACK can rewrite the cache and no post-reset poll uses the old
                // credential) and then wipes identity/cache/scheduler/diagnostics on its io
                // executor asynchronously. reset() returns IMMEDIATELY without waiting on the
                // executor, so onStartCommand never blocks the Android main thread; a bounded
                // watchdog inside the client surfaces a diagnostic if the wipe does not finish.
                // The client shuts its io executor down as part of reset(), so it is now a
                // spent, un-armable instance (a shut-down ScheduledExecutorService cannot be
                // reused). Drop the reference so the NEXT entry (e.g. a later
                // ACTION_CLOUD_CONNECT) reconstructs a fresh client via the
                // "cloudClient == null" path above and calls start() again; without this the
                // stale client's running==false flag would make activate() a silent no-op.
                if (cloudClient != null) {
                    cloudClient.reset();
                    cloudClient = null;
                }
                if (renderer != null) {
                    renderer.dismiss();
                    renderer = null;
                }
                Log.i(TAG, "Cloud reset requested via runtime");
            }

            String visibility = showOnEntry
                    ? "visible; position=" + (bottom ? "bottom" : "top")
                    : "armed (no overlay shown)";
            if (BuildConfig.ENABLE_LAN_DEV) {
                Log.i(TAG, "Overlay " + visibility
                        + "; commentary=http://TV_IP:" + CommentaryServer.PORT + "/commentary"
                        + "; track=http://TV_IP:" + CommentaryServer.PORT + "/track"
                        + "; mediaSessionAccess=" + NotificationAccess.isGranted(this));
            } else {
                Log.i(TAG, "Overlay " + visibility
                        + "; mode=cloud-only"
                        + "; mediaSessionAccess=" + NotificationAccess.isGranted(this));
            }
            return START_STICKY;
        } catch (RuntimeException error) {
            Log.e(TAG, "WindowManager overlay failed", error);
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    /**
     * Creates the OverlayRenderer on first need and shows it at the current position. This is
     * the single lazy-creation point: the boot path never calls it, so an armed-but-idle boot
     * draws no card until a comment is actually due and the scheduler triggers a render.
     */
    private void showRenderer() {
        if (renderer == null) {
            renderer = new OverlayRenderer(this, this::onPermissionLost, BuildConfig.ENABLE_LAN_DEV);
        }
        renderer.show(bottomPosition);
    }

    /**
     * Pure, testable decision for whether an {@code onStartCommand} entry should draw the
     * overlay immediately, or merely arm the runtime. In Consumer Mode ({@code enableLanDev}
     * false) NO entry path shows the window: every path (Start SceneVibe / ACTION_TOP,
     * ACTION_BOTTOM, ACTION_CLOUD_CONNECT, ACTION_BOOT_PREPARE and a null-Intent restart) is
     * ARM-only, and the renderer is created lazily only when a comment is actually due. In LAN
     * DEV, ACTION_TOP/ACTION_BOTTOM and a null-Intent restart still show immediately, while
     * ACTION_BOOT_PREPARE stays armed-not-visible.
     */
    static boolean shouldShowOnEntry(String action, boolean enableLanDev) {
        if (!enableLanDev) return false;
        if (ACTION_BOOT_PREPARE.equals(action)) return false;
        if (ACTION_CLOUD_CONNECT.equals(action)) return false;
        // LAN DEV: ACTION_TOP, ACTION_BOTTOM, and a null-Intent START_STICKY restart show now.
        return true;
    }

    /**
     * Pure, testable decision for whether the renderer restores the permanent SceneVibe status
     * badge on comment expiry / media-identity loss (LAN DEV) or fully removes the overlay
     * window (Consumer Mode). Mirrors {@link OverlayRenderer}'s {@code permanentBadge} seam.
     */
    static boolean shouldRestoreBadgeOnExpiry(boolean enableLanDev) {
        return enableLanDev;
    }

    /**
     * Pure, testable mirror of the {@code onStartCommand} Cloud-client construction guard
     * ({@code if (cloudClient == null && !BuildConfig.CLOUD_ORIGIN.isEmpty())}). It exists to
     * lock the "restartable after reset" contract: {@code ACTION_CLOUD_RESET} tears the client
     * down and nulls the field, so the next entry MUST reconstruct a fresh client (and call
     * {@code start()}) rather than leave a spent, {@code running==false} instance behind that
     * would make {@code activate()} a silent no-op. A configured origin plus a null client field
     * therefore means "reconstruct now".
     *
     * @param cloudClientNull whether the {@code cloudClient} field is currently null
     * @param cloudOriginEmpty whether {@code BuildConfig.CLOUD_ORIGIN} is empty (cloud disabled)
     */
    static boolean shouldReconstructCloudClient(boolean cloudClientNull, boolean cloudOriginEmpty) {
        return cloudClientNull && !cloudOriginEmpty;
    }

    /** Stop if Android revokes the user's overlay capability during the test. */
    private void onPermissionLost() {
        Log.w(TAG, "Overlay permission revoked while running");
        stopSelf();
    }

    /** This service accepts no external binding or command channel. */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Remove the window and notification on every normal destruction path. */
    @Override
    public void onDestroy() {
        if (cloudClient != null) {
            cloudClient.stop();
            cloudClient = null;
        }
        if (mediaSessionProbe != null) {
            mediaSessionProbe.stop();
            mediaSessionProbe = null;
        }
        if (trackScheduler != null) {
            trackScheduler.clear();
            trackScheduler = null;
        }
        if (commentaryServer != null) {
            commentaryServer.stop();
            commentaryServer = null;
        }
        if (renderer != null) {
            renderer.dismiss();
            renderer = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        // Observational only: the service is no longer running.
        DiagnosticsStore.INSTANCE.setServiceRunning(false);
        Log.i(TAG, "OverlayService destroyed");
        super.onDestroy();
    }
}
