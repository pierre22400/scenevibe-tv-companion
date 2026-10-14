package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;

/**
 * In-memory E5 qualification plan, separate from strict graphical OverlayManifest v1.
 *
 * <p>No parser, Cloud publication, MEDIA event or durable DTO accepts this plan. The fixture
 * is fixed by asset name, SHA-256 and size; callers cannot supply a URI or filesystem path.</p>
 */
final class LocalInterludePlan {
    static final String ASSET_NAME = "scenevibe_interlude_10s.mp4";
    static final String SHA256 = "c66ded0b6a2ea41ef34320a289f2c1ec37e2a87d34ef580d9ac45d08815682ec";
    static final long FILE_BYTES = 2146888L;
    static final long DURATION_MS = 10000L;
    final OverlayManifest.Scene scene;
    final LocalMediaResource resource;
    final OverlayManifest.Frame videoFrame;
    final int videoZ;
    final boolean vodConfirmed;
    final NativeQualificationPolicy policy;

    /** Bind valid graphical data to the fixed local qualification fixture only. */
    LocalInterludePlan(OverlayManifest.Scene scene, OverlayManifest.Frame videoFrame,
            int videoZ, boolean vodConfirmed, NativeQualificationPolicy policy) {
        if (scene == null || videoFrame == null || policy == null
                || videoFrame.x < 0 || videoFrame.y < 0 || videoFrame.width <= 0
                || videoFrame.height <= 0 || (long) videoFrame.x + videoFrame.width > 1920
                || (long) videoFrame.y + videoFrame.height > 1080 || videoZ < -1000 || videoZ > 1000) {
            throw new IllegalArgumentException("Invalid local qualification plan");
        }
        this.scene = scene;
        this.resource = LocalMediaResource.fixture035();
        this.videoFrame = videoFrame;
        this.videoZ = videoZ;
        this.vodConfirmed = vodConfirmed;
        this.policy = policy;
    }

    /** Create text/shape through the unchanged strict v1 parser; no VIDEO primitive is added. */
    static LocalInterludePlan qualification(boolean vodConfirmed) {
        String json = """
                {"type":"scenevibe.overlay-manifest.v1","schemaVersion":"1.0.0",
                 "manifestId":"e5-native-local","source":{"product":"video","sourceId":"e5-native-035"},
                 "canvas":{"width":1920,"height":1080},"clock":{"mode":"media","pauseBehavior":"freeze"},
                 "scenes":[{"id":"e5-local-clip","startMs":0,"durationMs":10000,"elements":[
                  {"id":"e5-panel","type":"rectangle","frame":{"x":160,"y":930,"width":1600,"height":100},
                   "zIndex":9,"opacity":1,"style":{"fillColor":"#202020","cornerRadius":0}},
                  {"id":"e5-caption","type":"text","frame":{"x":160,"y":930,"width":1600,"height":100},
                   "zIndex":10,"opacity":1,"text":"SceneVibe — local interlude qualification",
                   "style":{"color":"#FFFFFF","backgroundColor":"#00000000","fontSize":36,
                            "fontWeight":"normal","textAlign":"center","padding":8,"cornerRadius":0}}]}]}
                """;
        try {
            OverlayManifest manifest = OverlayManifestParser.parse(new JSONObject(json));
            return new LocalInterludePlan(manifest.scenes.get(0),
                    new OverlayManifest.Frame(160, 30, 1600, 900), 0, vodConfirmed,
                    NativeQualificationPolicy.local035());
        } catch (Exception invalid) {
            throw new IllegalStateException("LOCAL_GRAPHIC_INVALID", invalid);
        }
    }
}
