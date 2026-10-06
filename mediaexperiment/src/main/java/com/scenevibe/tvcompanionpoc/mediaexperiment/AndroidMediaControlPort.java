package com.scenevibe.tvcompanionpoc.mediaexperiment;

import android.media.session.MediaController;
import android.util.Log;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.MediaControlPort;

/**
 * Android implementation of {@link MediaControlPort} that issues transport
 * commands ONLY through the discovered {@link MediaController}'s
 * {@code getTransportControls()}. This is MediaSession transport — the sole
 * permitted mechanism. There is NO key/remote injection and NO UI interaction.
 *
 * <p>Dispatching a command is fire-and-forget: it is NOT confirmation. The core
 * state machine treats only an OBSERVED PAUSED/PLAYING snapshot as confirmation.</p>
 */
final class AndroidMediaControlPort implements MediaControlPort {
    private static final String TAG = "SceneVibeInterludePoc";

    private final MediaSessionScanner scanner;

    AndroidMediaControlPort(MediaSessionScanner scanner) {
        this.scanner = scanner;
    }

    @Override
    public void pause() {
        MediaController controller = scanner.currentController();
        if (controller == null) {
            Log.w(TAG, "PAUSE_NO_CONTROLLER");
            return;
        }
        controller.getTransportControls().pause();
        Log.i(TAG, "PAUSE_DISPATCHED package=" + controller.getPackageName());
    }

    @Override
    public void play() {
        MediaController controller = scanner.currentController();
        if (controller == null) {
            Log.w(TAG, "PLAY_NO_CONTROLLER");
            return;
        }
        controller.getTransportControls().play();
        Log.i(TAG, "PLAY_DISPATCHED package=" + controller.getPackageName());
    }
}
