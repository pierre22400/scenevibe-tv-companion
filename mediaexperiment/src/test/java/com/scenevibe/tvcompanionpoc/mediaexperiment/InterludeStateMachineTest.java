package com.scenevibe.tvcompanionpoc.mediaexperiment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.scenevibe.tvcompanionpoc.mediaexperiment.TestFakes.FakeClock;
import com.scenevibe.tvcompanionpoc.mediaexperiment.TestFakes.RecordingAudioFocusPort;
import com.scenevibe.tvcompanionpoc.mediaexperiment.TestFakes.RecordingLocalAudioPort;
import com.scenevibe.tvcompanionpoc.mediaexperiment.TestFakes.RecordingMediaControlPort;
import com.scenevibe.tvcompanionpoc.mediaexperiment.TestFakes.RecordingOverlayVideoPort;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.AudioFocusPort;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.InterludeState;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.InterludeStateMachine;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.PlaybackSnapshot;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.PlaybackStateCodes;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SafeResumeGuard;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SessionTarget;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SessionCatalog;

import org.junit.Test;

/**
 * Pure-JVM tests for the interlude state machine and safe-resume guard.
 *
 * <p>These are logic tests only. They assert what the decision core WOULD dispatch
 * to transport controls (via recording ports); they do not and cannot prove real
 * audio ducking or a real streaming-app pause, which are physical observations.</p>
 */
public class InterludeStateMachineTest {

    private static final String PKG = "com.amazon.amazonvideo.livingroom";
    private static final long PAUSE_TIMEOUT = 2_000L;
    private static final long PLAY_TIMEOUT = 2_000L;

    private final RecordingAudioFocusPort focus = new RecordingAudioFocusPort();
    private final RecordingMediaControlPort media = new RecordingMediaControlPort();
    private final RecordingOverlayVideoPort overlay = new RecordingOverlayVideoPort();
    private final RecordingLocalAudioPort audio = new RecordingLocalAudioPort();
    private final FakeClock clock = new FakeClock();

    private final TestFakes.MutableSessions sessions = new TestFakes.MutableSessions();
    private final TestFakes.RecordingController original = new TestFakes.RecordingController();
    private final SessionCatalog catalog = new SessionCatalog(sessions);

    /** Build the core with the same active-session policy used in Android. */
    private InterludeStateMachine machine() {
        sessions.active.clear();
        sessions.active.add(original);
        catalog.scan();
        return new InterludeStateMachine(
                focus, media, catalog, overlay, audio, SafeResumeGuard.create(),
                clock, PAUSE_TIMEOUT, PLAY_TIMEOUT);
    }

    /** Capture the original token separately from its media identity. */
    private SessionTarget target() {
        return new SessionTarget(PKG, "mid-123", "The Expanse", "Episode 1", 3_600_000L, "original-token");
    }

    /** Change the live source and return the corresponding observation. */
    private PlaybackSnapshot snapshot(String pkg, int state, String mediaId, String title) {
        original.packageName = pkg;
        original.state = state;
        original.mediaId = mediaId;
        return new PlaybackSnapshot(pkg, state, mediaId, title, "Episode 1", 3_600_000L, "original-token");
    }

    // (a) Happy path: full confirmed-and-owned sequence issues exactly one PLAY.
    /** Verify happyPath confirmedOwned playsExactlyOnce. */
    @Test public void happyPath_confirmedOwned_playsExactlyOnce() {
        InterludeStateMachine m = machine();
        m.begin(target());

        assertEquals(InterludeState.PAUSE_SENT, m.state());
        assertEquals(1, media.pauseCalls);
        assertEquals("full flow does not play the isolated duck cue", 0, audio.playShortClipCalls);

        // Observe PAUSED on the same package -> interlude attaches and plays.
        m.observe(snapshot(PKG, PlaybackStateCodes.STATE_PAUSED, "mid-123", "The Expanse"));
        assertEquals(InterludeState.VIDEO_PLAYING, m.state());
        assertEquals(1, overlay.attachCalls);
        assertEquals(1, overlay.playLocalVideoCalls);

        // Keep the live snapshot current (still same media) and complete the video.
        m.observe(snapshot(PKG, PlaybackStateCodes.STATE_PAUSED, "mid-123", "The Expanse"));
        m.onVideoCompleted();
        assertEquals(InterludeState.PLAY_SENT, m.state());
        assertEquals("resume dispatched exactly once", 1, media.playCalls);

        // Observe PLAYING -> confirmed, clean teardown.
        m.observe(snapshot(PKG, PlaybackStateCodes.STATE_PLAYING, "mid-123", "The Expanse"));
        assertEquals(InterludeState.STOPPED, m.state());
        assertTrue(m.diagnostics().resumeConfirmed);
        assertEquals(1, overlay.removeCalls);
        assertEquals(1, focus.abandonCalls);
    }

    // (b) Focus DENIED: no local audio, no pause, clean abandon, zero play.
    /** Verify focusDenied noPauseNoAudio zeroPlay. */
    @Test public void focusDenied_noPauseNoAudio_zeroPlay() {
        focus.result = AudioFocusPort.Result.DENIED;
        InterludeStateMachine m = machine();
        m.begin(target());

        assertEquals(InterludeState.STOPPED, m.state());
        assertEquals("no local audio when focus denied", 0, audio.playShortClipCalls);
        assertEquals("no pause when focus denied", 0, media.pauseCalls);
        assertEquals("no play when focus denied", 0, media.playCalls);
        assertEquals("focus abandoned cleanly", 1, focus.abandonCalls);
        assertTrue(m.transitionLog().contains(InterludeState.FOCUS_DENIED));
    }

    // (c) Pause timeout: report NOT CONFIRMED, do not continue, zero play.
    /** Verify pauseTimeout notConfirmed doesNotContinue zeroPlay. */
    @Test public void pauseTimeout_notConfirmed_doesNotContinue_zeroPlay() {
        InterludeStateMachine m = machine();
        m.begin(target());
        assertEquals(1, media.pauseCalls);

        // Deadline passes with no PAUSED observation.
        clock.advance(PAUSE_TIMEOUT + 1);
        m.onTick();

        assertEquals(InterludeState.STOPPED, m.state());
        assertTrue(m.diagnostics().pauseTimedOut);
        assertTrue("pause was sent", m.diagnostics().pauseCommandSent);
        assertFalse("but never confirmed", m.diagnostics().pauseConfirmed);
        assertEquals("interlude must not attach after pause timeout", 0, overlay.attachCalls);
        assertEquals("no play after unconfirmed pause", 0, media.playCalls);
        assertEquals(1, focus.abandonCalls);
    }

    // (d) Session replacement: a different package appears -> guard fails, zero play.
    /** Verify sessionReplacement differentPackage guardFails zeroPlay. */
    @Test public void sessionReplacement_differentPackage_guardFails_zeroPlay() {
        InterludeStateMachine m = machine();
        m.begin(target());
        m.observe(snapshot(PKG, PlaybackStateCodes.STATE_PAUSED, "mid-123", "The Expanse"));
        assertEquals(InterludeState.VIDEO_PLAYING, m.state());

        // User switched: a different media app is now the live session.
        m.observe(snapshot("com.netflix.ninja",
                PlaybackStateCodes.STATE_PLAYING, "nf-999", "Something Else"));
        m.onVideoCompleted();

        assertEquals("no resume into a different app", 0, media.playCalls);
        assertEquals(InterludeState.STOPPED, m.state());
        assertEquals("overlay removed", 1, overlay.removeCalls);
        assertEquals("focus released", 1, focus.abandonCalls);
    }

    // (e) Media identity change: same package, different mediaId -> guard fails, zero play.
    /** Verify mediaIdentityChange samePackageNewId guardFails zeroPlay. */
    @Test public void mediaIdentityChange_samePackageNewId_guardFails_zeroPlay() {
        InterludeStateMachine m = machine();
        m.begin(target());
        m.observe(snapshot(PKG, PlaybackStateCodes.STATE_PAUSED, "mid-123", "The Expanse"));
        assertEquals(InterludeState.VIDEO_PLAYING, m.state());

        // Same app, clearly different media id/title now playing.
        m.observe(snapshot(PKG, PlaybackStateCodes.STATE_PLAYING, "mid-999", "A Different Show"));
        m.onVideoCompleted();

        assertEquals("no resume when media identity changed", 0, media.playCalls);
        assertEquals(InterludeState.STOPPED, m.state());
        assertEquals(1, overlay.removeCalls);
        assertEquals(1, focus.abandonCalls);
    }

    // (f) Overlay/video failure: emergency cleanup, overlay removed, focus abandoned, zero play.
    /** Verify overlayFailure emergencyCleanup zeroPlay. */
    @Test public void overlayFailure_emergencyCleanup_zeroPlay() {
        overlay.throwOnPlay = true;
        InterludeStateMachine m = machine();
        m.begin(target());
        m.observe(snapshot(PKG, PlaybackStateCodes.STATE_PAUSED, "mid-123", "The Expanse"));

        assertEquals(InterludeState.STOPPED, m.state());
        assertTrue(m.diagnostics().localVideoError);
        assertEquals("no play when our overlay/video failed", 0, media.playCalls);
        assertEquals("overlay removed on failure", 1, overlay.removeCalls);
        assertEquals("focus abandoned on failure", 1, focus.abandonCalls);
    }

    // (g) Repeated STOP / emergency cleanup is idempotent and never throws.
    /** Verify repeatedStop isIdempotent. */
    @Test public void repeatedStop_isIdempotent() {
        InterludeStateMachine m = machine();
        m.begin(target());
        m.observe(snapshot(PKG, PlaybackStateCodes.STATE_PAUSED, "mid-123", "The Expanse"));

        m.stop();
        assertEquals(InterludeState.STOPPED, m.state());
        int removeAfterFirst = overlay.removeCalls;
        int abandonAfterFirst = focus.abandonCalls;
        int audioStopAfterFirst = audio.stopCalls;

        // Calling stop again (and feeding late events) must not change effects or throw.
        m.stop();
        m.stop();
        m.onVideoCompleted();
        m.onTick();
        m.observe(snapshot(PKG, PlaybackStateCodes.STATE_PLAYING, "mid-123", "The Expanse"));

        assertEquals(InterludeState.STOPPED, m.state());
        assertEquals("overlay removed once-effectively", removeAfterFirst, overlay.removeCalls);
        assertEquals("focus abandoned once-effectively", abandonAfterFirst, focus.abandonCalls);
        assertEquals("local audio stopped once-effectively",
                audioStopAfterFirst, audio.stopCalls);
        assertEquals("no play after teardown", 0, media.playCalls);
    }

    // (h) No accidental PLAY after an unsupported / unconfirmed pause.
    /** Verify noAccidentalPlay afterUnconfirmedPause. */
    @Test public void noAccidentalPlay_afterUnconfirmedPause() {
        InterludeStateMachine m = machine();
        m.begin(target());
        assertEquals(InterludeState.PAUSE_SENT, m.state());

        // Never observe PAUSED. Force the sequence toward completion anyway.
        // Out-of-order onVideoCompleted while pause unconfirmed must not resume.
        m.onVideoCompleted();

        assertEquals("sent-but-unconfirmed pause must not authorize play", 0, media.playCalls);
        assertEquals(InterludeState.STOPPED, m.state());
    }

    // Reinforces (sent != confirmed): pausing is dispatched but interlude waits.
    /** Verify pauseSentIsNotPauseConfirmed. */
    @Test public void pauseSentIsNotPauseConfirmed() {
        InterludeStateMachine m = machine();
        m.begin(target());
        assertEquals(1, media.pauseCalls);
        assertTrue(m.diagnostics().pauseCommandSent);
        assertFalse(m.diagnostics().pauseConfirmed);
        assertEquals("overlay must not attach on a mere sent command", 0, overlay.attachCalls);
        assertEquals(InterludeState.PAUSE_SENT, m.state());
    }
}
