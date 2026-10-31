package com.scenevibe.accessibilityprobe004;

/**
 * Irreversible minimal projection of one fresh node. No text, resource ID, title, stable hash
 * or class name is retained. Range numbers remain unit UNKNOWN, including normalized ranges.
 */
final class NodeFacts {
    final int children;
    final SafeRow.Role role;
    final SafeRow.ViewKind view;
    final boolean visible, focused, selected, enabled, checked, password, text, description;
    final SafeRow.Label textLabel, descriptionLabel;
    final SafeRow.RangeState range;
    final int rangeType;
    final double minimum, current, maximum;

    /** Copy only allowed scalars; range validity and password suppression are enforced here. */
    NodeFacts(int children, SafeRow.Role role, SafeRow.ViewKind view, boolean visible,
            boolean focused, boolean selected, boolean enabled, boolean checked, boolean password,
            boolean text, boolean description, SafeRow.Label textLabel, SafeRow.Label descriptionLabel,
            boolean rangePresent, int rangeType, double minimum, double current, double maximum) {
        this.children = Math.max(0, children); this.role = role; this.view = view;
        this.visible = visible; this.focused = focused; this.selected = selected;
        this.enabled = enabled; this.checked = checked; this.password = password;
        this.text = !password && text; this.description = !password && description;
        this.textLabel = password ? SafeRow.Label.PASSWORD_SKIPPED : textLabel;
        this.descriptionLabel = password ? SafeRow.Label.PASSWORD_SKIPPED : descriptionLabel;
        this.rangeType = rangeType; this.minimum = minimum; this.current = current; this.maximum = maximum;
        this.range = password ? SafeRow.RangeState.PASSWORD_SKIPPED : !rangePresent ? SafeRow.RangeState.ABSENT
            : Double.isFinite(minimum) && Double.isFinite(current) && Double.isFinite(maximum)
              && minimum <= current && current <= maximum && rangeType >= 0 && rangeType <= 2
                ? SafeRow.RangeState.VALID : SafeRow.RangeState.INVALID;
    }

    /** Append safe properties to a contextual node row; invalid numeric values never reach JSON. */
    SafeRow append(SafeRow row) {
        row.number("childCount", children).category("classCategory", role).category("viewKind", view)
            .flag("visible", visible).flag("focused", focused).flag("selected", selected)
            .flag("enabled", enabled).flag("checked", checked).flag("password", password)
            .flag("textPresent", text).flag("descriptionPresent", description)
            .category("labelCategory", textLabel).category("descriptionCategory", descriptionLabel)
            .category("rangeState", range).category("rangeUnit", SafeRow.Unit.UNKNOWN);
        if (range == SafeRow.RangeState.VALID) row.number("rangeType", rangeType)
            .decimal("rangeMin", minimum).decimal("rangeCurrent", current).decimal("rangeMax", maximum);
        return row;
    }

    /** Produce an in-memory structural token without content strings or stable identifiers. */
    String shape(int parent, int depth) {
        return parent + ":" + depth + ":" + children + ":" + role + ":" + view + ":"
            + visible + ":" + focused + ":" + selected + ":" + enabled + ":" + checked + ":"
            + text + ":" + description + ":" + textLabel + ":" + descriptionLabel + ":" + range;
    }
}
