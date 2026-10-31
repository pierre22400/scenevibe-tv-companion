package com.scenevibe.accessibilityprobe004;

import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Public-API31 read adapter. The engine refreshes every interpreted node. Text is transiently
 * projected; password nodes suppress text, description and range payload before accessing it.
 */
final class AndroidSurveyNode implements SurveyNode {
    private final AccessibilityNodeInfo node;
    /** Own one obtained handle, recycled exactly once by the traversal. */
    AndroidSurveyNode(AccessibilityNodeInfo node) { this.node = node; }
    /** Return the transient package solely for admission. */
    @Override public CharSequence packageName() { return node.getPackageName(); }
    /** Refresh this node using only the public API31 method. */
    @Override public boolean refresh() { return node.refresh(); }
    /** Build allowed scalar categories without retaining private strings. */
    @Override public NodeFacts facts(ProbeProfile profile, TextProjection projection) {
        boolean password = node.isPassword();
        CharSequence text = password ? null : node.getText();
        CharSequence description = password ? null : node.getContentDescription();
        AccessibilityNodeInfo.RangeInfo range = password ? null : node.getRangeInfo();
        return new NodeFacts(node.getChildCount(), projection.role(node.getClassName()),
            projection.viewKind(node.getViewIdResourceName(), profile), node.isVisibleToUser(),
            node.isFocused(), node.isSelected(), node.isEnabled(), node.isChecked(), password,
            text != null && text.length() > 0, description != null && description.length() > 0,
            projection.classify(text, password), projection.classify(description, password),
            range != null, range == null ? -1 : range.getType(), range == null ? 0 : range.getMin(),
            range == null ? 0 : range.getCurrent(), range == null ? 0 : range.getMax());
    }
    /** Obtain a child without interpreting its properties before its own refresh. */
    @Override public SurveyNode child(int index) {
        AccessibilityNodeInfo child = node.getChild(index);
        return child == null ? null : new AndroidSurveyNode(child);
    }
    /** Release the owned Android handle. */
    @Override public void close() { node.recycle(); }
}

