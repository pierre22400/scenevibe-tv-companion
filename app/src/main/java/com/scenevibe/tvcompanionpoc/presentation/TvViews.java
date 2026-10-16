package com.scenevibe.tvcompanionpoc.presentation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.scenevibe.tvcompanionpoc.R;

/** Native primitives for the frozen cream/green composition; all dimensions remain dp/sp. */
public final class TvViews {
    private final Context context;

    /** Uses the hosting Activity's density and fonts, with no fixed bitmap stage. */
    public TvViews(Context context) { this.context = context; }

    /** Converts a reference dp dimension using Android density. */
    public int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    /** Resolves a single converted sRGB token. */
    public int color(int id) { return context.getColor(id); }

    /** Creates an unclipped native layout so external focus rings remain visible. */
    public LinearLayout column() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setClipChildren(false);
        layout.setClipToPadding(false);
        return layout;
    }

    /** Creates a horizontal layout with native measurement rather than TvStage scaling. */
    public LinearLayout row() {
        LinearLayout layout = column();
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    /** Applies the original font, exact variable weight and the chosen typographic role. */
    public void typography(TextView view, float sp, boolean heading, int weight) {
        // Reapplying a font must also reapply its axes: TextView caches an equal axis string.
        view.setFontVariationSettings(null);
        view.setTypeface(context.getResources().getFont(heading
                ? R.font.scenevibe_spacegrotesk : R.font.scenevibe_inter));
        view.setFontVariationSettings("'wght' " + weight + (heading ? "" : ", 'opsz' " + Math.max(14, Math.min(32, sp * 2))));
        view.setTextSize(sp);
        view.setIncludeFontPadding(false);
        view.setTextColor(color(R.color.scenevibe_foreground));
        view.setLineSpacing(0, heading ? 1.12f : 1.55f);
        view.setMinimumHeight(Math.round(sp * context.getResources().getDisplayMetrics().scaledDensity * (heading ? 1.12f : 1.55f)));
        if (heading) view.setLetterSpacing(-.03f);
    }

    /** Makes body text naturally wrap and remain readable by Android accessibility services. */
    public TextView text(CharSequence text, float sp, boolean heading, int weight, int color) {
        TextView view = new TextView(context);
        typography(view, sp, heading, weight);
        view.setText(text);
        view.setTextColor(color(color));
        if (heading && android.os.Build.VERSION.SDK_INT >= 28) view.setAccessibilityHeading(true);
        return view;
    }

    /** Uses an actual Android button: OK clicks; focus never invokes its action. */
    public Button button(String label, boolean primary, Runnable action) {
        Button button = new Button(context);
        typography(button, 14, false, 600);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(20), dp(10), dp(20), dp(10));
        button.setGravity(Gravity.CENTER);
        button.setStateListAnimator(null);
        button.setBackgroundTintList(null);
        button.setTextColor(buttonTextColors(primary));
        button.setBackground(focusBackground(primary ? R.color.scenevibe_primary : R.color.scenevibe_card,
                primary ? R.color.scenevibe_primary : R.color.scenevibe_public_action_border, 8, false));
        button.setFocusable(true);
        button.setFocusableInTouchMode(false);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    /** Disabled text stays readable on its neutral fill, without implying an available action. */
    public android.content.res.ColorStateList buttonTextColors(boolean primary) {
        return new android.content.res.ColorStateList(new int[][] {
                new int[] {-android.R.attr.state_enabled}, new int[] {}}, new int[] {
                color(R.color.scenevibe_muted_foreground),
                color(primary ? R.color.scenevibe_primary_foreground : R.color.scenevibe_foreground)});
    }

    /** Creates a rounded panel using the shared resources. */
    public Drawable panel(int fill, int border, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(fill));
        drawable.setCornerRadius(dp(radius));
        if (border != 0) drawable.setStroke(dp(1), color(border));
        return drawable;
    }

    /** Adds the reference's green left rule without changing panel content or focus behavior. */
    public Drawable accentPanel(int fill, float radius) {
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                new Drawable[] {panel(fill, R.color.scenevibe_border, radius), panel(R.color.scenevibe_primary, 0, 2)});
        layers.setLayerWidth(1, dp(4));
        layers.setLayerGravity(1, Gravity.START | Gravity.FILL_VERTICAL);
        layers.setLayerInset(1, 0, dp(3), 0, dp(3));
        return layers;
    }

    /** Provides a stateful focus ring with a 3dp stroke, 3dp gap and no layout resizing. */
    public Drawable focusBackground(int fill, int border, float radius, boolean selected) {
        return new FocusBackground(color(fill), border == 0 ? 0 : color(border), dp(radius),
                color(R.color.scenevibe_primary), color(R.color.scenevibe_muted), dp(3), selected);
    }

    /** Focus rings are decorative; semantic selection remains a separate View state. */
    private static final class FocusBackground extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int fill, border, green, disabledFill, stroke;
        private final float radius;
        private final boolean selected;
        private boolean focused;
        private boolean enabled = true;

        /** Keeps colors immutable and tracks only focus through drawable state. */
        FocusBackground(int fill, int border, float radius, int green, int disabledFill, int stroke, boolean selected) {
            this.fill = fill; this.border = border; this.radius = radius;
            this.green = green; this.disabledFill = disabledFill; this.stroke = stroke; this.selected = selected;
        }

        /** Draws the accepted card/button and an external focus ring without scaling it. */
        @Override public void draw(Canvas canvas) {
            RectF rect = new RectF(getBounds());
            paint.setStyle(Paint.Style.FILL); paint.setColor(enabled ? fill : disabledFill);
            canvas.drawRoundRect(rect, radius, radius, paint);
            if (border != 0) {
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(1, stroke / 3f));
                paint.setColor(border); rect.inset(stroke / 6f, stroke / 6f);
                canvas.drawRoundRect(rect, radius, radius, paint);
            }
            if (selected) {
                paint.setStyle(Paint.Style.FILL); paint.setColor(green);
                canvas.drawRoundRect(0, radius, stroke, getBounds().bottom - radius, stroke, stroke, paint);
            }
            if (focused && enabled) {
                rect.set(getBounds()); rect.inset(-stroke * 1.5f, -stroke * 1.5f);
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(stroke); paint.setColor(green);
                canvas.drawRoundRect(rect, radius + stroke * 1.5f, radius + stroke * 1.5f, paint);
            }
        }

        /** Tells Android that focus changes require a redraw. */
        @Override public boolean isStateful() { return true; }

        /** Supplies Android's native card shadow outline without clipping the external focus ring. */
        @Override public void getOutline(Outline outline) { outline.setRoundRect(getBounds(), radius); }

        /** Updates the ring without initiating any application action. */
        @Override protected boolean onStateChange(int[] states) {
            boolean next = false, nextEnabled = false;
            for (int state : states) {
                if (state == android.R.attr.state_focused) next = true;
                if (state == android.R.attr.state_enabled) nextEnabled = true;
            }
            if (focused == next && enabled == nextEnabled) return false;
            focused = next; enabled = nextEnabled; invalidateSelf(); return true;
        }

        /** Preserves opaque token colors; disabled controls use explicit presentation copy. */
        @Override public void setAlpha(int alpha) { }
        /** No external filter may silently replace the reference palette. */
        @Override public void setColorFilter(ColorFilter filter) { }
        /** Rounded corners and the external ring have transparent surroundings. */
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
