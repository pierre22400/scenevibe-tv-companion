package com.scenevibe.tvcompanionpoc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reconstruct activity state from a retained lifecycle epoch instead of expiring a RESUMED.
 * A startup, shutdown, screen-off or keyguard boundary first invalidates all prior ownership.
 * A subsequent explicit RESUMED is required. Missing boundaries, malformed order, duplicate
 * resumes and unmatched departures during ownership refuse. Nothing survives this query.
 * Public package/class identities distinguish activities; hidden instance IDs are not used.
 */
final class NativeForegroundHistory {
    private final Map<String, Boolean> activities = new HashMap<>();
    private final Map<String, String> packages = new HashMap<>();
    private long lastTimestamp = -1;
    private boolean boundary;
    private boolean invalid;
    private boolean orderInvalid;

    /** Consume public UsageEvents primitives; unrelated events cannot create ownership. */
    void event(int type, String pkg, String activity, long timestamp) {
        if (timestamp < 0 || timestamp < lastTimestamp) {
            orderInvalid = true;
            return;
        }
        lastTimestamp = timestamp;
        if (type == 16 || type == 17 || type == 26 || type == 27) {
            activities.clear();
            packages.clear();
            boundary = true;
            invalid = false;
            // A lifecycle boundary cannot repair a malformed query ordering.
            return;
        }
        if (type != 1 && type != 2 && type != 23) return;
        if (pkg == null || pkg.isEmpty() || activity == null || activity.isEmpty()) {
            invalid = true;
            return;
        }
        String key = pkg + '\0' + activity;
        Boolean previous = activities.get(key);
        if (type == 1) {
            if (Boolean.TRUE.equals(previous)) invalid = true;
            activities.put(key, true);
            packages.put(key, pkg);
        } else {
            if (previous == null && resumedCount() > 0) invalid = true;
            activities.put(key, false);
            packages.put(key, pkg);
        }
    }

    /** Require a known epoch and exactly one resumed activity, belonging to the target. */
    boolean primeAlone() {
        if (!boundary || invalid || orderInvalid) return false;
        int active = 0;
        String owner = null;
        for (Map.Entry<String, Boolean> entry : activities.entrySet()) {
            if (entry.getValue()) {
                active++;
                owner = packages.get(entry.getKey());
            }
        }
        return active == 1 && NativeQualificationPolicy.PRIME_PACKAGE.equals(owner);
    }

    /** Count resumed packages for bounded diagnostics without exposing their names. */
    int resumedCount() {
        Set<String> resumed = new HashSet<>();
        for (Map.Entry<String, Boolean> entry : activities.entrySet()) {
            if (entry.getValue()) resumed.add(packages.get(entry.getKey()));
        }
        return resumed.size();
    }

    /** Distinguish missing retained anchor or inconsistent history from a normal departure. */
    boolean incomplete() { return !boundary || invalid || orderInvalid; }
}
