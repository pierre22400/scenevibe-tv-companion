package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.wall.WallCalendar;
import com.scenevibe.tvcompanionpoc.wall.WallEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Strict parser for the local Banner WALL profile body (section 8). The body is inert data: a
 * closed-form JSON carrying a version, a UTC horizon, the WALL window set, an inline
 * {@link OverlayManifest} and a minimal bounded provenance. Unknown keys/types/versions, an
 * out-of-domain epoch, a horizon longer than {@link WallCalendar#MAX_HORIZON_MS}, more than
 * {@link WallCalendar#MAX_EVENTS} windows, or any malformed field fail closed before any Android
 * View or durable write.
 *
 * <p>It reuses the pure {@link WallEvent}/{@link WallCalendar} models for the window set and the
 * existing {@link OverlayManifestParser} for the inline manifest, so no second parser family and
 * no clock/timer/renderer is introduced here. It performs NO cross-contract check itself; the
 * scene&lt;-&gt;window bijection, offset-zero and exact-duration rules live in
 * {@link BannerOverlayManifestBridge}, exactly mirroring the Video parse/bridge split.</p>
 */
final class BannerProfileParser {
    static final String TYPE = "scenevibe.banner.wall-package.v1";
    static final String SCHEMA_VERSION = "1.0.0";

    /** Safe bounded parse failure with no submitted content. */
    static final class Invalid extends Exception {
        Invalid(String message) { super(message); }
    }

    /** Immutable trusted result of a Banner body parse: the pure horizon plus the inline manifest. */
    static final class Profile {
        final WallCalendar calendar;
        final OverlayManifest manifest;
        private Profile(WallCalendar calendar, OverlayManifest manifest) {
            this.calendar = calendar;
            this.manifest = manifest;
        }
    }

    /** Parse and fully validate one Banner profile body snapshot; cross-contract rules run later. */
    static Profile parse(JSONObject json) throws Invalid {
        if (json == null
                || !TYPE.equals(string(json, "type"))
                || !SCHEMA_VERSION.equals(string(json, "schemaVersion"))) {
            throw new Invalid("Invalid banner contract");
        }
        onlyKeys(json, "type", "schemaVersion", "horizon", "windows", "manifest", "provenance");

        JSONObject horizon = requireObject(json, "horizon");
        onlyKeys(horizon, "startEpochMs", "endEpochMs");
        long horizonStart = epoch(horizon, "startEpochMs");
        long horizonEnd = epoch(horizon, "endEpochMs");

        JSONArray rawWindows = json.optJSONArray("windows");
        if (rawWindows == null || rawWindows.length() < 1 || rawWindows.length() > WallCalendar.MAX_EVENTS) {
            throw new Invalid("Invalid banner windows");
        }
        List<WallEvent> windows = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < rawWindows.length(); i++) {
            JSONObject window = rawWindows.optJSONObject(i);
            if (window == null) throw new Invalid("Window must be object");
            onlyKeys(window, "eventId", "startEpochMs", "endEpochMs");
            String eventId = string(window, "eventId");
            if (!ids.add(eventId)) throw new Invalid("Duplicate banner window id");
            long start = epoch(window, "startEpochMs");
            long end = epoch(window, "endEpochMs");
            // WallEvent re-validates the id charset, the ordering and the M4 duration bounds.
            windows.add(new WallEvent(eventId, start, end));
        }

        // WallCalendar re-validates the horizon bound, the count, uniqueness and containment.
        WallCalendar calendar = new WallCalendar(horizonStart, horizonEnd, windows);

        JSONObject manifestJson = requireObject(json, "manifest");
        OverlayManifest manifest;
        try {
            manifest = OverlayManifestParser.parse(manifestJson);
        } catch (OverlayManifestParser.Invalid invalid) {
            throw new Invalid("Invalid banner manifest");
        }

        // Minimal bounded provenance only: a closed object with a single safe-id source field. No
        // URL, token, credential or free-form payload is accepted or retained.
        JSONObject provenance = requireObject(json, "provenance");
        onlyKeys(provenance, "source");
        safeId(string(provenance, "source"));

        return new Profile(calendar, manifest);
    }

    /** Require an actual JSON string; org.json coercion is deliberately forbidden. */
    private static String string(JSONObject object, String key) throws Invalid {
        Object raw = object.opt(key);
        if (!(raw instanceof String)) throw new Invalid("Invalid string");
        return (String) raw;
    }

    /** Require an object property. */
    private static JSONObject requireObject(JSONObject parent, String key) throws Invalid {
        JSONObject value = parent.optJSONObject(key);
        if (value == null) throw new Invalid("Missing object");
        return value;
    }

    /** Reject undeclared properties without recursing into their values. */
    private static void onlyKeys(JSONObject object, String... allowed) throws Invalid {
        Set<String> keys = new HashSet<>(java.util.Arrays.asList(allowed));
        java.util.Iterator<String> names = object.keys();
        while (names.hasNext()) if (!keys.contains(names.next())) throw new Invalid("Unknown property");
    }

    /** Validate the bounded safe-ASCII provenance id without echoing it. */
    private static String safeId(String value) throws Invalid {
        if (value == null || value.length() > 128 || !value.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new Invalid("Invalid banner provenance");
        }
        return value;
    }

    /** Read an exactly integral bounded epoch; WallEvent/WallCalendar re-check the WALL domain. */
    private static long epoch(JSONObject object, String key) throws Invalid {
        Object raw = object.opt(key);
        if (!(raw instanceof Number)) throw new Invalid("Invalid epoch");
        double value = ((Number) raw).doubleValue();
        long integral = ((Number) raw).longValue();
        if (!Double.isFinite(value) || value != (double) integral
                || integral < 0L || integral > WallEvent.MAX_EPOCH_MS) {
            throw new Invalid("Invalid epoch");
        }
        return integral;
    }

    /** Utility class has no mutable global state. */
    private BannerProfileParser() {}
}
