package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Android-free orchestrator for the local interlude capability spike.
 *
 * <p>It drives the {@link InterludeState} sequence through the injected ports and
 * enforces three correctness rules that are the heart of this POC:</p>
 * <ul>
 *   <li><b>Sent != confirmed.</b> A dispatched pause only advances to the interlude
 *       after an OBSERVED {@code PAUSED} snapshot. A missed confirmation before the
 *       deadline becomes {@link InterludeState#PAUSE_TIMEOUT} (reported UNSUPPORTED /
 *       NOT CONFIRMED) and the sequence does not continue.</li>
 *   <li><b>Guarded resume.</b> PLAY is dispatched only when {@link SafeResumeGuard}
 *       permits it against the recorded pause-ownership facts, the original target,
 *       and the latest observed snapshot.</li>
 *   <li><b>Fail-closed cleanup.</b> Any unexpected/out-of-order event or port error
 *       converges on {@link InterludeState#STOPPED} with the overlay removed and
 *       audio focus abandoned, without throwing. {@link #stop()} is idempotent.</li>
 * </ul>
 *
 * <p>These are logic guarantees only; nothing here proves real audio ducking or a
 * real streaming-app pause, which are physical on-device observations.</p>
 */
public final class InterludeStateMachine {
    private static final int MAX_LOG = 64;

    private final AudioFocusPort audioFocus;
    private final MediaControlPort mediaControl;
    private final OverlayVideoPort overlayVideo;
    private final LocalAudioPort localAudio;
    private final SafeResumeGuard guard;
    private final Clock clock;
    private final long pauseTimeoutMs;
    private final long playTimeoutMs;

    private final List<InterludeState> log = new ArrayList<>();
    private final Diagnostics diagnostics = new Diagnostics();

    private InterludeState state = InterludeState.IDLE;
    private SessionTarget target;

    private boolean pauseSent;
    private boolean pauseConfirmed;
    private boolean videoCompletedNormally;
    private long pauseDeadlineMs;
    private long playDeadlineMs;
    private boolean stopped;

    public InterludeStateMachine(
            AudioFocusPort audioFocus,
            MediaControlPort mediaControl,
            OverlayVideoPort overlayVideo,
            LocalAudioPort localAudio,
            SafeResumeGuard guard,
            Clock clock,
            long pauseTimeoutMs,
            long playTimeoutMs) {
        this.audioFocus = audioFocus;
        this.mediaControl = mediaControl;
        this.overlayVideo = overlayVideo;
        this.localAudio = localAudio;
        this.guard = guard;
        this.clock = clock;
        this.pauseTimeoutMs = pauseTimeoutMs;
        this.playTimeoutMs = playTimeoutMs;
        record(InterludeState.IDLE);
    }

    public InterludeState state() {
        return state;
    }

    public List<InterludeState> transitionLog() {
        return Collections.unmodifiableList(new ArrayList<>(log));
    }

    public Diagnostics diagnostics() {
        return diagnostics;
    }

    // --- Sequence entry points ------------------------------------------------

    /**
     * Begin an interlude for the given target. Requests transient audio focus; if
     * DENIED, no local audio is played, no pause is sent, focus is abandoned, and
     * the machine moves to {@link InterludeState#FOCUS_DENIED} then {@link #STOPPED}.
     */
    public void begin(SessionTarget target) {
        if (state != InterludeState.IDLE || stopped) {
            unexpected();
            return;
        }
        this.target = target;
        transition(InterludeState.SCANNING);
        transition(InterludeState.FOCUS_REQUESTED);
        diagnostics.selectedPackage = target == null ? null : target.packageName;
        diagnostics.mediaIdPresent =
                target != null && target.mediaId != null && !target.mediaId.trim().isEmpty();

        AudioFocusPort.Result result = audioFocus.requestTransientMayDuck();
        diagnostics.audioFocusResult = result;
        if (result != AudioFocusPort.Result.GRANTED) {
            transition(InterludeState.FOCUS_DENIED);
            // No pause, no local audio. Clean abandon.
            cleanup();
            return;
        }
        transition(InterludeState.FOCUS_GRANTED);

        // Focus granted: start the short local audio clip, then dispatch pause.
        localAudio.playShortClip();
        sendPause();
    }

    private void sendPause() {
        mediaControl.pause();
        pauseSent = true;
        diagnostics.pauseCommandSent = true;
        pauseDeadlineMs = clock.nowMs() + pauseTimeoutMs;
        transition(InterludeState.PAUSE_SENT);
    }

    /** Feed an observed snapshot. Confirms pause/play or ignores irrelevant states. */
    public void onSnapshot(PlaybackSnapshot snapshot) {
        if (stopped || snapshot == null) {
            if (snapshot == null) { unexpected(); }
            return;
        }
        diagnostics.playbackStateName = snapshot.stateName();

        if (state == InterludeState.PAUSE_SENT) {
            if (clock.nowMs() > pauseDeadlineMs) {
                onPauseTimeout();
                return;
            }
            if (snapshot.state == PlaybackStateCodes.STATE_PAUSED
                    && samePackage(snapshot)) {
                pauseConfirmed = true;
                diagnostics.pauseConfirmed = true;
                transition(InterludeState.PAUSE_CONFIRMED);
                startInterlude();
            }
            return;
        }

        if (state == InterludeState.PLAY_SENT) {
            if (clock.nowMs() > playDeadlineMs) {
                onPlayTimeout();
                return;
            }
            if (snapshot.state == PlaybackStateCodes.STATE_PLAYING
                    && samePackage(snapshot)) {
                diagnostics.resumeConfirmed = true;
                transition(InterludeState.PLAY_CONFIRMED);
                cleanup();
            }
        }
    }

    /** Explicit clock tick to evaluate pending confirmation deadlines. */
    public void onTick() {
        if (stopped) return;
        long now = clock.nowMs();
        if (state == InterludeState.PAUSE_SENT && now > pauseDeadlineMs) {
            onPauseTimeout();
        } else if (state == InterludeState.PLAY_SENT && now > playDeadlineMs) {
            onPlayTimeout();
        }
    }

    private void onPauseTimeout() {
        // Pause was sent but never confirmed: UNSUPPORTED / NOT CONFIRMED.
        diagnostics.pauseTimedOut = true;
        transition(InterludeState.PAUSE_TIMEOUT);
        // Do NOT continue to the interlude. Clean up (never resume: pause unconfirmed).
        cleanup();
    }

    private void onPlayTimeout() {
        diagnostics.resumeTimedOut = true;
        transition(InterludeState.PLAY_TIMEOUT);
        cleanup();
    }

    private void startInterlude() {
        try {
            overlayVideo.attach();
            diagnostics.overlayAttached = true;
            transition(InterludeState.OVERLAY_ATTACHED);
            overlayVideo.playLocalVideo();
            diagnostics.localVideoStarted = true;
            transition(InterludeState.VIDEO_PLAYING);
        } catch (RuntimeException error) {
            // Overlay/video failed: emergency cleanup. Resume only if pause owned.
            diagnostics.localVideoError = true;
            onVideoError();
        }
    }

    /** The local interlude video finished normally. */
    public void onVideoCompleted() {
        if (stopped) return;
        if (state != InterludeState.VIDEO_PLAYING) {
            unexpected();
            return;
        }
        videoCompletedNormally = true;
        diagnostics.localVideoCompleted = true;
        transition(InterludeState.VIDEO_COMPLETED);
        attemptResume();
    }

    /**
     * The local interlude video (or overlay) reported an error.
     *
     * <p>Emergency path: the POC's own overlay/video failed, so transport state is
     * uncertain. The safest behavior is to tear down WITHOUT dispatching PLAY. We
     * therefore converge straight on cleanup and never touch transport controls on
     * this path, satisfying "no PLAY on overlay/video failure".</p>
     */
    public void onVideoError() {
        if (stopped) return;
        diagnostics.localVideoError = true;
        transition(InterludeState.VIDEO_ERROR);
        // Fail closed: do not resume on our own overlay/video failure.
        cleanup();
    }

    private void attemptResume() {
        PauseOwnership ownership = new PauseOwnership(
                pauseSent, pauseConfirmed, videoCompletedNormally, false);
        PlaybackSnapshot latest = lastSnapshot;
        if (guard.mayResume(target, ownership, latest)) {
            mediaControl.play();
            diagnostics.playCommandSent = true;
            playDeadlineMs = clock.nowMs() + playTimeoutMs;
            transition(InterludeState.PLAY_SENT);
        } else {
            // Guard refused resume: tear down without touching transport PLAY.
            cleanup();
        }
    }

    // Track the most recent snapshot so the guard can re-check the live target.
    private PlaybackSnapshot lastSnapshot;

    /**
     * Record the latest observed snapshot for guard evaluation and advance any
     * pending confirmation. This is the single public ingestion point for
     * observed sessions.
     */
    public void observe(PlaybackSnapshot snapshot) {
        if (!stopped && snapshot != null) {
            lastSnapshot = snapshot;
        }
        onSnapshot(snapshot);
    }

    /**
     * Idempotent teardown. Removes the overlay, stops local audio, abandons focus,
     * and converges on {@link InterludeState#STOPPED}. Safe to call repeatedly.
     */
    public void stop() {
        cleanup();
    }

    private void cleanup() {
        if (stopped) {
            // Idempotent: already cleaned. Do not re-abandon ports or re-log.
            return;
        }
        stopped = true;
        safeStopLocalAudio();
        safeRemoveOverlay();
        // Always abandon focus during teardown; the port is a safe no-op when
        // nothing is held. This guarantees a clean abandon even on the
        // focus-denied path.
        safeAbandonFocus();
        transition(InterludeState.STOPPED);
    }

    private void unexpected() {
        // Out-of-order / unexpected event: fail closed to cleanup, never crash.
        cleanup();
    }

    private boolean samePackage(PlaybackSnapshot snapshot) {
        return target != null && target.packageName != null
                && target.packageName.equals(snapshot.packageName);
    }

    private void safeStopLocalAudio() {
        try {
            localAudio.stop();
        } catch (RuntimeException ignored) {
            // Teardown must not throw.
        }
    }

    private void safeRemoveOverlay() {
        try {
            overlayVideo.remove();
        } catch (RuntimeException ignored) {
            // Teardown must not throw.
        }
    }

    private void safeAbandonFocus() {
        try {
            audioFocus.abandon();
        } catch (RuntimeException ignored) {
            // Teardown must not throw.
        }
    }

    private void transition(InterludeState next) {
        state = next;
        record(next);
    }

    private void record(InterludeState s) {
        if (log.size() >= MAX_LOG) {
            log.remove(0);
        }
        log.add(s);
    }
}
