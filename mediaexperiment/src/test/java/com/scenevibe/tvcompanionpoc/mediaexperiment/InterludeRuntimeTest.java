package com.scenevibe.tvcompanionpoc.mediaexperiment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.scenevibe.tvcompanionpoc.mediaexperiment.TestFakes.RecordingController;
import com.scenevibe.tvcompanionpoc.mediaexperiment.TestFakes.RuntimeFixture;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.AudioFocusPort;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.DeniedReason;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.InterludeState;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.PlaybackStateCodes;

import org.junit.Test;

/**
 * Exercise the actual service router and scanner catalog together. Inventory
 * changes enter through the same fresh active-controller seam as Android, never
 * by injecting a fictitious replacement snapshot into the decision core.
 */
public class InterludeRuntimeTest {
    private final RuntimeFixture fixture = new RuntimeFixture();

    /** Scan only observes; it cannot issue transport or start local media. */
    @Test public void scanOnly_neverDispatchesOrPlays() {
        fixture.runtime.scanMediaSession();
        assertEquals(0, fixture.focus.requestCalls);
        assertNoInterludeOrPlay();
        assertEquals(0, fixture.original.pauseCalls);
        assertEquals(0, fixture.audio.playShortClipCalls);
    }

    /** Denied focus prevents cue audio and every transport command. */
    @Test public void focusDenied_noAudioNoPause() {
        fixture.focus.result = AudioFocusPort.Result.DENIED;
        fixture.runtime.testAudioDuck();
        assertEquals(InterludeState.STOPPED, fixture.runtime.state());
        assertEquals(0, fixture.audio.playShortClipCalls);
        assertEquals(0, fixture.original.pauseCalls);
        assertNoInterludeOrPlay();
    }

    /** A full sequence also refuses to pause when its local-video focus is denied. */
    @Test public void fullFocusDenied_noPauseNoVideo() {
        fixture.focus.result = AudioFocusPort.Result.DENIED;
        fixture.runtime.testFullInterlude();
        assertEquals(0, fixture.original.pauseCalls);
        assertNoInterludeOrPlay();
    }

    /** Duck-only cue teardown is bounded and leaves native transport untouched. */
    @Test public void audioDuckOnly_boundedCue_noPauseOrVideo() {
        fixture.runtime.testAudioDuck();
        assertEquals(1, fixture.audio.playShortClipCalls);
        fixture.clock.advance(2000L);
        fixture.runtime.poll();
        assertEquals(InterludeState.STOPPED, fixture.runtime.state());
        assertEquals(1, fixture.focus.abandonCalls);
        assertEquals(0, fixture.original.pauseCalls);
        assertNoInterludeOrPlay();
    }

    /** TEST PAUSE never requests cue/focus or attaches/plays a fullscreen overlay. */
    @Test public void testPause_neverAttachesOverlayOrStartsVideo() {
        fixture.runtime.testPause();
        assertEquals(InterludeState.PAUSE_SENT, fixture.runtime.state());
        fixture.original.state = PlaybackStateCodes.STATE_PAUSED;
        fixture.runtime.poll();
        assertEquals(InterludeState.STOPPED, fixture.runtime.state());
        assertTrue(fixture.runtime.diagnostics().pauseConfirmed);
        assertTrue(fixture.runtime.diagnostics().pauseOwnershipAcquired);
        assertEquals(0, fixture.focus.requestCalls);
        assertEquals(0, fixture.audio.playShortClipCalls);
        fixture.runtime.onVideoCompleted();
        assertNoInterludeOrPlay();
    }

    /** A sent command is not evidence of a PAUSED transition. */
    @Test public void pauseSent_isNotPauseConfirmed() {
        fixture.runtime.testPause();
        fixture.runtime.poll();
        assertTrue(fixture.runtime.diagnostics().pauseCommandSent);
        assertFalse(fixture.runtime.diagnostics().pauseConfirmed);
        assertFalse(fixture.runtime.diagnostics().pauseOwnershipAcquired);
        assertNoInterludeOrPlay();
    }

    /** Timeout of the isolated pause test has no side effect beyond teardown. */
    @Test public void pauseOnlyTimeout_noInterludeNoPlay() {
        fixture.runtime.testPause();
        fixture.clock.advance(4001L);
        fixture.runtime.poll();
        assertTrue(fixture.runtime.diagnostics().pauseTimedOut);
        assertNoInterludeOrPlay();
    }

    /** Full flow must also stop when PAUSED is never confirmed. */
    @Test public void fullPauseTimeout_noInterludeNoPlay() {
        fixture.runtime.testFullInterlude();
        fixture.clock.advance(4001L);
        fixture.runtime.poll();
        fixture.runtime.onVideoCompleted();
        assertTrue(fixture.runtime.diagnostics().pauseTimedOut);
        assertNoInterludeOrPlay();
    }

    /** A pause already chosen by the user is never attributed to SceneVibe. */
    @Test public void alreadyPausedBeforeCommand_neverOwnsPause_neverPlays() {
        fixture.original.state = PlaybackStateCodes.STATE_PAUSED;
        fixture.runtime.testFullInterlude();
        fixture.runtime.onVideoCompleted();
        assertEquals(0, fixture.original.pauseCalls);
        assertFalse(fixture.runtime.diagnostics().pauseOwnershipAcquired);
        assertEquals(DeniedReason.INITIAL_STATE_NOT_PLAYING,
                fixture.runtime.diagnostics().resumeDeniedReason);
        assertNoInterludeOrPlay();
    }

    /** Every non-PLAYING initial state is unsupported without transport or video. */
    @Test public void initialStateNotPlaying_neverStartsFullInterlude() {
        for (int state = 0; state <= 12; state++) {
            if (state == PlaybackStateCodes.STATE_PLAYING) continue;
            RuntimeFixture attempt = new RuntimeFixture();
            attempt.original.state = state;
            attempt.runtime.testFullInterlude();
            assertEquals("initial state " + state, 0, attempt.original.pauseCalls);
            assertEquals("initial state " + state, 0, attempt.overlay.attachCalls);
            assertEquals("initial state " + state, 0, attempt.original.playCalls);
            assertFalse(attempt.runtime.diagnostics().pauseOwnershipAcquired);
        }
    }

    /** Normal completion rescans, issues one PLAY and confirms a later PLAYING state. */
    @Test public void testFullInterlude_happyPath_exactlyOneGuardedPlay() {
        fixture.beginConfirmedVideo();
        assertEquals(InterludeState.VIDEO_PLAYING, fixture.runtime.state());
        int queriesBeforeCompletion = fixture.sessions.queries;
        fixture.runtime.onVideoCompleted();
        assertTrue("completion must query the live inventory", fixture.sessions.queries > queriesBeforeCompletion);
        assertEquals(1, fixture.original.playCalls);
        assertEquals("PAUSED", fixture.runtime.diagnostics().latestStateBeforeResume);
        assertEquals(1, fixture.overlay.removeCalls);
        assertEquals(1, fixture.focus.abandonCalls);
        fixture.original.state = PlaybackStateCodes.STATE_PLAYING;
        fixture.runtime.poll();
        fixture.runtime.onVideoCompleted();
        assertTrue(fixture.runtime.diagnostics().resumeConfirmed);
        assertEquals(1, fixture.original.playCalls);
    }

    /** A different active package cannot be resumed through a stale original controller. */
    @Test public void sessionPackageReplacement_neverPlays() {
        fixture.beginConfirmedVideo();
        RecordingController replacement = new RecordingController();
        replacement.packageName = "com.netflix.ninja";
        replacement.token = "netflix-token";
        fixture.sessions.active.clear();
        fixture.sessions.active.add(replacement);
        fixture.runtime.onVideoCompleted();
        assertEquals(0, fixture.original.playCalls);
        assertEquals(0, replacement.playCalls);
        assertEquals(InterludeState.STOPPED, fixture.runtime.state());
    }

    /** Same package and identical media metadata do not establish session-token equality. */
    @Test public void sessionTokenChanged_samePackage_neverPlays() {
        fixture.beginConfirmedVideo();
        RecordingController replacement = new RecordingController();
        replacement.token = "replacement-token";
        replacement.state = PlaybackStateCodes.STATE_PAUSED;
        fixture.sessions.active.clear();
        fixture.sessions.active.add(replacement);
        fixture.runtime.onVideoCompleted();
        assertEquals(DeniedReason.SESSION_TOKEN_CHANGED, fixture.runtime.diagnostics().resumeDeniedReason);
        assertEquals(0, fixture.original.playCalls);
        assertEquals(0, replacement.playCalls);
    }

    /** A vanished original session is discovered by the completion-time rescan. */
    @Test public void originalSessionMissing_neverPlays() {
        fixture.beginConfirmedVideo();
        fixture.sessions.active.clear();
        fixture.runtime.onVideoCompleted();
        assertFalse(fixture.runtime.diagnostics().originalSessionIdentityPresent);
        assertEquals(DeniedReason.ORIGINAL_SESSION_MISSING, fixture.runtime.diagnostics().resumeDeniedReason);
        assertEquals(0, fixture.original.playCalls);
    }

    /** Another playing app is relevant even if the paused original is still listed first. */
    @Test public void otherMediaAppBecomesRelevant_neverPlays() {
        fixture.beginConfirmedVideo();
        RecordingController other = new RecordingController();
        other.packageName = "com.netflix.ninja";
        other.token = "other-token";
        fixture.sessions.active.add(other);
        fixture.runtime.onVideoCompleted();
        assertTrue(fixture.runtime.diagnostics().originalSessionIdentityPresent);
        assertTrue(fixture.runtime.diagnostics().relevantActivePackageChanged);
        assertEquals(DeniedReason.OTHER_MEDIA_APP_RELEVANT, fixture.runtime.diagnostics().resumeDeniedReason);
        assertEquals(0, fixture.original.playCalls);
        assertEquals(0, other.playCalls);
    }

    /** The platform's changed priority owner is relevant even when it is paused. */
    @Test public void otherPausedAppBecomesPriorityOwner_neverPlays() {
        fixture.beginConfirmedVideo();
        RecordingController other = new RecordingController();
        other.packageName = "com.netflix.ninja";
        other.token = "other-token";
        other.state = PlaybackStateCodes.STATE_PAUSED;
        fixture.sessions.active.add(0, other);
        fixture.runtime.onVideoCompleted();
        assertEquals(0, fixture.original.playCalls);
        assertEquals(DeniedReason.OTHER_MEDIA_APP_RELEVANT, fixture.runtime.diagnostics().resumeDeniedReason);
    }

    /** New media on the same live token cannot inherit old pause ownership. */
    @Test public void mediaIdentityChanged_neverPlays() {
        fixture.beginConfirmedVideo();
        fixture.original.mediaId = "different-media";
        fixture.runtime.onVideoCompleted();
        assertEquals(DeniedReason.MEDIA_CHANGED, fixture.runtime.diagnostics().resumeDeniedReason);
        assertEquals(0, fixture.original.playCalls);
    }

    /** Completion must freshly read PAUSED instead of trusting the last poll. */
    @Test public void latestStateNoLongerPaused_neverPlays() {
        for (int state = 0; state <= 12; state++) {
            if (state == PlaybackStateCodes.STATE_PAUSED) continue;
            RuntimeFixture attempt = new RuntimeFixture();
            attempt.beginConfirmedVideo();
            attempt.original.state = state;
            attempt.runtime.onVideoCompleted();
            assertEquals("state " + state, 0, attempt.original.playCalls);
            assertEquals(DeniedReason.LIVE_STATE_NOT_PAUSED, attempt.runtime.diagnostics().resumeDeniedReason);
        }
    }

    /** Polling invalidation tears down immediately rather than waiting for completion. */
    @Test public void sessionInvalidatedDuringVideo_teardownImmediately() {
        fixture.beginConfirmedVideo();
        fixture.sessions.active.clear();
        fixture.runtime.poll();
        assertEquals(InterludeState.STOPPED, fixture.runtime.state());
        assertEquals(1, fixture.overlay.removeCalls);
        assertEquals(1, fixture.focus.abandonCalls);
        assertEquals(0, fixture.original.playCalls);
    }

    /** Lost notification/session-query access is uncertainty and must not send PLAY. */
    @Test public void activeSessionQueryFails_neverPlays() {
        fixture.beginConfirmedVideo();
        fixture.sessions.failQuery = true;
        fixture.runtime.onVideoCompleted();
        assertEquals(DeniedReason.SESSION_QUERY_FAILED, fixture.runtime.diagnostics().resumeDeniedReason);
        assertEquals(0, fixture.original.playCalls);
    }

    /** A changed controller in the same package cannot confirm our original pause. */
    @Test public void tokenChangedBeforePausedConfirmation_neverOwnsPause() {
        fixture.runtime.testFullInterlude();
        RecordingController replacement = new RecordingController();
        replacement.token = "replacement-token";
        replacement.state = PlaybackStateCodes.STATE_PAUSED;
        fixture.sessions.active.clear();
        fixture.sessions.active.add(replacement);
        fixture.runtime.poll();
        assertFalse(fixture.runtime.diagnostics().pauseOwnershipAcquired);
        assertNoInterludeOrPlay();
    }

    /** Advertised unsupported transport never leads to a compensating PLAY. */
    @Test public void unsupportedPause_noCommandNoAccidentalPlay() {
        fixture.original.pauseSupported = false;
        fixture.runtime.testFullInterlude();
        fixture.runtime.onVideoCompleted();
        assertEquals(DeniedReason.PAUSE_UNSUPPORTED, fixture.runtime.diagnostics().resumeDeniedReason);
        assertEquals(0, fixture.original.pauseCalls);
        assertNoInterludeOrPlay();
    }

    /** Overlay attachment errors tear down and retain the native pause. */
    @Test public void overlayAttachmentFails_teardownNoPlay() {
        fixture.overlay.throwOnAttach = true;
        fixture.beginConfirmedVideo();
        assertEquals(InterludeState.STOPPED, fixture.runtime.state());
        assertEquals(1, fixture.overlay.removeCalls);
        assertEquals(0, fixture.original.playCalls);
    }

    /** Local video errors never take an emergency auto-resume branch. */
    @Test public void videoFailure_teardownNoPlay() {
        fixture.overlay.throwOnPlay = true;
        fixture.beginConfirmedVideo();
        assertEquals(InterludeState.STOPPED, fixture.runtime.state());
        assertTrue(fixture.runtime.diagnostics().localVideoError);
        assertEquals(0, fixture.original.playCalls);
    }

    /** STOP and late callbacks are idempotent after a confirmed owned pause. */
    @Test public void repeatedStop_idempotentNoPlay() {
        fixture.beginConfirmedVideo();
        fixture.runtime.stop();
        fixture.runtime.stop();
        fixture.runtime.stop();
        fixture.runtime.onVideoCompleted();
        fixture.runtime.onVideoError();
        fixture.runtime.poll();
        assertEquals(1, fixture.overlay.removeCalls);
        assertEquals(1, fixture.focus.abandonCalls);
        assertEquals(1, fixture.audio.stopCalls);
        assertEquals(0, fixture.original.playCalls);
    }

    /** Starting full after an isolated pause still requires manual native PLAYING. */
    @Test public void fullAfterPauseOnly_refusesPreExistingPause() {
        fixture.runtime.testPause();
        fixture.original.state = PlaybackStateCodes.STATE_PAUSED;
        fixture.runtime.poll();
        fixture.runtime.testFullInterlude();
        assertEquals(1, fixture.original.pauseCalls);
        assertFalse(fixture.runtime.diagnostics().pauseOwnershipAcquired);
        assertNoInterludeOrPlay();
    }

    /** The transport seam catches a session disappearing after the guard's own query. */
    @Test public void sessionDisappearsBetweenGuardAndDispatch_neverPlays() {
        fixture.beginConfirmedVideo();
        int dispatchQuery = fixture.sessions.queries + 2;
        fixture.sessions.beforeQuery = () -> {
            if (fixture.sessions.queries == dispatchQuery) fixture.sessions.active.clear();
        };
        fixture.runtime.onVideoCompleted();
        assertEquals(0, fixture.original.playCalls);
        assertEquals(InterludeState.STOPPED, fixture.runtime.state());
    }

    /** A pause occurring between precheck and actual dispatch is never claimed. */
    @Test public void preExistingPauseAtDispatch_neverOwnsPause() {
        fixture.sessions.beforeQuery = () -> {
            if (fixture.sessions.queries == 4) fixture.original.state = PlaybackStateCodes.STATE_PAUSED;
        };
        fixture.runtime.testFullInterlude();
        assertEquals(0, fixture.original.pauseCalls);
        assertFalse(fixture.runtime.diagnostics().pauseOwnershipAcquired);
        assertNoInterludeOrPlay();
    }

    /** No stale duck callback can abandon focus belonging to a later full sequence. */
    @Test public void newActionCancelsPriorCueDeadline() {
        fixture.runtime.testAudioDuck();
        fixture.runtime.testFullInterlude();
        fixture.original.state = PlaybackStateCodes.STATE_PAUSED;
        fixture.runtime.poll();
        int abandoned = fixture.focus.abandonCalls;
        fixture.clock.advance(2000L);
        fixture.runtime.poll();
        assertEquals(InterludeState.VIDEO_PLAYING, fixture.runtime.state());
        assertEquals(abandoned, fixture.focus.abandonCalls);
        fixture.runtime.stop();
        assertEquals(0, fixture.original.playCalls);
    }

    /** Focus-related player changes are re-observed before sending any PAUSE. */
    @Test public void focusGrantChangedNativeState_noPauseOwnership() {
        fixture.focus.onRequest = () -> fixture.original.state = PlaybackStateCodes.STATE_PAUSED;
        fixture.runtime.testFullInterlude();
        assertEquals(DeniedReason.INITIAL_STATE_NOT_PLAYING, fixture.runtime.diagnostics().resumeDeniedReason);
        assertEquals(0, fixture.original.pauseCalls);
        assertFalse(fixture.runtime.diagnostics().pauseOwnershipAcquired);
        assertNoInterludeOrPlay();
    }

    /** A synchronous Android setup-error callback cannot be overwritten by VIDEO_PLAYING. */
    @Test public void synchronousVideoErrorCallback_staysStoppedNoPlay() {
        fixture.overlay.onPlay = fixture.runtime::onVideoError;
        fixture.beginConfirmedVideo();
        assertEquals(InterludeState.STOPPED, fixture.runtime.state());
        assertEquals(0, fixture.original.playCalls);
        assertTrue(fixture.runtime.diagnostics().localVideoError);
    }

    /** Failed overlay teardown denies normal resume even after video completion. */
    @Test public void overlayRemovalFailure_noPlay() {
        fixture.beginConfirmedVideo();
        fixture.overlay.throwOnRemove = true;
        fixture.runtime.onVideoCompleted();
        assertEquals(InterludeState.STOPPED, fixture.runtime.state());
        assertEquals(DeniedReason.PORT_ERROR, fixture.runtime.diagnostics().resumeDeniedReason);
        assertEquals(0, fixture.original.playCalls);
    }

    /** Shared assertion for actions that must never initiate interlude or resume. */
    private void assertNoInterludeOrPlay() {
        assertEquals(0, fixture.overlay.attachCalls);
        assertEquals(0, fixture.overlay.playLocalVideoCalls);
        assertEquals(0, fixture.original.playCalls);
    }
}
