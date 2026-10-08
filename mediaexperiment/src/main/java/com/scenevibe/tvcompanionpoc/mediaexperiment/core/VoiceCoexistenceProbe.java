package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/**
 * Isolated, Android-free Spike 2.0 voice-over-native-playback experiment.
 *
 * <p>Accepts only a freshly validated PLAYING Prime Video MediaSession, starts
 * local speech without an AudioFocus request, and revalidates the original
 * session on every poll. It has NO MediaControlPort and cannot send native
 * PAUSE or PLAY. "COMPLETED" denotes mechanical eligibility only: audible
 * mixing and continuous picture still require physical Sony observations.</p>
 */
public final class VoiceCoexistenceProbe {
    /** Mechanism-only outcome; none of these values asserts physically audible mixing. */
    public enum State {
        IDLE, PLAYING_FIXTURE, COMPLETED_NEEDS_PHYSICAL_PROOF,
        REFUSED_NOT_PLAYING, REFUSED_NOT_PRIME, REFUSED_SESSION,
        FIXTURE_FAILED, NATIVE_PLAYBACK_INTERRUPTED, SESSION_CHANGED,
        FIXTURE_TIMEOUT, STOPPED
    }

    private static final String PRIME_PACKAGE = "com.amazon.amazonvideo.livingroom";
    private static final long MAX_FIXTURE_MS = 13000L;

    private final SessionPort sessions;
    private final SpeechFixturePort speech;
    private final Clock clock;
    private SessionTarget target;
    private long deadline;
    private State state = State.IDLE;

    /** Construct the experiment without any capability to control the native player. */
    public VoiceCoexistenceProbe(SessionPort sessions, SpeechFixturePort speech, Clock clock) {
        this.sessions = sessions;
        this.speech = speech;
        this.clock = clock;
    }

    /** Guard native PLAYING and session identity before starting local speech. */
    public void begin() {
        speech.stop();
        target = null;
        state = State.IDLE;
        PlaybackSnapshot initial = sessions.scan();
        if (initial == null || initial.state != PlaybackStateCodes.STATE_PLAYING) {
            state = State.REFUSED_NOT_PLAYING;
            return;
        }
        if (!PRIME_PACKAGE.equals(initial.packageName)) {
            state = State.REFUSED_NOT_PRIME;
            return;
        }
        target = SessionTarget.from(initial);
        SessionRevalidation live = sessions.revalidate(target);
        if (live == null || !live.valid() || live.latest.state != PlaybackStateCodes.STATE_PLAYING) {
            state = State.REFUSED_SESSION;
            return;
        }
        deadline = clock.nowMs() + MAX_FIXTURE_MS;
        state = State.PLAYING_FIXTURE;
        try {
            speech.start();
        } catch (RuntimeException failure) {
            fail(State.FIXTURE_FAILED);
        }
    }

    /** Abort on any non-PLAYING native state, lost token or expired local deadline. */
    public void poll() {
        if (!running()) return;
        SessionRevalidation live = sessions.revalidate(target);
        if (live == null || !live.valid()) {
            fail(State.SESSION_CHANGED);
        } else if (live.latest.state != PlaybackStateCodes.STATE_PLAYING) {
            fail(State.NATIVE_PLAYBACK_INTERRUPTED);
        } else if (clock.nowMs() > deadline) {
            fail(State.FIXTURE_TIMEOUT);
        }
    }

    /** Record local completion only if the original session still plays. */
    public void onCompleted() {
        if (!running()) return;
        poll();
        if (!running()) return;
        speech.stop();
        state = State.COMPLETED_NEEDS_PHYSICAL_PROOF;
    }

    /** A platform decoder failure cannot be interpreted as voice coexistence. */
    public void onError() {
        if (running()) fail(State.FIXTURE_FAILED);
    }

    /** Emergency STOP is idempotent, audio-only, and never resumes native media. */
    public void stop() {
        speech.stop();
        state = State.STOPPED;
    }

    /** True only while the experimental local MP3 is actually requested. */
    public boolean running() {
        return state == State.PLAYING_FIXTURE;
    }

    /** Provide bounded, content-free diagnostics to the TV operator. */
    public State state() {
        return state;
    }

    /** Stop local playback and retain a truthful terminal reason. */
    private void fail(State reason) {
        speech.stop();
        state = reason;
    }
}
