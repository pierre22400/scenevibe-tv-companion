package com.scenevibe.tvcompanionpoc;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic E5 ports: commands do not automatically change observations.
 * Tests must explicitly provide PAUSED/PLAYING, proving that command dispatch is not confirmation.
 * No Android, Cloud, native TV or decoder behavior is claimed by this harness.
 */
final class E5TestHarness {
    final List<String> events = new ArrayList<>();
    final Clock clock = new Clock();
    final Session session = new Session();
    final Player player = new Player();
    final Sink sink = new Sink();
    final NativeQualificationPolicy policy = NativeQualificationPolicy.local035();
    final NativeTransportOwner owner = new NativeTransportOwner(session, policy,
            (event, generation, snapshot, reason) -> events.add(event), clock::nowMs);
    final SceneRuntimeController controller = new SceneRuntimeController(sink);

    /** Construct a single enabled regie with independently controlled simulated observations. */
    E5TestHarness() {
        session.current = sample(NativeSessionSnapshot.PLAYING, 300000, clock.now, clock.now);
        controller.configureLocal(true, player, owner, clock,
                (event, generation, snapshot, reason) -> events.add(event));
    }

    /** Produce a coherent sample from one simulated token/program at the given times. */
    NativeSessionSnapshot sample(int state, long position, long observed, long updated) {
        return new NativeSessionSnapshot(session.token, NativeQualificationPolicy.PRIME_PACKAGE,
                "programme-one", "A specific VOD programme", "Episode one", 3000000,
                position, observed, updated, state == NativeSessionSnapshot.PLAYING ? 1f : 0f,
                state, true, true, true);
    }

    /** Refresh only reception time; a stationary PAUSED provider retains its original update. */
    void refreshPaused() {
        NativeSessionSnapshot s = session.current;
        session.current = sample(s.state, s.positionMs, clock.now, s.updatedAtMs);
    }

    /** Request and independently mark the local decoder ready; PAUSE remains unconfirmed. */
    void requestAndReady() {
        controller.requestLocal(LocalInterludePlan.qualification(true));
        player.ready();
    }

    /** Supply a real simulated PAUSED observation, distinct from the previously sent command. */
    void confirmPauseAndStart() {
        clock.now += 100;
        session.current = sample(NativeSessionSnapshot.PAUSED, 300100, clock.now, clock.now);
        controller.tickLocal();
    }

    /** Reach the known fixture EOF with a still-owned pause and a fresh reception. */
    void completeFile() {
        clock.now += 10000;
        refreshPaused();
        player.state = LocalVideoPort.State.STOPPED;
        player.callbacks.eof(player.generation);
    }

    /** Confirm, rather than infer, playback after the guard's single PLAY dispatch. */
    void confirmPlaying() {
        clock.now += 100;
        session.current = sample(NativeSessionSnapshot.PLAYING, 300200, clock.now, clock.now);
        controller.tickLocal();
    }

    /** Monotone execution-only clock; no editorial scheduler is introduced. */
    static final class Clock implements SceneRuntimeController.ExecutionClock {
        long now = 100000;
        /** Return explicit test time without sleeping. */
        @Override public long nowMs() { return now; }
    }

    /** Fake transport records accepted commands but never auto-confirms their effect. */
    final class Session implements NativeSessionPort {
        final Object token = new Object();
        NativeSessionSnapshot current;
        boolean selectable = true;
        boolean observable = true;
        boolean pauseAllowed = true;
        boolean playAllowed = true;
        int pauses;
        int plays;

        /** Return the independently supplied initial evidence. */
        @Override public NativeSessionSnapshot select(boolean vod) { return selectable ? current : null; }
        /** Model session/app departure by unavailable evidence, never substitution. */
        @Override public NativeSessionSnapshot observe(NativeSessionSnapshot original) {
            return observable ? current : null;
        }
        /** Record only an accepted PAUSE; leave observations unchanged until the test supplies one. */
        @Override public boolean pause(NativeSessionSnapshot original, NativeQualificationPolicy p, long now) {
            if (!pauseAllowed) return false;
            pauses++;
            events.add("pause-command");
            return true;
        }
        /** Record only an accepted PLAY; leave observations unchanged until the test supplies one. */
        @Override public boolean play(NativeSessionSnapshot anchor, NativeQualificationPolicy p, long now) {
            if (!playAllowed) return false;
            plays++;
            events.add("play-command");
            return true;
        }
    }

    /** Fake decoder separates PREPARING, READY, actual start and cleanup. */
    final class Player implements LocalVideoPort {
        State state = State.DETACHED;
        long generation;
        Callbacks callbacks;
        int preparations;
        int starts;
        int releases;

        /** Capture a generation-bound callback without asserting codec readiness. */
        @Override public void prepare(LocalInterludePlan plan, long attempt, Callbacks cb) {
            preparations++;
            generation = attempt;
            callbacks = cb;
            state = State.PREPARING;
            events.add("prepare");
        }
        /** Supply readiness separately from preparation dispatch. */
        void ready() {
            state = State.READY;
            callbacks.ready(generation);
        }
        /** Supply actual start after confirmed pause. */
        @Override public void play(long attempt) {
            starts++;
            state = State.PLAYING;
            events.add("player-start");
            callbacks.started(attempt);
        }
        /** Release at most once and never touch a newer generation. */
        @Override public void release(long attempt) {
            if (attempt != generation || state == State.RELEASED) return;
            releases++;
            state = State.RELEASED;
            events.add("player-release");
        }
        /** Observe actual fake decoder state. */
        @Override public State state() { return state; }
    }

    /** Shared one-scene sink records graphics/host cleanup without opening an Android window. */
    final class Sink implements SceneRuntimeController.SceneSink {
        boolean available = true;
        int localShows;
        int regularShows;
        int hides;
        /** Independently refuse graphical/host admission. */
        @Override public boolean preflight(OverlayManifest.Scene scene) { return available; }
        /** Record only a manifested graphical show. */
        @Override public void show(OverlayManifest.Scene scene) { regularShows++; }
        /** Record an ordinary visual retirement. */
        @Override public void hide(OverlayManifest.Scene scene) { hides++; }
        /** Record complete host cleanup. */
        @Override public void hideAll() { hides++; events.add("host-detach"); }
        /** Present inside the common scene slot only after native confirmation. */
        @Override public void showLocal(LocalInterludePlan plan) { localShows++; }
    }
}
