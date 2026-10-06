package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/**
 * The actual service action router, kept Android-free so JVM tests exercise wiring.
 * A new operator action tears down the previous attempt without PLAY, then gets a
 * fresh machine. Scan, duck, pause-only and full interlude have separate entry points.
 */
public final class InterludeRuntime {
    private final SessionPort sessions;
    private final AudioFocusPort focus;
    private final MediaControlPort media;
    private final OverlayVideoPort overlay;
    private final LocalAudioPort audio;
    private final Clock clock;
    private InterludeStateMachine machine;

    /** Wire the same session catalog into runtime observations and transport checks. */
    public InterludeRuntime(SessionPort sessions, AudioFocusPort focus, MediaControlPort media,
            OverlayVideoPort overlay, LocalAudioPort audio, Clock clock) {
        this.sessions = sessions;
        this.focus = focus;
        this.media = media;
        this.overlay = overlay;
        this.audio = audio;
        this.clock = clock;
        freshAttempt();
    }

    /** SCAN only records coarse facts; it never starts a media sequence. */
    public void scanMediaSession() {
        freshAttempt();
        selectedSnapshot();
    }

    /** AUDIO DUCK only requests focus and a local cue; no pause or overlay. */
    public void testAudioDuck() {
        freshAttempt();
        machine.beginAudioDuck();
    }

    /** PAUSE only leaves a confirmed pause for the operator to resume manually. */
    public void testPause() {
        freshAttempt();
        machine.beginPause(SessionTarget.from(selectedSnapshot()));
    }

    /** FULL interlude follows its own guarded sequence instead of calling testPause. */
    public void testFullInterlude() {
        freshAttempt();
        machine.begin(SessionTarget.from(selectedSnapshot()));
    }

    /** Rescan active sessions and advance confirmation/cue deadlines. */
    public void poll() {
        machine.poll();
    }

    /** Normal completion alone can request guarded resume. */
    public void onVideoCompleted() {
        machine.onVideoCompleted();
    }

    /** Local playback failure always tears down without PLAY. */
    public void onVideoError() {
        machine.onVideoError();
    }

    /** Repeated emergency STOP is idempotent and never resumes native media. */
    public void stop() {
        machine.stop();
    }

    /** Expose mechanism facts only to the diagnostic UI. */
    public Diagnostics diagnostics() {
        return machine.diagnostics();
    }

    /** Expose the current state for sampler lifecycle decisions. */
    public InterludeState state() {
        return machine.state();
    }

    /** Reset attempt-local evidence after safely stopping any earlier operation. */
    private void freshAttempt() {
        if (machine != null && machine.state() != InterludeState.IDLE) machine.stop();
        machine = new InterludeStateMachine(focus, media, sessions, overlay, audio,
                SafeResumeGuard.create(), clock, 4000L, 4000L);
    }

    /** Scan and publish flags without printing or persisting token/media content. */
    private PlaybackSnapshot selectedSnapshot() {
        PlaybackSnapshot snapshot = sessions.scan();
        Diagnostics diagnostics = machine.diagnostics();
        if (snapshot == null) {
            diagnostics.resumeDeniedReason = DeniedReason.NO_SESSION;
            return null;
        }
        diagnostics.selectedPackage = snapshot.packageName;
        diagnostics.playbackStateName = snapshot.stateName();
        diagnostics.initialPlaybackStateName = snapshot.stateName();
        diagnostics.mediaIdPresent = snapshot.mediaId != null && !snapshot.mediaId.trim().isEmpty();
        sessions.recordActions(diagnostics);
        return snapshot;
    }
}
