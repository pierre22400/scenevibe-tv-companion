package com.scenevibe.tvcompanionpoc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Test-only ordered temporal evidence, with one frame for every supplied input.
 * Empty frames retain the absence of effects. Nothing is sorted, deduplicated,
 * delayed or normalized. The optional token is external opaque harness data.
 */
final class M5TemporalJournal {
    /** The complete vocabulary observable at the legacy scheduler listener. */
    enum Kind { INELIGIBLE, ELIGIBLE, PLAYBACK, DUE, EXPIRE }

    /** Immutable effect carrying only its exact temporal arguments. */
    static final class Effect {
        final Kind kind;
        final String eventId;
        final boolean playing;
        final boolean freeze;

        /** Retain arguments without interpreting identifiers or pause policy. */
        Effect(Kind kind, String eventId, boolean playing, boolean freeze) {
            this.kind = kind;
            this.eventId = eventId;
            this.playing = playing;
            this.freeze = freeze;
        }

        /** Compare every argument exactly; no temporal tolerance is available. */
        boolean same(Effect other) {
            return kind == other.kind && Objects.equals(eventId, other.eventId)
                    && playing == other.playing && freeze == other.freeze;
        }

        /** Export the ordered fixture representation without payload data. */
        JSONArray json() {
            JSONArray result = new JSONArray().put(kind.name());
            if (kind == Kind.DUE || kind == Kind.EXPIRE) {
                result.put(eventId);
            } else if (kind == Kind.PLAYBACK) {
                result.put(playing).put(freeze);
            }
            return result;
        }

        /** Read one exact fixture effect and reject non-vocabulary arguments. */
        static Effect fromJson(JSONArray value) throws org.json.JSONException {
            Kind kind = Kind.valueOf(value.getString(0));
            int arity = kind == Kind.PLAYBACK ? 3
                    : kind == Kind.DUE || kind == Kind.EXPIRE ? 2 : 1;
            if (value.length() != arity) {
                throw new IllegalArgumentException("Invalid journal effect");
            }
            return new Effect(kind, arity == 2 ? value.getString(1) : null,
                    arity == 3 && value.getBoolean(1), arity == 3 && value.getBoolean(2));
        }
    }

    /** Immutable frame that makes even a no-op input observable to the comparator. */
    static final class Frame {
        final int input;
        final String token;
        final List<Effect> effects;

        /** Own the exact callback sequence for a single numbered input. */
        Frame(int input, String token, List<Effect> effects) {
            this.input = input;
            this.token = token;
            this.effects = Collections.unmodifiableList(new ArrayList<>(effects));
        }

        /** Export a frame without changing order or omitting an empty sequence. */
        JSONObject json() throws org.json.JSONException {
            JSONArray values = new JSONArray();
            for (Effect effect : effects) {
                values.put(effect.json());
            }
            return new JSONObject().put("input", input)
                    .put("token", token == null ? JSONObject.NULL : token).put("effects", values);
        }
    }

    private final List<Frame> frames = new ArrayList<>();
    private List<Effect> pending;
    private String token;

    /** Begin a frame before invoking an actual legacy entry point. */
    void begin(String externalToken) {
        if (pending != null) {
            throw new IllegalStateException("Unfinished journal frame");
        }
        token = externalToken;
        pending = new ArrayList<>();
    }

    /** Append one callback immediately in its original order. */
    void append(Kind kind, String eventId, boolean playing, boolean freeze) {
        if (pending == null) {
            throw new IllegalStateException("Callback outside input frame");
        }
        pending.add(new Effect(kind, eventId, playing, freeze));
    }

    /** Commit the frame even when the actual scheduler emitted nothing. */
    void end() {
        frames.add(new Frame(frames.size(), token, pending));
        pending = null;
    }

    /** Return a defensive immutable view of all ordered inputs and effects. */
    List<Frame> frames() {
        return Collections.unmodifiableList(new ArrayList<>(frames));
    }

    /** Export raw frames for fixture freezing and environment-dependent evidence. */
    JSONArray json() throws org.json.JSONException {
        JSONArray result = new JSONArray();
        for (Frame frame : frames) {
            result.put(frame.json());
        }
        return result;
    }

    /** Decode frozen expected frames; their indices and tokens remain exact. */
    static List<Frame> fromJson(JSONArray values) throws org.json.JSONException {
        List<Frame> result = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.getJSONObject(i);
            List<Effect> effects = new ArrayList<>();
            JSONArray entries = value.getJSONArray("effects");
            for (int j = 0; j < entries.length(); j++) {
                effects.add(Effect.fromJson(entries.getJSONArray(j)));
            }
            result.add(new Frame(value.getInt("input"),
                    value.isNull("token") ? null : value.getString("token"), effects));
        }
        return Collections.unmodifiableList(result);
    }

    /** Strict comparator for C: every input, token, absence and callback must match. */
    static boolean exactlyEqual(List<Frame> left, List<Frame> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            Frame a = left.get(i);
            Frame b = right.get(i);
            if (a.input != b.input || !Objects.equals(a.token, b.token)
                    || a.effects.size() != b.effects.size()) {
                return false;
            }
            for (int j = 0; j < a.effects.size(); j++) {
                if (!a.effects.get(j).same(b.effects.get(j))) {
                    return false;
                }
            }
        }
        return true;
    }
}
