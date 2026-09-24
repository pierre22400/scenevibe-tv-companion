package com.scenevibe.tvcompanionpoc;

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
 * Tiny LAN-only HTTP transport for the physical v0.2.0 POC.
 * It accepts one bounded commentary contract and returns a correlated ACK.
 */
public final class CommentaryServer {
    public interface Listener {
        void onCommentary(String id, String text, long durationMs);
    }

    public static final int PORT = 8765;
    private static final String TAG = "SceneVibePoc";
    private static final int MAX_HEADER_BYTES = 8192;
    private static final int MAX_BODY_BYTES = 16384;
    private final Listener listener;
    private final ExecutorService clients = Executors.newSingleThreadExecutor();
    private volatile boolean running;
    private ServerSocket server;
    private Thread acceptThread;

    public CommentaryServer(Listener listener) {
        this.listener = listener;
    }

    public synchronized void start() throws IOException {
        if (running) return;
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(PORT));
        running = true;
        acceptThread = new Thread(this::acceptLoop, "scenevibe-commentary-server");
        acceptThread.start();
        Log.i(TAG, "Commentary server listening on 0.0.0.0:" + PORT);
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = server.accept();
                socket.setSoTimeout(5000);
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
                health.put("version", "0.2.0");
                respond(output, 200, health);
                return;
            }
            if (!"POST".equals(method) || !"/commentary".equals(path)) {
                respond(output, 404, error("not_found", "Use POST /commentary"));
                return;
            }

            int length = contentLength(lines);
            if (length <= 0 || length > MAX_BODY_BYTES) {
                respond(output, 413, error("invalid_length", "Body must be 1.." + MAX_BODY_BYTES + " bytes"));
                return;
            }
            byte[] body = input.readNBytes(length);
            if (body.length != length) {
                respond(output, 400, error("bad_request", "Incomplete request body"));
                return;
            }
            JSONObject json = new JSONObject(new String(body, StandardCharsets.UTF_8));
            if (!"scenevibe.commentary.v1".equals(json.optString("type"))) {
                respond(output, 422, error("invalid_type", "Expected scenevibe.commentary.v1"));
                return;
            }
            String id = json.optString("id", "").trim();
            String text = json.optString("text", "").trim();
            long durationMs = json.optLong("durationMs", 10000);
            if (id.isEmpty() || id.length() > 128 || text.isEmpty() || text.length() > 1000
                    || durationMs < 1000 || durationMs > 60000) {
                respond(output, 422, error("invalid_commentary", "Invalid id, text or durationMs"));
                return;
            }

            listener.onCommentary(id, text, durationMs);
            JSONObject ack = new JSONObject();
            ack.put("type", "scenevibe.commentary.ack.v1");
            ack.put("id", id);
            ack.put("status", "rendered");
            respond(output, 200, ack);
            Log.i(TAG, "Commentary rendered and ACK sent; id=" + id);
        } catch (JSONException error) {
            Log.w(TAG, "Invalid commentary JSON", error);
        } catch (IOException error) {
            if (running) Log.w(TAG, "Commentary client failed", error);
        }
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

    private JSONObject error(String code, String message) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("type", "scenevibe.error.v1");
        json.put("code", code);
        json.put("message", message);
        return json;
    }

    private void respond(BufferedOutputStream output, int status, JSONObject json) throws IOException {
        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        String reason = status == 200 ? "OK" : status == 404 ? "Not Found"
                : status == 413 ? "Payload Too Large" : status == 422 ? "Unprocessable Entity" : "Bad Request";
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
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
}
