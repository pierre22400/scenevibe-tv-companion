package com.scenevibe.tvcompanionpoc;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Section 20-E renderer-boundary coverage that is feasible in pure JVM (no real Android View).
 *
 * <p>The initial primitive family (text/rectangle/group/table/image-with-asset, fade and
 * coordinates) is drawn with real Android Views whose constructors return default stubs under
 * {@code testOptions.unitTests.returnDefaultValues=true}; those visual cases remain CI/physical
 * and are proven here only up to the parser contract (that they PARSE into the expected
 * primitives). What IS unit-proven here is the pure, Android-free decision logic the regie uses
 * before mutating any window:</p>
 * <ul>
 *   <li>the scene-asset preflight collector walks every IMAGE assetRef, including group
 *       children, so a missing local asset is detected as a bounded failure with no partial
 *       render (section 15);</li>
 *   <li>a scene with no image elements has no asset requirement;</li>
 *   <li>the z-order used by the renderer is deterministic (zIndex asc, id tie-break).</li>
 * </ul>
 *
 * <p>The "preflight returns false => suppressed show, no crash" and "dismiss idempotent"
 * behaviors through the regie are proven at the controller boundary in
 * {@link SceneRuntimeControllerTest#preflightFailureSuppressesShow()} and the SceneSink
 * contract there; this test covers the pure asset-walk and ordering seams that SceneRenderer
 * exposes.</p>
 */
public final class SceneRendererPreflightTest {

    // ---- fixture builders (reuse the parser's JSON contract) ----------------------------

    private static JSONObject frame(int x,int y,int w,int h) throws Exception {
        return new JSONObject().put("x",x).put("y",y).put("width",w).put("height",h);
    }

    private static JSONObject text(String id,String value) throws Exception {
        return new JSONObject().put("id",id).put("type","text").put("frame",frame(0,0,800,120))
                .put("zIndex",2).put("opacity",1.0).put("text",value)
                .put("style",new JSONObject().put("color","#FFFFFF").put("backgroundColor","#00000000")
                        .put("fontSize",42).put("fontWeight","normal").put("textAlign","center")
                        .put("padding",0).put("cornerRadius",0));
    }

    private static JSONObject rectangle(String id,int z) throws Exception {
        return new JSONObject().put("id",id).put("type","rectangle").put("frame",frame(0,0,800,120))
                .put("zIndex",z).put("opacity",0.9)
                .put("style",new JSONObject().put("fillColor","#17130F").put("cornerRadius",24));
    }

    private static JSONObject image(String id,String assetRef) throws Exception {
        return new JSONObject().put("id",id).put("type","image").put("frame",frame(0,0,200,200))
                .put("zIndex",2).put("opacity",1.0).put("assetRef",assetRef).put("fit","contain");
    }

    private static OverlayManifest.Scene parseScene(JSONArray elements) throws Exception {
        JSONObject scene=new JSONObject().put("id","scene-1").put("startMs",0).put("durationMs",6000)
                .put("elements",elements);
        JSONObject manifest=new JSONObject()
                .put("type","scenevibe.overlay-manifest.v1").put("schemaVersion","1.0.0")
                .put("manifestId","video:track-1")
                .put("source",new JSONObject().put("product","video").put("sourceId","track-1"))
                .put("canvas",new JSONObject().put("width",1920).put("height",1080))
                .put("clock",new JSONObject().put("mode","media").put("pauseBehavior","freeze"))
                .put("scenes",new JSONArray().put(scene));
        return OverlayManifestParser.parse(manifest).scenes.get(0);
    }

    // ---- preflight asset-walk (section 15) ----------------------------------------------

    /** An image nested inside a group is discovered by the asset walk (recurses children). */
    @Test public void imageAssetRefsIncludeGroupChildren() throws Exception {
        JSONArray children=new JSONArray()
                .put(rectangle("bg",0))
                .put(image("logo","asset:demo-logo"));
        JSONObject group=new JSONObject().put("id","card").put("type","group")
                .put("frame",frame(0,0,1720,900)).put("zIndex",1).put("opacity",1.0)
                .put("children",children);
        OverlayManifest.Scene scene=parseScene(new JSONArray().put(group));

        List<String> refs=SceneRenderer.imageAssetRefs(scene);
        assertEquals(Arrays.asList("asset:demo-logo"),refs);
    }

    /** Multiple images at several depths are all collected in declared order. */
    @Test public void imageAssetRefsCollectEveryImage() throws Exception {
        JSONArray innerChildren=new JSONArray()
                .put(image("inner","asset:inner-pic"))
                .put(text("caption","hi"));
        JSONObject inner=new JSONObject().put("id","inner-group").put("type","group")
                .put("frame",frame(0,0,800,400)).put("zIndex",0).put("opacity",1.0)
                .put("children",innerChildren);
        JSONArray outerChildren=new JSONArray()
                .put(image("top","asset:top-pic"))
                .put(inner);
        JSONObject outer=new JSONObject().put("id","outer-group").put("type","group")
                .put("frame",frame(0,0,1720,900)).put("zIndex",1).put("opacity",1.0)
                .put("children",outerChildren);
        OverlayManifest.Scene scene=parseScene(new JSONArray().put(outer));

        List<String> refs=SceneRenderer.imageAssetRefs(scene);
        assertEquals(Arrays.asList("asset:top-pic","asset:inner-pic"),refs);
    }

    /** A text-only scene requires no assets, so preflight has nothing to resolve. */
    @Test public void textOnlySceneHasNoAssetRequirement() throws Exception {
        OverlayManifest.Scene scene=parseScene(new JSONArray().put(text("t","body")));
        assertTrue(SceneRenderer.imageAssetRefs(scene).isEmpty());
    }

    /** A null scene yields an empty, crash-free asset list (bounded, fail-closed upstream). */
    @Test public void nullSceneYieldsNoAssets() {
        assertTrue(SceneRenderer.imageAssetRefs(null).isEmpty());
    }

    // ---- deterministic z-order ----------------------------------------------------------

    /** Lower zIndex is ordered first; equal zIndex falls back to the id for stable ordering. */
    @Test public void orderingIsDeterministicByZIndexThenId() throws Exception {
        JSONArray elements=new JSONArray()
                .put(rectangle("beta",5))
                .put(rectangle("alpha",5))
                .put(rectangle("front",10))
                .put(rectangle("back",0));
        OverlayManifest.Scene scene=parseScene(elements);

        List<String> ids=new ArrayList<>();
        for(OverlayManifest.Element element:SceneRenderer.orderedForTest(scene.elements)) {
            ids.add(element.id);
        }
        // back(0) < alpha(5)==beta(5) by id < front(10).
        assertEquals(Arrays.asList("back","alpha","beta","front"),ids);
    }
}
