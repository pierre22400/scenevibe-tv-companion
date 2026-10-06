package com.scenevibe.tvcompanionpoc.mediaexperiment;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.MediaControlPort;

/**
 * Android implementation of {@link MediaControlPort} that issues transport
 * commands ONLY through the live, revalidated Android MediaController's
 * {@code getTransportControls()}. This is MediaSession transport — the sole
 * permitted mechanism. There is NO key/remote injection and NO UI interaction.
 *
 * <p>Dispatching a command is fire-and-forget: it is NOT confirmation. The core
 * state machine treats only an OBSERVED PAUSED/PLAYING snapshot as confirmation.</p>
 */
final class AndroidMediaControlPort implements MediaControlPort {
    private final MediaSessionScanner scanner;

    /** Use the same live catalog as the service observations. */
    AndroidMediaControlPort(MediaSessionScanner scanner) {
        this.scanner = scanner;
    }

    /** Dispatch PAUSE only after a fresh token/state recheck; failure throws. */
    @Override
    public void pause() {
        scanner.dispatchPause();
    }

    /** Dispatch PLAY only to the freshly revalidated original PAUSED session. */
    @Override
    public void play() {
        scanner.dispatchPlay();
    }
}
