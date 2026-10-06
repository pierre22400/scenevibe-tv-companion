package com.scenevibe.tvcompanionpoc;

import java.text.Normalizer;
import java.util.Locale;

/** Fail-closed matcher between one projected FinalTrack and passive MediaSession metadata. */
final class MediaIdentityMatcher {
    private static final long DURATION_TOLERANCE_MS = 180_000L;

    static boolean matches(ScheduledTrack track, MediaSessionProbe.Snapshot snapshot) {
        if (track == null || snapshot == null) return false;
        if (!track.targetPackage.equals(snapshot.packageName)) return false;
        if (!"prime_video".equals(track.mediaIdentity.platform)) return false;

        String observedId = clean(snapshot.mediaId);
        if (!observedId.isEmpty() && observedId.equals(clean(track.mediaIdentity.videoId))) {
            return true;
        }

        if (!titleMatches(track.mediaIdentity.title, snapshot.title, snapshot.subtitle)) {
            return false;
        }

        if (snapshot.durationMs > 0L && track.mediaIdentity.durationMs > 0L
                && Math.abs(snapshot.durationMs - track.mediaIdentity.durationMs)
                        > DURATION_TOLERANCE_MS) {
            return false;
        }
        return true;
    }

    /** Never accepts a generic series title alone when the canonical title is more specific. */
    static boolean titleMatches(String expectedTitle, String title, String subtitle) {
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

        // Some TV apps expose only the episode title. Permit a meaningful multi-token
        // suffix/prefix, but never a lone generic series name such as "Columbo".
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

    private MediaIdentityMatcher() {}
}
