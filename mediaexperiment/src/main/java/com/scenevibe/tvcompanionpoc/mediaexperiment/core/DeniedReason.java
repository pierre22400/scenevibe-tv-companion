package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/** Bounded mechanism-only reasons. No token, metadata, content or credentials. */
public enum DeniedReason {
    NONE,
    NO_SESSION,
    SESSION_QUERY_FAILED,
    AMBIGUOUS_SESSION,
    INITIAL_STATE_NOT_PLAYING,
    PAUSE_UNSUPPORTED,
    FOCUS_DENIED,
    ORIGINAL_SESSION_MISSING,
    SESSION_TOKEN_CHANGED,
    PACKAGE_CHANGED,
    OTHER_MEDIA_APP_RELEVANT,
    MEDIA_CHANGED,
    LIVE_STATE_NOT_PAUSED,
    PAUSE_NOT_OWNED,
    INTERLUDE_NOT_COMPLETED,
    PAUSE_TIMEOUT,
    PORT_ERROR,
    VIDEO_ERROR,
    STOP_REQUESTED,
    UNEXPECTED_EVENT
}
