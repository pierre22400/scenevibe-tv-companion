package com.scenevibe.tvcompanionpoc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable, bounded in-memory representation of scenevibe.overlay-manifest.v1.
 *
 * <p>The model is deliberately product-agnostic: Video, Banner and future products all
 * become timed scenes made of the same initial primitive family. Android is responsible
 * for drawing those primitives; this class contains no executable renderer references.</p>
 */
final class OverlayManifest {
    static final String TYPE="scenevibe.overlay-manifest.v1";
    static final String SCHEMA_VERSION="1.0.0";
    static final int CANVAS_WIDTH=1920;
    static final int CANVAS_HEIGHT=1080;

    final String manifestId;
    final String product;
    final String sourceId;
    final String clockMode;
    final String pauseBehavior;
    final List<Scene> scenes;

    OverlayManifest(String manifestId,String product,String sourceId,String clockMode,
            String pauseBehavior,List<Scene> scenes) {
        this.manifestId=manifestId;
        this.product=product;
        this.sourceId=sourceId;
        this.clockMode=clockMode;
        this.pauseBehavior=pauseBehavior;
        this.scenes=immutable(scenes);
    }

    /** One timed scene rendered by the Companion's local regie. */
    static final class Scene {
        final String id;
        final long startMs;
        final long durationMs;
        final List<Element> elements;

        Scene(String id,long startMs,long durationMs,List<Element> elements) {
            this.id=id;
            this.startMs=startMs;
            this.durationMs=durationMs;
            this.elements=immutable(elements);
        }
    }

    /** Supported first-generation primitive kinds. */
    enum PrimitiveType {
        TEXT,
        IMAGE,
        RECTANGLE,
        TABLE,
        GROUP
    }

    /** Logical 1920x1080 frame; group-child frames are relative to their parent. */
    static final class Frame {
        final int x;
        final int y;
        final int width;
        final int height;

        Frame(int x,int y,int width,int height) {
            this.x=x;
            this.y=y;
            this.width=width;
            this.height=height;
        }
    }

    /** Bounded first-generation animation metadata. */
    static final class Animation {
        final String enter;
        final String exit;
        final int durationMs;

        Animation(String enter,String exit,int durationMs) {
            this.enter=enter;
            this.exit=exit;
            this.durationMs=durationMs;
        }
    }

    /**
     * Generic primitive snapshot. Only fields relevant to {@link #type} are populated.
     * This keeps the parser/renderer boundary small without introducing dynamic classes.
     */
    static final class Element {
        final String id;
        final PrimitiveType type;
        final Frame frame;
        final int zIndex;
        final float opacity;
        final Animation animation;
        final String text;
        final String assetRef;
        final String fit;
        final String color;
        final String backgroundColor;
        final int fontSize;
        final boolean bold;
        final String textAlign;
        final int padding;
        final int cornerRadius;
        final String fillColor;
        final String gridColor;
        final List<List<String>> rows;
        final List<Element> children;

        Element(String id,PrimitiveType type,Frame frame,int zIndex,float opacity,
                Animation animation,String text,String assetRef,String fit,String color,
                String backgroundColor,int fontSize,boolean bold,String textAlign,int padding,
                int cornerRadius,String fillColor,String gridColor,List<List<String>> rows,
                List<Element> children) {
            this.id=id;
            this.type=type;
            this.frame=frame;
            this.zIndex=zIndex;
            this.opacity=opacity;
            this.animation=animation;
            this.text=text;
            this.assetRef=assetRef;
            this.fit=fit;
            this.color=color;
            this.backgroundColor=backgroundColor;
            this.fontSize=fontSize;
            this.bold=bold;
            this.textAlign=textAlign;
            this.padding=padding;
            this.cornerRadius=cornerRadius;
            this.fillColor=fillColor;
            this.gridColor=gridColor;
            this.rows=immutableRows(rows);
            this.children=immutable(children);
        }
    }

    /** Defensive immutable copy helper. */
    private static <T> List<T> immutable(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    /** Defensive immutable copy for table rows. */
    private static List<List<String>> immutableRows(List<List<String>> source) {
        ArrayList<List<String>> copy=new ArrayList<>();
        for(List<String> row:source)copy.add(Collections.unmodifiableList(new ArrayList<>(row)));
        return Collections.unmodifiableList(copy);
    }
}
