package com.scenevibe.tvcompanionpoc;

import java.util.HashMap;
import java.util.Map;

/**
 * Android-independent runtime "regie" that coordinates the cache/manifest layer, the EXISTING
 * {@link com.scenevibe.tvcompanionpoc.calendar.MediaCalendarScheduler} events, and the {@link SceneRenderer} (user sections 9, 10,
 * 12, 13, 14, 30). It is the single decision point for WHEN a manifested Video scene is shown
 * or hidden.
 *
 * <p>The non-responsibilities below describe the unchanged manifested MEDIA path. The explicit,
 * OFF-by-default E5 local qualification path delegates decoder and transport operations through
 * optional ports while this controller remains the sole presentation/lifecycle owner.</p>
 * <p>Deliberate non-responsibilities, so each collaborator keeps its single role:</p>
 * <ul>
 *   <li>It owns NO clock. Timing arrives only as scheduler events forwarded in through
 *       {@link #onEventDue(String)} / {@link #onPlayback(boolean, boolean)} /
 *       {@link #onEligibility(boolean)}. It never creates a {@code Handler}/{@code postDelayed}
 *       timer and never re-derives media position.</li>
 *   <li>It NEVER commands the video player: there is no reference to transport controls or any
 *       media command here.</li>
 *   <li>It draws nothing itself. All rendering goes through the {@link SceneSink} seam, so this
 *       class carries no {@code android.*} import and is fully JVM-testable. In production the
 *       {@link OverlayService} adapter (FEAT-004) implements {@link SceneSink} by delegating to
 *       {@link SceneRenderer}; tests use a fake that records calls.</li>
 * </ul>
 *
 * <p>Exactly one visual scene is active at any time (section 10/30). A generation guard
 * (section 14/21) makes revision replacement safe: a late scheduler callback that was produced
 * under a superseded revision/generation is ignored and can never resurrect an old scene.</p>
 *
 * <p>This type is not thread-safe by itself; it is driven from the same single callback chain
 * as the scheduler (the Android main thread in production, the test thread in JVM tests), which
 * already serializes delivery. Methods are {@code synchronized} as a cheap safety net so a
 * late callback crossing threads still observes a consistent generation.</p>
 */
final class SceneRuntimeController {

    /**
     * Thin drawing seam so the controller stays Android-independent and JVM-testable. The
     * production adapter delegates to {@link SceneRenderer}; a test fake records calls. The
     * controller calls {@link #preflight(OverlayManifest.Scene)} before {@link #show} and
     * suppresses the show when preflight returns false (e.g. a required local asset is
     * missing), so a half-rendered scene is never displayed.
     */
    interface SceneSink {
        /** Returns true when the scene can be shown now (assets/permission present). */
        boolean preflight(OverlayManifest.Scene scene);
        /** Render the already-validated scene. */
        void show(OverlayManifest.Scene scene);
        /** Hide one specific scene (bounded exit). */
        void hide(OverlayManifest.Scene scene);
        /** Immediately drop any visible scene. */
        void hideAll();
        /** Present graphics around an already prepared local host; unsupported sinks refuse. */
        default void showLocal(LocalInterludePlan plan) {
            throw new IllegalStateException("LOCAL_HOST_UNSUPPORTED");
        }
    }

    private final SceneSink sink;

    /** The active manifest, indexed by scene id for O(1) lookup; null when unloaded. */
    private Map<String, OverlayManifest.Scene> scenesById;
    private OverlayManifest activeManifest;
    private long activeRevision;
    /**
     * Monotonic guard bumped on every load/unload/replace. A scheduler callback is only honored
     * while the generation it is checked against still matches; this is what disarms a late
     * revision-1 event after revision 2 has taken over.
     */
    private long generation;

    /** The single scene currently shown, or null. Enforces exactly-one-visual-scene. */
    private OverlayManifest.Scene visibleScene;
    /** Eligibility gate mirrored from the scheduler. armed != visible. */
    private boolean eligible = true;
    /** Last playback state observed from the scheduler (diagnostic/documentation only). */
    private boolean playing;
    private boolean pauseFreezesDisplay;

    /** Bind the drawing seam without creating a clock, player or visible scene. */
    SceneRuntimeController(SceneSink sink) {
        if (sink == null) throw new IllegalArgumentException("sink");
        this.sink = sink;
    }

    /**
     * Loads the active manifest for a revision and indexes its scenes by id (reusing the exact
     * same by-id indexing rule as {@link VideoOverlayManifestBridge#validate}). Loading bumps
     * the generation and drops any scene left visible from a prior manifest, so a stale
     * in-flight callback cannot keep an old scene on screen. The caller is responsible for
     * having validated the manifest against its runtime track first (FEAT-002 bridge/cache);
     * the controller trusts an already-installed revision.
     */
    synchronized void loadManifest(long revision, OverlayManifest manifest) {
        cancelLocal("REVISION_REPLACED");
        if (manifest == null) {
            unload();
            return;
        }
        dropVisibleScene();
        Map<String, OverlayManifest.Scene> index = new HashMap<>();
        for (OverlayManifest.Scene scene : manifest.scenes) {
            // Last-writer-wins is acceptable here: the bridge already rejected duplicate ids
            // before install, so a loaded manifest has unique scene ids.
            index.put(scene.id, scene);
        }
        this.activeManifest = manifest;
        this.scenesById = index;
        this.activeRevision = revision;
        this.generation++;
    }

    /**
     * Unloads the active manifest and disarms any visible scene. The durable manifest/track in
     * the cache is NOT touched here (section 13): this only clears in-memory regie state. After
     * unload, {@link #hasActiveManifest()} is false and no due event can show anything until a
     * new manifest is loaded.
     */
    synchronized void unload() {
        cancelLocal("UNLOAD");
        dropVisibleScene();
        this.activeManifest = null;
        this.scenesById = null;
        this.activeRevision = 0;
        this.generation++;
    }

    /**
     * Replaces the active manifest with a newer revision (section 14). It fully disarms the old
     * revision (hide any visible old-revision scene, bump the generation so late old-revision
     * callbacks are ignored) and loads the new one atomically from the controller's point of
     * view. Only new-revision scenes can show afterwards.
     */
    synchronized void replaceRevision(long revision, OverlayManifest manifest) {
        loadManifest(revision, manifest);
    }

    /**
     * The sole entry point for "a Video comment became due". It looks up the matching scene by
     * {@code eventId}; if found it preflights then shows it, if not found it does NOTHING (no
     * render). Idempotent for the same scene: a second due callback for the already-visible
     * scene is a no-op, never a duplicate overlay. There is no generation argument here because
     * the scheduler delivers synchronously under the current generation; the
     * {@link #onEventDue(String, long)} overload exists for tests that simulate
     * a late callback carrying a stale generation.
     */
    synchronized void onEventDue(String eventId) {
        onEventDue(eventId, generation);
    }

    /**
     * Generation-guarded form (section 14/21). {@code callbackGeneration} is the generation that
     * was active when the scheduler produced the event. A callback whose generation no longer
     * matches the current one is a late/superseded event: it is ignored and can never resurrect
     * an old scene. The service captures {@link #currentGeneration()} once during manifest ARM and forwards that fixed binding.
     */
    synchronized void onEventDue(String eventId, long callbackGeneration) {
        if (isLocalPresentationActive()) {
            localRecord("CONCURRENT_SCENE_REFUSED");
            return;
        }
        if (eventId == null) return;
        // Superseded revision/generation: a stale callback must never show an old scene.
        if (callbackGeneration != generation) return;
        // No active manifest (unloaded/stopped) means Case A legacy path owns the comment, or
        // the service has stopped; either way the regie shows nothing.
        if (scenesById == null) return;
        // armed != visible: without current eligibility a due event does not become visible.
        if (!eligible) return;
        OverlayManifest.Scene scene = scenesById.get(eventId);
        // Unknown scene id => no render (nothing matches this comment in this manifest).
        if (scene == null) return;
        // Idempotent: a double callback for the already-visible scene is a safe no-op.
        if (visibleScene != null && visibleScene.id.equals(scene.id)) return;
        // Exactly one visual scene: replace any other visible scene deterministically.
        if (visibleScene != null) {
            sink.hide(visibleScene);
            visibleScene = null;
        }
        if (!sink.preflight(scene)) return;
        sink.show(scene);
        visibleScene = scene;
    }

    /**
     * A scene reached the end of its window (or the scheduler asked to hide it). Hides the scene
     * only if it is the currently visible one, under the current generation. A stale hide for an
     * already-replaced scene is a no-op.
     */
    synchronized void onEventExpired(String eventId, long callbackGeneration) {
        if (isLocalPresentationActive()) return;
        if (eventId == null) return;
        if (callbackGeneration != generation) return;
        if (visibleScene == null) return;
        if (!visibleScene.id.equals(eventId)) return;
        dropVisibleScene();
    }

    /** Generation-current convenience overload. */
    synchronized void onEventExpired(String eventId) {
        onEventExpired(eventId, generation);
    }

    /**
     * Eligibility transition forwarded from {@link com.scenevibe.tvcompanionpoc.calendar.MediaCalendarScheduler.Sink#onEligibility(String, boolean)}
     * (section 13). On loss (false) the visible scene is hidden IMMEDIATELY (armed != visible),
     * but the loaded in-memory manifest and the durable cache are kept intact so a later due
     * event can show again when eligibility returns. On regain (true) nothing is forced visible;
     * a subsequent due event drives the next show per existing policy.
     */
    synchronized void onEligibility(boolean nowEligible) {
        this.eligible = nowEligible;
        // The local command is not an event in the installed Track. Its independent, bound
        // native lease handles session/program loss; a different Track's mismatch cannot
        // transfer ownership or reinterpret this explicit qualification as a MEDIA event.
        if (isLocalPresentationActive()) return;
        if (!nowEligible) {
            dropVisibleScene();
            sink.hideAll();
        }
    }

    /**
     * Playback transition forwarded from the scheduler (section 12). The controller reacts only
     * to the extent the legacy path does: it records the state and does NOT add a second clock.
     * When {@code pauseFreezesDisplay} is set and playback is paused, the controller does not
     * force-expire the visible scene by wall time; expiry/visibility continue to be driven solely
     * by scheduler events. This method intentionally performs no show/hide of its own.
     */
    synchronized void onPlayback(boolean nowPlaying, boolean freeze) {
        this.playing = nowPlaying;
        this.pauseFreezesDisplay = freeze;
        // No wall-clock expiry here: the scheduler remains the sole temporal authority. A frozen
        // (paused) scene stays coherent because nothing in the controller drains its duration.
    }

    /**
     * Deterministic Case A vs Case B selector (section 10/30), mirroring the static-predicate
     * style of {@link OverlayService#shouldShowOnEntry}. Returns true (Case B: regie +
     * SceneRenderer) only when a valid manifest is loaded for the given active revision; false
     * (Case A: legacy OverlayRenderer) otherwise. The service uses exactly this signal so the
     * two visual paths are never both active for the same comment.
     *
     * @param manifestLoadedRevision the revision whose manifest is currently loaded (0 if none)
     * @param activeRevision the revision currently driving the scheduler
     */
    static boolean shouldUseSceneRenderer(long manifestLoadedRevision, long activeRevision) {
        return activeRevision > 0 && manifestLoadedRevision == activeRevision;
    }

    /**
     * Instance form of the Case A/B predicate: true when this controller holds a valid manifest
     * for {@code activeRevision}. This is the authoritative signal the OverlayService consults to
     * choose Case B over the legacy Case A for the comment stream of {@code activeRevision}.
     */
    synchronized boolean isSceneRendererActiveFor(long activeRevision) {
        return hasActiveManifest()
                && shouldUseSceneRenderer(this.activeRevision, activeRevision);
    }

    /** True when a manifest is loaded and indexed (Case B is possible). */
    synchronized boolean hasActiveManifest() {
        return scenesById != null;
    }

    /** The revision whose manifest is currently loaded, or 0 when none. */
    synchronized long activeRevision() {
        return activeRevision;
    }

    /** The current generation; capture once during ARM to detect stale callbacks later. */
    synchronized long currentGeneration() {
        return generation;
    }

    /** True when a scene is currently shown. Test/diagnostic aid for the one-visual-scene rule. */
    synchronized boolean hasVisibleScene() {
        return visibleScene != null;
    }

    /** The id of the currently visible scene, or null. Test/diagnostic aid. */
    synchronized String visibleSceneId() {
        return visibleScene == null ? null : visibleScene.id;
    }

    /** Local decoder/transport lifecycle owned here; terminal values are diagnostic only. */
    enum LocalPhase {
        IDLE, PREPARING, PAUSE_REQUESTED, PLAYER_STARTING, PLAYER_PLAYING,
        RESUME_REQUESTED, SUCCESS, REFUSED, RECOVERY_REQUIRED
    }

    /** Monotone execution-budget seam; never an editorial clock or scheduler. */
    interface ExecutionClock {
        /** Return elapsed time used only for finite preparation/transport/player budgets. */
        long nowMs();
    }

    private LocalVideoPort localPlayer;
    private NativeTransportOwner localTransport;
    private ExecutionClock executionClock;
    private NativeTransportOwner.Diagnostics localDiagnostics;
    private boolean localEnabled;
    private LocalInterludePlan localPlan;
    private LocalPhase localPhase = LocalPhase.IDLE;
    private long localAttempt;
    private long localRevisionGeneration;
    private long localDeadline;
    private long localStartedAt;

    /**
     * Bind optional E5 seams without creating a player/session/window or taking native control.
     * Production calls this only for the explicit qualification action and opt-in debug build.
     */
    synchronized void configureLocal(boolean enabled, LocalVideoPort player,
            NativeTransportOwner transport, ExecutionClock clock,
            NativeTransportOwner.Diagnostics diagnostics) {
        if (isLocalPresentationActive()) throw new IllegalStateException("LOCAL_ALREADY_ACTIVE");
        localEnabled = enabled;
        localPlayer = player;
        localTransport = transport;
        executionClock = clock;
        localDiagnostics = diagnostics;
    }

    /** Admit a manual local plan while the existing graphical slot is idle; never preempt. */
    synchronized boolean requestLocal(LocalInterludePlan plan) {
        if (!localEnabled || localPlayer == null || localTransport == null || executionClock == null
                || localDiagnostics == null) return false;
        if (isLocalPresentationActive() || visibleScene != null || localTransport.hasLease()) {
            localRecord("REQUEST_REFUSED_BUSY");
            return false;
        }
        localAttempt++;
        localRevisionGeneration = generation;
        localPlan = plan;
        localPhase = LocalPhase.PREPARING;
        localRecord("REQUEST");
        if (plan == null || !plan.vodConfirmed || !sink.preflight(plan.scene)) {
            finishLocal(LocalPhase.REFUSED, "LOCAL_PREFLIGHT_REFUSED");
            return false;
        }
        localDeadline = executionClock.nowMs() + plan.policy.preparationTimeoutMs;
        localRecord("PREFLIGHT");
        try {
            localPlayer.prepare(plan, localAttempt, localCallbacks);
            return isLocalPresentationActive();
        } catch (RuntimeException failed) {
            finishLocal(LocalPhase.REFUSED, "PREPARATION_FAILED");
            return false;
        }
    }

    /** Route decoder readiness to the only owner permitted to ask for native PAUSE. */
    private synchronized void localReady(long attempt) {
        if (!localCurrent(attempt) || localPhase != LocalPhase.PREPARING) return;
        long now = executionClock.nowMs();
        if (now > localDeadline || localPlayer.state() != LocalVideoPort.State.READY) {
            finishLocal(LocalPhase.REFUSED, "PREPARATION_NOT_READY");
            return;
        }
        localPhase = LocalPhase.PAUSE_REQUESTED;
        localDeadline = now + localPlan.policy.pauseTimeoutMs;
        localRecord("PLAYER_READY");
        try {
            if (!localTransport.requestPause(attempt, now, localPlan.vodConfirmed)) {
                finishLocal(LocalPhase.REFUSED, "NATIVE_ADMISSION_" + localTransport.reason().name());
            }
        } catch (RuntimeException failed) {
            finishLocal(LocalPhase.RECOVERY_REQUIRED, "PAUSE_DISPATCH_ERROR");
        }
    }

    /** A real player start follows confirmed PAUSED; no prepared callback implies playback. */
    private synchronized void localStarted(long attempt) {
        if (!localCurrent(attempt) || localPhase != LocalPhase.PLAYER_STARTING) return;
        if (localPlayer.state() != LocalVideoPort.State.PLAYING) {
            finishLocal(LocalPhase.RECOVERY_REQUIRED, "START_NOT_OBSERVED");
            return;
        }
        localPhase = LocalPhase.PLAYER_PLAYING;
        localStartedAt = executionClock.nowMs();
        localDeadline = localStartedAt + localPlan.policy.clipWatchdogMs;
        localRecord("PLAYER_STARTED");
    }

    /** EOF closes the decoder first, then asks the lease guard for a single native PLAY. */
    private synchronized void localEof(long attempt) {
        if (!localCurrent(attempt) || localPhase != LocalPhase.PLAYER_PLAYING) return;
        if (localPlayer.state() != LocalVideoPort.State.STOPPED) {
            finishLocal(LocalPhase.RECOVERY_REQUIRED, "EOF_PLAYER_NOT_NORMAL");
            return;
        }
        long now = executionClock.nowMs();
        if (now > localDeadline || now - localStartedAt < LocalInterludePlan.DURATION_MS - 500L) {
            finishLocal(LocalPhase.RECOVERY_REQUIRED, "EOF_OUTSIDE_FIXTURE_BUDGET");
            return;
        }
        localRecord("EOF_NORMAL");
        try {
            localPlayer.release(attempt);
            localPhase = LocalPhase.RESUME_REQUESTED;
            localDeadline = now + localPlan.policy.playingTimeoutMs;
            if (!localTransport.requestPlay(attempt, now, true)) {
                finishLocal(LocalPhase.RECOVERY_REQUIRED, "RESUME_REFUSED_" + localTransport.reason().name());
            }
        } catch (RuntimeException failed) {
            finishLocal(LocalPhase.RECOVERY_REQUIRED, "RESUME_DISPATCH_ERROR");
        }
    }

    /** Any decoder/surface error cleans up and never sends native PLAY. */
    private synchronized void localError(long attempt, String reason) {
        if (!localCurrent(attempt)) return;
        finishLocal(localTransport.hasLease() ? LocalPhase.RECOVERY_REQUIRED : LocalPhase.REFUSED,
                reason == null ? "PLAYER_ERROR" : reason);
    }

    /**
     * Observe the current lease and finite execution budgets. Android supplies a serialized
     * poll; this method schedules nothing and never produces an editorial MEDIA/WALL event.
     */
    synchronized void tickLocal() {
        if (!isLocalPresentationActive()) return;
        long now = executionClock.nowMs();
        if (localRevisionGeneration != generation) {
            finishLocal(LocalPhase.RECOVERY_REQUIRED, "GENERATION_CHANGED");
            return;
        }
        if (now > localDeadline) {
            finishLocal(localTransport.hasLease() ? LocalPhase.RECOVERY_REQUIRED : LocalPhase.REFUSED,
                    "EXECUTION_TIMEOUT");
            return;
        }
        try {
            if (localPhase == LocalPhase.PAUSE_REQUESTED) {
                NativeTransportOwner.Result result = localTransport.confirmPaused(localAttempt, now);
                if (result == NativeTransportOwner.Result.REFUSED) {
                    finishLocal(LocalPhase.RECOVERY_REQUIRED, "PAUSE_REFUSED_" + localTransport.reason().name());
                } else if (result == NativeTransportOwner.Result.CONFIRMED) {
                    sink.showLocal(localPlan);
                    visibleScene = localPlan.scene;
                    localPhase = LocalPhase.PLAYER_STARTING;
                    localDeadline = now + localPlan.policy.preparationTimeoutMs;
                    localPlayer.play(localAttempt);
                }
            } else if (localPhase == LocalPhase.PLAYER_STARTING || localPhase == LocalPhase.PLAYER_PLAYING) {
                if (!localTransport.pauseStillOwned(localAttempt, now)) {
                    finishLocal(LocalPhase.RECOVERY_REQUIRED, "LEASE_INVALID_" + localTransport.reason().name());
                }
            } else if (localPhase == LocalPhase.RESUME_REQUESTED) {
                NativeTransportOwner.Result result = localTransport.confirmPlaying(localAttempt, now);
                if (result == NativeTransportOwner.Result.CONFIRMED) {
                    finishLocal(LocalPhase.SUCCESS, "SUCCESS");
                } else if (result == NativeTransportOwner.Result.REFUSED) {
                    finishLocal(LocalPhase.RECOVERY_REQUIRED, "PLAY_NOT_CONFIRMED_" + localTransport.reason().name());
                }
            }
        } catch (RuntimeException failed) {
            finishLocal(LocalPhase.RECOVERY_REQUIRED, "NATIVE_OBSERVATION_ERROR");
        }
    }

    /** Cancel an active local plan; STOP/expiry/unload never authorizes automatic native PLAY. */
    synchronized void cancelLocal(String reason) {
        if (isLocalPresentationActive()) {
            finishLocal(localTransport.hasLease() ? LocalPhase.RECOVERY_REQUIRED : LocalPhase.REFUSED,
                    reason == null ? "STOP" : reason);
        }
    }

    /** True for the one active presentation, including preparation and PLAY confirmation. */
    synchronized boolean isLocalPresentationActive() {
        return localPlan != null;
    }

    /** Expose the bounded lifecycle for diagnostics and tests, not another source of state. */
    synchronized LocalPhase localPhase() {
        return localPhase;
    }

    /** Capture this player's generation for immutable Android callbacks. */
    synchronized long localAttempt() {
        return localAttempt;
    }

    /** Release player before host/Surface removal and invalidate callbacks before any teardown. */
    private void finishLocal(LocalPhase terminal, String reason) {
        if (localPlan == null) return;
        long retired = localAttempt;
        localPlan = null;
        localPhase = terminal;
        visibleScene = null;
        localRecord(reason);
        try {
            localPlayer.release(retired);
        } finally {
            try {
                localTransport.release(retired);
            } finally {
                sink.hideAll();
                localRecord("CLEANUP");
            }
        }
    }

    /** An old ready/EOF/error can never touch a newer scene, lease or released player. */
    private boolean localCurrent(long attempt) {
        return localPlan != null && attempt == localAttempt && localRevisionGeneration == generation;
    }

    /** Record only bounded codes; the diagnostic adapter never receives authored scene text. */
    private void localRecord(String event) {
        if (localDiagnostics != null) {
            localDiagnostics.record(event, localAttempt, null,
                    localTransport == null ? NativeTransportOwner.Reason.NONE : localTransport.reason());
        }
    }

    private final LocalVideoPort.Callbacks localCallbacks = new LocalVideoPort.Callbacks() {
        /** Preparation belongs only to the captured attempt. */
        @Override public void ready(long attempt) { localReady(attempt); }
        /** Playback belongs only to the captured attempt. */
        @Override public void started(long attempt) { localStarted(attempt); }
        /** Only a real normal EOF enters guarded resume. */
        @Override public void eof(long attempt) { localEof(attempt); }
        /** Decoder failure never becomes EOF. */
        @Override public void error(long attempt, String reason) { localError(attempt, reason); }
    };

    /** Hide the current scene through the sink and clear the one-visual-scene slot. */
    private void dropVisibleScene() {
        if (visibleScene != null) {
            sink.hide(visibleScene);
            visibleScene = null;
        }
    }
}
