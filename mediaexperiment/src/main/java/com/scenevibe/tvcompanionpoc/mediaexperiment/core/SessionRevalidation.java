package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/** Result of a fresh active-session query, distinct from an old controller sample. */
public final class SessionRevalidation {
    public final PlaybackSnapshot latest;
    public final boolean originalSessionPresent;
    public final boolean relevantPackageChanged;
    public final DeniedReason reason;

    /** Keep evidence coarse; the opaque identity lives only inside the snapshot. */
    public SessionRevalidation(PlaybackSnapshot latest, boolean originalSessionPresent,
            boolean relevantPackageChanged, DeniedReason reason) {
        this.latest = latest;
        this.originalSessionPresent = originalSessionPresent;
        this.relevantPackageChanged = relevantPackageChanged;
        this.reason = reason;
    }

    /** A missing or ambiguous original session is never a successful revalidation. */
    public boolean valid() {
        return reason == DeniedReason.NONE && originalSessionPresent && latest != null
                && !relevantPackageChanged;
    }
}
