package com.scenevibe.tvcompanionpoc;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The real regie is exercised with independently controlled player/session seams.
 * The existing manifested-MEDIA controller tests remain unchanged and execute actual candidate code.
 */
public final class SceneRuntimeLocalInterludeTest {
    /** Establish one local playback after a separately observed native PAUSED state. */
    private E5TestHarness playing() {
        E5TestHarness h = new E5TestHarness();
        h.requestAndReady();
        h.confirmPauseAndStart();
        assertEquals(SceneRuntimeController.LocalPhase.PLAYER_PLAYING, h.controller.localPhase());
        return h;
    }

    /** Full nominal path requires both native confirmations and cleanup after PLAYING. */
    @Test public void nominalConfirmsPauseAndPlayBeforeSuccess() {
        E5TestHarness h = new E5TestHarness();
        assertTrue(h.controller.requestLocal(LocalInterludePlan.qualification(true)));
        assertEquals(0, h.session.pauses);
        h.player.ready();
        assertEquals(1, h.session.pauses);
        h.controller.tickLocal();
        assertEquals(0, h.player.starts);
        h.confirmPauseAndStart();
        assertEquals(1, h.player.starts);
        assertTrue(h.controller.hasVisibleScene());
        h.completeFile();
        assertEquals(1, h.player.releases);
        assertEquals(1, h.session.plays);
        assertEquals(SceneRuntimeController.LocalPhase.RESUME_REQUESTED, h.controller.localPhase());
        h.confirmPlaying();
        assertEquals(SceneRuntimeController.LocalPhase.SUCCESS, h.controller.localPhase());
        assertFalse(h.owner.hasLease());
        assertFalse(h.controller.hasVisibleScene());
        assertFalse(h.controller.isLocalPresentationActive());
        assertEquals(1, h.sink.hides);
        assertTrue(h.events.indexOf("player-release") < h.events.indexOf("play-command"));
        assertTrue(h.events.indexOf("PLAYING_CONFIRMED") < h.events.indexOf("host-detach"));
    }

    /** OFF refuses before preflight/player/session commands and leaves the historical owner idle. */
    @Test public void flagOffNeverPreparesOrCommands() {
        E5TestHarness h = new E5TestHarness();
        h.controller.configureLocal(false, h.player, h.owner, h.clock,
                (event, generation, snapshot, reason) -> h.events.add(event));
        assertFalse(h.controller.requestLocal(LocalInterludePlan.qualification(true)));
        assertEquals(0, h.player.preparations);
        assertEquals(0, h.session.pauses);
        assertEquals(0, h.sink.hides);
    }

    /** Missing VOD confirmation is refused before local preparation. */
    @Test public void noVodAttestationRefusesBeforePreparation() {
        E5TestHarness h = new E5TestHarness();
        assertFalse(h.controller.requestLocal(LocalInterludePlan.qualification(false)));
        assertEquals(0, h.player.preparations);
        assertEquals(0, h.session.pauses);
    }

    /** A unavailable graphical host cannot pause the programme. */
    @Test public void graphicalPreflightRefusalHasNoPause() {
        E5TestHarness h = new E5TestHarness(); h.sink.available = false;
        assertFalse(h.controller.requestLocal(LocalInterludePlan.qualification(true)));
        assertEquals(0, h.session.pauses);
        assertEquals(0, h.player.preparations);
    }

    /** Corrupt/missing fixture preparation is not a native interruption. */
    @Test public void decoderPreparationErrorNeverPauses() {
        E5TestHarness h = new E5TestHarness();
        h.controller.requestLocal(LocalInterludePlan.qualification(true));
        h.player.callbacks.error(h.player.generation, "FIXTURE_DIGEST_MISMATCH");
        assertEquals(SceneRuntimeController.LocalPhase.REFUSED, h.controller.localPhase());
        assertEquals(0, h.session.pauses);
        assertEquals(0, h.session.plays);
        assertEquals(1, h.player.releases);
    }

    /** A preparation watchdog protects resources without granting native ownership. */
    @Test public void preparationTimeoutReleasesBeforePause() {
        E5TestHarness h = new E5TestHarness();
        h.controller.requestLocal(LocalInterludePlan.qualification(true));
        h.clock.now += h.policy.preparationTimeoutMs + 1;
        h.controller.tickLocal();
        assertFalse(h.controller.isLocalPresentationActive());
        assertEquals(0, h.session.pauses);
        assertEquals(1, h.player.releases);
    }

    /** Already-PAUSED input never starts the prepared clip. */
    @Test public void initiallyPausedRefusesAfterReady() {
        E5TestHarness h = new E5TestHarness();
        h.session.current = h.sample(NativeSessionSnapshot.PAUSED, 300000, h.clock.now, h.clock.now);
        h.requestAndReady();
        assertEquals(SceneRuntimeController.LocalPhase.REFUSED, h.controller.localPhase());
        assertEquals(0, h.player.starts);
        assertEquals(0, h.session.pauses);
    }

    /** A sent but unconfirmed pause never starts playback or resumes the programme. */
    @Test public void unconfirmedPauseCleansUpWithoutPlay() {
        E5TestHarness h = new E5TestHarness(); h.requestAndReady();
        h.clock.now += h.policy.pauseTimeoutMs + 1;
        h.controller.tickLocal();
        assertEquals(SceneRuntimeController.LocalPhase.RECOVERY_REQUIRED, h.controller.localPhase());
        assertEquals(0, h.player.starts);
        assertEquals(0, h.session.plays);
        assertFalse(h.owner.hasLease());
    }

    /** A concurrent request is refused without preparing, replacing or queuing it. */
    @Test public void secondRequestNeverPreempts() {
        E5TestHarness h = playing();
        assertFalse(h.controller.requestLocal(LocalInterludePlan.qualification(true)));
        assertEquals(1, h.player.preparations);
        assertEquals(1, h.player.starts);
        assertEquals(1, h.session.pauses);
        assertTrue(h.controller.isLocalPresentationActive());
    }

    /** An error during playback requires recovery, not automatic native PLAY. */
    @Test public void decoderErrorDuringPlayDoesNotResume() {
        E5TestHarness h = playing();
        h.player.callbacks.error(h.player.generation, "PLAYER_DECODE_ERROR");
        assertEquals(SceneRuntimeController.LocalPhase.RECOVERY_REQUIRED, h.controller.localPhase());
        assertEquals(0, h.session.plays);
        assertEquals(1, h.player.releases);
        assertFalse(h.owner.hasLease());
    }

    /** A retired Surface/player cannot receive a late start or EOF with side effects. */
    @Test public void surfaceFailureInvalidatesLateCallbacks() {
        E5TestHarness h = playing();
        LocalVideoPort.Callbacks old = h.player.callbacks;
        long generation = h.player.generation;
        old.error(generation, "SURFACE_LOST");
        old.started(generation); old.ready(generation); old.eof(generation);
        assertEquals(0, h.session.plays);
        assertEquals(1, h.player.starts);
        assertEquals(1, h.player.releases);
        assertEquals(SceneRuntimeController.LocalPhase.RECOVERY_REQUIRED, h.controller.localPhase());
    }

    /** STOP is idempotent and never becomes a successful normal completion. */
    @Test public void explicitStopNeverResumes() {
        E5TestHarness h = playing();
        h.controller.cancelLocal("STOP"); h.controller.cancelLocal("STOP");
        assertEquals(0, h.session.plays);
        assertEquals(1, h.player.releases);
        assertEquals(1, h.sink.hides);
        assertFalse(h.controller.isLocalPresentationActive());
    }

    /** The clip watchdog is an error and cannot authorize PLAY. */
    @Test public void playerWatchdogNeverBecomesEof() {
        E5TestHarness h = playing();
        h.clock.now += h.policy.clipWatchdogMs + 1; h.refreshPaused();
        h.controller.tickLocal();
        assertEquals(0, h.session.plays);
        assertEquals(SceneRuntimeController.LocalPhase.RECOVERY_REQUIRED, h.controller.localPhase());
    }

    /** Duplicate EOF is ignored after the first transition into native confirmation. */
    @Test public void duplicateEofHasOnlyOnePlay() {
        E5TestHarness h = playing();
        h.completeFile();
        h.player.callbacks.eof(h.player.generation);
        assertEquals(1, h.session.plays);
        assertEquals(1, h.player.releases);
    }

    /** EOF before the fixed fixture's valid playback budget is not accepted as normal. */
    @Test public void prematureEofRefusesResume() {
        E5TestHarness h = playing();
        h.player.callbacks.eof(h.player.generation);
        assertEquals(0, h.session.plays);
        assertEquals(SceneRuntimeController.LocalPhase.RECOVERY_REQUIRED, h.controller.localPhase());
    }

    /** Missing/changed session evidence cancels the live presentation without substitution. */
    @Test public void appDepartureInvalidatesLease() {
        E5TestHarness h = playing(); h.session.observable = false;
        h.controller.tickLocal();
        assertEquals(0, h.session.plays);
        assertFalse(h.controller.isLocalPresentationActive());
        assertEquals(1, h.player.releases);
    }

    /** Seek during pause invalidates native ownership before EOF. */
    @Test public void userSeekDuringInterludeCancels() {
        E5TestHarness h = playing();
        h.session.current = h.sample(NativeSessionSnapshot.PAUSED, 400000, h.clock.now, h.clock.now);
        h.controller.tickLocal();
        assertEquals(0, h.session.plays);
        assertEquals(SceneRuntimeController.LocalPhase.RECOVERY_REQUIRED, h.controller.localPhase());
    }

    /** A manifest revision replacement cannot retain an old player or authorize resume. */
    @Test public void revisionReplacementCancelsLocalAttempt() {
        E5TestHarness h = playing();
        LocalVideoPort.Callbacks old = h.player.callbacks;
        h.controller.loadManifest(2, new OverlayManifest("manifest", "video", "source",
                "media", "freeze", java.util.List.of(LocalInterludePlan.qualification(true).scene)));
        old.eof(h.player.generation);
        assertEquals(0, h.session.plays);
        assertEquals(1, h.player.releases);
        assertEquals(2, h.controller.activeRevision());
        assertFalse(h.controller.isLocalPresentationActive());
    }

    /** Destruction/unload invalidates every asynchronous callback and memory-only lease. */
    @Test public void unloadDoesNotRestoreOrResume() {
        E5TestHarness h = playing(); h.controller.unload();
        h.player.callbacks.eof(h.player.generation);
        assertFalse(h.owner.hasLease());
        assertEquals(0, h.session.plays);
        assertFalse(h.controller.hasActiveManifest());
    }

    /** Normal EOF plus a missing PLAYING confirmation remains recovery-required. */
    @Test public void playUnconfirmedIsNotSuccess() {
        E5TestHarness h = playing(); h.completeFile();
        h.clock.now += h.policy.playingTimeoutMs + 1; h.refreshPaused();
        h.controller.tickLocal();
        assertEquals(SceneRuntimeController.LocalPhase.RECOVERY_REQUIRED, h.controller.localPhase());
        assertEquals(1, h.session.plays);
        assertFalse(h.owner.hasLease());
    }

    /** Stale callbacks cannot mutate the new attempt after the user manually recovers native playback. */
    @Test public void oldCallbacksCannotResurrectIntoAnotherAttempt() {
        E5TestHarness h = playing();
        LocalVideoPort.Callbacks old = h.player.callbacks;
        long oldGeneration = h.player.generation;
        h.controller.cancelLocal("STOP");
        h.session.current = h.sample(NativeSessionSnapshot.PLAYING, 300100, h.clock.now, h.clock.now);
        assertTrue(h.controller.requestLocal(LocalInterludePlan.qualification(true)));
        long newGeneration = h.player.generation;
        old.ready(oldGeneration); old.started(oldGeneration); old.eof(oldGeneration);
        old.error(oldGeneration, "OLD_ERROR");
        assertTrue(newGeneration > oldGeneration);
        assertEquals(SceneRuntimeController.LocalPhase.PREPARING, h.controller.localPhase());
        assertEquals(0, h.session.plays);
        assertEquals(1, h.player.releases);
    }

    /** A loaded Track's unrelated eligibility is not the manual qualification's native identity. */
    @Test public void unrelatedTrackEligibilityDoesNotTransferOwnership() {
        E5TestHarness h = playing();
        h.controller.onEligibility(false);
        assertTrue(h.controller.isLocalPresentationActive());
        assertEquals(0, h.sink.hides);
        h.session.observable = false;
        h.controller.tickLocal();
        assertFalse(h.controller.isLocalPresentationActive());
        assertEquals(0, h.session.plays);
    }

    /** Regular DUE cannot take over the one active local presentation slot. */
    @Test public void manifestedDueDoesNotReplaceLocalPresentation() {
        E5TestHarness h = playing();
        h.controller.onEventDue("another-event");
        h.controller.onEventExpired("e5-local-clip");
        assertTrue(h.controller.isLocalPresentationActive());
        assertEquals(0, h.sink.regularShows);
        assertEquals(0, h.session.plays);
    }

    /** A visible graphical scene keeps precedence by refusing the new manual request. */
    @Test public void existingSceneRefusesLocalRequest() {
        E5TestHarness h = new E5TestHarness();
        OverlayManifest.Scene scene = LocalInterludePlan.qualification(true).scene;
        h.controller.loadManifest(1, new OverlayManifest("manifest", "video", "source",
                "media", "freeze", java.util.List.of(scene)));
        h.controller.onEventDue(scene.id);
        assertFalse(h.controller.requestLocal(LocalInterludePlan.qualification(true)));
        assertEquals(1, h.sink.regularShows);
        assertEquals(0, h.player.preparations);
        assertEquals(0, h.session.pauses);
    }
}
