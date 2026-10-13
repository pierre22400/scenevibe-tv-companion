package com.scenevibe.tvcompanionpoc;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;

import java.util.List;
import java.util.Objects;

/**
 * Read/dispatch adapter adapted from the Spike's MediaSessionScanner and SessionCatalog.
 *
 * <p>Every read and command reacquires controllers and the original token. Metadata and position
 * come from the SAME controller, with a second read rejecting races. No controller or token is
 * stored for future commands. There is no focus request, polling scheduler, Cloud or autonomous PLAY.</p>
 */
final class AndroidNativeSessionPort implements NativeSessionPort {
    private final Context context;
    private final AndroidForegroundEvidence foreground;

    /** Bind coherent native observations and explicit foreground evidence for this profile only. */
    AndroidNativeSessionPort(Context context, AndroidForegroundEvidence foreground) {
        this.context = context.getApplicationContext();
        this.foreground = foreground;
    }

    /** Select one VOD-attested Prime controller, refusing relevant competing sessions. */
    @Override public NativeSessionSnapshot select(boolean vodConfirmed) {
        Selection selection = choose(vodConfirmed);
        return selection == null ? null : selection.snapshot;
    }

    /** Reacquire the same token/program; a package-only replacement is not accepted. */
    @Override public NativeSessionSnapshot observe(NativeSessionSnapshot original) {
        Selection selected = choose(original != null && original.vodConfirmed);
        return selected != null && original.sameTarget(selected.snapshot) ? selected.snapshot : null;
    }

    /** Revalidate at actual PAUSE dispatch; no stale controller is retained from selection. */
    @Override public boolean pause(NativeSessionSnapshot expected,
            NativeQualificationPolicy policy, long nowMs) {
        Selection selected = choose(expected != null && expected.vodConfirmed);
        if (selected == null || !expected.sameTarget(selected.snapshot)
                || !policy.fresh(selected.snapshot, SystemClock.elapsedRealtime())
                || selected.snapshot.state != NativeSessionSnapshot.PLAYING
                || !selected.snapshot.canPause) return false;
        try {
            selected.controller.getTransportControls().pause();
            return true;
        } catch (RuntimeException refused) {
            return false;
        }
    }

    /** Revalidate the exact raw anchor and provider update immediately before dispatching PLAY. */
    @Override public boolean play(NativeSessionSnapshot anchor,
            NativeQualificationPolicy policy, long nowMs) {
        Selection selected = choose(anchor != null && anchor.vodConfirmed);
        if (selected == null || !anchor.sameTarget(selected.snapshot)
                || !policy.fresh(selected.snapshot, SystemClock.elapsedRealtime())
                || selected.snapshot.state != NativeSessionSnapshot.PAUSED
                || selected.snapshot.speed != 0f || !selected.snapshot.canPlay
                || selected.snapshot.updatedAtMs != anchor.updatedAtMs
                || Math.abs(selected.snapshot.positionMs - anchor.positionMs) > policy.positionToleranceMs) {
            return false;
        }
        try {
            selected.controller.getTransportControls().play();
            return true;
        } catch (RuntimeException refused) {
            return false;
        }
    }

    /** Query live controllers; explicit foreground proof also rejects HOME/app departure. */
    private Selection choose(boolean vodConfirmed) {
        if (!vodConfirmed || !foreground.primeForeground() || !NotificationAccess.isGranted(context)) {
            return null;
        }
        try {
            MediaSessionManager manager = context.getSystemService(MediaSessionManager.class);
            if (manager == null) return null;
            List<MediaController> controllers = manager.getActiveSessions(
                    new ComponentName(context, MediaSessionAccessService.class));
            if (controllers == null) return null;
            Selection prime = null;
            for (MediaController controller : controllers) {
                String pkg = controller.getPackageName();
                if (pkg.equals(context.getPackageName()) || pkg.startsWith(
                        "com.scenevibe.tvcompanionpoc.mediaexperiment")) continue;
                NativeSessionSnapshot sample = snapshot(controller, vodConfirmed);
                if (sample == null) return null;
                if (NativeQualificationPolicy.PRIME_PACKAGE.equals(pkg)) {
                    if (prime != null) return null;
                    prime = new Selection(controller, sample);
                } else if (relevant(sample.state)) {
                    return null;
                }
            }
            return prime;
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    /** Capture metadata and playback twice on this controller; mismatched reads fail closed. */
    private static NativeSessionSnapshot snapshot(MediaController controller, boolean vodConfirmed) {
        MediaMetadata firstMetadata = controller.getMetadata();
        PlaybackState first = controller.getPlaybackState();
        MediaMetadata metadata = controller.getMetadata();
        PlaybackState state = controller.getPlaybackState();
        if (firstMetadata == null || metadata == null || first == null || state == null
                || !sameMetadata(firstMetadata, metadata) || first.getState() != state.getState()
                || first.getPosition() != state.getPosition()
                || first.getLastPositionUpdateTime() != state.getLastPositionUpdateTime()
                || first.getPlaybackSpeed() != state.getPlaybackSpeed()
                || first.getActions() != state.getActions()) return null;
        long actions = state.getActions();
        return new NativeSessionSnapshot(controller.getSessionToken(), controller.getPackageName(),
                metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
                metadata.getLong(MediaMetadata.METADATA_KEY_DURATION), state.getPosition(),
                SystemClock.elapsedRealtime(), state.getLastPositionUpdateTime(),
                state.getPlaybackSpeed(), state.getState(),
                (actions & (PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE)) != 0,
                (actions & (PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_PAUSE)) != 0,
                vodConfirmed);
    }

    /** Reject metadata changes during a coherent observation without logging the content. */
    private static boolean sameMetadata(MediaMetadata first, MediaMetadata second) {
        return Objects.equals(first.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                second.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))
                && Objects.equals(first.getString(MediaMetadata.METADATA_KEY_TITLE),
                second.getString(MediaMetadata.METADATA_KEY_TITLE))
                && Objects.equals(first.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
                second.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))
                && first.getLong(MediaMetadata.METADATA_KEY_DURATION)
                == second.getLong(MediaMetadata.METADATA_KEY_DURATION);
    }

    /** Treat another app playing/preparing/seeking as an ambiguous native ownership signal. */
    private static boolean relevant(int state) {
        return state != PlaybackState.STATE_NONE && state != PlaybackState.STATE_STOPPED
                && state != PlaybackState.STATE_PAUSED && state != PlaybackState.STATE_ERROR;
    }

    /** A controller and its coherent snapshot are local to this one query/dispatch only. */
    private static final class Selection {
        final MediaController controller;
        final NativeSessionSnapshot snapshot;

        /** Keep matched values together until this invocation finishes. */
        Selection(MediaController controller, NativeSessionSnapshot snapshot) {
            this.controller = controller;
            this.snapshot = snapshot;
        }
    }
}
