package com.scenevibe.accessibilityprobe004;

/**
 * Owned read-only node handle. Android and synthetic adapters share the same traversal and
 * freshness policy; no action, focus, transport or arbitrary export method exists here.
 */
interface SurveyNode {
    /** Return the transient package solely for admission; it must never be exported freely. */
    CharSequence packageName();
    /** Request fresh public properties for this node; false means unusable, never ad absence. */
    boolean refresh();
    /** Project only refreshed properties into closed categories and numeric flags. */
    NodeFacts facts(ProbeProfile profile, TextProjection projection);
    /** Retrieve one child handle; a null result is an explicit incomplete traversal. */
    SurveyNode child(int index);
    /** Release this handle once; callers must not inspect it after closing. */
    void close();
}
