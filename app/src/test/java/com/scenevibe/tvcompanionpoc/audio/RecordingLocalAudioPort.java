package com.scenevibe.tvcompanionpoc.audio;

import java.util.ArrayList;
import java.util.List;

/**
 * JVM-only fake {@link LocalAudioPort} that records every call so tests can assert
 * ordering and lifecycle. It implements EXACTLY the Android-free port surface, so
 * there is structurally NO audio-focus request and NO {@code setStreamVolume} path
 * reachable from the CORE (those APIs are not part of this interface at all),
 * mirroring the A1 {@code VoiceCoexistenceProbeTest} no-focus guards.
 */
final class RecordingLocalAudioPort implements LocalAudioPort {

    /** Append-only call log, e.g. {@code "prepare:cue.aac"}, {@code "start"}. */
    final List<String> calls = new ArrayList<>();

    int prepareCount;
    int startCount;
    int stopCount;
    int releaseCount;
    String lastAssetRef;
    float lastGain = Float.NaN;

    /** Optional: throw from {@link #start()} to model a decode failure. */
    boolean failOnStart;

    private State state = State.IDLE;

    /** Record prepare and move to PREPARED without producing audio. */
    @Override public void prepare(String assetRef) {
        prepareCount++;
        lastAssetRef = assetRef;
        calls.add("prepare:" + assetRef);
        state = State.PREPARED;
    }

    /** Record start, honoring an optional simulated decode failure. */
    @Override public void start() {
        startCount++;
        calls.add("start");
        if (failOnStart) {
            state = State.ERROR;
            throw new IllegalStateException("decode-failed");
        }
        state = State.STARTED;
    }

    /** Record a safe, idempotent stop. */
    @Override public void stop() {
        stopCount++;
        calls.add("stop");
        if (state != State.ERROR) {
            state = State.IDLE;
        }
    }

    /** Record a safe, idempotent release. */
    @Override public void release() {
        releaseCount++;
        calls.add("release");
        state = State.IDLE;
    }

    /** Record the LOCAL gain applied to this port's own stream only. */
    @Override public void setGain(float gain) {
        lastGain = gain;
        calls.add("setGain:" + gain);
    }

    /** Return the Android-free lifecycle state. */
    @Override public State state() {
        return state;
    }
}
