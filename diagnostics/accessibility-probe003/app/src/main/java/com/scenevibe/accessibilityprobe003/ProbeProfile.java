package com.scenevibe.accessibilityprobe003;

/** Closed package allowlist; one profile is immutable during a collection campaign. */
public enum ProbeProfile {
    OWN_CONTROL("com.scenevibe.accessibilityprobe003"),
    NETFLIX("com.netflix.ninja"),
    DISNEY_PLUS("com.disney.disneyplus");

    public final String packageName;

    /** Associate a compiled public package constant with the named profile. */
    ProbeProfile(String packageName) { this.packageName = packageName; }

    /** Compare a supplied package without retaining arbitrary application strings. */
    public boolean accepts(CharSequence value) {
        return value != null && packageName.contentEquals(value);
    }
}
