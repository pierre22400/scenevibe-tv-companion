package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

import java.util.List;

/** Supply a fresh priority-ordered active-controller list on every call. */
public interface ActiveSessionSource {
    /** Query active sessions; failure must throw instead of returning stale data. */
    List<SessionController> activeSessions();
}
