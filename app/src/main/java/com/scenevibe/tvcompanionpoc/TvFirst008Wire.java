package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.json.JSONObject;

/**
 * Closed cache-only wire contract. Original artifact bytes, never reserialized JSON,
 * cross the existing V1 validators and durable installer. No runtime port is accepted.
 */
final class TvFirst008Wire {
    static final String PROFILE = "tvfirst008-cache-v1";

    /** Exact association, intention and cache coordinates; titles confer no authority. */
    static final class Binding {
        final String device, generation, intention, track, codec, digest;
        final long revision;
        /** Refuse incomplete or ambiguous coordinates before any disk or network mutation. */
        Binding(JSONObject data) {
            if (!PROFILE.equals(data.optString("profile"))) throw invalid();
            device = uuid(data.optString("deviceId")); generation = uuid(data.optString("generation"));
            intention = uuid(data.optString("intentionId")); track = uuid(data.optString("finalTrackId"));
            codec = data.optString("codecId"); digest = data.optString("digest");
            revision = integer(data, "revision", 1);
            if (!TvCapabilities.current().supportedCodecs().contains(codec) || !digest.matches("[0-9a-f]{64}")) throw invalid();
        }
        /** Return only the declared cache receipt fields, without credentials. */
        JSONObject json() {
            try { return new JSONObject().put("profile", PROFILE).put("deviceId", device).put("generation", generation)
                    .put("intentionId", intention).put("finalTrackId", track).put("codecId", codec)
                    .put("digest", digest).put("revision", revision); }
            catch (Exception failed) { throw invalid(); }
        }
        /** Compare every authority coordinate, including generation and codec. */
        @Override public boolean equals(Object value) {
            if (!(value instanceof Binding)) return false;
            Binding other = (Binding) value;
            return revision == other.revision && device.equals(other.device) && generation.equals(other.generation)
                    && intention.equals(other.intention) && track.equals(other.track) && codec.equals(other.codec) && digest.equals(other.digest);
        }
        /** Keep equality and collection identity coherent. */
        @Override public int hashCode() { return Objects.hash(device, generation, intention, track, codec, digest, revision); }
    }

    /** Pair a fully checked binding with the original, bounded artifact bytes. */
    static final class Envelope {
        final Binding binding;
        final InstallRequest request;
        /** Reconstruct JSON solely for old validation; retain raw UTF-8 bytes for storage. */
        Envelope(JSONObject data, Binding expected) {
            try {
                binding = new Binding(data);
                if (!binding.equals(expected) || !"scenevibe.cloud.cache-assignment.v1".equals(data.optString("type"))) throw invalid();
                JSONObject raw = data.getJSONObject("artifacts");
                boolean manifested = TvCapabilities.CODEC_TRACK_OVERLAY.equals(binding.codec);
                if (raw.length() != (manifested ? 2 : 1) || !raw.has("runtime") || manifested != raw.has("manifest")) throw invalid();
                Map<String, byte[]> artifacts = new TreeMap<>();
                for (String name : new String[] {"runtime", "manifest"}) {
                    if (!raw.has(name)) continue;
                    String encoded = raw.getString(name);
                    int maximum = name.equals("runtime") ? 400_000 : 800_000;
                    if (encoded.length() > ((maximum + 2) / 3) * 4) throw invalid();
                    byte[] bytes = Base64.getDecoder().decode(encoded);
                    if (bytes.length == 0 || bytes.length > maximum || !Base64.getEncoder().encodeToString(bytes).equals(encoded)) throw invalid();
                    artifacts.put(name, bytes);
                }
                request = new InstallRequest(binding.revision, binding.codec, artifacts);
                if (!binding.digest.equals(digest(request))) throw invalid();
                JSONObject legacy = new JSONObject().put("type", "scenevibe.cloud.assignment.v1")
                        .put("deviceId", binding.device).put("revision", binding.revision).put("finalTrackId", binding.track)
                        .put("trackId", trackReference(data.optString("trackId")))
                        .put("runtimeTrack", new JSONObject(utf8(artifacts.get("runtime"))));
                if (manifested) legacy.put("overlayManifest", new JSONObject(utf8(artifacts.get("manifest"))));
                if (!CloudProtocol.validAssignment(legacy, binding.device, 0)) throw invalid();
            } catch (Exception rejected) { throw invalid(); }
        }
    }

    /** Match the Cloud framing vector exactly, including byte lengths and sorted names. */
    static String digest(InstallRequest request) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update(("scenevibe.cache.v1\n" + request.codecId() + "\n").getBytes(StandardCharsets.UTF_8));
            for (Map.Entry<String, byte[]> entry : new TreeMap<>(request.artifacts()).entrySet()) {
                hash.update((entry.getKey() + "\n" + entry.getValue().length + "\n").getBytes(StandardCharsets.UTF_8));
                hash.update(entry.getValue());
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : hash.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return hex.toString();
        } catch (Exception failed) { throw invalid(); }
    }
    /** Reject replacement characters and invalid UTF-8 rather than normalizing corrupt content. */
    static String utf8(byte[] bytes) {
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (Exception rejected) { throw invalid(); }
    }
    /** Canonical executable track ids are opaque strings, distinct from FinalTrack row UUIDs. */
    private static String trackReference(String value) {
        if (value == null || value.trim().isEmpty() || value.length() > 512) throw invalid();
        return value;
    }
    /** Require a real UUID, never a guessed title, serial or list offset. */
    static String uuid(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw invalid();
        return value;
    }
    /** Reject fractional, negative or unsafe JSON numbers. */
    static long integer(JSONObject data, String key, long minimum) {
        Object value = data.opt(key);
        if (!(value instanceof Number)) throw invalid();
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number) || number < minimum || number > 9_007_199_254_740_991L || number != Math.floor(number)) throw invalid();
        return ((Number) value).longValue();
    }
    /** Expose a constant error without content, identity, secret or parser diagnostics. */
    static IllegalArgumentException invalid() { return new IllegalArgumentException("INVALID_CACHE_CONTRACT"); }
    /** Static contract has no mutable state. */
    private TvFirst008Wire() {}
}
