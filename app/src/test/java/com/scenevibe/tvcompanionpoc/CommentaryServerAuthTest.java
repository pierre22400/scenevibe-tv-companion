package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/** Sends real HTTP requests to a loopback receiver on a disposable port. */
public class CommentaryServerAuthTest {
    @Test public void mutationsRequireTokenAndResetRevokesIt() throws Exception {
        Map<String, String> data = new HashMap<>();
        PairingPolicy policy = new PairingPolicy(() -> 0L, new PairingPolicy.Storage() {
            @Override public String get(String key) { return data.get(key); }
            @Override public void put(String key, String value) { data.put(key, value); }
            @Override public void remove(String key) { data.remove(key); }
        }, new SecureRandom());
        int[] rendered = {0};
        int[] loaded = {0};
        CommentaryServer server = new CommentaryServer((id, text, duration, image) -> rendered[0]++,
                track -> loaded[0]++, policy, 0);
        server.start();
        try {
            int port = server.boundPort();
            JSONObject health = body(send(port, "GET", "/health", null, null));
            assertEquals("0.6.0", health.getString("version"));
            assertTrue(health.getBoolean("pairingRequired"));
            assertFalse(health.toString().contains("token"));

            String comment = "{\"type\":\"scenevibe.commentary.v1\",\"id\":\"one\",\"text\":\"Hello\",\"durationMs\":2000}";
            String track = "{\"type\":\"scenevibe.track.v1\",\"trackId\":\"one\",\"targetPackage\":\"com.test\",\"pauseFreezesDisplay\":true,\"comments\":[{\"id\":\"a\",\"text\":\"Hi\",\"startMs\":0,\"durationMs\":2000}]}";
            assertStatus(401, send(port, "POST", "/commentary", comment, null));
            assertStatus(401, send(port, "POST", "/track", track, null));
            assertStatus(409, send(port, "POST", "/pair", "{}", null));

            String code = policy.start();
            String request = "{\"type\":\"scenevibe.pair.request.v1\",\"code\":\"" + code
                    + "\",\"clientName\":\"test sender\"}";
            String paired = send(port, "POST", "/pair", request, null);
            assertStatus(200, paired);
            String token = body(paired).getString("token");
            assertEquals(43, token.length());
            assertStatus(401, send(port, "POST", "/track", track, "Bearer bogus"));
            assertStatus(200, send(port, "POST", "/commentary", comment, "Bearer " + token));
            assertStatus(200, send(port, "POST", "/track", track, "Bearer " + token));
            assertEquals(1, rendered[0]);
            assertEquals(1, loaded[0]);
            policy.reset();
            assertStatus(401, send(port, "POST", "/commentary", comment, "Bearer " + token));
            assertStatus(401, send(port, "POST", "/track", track, "Bearer " + token));
        } finally {
            server.stop();
        }
    }

    private static String send(int port, String method, String path, String body, String auth)
            throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            String headers = method + " " + path + " HTTP/1.1\r\nHost: localhost\r\n"
                    + (auth == null ? "" : "Authorization: " + auth + "\r\n")
                    + "Content-Length: " + payload.length + "\r\n\r\n";
            socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(payload);
            socket.getOutputStream().flush();
            InputStream response = socket.getInputStream();
            return new String(response.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JSONObject body(String response) throws Exception {
        return new JSONObject(response.substring(response.indexOf("\r\n\r\n") + 4));
    }

    private static void assertStatus(int code, String response) {
        assertTrue(response, response.startsWith("HTTP/1.1 " + code + " "));
    }
}
