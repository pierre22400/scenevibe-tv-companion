package com.scenevibe.tvcompanionpoc;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;
import com.scenevibe.tvcompanionpoc.audio.AndroidLocalAudioPort;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Own the experimental cache, clock and existing renderer ports on the service's main thread. */
public final class NetflixManualSyncService extends Service {
    // Both experimental services receive lifecycle and button callbacks on the main thread.
    private static NetflixManualSyncService shortcutOwner;
    private static final Set<Runnable> sessionObservers = new LinkedHashSet<>();
    private static LaunchStatus currentLaunchStatus = LaunchStatus.IDLE;
    private static final String CHANNEL = "netflix-manual-003c";
    /**
     * Remote-first, deliberately explicit playback policies.
     *
     * The first action simplifies the usual manual start: the viewer chooses to resume
     * BEFORE entering the editor. The second preserves the qualified pause-on-commit
     * workflow for adverts, interruptions and other deliberate manual resynchronisations.
     * These indices are protected by NetflixManualPanelTest.
     */
    private static final String[] CONTROL_CHOICES = {
            "Recaler et reprendre",
            "Recaler sans reprise",
            "Pause SceneVibe",
            "Reprendre SceneVibe"
    };
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService decoderWorker = Executors.newSingleThreadExecutor();
    private CompanionControlPanel panel;
    private NetflixManualClock clock;
    private NetflixLocalAdmission.Selection selection;
    private Runnable pendingTick;
    private boolean arming;
    private boolean destroyed;
    private boolean resumeAfterClose;
    private Edit activeEdit;
    private AlertDialog controls;

    private enum State { INACTIF, PRET, PAUSE, MARCHE, EDITION }

    /** Admission and lifetime of the one local session, independent of its clock position. */
    enum LaunchStatus { IDLE, PREPARING, READY, FAILED, STOPPED }

    /** Return the latest process-local status without starting or restoring a session. */
    static LaunchStatus launchStatus() {
        return currentLaunchStatus;
    }

    /** Subscribe a visible launcher to main-thread admission and stop results. */
    static void observeSession(Runnable observer) {
        sessionObservers.add(observer);
    }

    /** Detach a launcher that is leaving the foreground. */
    static void removeSessionObserver(Runnable observer) {
        sessionObservers.remove(observer);
    }

    /** Stop the current owner without starting a new service for an idle launcher. */
    static void requestStopFromLauncher() {
        if (shortcutOwner != null) shortcutOwner.stopSession();
    }

    /** Update launchers on the main thread after the owner state has changed. */
    private static void publishLaunchStatus(LaunchStatus status) {
        currentLaunchStatus = status;
        for (Runnable observer : List.copyOf(sessionObservers)) observer.run();
    }

    /** Start the protected owner and publish only local SceneVibe controls. */
    @Override public void onCreate() {
        super.onCreate();
        panel = new CompanionControlPanel(this, null);
        panel.onLifecycle(this::windowClosed, () -> {});
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "SceneVibe Netflix 003-C",
                NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder note = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("SceneVibe · horloge manuelle")
                .setContentText("Choisis la track locale avant d’ouvrir le recalage.")
                .setOngoing(true);
        for (String action : new String[]{"OPEN_CONTROLS", "ARM", "OPEN_I", "OPEN_PAUSED", "PAUSE", "RESUME", "CLOSE", "STOP"}) {
            PendingIntent command = PendingIntent.getService(this, action.hashCode(),
                    new Intent(this, getClass()).setAction(action),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            note.addAction(new Notification.Action.Builder(null,
                    "OPEN_CONTROLS".equals(action) ? "Commandes SceneVibe" : action, command).build());
        }
        startForeground(8103, note.build());
        shortcutOwner = this;
    }

    /** Accept only the enumerated commands for the local service. */
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) return START_NOT_STICKY;
        switch (action) {
            case "ARM": armFixture(); break;
            case "OPEN_CONTROLS": openControls(); break;
            case "OPEN_I": openEditor(true); break;
            case "OPEN_PAUSED": openEditor(false); break;
            case "PAUSE": pause(); break;
            case "RESUME": resume(); break;
            case "CLOSE": closeControls(); panel.close(); break;
            case "STOP": stopSession(); break;
            default: trace("COMMAND_REFUSED");
        }
        return START_NOT_STICKY;
    }

    /** Select the packaged fixture explicitly and inspect the MP3s away from the UI owner. */
    private void armFixture() {
        if (shortcutOwner != this || arming || (clock != null && clock.editable())) return;
        arming = true;
        publishLaunchStatus(LaunchStatus.PREPARING);
        decoderWorker.execute(() -> admitFixture(new AndroidStudioAudioInspector(this)));
    }

    /** Verify and import the packaged fixture on the worker, then return to the owner thread. */
    private void admitFixture(StudioMultimediaPackage.Decoder inspector) {
        try {
            JSONObject reference = new JSONObject(new String(asset("selection.json"), StandardCharsets.UTF_8));
            NetflixLocalAdmission.Selection selected = new NetflixLocalAdmission.Selection(
                    reference.getString("trackId"), reference.getString("programId"),
                    reference.getLong("revision"), reference.getString("sha256"));
            Map<String, Long> durations = new HashMap<>();
            byte[] zip = asset("track.zip");
            NetflixLocalAdmission importing = NetflixLocalAdmission.open(this, selected, bytes -> {
                long duration = inspector.decodedDurationMs(bytes);
                durations.put(StudioMultimediaPackage.sha256(bytes), duration);
                return duration;
            });
            importing.admit(zip);
            Map<String, Long> verified = Map.copyOf(durations);
            handler.post(() -> activate(selected, verified));
        } catch (Exception invalid) {
            handler.post(this::admissionFailed);
        }
    }

    /** Read the original bundled artifact under its fixed application-private name. */
    private byte[] asset(String name) throws Exception {
        try (InputStream input = getAssets().open("netflix003c/" + name)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int size;
            while ((size = input.read(chunk)) != -1) bytes.write(chunk, 0, size);
            return bytes.toByteArray();
        }
    }

    /** Revalidate the private cache on the owner and reuse the previously measured native durations. */
    private void activate(NetflixLocalAdmission.Selection selected, Map<String, Long> durations) {
        arming = false;
        if (shortcutOwner != this || destroyed) return;
        try {
            NetflixLocalAdmission admission = NetflixLocalAdmission.open(this, selected, bytes -> {
                Long duration = durations.get(StudioMultimediaPackage.sha256(bytes));
                if (duration == null) throw new IllegalStateException("NATIVE_INSPECTION_MISSING");
                return duration;
            });
            clock = admission.activateManual(this::createRuntime, SystemClock::elapsedRealtime);
            selection = selected;
            trace("ADMISSION_READY");
        } catch (Exception invalid) {
            admissionFailed();
            return;
        }
        publishLaunchStatus(LaunchStatus.READY);
    }

    /** Retire a failed admission and retain its result until a new explicit attempt. */
    private void admissionFailed() {
        arming = false;
        if (shortcutOwner != this || destroyed) return;
        trace("ADMISSION_REFUSED");
        retireShortcutOwner();
        stopSelf();
        publishLaunchStatus(LaunchStatus.FAILED);
    }

    /** Stop the current admission, invalidate callbacks and release the foreground owner. */
    private void stopSession() {
        if (shortcutOwner != this) return;
        retireShortcutOwner();
        resumeAfterClose = false;
        closeControls();
        if (clock != null) clock.revoke();
        panel.close();
        stopSelf();
        publishLaunchStatus(LaunchStatus.STOPPED);
    }

    /** Reuse the existing Android drawing and verified MP3 adapters. */
    private StudioMultimediaRuntime createRuntime(StudioMultimediaPackage track,
            BooleanSupplier permitted) throws Exception {
        StudioMultimediaFiles files = new StudioMultimediaFiles(new File(getFilesDir(), "manual003b"), track);
        SceneRenderer text = new SceneRenderer(this, id -> null, () -> {
            NetflixManualClock captured = clock;
            handler.post(() -> {
                if (captured != null && captured == clock) stopSession();
            });
        });
        AndroidLocalAudioPort audio = new AndroidLocalAudioPort(files, start -> {
            if (!permitted.getAsBoolean() || !Settings.canDrawOverlays(this)) return false;
            start.run();
            return true;
        });
        return new StudioMultimediaRuntime(new SceneRuntimeController.SceneSink() {
            /** Check the native renderer and current permission before submitting a scene. */
            @Override public boolean preflight(OverlayManifest.Scene scene) {
                return permitted.getAsBoolean() && text.preflight(scene);
            }
            /** Render the original verified TEXT scene. */
            @Override public void show(OverlayManifest.Scene scene) { text.render(scene); }
            /** Retire only this scene's existing window. */
            @Override public void hide(OverlayManifest.Scene scene) { text.dismiss(scene); }
            /** Drop all TEXT synchronously on seek or loss of admission. */
            @Override public void hideAll() { text.dismissNow(); }
        }, audio, false, true, null);
    }

    /** Project the owner state without introducing another clock or event scheduler. */
    private State state() {
        if (clock == null || !clock.editable()) return State.INACTIF;
        if (activeEdit != null) return State.EDITION;
        if (clock.running()) return State.MARCHE;
        return clock.anchored() ? State.PAUSE : State.PRET;
    }

    /**
     * Format an editorial clock position for TV viewing so the viewer can compare it,
     * digit for digit, against the time their film shows.
     *
     * <p>Below one hour the value is rendered as {@code MM:SS} (e.g. {@code 05:02},
     * {@code 10:15}); at or above one hour it is rendered as {@code HH:MM:SS} (e.g.
     * {@code 01:05:15}, {@code 02:13:08}). Ambiguous forms where the minutes field
     * exceeds 59 (such as {@code 65:15}) are therefore never produced. The same single
     * helper is used for the command-menu title and for the recalage panel so both read
     * identically.
     *
     * @param ms a non-negative position in milliseconds
     * @return the padded {@code MM:SS} or {@code HH:MM:SS} representation
     */
    private static String timeLabel(long ms) {
        long seconds = Math.max(0, ms) / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainder = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainder);
        }
        return String.format(Locale.ROOT, "%02d:%02d", minutes, remainder);
    }

    /** Route OPEN_CONTROLS locally without starting a service from the background. */
    static void requestControlsFromShortcut() {
        NetflixManualSyncService owner = shortcutOwner;
        if (owner != null && owner.clock != null && owner.clock.editable()) {
            owner.openControls(true);
        } else {
            NetflixShortcutService.traceShortcut("SHORTCUT_OPEN_REFUSED_NOT_READY");
        }
    }

    /** Withdraw this instance from shortcut dispatch before it stops. */
    private void retireShortcutOwner() {
        if (shortcutOwner == this) shortcutOwner = null;
    }

    /** Open a focusable, light local command menu without changing the clock. */
    private void openControls() {
        openControls(false);
    }

    /** Trace shortcut-only outcomes at the existing command menu boundaries. */
    private void openControls(boolean shortcut) {
        if (panel.isOpen() || controls != null) {
            if (shortcut) NetflixShortcutService.traceShortcut("SHORTCUT_OPEN_REFUSED_ALREADY_OPEN");
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            if (shortcut) NetflixShortcutService.traceShortcut("SHORTCUT_OPEN_REFUSED_OVERLAY_PERMISSION");
            refuse("Autorise la superposition depuis l’accueil SceneVibe.");
            return;
        }
        if (shortcut) NetflixShortcutService.traceShortcut("SHORTCUT_OPEN_ACCEPTED");
        AlertDialog menu = new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle(controlTitle())
                .setItems(CONTROL_CHOICES, (dialog, which) -> {
                    closeControls();
                    switch (which) {
                        case 0: openEditor(true, false); break;
                        case 1: openEditor(false, false); break;
                        case 2: pause(); break;
                        case 3: resume(); break;
                        default: trace("COMMAND_REFUSED_INVALID_MENU_CHOICE");
                    }
                })
                .setNegativeButton("Fermer", null)
                .create();
        menu.setOnDismissListener(dialog -> { if (controls == menu) controls = null; });
        controls = menu;
        try {
            menu.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            menu.show();
            if (shortcut && menu.isShowing()) NetflixShortcutService.traceShortcut("SHORTCUT_PANEL_OPENED");
            boolean[] acquired = {menu.getWindow().getDecorView().hasWindowFocus()};
            menu.getWindow().getDecorView().getViewTreeObserver().addOnWindowFocusChangeListener(focused -> {
                if (focused) acquired[0] = true;
                else if (acquired[0] && controls == menu) closeControls();
            });
        } catch (WindowManager.BadTokenException | SecurityException denied) {
            if (shortcut) NetflixShortcutService.traceShortcut("SHORTCUT_PANEL_OPEN_FAILED");
            closeControls();
            refuse("Panneau indisponible.");
        }
    }

    /** Describe the current position and local command state without moving focus. */
    private String controlTitle() {
        State current = state();
        String status = current == State.MARCHE ? "EN MARCHE" : "EN PAUSE";
        if (current == State.INACTIF || current == State.PRET) status += " · Recalage requis";
        return "SceneVibe · " + timeLabel(clock == null ? 0 : clock.position()) + " · " + status;
    }

    /** Release the command window without any clock transition. */
    private void closeControls() {
        AlertDialog closing = controls;
        controls = null;
        if (closing != null) closing.dismiss();
    }

    /** Explain a refused command on screen and keep a local diagnostic. */
    private void refuse(String reason) {
        trace("COMMAND_REFUSED " + reason);
        Toast.makeText(this, reason, Toast.LENGTH_SHORT).show();
    }

    /** Freeze once and remember a pause explicitly requested during an edit. */
    private void pause() {
        if (activeEdit != null) activeEdit.pauseRequested = true;
        if (clock != null) clock.pause();
        if (pendingTick != null) {
            handler.removeCallbacks(pendingTick);
            pendingTick = null;
        }
    }

    /** Freeze local effects and open the 001-C window with an isolated transaction. */
    private void openEditor(boolean resumeOnCommit) {
        openEditor(resumeOnCommit, true);
    }

    /** Keep explicit menu choices paused on cancellation, preserving legacy command policy. */
    private void openEditor(boolean resumeOnCommit, boolean restoreOnCancel) {
        if (clock == null || !clock.editable() || !Settings.canDrawOverlays(this)) {
            trace("OPEN_REFUSED");
            refuse("Piste indisponible ou superposition non autorisée.");
            return;
        }
        if (panel.isOpen()) return;
        closeControls();
        Edit edit = new Edit(clock, selection, resumeOnCommit, restoreOnCancel);
        activeEdit = edit;
        if (!panel.openManual(edit) && activeEdit == edit) {
            edit.cancel();
            windowClosed();
        }
    }

    /** Resume only the local editorial clock after the window is removed. */
    private void windowClosed() {
        activeEdit = null;
        trace("WINDOW_CLOSED");
        if (resumeAfterClose) {
            resumeAfterClose = false;
            if (shortcutOwner == this) resume();
        }
    }

    /** Apply an explicit local resume and arm one generation-bound tick. */
    private void resume() {
        if (activeEdit != null) {
            refuse("Termine le recalage avant de reprendre.");
            return;
        }
        if (clock == null || !clock.editable() || !Settings.canDrawOverlays(this)) {
            refuse("Piste indisponible ou superposition non autorisée.");
            return;
        }
        if (clock.running()) return;
        clock.resume();
        if (!clock.running() && clock.position() < clock.horizon()) {
            refuse("Valide une position avant de reprendre.");
        }
        scheduleTick();
    }

    /** Submit owner-thread observations while the same local activation advances. */
    private void scheduleTick() {
        if (pendingTick != null || clock == null || !clock.running()) return;
        NetflixManualClock captured = clock;
        long generation = captured.generation();
        pendingTick = () -> {
            pendingTick = null;
            if (clock != captured || captured.generation() != generation) return;
            if (!Settings.canDrawOverlays(this)) {
                stopSession();
                return;
            }
            captured.tick(generation);
            if (controls != null) controls.setTitle(controlTitle());
            scheduleTick();
        };
        handler.postDelayed(pendingTick, 100);
    }

    /** Stop local rendering and all future callbacks when Android retires this service. */
    @Override public void onDestroy() {
        boolean wasOwner = shortcutOwner == this;
        retireShortcutOwner();
        destroyed = true;
        resumeAfterClose = false;
        decoderWorker.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        if (clock != null) clock.revoke();
        closeControls();
        if (panel != null) panel.close();
        if (wasOwner) publishLaunchStatus(LaunchStatus.STOPPED);
        super.onDestroy();
    }

    /** The experimental command service exposes no binder. */
    @Override public IBinder onBind(Intent intent) { return null; }

    /** Record local state only, without observing or commanding the Netflix player. */
    private static void trace(String event) { Log.i("SceneVibeManual003C", event); }

    /** Detached candidate with explicit commit and cancel policies. */
    private final class Edit implements CompanionControlPanel.ManualEdit {
        private final NetflixManualClock bound;
        private final NetflixLocalAdmission.Selection selected;
        private final boolean priorRunning;
        private final boolean resumeOnCommit;
        private final boolean restoreOnCancel;
        private final long generation;
        private final long initial;
        private long candidate;
        private boolean finished;
        private boolean pauseRequested;

        /** Capture the exact owner state after freezing the editorial clock. */
        Edit(NetflixManualClock bound, NetflixLocalAdmission.Selection selected,
                boolean resumeOnCommit, boolean restoreOnCancel) {
            this.bound = bound;
            this.selected = selected;
            this.resumeOnCommit = resumeOnCommit;
            this.restoreOnCancel = restoreOnCancel;
            priorRunning = bound.running();
            pause();
            initial = bound.position();
            candidate = initial;
            generation = bound.generation();
        }

        /**
         * Compact single-line status used as the panel's data source and action label:
         * the frozen current editorial time, the candidate the viewer is building, and
         * the explicitly chosen post-OK policy. The large current-vs-candidate display is
         * drawn separately by the panel through {@link #currentLabel()} and
         * {@link #candidateLabel()}; this string keeps the historical substrings
         * ("Actuel", "candidat", "reprise après OK" / "reste en pause") intact.
         */
        @Override public String label() {
            return "EN PAUSE · Actuel " + timeLabel(initial) + " · candidat " + timeLabel(candidate)
                    + (resumeOnCommit && !pauseRequested ? " · reprise après OK" : " · reste en pause");
        }

        /**
         * The Track's CURRENT position, projected from the frozen clock snapshot only.
         * SceneVibe shows where its own track stands and never asserts the film's time.
         */
        @Override public String currentLabel() {
            return timeLabel(initial);
        }

        /**
         * The Track's NEW (candidate) position the viewer is dialling in to match the
         * time displayed by their film. It is the captured candidate, not any detected or
         * auto-computed film/Track offset.
         */
        @Override public String candidateLabel() {
            return timeLabel(candidate);
        }

        /** Clamp the requested change to the verified track horizon. */
        @Override public void adjust(long deltaMs) {
            candidate = Math.max(0, Math.min(bound.horizon(), candidate + deltaMs));
        }

        /** Reanchor exactly at OK and defer any local resume until window removal. */
        @Override public boolean commit() {
            if (finished || clock != bound || !Settings.canDrawOverlays(NetflixManualSyncService.this)) return false;
            try {
                bound.seek(selected.trackId, selected.revision, generation, candidate);
            } catch (IllegalStateException stale) {
                trace("MANUAL_COMMIT_REFUSED");
                return false;
            }
            finished = true;
            resumeAfterClose = resumeOnCommit && !pauseRequested;
            trace("MANUAL_COMMITTED");
            return true;
        }

        /** Drop the candidate and restore the pre-edit local running state only for legacy commands. */
        @Override public void cancel() {
            if (finished) return;
            finished = true;
            resumeAfterClose = restoreOnCancel && priorRunning && !pauseRequested && clock == bound && !destroyed;
        }
    }
}
