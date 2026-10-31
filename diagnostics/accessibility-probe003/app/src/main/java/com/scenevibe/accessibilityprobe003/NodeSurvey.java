package com.scenevibe.accessibilityprobe003;

import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Bounded source/root traversal. Counts describe node reads, possibly duplicated across paths,
 * not a reconstructed unique tree. Every obtained node is recycled on Android 12.
 */
final class NodeSurvey {
    private final ProbeController controller;
    private final TextProjection projection;
    private int reads;
    private int visited;
    private int texts;
    private int descriptions;
    private int visible;
    private int focused;
    private int selected;
    private int checked;
    private int passwords;
    private int sourceReads;
    private int activeReads;
    private int windowReads;
    private int candidates;
    private int errors;
    private boolean truncated;

    /** Share one node budget across event source, active root and interactive window roots. */
    NodeSurvey(ProbeController controller, TextProjection projection) {
        this.controller = controller;
        this.projection = projection;
    }

    /** Return whether another root can be obtained within this exploration budget. */
    boolean remaining() { return reads < ProbeLimits.NODES && controller.active(); }

    /** Refresh an owned root, record availability, traverse target nodes, and always recycle it. */
    void root(AccessibilityNodeInfo node, SafeRow.Origin origin) {
        if (node == null) {
            controller.record(new SafeRow(SafeRow.Kind.ACCESS_FAILURE).category("origin", origin)
                .category("quality", origin == SafeRow.Origin.SOURCE_NODE
                    ? SafeRow.Quality.NULL_SOURCE : SafeRow.Quality.NULL_ROOT));
            return;
        }
        if (!remaining()) { truncated = true; node.recycle(); return; }
        reads++;
        try {
            if (!controller.profile().accepts(node.getPackageName())) {
                controller.record(new SafeRow(SafeRow.Kind.ACCESS_FAILURE).category("origin", origin)
                    .category("quality", SafeRow.Quality.FOREIGN_PACKAGE));
                node.recycle();
                return;
            }
            if (!node.refresh()) {
                controller.record(new SafeRow(SafeRow.Kind.ACCESS_FAILURE).category("origin", origin)
                    .category("quality", SafeRow.Quality.REFRESH_FAILED));
                node.recycle();
                return;
            }
        } catch (RuntimeException failure) {
            errors++; node.recycle(); return;
        }
        visit(node, 0, origin);
    }

    /** Read a target node and bounded descendants; no action, text payload or arbitrary ID is emitted. */
    private void visit(AccessibilityNodeInfo node, int depth, SafeRow.Origin origin) {
        if (!controller.active()) { node.recycle(); return; }
        try {
            if (!controller.profile().accepts(node.getPackageName())) return;
            visited++;
            if (origin == SafeRow.Origin.SOURCE_NODE) sourceReads++;
            if (origin == SafeRow.Origin.ACTIVE_ROOT) activeReads++;
            if (origin == SafeRow.Origin.WINDOW_ROOT) windowReads++;
            if (node.isVisibleToUser()) visible++;
            if (node.isFocused()) focused++;
            if (node.isSelected()) selected++;
            if (node.isChecked()) checked++;
            boolean password = node.isPassword();
            if (password) passwords++;
            else {
                CharSequence text = node.getText();
                CharSequence description = node.getContentDescription();
                if (text != null && text.length() > 0) texts++;
                if (description != null && description.length() > 0) descriptions++;
                candidate(node, projection.classify(text, false), origin, depth);
                candidate(node, projection.classify(description, false), origin, depth);
            }
            int children = node.getChildCount();
            if (depth >= ProbeLimits.DEPTH) { if (children > 0) truncated = true; return; }
            for (int index = 0; index < children; index++) {
                if (!remaining()) { truncated = true; break; }
                reads++;
                AccessibilityNodeInfo child = node.getChild(index);
                if (child == null) { errors++; }
                else visit(child, depth + 1, origin);
            }
        } catch (RuntimeException failure) { errors++; }
        finally { node.recycle(); }
    }

    /** Emit up to eight structured candidate details; labels never become ad boundaries. */
    private void candidate(AccessibilityNodeInfo node, SafeRow.Label label, SafeRow.Origin origin, int depth) {
        if (label != SafeRow.Label.AD_LABEL_CANDIDATE && label != SafeRow.Label.COUNTDOWN_CANDIDATE) return;
        candidates++;
        if (candidates > 8) { truncated = true; return; }
        controller.record(new SafeRow(SafeRow.Kind.SNAPSHOT).category("origin", origin)
            .category("labelCategory", label).category("classCategory", projection.role(node.getClassName()))
            .number("viewRef", projection.reference(node.getViewIdResourceName()))
            .number("classRef", projection.reference(node.getClassName())).number("depth", depth)
            .flag("visible", node.isVisibleToUser()).flag("selected", node.isSelected())
            .flag("focused", node.isFocused()).flag("checked", node.isChecked())
            .flag("automaticAdDecision", false));
    }

    /** Emit audit counts even for empty/partial surveys; duplicate reads and truncation are explicit. */
    void finish() {
        controller.record(new SafeRow(SafeRow.Kind.SNAPSHOT).category("origin", SafeRow.Origin.NONE)
            .number("nodeReadCount", reads).number("targetNodeCount", visited)
            .number("sourceNodeCount", sourceReads).number("activeRootNodeCount", activeReads)
            .number("windowRootNodeCount", windowReads).number("visibleCount", visible)
            .number("nonEmptyTextCount", texts).number("nonEmptyDescriptionCount", descriptions)
            .number("focusedCount", focused).number("selectedCount", selected).number("checkedCount", checked)
            .number("passwordSkippedCount", passwords).number("candidateCount", candidates)
            .number("accessFailureCount", errors).flag("countsMayDuplicate", true)
            .flag("truncated", truncated).flag("automaticAdDecision", false));
    }
}
