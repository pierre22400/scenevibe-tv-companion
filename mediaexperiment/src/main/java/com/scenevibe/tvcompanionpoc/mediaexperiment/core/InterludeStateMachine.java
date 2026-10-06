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
    private final SessionPort sessions;
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
    private boolean pauseOnly;
    private boolean audioDuckOnly;
    private boolean resourcesReleased;
    private boolean resourceCleanupFailed;
    private int initialPlaybackState = PlaybackStateCodes.STATE_NONE;
    private long audioDeadlineMs;

    /** Inject live-session observations and ports; no Android state is inferred. */
    public InterludeStateMachine(
            AudioFocusPort audioFocus,
            MediaControlPort mediaControl,
            SessionPort sessions,
            OverlayVideoPort overlayVideo,
            LocalAudioPort localAudio,
            SafeResumeGuard guard,
            Clock clock,
            long pauseTimeoutMs,
            long playTimeoutMs) {
        this.audioFocus = audioFocus;
        this.mediaControl = mediaControl;
        this.sessions = sessions;
        this.overlayVideo = overlayVideo;
        this.localAudio = localAudio;
        this.guard = guard;
        this.clock = clock;
        this.pauseTimeoutMs = pauseTimeoutMs;
        this.playTimeoutMs = playTimeoutMs;
        record(InterludeState.IDLE);
    }

    /** Current bounded state, suitable for the operator UI. */
    public InterludeState state() {
        return state;
    }

    /** Return a defensive copy of the bounded mechanism-only transition log. */
    public List<InterludeState> transitionLog() {
        return Collections.unmodifiableList(new ArrayList<>(log));
    }

    /** Return coarse facts without exposing token or media content. */
    public Diagnostics diagnostics() {
        return diagnostics;
    }

    // --- Sequence entry points ------------------------------------------------

    /**
     * Begin a full interlude for the given target. Requests focus for the local
     * video's audio before altering transport; no separate cue is played. If
     * DENIED, no local audio is played, no pause is sent, focus is abandoned, and
     * the machine moves to {@link InterludeState#FOCUS_DENIED} then {@link #STOPPED}.
     */
    public void begin(SessionTarget target) {
        beginAttempt(target, false);
    }

    /** TEST PAUSE owns only its observed pause: no focus, cue, overlay, video or PLAY. */
    public void beginPause(SessionTarget target) {
        beginAttempt(target, true);
    }

    /** TEST AUDIO DUCK requests focus and a bounded local cue, never transport/video. */
    public void beginAudioDuck() {
        if (state != InterludeState.IDLE || stopped) {
            unexpected();
            return;
        }
        audioDuckOnly = true;
        if (!requestFocus()) return;
        try {
            localAudio.playShortClip();
            audioDeadlineMs = clock.nowMs() + 2000L;
        } catch (RuntimeException failure) {
            deny(DeniedReason.PORT_ERROR);
        }
    }

    /** Prepare an independently selected pause-only or full-interlude attempt. */
    private void beginAttempt(SessionTarget target, boolean pauseOnly) {
        if (state != InterludeState.IDLE || stopped) {
            unexpected();
            return;
        }
        this.target = target;
        this.pauseOnly = pauseOnly;
        transition(InterludeState.SCANNING);
        diagnostics.selectedPackage = target == null ? null : target.packageName;
        diagnostics.mediaIdPresent =
                target != null && target.mediaId != null && !target.mediaId.trim().isEmpty();
        if (target == null) {
            deny(DeniedReason.NO_SESSION);
            return;
        }

        SessionRevalidation live = revalidate();
        if (!requirePlaying(live)) return;
        if (!sessions.canPause()) {
            deny(DeniedReason.PAUSE_UNSUPPORTED);
            return;
        }
        // Full interlude audio requires focus; pause-only deliberately requests none.
        if (!pauseOnly && !requestFocus()) return;
        sendPause();
    }

    /** Request transient focus without turning a grant into evidence of ducking. */
    private boolean requestFocus() {
        transition(InterludeState.FOCUS_REQUESTED);
        AudioFocusPort.Result result;
        try {
            result = audioFocus.requestTransientMayDuck();
        } catch (RuntimeException failure) {
            deny(DeniedReason.PORT_ERROR);
            return false;
        }
        diagnostics.audioFocusResult = result;
        if (result != AudioFocusPort.Result.GRANTED) {
            transition(InterludeState.FOCUS_DENIED);
            // No pause, no local audio. Clean abandon.
            deny(DeniedReason.FOCUS_DENIED);
            return false;
        }
        transition(InterludeState.FOCUS_GRANTED);

        return true;
    }

    /** Re-observe PLAYING immediately before dispatch, then record sent-only evidence. */
    private void sendPause() {
        SessionRevalidation live = revalidate();
        if (!requirePlaying(live)) return;
        target = SessionTarget.from(live.latest);
        initialPlaybackState = live.latest.state;
        diagnostics.initialPlaybackStateName = live.latest.stateName();
        try {
            mediaControl.pause();
        } catch (RuntimeException failure) {
            deny(DeniedReason.PORT_ERROR);
            return;
        }
        pauseSent = true;
        diagnostics.pauseCommandSent = true;
        pauseDeadlineMs = clock.nowMs() + pauseTimeoutMs;
        transition(InterludeState.PAUSE_SENT);
    }

    /** Refuse a pre-existing pause or any state other than positively observed PLAYING. */
    private boolean requirePlaying(SessionRevalidation live) {
        if (!live.valid()) {
            deny(live.reason);
            return false;
        }
        diagnostics.initialPlaybackStateName = live.latest.stateName();
        diagnostics.playbackStateName = live.latest.stateName();
        if (live.latest.state != PlaybackStateCodes.STATE_PLAYING) {
            deny(DeniedReason.INITIAL_STATE_NOT_PLAYING);
            return false;
        }
        return true;
    }

    /** Record an actual fresh active-session query, never a cached controller sample. */
    private SessionRevalidation revalidate() {
        diagnostics.sessionRevalidationAttempted = true;
        SessionRevalidation live;
        try {
            live = sessions.revalidate(target);
        } catch (RuntimeException failure) {
            live = null;
        }
        if (live == null) {
            live = new SessionRevalidation(null, false, false, DeniedReason.SESSION_QUERY_FAILED);
        }
        diagnostics.originalSessionIdentityPresent = live.originalSessionPresent;
        diagnostics.relevantActivePackageChanged = live.relevantPackageChanged;
        diagnostics.sessionRevalidationSucceeded = live.valid();
        return live;
    }

    /** Runtime polling uses the same catalog policy as the scanner and transport ports. */
    public void poll() {
        if (stopped) return;
        if (audioDuckOnly) {
            onTick();
            return;
        }
        if (target == null) return;
        SessionRevalidation live = revalidate();
        if (!live.valid()) {
            deny(live.reason);
            return;
        }
        observe(live.latest);
        onTick();
    }

    /** Feed an observed snapshot. Confirms pause/play or ignores irrelevant states. */
    public void onSnapshot(PlaybackSnapshot snapshot) {
        if (stopped) return;
        if (snapshot == null) {
            if (snapshot == null) { unexpected(); }
            return;
        }
        diagnostics.playbackStateName = snapshot.stateName();
        if (!sameSession(snapshot) || !PocMediaIdentity.sameMedia(target, snapshot)) {
            deny(sameSession(snapshot) ? DeniedReason.MEDIA_CHANGED : DeniedReason.SESSION_TOKEN_CHANGED);
            return;
        }

        if (state == InterludeState.PAUSE_SENT) {
            if (clock.nowMs() > pauseDeadlineMs) {
                onPauseTimeout();
                return;
            }
            if (snapshot.state == PlaybackStateCodes.STATE_PAUSED
                    && initialPlaybackState == PlaybackStateCodes.STATE_PLAYING) {
                pauseConfirmed = true;
                diagnostics.pauseConfirmed = true;
                diagnostics.pauseOwnershipAcquired = true;
                transition(InterludeState.PAUSE_CONFIRMED);
                if (pauseOnly) {
                    cleanup();
                } else {
                    startInterlude();
                }
            }
            return;
        }

        if (state == InterludeState.PLAY_SENT) {
            if (clock.nowMs() > playDeadlineMs) {
                onPlayTimeout();
                return;
            }
            if (snapshot.state == PlaybackStateCodes.STATE_PLAYING
                    && sameSession(snapshot)) {
                diagnostics.resumeConfirmed = true;
                transition(InterludeState.PLAY_CONFIRMED);
                cleanup();
            }
        } else if (state == InterludeState.VIDEO_PLAYING
                && snapshot.state != PlaybackStateCodes.STATE_PAUSED) {
            deny(DeniedReason.LIVE_STATE_NOT_PAUSED);
        }
    }

    /** Explicit clock tick to evaluate pending confirmation deadlines. */
    public void onTick() {
        if (stopped) return;
        long now = clock.nowMs();
        if (audioDuckOnly && now >= audioDeadlineMs) {
            cleanup();
        } else if (state == InterludeState.PAUSE_SENT && now > pauseDeadlineMs) {
            onPauseTimeout();
        } else if (state == InterludeState.PLAY_SENT && now > playDeadlineMs) {
            onPlayTimeout();
        }
    }

    /** Missing confirmation never authorizes the interlude or a compensating PLAY. */
    private void onPauseTimeout() {
        // Pause was sent but never confirmed: UNSUPPORTED / NOT CONFIRMED.
        diagnostics.pauseTimedOut = true;
        diagnostics.resumeDeniedReason = DeniedReason.PAUSE_TIMEOUT;
        transition(InterludeState.PAUSE_TIMEOUT);
        // Do NOT continue to the interlude. Clean up (never resume: pause unconfirmed).
        cleanup();
    }

    /** A missing PLAYING confirmation is reported without issuing a second PLAY. */
    private void onPlayTimeout() {
        diagnostics.resumeTimedOut = true;
        transition(InterludeState.PLAY_TIMEOUT);
        cleanup();
    }

    /** Attach and play only after acquiring an observed, session-bound pause. */
    private void startInterlude() {
        try {
            overlayVideo.attach();
            if (stopped) return;
            diagnostics.overlayAttached = true;
            transition(InterludeState.OVERLAY_ATTACHED);
            transition(InterludeState.VIDEO_PLAYING);
            overlayVideo.playLocalVideo();
            if (!stopped) diagnostics.localVideoStarted = true;
        } catch (RuntimeException error) {
            // Overlay/video failure always tears down without resuming.
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
        diagnostics.resumeDeniedReason = DeniedReason.VIDEO_ERROR;
        transition(InterludeState.VIDEO_ERROR);
        // Fail closed: do not resume on our own overlay/video failure.
        cleanup();
    }

    /** Remove local playback/focus, rescan the original session, then guard one PLAY. */
    private void attemptResume() {
        releaseLocalResources();
        if (stopped) return;
        if (resourceCleanupFailed) {
            deny(DeniedReason.PORT_ERROR);
            return;
        }
        PauseOwnership ownership = new PauseOwnership(
                initialPlaybackState, pauseSent, pauseConfirmed, videoCompletedNormally);
        SessionRevalidation live = revalidate();
        diagnostics.latestStateBeforeResume = live.latest == null ? "NONE" : live.latest.stateName();
        diagnostics.resumeDeniedReason = guard.denialReason(target, ownership, live);
        if (guard.mayResume(target, ownership, live)) {
            try {
                mediaControl.play();
            } catch (RuntimeException failure) {
                deny(DeniedReason.PORT_ERROR);
                return;
            }
            diagnostics.playCommandSent = true;
            playDeadlineMs = clock.nowMs() + playTimeoutMs;
            transition(InterludeState.PLAY_SENT);
        } else {
            // Guard refused resume: tear down without touching transport PLAY.
            cleanup();
        }
    }

    /**
     * Advance pending confirmation from an observed snapshot. Resume authorization
     * never trusts this snapshot alone: completion performs a fresh active-session
     * query through the session port.
     */
    public void observe(PlaybackSnapshot snapshot) {
        onSnapshot(snapshot);
    }

    /**
     * Idempotent teardown. Removes the overlay, stops local audio, abandons focus,
     * and converges on {@link InterludeState#STOPPED}. Safe to call repeatedly.
     */
    public void stop() {
        if (!stopped) diagnostics.resumeDeniedReason = DeniedReason.STOP_REQUESTED;
        cleanup();
    }

    /** Tear down exactly once; STOP and every error path never dispatch PLAY. */
    private void cleanup() {
        if (stopped) {
            // Idempotent: already cleaned. Do not re-abandon ports or re-log.
            return;
        }
        stopped = true;
        releaseLocalResources();
        transition(InterludeState.STOPPED);
    }

    /** Release local resources once, also before normal guarded resume. */
    private void releaseLocalResources() {
        if (resourcesReleased) return;
        resourcesReleased = true;
        safeStopLocalAudio();
        safeRemoveOverlay();
        // Always abandon focus during teardown; the port is a safe no-op when
        // nothing is held. This guarantees a clean abandon even on the
        // focus-denied path.
        safeAbandonFocus();
    }

    /** Keep unsupported event order fail-closed. */
    private void unexpected() {
        // Out-of-order / unexpected event: fail closed to cleanup, never crash.
        deny(DeniedReason.UNEXPECTED_EVENT);
    }

    /** Package equality alone is never evidence of MediaSession equality. */
    private boolean sameSession(PlaybackSnapshot snapshot) {
        return target != null && target.packageName != null
                && target.packageName.equals(snapshot.packageName)
                && target.sessionIdentity != null
                && target.sessionIdentity.equals(snapshot.sessionIdentity);
    }

    /** Store a bounded denial and converge on conservative cleanup. */
    private void deny(DeniedReason reason) {
        diagnostics.resumeDeniedReason = reason;
        cleanup();
    }

    /** A faulty audio port must not prevent the rest of teardown. */
    private void safeStopLocalAudio() {
        try {
            localAudio.stop();
        } catch (RuntimeException ignored) {
            resourceCleanupFailed = true;
            // Teardown must not throw.
        }
    }

    /** A faulty overlay port must not prevent focus release. */
    private void safeRemoveOverlay() {
        try {
            overlayVideo.remove();
        } catch (RuntimeException ignored) {
            resourceCleanupFailed = true;
            // Teardown must not throw.
        }
    }

    /** A faulty focus port must not make cleanup throw. */
    private void safeAbandonFocus() {
        try {
            audioFocus.abandon();
        } catch (RuntimeException ignored) {
            resourceCleanupFailed = true;
            // Teardown must not throw.
        }
    }

    /** Advance the state and record only its enum label. */
    private void transition(InterludeState next) {
        state = next;
        record(next);
    }

    /** Keep transition history bounded to the most recent mechanism states. */
    private void record(InterludeState s) {
        if (log.size() >= MAX_LOG) {
            log.remove(0);
        }
        log.add(s);
    }
}
