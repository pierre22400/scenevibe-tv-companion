package com.scenevibe.tvcompanionpoc.mediaexperiment;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.AudioFocusPort;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.Clock;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.LocalAudioPort;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.MediaControlPort;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.OverlayVideoPort;

/** Recording fakes shared by the pure-JVM interlude tests. */
final class TestFakes {

    static final class RecordingAudioFocusPort implements AudioFocusPort {
        Result result = Result.GRANTED;
        int requestCalls;
        int abandonCalls;

        @Override public Result requestTransientMayDuck() {
            requestCalls++;
            return result;
        }

        @Override public void abandon() {
            abandonCalls++;
        }
    }

    /** Counts pause()/play() dispatches so tests can prove transport decisions. */
    static final class RecordingMediaControlPort implements MediaControlPort {
        int pauseCalls;
        int playCalls;

        @Override public void pause() {
            pauseCalls++;
        }

        @Override public void play() {
            playCalls++;
        }
    }

    static final class RecordingOverlayVideoPort implements OverlayVideoPort {
        int attachCalls;
        int playLocalVideoCalls;
        int removeCalls;
        boolean throwOnAttach;
        boolean throwOnPlay;

        @Override public void attach() {
            attachCalls++;
            if (throwOnAttach) {
                throw new RuntimeException("overlay attach failed (fake)");
            }
        }

        @Override public void playLocalVideo() {
            playLocalVideoCalls++;
            if (throwOnPlay) {
                throw new RuntimeException("overlay video failed (fake)");
            }
        }

        @Override public void remove() {
            removeCalls++;
        }
    }

    static final class RecordingLocalAudioPort implements LocalAudioPort {
        int playShortClipCalls;
        int stopCalls;

        @Override public void playShortClip() {
            playShortClipCalls++;
        }

        @Override public void stop() {
            stopCalls++;
        }
    }

    /** Mutable, deterministic clock. */
    static final class FakeClock implements Clock {
        long now;

        @Override public long nowMs() {
            return now;
        }

        void advance(long ms) {
            now += ms;
        }
    }

    private TestFakes() {}
}
