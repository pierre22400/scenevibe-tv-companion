package com.scenevibe.tvcompanionpoc.mediaexperiment;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.util.Log;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.AudioFocusPort;

/**
 * Android implementation of {@link AudioFocusPort} using {@link AudioManager} and
 * {@link AudioFocusRequest} with {@code AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK}.
 *
 * <p>Rationale for the AudioAttributes pair: the SceneVibe interlude cue is short
 * SPOKEN audio layered briefly over the user's media, so we use
 * {@code USAGE_ASSISTANT} with {@code CONTENT_TYPE_SPEECH}. This is the honest
 * description of a transient spoken assistant-style cue and is exactly the kind of
 * stream the platform is designed to ALLOW to duck other media rather than stop it.
 * TRANSIENT_MAY_DUCK asks the current player (e.g. Prime) to lower its volume for
 * the duration; it is the system — not this app — that performs any ducking.</p>
 *
 * <p>This class NEVER changes stream volume programmatically (no
 * {@code setStreamVolume}); it only requests and abandons transient focus.</p>
 */
final class AndroidAudioFocusPort implements AudioFocusPort {
    private static final String TAG = "SceneVibeInterludePoc";

    private final AudioManager audioManager;
    private final AudioFocusRequest focusRequest;
    private final AudioManager.OnAudioFocusChangeListener focusListener =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    // Observational only: the state machine drives teardown. We do not
                    // modify any player or stream here.
                    Log.i(TAG, "AUDIO_FOCUS_CHANGE change=" + focusChange);
                }
            };

    private boolean held;

    AndroidAudioFocusPort(Context context) {
        this.audioManager = context.getSystemService(AudioManager.class);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        this.focusRequest =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(attributes)
                        .setWillPauseWhenDucked(false)
                        .setOnAudioFocusChangeListener(focusListener)
                        .build();
    }

    @Override
    public Result requestTransientMayDuck() {
        if (audioManager == null) {
            return Result.DENIED;
        }
        int result = audioManager.requestAudioFocus(focusRequest);
        boolean granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        held = granted;
        Log.i(TAG, "AUDIO_FOCUS_REQUEST granted=" + granted);
        return granted ? Result.GRANTED : Result.DENIED;
    }

    @Override
    public void abandon() {
        if (audioManager == null || !held) {
            return;
        }
        audioManager.abandonAudioFocusRequest(focusRequest);
        held = false;
        Log.i(TAG, "AUDIO_FOCUS_ABANDONED");
    }
}
