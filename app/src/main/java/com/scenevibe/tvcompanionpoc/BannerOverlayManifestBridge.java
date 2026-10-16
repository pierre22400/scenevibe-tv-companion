package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.wall.WallCalendar;
import com.scenevibe.tvcompanionpoc.wall.WallEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Pure, Android-independent cross-contract validator binding a Banner profile's pure
 * {@link WallCalendar} window set to its inline {@link OverlayManifest} (section 8). It is the WALL
 * analogue of {@link VideoOverlayManifestBridge}: it moves no bytes, reads no clock and has NO ACK
 * responsibility; a reject is a bounded failed {@link Result} the caller must honor by keeping the
 * prior valid durable intact.
 *
 * <p>It enforces EXACTLY, with no tolerance:
 * <ul>
 *   <li>{@code manifest.product == "banner"}, {@code manifest.clockMode == "wall"},
 *       {@code manifest.pauseBehavior == "continue"}</li>
 *   <li>a bijection between WALL window ids and manifest scene ids (equal counts, every window maps
 *       to exactly one scene, no extra scenes, no duplicates)</li>
 *   <li>for every scene: {@code scene.id == eventId}, {@code scene.startMs == 0} (offset-zero local
 *       anchor), and {@code scene.durationMs == window.end - window.start} compared as an exact
 *       {@code long}</li>
 *   <li>no asset reference and no image primitive anywhere in the scene tree (recursively), and
 *       {@code animation == none} everywhere (recursively): a deferred/fade animation is refused</li>
 * </ul>
 * The parser already applied every M4 size bound (scenes, primitives per scene, group depth, canvas,
 * text codepoints) and the pure models already applied the WALL domain/horizon bounds, so this
 * bridge only decides the Banner coherence rules the parser cannot see across the two contracts.
 */
final class BannerOverlayManifestBridge {

    /** Bounded outcome of one Banner cross-contract check; carries only a short diagnostic code. */
    static final class Result {
        final boolean ok;
        final RuntimeDiagnostics.ManifestCode code;

        private Result(boolean ok, RuntimeDiagnostics.ManifestCode code) {
            this.ok = ok;
            this.code = code;
        }

        static Result ok() { return new Result(true, null); }

        static Result fail(RuntimeDiagnostics.ManifestCode code) { return new Result(false, code); }
    }

    /**
     * Validate a parsed WALL calendar against its parsed inline manifest. Returns a bounded success
     * or a bounded failure code; never throws for an invalid pairing and never surfaces content.
     */
    static Result validate(WallCalendar calendar, OverlayManifest manifest) {
        if (calendar == null || manifest == null) {
            return Result.fail(RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID);
        }
        // Envelope-level Banner contract: product, WALL clock and continuing pause behavior.
        if (!"banner".equals(manifest.product)
                || !"wall".equals(manifest.clockMode)
                || !"continue".equals(manifest.pauseBehavior)) {
            return Result.fail(RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID);
        }

        // The Banner profile carries no renderable asset or image, and no deferred animation.
        for (OverlayManifest.Scene scene : manifest.scenes) {
            if (!noAssetNoImageNoAnimation(scene.elements)) {
                return Result.fail(RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID);
            }
        }

        // Build an O(n) scene index by id. A duplicate scene id is structurally invalid.
        Map<String, OverlayManifest.Scene> scenesById = new HashMap<>();
        for (OverlayManifest.Scene scene : manifest.scenes) {
            if (scenesById.put(scene.id, scene) != null) {
                return Result.fail(RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID);
            }
        }

        // A bijection requires the same cardinality: no extra scenes, no missing windows.
        if (scenesById.size() != calendar.events().size()) {
            return Result.fail(RuntimeDiagnostics.ManifestCode.MANIFEST_INCONSISTENT);
        }

        // Each WALL window maps to exactly one scene whose id == eventId, startMs == 0 (offset-zero)
        // and durationMs == (end - start) as an exact long.
        for (WallEvent window : calendar.events()) {
            OverlayManifest.Scene scene = scenesById.get(window.eventId());
            if (scene == null) {
                return Result.fail(RuntimeDiagnostics.ManifestCode.MANIFEST_INCONSISTENT);
            }
            if (scene.startMs != 0L
                    || scene.durationMs != window.endEpochMs() - window.startEpochMs()) {
                return Result.fail(RuntimeDiagnostics.ManifestCode.MANIFEST_INCONSISTENT);
            }
        }
        return Result.ok();
    }

    /** Recursively refuse any image primitive, any asset reference and any non-none animation. */
    private static boolean noAssetNoImageNoAnimation(java.util.List<OverlayManifest.Element> elements) {
        if (elements == null) return true;
        for (OverlayManifest.Element element : elements) {
            if (element.type == OverlayManifest.PrimitiveType.IMAGE || element.assetRef != null) {
                return false;
            }
            if (element.animation != null
                    && (!"none".equals(element.animation.enter) || !"none".equals(element.animation.exit))) {
                return false;
            }
            if (element.type == OverlayManifest.PrimitiveType.GROUP
                    && !noAssetNoImageNoAnimation(element.children)) {
                return false;
            }
        }
        return true;
    }

    /** Utility class has no mutable state. */
    private BannerOverlayManifestBridge() {}
}
