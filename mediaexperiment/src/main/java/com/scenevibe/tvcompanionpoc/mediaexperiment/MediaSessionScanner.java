package com.scenevibe.tvcompanionpoc.mediaexperiment;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;

import com.scenevibe.tvcompanionpoc.mediaexperiment.core.Diagnostics;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.PlaybackSnapshot;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SessionCatalog;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SessionController;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SessionPort;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SessionRevalidation;
import com.scenevibe.tvcompanionpoc.mediaexperiment.core.SessionTarget;

import java.util.ArrayList;
import java.util.List;

/**
 * Isolated MediaSession discovery for the POC, mirroring the IDEA of :app's
 * MediaSessionProbe without importing it. Active sessions are queried afresh for
 * every confirmation and resume, through the JVM-tested SessionCatalog policy.
 *
 * <p>MediaSessionManager.getActiveSessions uses this module's own notification
 * listener component. MediaController.getSessionToken supplies an opaque identity
 * whose equality is independent of package and metadata. Tokens and content are
 * never logged. Transport controls are the only control mechanism; there is no
 * accessibility, input injection, coordinate injection or view-hierarchy reading.</p>
 */
final class MediaSessionScanner implements SessionPort {
    private final Context context;
    private final MediaSessionManager sessions;
    private final ComponentName listenerComponent;
    private final SessionCatalog catalog;

    /** Bind discovery to the experimental app's own notification listener. */
    MediaSessionScanner(Context context) {
        this.context = context.getApplicationContext();
        this.sessions = context.getSystemService(MediaSessionManager.class);
        this.listenerComponent = ExperimentNotificationAccess.component(context);
        this.catalog = new SessionCatalog(this::activeControllers);
    }

    /** True when this module's notification-listener access has been granted. */
    boolean accessGranted() {
        return ExperimentNotificationAccess.isGranted(context);
    }

    /** Discover the relevant controller without issuing any transport command. */
    @Override
    public PlaybackSnapshot scan() {
        return catalog.scan();
    }

    /** Rescan live sessions; never sample a cached, potentially dead controller. */
    @Override
    public SessionRevalidation revalidate(SessionTarget original) {
        return catalog.revalidate(original);
    }

    /** Report advertised PAUSE support on the validated controller. */
    @Override
    public boolean canPause() {
        return catalog.canPause();
    }

    /** Record only bounded action flags, never metadata or session token contents. */
    @Override
    public void recordActions(Diagnostics diagnostics) {
        catalog.recordActions(diagnostics);
    }

    /** Dispatch only after the catalog rechecks the original live PLAYING session. */
    void dispatchPause() {
        catalog.pause();
    }

    /** Dispatch only after the catalog rechecks the original live PAUSED session. */
    void dispatchPlay() {
        catalog.play();
    }

    /** Query the platform every time; missing access is uncertainty, not stale data. */
    private List<SessionController> activeControllers() {
        if (!accessGranted() || sessions == null) {
            throw new IllegalStateException("Active-session access unavailable");
        }
        List<MediaController> active = sessions.getActiveSessions(listenerComponent);
        if (active == null) throw new IllegalStateException("Active-session query unavailable");
        List<SessionController> adapters = new ArrayList<>();
        for (MediaController controller : active) {
            adapters.add(new AndroidController(controller));
        }
        return adapters;
    }

    /** Convert one live controller observation without conflating token and media. */
    private static PlaybackSnapshot toSnapshot(MediaController controller) {
        PlaybackState state = controller.getPlaybackState();
        MediaMetadata metadata = controller.getMetadata();
        int stateCode = state == null ? PlaybackState.STATE_NONE : state.getState();
        String mediaId = metadataText(metadata, MediaMetadata.METADATA_KEY_MEDIA_ID);
        String title = metadataText(metadata, MediaMetadata.METADATA_KEY_TITLE);
        if (title.isEmpty()) title = metadataText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        String subtitle = metadataText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        long durationMs = metadata == null ? -1L : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        return new PlaybackSnapshot(controller.getPackageName(), stateCode, mediaId,
                title, subtitle, durationMs, controller.getSessionToken());
    }

    /** Read metadata internally for comparison; never publish or log its text. */
    private static String metadataText(MediaMetadata metadata, String key) {
        if (metadata == null) return "";
        CharSequence value = metadata.getText(key);
        return value == null ? "" : value.toString();
    }

    /** Thin Android adapter; all selection and revalidation policy stays in the core. */
    private static final class AndroidController implements SessionController {
        private final MediaController controller;

        /** Wrap a controller from the latest platform active-session query. */
        AndroidController(MediaController controller) {
            this.controller = controller;
        }

        /** Read fresh state and preserve MediaSession.Token as an opaque object. */
        @Override
        public PlaybackSnapshot snapshot() {
            return toSnapshot(controller);
        }

        /** Report the live ACTION_PLAY flag. */
        @Override
        public boolean canPlay() {
            return advertises(PlaybackState.ACTION_PLAY);
        }

        /** Report the live ACTION_PAUSE flag. */
        @Override
        public boolean canPause() {
            return advertises(PlaybackState.ACTION_PAUSE);
        }

        /** Report the live ACTION_PLAY_PAUSE flag. */
        @Override
        public boolean canPlayPause() {
            return advertises(PlaybackState.ACTION_PLAY_PAUSE);
        }

        /** Dispatch PAUSE exclusively through this MediaSession's transport controls. */
        @Override
        public void pause() {
            controller.getTransportControls().pause();
        }

        /** Dispatch PLAY exclusively through this MediaSession's transport controls. */
        @Override
        public void play() {
            controller.getTransportControls().play();
        }

        /** Null playback state never advertises a supported action. */
        private boolean advertises(long action) {
            PlaybackState state = controller.getPlaybackState();
            return state != null && (state.getActions() & action) != 0L;
        }
    }
}
