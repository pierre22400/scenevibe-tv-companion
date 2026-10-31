package com.scenevibe.accessibilityprobe004;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared bounded traversal for real public Android nodes and synthetic software cases. Every
 * interpreted node is refreshed. A window is traversed once; differences use only safe shape
 * tokens and are never converted into ad boundaries. Campaign generation gates every write.
 */
final class SurveyEngine {
    private final ProbeController core;
    private final ProbeClock clock;
    private final TextProjection projection;
    private final long generation, snapshotId, started;
    private final SafeRow.Trigger trigger;
    private final History history;
    private final Set<Integer> windows = new HashSet<>();
    private final List<String> shape = new ArrayList<>();
    private int attempts, successful, ignored, roots, failures, texts, descriptions, visible, focus, candidates;
    private int sourceNodes, activeNodes, windowNodes;
    private boolean truncated, delayed, incomplete;

    /** Capture immutable campaign scope and one time budget; history belongs to this generation. */
    SurveyEngine(ProbeController core, ProbeClock clock, TextProjection projection, long generation,
            long snapshotId, SafeRow.Trigger trigger, History history) {
        this.core = core; this.clock = clock; this.projection = projection; this.generation = generation;
        this.snapshotId = snapshotId; this.trigger = trigger; this.history = history; started = clock.elapsed();
    }

    /** Return whether another public node read may start, enforcing generation, time and node caps. */
    boolean remaining() {
        if (!core.generationActive(generation)) return false;
        if (clock.elapsed() - started > ProbeLimits.SOFT_SURVEY_MS) { delayed = truncated = true; return false; }
        if (attempts >= ProbeLimits.NODES) { truncated = true; return false; }
        return true;
    }

    /** Report whether a complete-window root was admitted, to avoid reacquiring its duplicate. */
    boolean hasWindow(int id) { return windows.contains(id); }

    /** Mark an outer API failure or source-only fallback as an incomplete observation. */
    void incomplete() { incomplete = true; }

    /** Admit a root handle or record explicit absence; successful windows are never traversed twice. */
    void root(SurveyNode node, int window, SafeRow.Origin origin) {
        if (node == null) { incomplete = true; issue(SafeRow.Quality.ROOT_UNAVAILABLE, origin); return; }
        if (windows.contains(window)) { ignored++; node.close(); return; }
        if (windows.size() >= ProbeLimits.WINDOWS) { truncated = true; ignored++; node.close(); return; }
        if (!remaining()) { ignored++; node.close(); return; }
        attempts++;
        if (visit(node, window, 0, 0, origin)) { roots++; windows.add(window); }
    }

    /** Refresh before properties/children, recheck package, traverse bounded descendants and release. */
    private boolean visit(SurveyNode node, int window, int parent, int depth, SafeRow.Origin origin) {
        try {
            if (!core.generationActive(generation)) return false;
            if (!core.profile().accepts(node.packageName())) {
                incomplete = true; issue(SafeRow.Quality.FOREIGN_PACKAGE, origin); return false;
            }
            if (!node.refresh()) { failures++; incomplete = true; issue(SafeRow.Quality.REFRESH_FAILED, origin); return false; }
            if (!core.generationActive(generation)) return false;
            if (!core.profile().accepts(node.packageName())) {
                incomplete = true; issue(SafeRow.Quality.FOREIGN_PACKAGE, origin); return false;
            }
            NodeFacts facts = node.facts(core.profile(), projection);
            int index = ++successful;
            shape.add(window + ":" + facts.shape(parent, depth));
            if (facts.text) texts++; if (facts.description) descriptions++;
            if (facts.visible) visible++; if (facts.focused) focus++;
            if (facts.textLabel == SafeRow.Label.AD_LABEL_CANDIDATE
                || facts.descriptionLabel == SafeRow.Label.AD_LABEL_CANDIDATE) candidates++;
            if (origin == SafeRow.Origin.SOURCE_NODE) sourceNodes++;
            if (origin == SafeRow.Origin.ACTIVE_ROOT) activeNodes++;
            if (origin == SafeRow.Origin.WINDOW_ROOT) windowNodes++;
            core.recordFor(generation, facts.append(row(SafeRow.Kind.NODE).number("nodeIndex", index)
                .number("parentIndex", parent).number("depth", depth).number("windowId", window)
                .category("origin", origin).category("quality", SafeRow.Quality.FRESH_OK)));
            if (depth >= ProbeLimits.DEPTH) { if (facts.children > 0) truncated = true; return true; }
            for (int childIndex = 0; childIndex < facts.children; childIndex++) {
                if (!remaining()) { ignored += facts.children - childIndex; break; }
                attempts++;
                SurveyNode child = node.child(childIndex);
                if (child == null) { failures++; incomplete = true; issue(SafeRow.Quality.STALE_NODE, origin); }
                else visit(child, window, index, depth + 1, origin);
            }
            return true;
        } catch (RuntimeException failure) {
            failures++; incomplete = true; issue(SafeRow.Quality.INCOMPLETE, origin); return false;
        } finally { node.close(); }
    }

    /** Record a closed availability category without an exception message or target payload. */
    private void issue(SafeRow.Quality quality, SafeRow.Origin origin) {
        core.recordFor(generation, row(SafeRow.Kind.ACCESS_FAILURE).category("quality", quality).category("origin", origin));
    }

    /** Add immutable snapshot/trigger context to every row emitted by this traversal. */
    private SafeRow row(SafeRow.Kind kind) {
        return new SafeRow(kind).number("snapshotId", snapshotId).category("trigger", trigger).flag("automaticAdDecision", false);
    }

    /** Finish with an auditable result even when empty; partial readings never update shape history. */
    SafeRow.Quality finish() {
        long duration = Math.max(0, clock.elapsed() - started);
        if (duration > ProbeLimits.SOFT_SURVEY_MS) delayed = truncated = true;
        SafeRow.Quality quality = delayed ? SafeRow.Quality.TIMEOUT_OR_DELAY : truncated ? SafeRow.Quality.TRUNCATED
            : roots == 0 ? SafeRow.Quality.ROOT_UNAVAILABLE : incomplete ? SafeRow.Quality.INCOMPLETE : SafeRow.Quality.FRESH_OK;
        boolean comparable = quality == SafeRow.Quality.FRESH_OK && history.windows.equals(windows) && history.previous != null;
        boolean changed = comparable && !history.previous.equals(shape);
        core.recordFor(generation, row(SafeRow.Kind.SNAPSHOT).category("origin", SafeRow.Origin.NONE)
            .category("quality", quality).number("nodeReadCount", attempts).number("targetNodeCount", successful)
            .number("validRootCount", roots).number("ignoredReadCount", ignored).number("refreshFailureCount", failures)
            .number("sourceNodeCount", sourceNodes).number("activeRootNodeCount", activeNodes).number("windowRootNodeCount", windowNodes)
            .number("visibleCount", visible).number("focusedCount", focus).number("nonEmptyTextCount", texts)
            .number("nonEmptyDescriptionCount", descriptions).number("candidateCount", candidates)
            .number("accessFailureCount", failures).flag("countsMayDuplicate", false).flag("truncated", truncated)
            .flag("comparisonAvailable", comparable).flag("shapeChanged", changed).number("durationMs", duration));
        if (quality == SafeRow.Quality.FRESH_OK && core.generationActive(generation)) {
            history.previous = new ArrayList<>(shape); history.windows = new HashSet<>(windows);
        } else history.previous = null;
        return quality;
    }

    /** Campaign-local safe structural memory, discarded at every arm or reconnection. */
    static final class History {
        private List<String> previous;
        private Set<Integer> windows = new HashSet<>();
        /** Create empty history with no cross-campaign identity or content hash. */
        History() { }
    }
}
