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
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import com.scenevibe.tvcompanionpoc.calendar.MediaCalendarScheduler;
import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallationHandler;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.PackageInstaller;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;

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
    private MediaCalendarScheduler trackScheduler;
    /** Publish the owner's current instance for Cloud io's pre-ACK lifetime rechecks. */
    private volatile CloudControlClient cloudClient;
    /** One service-owned generic stack serializes every live assignment through the owner gate. */
    private InstallationStore installationStore;
    private PackageInstaller packageInstaller;
    private LiveVideoRuntimePorts videoRuntimePorts;
    /** A fresh client cannot reuse the old identity or race the asynchronous reset wipe. */
    private boolean cloudResetPending;
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
                trackScheduler = new MediaCalendarScheduler(new MediaCalendarScheduler.Sink() {
                    /** Route only callbacks bearing the immutable active activation token. */
                    @Override public void onDue(String token,String eventId) {
                        if(videoRuntimePorts!=null)videoRuntimePorts.onDue(token,eventId);
                    }
                    /** Legacy display countdown remains freeze-aware and owns its own visual expiry. */
                    @Override public void onExpire(String token,String eventId) {
                        if(videoRuntimePorts!=null)videoRuntimePorts.onExpire(token,eventId);
                    }
                    /** Preserve every passive playback callback without acquiring a second clock. */
                    @Override public void onPlayback(String token,boolean playing,boolean freeze) {
                        if(videoRuntimePorts!=null)videoRuntimePorts.onPlayback(token,playing,freeze);
                    }
                    /** Preserve old-binding load invalidation before any new activation becomes active. */
                    @Override public void onEligibility(String token,boolean eligible) {
                        if(videoRuntimePorts!=null)videoRuntimePorts.onEligibility(token,eligible);
                    }
                });
                installationStore = new InstallationStore(new AndroidInstallationBackend(this));
                packageInstaller = new PackageInstaller(installationStore,
                        VideoInstallationHandlers.registry(), TvCapabilities.current());
                videoRuntimePorts = new LiveVideoRuntimePorts(
                        () -> Looper.myLooper() == Looper.getMainLooper(),
                        () -> trackScheduler, () -> sceneController,
                        () -> { if (renderer != null) renderer.dismiss(); },
                        () -> { if (sceneRenderer != null) sceneRenderer.dismissNow(); },
                        revision -> activeRevision = revision,
                        new LiveVideoRuntimePorts.LegacySink() {
                            /** Create the legacy window lazily using the exact prepared payload. */
                            @Override public void due(ScheduledTrack.Event event) {
                                showRenderer();
                                if(renderer!=null)renderer.showTrackedCommentary(event.text,event.durationMs,event.mediaBitmap);
                            }
                            /** Forward repetitions to the existing freeze-aware visual countdown. */
                            @Override public void playback(boolean playing,boolean freeze) {
                                if(renderer!=null)renderer.onPlayback(playing, freeze);
                            }
                            /** Eligibility loss hides only the legacy owner, without constructing a window. */
                            @Override public void eligibility(boolean eligible) {
                                if(renderer!=null)renderer.onTrackEligibility(false);
                            }
                        }, eligible -> {
                            // Observational only: diagnostics precede controller/native retirement.
                            DiagnosticsStore.INSTANCE.setMediaIdentityState(eligible
                                    ?RuntimeDiagnostics.MediaIdentityState.ELIGIBLE:RuntimeDiagnostics.MediaIdentityState.BLOCKED);
                            if(!eligible)DiagnosticsStore.INSTANCE.setLastBlockCode(BLOCK_CODE_MEDIA_IDENTITY);
                        });
                // Complete durable restoration on main before either probe events or Cloud polling.
                // Only the installer's same-revision path resolves/validates the durable handler.
                InstallationStatus restored = restoreInstalledPackage(installationStore,
                        packageInstaller, videoRuntimePorts, DiagnosticsStore.INSTANCE);
                Log.i(TAG, "Installation startup restore=" + (restored == null ? "EMPTY" : restored.name()));
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
                                videoRuntimePorts.retireManifestedVisualOwner();
                                videoRuntimePorts.retireLegacyVisualOwner();
                                if(videoRuntimePorts.loadPreparedVideo(VideoPreparedState.lan(track))) {
                                    if(!videoRuntimePorts.selectLanActivation())videoRuntimePorts.abortActivation();
                                } else videoRuntimePorts.abortActivation();
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
                        if (videoRuntimePorts != null) videoRuntimePorts.onSnapshot(snapshot);
                    }
                    @Override public void onUnavailable() {
                        // Observational only: no active/authorized session. Record a bounded
                        // block code before forwarding to the frozen scheduler; never gates logic.
                        DiagnosticsStore.INSTANCE.setLastBlockCode(BLOCK_CODE_SESSION_UNAVAILABLE);
                        if (trackScheduler != null) trackScheduler.onUnavailable();
                    }
                });
                mediaSessionProbe.start();
            }

            if (!cloudResetPending && shouldReconstructCloudClient(cloudClient == null, BuildConfig.CLOUD_ORIGIN.isEmpty())) {
                // Fresh construction on first entry AND after ACTION_CLOUD_RESET nulled the
                // field: a reset client's io executor is shut down and cannot be reused, so the
                // only correct way to re-arm the cloud is a brand-new CloudControlClient here.
                cloudClient = new CloudControlClient(this, installationStore, packageInstaller,
                        videoRuntimePorts, videoRuntimePorts::abortActivation, () -> cloudClient);
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
                    CloudControlClient spent = cloudClient;
                    cloudResetPending = true;
                    cloudClient = null;
                    spent.reset(() -> new android.os.Handler(Looper.getMainLooper())
                            .post(() -> cloudResetPending = false));
                }
                if (renderer != null) {
                    renderer.dismiss();
                    renderer = null;
                }
                if (videoRuntimePorts != null) videoRuntimePorts.abortActivation();
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
     * Service-owned runtime ports reuse the existing scheduler/controller and lazy windows.
     * Suppliers tolerate teardown and permit deterministic tests of this actual adapter;
     * only native retirement and the owner predicate are substituted. No parser, storage,
     * network or ACK enters these ports. Selection is the final successful ARM operation.
     */
    static final class LiveVideoRuntimePorts implements VideoInstallationRuntimePorts, MediaCalendarScheduler.Sink {
        /** Native legacy drawing remains outside the temporal core and preserves its countdown. */
        interface LegacySink {
            /** Render the exact payload indexed by the active prepared Video binding. */
            void due(ScheduledTrack.Event event);
            /** Forward passive playback repetitions to the existing renderer. */
            void playback(boolean playing,boolean freeze);
            /** Hide the legacy visual immediately on loss of eligibility. */
            void eligibility(boolean eligible);
        }
        /** Immutable activation identity and the generation captured at manifest ARM. */
        private static final class Activation {
            final VideoPreparedState state;
            final String token;
            final long generation;
            /** Retain memory-only prepared values and a fixed local token/generation. */
            Activation(VideoPreparedState state,String token,long generation) {
                this.state=state;this.token=token;this.generation=generation;
            }
        }
        private final java.util.function.BooleanSupplier owner;
        private final java.util.function.Supplier<MediaCalendarScheduler> scheduler;
        private final java.util.function.Supplier<SceneRuntimeController> controller;
        private final Runnable retireLegacy,retireScenes;
        private final java.util.function.LongConsumer selection;
        private final LegacySink legacy;
        private final java.util.function.Consumer<Boolean> diagnostics;
        private long nextActivation;
        private Activation pending,active,retiring;
        private boolean invalidating;

        /** Bind the service's existing owners; construction never loads, renders or persists. */
        LiveVideoRuntimePorts(java.util.function.BooleanSupplier owner,
                java.util.function.Supplier<MediaCalendarScheduler> scheduler,
                java.util.function.Supplier<SceneRuntimeController> controller,
                Runnable retireLegacy,Runnable retireScenes,java.util.function.LongConsumer selection,
                LegacySink legacy,java.util.function.Consumer<Boolean> diagnostics) {
            if(owner==null||scheduler==null||controller==null||retireLegacy==null
                    ||retireScenes==null||selection==null||legacy==null||diagnostics==null)
                throw new IllegalArgumentException("Missing Video owner");
            this.owner=owner;this.scheduler=scheduler;this.controller=controller;
            this.retireLegacy=retireLegacy;this.retireScenes=retireScenes;this.selection=selection;
            this.legacy=legacy;this.diagnostics=diagnostics;
        }
        /** Android composition returns true only on the actual main/window owner thread. */
        @Override public boolean isOwnerThread() {return owner.getAsBoolean();}
        /** Invalidate callback ownership before any synchronous native retirement can reenter. */
        private void invalidate() {
            if(active!=null)retiring=active;
            else if(pending!=null)retiring=pending;
            active=null;pending=null;
        }
        /** Synchronously dismiss the existing legacy renderer; never construct a renderer. */
        @Override public boolean retireLegacyVisualOwner() {
            if(!isOwnerThread())return false;
            invalidate();retireLegacy.run();return true;
        }
        /** Unload/invalidate the regie, then force immediate removal of its existing window. */
        @Override public boolean retireManifestedVisualOwner() {
            if(!isOwnerThread())return false;
            invalidate();SceneRuntimeController runtime=controller.get();
            if(runtime==null)return false;
            runtime.unload();retireScenes.run();return true;
        }
        /** Load the exact prepared calendar and payload index; pending callbacks cannot render. */
        @Override public boolean loadPreparedVideo(VideoPreparedState state) {
            if(!isOwnerThread()||state==null)return false;
            MediaCalendarScheduler runtime=scheduler.get();if(runtime==null)return false;
            invalidate();
            if(nextActivation==Long.MAX_VALUE)return false;
            pending=new Activation(state,"video-activation-"+(++nextActivation),-1L);
            invalidating=true;
            try {runtime.load(state.calendar,pending.token);return true;}
            finally {invalidating=false;retiring=null;}
        }
        /** Replace the exact manifest, then capture its generation once before selection. */
        @Override public boolean armPreparedManifest(long revision,OverlayManifest manifest) {
            if(!isOwnerThread()||revision<1||manifest==null||pending==null
                    ||pending.state.manifest!=manifest)return false;
            SceneRuntimeController runtime=controller.get();if(runtime==null)return false;
            runtime.replaceRevision(revision,manifest);
            pending=new Activation(pending.state,pending.token,runtime.currentGeneration());
            return runtime.isSceneRendererActiveFor(revision);
        }
        /** Promote pending only after every previous ARM operation has succeeded. */
        @Override public boolean selectActiveRevision(long revision,boolean manifested) {
            if(!isOwnerThread()||revision<1||pending==null)return false;
            SceneRuntimeController runtime=controller.get();
            if(runtime==null||(pending.state.manifest!=null)!=manifested
                    ||(manifested?(pending.generation<0||!runtime.isSceneRendererActiveFor(revision))
                        :runtime.hasActiveManifest()))return false;
            selection.accept(revision);active=pending;pending=null;return true;
        }
        /** Preserve the supported LAN development activation without inventing a durable revision. */
        boolean selectLanActivation() {
            if(!isOwnerThread()||pending==null||pending.state.manifest!=null)return false;
            SceneRuntimeController runtime=controller.get();if(runtime==null||runtime.hasActiveManifest())return false;
            selection.accept(0);active=pending;pending=null;return true;
        }
        /** Project only the active prepared track through the qualified C observation adapter. */
        void onSnapshot(MediaSessionProbe.Snapshot snapshot) {
            if(!isOwnerThread()||active==null)return;
            MediaCalendarScheduler runtime=scheduler.get();if(runtime!=null)
                runtime.onObservation(VideoMediaObservationAdapter.observe(active.state.track,snapshot));
        }
        /** An opaque callback token must match the immutable active activation, never a revision. */
        private Activation matching(String token) {
            return isOwnerThread()&&active!=null&&active.token.equals(token)?active:null;
        }
        /** Route ID plus captured generation to the regie, or exact payload to the legacy renderer. */
        @Override public void onDue(String token,String eventId) {
            Activation binding=matching(token);if(binding==null)return;
            if(binding.state.manifest!=null) {
                SceneRuntimeController runtime=controller.get();if(runtime!=null)runtime.onEventDue(eventId,binding.generation);
            } else {
                ScheduledTrack.Event event=binding.state.eventsById.get(eventId);
                if(event!=null)legacy.due(event);
            }
        }
        /** Manifested expiry is generation-guarded; legacy retains its own freeze-aware countdown. */
        @Override public void onExpire(String token,String eventId) {
            Activation binding=matching(token);if(binding==null||binding.state.manifest==null)return;
            SceneRuntimeController runtime=controller.get();if(runtime!=null)runtime.onEventExpired(eventId,binding.generation);
        }
        /** Preserve repetitions while ignoring every stale or pending playback token. */
        @Override public void onPlayback(String token,boolean playing,boolean freeze) {
            Activation binding=matching(token);if(binding==null)return;
            if(binding.state.manifest!=null) {
                SceneRuntimeController runtime=controller.get();if(runtime!=null)runtime.onPlayback(playing,freeze);
            } else legacy.playback(playing,freeze);
        }
        /** Diagnostics precede controller/hide; only synchronous old-binding invalidation bypasses active matching. */
        @Override public void onEligibility(String token,boolean eligible) {
            Activation binding=matching(token);
            if(binding==null) {
                if(!isOwnerThread()||!invalidating||eligible
                        ||(retiring==null?token!=null:!retiring.token.equals(token)))return;
                binding=retiring;
            }
            diagnostics.accept(eligible);
            SceneRuntimeController runtime=controller.get();if(runtime!=null)runtime.onEligibility(eligible);
            if(!eligible) {
                if(binding!=null&&binding.state.manifest!=null)retireScenes.run();
                else legacy.eligibility(false);
            }
        }
        /** Clear partial ownership idempotently, attempting every cleanup even after a refusal. */
        @Override public void abortActivation() {
            if(!isOwnerThread())return;
            invalidate();
            cleanup(()->{SceneRuntimeController runtime=controller.get();if(runtime!=null)runtime.unload();});
            cleanup(retireScenes);cleanup(retireLegacy);
            invalidating=true;
            try {cleanup(()->{
                MediaCalendarScheduler runtime=scheduler.get();if(runtime==null)return;
                try {runtime.clear();}
                catch(RuntimeException refusedInvalidation) {
                    // A native eligibility failure cannot retain the core's old token/calendar.
                    // Retry the same qualified clear with all callbacks already invalidated.
                    invalidating=false;runtime.clear();
                }
            });}
            finally {invalidating=false;pending=null;active=null;retiring=null;cleanup(()->selection.accept(0));}
        }
        /** A failed native removal cannot skip the remaining cleanup or expose raw exceptions. */
        private static void cleanup(Runnable work) {
            try {work.run();}catch(RuntimeException refused) { /* The handler returns bounded ARM_FAILED. */ }
        }
    }

    /**
     * Restore once into a freshly composed runtime, without parsing, writing, migrating or ACKing.
     * EMPTY does nothing; CORRUPT stays intact. A snapshot's inert canonical request reuses the
     * same installer's qualified same-revision path and exact durable handler binding.
     * Only ARMED selects a revision through the existing owner ports. The nullable result means
     * no package was present; diagnostics records a bounded last-startup outcome, never content.
     */
    static InstallationStatus restoreInstalledPackage(InstallationStore store, PackageInstaller installer,
            InstallationHandler.RuntimePorts ports, DiagnosticsStore diagnostics) {
        InstallationStatus result;
        try {
            InstallationStore.ReadResult durable = store.read();
            if (durable.state() == InstallationStore.ReadState.EMPTY) {
                result = null;
            } else if (durable.state() == InstallationStore.ReadState.CORRUPT) {
                result = InstallationStatus.CACHE_FAILED;
            } else {
                result = installer.install(durable.snapshot().canonical(), ports);
            }
        } catch (RuntimeException unavailable) {
            result = InstallationStatus.CACHE_FAILED;
        }
        diagnostics.setLastStartupRestoreResult(result);
        return result;
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
        if (videoRuntimePorts != null) videoRuntimePorts.abortActivation();
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
