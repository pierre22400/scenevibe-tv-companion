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
        // "armed != visible": the boot-prepare entry primes the runtime but never draws the
        // overlay. Every other entry (user-initiated top/bottom, cloud connect, restart with
        // null action) keeps the original behavior of showing the window immediately.
        boolean bootPrepare = ACTION_BOOT_PREPARE.equals(action);
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
            if (!bootPrepare) {
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
                        if (trackScheduler != null) trackScheduler.onPlaybackSnapshot(snapshot);
                    }
                    @Override public void onUnavailable() {
                        if (trackScheduler != null) trackScheduler.onPlaybackUnavailable();
                    }
                });
                mediaSessionProbe.start();
            }

            if (cloudClient == null && !BuildConfig.CLOUD_ORIGIN.isEmpty()) {
                cloudClient = new CloudControlClient(this, cloudTrackRepository, trackScheduler);
                cloudClient.start();
            }
            if (ACTION_CLOUD_CONNECT.equals(action) && cloudClient != null) cloudClient.activate();

            String visibility = bootPrepare ? "armed (no overlay shown)"
                    : "visible; position=" + (bottom ? "bottom" : "top");
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
            renderer = new OverlayRenderer(this, this::onPermissionLost);
        }
        renderer.show(bottomPosition);
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
        Log.i(TAG, "OverlayService destroyed");
        super.onDestroy();
    }
}
