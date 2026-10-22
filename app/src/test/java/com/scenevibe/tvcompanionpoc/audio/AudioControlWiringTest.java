package com.scenevibe.tvcompanionpoc.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pure-JVM proof of the control semantics that {@code OverlayService} relies on for
 * the AUDIO 002 UI (independent TEXT/AUDIO toggles + SceneVibe LOCAL volume). The
 * service's reconcile step forwards {@code setGain}/{@code setAudioEnabled} to this
 * renderer; the Android wiring itself (service/activity) is instrumented/physical-only
 * under the sandbox toolchain limit, so the testable decision logic lives here in the
 * android-free CORE.
 */
public final class AudioControlWiringTest {

    private SceneVibeAudioRenderer armedRenderer(RecordingLocalAudioPort port) {
        SceneVibeAudioRenderer renderer = new SceneVibeAudioRenderer(port);
        renderer.setCatalog(DemoAudioTrack.DEMO_TOKEN, DemoAudioTrack.defaultEpisode().events());
        renderer.onEligibility(DemoAudioTrack.DEMO_TOKEN, true);
        renderer.onPlayback(DemoAudioTrack.DEMO_TOKEN, true, false);
        return renderer;
    }

    /** AUDIO OFF during playback stops+releases the renderer immediately. */
    @Test public void audioOffDuringPlaybackStopsAndReleasesImmediately() {
        RecordingLocalAudioPort port = new RecordingLocalAudioPort();
        SceneVibeAudioRenderer renderer = armedRenderer(port);
        renderer.onDue(DemoAudioTrack.DEMO_TOKEN, "demo-audio-1");
        assertTrue(renderer.isStarted());

        renderer.setAudioEnabled(false);

        assertFalse(renderer.isAudioEnabled());
        assertFalse(renderer.isStarted());
        assertTrue(port.stopCount >= 1);
        assertTrue(port.releaseCount >= 1);
    }

    /** With AUDIO OFF, a due comment produces no sound (toggle is independent of due). */
    @Test public void audioOffSuppressesSubsequentDue() {
        RecordingLocalAudioPort port = new RecordingLocalAudioPort();
        SceneVibeAudioRenderer renderer = armedRenderer(port);
        renderer.setAudioEnabled(false);

        renderer.onDue(DemoAudioTrack.DEMO_TOKEN, "demo-audio-1");

        assertEquals(0, port.startCount);
        assertFalse(renderer.isStarted());
    }

    /** Re-enabling AUDIO lets a later due comment play again (no retroactive replay). */
    @Test public void audioOnAfterOffResumesForNewDue() {
        RecordingLocalAudioPort port = new RecordingLocalAudioPort();
        SceneVibeAudioRenderer renderer = armedRenderer(port);
        renderer.setAudioEnabled(false);
        renderer.onDue(DemoAudioTrack.DEMO_TOKEN, "demo-audio-1");
        assertEquals(0, port.startCount);

        renderer.setAudioEnabled(true);
        renderer.onDue(DemoAudioTrack.DEMO_TOKEN, "demo-audio-2");

        assertTrue(renderer.isStarted());
        assertEquals(1, port.startCount);
    }

    /** SceneVibe volume is forwarded to the LOCAL port only; the value is in range. */
    @Test public void sceneVibeVolumeForwardedToLocalPortOnly() {
        RecordingLocalAudioPort port = new RecordingLocalAudioPort();
        SceneVibeAudioRenderer renderer = new SceneVibeAudioRenderer(port);
        renderer.setGain(0.4f);
        assertEquals(0.4f, port.lastGain, 0.0001f);
        assertTrue(port.lastGain >= 0.0f && port.lastGain <= 1.0f);
    }

    /**
     * The reconcile order the service uses (setGain then setAudioEnabled) is safe even
     * when AUDIO is being turned OFF: gain is recorded, then teardown runs.
     */
    @Test public void reconcileGainThenAudioOffIsSafe() {
        RecordingLocalAudioPort port = new RecordingLocalAudioPort();
        SceneVibeAudioRenderer renderer = armedRenderer(port);
        renderer.onDue(DemoAudioTrack.DEMO_TOKEN, "demo-audio-1");

        renderer.setGain(0.2f);
        renderer.setAudioEnabled(false);

        assertEquals(0.2f, port.lastGain, 0.0001f);
        assertFalse(renderer.isStarted());
    }
}
