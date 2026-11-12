package com.amazon.amazonvideo.livingroom;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.TextView;

/** Publishes test MEDIA facts only on a disposable QEMU device, with no stream or network access. */
public final class OwnedPlayer extends Activity {
    private MediaSession session;

    /** Show a focusable local surface and publish the reference work's metadata. */
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (!android.os.Build.FINGERPRINT.contains("generic")
                && !android.os.Build.FINGERPRINT.contains("sdk")) {
            finish();
            return;
        }
        TextView label = new TextView(this);
        label.setText("SceneVibe Android TV qualification");
        label.setFocusableInTouchMode(true);
        setContentView(label);
        label.requestFocus();
        session = new MediaSession(this, "SceneVibe owned emulator MEDIA");
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "amzn1.dv.gti.baecadcc-c3ef-42a3-bce0-c241adaa992e")
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Columbo — Eaux troubles")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, 5884768L).build());
        update(getIntent());
        session.setActive(true);
    }

    /** Receive the emulator's next owned playback position without transport commands. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        update(intent);
    }

    /** Record the window manager’s real focus callbacks for this owned fixture. */
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        android.util.Log.i("SceneVibeOwnedFocus", focused ? "WINDOW_FOCUSED" : "WINDOW_UNFOCUSED");
    }

    /** Release only the MEDIA session created by this fixture. */
    @Override protected void onDestroy() {
        if (session != null) session.release();
        super.onDestroy();
    }

    /** Publish real Android MediaSession state sampled by the unchanged Companion. */
    private void update(Intent intent) {
        if (session == null) return;
        boolean playing = intent.getBooleanExtra("playing", false);
        long position = intent.getLongExtra("position", 0);
        session.setPlaybackState(new PlaybackState.Builder()
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        position, playing ? 1f : 0f, SystemClock.elapsedRealtime()).build());
    }
}
