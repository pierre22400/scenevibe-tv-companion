package com.scenevibe.tvcompanionpoc.presentation;

import java.util.List;

/** Pure spatial score from v0; only the current Activity's views participate. */
public final class TvFocusSearch {
    public enum Direction { LEFT, RIGHT, UP, DOWN }

    /** A focus target's stable key and layout rectangle in one coordinate space. */
    public static final class Target {
        public final String key;
        public final float left, top, right, bottom;
        public final boolean enabled;

        /** Retains geometry without depending on Android mocked geometry in JVM tests. */
        public Target(String key, float left, float top, float right, float bottom, boolean enabled) {
            this.key = key;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.enabled = enabled;
        }
    }

    /** Finds the closest enabled target; left exits to the active menu unless a row neighbor exists. */
    public static String next(Target from, List<Target> candidates, Direction direction, String activeMenu) {
        Target best = null;
        float bestScore = Float.POSITIVE_INFINITY;
        float cx = (from.left + from.right) / 2, cy = (from.top + from.bottom) / 2;
        for (Target to : candidates) {
            if (!to.enabled || to.key.equals(from.key)) continue;
            float rx = (to.left + to.right) / 2, ry = (to.top + to.bottom) / 2;
            float primary, gap, offset;
            switch (direction) {
                case LEFT:
                    if (!(rx < cx - 1 && to.right <= from.right - 1)) continue;
                    primary = Math.max(0, from.left - to.right);
                    gap = gap(from.top, from.bottom, to.top, to.bottom);
                    offset = Math.abs(ry - cy);
                    break;
                case RIGHT:
                    if (!(rx > cx + 1 && to.left >= from.left + 1)) continue;
                    primary = Math.max(0, to.left - from.right);
                    gap = gap(from.top, from.bottom, to.top, to.bottom);
                    offset = Math.abs(ry - cy);
                    break;
                case UP:
                    if (!(ry < cy - 1 && to.bottom <= from.bottom - 1)) continue;
                    primary = Math.max(0, from.top - to.bottom);
                    gap = gap(from.left, from.right, to.left, to.right);
                    offset = Math.abs(rx - cx);
                    break;
                default:
                    if (!(ry > cy + 1 && to.top >= from.top + 1)) continue;
                    primary = Math.max(0, to.top - from.bottom);
                    gap = gap(from.left, from.right, to.left, to.right);
                    offset = Math.abs(rx - cx);
            }
            float score = primary + 3 * gap + .4f * offset;
            if (score < bestScore) { bestScore = score; best = to; }
        }
        if (direction == Direction.LEFT && !from.key.startsWith("nav-")) {
            boolean sameRow = best != null && !best.key.startsWith("nav-")
                    && best.top < from.bottom && from.top < best.bottom;
            if (!sameRow) for (Target candidate : candidates) {
                if (candidate.enabled && candidate.key.equals(activeMenu)) return activeMenu;
            }
        }
        return best == null ? null : best.key;
    }

    /** Measures the empty interval between two projections, with zero for overlap. */
    private static float gap(float a1, float a2, float b1, float b2) {
        return Math.max(0, Math.max(b1 - a2, a1 - b2));
    }
}
