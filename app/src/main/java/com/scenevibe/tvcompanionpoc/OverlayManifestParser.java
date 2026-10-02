package com.scenevibe.tvcompanionpoc;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Strict parser for scenevibe.overlay-manifest.v1.
 *
 * <p>OverlayManifest is inert data. Unsupported primitive types, out-of-bounds geometry,
 * direct URLs and malformed tables fail closed before any Android View is created.</p>
 */
final class OverlayManifestParser {
    private static final Pattern SAFE_ID=Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Pattern SAFE_ASSET=Pattern.compile("asset:[A-Za-z0-9._/-]{1,249}");
    private static final Pattern COLOR=Pattern.compile("#(?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})");
    private static final int MAX_SCENES=256;
    private static final int MAX_ELEMENTS=256;
    private static final int MAX_GROUP_DEPTH=4;

    /** Safe bounded parse failure with no submitted content. */
    static final class Invalid extends Exception {
        Invalid(String message) {super(message);}
    }

    /** Parse and fully validate one manifest snapshot. */
    static OverlayManifest parse(JSONObject json) throws Invalid {
        if(json==null
                ||!OverlayManifest.TYPE.equals(json.optString("type"))
                ||!OverlayManifest.SCHEMA_VERSION.equals(json.optString("schemaVersion")))
            throw new Invalid("Invalid manifest contract");

        String manifestId=safeId(json.optString("manifestId",""),"manifestId");
        JSONObject source=requireObject(json,"source");
        String product=source.optString("product","");
        if(!product.equals("video")&&!product.equals("banner")
                &&!product.equals("language")&&!product.equals("other"))
            throw new Invalid("Invalid product");
        String sourceId=safeId(source.optString("sourceId",""),"sourceId");

        JSONObject canvas=requireObject(json,"canvas");
        if(canvas.optInt("width",-1)!=OverlayManifest.CANVAS_WIDTH
                ||canvas.optInt("height",-1)!=OverlayManifest.CANVAS_HEIGHT)
            throw new Invalid("Invalid canvas");

        JSONObject clock=requireObject(json,"clock");
        String clockMode=clock.optString("mode","");
        String pause=clock.optString("pauseBehavior","");
        if((!clockMode.equals("media")&&!clockMode.equals("wall"))
                ||(!pause.equals("freeze")&&!pause.equals("continue")))
            throw new Invalid("Invalid clock");

        JSONArray rawScenes=json.optJSONArray("scenes");
        if(rawScenes==null||rawScenes.length()<1||rawScenes.length()>MAX_SCENES)
            throw new Invalid("Invalid scenes");

        ArrayList<OverlayManifest.Scene> scenes=new ArrayList<>();
        Set<String> sceneIds=new HashSet<>();
        for(int i=0;i<rawScenes.length();i++) {
            JSONObject scene=rawScenes.optJSONObject(i);
            if(scene==null)throw new Invalid("Scene must be object");
            String sceneId=safeId(scene.optString("id",""),"scene id");
            if(!sceneIds.add(sceneId))throw new Invalid("Duplicate scene id");
            long start=exactLong(scene,"startMs",0,43_200_000L);
            long duration=exactLong(scene,"durationMs",250,3_600_000L);
            JSONArray elements=scene.optJSONArray("elements");
            if(elements==null||elements.length()<1)
                throw new Invalid("Scene elements missing");
            Counter counter=new Counter();
            Set<String> elementIds=new HashSet<>();
            List<OverlayManifest.Element> parsed=parseElements(elements,
                    OverlayManifest.CANVAS_WIDTH,OverlayManifest.CANVAS_HEIGHT,0,
                    counter,elementIds);
            scenes.add(new OverlayManifest.Scene(sceneId,start,duration,parsed));
        }

        return new OverlayManifest(manifestId,product,sourceId,clockMode,pause,scenes);
    }

    /** Parse a bounded list of primitives relative to one parent coordinate space. */
    private static List<OverlayManifest.Element> parseElements(JSONArray input,int parentWidth,
            int parentHeight,int depth,Counter counter,Set<String> ids) throws Invalid {
        ArrayList<OverlayManifest.Element> result=new ArrayList<>();
        for(int i=0;i<input.length();i++) {
            counter.count++;
            if(counter.count>MAX_ELEMENTS)throw new Invalid("Too many elements");
            JSONObject item=input.optJSONObject(i);
            if(item==null)throw new Invalid("Element must be object");
            result.add(parseElement(item,parentWidth,parentHeight,depth,counter,ids));
        }
        return result;
    }

    /** Parse one allow-listed primitive; no reflection or dynamic renderer lookup exists. */
    private static OverlayManifest.Element parseElement(JSONObject item,int parentWidth,
            int parentHeight,int depth,Counter counter,Set<String> ids) throws Invalid {
        String id=safeId(item.optString("id",""),"element id");
        if(!ids.add(id))throw new Invalid("Duplicate element id");
        OverlayManifest.Frame frame=parseFrame(requireObject(item,"frame"),parentWidth,parentHeight);
        int zIndex=exactInt(item,"zIndex",-1000,1000);
        float opacity=exactFloat(item,"opacity",0f,1f);
        OverlayManifest.Animation animation=parseAnimation(item.optJSONObject("animation"));
        String type=item.optString("type","");

        if("text".equals(type)) {
            JSONObject style=requireObject(item,"style");
            String text=item.optString("text","");
            if(text.trim().isEmpty()||text.length()>2000)throw new Invalid("Invalid text");
            String color=color(style.optString("color",""));
            String background=color(style.optString("backgroundColor",""));
            int fontSize=exactInt(style,"fontSize",8,180);
            String weight=style.optString("fontWeight","");
            if(!weight.equals("normal")&&!weight.equals("bold"))throw new Invalid("Invalid font weight");
            String align=style.optString("textAlign","");
            if(!align.equals("start")&&!align.equals("center")&&!align.equals("end"))
                throw new Invalid("Invalid text alignment");
            int padding=exactInt(style,"padding",0,200);
            int radius=exactInt(style,"cornerRadius",0,300);
            return element(id,OverlayManifest.PrimitiveType.TEXT,frame,zIndex,opacity,animation,
                    text,null,null,color,background,fontSize,weight.equals("bold"),align,padding,
                    radius,null,null,java.util.Collections.emptyList(),java.util.Collections.emptyList());
        }

        if("image".equals(type)) {
            String assetRef=item.optString("assetRef","");
            if(!SAFE_ASSET.matcher(assetRef).matches())throw new Invalid("Invalid asset ref");
            String fit=item.optString("fit","");
            if(!fit.equals("contain")&&!fit.equals("cover"))throw new Invalid("Invalid image fit");
            return element(id,OverlayManifest.PrimitiveType.IMAGE,frame,zIndex,opacity,animation,
                    null,assetRef,fit,null,null,0,false,null,0,0,null,null,java.util.Collections.emptyList(),java.util.Collections.emptyList());
        }

        if("rectangle".equals(type)) {
            JSONObject style=requireObject(item,"style");
            String fill=color(style.optString("fillColor",""));
            int radius=exactInt(style,"cornerRadius",0,300);
            return element(id,OverlayManifest.PrimitiveType.RECTANGLE,frame,zIndex,opacity,animation,
                    null,null,null,null,null,0,false,null,0,radius,fill,null,java.util.Collections.emptyList(),java.util.Collections.emptyList());
        }

        if("table".equals(type)) {
            JSONObject style=requireObject(item,"style");
            List<List<String>> rows=parseRows(item.optJSONArray("rows"));
            String color=color(style.optString("color",""));
            String background=color(style.optString("backgroundColor",""));
            String grid=color(style.optString("gridColor",""));
            int fontSize=exactInt(style,"fontSize",8,120);
            int padding=exactInt(style,"padding",0,120);
            return element(id,OverlayManifest.PrimitiveType.TABLE,frame,zIndex,opacity,animation,
                    null,null,null,color,background,fontSize,false,null,padding,0,null,grid,rows,java.util.Collections.emptyList());
        }

        if("group".equals(type)) {
            if(depth>=MAX_GROUP_DEPTH)throw new Invalid("Group nesting too deep");
            JSONArray children=item.optJSONArray("children");
            if(children==null||children.length()<1||children.length()>128)
                throw new Invalid("Invalid group children");
            List<OverlayManifest.Element> parsed=parseElements(children,frame.width,frame.height,
                    depth+1,counter,ids);
            return element(id,OverlayManifest.PrimitiveType.GROUP,frame,zIndex,opacity,animation,
                    null,null,null,null,null,0,false,null,0,0,null,null,java.util.Collections.emptyList(),parsed);
        }

        throw new Invalid("Unsupported primitive type");
    }

    /** Parse a rectangular table with bounded cells. */
    private static List<List<String>> parseRows(JSONArray rows) throws Invalid {
        if(rows==null||rows.length()<1||rows.length()>20)throw new Invalid("Invalid table");
        ArrayList<List<String>> result=new ArrayList<>();
        int width=-1;
        for(int r=0;r<rows.length();r++) {
            JSONArray row=rows.optJSONArray(r);
            if(row==null||row.length()<1||row.length()>10)throw new Invalid("Invalid table row");
            if(width<0)width=row.length();
            if(row.length()!=width)throw new Invalid("Unequal table rows");
            ArrayList<String> cells=new ArrayList<>();
            for(int c=0;c<row.length();c++) {
                Object raw=row.opt(c);
                if(!(raw instanceof String)||((String)raw).length()>500)
                    throw new Invalid("Invalid table cell");
                cells.add((String)raw);
            }
            result.add(cells);
        }
        return result;
    }

    /** Parse one frame and require it to remain inside its parent. */
    private static OverlayManifest.Frame parseFrame(JSONObject frame,int parentWidth,
            int parentHeight) throws Invalid {
        int x=exactInt(frame,"x",0,parentWidth);
        int y=exactInt(frame,"y",0,parentHeight);
        int width=exactInt(frame,"width",1,parentWidth);
        int height=exactInt(frame,"height",1,parentHeight);
        if(x+width>parentWidth||y+height>parentHeight)throw new Invalid("Frame outside parent");
        return new OverlayManifest.Frame(x,y,width,height);
    }

    /** Parse optional bounded fade metadata. */
    private static OverlayManifest.Animation parseAnimation(JSONObject animation) throws Invalid {
        if(animation==null)return null;
        String enter=animation.optString("enter","");
        String exit=animation.optString("exit","");
        if((!enter.equals("none")&&!enter.equals("fade"))
                ||(!exit.equals("none")&&!exit.equals("fade")))
            throw new Invalid("Invalid animation");
        int duration=exactInt(animation,"durationMs",0,10_000);
        return new OverlayManifest.Animation(enter,exit,duration);
    }

    /** Construct one immutable primitive without exposing a dynamic factory. */
    private static OverlayManifest.Element element(String id,OverlayManifest.PrimitiveType type,
            OverlayManifest.Frame frame,int zIndex,float opacity,OverlayManifest.Animation animation,
            String text,String assetRef,String fit,String color,String backgroundColor,int fontSize,
            boolean bold,String textAlign,int padding,int cornerRadius,String fillColor,
            String gridColor,List<List<String>> rows,List<OverlayManifest.Element> children) {
        return new OverlayManifest.Element(id,type,frame,zIndex,opacity,animation,text,assetRef,fit,
                color,backgroundColor,fontSize,bold,textAlign,padding,cornerRadius,fillColor,
                gridColor,rows,children);
    }

    /** Require an object property. */
    private static JSONObject requireObject(JSONObject parent,String key) throws Invalid {
        JSONObject value=parent.optJSONObject(key);
        if(value==null)throw new Invalid("Missing object");
        return value;
    }

    /** Validate a bounded identifier. */
    private static String safeId(String value,String label) throws Invalid {
        if(!SAFE_ID.matcher(value).matches())throw new Invalid("Invalid "+label);
        return value;
    }

    /** Validate a CSS-style hexadecimal color only. */
    private static String color(String value) throws Invalid {
        if(!COLOR.matcher(value).matches())throw new Invalid("Invalid color");
        return value;
    }

    /** Read an exactly integral bounded number. */
    private static long exactLong(JSONObject object,String key,long min,long max) throws Invalid {
        Object raw=object.opt(key);
        if(!(raw instanceof Number))throw new Invalid("Invalid number");
        double value=((Number)raw).doubleValue();
        long integral=((Number)raw).longValue();
        if(!Double.isFinite(value)||value!=(double)integral||integral<min||integral>max)
            throw new Invalid("Invalid number");
        return integral;
    }

    /** Read an exactly integral bounded int. */
    private static int exactInt(JSONObject object,String key,int min,int max) throws Invalid {
        long value=exactLong(object,key,min,max);
        return (int)value;
    }

    /** Read a bounded finite float. */
    private static float exactFloat(JSONObject object,String key,float min,float max) throws Invalid {
        Object raw=object.opt(key);
        if(!(raw instanceof Number))throw new Invalid("Invalid float");
        double value=((Number)raw).doubleValue();
        if(!Double.isFinite(value)||value<min||value>max)throw new Invalid("Invalid float");
        return (float)value;
    }

    /** Mutable local counter used only during one parse. */
    private static final class Counter {
        int count;
    }

    /** Utility class has no mutable global state. */
    private OverlayManifestParser() {}
}
