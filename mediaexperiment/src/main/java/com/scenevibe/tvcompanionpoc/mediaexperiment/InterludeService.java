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

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.AudioFocusPort;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.Diagnostics;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.InterludeState;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.InterludeStateMachine;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.PlaybackSnapshot;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SafeResumeGuard;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SessionTarget;

/**
 * User-started mediaPlayback foreground service that owns the interlude runtime for
 * the POC. It wires the Android-free {@link InterludeStateMachine} (FEAT-002) to the
 * real Android ports (FEAT-003) and drives everything on the MAIN THREAD.
 *
 * <p>Lifecycle mirrors :app's {@code OverlayService} (createNotificationChannel +
 * startForeground in onCreate, START_STICKY, stopSelf on overlay-permission loss)
 * but shares NO code, NO SharedPreferences, and uses a DISTINCT channel id. It is a
 * {@code mediaPlayback} foreground service, matching the honest work it performs.</p>
 *
 * <p>The service runs a 1-second sampler of the selected MediaSession so dispatched
 * pause/play commands are confirmed only by OBSERVED state, and so pending
 * confirmation deadlines (pause/play timeouts) are evaluated via {@code onTick()}.</p>
 */
public final class InterludeService extends Service
        implements AndroidOverlayVideoPort.Callbacks {
    private static final String TAG = "SceneVibeInterludePoc";
    private static final String CHANNEL_ID = "media_interlude_poc";
    private static final int NOTIFICATION_ID = 2001;
    private static final long SAMPLE_INTERVAL_MS = 1000L;
    private static final long PAUSE_TIMEOUT_MS = 4000L;
    private static final long PLAY_TIMEOUT_MS = 4000L;

    /** Receives bounded diagnostics after each runtime step (never content/credentials). */
    interface DiagnosticsListener {
        void onDiagnostics(Diagnostics diagnostics, InterludeState state, boolean accessGranted);
    }

    /** Local binder so the diagnostic Activity can drive the five actions in-process. */
    final class LocalBinder extends Binder {
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
    private final SafeResumeGuard guard = SafeResumeGuard.create();
    private final ElapsedRealtimeClock clock = new ElapsedRealtimeClock();

    private InterludeStateMachine machine;
    private boolean sampling;
    private boolean foregroundReady;
    private DiagnosticsListener listener;

    private final Runnable sampler = new Runnable() {
        @Override
        public void run() {
            if (!sampling || machine == null) {
                return;
            }
            PlaybackSnapshot snapshot = scanner.sampleCurrent();
            if (snapshot != null) {
                machine.observe(snapshot);
            } else {
                // No live sample: still advance any pending timeout deadline.
                machine.onTick();
            }
            publish();
            if (machine.state() == InterludeState.STOPPED) {
                sampling = false;
            } else {
                handler.postDelayed(this, SAMPLE_INTERVAL_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        scanner = new MediaSessionScanner(this);
        audioFocus = new AndroidAudioFocusPort(this);
        mediaControl = new AndroidMediaControlPort(scanner);
        overlayVideo = new AndroidOverlayVideoPort(this, this);
        localAudio = new AndroidLocalAudioPort(this);
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

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    void setDiagnosticsListener(DiagnosticsListener listener) {
        this.listener = listener;
    }

    boolean accessGranted() {
        return scanner != null && scanner.accessGranted();
    }

    // --- The five diagnostic actions -----------------------------------------

    /** SCAN MEDIA SESSION: discover the active session and record its facts. */
    void scanMediaSession() {
        ensureFreshMachine();
        PlaybackSnapshot snapshot = scanner.scan();
        Diagnostics diagnostics = machine.diagnostics();
        if (snapshot == null) {
            diagnostics.selectedPackage = null;
            Log.i(TAG, "SCAN no active/authorized session");
        } else {
            diagnostics.selectedPackage = snapshot.packageName;
            diagnostics.playbackStateName = snapshot.stateName();
            diagnostics.mediaIdPresent =
                    snapshot.mediaId != null && !snapshot.mediaId.trim().isEmpty();
            scanner.recordActions(diagnostics);
            Log.i(TAG, "SCAN selected package=" + snapshot.packageName
                    + " state=" + snapshot.stateName());
        }
        publish();
    }

    /**
     * TEST AUDIO DUCK: request transient-may-duck focus; play the short local cue
     * ONLY if granted; then finish and abandon focus. Never plays on DENIED.
     */
    void testAudioDuck() {
        ensureFreshMachine();
        Diagnostics diagnostics = machine.diagnostics();
        AudioFocusPort.Result result = audioFocus.requestTransientMayDuck();
        diagnostics.audioFocusResult = result;
        if (result == AudioFocusPort.Result.GRANTED) {
            localAudio.playShortClip();
            // Short clip: schedule a bounded stop + focus abandon.
            handler.postDelayed(() -> {
                localAudio.stop();
                audioFocus.abandon();
                publish();
            }, 2000L);
        } else {
            // Not granted: DO NOT play. Nothing to abandon.
            Log.i(TAG, "AUDIO_DUCK denied; not playing local cue");
        }
        publish();
    }

    /**
     * TEST PAUSE: on a scanned session that advertises PAUSE, dispatch pause via
     * the state machine and wait (bounded) for an OBSERVED STATE_PAUSED. Dispatch is
     * not confirmation; a timeout reports UNSUPPORTED / NOT CONFIRMED.
     */
    void testPause() {
        PlaybackSnapshot snapshot = scanner.scan();
        if (snapshot == null) {
            ensureFreshMachine();
            machine.diagnostics().selectedPackage = null;
            Log.i(TAG, "PAUSE no active session to target");
            publish();
            return;
        }
        if (!scanner.canPause()) {
            // Session does not advertise PAUSE: report UNSUPPORTED, do not dispatch.
            ensureFreshMachine();
            Diagnostics diagnostics = machine.diagnostics();
            diagnostics.selectedPackage = snapshot.packageName;
            diagnostics.playbackStateName = snapshot.stateName();
            scanner.recordActions(diagnostics);
            Log.i(TAG, "PAUSE unsupported: session does not advertise ACTION_PAUSE");
            publish();
            return;
        }
        beginSequence(snapshot);
    }

    /**
     * TEST FULL INTERLUDE: same confirmed-pause gate as TEST PAUSE, after which the
     * core attaches the overlay and plays the local video. The overlay is only
     * attached after an observed STATE_PAUSED (enforced inside the state machine).
     */
    void testFullInterlude() {
        testPause();
    }

    /**
     * EMERGENCY RESTORE / STOP: stop local audio/video, abandon focus, remove every
     * overlay, clear POC state. The core only sends PLAY if the SafeResumeGuard
     * proves this POC owns a confirmed pause; a bare STOP never forces resume.
     */
    void emergencyStop() {
        if (machine != null) {
            machine.stop();
        }
        // Defensive teardown independent of machine state.
        localAudio.stop();
        overlayVideo.remove();
        audioFocus.abandon();
        sampling = false;
        handler.removeCallbacks(sampler);
        publish();
    }

    // --- Internal wiring -------------------------------------------------------

    private void beginSequence(PlaybackSnapshot snapshot) {
        ensureFreshMachine();
        Diagnostics diagnostics = machine.diagnostics();
        diagnostics.selectedPackage = snapshot.packageName;
        diagnostics.playbackStateName = snapshot.stateName();
        scanner.recordActions(diagnostics);

        SessionTarget target = scanner.toTarget(snapshot);
        // Seed the machine with the current observation so the guard has a live
        // snapshot, then begin: begin() requests focus, plays the cue, dispatches
        // pause; the sampler drives confirmation and the interlude.
        machine.observe(snapshot);
        machine.begin(target);
        startSampling();
        publish();
    }

    private void ensureFreshMachine() {
        if (machine == null || machine.state() == InterludeState.STOPPED) {
            machine = new InterludeStateMachine(
                    audioFocus, mediaControl, overlayVideo, localAudio,
                    guard, clock, PAUSE_TIMEOUT_MS, PLAY_TIMEOUT_MS);
        }
    }

    private void startSampling() {
        if (sampling) {
            return;
        }
        sampling = true;
        handler.postDelayed(sampler, SAMPLE_INTERVAL_MS);
    }

    private void publish() {
        if (listener != null && machine != null) {
            listener.onDiagnostics(machine.diagnostics(), machine.state(), accessGranted());
        }
    }

    // --- Overlay video callbacks (delivered on the main thread) ----------------

    @Override
    public void onVideoCompleted() {
        if (machine != null) {
            machine.onVideoCompleted();
            publish();
        }
    }

    @Override
    public void onVideoError() {
        if (machine != null) {
            machine.onVideoError();
            publish();
        }
    }

    @Override
    public void onDestroy() {
        sampling = false;
        handler.removeCallbacks(sampler);
        if (machine != null) {
            machine.stop();
        }
        super.onDestroy();
    }

    /** Convenience for the Activity to start this service as a foreground service. */
    static void start(Context context) {
        context.startForegroundService(new Intent(context, InterludeService.class));
    }
}
