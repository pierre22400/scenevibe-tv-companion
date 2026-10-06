package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Android-free, fail-closed media-identity comparison for the POC.
 *
 * <p>This ports the IDEA of the production {@code MediaIdentityMatcher}
 * (compare mediaId, then title/subtitle, then duration tolerance) into a small
 * self-contained helper inside the experimental module. It intentionally does
 * NOT import the production class and makes no SceneVibe/Cloud assumptions.</p>
 *
 * <p>"Fail-closed" means: when there is not enough evidence that the originally
 * targeted media is still what is playing, {@link #sameMedia} returns false so the
 * safe-resume guard will refuse to issue PLAY.</p>
 */
public final class PocMediaIdentity {
    private static final long DURATION_TOLERANCE_MS = 180_000L;

    /**
     * @return true only when the observed snapshot still plausibly identifies the
     *     same media as the original target. Any clear mismatch returns false.
     */
    public static boolean sameMedia(SessionTarget target, PlaybackSnapshot observed) {
        if (target == null || observed == null) return false;

        String targetId = clean(target.mediaId);
        String observedId = clean(observed.mediaId);
        if (!targetId.isEmpty() && !observedId.isEmpty()) {
            // Both sides expose a stable media id: it is authoritative.
            return targetId.equals(observedId);
        }

        if (!titleMatches(target.title, observed.title, observed.subtitle)) {
            return false;
        }

        if (observed.durationMs > 0L && target.durationMs > 0L
                && Math.abs(observed.durationMs - target.durationMs) > DURATION_TOLERANCE_MS) {
            return false;
        }
        return true;
    }

    /** Never accepts a lone generic series title when the canonical title is more specific. */
    public static boolean titleMatches(String expectedTitle, String title, String subtitle) {
        String expected = normalize(expectedTitle);
        String observedTitle = normalize(title);
        String observedSubtitle = normalize(subtitle);
        if (expected.isEmpty()) return false;

        String combined = join(observedTitle, observedSubtitle);
        if (expected.equals(observedTitle) || expected.equals(observedSubtitle)
                || expected.equals(combined)) return true;
        if (!combined.isEmpty() && combined.contains(expected)) return true;

        if (!observedSubtitle.isEmpty() && expected.contains(observedSubtitle)
                && (observedTitle.isEmpty() || expected.contains(observedTitle))) {
            return true;
        }

        if (observedSubtitle.isEmpty() && tokenCount(observedTitle) >= 2
                && observedTitle.length() >= 8
                && (expected.endsWith(" " + observedTitle)
                    || expected.startsWith(observedTitle + " "))) {
            return true;
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return decomposed.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String join(String first, String second) {
        if (first.isEmpty()) return second;
        if (second.isEmpty()) return first;
        return first + " " + second;
    }

    private static int tokenCount(String value) {
        if (value == null || value.isEmpty()) return 0;
        return value.split(" ").length;
    }

    private PocMediaIdentity() {}
}
