package com.scenevibe.tvcompanionpoc.tvfirst;

/**
 * One bounded observation owned by the current service lifetime. A closed or replaced
 * session cannot publish a late callback; no observation survives as persisted admission.
 * This memory-only read model never consults credentials or an installation repository.
 */
public final class ObservationDiagnostics {
    private static Session current;

    /** Start a new service lifetime and retire the previous diagnostic writer, if any. */
    public static synchronized Session begin() {
        if (current != null) current.closed = true;
        current = new Session();
        return current;
    }

    /** Return only the live service's immutable batch, without creating an identity or worker. */
    public static synchronized MediaEvidence latest() { return current == null ? null : current.latest; }

    /** Captured writer identity; callbacks never resolve a newer writer to retag old data. */
    public static final class Session {
        private MediaEvidence latest;
        private boolean closed;

        /** Only begin creates a new writer lifetime. */
        private Session() { }

        /** Ignore stale, duplicate, stopped or superseded callbacks before touching the read model. */
        public void record(MediaEvidence evidence) {
            synchronized (ObservationDiagnostics.class) {
                if (closed || current != this || evidence == null
                        || latest != null && (evidence.sequence <= latest.sequence
                        || evidence.capturedAtMs < latest.capturedAtMs)) return;
                latest = evidence;
            }
        }

        /** Retire this exact writer; an old destruction cannot clear a newer service's batch. */
        public void close() {
            synchronized (ObservationDiagnostics.class) {
                closed = true; latest = null;
                if (current == this) current = null;
            }
        }
    }

    /** Static registry has no public mutable construction. */
    private ObservationDiagnostics() { }
}
