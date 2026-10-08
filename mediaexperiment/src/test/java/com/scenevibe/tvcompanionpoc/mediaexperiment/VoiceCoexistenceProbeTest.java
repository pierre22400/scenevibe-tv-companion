package com.scenevibe.tvcompanionpoc.mediaexperiment;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.Clock;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.DeniedReason;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.Diagnostics;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.PlaybackSnapshot;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.PlaybackStateCodes;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SessionPort;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SessionRevalidation;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SessionTarget;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SpeechFixturePort;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.VoiceCoexistenceProbe;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * JVM-only Spike 2.0 guards: no native transport or AudioFocus dependency exists.
 * Physically audible coexistence cannot be inferred from these unit tests.
 */
public final class VoiceCoexistenceProbeTest {
    private static final String PRIME = "com.amazon.amazonvideo.livingroom";
    private final MutableClock clock = new MutableClock();
    private final FakeSessions sessions = new FakeSessions();
    private final FakeSpeech speech = new FakeSpeech();
    private final VoiceCoexistenceProbe probe = new VoiceCoexistenceProbe(sessions, speech, clock);

    /** Native uninterrupted PLAYING must remain observable through completion. */
    @Test public void completesOnlyWithUninterruptedPlaying() {
        probe.begin();
        assertTrue(probe.running());
        assertEquals(1, speech.starts);
        clock.now = 11000L;
        probe.poll();
        probe.onCompleted();
        assertEquals(VoiceCoexistenceProbe.State.COMPLETED_NEEDS_PHYSICAL_PROOF, probe.state());
        assertFalse(probe.running());
        assertTrue(speech.stops >= 1);
    }

    /** The probe must fail closed if Prime pauses without a SceneVibe transport call. */
    @Test public void detectsNativePauseAndDoesNotResumeIt() {
        probe.begin();
        sessions.state = PlaybackStateCodes.STATE_PAUSED;
        probe.poll();
        assertEquals(VoiceCoexistenceProbe.State.NATIVE_PLAYBACK_INTERRUPTED, probe.state());
        assertFalse(probe.running());
    }

    /** A new token or player invalidates voice playback immediately. */
    @Test public void stopsOnSessionReplacement() {
        probe.begin();
        sessions.identity = new Object();
        probe.poll();
        assertEquals(VoiceCoexistenceProbe.State.SESSION_CHANGED, probe.state());
    }

    /** A paused episode is never made PLAYING merely to execute a fixture. */
    @Test public void refusesAlreadyPaused() {
        sessions.state = PlaybackStateCodes.STATE_PAUSED;
        probe.begin();
        assertEquals(VoiceCoexistenceProbe.State.REFUSED_NOT_PLAYING, probe.state());
        assertEquals(0, speech.starts);
    }

    /** A different provider cannot accidentally receive the Prime-specific experiment. */
    @Test public void refusesOtherPackage() {
        sessions.pack = "com.other.player";
        probe.begin();
        assertEquals(VoiceCoexistenceProbe.State.REFUSED_NOT_PRIME, probe.state());
        assertEquals(0, speech.starts);
    }

    /** Local decoding failures are not interpreted as proof of mixing. */
    @Test public void failsClosedOnSpeechFailure() {
        speech.failStart = true;
        probe.begin();
        assertEquals(VoiceCoexistenceProbe.State.FIXTURE_FAILED, probe.state());
        assertFalse(probe.running());
    }

    /** A missing completion callback cannot leave speech playing indefinitely. */
    @Test public void boundsFixtureTimeout() {
        probe.begin();
        clock.now = 13001L;
        probe.poll();
        assertEquals(VoiceCoexistenceProbe.State.FIXTURE_TIMEOUT, probe.state());
        assertFalse(probe.running());
    }

    /** Emergency STOP does not require a matching native PLAY. */
    @Test public void repeatedStopIsIdempotent() {
        probe.begin();
        probe.stop();
        probe.stop();
        assertEquals(VoiceCoexistenceProbe.State.STOPPED, probe.state());
        assertFalse(probe.running());
    }

    /** Provide deterministic elapsed time without Android dependencies. */
    private static final class MutableClock implements Clock {
        long now;
        /** Return the controlled monotonic time of this test. */
        @Override public long nowMs() { return now; }
    }

    /** Record local MP3 activity without a real player. */
    private static final class FakeSpeech implements SpeechFixturePort {
        int starts;
        int stops;
        boolean failStart;
        /** Simulate a synchronous decoder success or failure. */
        @Override public void start() { starts++; if (failStart) throw new IllegalStateException(); }
        /** Track cleanup even after partial setup. */
        @Override public void stop() { stops++; }
    }

    /** Model a mutable Prime MediaSession with an opaque token. */
    private static final class FakeSessions implements SessionPort {
        Object identity = new Object();
        String pack = PRIME;
        int state = PlaybackStateCodes.STATE_PLAYING;
        SessionTarget original;
        /** Select a new on-demand snapshot. */
        @Override public PlaybackSnapshot scan() { return latest(); }
        /** Revalidate exact token, package and playing state. */
        @Override public SessionRevalidation revalidate(SessionTarget target) {
            PlaybackSnapshot value = latest();
            return new SessionRevalidation(value,
                    target != null && target.sessionIdentity == identity,
                    false,
                    target != null && target.sessionIdentity == identity
                            ? DeniedReason.NONE : DeniedReason.SESSION_TOKEN_CHANGED);
        }
        /** No PAUSE support is used by this voice-only probe. */
        @Override public boolean canPause() { return false; }
        /** The transport actions are deliberately irrelevant. */
        @Override public void recordActions(Diagnostics diagnostics) {}
        /** Synthesize a minimal media identity without exposing content. */
        private PlaybackSnapshot latest() {
            return new PlaybackSnapshot(pack, state, "media-1", null, null, 100000L, identity);
        }
    }
}
