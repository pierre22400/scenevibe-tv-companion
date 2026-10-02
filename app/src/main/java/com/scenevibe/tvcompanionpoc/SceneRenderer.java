package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Minimal generic Android interpreter for OverlayManifest v1 scenes.
 *
 * <p>This class draws the initial text/image/rectangle/table/group primitives using Android
 * Views inside TYPE_APPLICATION_OVERLAY. It never evaluates HTML/JavaScript, dynamically loads
 * renderer classes, or performs network requests. Image bytes must already exist in a trusted
 * local asset cache supplied through {@link AssetResolver}.</p>
 */
final class SceneRenderer {
    /** Local-only asset seam. Implementations must never fetch a network URL here. */
    interface AssetResolver {
        Bitmap resolve(String assetRef);
    }

    private final Context context;
    private final WindowManager windows;
    private final AssetResolver assets;
    private final Runnable permissionLost;
    private FrameLayout root;
    private WindowManager.LayoutParams windowParams;
    private float scaleX;
    private float scaleY;

    SceneRenderer(Context context,AssetResolver assets,Runnable permissionLost) {
        this.context=context;
        this.assets=assets;
        this.permissionLost=permissionLost;
        this.windows=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        if(windows==null)throw new IllegalStateException("WindowManager unavailable");
    }

    /**
     * Scene-asset preflight (section 15). Before any window mutation the regie calls this to
     * check that EVERY image element in the scene tree (including group children) resolves to
     * a locally cached bitmap. If any required asset is missing it returns false so the
     * controller suppresses the show with a bounded diagnostic
     * ({@link RuntimeDiagnostics.ManifestCode}) instead of starting a partial render that would
     * later throw in {@link #buildImage}. It performs NO network access and NO window mutation;
     * it only probes the local {@link AssetResolver}. A null scene fails closed (false).
     */
    boolean preflight(OverlayManifest.Scene scene) {
        if(scene==null)return false;
        try {
            for(String assetRef:imageAssetRefs(scene)) {
                if(assets.resolve(assetRef)==null)return false;
            }
            return true;
        } catch(RuntimeException assetFailure) {
            // A local resolver fault is a bounded scene failure, never a crash or partial render.
            return false;
        }
    }

    /**
     * Pure, Android-free collector of every IMAGE element {@code assetRef} in a scene tree,
     * walking group children recursively. Extracted so the preflight asset-check is unit
     * testable without a real {@link Context} or Android View. Order is the manifest's
     * declared element order (depth-first); it carries no scene/comment text, only bounded
     * {@code asset:} references.
     */
    static List<String> imageAssetRefs(OverlayManifest.Scene scene) {
        ArrayList<String> refs=new ArrayList<>();
        if(scene!=null)collectImageAssetRefs(scene.elements,refs);
        return refs;
    }

    /** Recursively gather image asset references from an element list and its groups. */
    private static void collectImageAssetRefs(List<OverlayManifest.Element> elements,
            List<String> into) {
        if(elements==null)return;
        for(OverlayManifest.Element element:elements) {
            if(element.type==OverlayManifest.PrimitiveType.IMAGE&&element.assetRef!=null) {
                into.add(element.assetRef);
            } else if(element.type==OverlayManifest.PrimitiveType.GROUP) {
                collectImageAssetRefs(element.children,into);
            }
        }
    }

    /**
     * Pure, Android-free stable z-order used by {@link #render}: lower zIndex first, then id as
     * a deterministic tie-break. Exposed package-private so the ordering contract can be unit
     * tested without constructing real Android Views.
     */
    static List<OverlayManifest.Element> orderedForTest(List<OverlayManifest.Element> source) {
        return ordered(source);
    }

    /** Render one already-validated scene into a full-screen transparent overlay. */
    void render(OverlayManifest.Scene scene) {
        if(!Settings.canDrawOverlays(context)) {
            permissionLost.run();
            return;
        }
        // Second line of defense behind the controller's preflight: never begin mutating the
        // window for a scene whose local assets are missing (section 15). The controller
        // already gates on preflight(), but a direct caller must not get a partial render.
        if(!preflight(scene))return;
        ensureWindow();
        // A previous scene may still be running its bounded fade-out with a withEndAction
        // that removes the shared root window. Cancel it before reusing that same root for a
        // replacement scene; Android guarantees a canceled ViewPropertyAnimator does not run
        // its withEndAction. Without this, a fast scene/revision replacement could render the
        // new scene and then have the OLD fade remove its window a few milliseconds later.
        root.animate().cancel();
        root.setAlpha(1f);
        root.removeAllViews();
        for(OverlayManifest.Element element:ordered(scene.elements)) {
            root.addView(build(element));
        }
    }

    /** Apply the scene's bounded exit animation, then remove the overlay window. */
    void dismiss(OverlayManifest.Scene scene) {
        if(root==null)return;
        int fadeDuration=maxExitFade(scene.elements);
        if(fadeDuration>0) {
            root.animate().cancel();
            root.animate().alpha(0f).setDuration(fadeDuration).withEndAction(this::removeNow).start();
        } else {
            removeNow();
        }
    }

    /** Immediately remove all SceneRenderer views and release the overlay window. */
    void dismissNow() {
        removeNow();
    }

    /** Create a transparent full-screen overlay and scale logical 1920x1080 scene units. */
    private void ensureWindow() {
        if(root!=null)return;
        DisplayMetrics metrics=context.getResources().getDisplayMetrics();
        scaleX=metrics.widthPixels/(float)OverlayManifest.CANVAS_WIDTH;
        scaleY=metrics.heightPixels/(float)OverlayManifest.CANVAS_HEIGHT;

        root=new FrameLayout(context);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setBackgroundColor(Color.TRANSPARENT);

        windowParams=new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        |WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        |WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        windowParams.gravity=Gravity.TOP|Gravity.START;
        windowParams.setTitle("SceneVibe SceneRenderer");
        windows.addView(root,windowParams);
    }

    /** Build one allow-listed primitive recursively. */
    private View build(OverlayManifest.Element element) {
        View view;
        switch(element.type) {
            case TEXT:
                view=buildText(element);
                break;
            case IMAGE:
                view=buildImage(element);
                break;
            case RECTANGLE:
                view=buildRectangle(element);
                break;
            case TABLE:
                view=buildTable(element);
                break;
            case GROUP:
                view=buildGroup(element);
                break;
            default:
                throw new IllegalArgumentException("Unsupported primitive");
        }
        view.setAlpha(element.opacity);
        applyFrame(view,element.frame);
        applyEnterAnimation(view,element);
        return view;
    }

    /** Render a text primitive with bounded typography and background. */
    private TextView buildText(OverlayManifest.Element element) {
        TextView text=new TextView(context);
        text.setText(element.text);
        text.setTextColor(Color.parseColor(element.color));
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX,element.fontSize*scaleY);
        text.setTypeface(Typeface.DEFAULT,element.bold?Typeface.BOLD:Typeface.NORMAL);
        text.setGravity(textGravity(element.textAlign));
        int padding=px(element.padding);
        text.setPadding(padding,padding,padding,padding);
        text.setBackground(roundRect(element.backgroundColor,element.cornerRadius));
        return text;
    }

    /** Render an image only from the caller-provided local asset cache. */
    private ImageView buildImage(OverlayManifest.Element element) {
        Bitmap bitmap=assets.resolve(element.assetRef);
        if(bitmap==null)throw new IllegalStateException("Overlay asset unavailable");
        ImageView image=new ImageView(context);
        image.setImageBitmap(bitmap);
        image.setAdjustViewBounds(false);
        image.setScaleType("cover".equals(element.fit)
                ?ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.CENTER_INSIDE);
        return image;
    }

    /** Render a filled rounded rectangle. */
    private View buildRectangle(OverlayManifest.Element element) {
        View rectangle=new View(context);
        rectangle.setBackground(roundRect(element.fillColor,element.cornerRadius));
        return rectangle;
    }

    /** Render a bounded rectangular string table with equal-width cells. */
    private TableLayout buildTable(OverlayManifest.Element element) {
        TableLayout table=new TableLayout(context);
        table.setStretchAllColumns(true);
        table.setBackgroundColor(Color.parseColor(element.backgroundColor));
        for(List<String> rowData:element.rows) {
            TableRow row=new TableRow(context);
            for(String value:rowData) {
                TextView cell=new TextView(context);
                cell.setText(value);
                cell.setTextColor(Color.parseColor(element.color));
                cell.setTextSize(TypedValue.COMPLEX_UNIT_PX,element.fontSize*scaleY);
                cell.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
                int padding=px(element.padding);
                cell.setPadding(padding,padding,padding,padding);
                GradientDrawable cellBackground=new GradientDrawable();
                cellBackground.setColor(Color.parseColor(element.backgroundColor));
                cellBackground.setStroke(Math.max(1,px(1)),Color.parseColor(element.gridColor));
                cell.setBackground(cellBackground);
                row.addView(cell,new TableRow.LayoutParams(0,
                        TableRow.LayoutParams.MATCH_PARENT,1f));
            }
            table.addView(row,new TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,0,1f));
        }
        return table;
    }

    /** Render a group as a transparent FrameLayout containing relative child frames. */
    private FrameLayout buildGroup(OverlayManifest.Element element) {
        FrameLayout group=new FrameLayout(context);
        group.setClipChildren(false);
        group.setClipToPadding(false);
        for(OverlayManifest.Element child:ordered(element.children)) {
            group.addView(build(child));
        }
        return group;
    }

    /** Apply logical scene coordinates to one Android View. */
    private void applyFrame(View view,OverlayManifest.Frame frame) {
        FrameLayout.LayoutParams layout=new FrameLayout.LayoutParams(
                Math.max(1,Math.round(frame.width*scaleX)),
                Math.max(1,Math.round(frame.height*scaleY)));
        layout.leftMargin=Math.round(frame.x*scaleX);
        layout.topMargin=Math.round(frame.y*scaleY);
        view.setLayoutParams(layout);
    }

    /** Apply a bounded fade-in; all other enter modes are immediate. */
    private void applyEnterAnimation(View view,OverlayManifest.Element element) {
        if(element.animation==null||!"fade".equals(element.animation.enter)
                ||element.animation.durationMs<=0)return;
        float target=element.opacity;
        view.setAlpha(0f);
        view.animate().alpha(target).setDuration(element.animation.durationMs).start();
    }

    /** Stable z-order: lower zIndex is added first, then id as deterministic tie-break. */
    private static List<OverlayManifest.Element> ordered(List<OverlayManifest.Element> source) {
        ArrayList<OverlayManifest.Element> ordered=new ArrayList<>(source);
        ordered.sort(Comparator.comparingInt((OverlayManifest.Element item)->item.zIndex)
                .thenComparing(item->item.id));
        return ordered;
    }

    /** Find the longest requested exit fade in this scene tree. */
    private static int maxExitFade(List<OverlayManifest.Element> elements) {
        int maximum=0;
        for(OverlayManifest.Element element:elements) {
            if(element.animation!=null&&"fade".equals(element.animation.exit))
                maximum=Math.max(maximum,element.animation.durationMs);
            if(element.type==OverlayManifest.PrimitiveType.GROUP)
                maximum=Math.max(maximum,maxExitFade(element.children));
        }
        return maximum;
    }

    /** Convert a logical corner radius/padding unit to physical pixels. */
    private int px(int logical) {
        return Math.max(0,Math.round(logical*Math.min(scaleX,scaleY)));
    }

    /** Create a bounded rounded-color background. */
    private GradientDrawable roundRect(String color,int radius) {
        GradientDrawable background=new GradientDrawable();
        background.setColor(Color.parseColor(color));
        background.setCornerRadius(px(radius));
        return background;
    }

    /** Map the manifest's portable alignment names to Android gravity. */
    private static int textGravity(String align) {
        if("center".equals(align))return Gravity.CENTER;
        if("end".equals(align))return Gravity.CENTER_VERTICAL|Gravity.END;
        return Gravity.CENTER_VERTICAL|Gravity.START;
    }

    /** Remove the full-screen overlay safely. */
    private void removeNow() {
        if(root==null)return;
        try {
            root.animate().cancel();
            windows.removeViewImmediate(root);
        } catch(IllegalArgumentException ignored) {
            // Already detached: idempotent cleanup.
        } finally {
            root=null;
            windowParams=null;
        }
    }
}
