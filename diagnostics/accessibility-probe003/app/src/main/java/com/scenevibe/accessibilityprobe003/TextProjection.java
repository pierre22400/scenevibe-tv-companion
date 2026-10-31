package com.scenevibe.accessibilityprobe003;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reduces transient text to a closed grammar. Candidates are not ad detection. Unknown IDs
 * get bounded per-campaign integer references; their raw values never leave this object.
 */
public final class TextProjection {
    private static final Pattern AD = Pattern.compile(
        "^(publicit[eé]s?|advertisements?|ads?)(?:\\s+\\d{1,2}\\s*(?:/|sur|of)\\s*\\d{1,2})?$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern COUNTDOWN = Pattern.compile("^[0-9]{1,2}:[0-5][0-9]$");
    private final Map<String, Integer> refs = new HashMap<>();

    /** Return only a category; reject password and long values before reading their characters. */
    public SafeRow.Label classify(CharSequence value, boolean password) {
        if (password) return SafeRow.Label.PASSWORD_SKIPPED;
        if (value == null || value.length() == 0) return SafeRow.Label.NONE;
        if (value.length() > ProbeLimits.TEXT_CHARS) return SafeRow.Label.TOO_LONG;
        String transientValue = value.toString().trim();
        if (AD.matcher(transientValue).matches()) return SafeRow.Label.AD_LABEL_CANDIDATE;
        if (COUNTDOWN.matcher(transientValue).matches()) return SafeRow.Label.COUNTDOWN_CANDIDATE;
        return SafeRow.Label.OTHER;
    }

    /** Return a non-stable integer for a bounded ID or class; zero means unavailable/limit. */
    public int reference(CharSequence value) {
        if (value == null || value.length() == 0 || value.length() > ProbeLimits.TEXT_CHARS) return 0;
        String key = value.toString();
        Integer prior = refs.get(key);
        if (prior != null) return prior;
        if (refs.size() >= 256) return 0;
        int ref = refs.size() + 1;
        refs.put(key, ref);
        return ref;
    }

    /** Classify only known platform widget names; no arbitrary class string is exported. */
    public SafeRow.Role role(CharSequence value) {
        if (value == null || value.length() > ProbeLimits.TEXT_CHARS) return SafeRow.Role.UNKNOWN;
        String name = value.toString();
        if (name.equals("android.widget.TextView")) return SafeRow.Role.TEXT;
        if (name.equals("android.widget.Button")) return SafeRow.Role.BUTTON;
        if (name.equals("android.widget.ImageView")) return SafeRow.Role.IMAGE;
        if (name.equals("android.widget.FrameLayout") || name.equals("android.view.ViewGroup"))
            return SafeRow.Role.CONTAINER;
        return SafeRow.Role.UNKNOWN;
    }

    /** Forget all transient identifiers at each campaign boundary or OFF. */
    public void clear() { refs.clear(); }
}
