package com.scenevibe.tvcompanionpoc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
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
     * executor (so no in-flight GET/ACK rewrites the cache), rotates the local installation
     * identity for safe re-pairing, and then dismisses the renderer so no stale card/comment
     * survives a reset.
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
    /**
     * 0.10A generic scene path (Case B). The {@link SceneRenderer} draws manifested Video
     * scenes and the {@link SceneRuntimeController} is the regie that decides WHEN. Both are
     * created lazily beside the existing scheduler and never run simultaneously with the legacy
     * {@link OverlayRenderer} for the same comment. The revision whose manifest the regie
     * currently holds is tracked so onRender can pick Case A vs Case B deterministically.
     */
    private SceneRenderer sceneRenderer;
    private SceneRuntimeController sceneController;
    /** The active revision currently driving the scheduler (0 when none restored/installed). */
    private long activeRevision;

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
                // Regie (Case B) created beside the legacy path. It draws nothing itself; the
                // SceneSink below delegates to a lazily created SceneRenderer and is the ONLY
                // bridge between the Android-free controller and the overlay window.
                sceneController = new SceneRuntimeController(sceneSink);
                trackScheduler = new MediaSyncedTrackScheduler(new MediaSyncedTrackScheduler.Listener() {
                    @Override public void onRender(ScheduledTrack.Event event) {
                        // Exactly one visual path per comment (section 10/30). When the regie
                        // holds a valid manifest for the active revision this is Case B: forward
                        // the due event to the controller (which drives SceneRenderer) and do
                        // NOT touch the legacy OverlayRenderer. Otherwise Case A is unchanged.
                        if (isSceneRendererActive()) {
                            sceneController.onCommentDue(event);
                            return;
                        }
                        // Case A: create the renderer lazily if the boot path has not shown it
                        // yet, then display the tracked commentary (legacy behavior unchanged).
                        showRenderer();
                        if (renderer != null) renderer.showTrackedCommentary(
                                event.text, event.durationMs, event.mediaBitmap);
                    }
                    @Override public void onPlayback(boolean playing, boolean freeze) {
                        // Forward playback to whichever path is active; the controller records
                        // the state without adding a second clock, the legacy renderer freezes.
                        if (isSceneRendererActive()) {
                            if (sceneController != null) sceneController.onPlayback(playing, freeze);
                        } else if (renderer != null) {
                            renderer.onPlayback(playing, freeze);
                        }
                    }
                    @Override public void onExpire(ScheduledTrack.Event event) {
                        // Case B ONLY: the scheduler (sole temporal authority) signals that a
                        // scene's media-time window elapsed; the regie hides it under the
                        // generation guard, so a stale/late expire from a superseded revision is
                        // a no-op and never resurrects or wrongly hides a new-revision scene.
                        // Case A is untouched: the legacy OverlayRenderer self-expires via its
                        // own freeze-aware countdown, so onExpire is ignored for it.
                        if (isSceneRendererActive() && sceneController != null) {
                            sceneController.onCommentExpired(event);
                        }
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
                        // Media no longer matches: hide the active path immediately, never both.
                        // The controller always receives the transition so a loaded manifest is
                        // disarmed; the legacy renderer hides only when it owns the comment.
                        if (sceneController != null) sceneController.onEligibility(eligible);
                        if (!eligible && !isSceneRendererActive() && renderer != null) {
                            renderer.onTrackEligibility(false);
                        }
                    }
                });
                cloudTrackRepository = new CloudTrackRepository(this);
                // Restart recovery (section 8): prefer the manifested restore so a revision that
                // has a durable valid manifest re-arms the regie armed-not-visible. A revision
                // with no manifest returns a failed result WITHOUT clearing, so the legacy
                // restore() below stays the Case A authority and nothing is lost.
                CloudTrackRepository.RestoreResult manifested =
                        cloudTrackRepository.restoreWithManifest(trackScheduler);
                if (manifested.ok) {
                    activeRevision = manifested.revision;
                    sceneController.loadManifest(manifested.revision, manifested.manifest);
                    Log.i(TAG, "Cached manifested revision restored; revision=" + manifested.revision);
                } else {
                    long restored = cloudTrackRepository.restore(trackScheduler);
                    if (restored > 0) {
                        activeRevision = restored;
                        Log.i(TAG, "Cached cloud track restored; revision=" + restored);
                    }
                }
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
                cloudClient = new CloudControlClient(this, cloudTrackRepository, trackScheduler,
                        manifestInstaller);
                cloudClient.start();
            }
            if (ACTION_CLOUD_CONNECT.equals(action) && cloudClient != null) cloudClient.activate();
            if (ACTION_CLOUD_RESET.equals(action)) {
                // Coordinated reset: the client flips running=false synchronously, which blocks
                // NEW Cloud work and future poll scheduling. A request already executing may
                // finish before the wipe, but because both share the same single-thread executor
                // the wipe runs after it and removes any state written before reset completion.
                // reset() returns IMMEDIATELY without waiting, so onStartCommand never blocks
                // the Android main thread; a bounded watchdog surfaces incomplete reset work.
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
                // The reset wipe clears the cache + scheduler; disarm the regie and drop any
                // visible scene too, so no stale manifested scene survives a reset.
                if (sceneController != null) sceneController.unload();
                if (sceneRenderer != null) sceneRenderer.dismissNow();
                activeRevision = 0;
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
     * Local-only asset seam for {@link SceneRenderer} (section 15). The 0.10A POC ships NO
     * Cloud asset transport: there is no download manager, CDN, HTTP prefetch or network path
     * here. Until a trusted local asset cache exists this resolver returns null for every
     * reference, so a Video scene that needs an image fails bounded in the regie's preflight
     * (no crash, no partial render) rather than reaching for the network.
     */
    private Bitmap resolveLocalAsset(String assetRef) {
        return null;
    }

    /**
     * Lazily creates the {@link SceneRenderer} on first Case B show, mirroring the lazy
     * creation of the legacy OverlayRenderer so an armed-but-idle service draws nothing until a
     * manifested comment is actually due.
     */
    private SceneRenderer ensureSceneRenderer() {
        if (sceneRenderer == null) {
            sceneRenderer = new SceneRenderer(this, this::resolveLocalAsset, this::onPermissionLost);
        }
        return sceneRenderer;
    }

    /**
     * True when the regie currently holds a valid manifest for the active revision (Case B).
     * This is the single deterministic switch the scheduler Listener consults so Case A (legacy
     * OverlayRenderer) and Case B (regie + SceneRenderer) are never both visible for one comment.
     */
    private boolean isSceneRendererActive() {
        return sceneController != null && sceneController.isSceneRendererActiveFor(activeRevision);
    }

    /**
     * Bridge from the Android-free {@link SceneRuntimeController} to the overlay window. The
     * controller preflights before each show and suppresses a show whose local assets are
     * missing, so a half-rendered scene is never displayed. All methods are null-safe against a
     * service that has begun tearing down.
     */
    private final SceneRuntimeController.SceneSink sceneSink =
            new SceneRuntimeController.SceneSink() {
        @Override public boolean preflight(OverlayManifest.Scene scene) {
            boolean ok = ensureSceneRenderer().preflight(scene);
            if (!ok) {
                // Bounded diagnostic only: a required local asset did not resolve. A missing
                // asset is a bounded SCENE failure, NOT a manifest inconsistency (section 19),
                // so it records the dedicated SCENE_ASSET_UNAVAILABLE code. Never log the
                // scene/comment content or the manifest JSON.
                DiagnosticsStore.INSTANCE.setLastSceneCode(
                        RuntimeDiagnostics.SceneCode.SCENE_ASSET_UNAVAILABLE);
            }
            return ok;
        }
        @Override public void show(OverlayManifest.Scene scene) {
            ensureSceneRenderer().render(scene);
        }
        @Override public void hide(OverlayManifest.Scene scene) {
            if (sceneRenderer != null) sceneRenderer.dismiss(scene);
        }
        @Override public void hideAll() {
            if (sceneRenderer != null) sceneRenderer.dismissNow();
        }
    };

    /**
     * Manifested-install seam supplied to {@link CloudControlClient} (section 7). The client
     * only moves bytes; this installer owns the regie-accept half of the ACK invariant. It
     * installs runtimeTrack + manifest ATOMICALLY and cross-contract-validated via the FEAT-002
     * repository (which runs {@link VideoOverlayManifestBridge}, persists durably and loads the
     * scheduler), then arms the regie by loading the manifest. It returns true ONLY when the
     * whole chain succeeded; any failure leaves the prior cache intact, records a bounded
     * diagnostic and returns false so the client does NOT ACK. No comment/scene content or
     * secret is ever logged here.
     */
    private final CloudControlClient.ManifestInstaller manifestInstaller =
            new CloudControlClient.ManifestInstaller() {
        @Override public boolean install(long revision, String runtimeJson, String manifestJson) {
            if (cloudTrackRepository == null || trackScheduler == null) return false;
            long armedRevision = installManifestedRevision(cloudTrackRepository, trackScheduler,
                    sceneController, DiagnosticsStore.INSTANCE, revision, runtimeJson, manifestJson);
            if (armedRevision <= 0) return false;
            activeRevision = armedRevision;
            return true;
        }
        @Override public boolean confirmArmed(long revision) {
            if (cloudTrackRepository == null || trackScheduler == null) return false;
            long armedRevision = confirmManifestedRevisionArmed(cloudTrackRepository,
                    trackScheduler, sceneController, revision);
            if (armedRevision <= 0) return false;
            activeRevision = armedRevision;
            return true;
        }
        @Override public boolean activateLegacy(long revision) {
            long legacyRevision=activateLegacyRevision(sceneController,revision);
            if(legacyRevision<=0)return false;
            // unload() asks the sink to hide a visible manifested scene. Force immediate window
            // removal as well so a bounded exit fade can never overlap the first legacy card.
            if(sceneRenderer!=null)sceneRenderer.dismissNow();
            activeRevision=legacyRevision;
            return true;
        }
    };

    /**
     * Android-free, unit-testable core of the manifested-install ACK decision (section 7). It
     * installs runtimeTrack + manifest ATOMICALLY and cross-contract-validated via the FEAT-002
     * repository (which runs {@link VideoOverlayManifestBridge}, persists durably and loads the
     * scheduler), records the bounded outcome into {@code diagnostics}, then arms the regie by
     * replacing the controller's active revision from the just-committed durable copy. It
     * returns the armed revision (&gt; 0) ONLY when the whole durable install + scheduler accept
     * + regie accept chain succeeded; it returns 0 on ANY failure, in which case the prior cache
     * is left intact and the client must NOT ACK. No comment/scene content or secret is logged.
     *
     * @return the armed revision on full success, or 0 when the install/arm chain failed
     */
    static long installManifestedRevision(CloudTrackRepository repository,
            MediaSyncedTrackScheduler scheduler, SceneRuntimeController controller,
            DiagnosticsStore diagnostics, long revision, String runtimeJson, String manifestJson) {
        CloudTrackRepository.InstallResult result =
                repository.install(revision, runtimeJson, manifestJson, scheduler);
        // Observational only: record the bounded install outcome, never any content.
        diagnostics.setLastManifestCode(result.code);
        if (!result.ok) return 0;
        // Durable install + scheduler accept succeeded; now arm the regie from the just-committed
        // durable copy so the bridge stays the sole owner of the cross-contract rules (it already
        // ran inside install()).
        CloudTrackRepository.RestoreResult armed = repository.restoreWithManifest(scheduler);
        if (!armed.ok) return 0;
        if (controller != null) controller.replaceRevision(armed.revision, armed.manifest);
        return armed.revision;
    }

    /**
     * Android-free, unit-testable core of the re-delivered manifested revision re-arm
     * (section 7/14). On a manifested assignment at {@code revision <= cached} the durable
     * install already happened, so this re-arms the regie DEFENSIVELY WITHOUT re-persisting: if
     * the controller already holds a manifest for the cached revision it is a no-op success; if
     * not (e.g. the service armed a legacy restore, or a prior arm was lost) it re-reads the
     * just-cached durable manifested copy via {@link CloudTrackRepository#restoreWithManifest}
     * and arms the regie from it. It returns the armed revision (&gt; 0) only when the regie
     * holds a manifest for the cached revision afterwards, and 0 when the cache has no durable
     * manifest for it (the manifested re-delivery must then fail closed, no ACK). It never
     * lowers the active revision and never logs content.
     *
     * @return the armed (cached) revision on success, or 0 when no durable manifest is armable
     */
    static long confirmManifestedRevisionArmed(CloudTrackRepository repository,
            MediaSyncedTrackScheduler scheduler, SceneRuntimeController controller, long revision) {
        if (controller == null) return 0;
        // Already armed for the cached revision => benign idempotent re-ACK, nothing to do.
        if (controller.hasActiveManifest() && controller.activeRevision() == revision) {
            return revision;
        }
        // Not armed for this revision yet: re-read the durable manifested copy and arm from it,
        // without re-persisting. A revision with no durable manifest fails closed.
        CloudTrackRepository.RestoreResult armed = repository.restoreWithManifest(scheduler);
        if (!armed.ok || armed.revision != revision) return 0;
        controller.replaceRevision(armed.revision, armed.manifest);
        return armed.revision;
    }

    /**
     * Android-free/testable Case-B -> Case-A transition. Once a newer legacy runtime revision
     * is already durable and accepted by the scheduler, disarm any loaded OverlayManifest
     * BEFORE that legacy revision can ACK. Returning the revision lets the Android service
     * update its visual-owner revision without coupling CloudControlClient to graphics state.
     */
    static long activateLegacyRevision(SceneRuntimeController controller,long revision) {
        if(controller==null||revision<1)return 0;
        controller.unload();
        return revision;
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
        // Case B teardown: unload the regie first so a late scheduler callback is ignored
        // (FEAT-003 generation guard), then drop any visible scene and release the window.
        if (sceneController != null) {
            sceneController.unload();
            sceneController = null;
        }
        if (sceneRenderer != null) {
            sceneRenderer.dismissNow();
            sceneRenderer = null;
        }
        activeRevision = 0;
        stopForeground(STOP_FOREGROUND_REMOVE);
        // Observational only: the service is no longer running.
        DiagnosticsStore.INSTANCE.setServiceRunning(false);
        Log.i(TAG, "OverlayService destroyed");
        super.onDestroy();
    }
}
