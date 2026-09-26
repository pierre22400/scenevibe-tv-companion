package com.scenevibe.tvcompanionpoc;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tiny LAN-only HTTP transport for the physical TV POC.
 *
 * POST /commentary keeps the direct rendering probe.
 * POST /track loads a bounded runtime track whose timing is then driven on-TV
 * by the passive MediaSession clock.
 */
public final class CommentaryServer {
    public interface Listener {
        void onCommentary(String id, String text, long durationMs, Bitmap mediaBitmap);
    }

    public interface TrackListener {
        void onTrackLoaded(ScheduledTrack track);
    }

    public static final int PORT = 8765;
    private static final String TAG = "SceneVibePoc";
    private static final int MAX_HEADER_BYTES = 8192;
    private static final int MAX_BODY_BYTES = 3 * 1024 * 1024;
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_RENDER_WIDTH = 1280;
    private static final int MAX_RENDER_HEIGHT = 720;

    private final Listener listener;
    private final TrackListener trackListener;
    private final PairingPolicy pairing;
    private final int port;
    private final ExecutorService clients = Executors.newSingleThreadExecutor();
    private volatile boolean running;
    private ServerSocket server;
    private Thread acceptThread;

    public CommentaryServer(Listener listener, TrackListener trackListener, PairingPolicy pairing) {
        this(listener, trackListener, pairing, PORT);
    }

    CommentaryServer(Listener listener, TrackListener trackListener, PairingPolicy pairing, int port) {
        this.listener = listener;
        this.trackListener = trackListener;
        this.pairing = pairing;
        this.port = port;
    }

    public synchronized void start() {
        if (running) return;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port));
            running = true;
            acceptThread = new Thread(this::acceptLoop, "scenevibe-commentary-server");
            acceptThread.start();
            Log.i(TAG, "Commentary server listening on 0.0.0.0:" + server.getLocalPort());
        } catch (IOException error) {
            throw new IllegalStateException("Could not bind commentary server", error);
        }
    }

    synchronized int boundPort() { return server.getLocalPort(); }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = server.accept();
                socket.setSoTimeout(10000);
                clients.execute(() -> handle(socket));
            } catch (IOException error) {
                if (running) Log.e(TAG, "Commentary server accept failed", error);
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket client = socket;
             BufferedInputStream input = new BufferedInputStream(client.getInputStream());
             BufferedOutputStream output = new BufferedOutputStream(client.getOutputStream())) {
            byte[] headerBytes = readHeaders(input);
            String headers = new String(headerBytes, StandardCharsets.US_ASCII);
            String[] lines = headers.split("\r\n");
            if (lines.length == 0) {
                respond(output, 400, error("bad_request", "Missing request line"));
                return;
            }
            String[] request = lines[0].split(" ");
            String method = request.length > 0 ? request[0] : "";
            String path = request.length > 1 ? request[1] : "";

            if ("GET".equals(method) && "/health".equals(path)) {
                JSONObject health = new JSONObject();
                health.put("type", "scenevibe.health.v1");
                health.put("status", "ready");
                health.put("version", "0.6.0");
                health.put("protocolVersion", "1");
                health.put("pairingRequired", true);
                health.put("paired", pairing.hasToken());
                health.put("media", "inline-image");
                health.put("synchronization", "media-session-clock");
                respond(output, 200, health);
                return;
            }
            if (!"POST".equals(method)
                    || (!"/commentary".equals(path) && !"/track".equals(path)
                            && !"/pair".equals(path))) {
                respond(output, 404, error("not_found", "Use POST /pair, /commentary or /track"));
                return;
            }

            if ("/pair".equals(path) && pairing.status() != PairingPolicy.Status.PAIRING_OPEN) {
                respond(output, 409, error("pairing_closed", "Open pairing on the TV first"));
                return;
            }
            if (!"/pair".equals(path) && !pairing.authorized(headerValue(lines, "authorization"))) {
                respond(output, 401, error("unauthorized", "Valid Bearer token required"));
                return;
            }

            int length = contentLength(lines);
            if (length <= 0 || length > MAX_BODY_BYTES) {
                respond(output, 413, error("invalid_length",
                        "Body must be 1.." + MAX_BODY_BYTES + " bytes"));
                return;
            }
            byte[] body = new byte[length];
            int offset = 0;
            while (offset < length) {
                int count = input.read(body, offset, length - offset);
                if (count < 0) break;
                offset += count;
            }
            if (offset != length) {
                respond(output, 400, error("bad_request", "Incomplete request body"));
                return;
            }

            JSONObject json;
            try {
                json = new JSONObject(new String(body, StandardCharsets.UTF_8));
            } catch (JSONException error) {
                respond(output, 400, error("invalid_json", "Request body must be valid JSON"));
                return;
            }

            if (!"/pair".equals(path) && !pairing.authorized(headerValue(lines, "authorization"))) {
                respond(output, 401, error("unauthorized", "Valid Bearer token required"));
                return;
            }

            if ("/pair".equals(path)) {
                handlePair(output, json);
            } else if ("/track".equals(path)) {
                handleTrack(output, json, headerValue(lines, "authorization"));
            } else {
                handleCommentary(output, json, headerValue(lines, "authorization"));
            }
        } catch (JSONException error) {
            Log.w(TAG, "Could not build commentary response", error);
        } catch (IOException error) {
            if (running) Log.w(TAG, "Commentary client failed", error);
        }
    }

    private void handlePair(BufferedOutputStream output, JSONObject json)
            throws IOException, JSONException {
        if (!"scenevibe.pair.request.v1".equals(json.optString("type"))) {
            respond(output, 422, error("invalid_type", "Expected scenevibe.pair.request.v1"));
            return;
        }
        if (!(json.opt("code") instanceof String)
                || !(json.opt("clientName") instanceof String)) {
            respond(output, 422, error("invalid_pair_request",
                    "code and clientName must be strings"));
            return;
        }
        synchronized (pairing) {
            PairingPolicy.Result result = pairing.pair(json.optString("code", null),
                    json.optString("clientName", null));
            if (result != PairingPolicy.Result.PAIRED) {
                String code = result == PairingPolicy.Result.CLOSED ? "pairing_closed"
                        : result == PairingPolicy.Result.TOO_MANY_ATTEMPTS ? "too_many_attempts"
                        : result == PairingPolicy.Result.INVALID_REQUEST ? "invalid_pair_request"
                        : "invalid_code";
                int status = result == PairingPolicy.Result.INVALID_REQUEST ? 422
                        : result == PairingPolicy.Result.INVALID_CODE ? 401
                        : result == PairingPolicy.Result.TOO_MANY_ATTEMPTS ? 429 : 409;
                respond(output, status, error(code, "Pairing failed; check TV pairing state and code"));
                return;
            }
            JSONObject ack = new JSONObject();
            ack.put("type", "scenevibe.pair.ack.v1");
            ack.put("status", "paired");
            ack.put("deviceId", pairing.deviceId());
            ack.put("token", pairing.tokenForPairAck());
            respond(output, 200, ack);
        }
        Log.i(TAG, "LAN sender paired");
    }

    private void handleCommentary(BufferedOutputStream output, JSONObject json,
            String authorization)
            throws IOException, JSONException {
        if (!"scenevibe.commentary.v1".equals(json.optString("type"))) {
            respond(output, 422, error("invalid_type", "Expected scenevibe.commentary.v1"));
            return;
        }
        String id = json.optString("id", "").trim();
        String text = json.optString("text", "").trim();
        long durationMs = json.optLong("durationMs", 10000);
        if (id.isEmpty() || id.length() > 128 || text.isEmpty() || text.length() > 1000
                || durationMs < 1000 || durationMs > 60000) {
            respond(output, 422, error("invalid_commentary",
                    "Invalid id, text or durationMs"));
            return;
        }

        Bitmap mediaBitmap = null;
        JSONObject media = json.optJSONObject("media");
        if (media != null) {
            MediaDecodeResult decoded = decodeImage(media);
            if (decoded.errorCode != null) {
                respond(output, 422, error(decoded.errorCode, decoded.errorMessage));
                return;
            }
            mediaBitmap = decoded.bitmap;
        }

        synchronized (pairing) {
            if (!pairing.authorized(authorization)) {
                respond(output, 401, error("unauthorized", "Valid Bearer token required"));
                return;
            }
            listener.onCommentary(id, text, durationMs, mediaBitmap);
        }
        JSONObject ack = new JSONObject();
        ack.put("type", "scenevibe.commentary.ack.v1");
        ack.put("id", id);
        ack.put("status", "rendered");
        ack.put("mediaRendered", mediaBitmap != null);
        respond(output, 200, ack);
        Log.i(TAG, "Commentary rendered and ACK sent; id=" + id
                + "; media=" + (mediaBitmap != null));
    }

    private void handleTrack(BufferedOutputStream output, JSONObject json, String authorization)
            throws IOException, JSONException {
        if (trackListener == null) {
            respond(output, 422, error("track_unavailable", "Track scheduler is unavailable"));
            return;
        }
        final ScheduledTrack track;
        try {
            track = TrackParser.parse(json, media -> {
                MediaDecodeResult decoded = decodeImage(media);
                if (decoded.errorCode != null) {
                    throw new TrackParser.Invalid(decoded.errorCode, decoded.errorMessage);
                }
                return decoded.bitmap;
            });
        } catch (TrackParser.Invalid invalid) {
            respond(output, 422, error(invalid.code, invalid.getMessage()));
            return;
        }

        synchronized (pairing) {
            if (!pairing.authorized(authorization)) {
                respond(output, 401, error("unauthorized", "Valid Bearer token required"));
                return;
            }
            trackListener.onTrackLoaded(track);
        }

        JSONObject ack = new JSONObject();
        ack.put("type", "scenevibe.track.ack.v1");
        ack.put("trackId", track.trackId);
        ack.put("status", "loaded");
        ack.put("targetPackage", track.targetPackage);
        ack.put("commentCount", track.comments.size());
        respond(output, 200, ack);
        Log.i(TAG, "Track loaded and ACK sent; trackId=" + track.trackId
                + "; targetPackage=" + track.targetPackage
                + "; comments=" + track.comments.size());
    }

    private MediaDecodeResult decodeImage(JSONObject media) {
        if (!"image".equals(media.optString("kind"))) {
            return MediaDecodeResult.error("invalid_media_kind", "Only media.kind=image is supported");
        }
        String mimeType = media.optString("mimeType", "").trim().toLowerCase(Locale.ROOT);
        if (!"image/jpeg".equals(mimeType) && !"image/png".equals(mimeType)) {
            return MediaDecodeResult.error("invalid_media_type",
                    "Only image/jpeg and image/png are supported");
        }
        String encoded = media.optString("dataBase64", "").trim();
        if (encoded.isEmpty()) {
            return MediaDecodeResult.error("invalid_media_data", "media.dataBase64 is required");
        }

        final byte[] imageBytes;
        try {
            imageBytes = Base64.decode(encoded, Base64.DEFAULT);
        } catch (IllegalArgumentException error) {
            return MediaDecodeResult.error("invalid_media_data", "media.dataBase64 is invalid");
        }
        if (imageBytes.length == 0 || imageBytes.length > MAX_IMAGE_BYTES) {
            return MediaDecodeResult.error("image_too_large",
                    "Decoded image must be 1.." + MAX_IMAGE_BYTES + " bytes");
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outMimeType == null) {
            return MediaDecodeResult.error("invalid_image", "Image bytes could not be decoded");
        }
        if (!mimeType.equals(bounds.outMimeType)) {
            return MediaDecodeResult.error("media_type_mismatch",
                    "Declared mimeType does not match decoded image");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
        if (bitmap == null) {
            return MediaDecodeResult.error("invalid_image", "Image bytes could not be rendered");
        }
        return MediaDecodeResult.success(bitmap);
    }

    private int sampleSize(int width, int height) {
        int sample = 1;
        while (width / sample > MAX_RENDER_WIDTH || height / sample > MAX_RENDER_HEIGHT) {
            sample *= 2;
        }
        return sample;
    }

    private byte[] readHeaders(BufferedInputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int state = 0;
        while (bytes.size() < MAX_HEADER_BYTES) {
            int value = input.read();
            if (value < 0) break;
            bytes.write(value);
            if ((state == 0 || state == 2) && value == '\r') state++;
            else if ((state == 1 || state == 3) && value == '\n') state++;
            else state = value == '\r' ? 1 : 0;
            if (state == 4) return bytes.toByteArray();
        }
        throw new IOException("HTTP headers missing terminator or too large");
    }

    private int contentLength(String[] lines) {
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("content-length:")) {
                try {
                    return Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private String headerValue(String[] lines, String name) {
        for (int i = 1; i < lines.length; i++) {
            int delimiter = lines[i].indexOf(':');
            if (delimiter > 0 && name.equalsIgnoreCase(lines[i].substring(0, delimiter).trim())) {
                return lines[i].substring(delimiter + 1).trim();
            }
        }
        return null;
    }

    private JSONObject error(String code, String message) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("type", "scenevibe.error.v1");
        json.put("code", code);
        json.put("message", message);
        return json;
    }

    private void respond(BufferedOutputStream output, int status, JSONObject json) throws IOException {
        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        String reason = status == 200 ? "OK" : status == 401 ? "Unauthorized"
                : status == 404 ? "Not Found" : status == 409 ? "Conflict"
                : status == 429 ? "Too Many Requests"
                : status == 413 ? "Payload Too Large" : status == 422 ? "Unprocessable Entity"
                : "Bad Request";
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.flush();
    }

    public synchronized void stop() {
        running = false;
        if (server != null) {
            try { server.close(); } catch (IOException ignored) {}
            server = null;
        }
        clients.shutdownNow();
        Log.i(TAG, "Commentary server stopped");
    }

    private static final class MediaDecodeResult {
        final Bitmap bitmap;
        final String errorCode;
        final String errorMessage;

        private MediaDecodeResult(Bitmap bitmap, String errorCode, String errorMessage) {
            this.bitmap = bitmap;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        static MediaDecodeResult success(Bitmap bitmap) {
            return new MediaDecodeResult(bitmap, null, null);
        }

        static MediaDecodeResult error(String code, String message) {
            return new MediaDecodeResult(null, code, message);
        }
    }
}
