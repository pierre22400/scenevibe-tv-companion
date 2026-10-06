package com.scenevibe.tvcompanionpoc;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Deterministic, Android-free JVM tests for {@link SceneRuntimeController}, the runtime regie
 * (user sections 9, 10, 12, 13, 14, 30, plus the section 20-D cases 16-22 and the section 21
 * async-risk cases). The controller owns no clock and draws nothing: these tests feed scheduler
 * events directly and observe a {@link RecordingSink} fake, with no Android and no real time.
 */
public final class SceneRuntimeControllerTest {

    /** Fake SceneSink that records every call so a test can assert exactly-one-visual-scene. */
    private static final class RecordingSink implements SceneRuntimeController.SceneSink {
        final List<String> shown = new ArrayList<>();
        final List<String> hidden = new ArrayList<>();
        int hideAllCalls;
        boolean preflightResult = true;
        int preflightCalls;
        /** Running count of scenes currently on screen per this fake's view of show/hide. */
        int visibleCount;

        @Override public boolean preflight(OverlayManifest.Scene scene) {
            preflightCalls++;
            return preflightResult;
        }
        @Override public void show(OverlayManifest.Scene scene) {
            shown.add(scene.id);
            visibleCount++;
            // A SceneRenderer only ever holds one scene; prove the regie never double-shows.
            assertTrue("exactly one visual scene must be active", visibleCount <= 1);
        }
        @Override public void hide(OverlayManifest.Scene scene) {
            hidden.add(scene.id);
            if (visibleCount > 0) visibleCount--;
        }
        @Override public void hideAll() {
            hideAllCalls++;
            visibleCount = 0;
        }
        String lastShown() { return shown.isEmpty() ? null : shown.get(shown.size() - 1); }
    }

    // ---- fixture builders ---------------------------------------------------------------

    private static OverlayManifest.Scene scene(String id, long startMs, long durationMs) {
        return new OverlayManifest.Scene(id, startMs, durationMs, new ArrayList<>());
    }

    private static OverlayManifest manifest(String trackId, OverlayManifest.Scene... scenes) {
        return new OverlayManifest("video:" + trackId, "video", trackId, "media", "freeze",
                Arrays.asList(scenes));
    }

    private static ScheduledTrack.Event comment(String id, long startMs, long durationMs) {
        return new ScheduledTrack.Event(id, "text-" + id, startMs, durationMs, null);
    }

    // ---- section 20-D cases 16-22 -------------------------------------------------------

    /** Case 16: comment-1 due => scene comment-1 is shown exactly once. */
    @Test public void commentDueShowsMatchingScene() {
        RecordingSink sink = new RecordingSink();
        SceneRuntimeController regie = new SceneRuntimeController(sink);
        regie.loadManifest(1, manifest("track-1", scene("comment-1", 12_000, 6_000)));

        regie.onEventDue(comment("comment-1", 12_000, 6_000).id);

        assertEquals(Arrays.asList("comment-1"), sink.shown);
        assertEquals("comment-1", regie.visibleSceneId());
        assertTrue(regie.hasVisibleScene());
    }

    /** Case 17: unknown scene id => no render at all. */
    @Test public void unknownSceneIdRendersNothing() {
        RecordingSink sink = new RecordingSink();
        SceneRuntimeController regie = new SceneRuntimeController(sink);
        regie.loadManifest(1, manifest("track-1", scene("comment-1", 12_000, 6_000)));

        regie.onEventDue(comment("comment-UNKNOWN", 12_000, 6_000).id);

        assertTrue(sink.shown.isEmpty());
        assertEquals(0, sink.preflightCalls);
        assertFalse(regie.hasVisibleScene());
    }

    /** Case 18: a callback tagged with an old generation is ignored (nothing new shown). */
    @Test public void staleGenerationCallbackIsIgnored() {
        RecordingSink sink = new RecordingSink();
        SceneRuntimeController regie = new SceneRuntimeController(sink);
        regie.loadManifest(1, manifest("track-1", scene("comment-1", 12_000, 6_000)));
        long oldGeneration = regie.currentGeneration();

        // A new revision supersedes generation; the old-gen callback must not resurrect a scene.
        regie.replaceRevision(2, manifest("track-1", scene("comment-9", 1_000, 2_000)));
        regie.onEventDue(comment("comment-1", 12_000, 6_000).id, oldGeneration);

        assertTrue("stale-generation callback must show nothing", sink.shown.isEmpty());
        assertFalse(regie.hasVisibleScene());
    }

    /** Case 19: replacing the revision disarms the old visible scene and ignores old-gen events. */
    @Test public void replaceRevisionDisarmsOldSceneAndIgnoresLateOldCallbacks() {
        RecordingSink sink = new RecordingSink();
        SceneRuntimeController regie = new SceneRuntimeController(sink);
        regie.loadManifest(1, manifest("track-1", scene("comment-1", 12_000, 6_000)));
        long gen1 = regie.currentGeneration();
        regie.onEventDue(comment("comment-1", 12_000, 6_000).id);
        assertEquals("comment-1", regie.visibleSceneId());

        regie.replaceRevision(2, manifest("track-2", scene("comment-2", 3_000, 4_000)));

        // The old revision-1 scene was hidden as part of the replacement.
        assertTrue(sink.hidden.contains("comment-1"));
        assertFalse(regie.hasVisibleScene());

        // A late revision-1 callback (old generation) must never show the old scene.
        regie.onEventDue(comment("comment-1", 12_000, 6_000).id, gen1);
        assertFalse(regie.hasVisibleScene());
        assertEquals("comment-1", sink.lastShown()); // nothing new shown since

        // Only the new revision's scenes can show now.
        regie.onEventDue(comment("comment-2", 3_000, 4_000).id);
        assertEquals("comment-2", regie.visibleSceneId());
    }

    /** Case 20: eligibility loss hides immediately but keeps the loaded manifest intact. */
    @Test public void eligibilityLossHidesButKeepsManifest() {
        RecordingSink sink = new RecordingSink();
        SceneRuntimeController regie = new SceneRuntimeController(sink);
        regie.loadManifest(1, manifest("track-1", scene("comment-1", 12_000, 6_000)));
        regie.onEventDue(comment("comment-1", 12_000, 6_000).id);
        assertTrue(regie.hasVisibleScene());

        regie.onEligibility(false);

        assertEquals("hideAll must fire on eligibility loss", 1, sink.hideAllCalls);
        assertFalse(regie.hasVisibleScene());
        // Durable + in-memory manifest kept: Case B is still selected for this revision.
        assertTrue("manifest must remain loaded after eligibility loss", regie.hasActiveManifest());
        assertTrue(regie.isSceneRendererActiveFor(1));

        // Eligibility returns: a subsequent due event shows again per policy.
        regie.onEligibility(true);
        regie.onEventDue(comment("comment-1", 12_000, 6_000).id);
        assertEquals("comment-1", regie.visibleSceneId());
    }

    /** Case 21: pause/resume keeps Video behavior - no second clock, no wall-expiry on pause. */
    @Test public void pauseDoesNotWallExpireVisibleScene() {
        RecordingSink sink = new RecordingSink();
        SceneRuntimeController regie = new SceneRuntimeController(sink);
        regie.loadManifest(1, manifest("track-1", scene("comment-1", 12_000, 6_000)));
        regie.onEventDue(comment("comment-1", 12_000, 6_000).id);
        assertTrue(regie.hasVisibleScene());

        // Paused with freeze: the controller must not force-expire the scene by wall time.
        regie.onPlayback(false, true);
        assertTrue("a frozen scene must stay visible on pause", regie.hasVisibleScene());
        assertEquals("comment-1", regie.visibleSceneId());

        // Resume keeps the same scene; nothing new rendered, no duplicate.
        regie.onPlayback(true, true);
        assertTrue(regie.hasVisibleScene());
        assertEquals(1, sink.shown.size());
    }

    /** Case 22: exactly one renderer/scene is active and Case A/B selection is deterministic. */
    @Test public void exactlyOneVisualPathSelectedDeterministically() {
        RecordingSink sink = new RecordingSink();
        SceneRuntimeController regie = new SceneRuntimeController(sink);

        // No manifest loaded for the active revision => Case A (legacy OverlayRenderer).
        assertFalse(regie.hasActiveManifest());
        assertFalse(regie.isSceneRendererActiveFor(5));
        assertFalse(SceneRuntimeController.shouldUseSceneRenderer(0, 5));

        // Valid manifest loaded for the active revision => Case B (regie + SceneRenderer).
        regie.loadManifest(5, manifest("track-5", scene("c", 0, 1000)));
        assertTrue(regie.isSceneRendererActiveFor(5));
        assertTrue(SceneRuntimeController.shouldUseSceneRenderer(5, 5));
        // A manifest for a different revision does not select Case B for the active one.
        assertFalse(regie.isSceneRendererActiveFor(6));
        assertFalse(SceneRuntimeController.shouldUseSceneRenderer(5, 6));
    }

    // ---- section 21 async-risk cases ----------------------------------------------------

    /** Render requested after a simulated stop/unload => ignored (no resurrection). */
    @Test public void renderAfterUnloadIsIgnored() {
        RecordingSink sink = new RecordingSink();
        SceneRuntimeController regie = new SceneRuntimeController(sink);
        regie.loadManifest(1, manifest("track-1", scene("comment-1", 12_000, 6_000)));
        long genBeforeUnload = regie.currentGeneration();

        regie.unload(); // service stop / manifest unload

        // A due callback arriving after unload (even current gen) shows nothing.
        regie.onEventDue(comment("comment-1", 12_000, 6_000).id);
        assertFalse(regie.hasVisibleScene());
        // A late callback carrying the pre-unload generation is also ignored.
        regie.onEventDue(comment("comment-1", 12_000, 6_000).id, genBeforeUnload);
        assertTrue(sink.shown.isEmpty());
        assertFalse(regie.hasActiveManifest());
    }

    /** Double callback for the same scene => idempotent single show. */
    @Test public void doubleCallbackForSameSceneIsIdempotent() {
        RecordingSink sink = new RecordingSink();
        SceneRuntimeController regie = new SceneRuntimeController(sink);
        regie.loadManifest(1, manifest("track-1", scene("comment-1", 12_000, 6_000)));

        regie.onEventDue(comment("comment-1", 12_000, 6_000).id);
        regie.onEventDue(comment("comment-1", 12_000, 6_000).id);

        assertEquals("second callback must be a no-op", 1, sink.shown.size());
        assertEquals("comment-1", regie.visibleSceneId());
        assertEquals("no duplicate hide for the same scene", 0, sink.hidden.size());
    }

    /** A new revision received while a scene is visible: old scene hidden, only new can show. */
    @Test public void newRevisionWhileSceneVisibleDisarmsOld() {
        RecordingSink sink = new RecordingSink();
        SceneRuntimeController regie = new SceneRuntimeController(sink);
        regie.loadManifest(1, manifest("track-1", scene("comment-1", 12_000, 6_000)));
        long gen1 = regie.currentGeneration();
        regie.onEventDue(comment("comment-1", 12_000, 6_000).id);
        assertTrue(regie.hasVisibleScene());

        regie.replaceRevision(2, manifest("track-2",
                scene("comment-2", 3_000, 4_000), scene("comment-3", 9_000, 2_000)));

        assertTrue("old scene hidden on replacement", sink.hidden.contains("comment-1"));
        assertFalse(regie.hasVisibleScene());

        // A late revision-1 event is ignored; new revision scenes show normally.
        regie.onEventDue(comment("comment-1", 12_000, 6_000).id, gen1);
        assertFalse(regie.hasVisibleScene());
        regie.onEventDue(comment("comment-3", 9_000, 2_000).id);
        assertEquals("comment-3", regie.visibleSceneId());
        assertEquals(2, sink.shown.size()); // comment-1 then comment-3, never a duplicate
    }

    /** Callback after eligibility loss => ignored while ineligible, allowed after regain. */
    @Test public void callbackWhileIneligibleIsIgnored() {
        RecordingSink sink = new RecordingSink();
        SceneRuntimeController regie = new SceneRuntimeController(sink);
        regie.loadManifest(1, manifest("track-1", scene("comment-1", 12_000, 6_000)));

        regie.onEligibility(false);
        regie.onEventDue(comment("comment-1", 12_000, 6_000).id);
        assertFalse("a due event while ineligible must not show", regie.hasVisibleScene());
        assertTrue(sink.shown.isEmpty());

        regie.onEligibility(true);
        regie.onEventDue(comment("comment-1", 12_000, 6_000).id);
        assertEquals("comment-1", regie.visibleSceneId());
    }

    /** preflight failure (e.g. missing local asset) suppresses the show without crashing. */
    @Test public void preflightFailureSuppressesShow() {
        RecordingSink sink = new RecordingSink();
        sink.preflightResult = false;
        SceneRuntimeController regie = new SceneRuntimeController(sink);
        regie.loadManifest(1, manifest("track-1", scene("comment-1", 12_000, 6_000)));

        regie.onEventDue(comment("comment-1", 12_000, 6_000).id);

        assertEquals(1, sink.preflightCalls);
        assertTrue("preflight false must suppress show", sink.shown.isEmpty());
        assertFalse(regie.hasVisibleScene());
    }

    /** onCommentExpired hides only the matching visible scene; stale ids are no-ops. */
    @Test public void expiryHidesOnlyMatchingVisibleScene() {
        RecordingSink sink = new RecordingSink();
        SceneRuntimeController regie = new SceneRuntimeController(sink);
        regie.loadManifest(1, manifest("track-1",
                scene("comment-1", 12_000, 6_000), scene("comment-2", 20_000, 3_000)));
        regie.onEventDue(comment("comment-1", 12_000, 6_000).id);

        // Expiry for a non-visible scene is a no-op.
        regie.onEventExpired(comment("comment-2", 20_000, 3_000).id);
        assertEquals("comment-1", regie.visibleSceneId());

        // Expiry for the visible scene hides it.
        regie.onEventExpired(comment("comment-1", 12_000, 6_000).id);
        assertFalse(regie.hasVisibleScene());
        assertTrue(sink.hidden.contains("comment-1"));
    }

    /**
     * A late expiry carrying a superseded generation is a no-op: it must neither hide the
     * new-revision scene nor resurrect the old one (sections 13/14/21). This locks the
     * interaction between the new scheduler-driven expiry and the generation guard.
     */
    @Test public void staleGenerationExpiryIsIgnored() {
        RecordingSink sink = new RecordingSink();
        SceneRuntimeController regie = new SceneRuntimeController(sink);
        regie.loadManifest(1, manifest("track-1", scene("comment-1", 12_000, 6_000)));
        long gen1 = regie.currentGeneration();
        regie.onEventDue(comment("comment-1", 12_000, 6_000).id);
        assertEquals("comment-1", regie.visibleSceneId());

        // Revision 2 takes over and shows its own scene under the new generation.
        regie.replaceRevision(2, manifest("track-2", scene("comment-2", 3_000, 4_000)));
        regie.onEventDue(comment("comment-2", 3_000, 4_000).id);
        assertEquals("comment-2", regie.visibleSceneId());

        // A late expiry from the superseded revision-1 generation must do nothing: it cannot
        // hide the new comment-2 scene, and comment-1 is already gone.
        regie.onEventExpired(comment("comment-1", 12_000, 6_000).id, gen1);
        assertEquals("stale-generation expiry must not hide the new scene",
                "comment-2", regie.visibleSceneId());
        assertFalse("stale-generation expiry must not hide the comment-2 scene",
                sink.hidden.contains("comment-2"));
    }

    /**
     * An expiry for a scene other than the currently visible one (same generation) is a no-op,
     * and expiry of the visible scene hides exactly it. Mirrors the scheduler firing onExpire
     * for whichever comment's media window elapsed.
     */
    @Test public void expiryOfVisibleSceneHidesItExactlyOnce() {
        RecordingSink sink = new RecordingSink();
        SceneRuntimeController regie = new SceneRuntimeController(sink);
        regie.loadManifest(1, manifest("track-1",
                scene("comment-1", 1_000, 6_000), scene("comment-2", 12_000, 6_000)));
        regie.onEventDue(comment("comment-1", 1_000, 6_000).id);

        // Expiry of a non-visible scene is a no-op.
        regie.onEventExpired(comment("comment-2", 12_000, 6_000).id);
        assertEquals("comment-1", regie.visibleSceneId());

        // Expiry of the visible scene hides it; a second (duplicate/late) expiry is harmless.
        regie.onEventExpired(comment("comment-1", 1_000, 6_000).id);
        assertFalse(regie.hasVisibleScene());
        regie.onEventExpired(comment("comment-1", 1_000, 6_000).id);
        assertEquals("duplicate expiry must not hide twice", 1,
                java.util.Collections.frequency(sink.hidden, "comment-1"));
    }

    /** Null event never NPEs and never shows anything. */
    @Test public void nullEventIsSafe() {
        RecordingSink sink = new RecordingSink();
        SceneRuntimeController regie = new SceneRuntimeController(sink);
        regie.loadManifest(1, manifest("track-1", scene("comment-1", 12_000, 6_000)));
        regie.onEventDue(null);
        regie.onEventExpired(null);
        assertFalse(regie.hasVisibleScene());
        assertTrue(sink.shown.isEmpty());
    }
}
