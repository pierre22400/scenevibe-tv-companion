package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.Checkable;

/**
 * Shared TV developer button: requested ON/OFF, availability and D-pad focus
 * are three independent states. ON keeps a red inner border on a light fill;
 * focus adds a black outer ring without changing the switch. Actions are never
 * advertised as checkable. Literal labels and accessibility state descriptions
 * keep the controls understandable without distinguishing any color.
 */
final class DeveloperButton extends Button implements Checkable {
    static final int ON_FILL=0xFFFFF4EF,ON_BORDER=0xFFB3261E,OFF_FILL=Color.WHITE;
    static final int UNAVAILABLE_FILL=0xFFE7E7E7,NEUTRAL_BORDER=0xFF777777;
    private boolean toggle,checked,initialized;

    /** Build one native remote-focusable button without changing any business state. */
    DeveloperButton(Context context) {
        super(context);setAllCaps(false);setTextSize(18);setFocusable(true);
        setMinHeight(0);setMinimumHeight(0);setPadding(dp(12),dp(5),dp(12),dp(5));
        initialized=true;refreshDrawableState();
    }
    /** Mark only real switches as checkable, retaining a requested value even when unavailable. */
    void switchState(boolean value){toggle=true;setChecked(value);refreshDescription();}
    /** Read the independent ON/OFF state; focus never writes it. */
    @Override public boolean isChecked(){return checked;}
    /** Update only this presentation projection, never the service preference. */
    @Override public void setChecked(boolean value){if(checked!=value){checked=value;refreshDrawableState();}refreshDescription();}
    /** Support standard Checkable semantics without silently invoking a business command. */
    @Override public void toggle(){setChecked(!checked);}
    /** Recompute availability without replacing the native focus target. */
    @Override public void setEnabled(boolean value){super.setEnabled(value);if(initialized)refreshDescription();}
    /** Expose the checked state separately from the focused and enabled framework states. */
    @Override protected int[] onCreateDrawableState(int extra) {
        int[] state=super.onCreateDrawableState(extra+1);
        if(toggle && checked)mergeDrawableStates(state,new int[]{android.R.attr.state_checked});return state;
    }
    /** Keep the inner activation border visible while focus adds an independent black outer ring. */
    @Override protected void drawableStateChanged() {
        super.drawableStateChanged();if(!initialized)return;
        GradientDrawable outer=shape(Color.TRANSPARENT,isFocused()?Color.BLACK:Color.TRANSPARENT,isFocused()?3:0);
        int fill=!isEnabled()?UNAVAILABLE_FILL:toggle && checked?ON_FILL:OFF_FILL;
        int border=isEnabled() && toggle && checked?ON_BORDER:NEUTRAL_BORDER;
        GradientDrawable inner=shape(fill,border,toggle && checked && isEnabled()?2:1);
        LayerDrawable layers=new LayerDrawable(new android.graphics.drawable.Drawable[]{outer,inner});
        layers.setLayerInset(1,dp(6),dp(6),dp(6),dp(6));setBackground(layers);
        setTextColor(isEnabled()?Color.BLACK:0xFF555555);
    }
    /** Describe availability in words while avoiding repeated accessibility events for an unchanged state. */
    private void refreshDescription() {
        if(!initialized || android.os.Build.VERSION.SDK_INT<30)return;
        String value=(toggle?(checked?"ON":"OFF"):"Action")+(isEnabled()?"":" · indisponible");
        if(!value.contentEquals(getStateDescription()==null?"":getStateDescription()))setStateDescription(value);
    }
    /** Present switches as switches, actions as buttons, and unavailable commands as disabled. */
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);info.setCheckable(toggle);info.setChecked(toggle && checked);
        if(toggle)info.setClassName("android.widget.Switch");
    }
    /** Build the two independent visual borders without relying on a color-only focus cue. */
    private GradientDrawable shape(int fill,int stroke,int width) {
        GradientDrawable result=new GradientDrawable();result.setColor(fill);result.setCornerRadius(dp(6));result.setStroke(dp(width),stroke);return result;
    }
    /** Scale outlines with the TV's configured density. */
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
