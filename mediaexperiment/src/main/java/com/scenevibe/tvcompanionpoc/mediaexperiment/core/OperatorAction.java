package com.scenevibe.tvcompanionpoc.mediaexperiment.core;

/**
 * ADB-only diagnostic action names. Unknown or absent requests fail closed:
 * opening the launcher with no explicit action is observation-only.
 *
 * <p>The host Android service is intentionally NOT exported. The exported
 * translucent launcher must remain strictly experimental/debug-only.</p>
 */
public enum OperatorAction {
    NONE(""), SCAN("scan"), VOICE("voice"), VIDEO_10S("video10"),
    PAUSE_ONLY("pause"), LEGACY_INTERLUDE("interlude"), DUCK("duck"),
    STOP("stop"), HIDE("hide");

    private final String wire;

    OperatorAction(String wire) {
        this.wire = wire;
    }

    public static OperatorAction parse(String value) {
        if (value == null) return NONE;
        for (OperatorAction action : values()) {
            if (action != NONE && action.wire.equals(value)) return action;
        }
        return NONE;
    }

    public String wireName() {
        return wire;
    }

    public boolean isEmergency() {
        return this == STOP || this == HIDE;
    }
}
