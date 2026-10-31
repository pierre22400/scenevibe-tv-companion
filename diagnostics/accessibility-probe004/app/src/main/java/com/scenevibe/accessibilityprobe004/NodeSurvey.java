package com.scenevibe.accessibilityprobe004;

import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Bounded source/root traversal. Counts describe node reads, possibly duplicated across paths,
 * not a reconstructed unique tree. Every obtained node is recycled on Android 12.
 *
 * Probe004 now delegates to the fresh engine and suppresses duplicate whole-window paths.
 * countsMayDuplicate=false explicitly identifies the new counting semantics.
 */
final class NodeSurvey {
    private final SurveyEngine engine;
    /** Share one node budget across event source, active root and interactive window roots. */
    NodeSurvey(ProbeController controller, TextProjection projection) {
        this(controller, projection, controller.generation(), 0, SafeRow.Trigger.SYNTHETIC_CONTROL, new SurveyEngine.History());
    }
    /** Bind one worker traversal to a generation, snapshot and campaign-local safe history. */
    NodeSurvey(ProbeController controller, TextProjection projection, long generation, long snapshotId,
            SafeRow.Trigger trigger, SurveyEngine.History history) {
        engine = new SurveyEngine(controller, new AndroidProbeClock(), projection, generation, snapshotId, trigger, history);
    }
    /** Return whether another root can be obtained within this exploration budget. */
    boolean remaining() { return engine.remaining(); }
    /** Return whether a full-window root was already admitted. */
    boolean hasWindow(int id) { return engine.hasWindow(id); }
    /** Mark a failed outer API request so a partial reading is never declared fresh and complete. */
    void incomplete() { engine.incomplete(); }
    /** Refresh an owned root, record availability, traverse target nodes, and always recycle it. */
    void root(AccessibilityNodeInfo node, SafeRow.Origin origin) {
        engine.root(node == null ? null : new AndroidSurveyNode(node), node == null ? -1 : node.getWindowId(), origin);
    }
    /** Emit audit counts even for empty/partial surveys; duplicate reads and truncation are explicit. */
    SafeRow.Quality finish() { return engine.finish(); }
}
