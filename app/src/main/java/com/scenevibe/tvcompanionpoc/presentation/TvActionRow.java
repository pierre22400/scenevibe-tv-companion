package com.scenevibe.tvcompanionpoc.presentation;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

/** Native two-action wrapping row, matching v0 flex-wrap without scaling a Web stage. */
public final class TvActionRow extends LinearLayout {
    private final int gap;

    /** Preserves room for external focus outlines between neighboring actions. */
    public TvActionRow(Context context) {
        super(context);
        gap = Math.round(12 * context.getResources().getDisplayMetrics().density);
        setClipChildren(false); setClipToPadding(false);
    }

    /** Chooses a row only when all buttons fit at their actual font scale, otherwise a column. */
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int available = MeasureSpec.getSize(widthSpec), wanted = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            wanted += child.getMeasuredWidth() + (i == 0 ? 0 : gap);
        }
        boolean horizontal = wanted <= available;
        setOrientation(horizontal ? HORIZONTAL : VERTICAL);
        for (int i = 0; i < getChildCount(); i++) {
            LayoutParams p = (LayoutParams) getChildAt(i).getLayoutParams();
            p.leftMargin = horizontal && i > 0 ? gap : 0;
            p.topMargin = !horizontal && i > 0 ? gap : 0;
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
