package com.scenevibe.accessibilityprobe004;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

/**
 * App-owned positive control. Its public virtual children change without sending accessibility
 * events. A fresh remote query can therefore see a new category/structure while a callback-only
 * observer remains silent. This is synthetic instrumentation, never a Netflix observation.
 */
public final class SilentFixtureView extends View {
    private volatile int phase;
    private final Paint paint = new Paint();
    private final Provider provider = new Provider();

    /** Create a visible own-app virtual hierarchy, with no focus request or external action. */
    public SilentFixtureView(Context context) {
        super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        paint.setColor(Color.BLACK); paint.setTextSize(24);
        setMinimumHeight(100);
    }

    /** Change internal provider state without notify/invalidate/sendAccessibilityEvent calls. */
    public void setSilentPhase(int value) { phase = value == 0 ? 0 : 1; }

    /** Draw only the owned fixture explanation; no platform pixels are read or captured. */
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.drawText("Contrôle virtuel synthétique — lecture par la sonde", 8, 40, paint);
    }

    /** Expose the public provider used by the same Android accessibility APIs as real targets. */
    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() { return provider; }

    /** Describe this host plus its virtual child; changing phase never emits an event itself. */
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.FrameLayout");
        info.addChild(this, 1);
        if (phase != 0) info.addChild(this, 2);
    }

    /** Public virtual-node provider; it implements no performAction, gesture or input capability. */
    private final class Provider extends AccessibilityNodeProvider {
        /** Bind only to the enclosing owned View; no cross-app reference is retained. */
        Provider() { }

        /** Return current virtual properties on every fresh request, without pushing callbacks. */
        @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualId) {
            if (virtualId == HOST_VIEW_ID) {
                AccessibilityNodeInfo host = AccessibilityNodeInfo.obtain(SilentFixtureView.this);
                onInitializeAccessibilityNodeInfo(host); return host;
            }
            if (virtualId != 1 && !(virtualId == 2 && phase != 0)) return null;
            AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
            info.setSource(SilentFixtureView.this, virtualId); info.setParent(SilentFixtureView.this);
            info.setPackageName(getContext().getPackageName()); info.setClassName("android.widget.TextView");
            info.setText(phase == 0 ? "Programme synthétique" : "Publicité");
            info.setContentDescription(phase == 0 ? "Contrôle synthétique" : "Publicité 1 sur 2");
            info.setViewIdResourceName(getContext().getPackageName() + ":id/silent_control");
            info.setEnabled(true); info.setVisibleToUser(true);
            Rect bounds = new Rect(0, 0, Math.max(1, getWidth()), Math.max(1, getHeight()));
            info.setBoundsInParent(bounds); int[] location = new int[2]; getLocationOnScreen(location);
            bounds.offset(location[0], location[1]); info.setBoundsInScreen(bounds);
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, 0, 100, phase == 0 ? 10 : 40));
            return info;
        }
    }
}
