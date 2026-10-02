package com.scenevibe.tvcompanionpoc;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/** Locks the 0.10A scene-language boundary without Android runtime dependencies. */
public final class OverlayManifestParserTest {
    /** Builds a valid Video manifest equivalent to the first FinalTrack projection. */
    private static JSONObject videoManifest() throws Exception {
        return new JSONObject()
                .put("type","scenevibe.overlay-manifest.v1")
                .put("schemaVersion","1.0.0")
                .put("manifestId","video:track-1")
                .put("source",new JSONObject()
                        .put("product","video")
                        .put("sourceId","track-1"))
                .put("canvas",new JSONObject().put("width",1920).put("height",1080))
                .put("clock",new JSONObject().put("mode","media").put("pauseBehavior","freeze"))
                .put("scenes",new JSONArray().put(new JSONObject()
                        .put("id","comment-1")
                        .put("startMs",12_000)
                        .put("durationMs",6_000)
                        .put("elements",new JSONArray().put(new JSONObject()
                                .put("id","comment-1:card")
                                .put("type","group")
                                .put("frame",frame(140,70,1640,210))
                                .put("zIndex",10)
                                .put("opacity",1.0)
                                .put("animation",new JSONObject()
                                        .put("enter","fade").put("exit","fade").put("durationMs",180))
                                .put("children",new JSONArray()
                                        .put(rectangle("comment-1:bg",0,0,1640,210))
                                        .put(text("comment-1:text","Documented comment.",46,28,1548,154))))));
    }

    /** Builds the initial primitive family in one Banner scene. */
    private static JSONObject bannerManifest() throws Exception {
        return new JSONObject()
                .put("type","scenevibe.overlay-manifest.v1")
                .put("schemaVersion","1.0.0")
                .put("manifestId","banner:demo")
                .put("source",new JSONObject().put("product","banner").put("sourceId","demo"))
                .put("canvas",new JSONObject().put("width",1920).put("height",1080))
                .put("clock",new JSONObject().put("mode","wall").put("pauseBehavior","continue"))
                .put("scenes",new JSONArray().put(new JSONObject()
                        .put("id","banner-scene")
                        .put("startMs",0)
                        .put("durationMs",30_000)
                        .put("elements",new JSONArray().put(new JSONObject()
                                .put("id","banner-group")
                                .put("type","group")
                                .put("frame",frame(100,80,1720,900))
                                .put("zIndex",1)
                                .put("opacity",1.0)
                                .put("children",new JSONArray()
                                        .put(rectangle("bg",0,0,1720,900))
                                        .put(text("title","Menu du jour",300,60,1320,140))
                                        .put(new JSONObject()
                                                .put("id","logo")
                                                .put("type","image")
                                                .put("frame",frame(60,50,200,200))
                                                .put("zIndex",2)
                                                .put("opacity",1.0)
                                                .put("assetRef","asset:demo-logo")
                                                .put("fit","contain"))
                                        .put(table("prices")))))));
    }

    /** Build a bounded frame object. */
    private static JSONObject frame(int x,int y,int width,int height) throws Exception {
        return new JSONObject().put("x",x).put("y",y).put("width",width).put("height",height);
    }

    /** Build a text primitive. */
    private static JSONObject text(String id,String value,int x,int y,int width,int height) throws Exception {
        return new JSONObject()
                .put("id",id).put("type","text").put("frame",frame(x,y,width,height))
                .put("zIndex",2).put("opacity",1.0).put("text",value)
                .put("style",new JSONObject()
                        .put("color","#FFFFFF").put("backgroundColor","#00000000")
                        .put("fontSize",42).put("fontWeight","normal").put("textAlign","center")
                        .put("padding",0).put("cornerRadius",0));
    }

    /** Build a rectangle primitive. */
    private static JSONObject rectangle(String id,int x,int y,int width,int height) throws Exception {
        return new JSONObject()
                .put("id",id).put("type","rectangle").put("frame",frame(x,y,width,height))
                .put("zIndex",0).put("opacity",0.9)
                .put("style",new JSONObject().put("fillColor","#17130F").put("cornerRadius",24));
    }

    /** Build a small table primitive. */
    private static JSONObject table(String id) throws Exception {
        return new JSONObject()
                .put("id",id).put("type","table").put("frame",frame(160,350,1400,380))
                .put("zIndex",2).put("opacity",1.0)
                .put("rows",new JSONArray()
                        .put(new JSONArray().put("Plat").put("Prix"))
                        .put(new JSONArray().put("Formule").put("14,90 €")))
                .put("style",new JSONObject()
                        .put("color","#FFFFFF").put("backgroundColor","#2A241F")
                        .put("gridColor","#665C52").put("fontSize",40).put("padding",16));
    }

    /** Video projection parses as a self-contained media-clock scene. */
    @Test public void videoManifestParses() throws Exception {
        OverlayManifest manifest=OverlayManifestParser.parse(videoManifest());
        assertEquals("video:track-1",manifest.manifestId);
        assertEquals("video",manifest.product);
        assertEquals("track-1",manifest.sourceId);
        assertEquals("media",manifest.clockMode);
        assertEquals("freeze",manifest.pauseBehavior);
        assertEquals(1,manifest.scenes.size());
        assertEquals(12_000,manifest.scenes.get(0).startMs);
        assertEquals(OverlayManifest.PrimitiveType.GROUP,manifest.scenes.get(0).elements.get(0).type);
    }

    /** Banner demonstrates text/image/rectangle/table/group without product-specific renderer code. */
    @Test public void bannerSupportsInitialPrimitiveFamily() throws Exception {
        OverlayManifest manifest=OverlayManifestParser.parse(bannerManifest());
        OverlayManifest.Element group=manifest.scenes.get(0).elements.get(0);
        assertEquals(OverlayManifest.PrimitiveType.GROUP,group.type);
        assertEquals(4,group.children.size());
        assertTrue(group.children.stream().anyMatch(item->item.type==OverlayManifest.PrimitiveType.TEXT));
        assertTrue(group.children.stream().anyMatch(item->item.type==OverlayManifest.PrimitiveType.IMAGE));
        assertTrue(group.children.stream().anyMatch(item->item.type==OverlayManifest.PrimitiveType.RECTANGLE));
        assertTrue(group.children.stream().anyMatch(item->item.type==OverlayManifest.PrimitiveType.TABLE));
    }

    /** Direct network URLs are not valid asset references. */
    @Test public void imageUrlFailsClosed() throws Exception {
        JSONObject banner=bannerManifest();
        JSONArray children=banner.getJSONArray("scenes").getJSONObject(0)
                .getJSONArray("elements").getJSONObject(0).getJSONArray("children");
        children.getJSONObject(2).put("assetRef","https://example.invalid/logo.png");
        assertInvalid(banner);
    }

    /** Unknown executable-looking primitives are rejected before any View is created. */
    @Test public void webViewPrimitiveFailsClosed() throws Exception {
        JSONObject banner=bannerManifest();
        banner.getJSONArray("scenes").getJSONObject(0).getJSONArray("elements")
                .getJSONObject(0).getJSONArray("children").getJSONObject(0).put("type","webview");
        assertInvalid(banner);
    }

    /** Child geometry may not escape the group's coordinate space. */
    @Test public void childOutsideGroupFailsClosed() throws Exception {
        JSONObject banner=bannerManifest();
        banner.getJSONArray("scenes").getJSONObject(0).getJSONArray("elements")
                .getJSONObject(0).getJSONArray("children").getJSONObject(0)
                .getJSONObject("frame").put("width",5000);
        assertInvalid(banner);
    }

    /** Duplicate primitive ids are ambiguous and fail closed. */
    @Test public void duplicateElementIdFailsClosed() throws Exception {
        JSONObject banner=bannerManifest();
        JSONArray children=banner.getJSONArray("scenes").getJSONObject(0)
                .getJSONArray("elements").getJSONObject(0).getJSONArray("children");
        children.getJSONObject(1).put("id",children.getJSONObject(0).getString("id"));
        assertInvalid(banner);
    }

    /** Helper proving the bounded protocol exception is raised. */
    private static void assertInvalid(JSONObject manifest) throws Exception {
        try {
            OverlayManifestParser.parse(manifest);
            fail("Expected invalid OverlayManifest");
        } catch(OverlayManifestParser.Invalid expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
