package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Android-free live-session selection and revalidation used by the real scanner.
 * Every check queries the source anew. A package/title match never substitutes for
 * a session token. Competing playback or a changed priority owner fails closed.
 */
public final class SessionCatalog implements SessionPort, MediaControlPort {
    private final ActiveSessionSource source;
    private SessionController current;
    private SessionTarget selected;

    /** Inject the live controller source without coupling policy to Android. */
    public SessionCatalog(ActiveSessionSource source) {
        this.source = source;
    }

    /** Scan only; multiple playing sessions are too ambiguous to target safely. */
    @Override
    public PlaybackSnapshot scan() {
        current = null;
        selected = null;
        try {
            List<SessionController> active = source.activeSessions();
            if (active == null || active.isEmpty()) return null;
            SessionController chosen = active.get(0);
            int playing = 0;
            for (SessionController controller : active) {
                PlaybackSnapshot snapshot = controller.snapshot();
                if (snapshot == null || snapshot.sessionIdentity == null) return null;
                if (snapshot.state == PlaybackStateCodes.STATE_PLAYING) {
                    chosen = controller;
                    playing++;
                }
            }
            if (playing > 1) return null;
            PlaybackSnapshot snapshot = chosen.snapshot();
            if (snapshot == null || snapshot.sessionIdentity == null) return null;
            current = chosen;
            selected = SessionTarget.from(snapshot);
            return snapshot;
        } catch (RuntimeException failure) {
            return null;
        }
    }

    /** Re-query active tokens, retain the original only, and detect competing owners. */
    @Override
    public SessionRevalidation revalidate(SessionTarget original) {
        current = null;
        if (original == null || original.sessionIdentity == null) {
            return denied(null, false, false, DeniedReason.ORIGINAL_SESSION_MISSING);
        }
        try {
            List<SessionController> active = source.activeSessions();
            if (active == null) {
                return denied(null, false, false, DeniedReason.SESSION_QUERY_FAILED);
            }
            List<PlaybackSnapshot> snapshots = new ArrayList<>();
            SessionController matched = null;
            PlaybackSnapshot latest = null;
            boolean samePackageReplacement = false;
            for (SessionController controller : active) {
                PlaybackSnapshot snapshot = controller.snapshot();
                if (snapshot == null || snapshot.sessionIdentity == null) {
                    return denied(null, false, false, DeniedReason.AMBIGUOUS_SESSION);
                }
                snapshots.add(snapshot);
                if (Objects.equals(original.sessionIdentity, snapshot.sessionIdentity)) {
                    if (matched != null) {
                        return denied(snapshot, true, false, DeniedReason.AMBIGUOUS_SESSION);
                    }
                    matched = controller;
                    latest = snapshot;
                } else if (Objects.equals(original.packageName, snapshot.packageName)) {
                    samePackageReplacement = true;
                }
            }
            if (matched == null) {
                return denied(null, false, false, samePackageReplacement
                        ? DeniedReason.SESSION_TOKEN_CHANGED : DeniedReason.ORIGINAL_SESSION_MISSING);
            }
            if (original.packageName == null
                    || !original.packageName.equals(latest.packageName)) {
                return denied(latest, true, true, DeniedReason.PACKAGE_CHANGED);
            }
            for (int index = 0; index < snapshots.size(); index++) {
                PlaybackSnapshot snapshot = snapshots.get(index);
                if (!Objects.equals(original.sessionIdentity, snapshot.sessionIdentity)
                        && (index == 0 || activelyPlayingOrPreparing(snapshot.state))) {
                    return denied(latest, true,
                            !Objects.equals(original.packageName, snapshot.packageName),
                            DeniedReason.OTHER_MEDIA_APP_RELEVANT);
                }
            }
            if (!PocMediaIdentity.sameMedia(original, latest)) {
                return denied(latest, true, false, DeniedReason.MEDIA_CHANGED);
            }
            current = matched;
            selected = original;
            return new SessionRevalidation(latest, true, false, DeniedReason.NONE);
        } catch (RuntimeException failure) {
            return denied(null, false, false, DeniedReason.SESSION_QUERY_FAILED);
        }
    }

    /** Recheck PLAYING and advertised PAUSE immediately at transport dispatch. */
    @Override
    public void pause() {
        SessionRevalidation live = revalidate(selected);
        if (!live.valid() || live.latest.state != PlaybackStateCodes.STATE_PLAYING
                || !canPause()) {
            throw new IllegalStateException("Pause target no longer safely playing");
        }
        current.pause();
    }

    /** Recheck the original token and live PAUSED state again at PLAY dispatch. */
    @Override
    public void play() {
        SessionRevalidation live = revalidate(selected);
        if (!live.valid() || live.latest.state != PlaybackStateCodes.STATE_PAUSED) {
            throw new IllegalStateException("Resume target no longer safely paused");
        }
        current.play();
    }

    /** Query supported pause actions on the currently validated controller. */
    @Override
    public boolean canPause() {
        try {
            return current != null && (current.canPause() || current.canPlayPause());
        } catch (RuntimeException failure) {
            return false;
        }
    }

    /** Expose flags without exposing token or media metadata. */
    @Override
    public void recordActions(Diagnostics diagnostics) {
        try {
            diagnostics.actionPlayAvailable = current != null && current.canPlay();
            diagnostics.actionPauseAvailable = current != null && current.canPause();
            diagnostics.actionPlayPauseAvailable = current != null && current.canPlayPause();
        } catch (RuntimeException failure) {
            diagnostics.actionPlayAvailable = false;
            diagnostics.actionPauseAvailable = false;
            diagnostics.actionPlayPauseAvailable = false;
        }
    }

    /** Treat playback preparation and seeking as evidence of a competing player. */
    private static boolean activelyPlayingOrPreparing(int state) {
        return state != PlaybackStateCodes.STATE_NONE && state != PlaybackStateCodes.STATE_STOPPED
                && state != PlaybackStateCodes.STATE_PAUSED && state != PlaybackStateCodes.STATE_ERROR;
    }

    /** Construct a bounded failure without retaining any stale controller. */
    private static SessionRevalidation denied(PlaybackSnapshot latest, boolean present,
            boolean changed, DeniedReason reason) {
        return new SessionRevalidation(latest, present, changed, reason);
    }
}
