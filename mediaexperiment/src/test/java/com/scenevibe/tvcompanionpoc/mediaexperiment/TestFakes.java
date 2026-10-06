package com.scenevibe.tvcompanionpoc.mediaexperiment;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.ActiveSessionSource;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.PlaybackSnapshot;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.PlaybackStateCodes;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SessionCatalog;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SessionController;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.InterludeRuntime;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.AudioFocusPort;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.Clock;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.LocalAudioPort;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.MediaControlPort;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.OverlayVideoPort;

import java.util.ArrayList;
import java.util.List;

/** Recording fakes shared by the pure-JVM interlude tests. */
final class TestFakes {

    static final class RecordingAudioFocusPort implements AudioFocusPort {
        Result result = Result.GRANTED;
        int requestCalls;
        int abandonCalls;
        Runnable onRequest;

        /** Record a transient focus request and its configured grant result. */
        @Override public Result requestTransientMayDuck() {
            requestCalls++;
            if (onRequest != null) onRequest.run();
            return result;
        }

        /** Record focus abandonment for teardown assertions. */
        @Override public void abandon() {
            abandonCalls++;
        }
    }

    /** Counts pause()/play() dispatches so tests can prove transport decisions. */
    static final class RecordingMediaControlPort implements MediaControlPort {
        int pauseCalls;
        int playCalls;

        /** Record a sent PAUSE without manufacturing confirmation. */
        @Override public void pause() {
            pauseCalls++;
        }

        /** Record a sent PLAY without manufacturing confirmation. */
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
        boolean throwOnRemove;
        Runnable onPlay;

        /** Record attachment and optionally fail to exercise teardown. */
        @Override public void attach() {
            attachCalls++;
            if (throwOnAttach) {
                throw new RuntimeException("overlay attach failed (fake)");
            }
        }

        /** Record local video start and optionally fail. */
        @Override public void playLocalVideo() {
            playLocalVideoCalls++;
            if (onPlay != null) onPlay.run();
            if (throwOnPlay) {
                throw new RuntimeException("overlay video failed (fake)");
            }
        }

        /** Record overlay removal for idempotence checks. */
        @Override public void remove() {
            removeCalls++;
            if (throwOnRemove) throw new IllegalStateException("Removal failed");
        }
    }

    static final class RecordingLocalAudioPort implements LocalAudioPort {
        int playShortClipCalls;
        int stopCalls;

        /** Record local cue playback. */
        @Override public void playShortClip() {
            playShortClipCalls++;
        }

        /** Record local cue teardown. */
        @Override public void stop() {
            stopCalls++;
        }
    }

    /** Mutable, deterministic clock. */
    static final class FakeClock implements Clock {
        long now;

        /** Return deterministic monotonic fixture time. */
        @Override public long nowMs() {
            return now;
        }

        /** Advance fixture time without sleeping. */
        void advance(long ms) {
            now += ms;
        }
    }

    /** Mutable active-list source; every catalog query observes its current inventory. */
    static final class MutableSessions implements ActiveSessionSource {
        final List<SessionController> active = new ArrayList<>();
        int queries;
        boolean failQuery;
        Runnable beforeQuery;

        /** Supply fresh inventory and optional race/failure injection at the source seam. */
        @Override public List<SessionController> activeSessions() {
            queries++;
            if (beforeQuery != null) beforeQuery.run();
            if (failQuery) throw new IllegalStateException("Session access lost");
            return new ArrayList<>(active);
        }
    }

    /** Transport never confirms itself: tests must change observed state separately. */
    static final class RecordingController implements SessionController {
        Object token = "original-token";
        String packageName = "com.amazon.amazonvideo.livingroom";
        String mediaId = "mid-123";
        int state = PlaybackStateCodes.STATE_PLAYING;
        boolean pauseSupported = true;
        int pauseCalls;
        int playCalls;

        /** Observe token and metadata separately, as the real Android adapter does. */
        @Override public PlaybackSnapshot snapshot() {
            return new PlaybackSnapshot(packageName, state, mediaId, "The Expanse",
                    "Episode 1", 3_600_000L, token);
        }

        /** Advertise a supported PLAY action. */
        @Override public boolean canPlay() { return true; }

        /** Advertise pause support only when the fixture permits it. */
        @Override public boolean canPause() { return pauseSupported; }

        /** This fixture has no combined action fallback. */
        @Override public boolean canPlayPause() { return false; }

        /** Record dispatch without fabricating a PAUSED observation. */
        @Override public void pause() { pauseCalls++; }

        /** Record dispatch without fabricating a PLAYING observation. */
        @Override public void play() { playCalls++; }
    }

    /** Same runtime/catalog wiring as the service/scanner, with a mutable platform source. */
    static final class RuntimeFixture {
        final MutableSessions sessions = new MutableSessions();
        final RecordingController original = new RecordingController();
        final SessionCatalog catalog = new SessionCatalog(sessions);
        final RecordingAudioFocusPort focus = new RecordingAudioFocusPort();
        final RecordingLocalAudioPort audio = new RecordingLocalAudioPort();
        final RecordingOverlayVideoPort overlay = new RecordingOverlayVideoPort();
        final FakeClock clock = new FakeClock();
        final InterludeRuntime runtime;

        /** Keep source/controller separate from recorded command effects. */
        RuntimeFixture() {
            sessions.active.add(original);
            runtime = new InterludeRuntime(catalog, focus, catalog, overlay, audio, clock);
        }

        /** Start full flow and separately deliver a later PAUSED observation. */
        void beginConfirmedVideo() {
            runtime.testFullInterlude();
            original.state = PlaybackStateCodes.STATE_PAUSED;
            runtime.poll();
        }
    }

    /** Utility holder; no instances needed. */
    private TestFakes() {}
}
