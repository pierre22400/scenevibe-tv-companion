package com.scenevibe.tvcompanionpoc.mediaexperiment;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.Diagnostics;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.InterludeState;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.InterludeRuntime;

/**
 * User-started mediaPlayback foreground service that owns the interlude runtime for
 * the POC. It wires the Android-free {@link InterludeRuntime} to the
 * real Android ports (FEAT-003) and drives everything on the MAIN THREAD.
 *
 * <p>Lifecycle mirrors :app's {@code OverlayService} (createNotificationChannel +
 * startForeground in onCreate, START_STICKY, stopSelf on overlay-permission loss)
 * but shares NO code, NO SharedPreferences, and uses a DISTINCT channel id. It is a
 * {@code mediaPlayback} foreground service, matching the honest work it performs.</p>
 *
 * <p>The service runs a 1-second fresh active-session rescan so dispatched
 * pause/play commands are confirmed only by OBSERVED state, and so pending
 * confirmation deadlines (pause/play timeouts) are evaluated via {@code onTick()}.</p>
 */
public final class InterludeService extends Service
        implements AndroidOverlayVideoPort.Callbacks {
    private static final String TAG = "SceneVibeInterludePoc";
    private static final String CHANNEL_ID = "media_interlude_poc";
    private static final int NOTIFICATION_ID = 2001;
    private static final long SAMPLE_INTERVAL_MS = 1000L;

    /** Receives bounded diagnostics after each runtime step (never content/credentials). */
    interface DiagnosticsListener {
        /** Receive bounded mechanism facts after a service step. */
        void onDiagnostics(Diagnostics diagnostics, InterludeState state, boolean accessGranted);
    }

    /** Local binder so the diagnostic Activity can drive the five actions in-process. */
    final class LocalBinder extends Binder {
        /** Expose this service to the local diagnostic Activity. */
        InterludeService service() {
            return InterludeService.this;
        }
    }

    private final IBinder binder = new LocalBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private MediaSessionScanner scanner;
    private AndroidAudioFocusPort audioFocus;
    private AndroidMediaControlPort mediaControl;
    private AndroidOverlayVideoPort overlayVideo;
    private AndroidLocalAudioPort localAudio;
    private final ElapsedRealtimeClock clock = new ElapsedRealtimeClock();

    private InterludeRuntime runtime;
    private boolean sampling;
    private boolean foregroundReady;
    private DiagnosticsListener listener;

    private final Runnable sampler = new Runnable() {
        /** Drive the tested runtime with live active-session queries. */
        @Override
        public void run() {
            if (!sampling || runtime == null) return;
            runtime.poll();
            publish();
            if (runtime.state() == InterludeState.STOPPED) {
                sampling = false;
            } else {
                handler.postDelayed(this, SAMPLE_INTERVAL_MS);
            }
        }
    };

    /** Create isolated Android ports and start the mediaPlayback foreground service. */
    @Override
    public void onCreate() {
        super.onCreate();
        scanner = new MediaSessionScanner(this);
        audioFocus = new AndroidAudioFocusPort(this);
        mediaControl = new AndroidMediaControlPort(scanner);
        overlayVideo = new AndroidOverlayVideoPort(this, this);
        localAudio = new AndroidLocalAudioPort(this);
        runtime = new InterludeRuntime(scanner, audioFocus, mediaControl,
                overlayVideo, localAudio, clock);
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.interlude_channel_name),
                    NotificationManager.IMPORTANCE_LOW));
            Intent launch = new Intent(this, MediaExperimentActivity.class);
            PendingIntent open = PendingIntent.getActivity(this, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.banner)
                    .setContentTitle(getString(R.string.interlude_notification_title))
                    .setContentText(getString(R.string.interlude_notification_text))
                    .setContentIntent(open)
                    .setOngoing(true)
                    .build();
            // The typed foreground-service-type overload exists from API 29. On
            // 26-28 the service type comes solely from the manifest, so the 2-arg
            // startForeground is correct there.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            foregroundReady = true;
            Log.i(TAG, "InterludeService foreground started (mediaPlayback)");
        } catch (RuntimeException error) {
            Log.e(TAG, "Foreground init failed", error);
            stopSelf();
        }
    }

    /** Validate foreground/overlay readiness without starting any test automatically. */
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
        return START_STICKY;
    }

    /** Return the local diagnostic binder without initiating media actions. */
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /** Attach or detach the local bounded-diagnostics listener. */
    void setDiagnosticsListener(DiagnosticsListener listener) {
        this.listener = listener;
    }

    /** Report this isolated app's notification-listener access. */
    boolean accessGranted() {
        return scanner != null && scanner.accessGranted();
    }

    // --- Independent diagnostic actions --------------------------------------

    /** SCAN MEDIA SESSION: discover only; no focus, pause, overlay or local playback. */
    void scanMediaSession() {
        resetSampler();
        runtime.scanMediaSession();
        publish();
    }

    /** TEST AUDIO DUCK: focus/cue only, bounded to two seconds, never transport/video. */
    void testAudioDuck() {
        resetSampler();
        runtime.testAudioDuck();
        startSampling();
        publish();
    }

    /**
     * TEST PAUSE: require live PLAYING, dispatch PAUSE, await bounded PAUSED.
     * Success ends this attempt without any cue, overlay, local video or PLAY.
     * The operator must manually restart native media before the full test.
     */
    void testPause() {
        resetSampler();
        runtime.testPause();
        startSampling();
        publish();
    }

    /**
     * TEST FULL INTERLUDE: independent focus/confirmed-pause/video/guarded-resume
     * flow. Fresh active-session/token revalidation is mandatory before PLAY.
     */
    void testFullInterlude() {
        resetSampler();
        runtime.testFullInterlude();
        startSampling();
        publish();
    }

    /**
     * EMERGENCY RESTORE / STOP removes local audio/video/overlay and abandons focus.
     * It always sends NO PLAY, even when this attempt owns a confirmed pause.
     */
    void emergencyStop() {
        resetSampler();
        if (runtime != null) runtime.stop();
        publish();
    }

    /** Cancel old callbacks before starting a separately owned operator action. */
    private void resetSampler() {
        sampling = false;
        handler.removeCallbacks(sampler);
    }

    /** Start bounded confirmations/cue polling on the main thread. */
    private void startSampling() {
        if (sampling || runtime == null || runtime.state() == InterludeState.STOPPED) {
            return;
        }
        sampling = true;
        handler.postDelayed(sampler, SAMPLE_INTERVAL_MS);
    }

    /** Deliver coarse diagnostics without exposing media text or token values. */
    private void publish() {
        if (listener != null && runtime != null) {
            listener.onDiagnostics(runtime.diagnostics(), runtime.state(), accessGranted());
        }
    }

    // --- Overlay video callbacks (delivered on the main thread) ----------------

    /** Forward normal completion to the tested runtime for fresh guarded resume. */
    @Override
    public void onVideoCompleted() {
        if (runtime != null) {
            runtime.onVideoCompleted();
            startSampling();
            publish();
        }
    }

    /** Forward local video failure for conservative teardown without PLAY. */
    @Override
    public void onVideoError() {
        if (runtime != null) {
            runtime.onVideoError();
            publish();
        }
    }

    /** Cancel polling and release every local resource when the service is destroyed. */
    @Override
    public void onDestroy() {
        sampling = false;
        handler.removeCallbacks(sampler);
        if (runtime != null) {
            runtime.stop();
        }
        super.onDestroy();
    }

    /** Convenience for the Activity to start this service as a foreground service. */
    static void start(Context context) {
        context.startForegroundService(new Intent(context, InterludeService.class));
    }
}
