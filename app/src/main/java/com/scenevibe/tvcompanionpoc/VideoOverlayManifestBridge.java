package com.scenevibe.tvcompanionpoc;

import java.util.HashMap;
import java.util.Map;

/**
 * Pure, Android-independent cross-contract validator binding a runtime {@link ScheduledTrack}
 * to its {@link OverlayManifest} for the Video product (user section 5). It is deliberately
 * kept OUT of {@link CloudControlClient}: the client only moves bytes; this helper decides
 * whether a manifested Video revision is coherent enough to install.
 *
 * <p>It enforces EXACTLY, with no tolerance:
 * <ul>
 *   <li>{@code manifest.product == "video"}</li>
 *   <li>{@code manifest.sourceId.equals(runtimeTrack.trackId)}</li>
 *   <li>{@code manifest.clockMode == "media"}</li>
 *   <li>a bijection between runtimeTrack comment ids and manifest scene ids (every comment
 *       maps to exactly one scene, no extra Video scenes, no duplicates)</li>
 *   <li>for every matched pair, {@code scene.startMs == comment.startMs} AND
 *       {@code scene.durationMs == comment.durationMs}, compared as exact integer
 *       milliseconds ({@code long ==})</li>
 * </ul>
 *
 * <p>On ANY failure it returns a bounded {@link Result} carrying only a short diagnostic
 * {@link RuntimeDiagnostics.ManifestCode}. It NEVER includes comment text, scene text or any
 * manifest content in the result, and it has NO ACK responsibility: a reject is simply a
 * failed result that the caller must honor by keeping the prior valid content intact.</p>
 */
final class VideoOverlayManifestBridge {
    /**
     * Bounded outcome of one cross-contract check. {@link #ok} is true only when every
     * section-5 rule holds; otherwise {@link #code} is the single bounded reason and
     * contains no submitted content.
     */
    static final class Result {
        final boolean ok;
        final RuntimeDiagnostics.ManifestCode code;

        private Result(boolean ok, RuntimeDiagnostics.ManifestCode code) {
            this.ok = ok;
            this.code = code;
        }

        static Result ok() {
            return new Result(true, null);
        }

        static Result fail(RuntimeDiagnostics.ManifestCode code) {
            return new Result(false, code);
        }
    }

    /**
     * Validates a parsed runtimeTrack against its parsed manifest. Returns a bounded success
     * or a bounded failure code; never throws for an invalid pairing and never surfaces
     * content. A null argument is treated as a structural {@code MANIFEST_INVALID}.
     */
    static Result validate(ScheduledTrack runtimeTrack, OverlayManifest manifest) {
        if (runtimeTrack == null || manifest == null) {
            return Result.fail(RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID);
        }
        // Envelope-level Video contract: product, bound source id and media clock.
        if (!"video".equals(manifest.product)
                || !"media".equals(manifest.clockMode)
                || manifest.sourceId == null
                || !manifest.sourceId.equals(runtimeTrack.trackId)) {
            return Result.fail(RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID);
        }

        // Build an O(n) scene index by id. A duplicate scene id is structurally invalid.
        Map<String, OverlayManifest.Scene> scenesById = new HashMap<>();
        for (OverlayManifest.Scene scene : manifest.scenes) {
            if (scenesById.put(scene.id, scene) != null) {
                return Result.fail(RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID);
            }
        }

        // A bijection requires the same cardinality: no extra Video scenes, no missing ones.
        if (scenesById.size() != runtimeTrack.comments.size()) {
            return Result.fail(RuntimeDiagnostics.ManifestCode.MANIFEST_INCONSISTENT);
        }

        // Each comment maps to exactly one scene with EXACT integer-ms timing.
        for (ScheduledTrack.Event comment : runtimeTrack.comments) {
            OverlayManifest.Scene scene = scenesById.get(comment.id);
            if (scene == null) {
                return Result.fail(RuntimeDiagnostics.ManifestCode.MANIFEST_INCONSISTENT);
            }
            if (scene.startMs != comment.startMs || scene.durationMs != comment.durationMs) {
                return Result.fail(RuntimeDiagnostics.ManifestCode.MANIFEST_INCONSISTENT);
            }
        }

        // Equal cardinality plus a hit for every comment id implies a full bijection with no
        // extra scenes, so the pairing is coherent.
        return Result.ok();
    }

    /** Utility class has no mutable state. */
    private VideoOverlayManifestBridge() {}
}
