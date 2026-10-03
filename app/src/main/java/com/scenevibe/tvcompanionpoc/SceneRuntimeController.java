package com.scenevibe.tvcompanionpoc;

import java.util.HashMap;
import java.util.Map;

/**
 * Android-independent runtime "regie" that coordinates the cache/manifest layer, the EXISTING
 * {@link MediaSyncedTrackScheduler} events, and the {@link SceneRenderer} (user sections 9, 10,
 * 12, 13, 14, 30). It is the single decision point for WHEN a manifested Video scene is shown
 * or hidden.
 *
 * <p>Deliberate non-responsibilities, so each collaborator keeps its single role:</p>
 * <ul>
 *   <li>It owns NO clock. Timing arrives only as scheduler events forwarded in through
 *       {@link #onCommentDue(ScheduledTrack.Event)} / {@link #onPlayback(boolean, boolean)} /
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
     * {@code event.id}; if found it preflights then shows it, if not found it does NOTHING (no
     * render). Idempotent for the same scene: a second due callback for the already-visible
     * scene is a no-op, never a duplicate overlay. There is no generation argument here because
     * the scheduler delivers synchronously under the current generation; the
     * {@link #onCommentDue(ScheduledTrack.Event, long)} overload exists for tests that simulate
     * a late callback carrying a stale generation.
     */
    synchronized void onCommentDue(ScheduledTrack.Event event) {
        onCommentDue(event, generation);
    }

    /**
     * Generation-guarded form (section 14/21). {@code callbackGeneration} is the generation that
     * was active when the scheduler produced the event. A callback whose generation no longer
     * matches the current one is a late/superseded event: it is ignored and can never resurrect
     * an old scene. Use {@link #currentGeneration()} to tag an event at production time.
     */
    synchronized void onCommentDue(ScheduledTrack.Event event, long callbackGeneration) {
        if (event == null) return;
        // Superseded revision/generation: a stale callback must never show an old scene.
        if (callbackGeneration != generation) return;
        // No active manifest (unloaded/stopped) means Case A legacy path owns the comment, or
        // the service has stopped; either way the regie shows nothing.
        if (scenesById == null) return;
        // armed != visible: without current eligibility a due event does not become visible.
        if (!eligible) return;
        OverlayManifest.Scene scene = scenesById.get(event.id);
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
    synchronized void onCommentExpired(ScheduledTrack.Event event, long callbackGeneration) {
        if (event == null) return;
        if (callbackGeneration != generation) return;
        if (visibleScene == null) return;
        if (!visibleScene.id.equals(event.id)) return;
        dropVisibleScene();
    }

    /** Generation-current convenience overload. */
    synchronized void onCommentExpired(ScheduledTrack.Event event) {
        onCommentExpired(event, generation);
    }

    /**
     * Eligibility transition forwarded from {@link MediaSyncedTrackScheduler.Listener#onEligibility}
     * (section 13). On loss (false) the visible scene is hidden IMMEDIATELY (armed != visible),
     * but the loaded in-memory manifest and the durable cache are kept intact so a later due
     * event can show again when eligibility returns. On regain (true) nothing is forced visible;
     * a subsequent due event drives the next show per existing policy.
     */
    synchronized void onEligibility(boolean nowEligible) {
        this.eligible = nowEligible;
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

    /** The current generation; tag a scheduler event with this to detect stale callbacks later. */
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

    /** Hide the current scene through the sink and clear the one-visual-scene slot. */
    private void dropVisibleScene() {
        if (visibleScene != null) {
            sink.hide(visibleScene);
            visibleScene = null;
        }
    }
}
