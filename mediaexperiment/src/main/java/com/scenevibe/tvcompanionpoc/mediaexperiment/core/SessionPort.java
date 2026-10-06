package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/** Live-session seam shared by the Android scanner and the real runtime tests. */
public interface SessionPort {
    /** Select a session from a fresh active-session query; do not issue transport. */
    PlaybackSnapshot scan();

    /** Rescan all active sessions and validate the original token and relevance. */
    SessionRevalidation revalidate(SessionTarget original);

    /** Whether the selected session advertises PAUSE or PLAY_PAUSE. */
    boolean canPause();

    /** Publish only the bounded advertised transport action flags. */
    void recordActions(Diagnostics diagnostics);
}
