package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;

/**
 * Resolves the additive scenevibe.overlay-manifest.v1 routing metadata carried by a
 * cloud assignment. Renderer identifiers are data, never executable class names or URLs.
 *
 * <p>Assignments without a manifest remain valid through the explicit LEGACY_COMMENTARY
 * route so a new TV build can still consume the already-qualified 0.9D cloud contract.
 * Once a manifest is present it must match an allow-listed renderer/payload mapping or the
 * whole assignment fails closed.</p>
 */
final class OverlayManifestRouter {
    static final String TYPE="scenevibe.overlay-manifest.v1";
    static final String SURFACE="system_overlay";
    static final String COMMENTARY_RENDERER="scenevibe.renderer.commentary.v1";
    static final String RUNTIME_TRACK_CONTRACT="scenevibe.track.v1";
    static final String RUNTIME_TRACK_REF="assignment.runtimeTrack";

    enum Kind {
        LEGACY_COMMENTARY,
        COMMENTARY
    }

    /** Immutable result containing only the selected kind and already-present sibling payload. */
    static final class Resolved {
        final Kind kind;
        final JSONObject payload;

        Resolved(Kind kind,JSONObject payload) {
            this.kind=kind;
            this.payload=payload;
        }
    }

    /** Bounded protocol failure; never includes submitted content or credentials. */
    static final class Invalid extends Exception {
        Invalid(String message) {super(message);}
    }

    /**
     * Resolve one assignment to an allow-listed renderer.
     *
     * <p>The v1 commentary manifest may only reference the sibling runtimeTrack field and its
     * payload id must equal runtimeTrack.trackId. Unknown renderer ids, arbitrary refs and
     * malformed objects are rejected instead of falling back to the commentary renderer.</p>
     */
    static Resolved resolve(JSONObject assignment) throws Invalid {
        if(assignment==null)throw new Invalid("Assignment missing");
        JSONObject runtime=assignment.optJSONObject("runtimeTrack");
        if(runtime==null)throw new Invalid("Runtime payload missing");

        Object rawManifest=assignment.opt("overlayManifest");
        if(rawManifest==null)return new Resolved(Kind.LEGACY_COMMENTARY,runtime);
        if(!(rawManifest instanceof JSONObject))throw new Invalid("Manifest malformed");
        JSONObject manifest=(JSONObject)rawManifest;
        JSONObject renderer=manifest.optJSONObject("renderer");
        JSONObject payload=manifest.optJSONObject("payload");
        String trackId=runtime.optString("trackId","");

        if(!TYPE.equals(manifest.optString("type"))
                ||!SURFACE.equals(manifest.optString("surface"))
                ||renderer==null||payload==null
                ||!COMMENTARY_RENDERER.equals(renderer.optString("id"))
                ||!RUNTIME_TRACK_CONTRACT.equals(payload.optString("contract"))
                ||!RUNTIME_TRACK_REF.equals(payload.optString("ref"))
                ||trackId.isEmpty()||!trackId.equals(payload.optString("id")))
            throw new Invalid("Unsupported overlay route");

        return new Resolved(Kind.COMMENTARY,runtime);
    }

    /** Utility class has no mutable state. */
    private OverlayManifestRouter() {}
}
